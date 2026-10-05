package dev.michidk.voxelvelo.client.obc;

/** Constants from the OpenBikeControl protocol (https://github.com/OpenBikeControl/openbikecontrol-protocol). */
public final class Obc {
	public static final String MDNS_TYPE = "_openbikecontrol._tcp.local.";

	public static final String BLE_SERVICE = "d273f680-d548-419d-b9d1-fa0472345229";
	public static final String BLE_BUTTON_STATE = "d273f681-d548-419d-b9d1-fa0472345229";
	public static final String BLE_HAPTIC = "d273f682-d548-419d-b9d1-fa0472345229";
	public static final String BLE_APP_INFO = "d273f683-d548-419d-b9d1-fa0472345229";

	public static final int MSG_BUTTON_STATE = 0x01;
	public static final int MSG_DEVICE_STATUS = 0x02;
	public static final int MSG_HAPTIC = 0x03;
	public static final int MSG_APP_INFO = 0x04;

	public static final int BUTTON_SHIFT_UP = 0x01;
	public static final int BUTTON_SHIFT_DOWN = 0x02;
	public static final int BUTTON_GEAR_SET = 0x03;
	public static final int BUTTON_STEER_LEFT = 0x18;
	public static final int BUTTON_STEER_RIGHT = 0x19;
	public static final int BUTTON_BRAKE = 0x1A;

	/** The actions this mod consumes; sent to the device in the App Information message. */
	public static final int[] SUPPORTED_BUTTONS = {
		BUTTON_SHIFT_UP, BUTTON_SHIFT_DOWN, BUTTON_GEAR_SET, BUTTON_STEER_LEFT, BUTTON_STEER_RIGHT, BUTTON_BRAKE
	};

	private Obc() {
	}
}
