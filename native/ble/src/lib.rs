//! Bluetooth LE for VoxVelo: a small C ABI over btleplug (WinRT on Windows, BlueZ on Linux, CoreBluetooth on macOS),
//! called from Java through the FFM API by `BtleplugBackend`.
//!
//! Every exported function returns immediately. Results and events are delivered through the one callback registered
//! with [`vvble_init`], from Bluetooth threads: `callback(kind, request, status, payload, payload_len)`. The payload is
//! only valid during the call. Strings in payloads are a little-endian u16 byte length followed by UTF-8; see
//! `Payload` below and its counterpart in `BtleplugBackend`.

use std::collections::{HashMap, HashSet};
use std::ffi::{CStr, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use btleplug::api::{Central, CentralEvent, CentralState, Characteristic, Manager as _, Peripheral as _, ScanFilter, WriteType};
use btleplug::platform::{Adapter, Manager, Peripheral, PeripheralId};
use futures::StreamExt;
use tokio::runtime::Runtime;
use uuid::Uuid;

/// Completes the request with the same number. Status 0 carries the result bytes, anything else a UTF-8 error message.
const RESULT: i32 = 1;
/// A device was seen or updated while scanning: id, name, rssi (i16), advertised service UUIDs (u16 count + strings).
const DEVICE: i32 = 2;
/// A connected device dropped its link: id.
const DISCONNECTED: i32 = 3;
/// A notification or indication arrived: id, service UUID, characteristic UUID, then the value as the remaining bytes.
const NOTIFICATION: i32 = 4;

const OK: i32 = 0;
const FAILED: i32 = 1;

/// How long a connection attempt may take before it is given up.
const CONNECT_TIMEOUT: Duration = Duration::from_secs(20);

type Callback = extern "C" fn(kind: i32, request: i64, status: i32, payload: *const u8, len: usize);

struct State {
	runtime: Runtime,
	callback: Callback,
	adapter: Mutex<Option<Adapter>>,
	/// Peripherals seen by a scan, by the id handed to Java.
	peripherals: Mutex<HashMap<String, Peripheral>>,
	/// Devices whose notification stream is already forwarded (the stream outlives single connections).
	forwarding: Mutex<HashSet<String>>,
}

static STATE: OnceLock<State> = OnceLock::new();

fn state() -> Option<&'static State> {
	STATE.get()
}

fn emit(kind: i32, request: i64, status: i32, payload: &[u8]) {
	if let Some(state) = state() {
		(state.callback)(kind, request, status, payload.as_ptr(), payload.len());
	}
}

fn complete(request: i64, result: Result<Vec<u8>, String>) {
	match result {
		Ok(bytes) => emit(RESULT, request, OK, &bytes),
		Err(message) => emit(RESULT, request, FAILED, message.as_bytes()),
	}
}

#[derive(Default)]
struct Payload(Vec<u8>);

impl Payload {
	fn str(mut self, value: &str) -> Self {
		let bytes = value.as_bytes();
		let len = bytes.len().min(u16::MAX as usize);
		self.0.extend_from_slice(&(len as u16).to_le_bytes());
		self.0.extend_from_slice(&bytes[..len]);
		self
	}

	fn i16(mut self, value: i16) -> Self {
		self.0.extend_from_slice(&value.to_le_bytes());
		self
	}

	fn u8(mut self, value: u8) -> Self {
		self.0.push(value);
		self
	}

	fn u16(mut self, value: u16) -> Self {
		self.0.extend_from_slice(&value.to_le_bytes());
		self
	}

	fn bytes(mut self, value: &[u8]) -> Self {
		self.0.extend_from_slice(value);
		self
	}
}

/// Copies a NUL-terminated UTF-8 string from Java. The memory is only valid during the call.
fn string(ptr: *const c_char) -> Option<String> {
	if ptr.is_null() {
		return None;
	}
	// SAFETY: Java passes NUL-terminated strings that stay alive for the duration of the call.
	unsafe { CStr::from_ptr(ptr) }.to_str().ok().map(str::to_owned)
}

fn uuid(ptr: *const c_char) -> Result<Uuid, String> {
	let text = string(ptr).ok_or("missing UUID")?;
	Uuid::parse_str(&text).map_err(|e| format!("bad UUID {text}: {e}"))
}

/// Runs an exported function body, turning a panic into a failed submission instead of unwinding into Java.
fn guard(body: impl FnOnce() -> i32) -> i32 {
	catch_unwind(AssertUnwindSafe(body)).unwrap_or(-1)
}

/// Runs an operation on the Bluetooth runtime and completes the request with its result.
fn submit<F>(request: i64, operation: F) -> i32
where
	F: Future<Output = Result<Vec<u8>, String>> + Send + 'static,
{
	guard(|| {
		let Some(state) = state() else {
			return -1;
		};
		state.runtime.spawn(async move {
			complete(request, operation.await);
		});
		0
	})
}

fn adapter() -> Result<Adapter, String> {
	let state = state().ok_or("Bluetooth is not initialised")?;
	state
		.adapter
		.lock()
		.unwrap()
		.clone()
		.ok_or_else(|| "No Bluetooth adapter".to_owned())
}

