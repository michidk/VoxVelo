package dev.michidk.voxvelo.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.google.common.reflect.TypeToken;
import dev.michidk.voxvelo.bike.BikeType;

/** Per-frame bike state copied into the GeckoLib render state so models, controllers and bone code can read it. */
public final class BikeDataTickets {
	public static final DataTicket<Integer> FRAME_COLOR = DataTicket.create("voxvelo_frame_color", new TypeToken<>() {});
	public static final DataTicket<Integer> COMPONENTS = DataTicket.create("voxvelo_components", new TypeToken<>() {});
	/** Installed treads and handlebar kind, see BikeComponents.setup. */
	public static final DataTicket<Integer> SETUP = DataTicket.create("voxvelo_setup", new TypeToken<>() {});
	public static final DataTicket<BikeType> BIKE_TYPE = DataTicket.create("voxvelo_bike_type", new TypeToken<>() {});
	/** Accumulated wheel rotation in radians. */
	public static final DataTicket<Float> WHEEL_ANGLE = DataTicket.create("voxvelo_wheel_angle", new TypeToken<>() {});
	/** Front wheel deflection in radians (positive = right). */
	public static final DataTicket<Float> STEER_ANGLE = DataTicket.create("voxvelo_steer_angle", new TypeToken<>() {});
	public static final DataTicket<Float> HURT_TIME = DataTicket.create("voxvelo_hurt_time", new TypeToken<>() {});
	public static final DataTicket<Integer> HURT_DIR = DataTicket.create("voxvelo_hurt_dir", new TypeToken<>() {});
	/** Vertical offset of the body from the suspension spring, in blocks. */
	public static final DataTicket<Float> SUSPENSION = DataTicket.create("voxvelo_suspension", new TypeToken<>() {});
	public static final DataTicket<Float> DAMAGE = DataTicket.create("voxvelo_damage", new TypeToken<>() {});

	private BikeDataTickets() {
	}
}