fn peripheral(id: &str) -> Result<Peripheral, String> {
	let state = state().ok_or("Bluetooth is not initialised")?;
	state
		.peripherals
		.lock()
		.unwrap()
		.get(id)
		.cloned()
		.ok_or_else(|| format!("Device {id} has not been seen by a scan"))
}

fn characteristic(peripheral: &Peripheral, service: Uuid, characteristic: Uuid) -> Result<Characteristic, String> {
	let services = peripheral.services();
	let Some(found) = services.iter().find(|s| s.uuid == service) else {
		return Err(format!("Service {service} not found"));
	};
	// Windows reports a service another program holds as present but empty.
	if found.characteristics.is_empty() {
		return Err(format!(
			"Service {service} is not accessible (is another program connected to the device?)"
		));
	}
	found
		.characteristics
		.iter()
		.find(|c| c.uuid == characteristic)
		.cloned()
		.ok_or_else(|| format!("Characteristic {characteristic} of service {service} not found"))
}

/// Starts the Bluetooth runtime and registers the callback. Safe to call more than once; later calls change nothing.
/// Returns 0 on success.
#[unsafe(no_mangle)]
pub extern "C" fn vvble_init(callback: Callback) -> i32 {
	guard(|| {
		if STATE.get().is_some() {
			return 0;
		}
		let runtime = match tokio::runtime::Builder::new_multi_thread()
			.worker_threads(2)
			.thread_name("voxvelo-ble")
			.enable_time()
			.build()
		{
			Ok(runtime) => runtime,
			Err(_) => return -1,
		};
		let _ = STATE.set(State {
			runtime,
			callback,
			adapter: Mutex::new(None),
			peripherals: Mutex::new(HashMap::new()),
			forwarding: Mutex::new(HashSet::new()),
		});
		0
	})
}

/// Looks for a usable adapter and starts listening to its events. Completes with the adapter description, or fails
/// with a short reason that the settings screen shows after "Bluetooth unavailable:". May be repeated (the user may
/// switch Bluetooth on later).
#[unsafe(no_mangle)]
pub extern "C" fn vvble_open(request: i64) -> i32 {
	submit(request, async move {
		let manager = Manager::new().await.map_err(|e| e.to_string())?;
		let adapter = manager
			.adapters()
			.await
			.map_err(|e| e.to_string())?
			.into_iter()
			.next()
			.ok_or("no adapter found")?;
		if adapter.adapter_state().await.ok() == Some(CentralState::PoweredOff) {
			return Err("switched off".to_owned());
		}
		let info = adapter.adapter_info().await.unwrap_or_default();
		let events = adapter.events().await.map_err(|e| e.to_string())?;
		*state().ok_or("Bluetooth is not initialised")?.adapter.lock().unwrap() = Some(adapter.clone());
		tokio::spawn(forward_events(adapter, events));
		Ok(Payload::default().str(&info).0)
	})
}

async fn forward_events(adapter: Adapter, mut events: std::pin::Pin<Box<dyn futures::Stream<Item = CentralEvent> + Send>>) {
	while let Some(event) = events.next().await {
		match event {
			CentralEvent::DeviceDiscovered(id)
			| CentralEvent::DeviceUpdated(id)
			| CentralEvent::ServicesAdvertisement { id, .. }
			| CentralEvent::RssiUpdate { id, .. } => report_device(&adapter, &id).await,
			CentralEvent::DeviceDisconnected(id) => emit(DISCONNECTED, 0, OK, &Payload::default().str(&id.to_string()).0),
			_ => {}
		}
	}
}

async fn report_device(adapter: &Adapter, id: &PeripheralId) {
	let Ok(peripheral) = adapter.peripheral(id).await else {
		return;
	};
	let Ok(Some(properties)) = peripheral.properties().await else {
		return;
	};
	let key = id.to_string();
	if let Some(state) = state() {
		state.peripherals.lock().unwrap().insert(key.clone(), peripheral);
	}
	let name = properties.local_name.or(properties.advertisement_name).unwrap_or_default();
	let services = properties.services;
	let mut payload = Payload::default()
		.str(&key)
		.str(&name)
		.i16(properties.rssi.unwrap_or(i16::MIN))
		.u16(services.len().min(u16::MAX as usize) as u16);
	for service in services.iter().take(u16::MAX as usize) {
		payload = payload.str(&service.to_string());
	}
	emit(DEVICE, 0, OK, &payload.0);
}

#[unsafe(no_mangle)]
pub extern "C" fn vvble_scan_start(request: i64) -> i32 {
	submit(request, async move {
		adapter()?.start_scan(ScanFilter::default()).await.map_err(|e| e.to_string())?;
		Ok(Vec::new())
	})
}

#[unsafe(no_mangle)]
pub extern "C" fn vvble_scan_stop(request: i64) -> i32 {
	submit(request, async move {
		adapter()?.stop_scan().await.map_err(|e| e.to_string())?;
		Ok(Vec::new())
	})
}

/// Connects to a device seen by a scan and discovers its services.
#[unsafe(no_mangle)]
pub extern "C" fn vvble_connect(request: i64, id: *const c_char) -> i32 {
	let id = string(id).unwrap_or_default();
	submit(request, async move {
		let peripheral = peripheral(&id)?;
		tokio::time::timeout(CONNECT_TIMEOUT, peripheral.connect())
			.await
			.map_err(|_| "Connecting timed out".to_owned())?
			.map_err(|e| e.to_string())?;
		peripheral.discover_services().await.map_err(|e| e.to_string())?;
		let first = state().is_some_and(|s| s.forwarding.lock().unwrap().insert(id.clone()));
		if first {
			match peripheral.notifications().await {
				Ok(mut stream) => {
					tokio::spawn(async move {
						while let Some(n) = stream.next().await {
							let payload = Payload::default()
								.str(&id)
								.str(&n.service_uuid.to_string())
								.str(&n.uuid.to_string())
								.bytes(&n.value);
							emit(NOTIFICATION, 0, OK, &payload.0);
						}
						if let Some(state) = state() {
							state.forwarding.lock().unwrap().remove(&id);
						}
					});
				}
				Err(e) => {
					if let Some(state) = state() {
						state.forwarding.lock().unwrap().remove(&id);
					}
					return Err(format!("Notifications are unavailable: {e}"));
				}
			}
		}
		Ok(Vec::new())
	})
}

#[unsafe(no_mangle)]
pub extern "C" fn vvble_disconnect(request: i64, id: *const c_char) -> i32 {
	let id = string(id).unwrap_or_default();
	submit(request, async move {
		peripheral(&id)?.disconnect().await.map_err(|e| e.to_string())?;
		Ok(Vec::new())
	})
}

/// Completes with the services discovered on a connected device: u16 count, then per service its UUID, a u16
/// characteristic count and per characteristic its UUID and property flags (u8).
#[unsafe(no_mangle)]
pub extern "C" fn vvble_services(request: i64, id: *const c_char) -> i32 {
	let id = string(id).unwrap_or_default();
	submit(request, async move {
		let services = peripheral(&id)?.services();
		let mut payload = Payload::default().u16(services.len() as u16);
		for service in &services {
			payload = payload.str(&service.uuid.to_string()).u16(service.characteristics.len() as u16);
			for c in &service.characteristics {
				payload = payload.str(&c.uuid.to_string()).u8(c.properties.bits());
			}
		}
		Ok(payload.0)
	})
}

#[unsafe(no_mangle)]
pub extern "C" fn vvble_read(request: i64, id: *const c_char, service: *const c_char, characteristic_uuid: *const c_char) -> i32 {
	let id = string(id).unwrap_or_default();
	let target = uuid(service).and_then(|s| uuid(characteristic_uuid).map(|c| (s, c)));
	submit(request, async move {
		let (service, characteristic_uuid) = target?;
		let peripheral = peripheral(&id)?;
		let c = characteristic(&peripheral, service, characteristic_uuid)?;
		peripheral.read(&c).await.map_err(|e| e.to_string())
	})
}

/// Writes `len` bytes at `data`. The data is copied before this returns.
///
/// # Safety
///
/// `data` must point to `len` readable bytes (or be null with `len` 0) for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn vvble_write(
	request: i64,
	id: *const c_char,
	service: *const c_char,
	characteristic_uuid: *const c_char,
	data: *const u8,
	len: usize,
	with_response: bool,
) -> i32 {
	let id = string(id).unwrap_or_default();
	let target = uuid(service).and_then(|s| uuid(characteristic_uuid).map(|c| (s, c)));
	let bytes = if data.is_null() || len == 0 {
		Vec::new()
	} else {
		// SAFETY: Java passes a buffer of `len` bytes that stays alive for the duration of the call.
		unsafe { std::slice::from_raw_parts(data, len) }.to_vec()
	};
	submit(request, async move {
		let (service, characteristic_uuid) = target?;
		let peripheral = peripheral(&id)?;
		let c = characteristic(&peripheral, service, characteristic_uuid)?;
		let kind = if with_response {
			WriteType::WithResponse
		} else {
			WriteType::WithoutResponse
		};
		peripheral.write(&c, &bytes, kind).await.map_err(|e| e.to_string())?;
		Ok(Vec::new())
	})
}

/// Enables notifications, or indications for characteristics that only support those.
#[unsafe(no_mangle)]
pub extern "C" fn vvble_subscribe(request: i64, id: *const c_char, service: *const c_char, characteristic_uuid: *const c_char) -> i32 {
	let id = string(id).unwrap_or_default();
	let target = uuid(service).and_then(|s| uuid(characteristic_uuid).map(|c| (s, c)));
	submit(request, async move {
		let (service, characteristic_uuid) = target?;
		let peripheral = peripheral(&id)?;
		let c = characteristic(&peripheral, service, characteristic_uuid)?;
		peripheral.subscribe(&c).await.map_err(|e| e.to_string())?;
		Ok(Vec::new())
	})
}
