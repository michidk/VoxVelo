package dev.michidk.voxelvelo.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.google.common.reflect.TypeToken;
import dev.michidk.voxelvelo.bike.BikeType;

/** Per-frame bike state copied into the GeckoLib render state so models, controllers and bone code can read it. */
public final class BikeDataTickets {
    public static final DataTicket<Integer> FRAME_COLOR = DataTicket.create("voxelvelo_frame_color", new TypeToken<>() {});
    public static final DataTicket<Integer> COMPONENTS = DataTicket.create("voxelvelo_components", new TypeToken<>() {});
	public static final DataTicket<BikeType> BIKE_TYPE = DataTicket.create("voxelvelo_bike_type", new TypeToken<>() {});
	/** Accumulated wheel rotation in radians. */
	public static final DataTicket<Float> WHEEL_ANGLE = DataTicket.create("voxelvelo_wheel_angle", new TypeToken<>() {});
	/** Front wheel deflection in radians (positive = right). */
	public static final DataTicket<Float> STEER_ANGLE = DataTicket.create("voxelvelo_steer_angle", new TypeToken<>() {});
	public static final DataTicket<Float> HURT_TIME = DataTicket.create("voxelvelo_hurt_time", new TypeToken<>() {});
	public static final DataTicket<Integer> HURT_DIR = DataTicket.create("voxelvelo_hurt_dir", new TypeToken<>() {});
	/** Vertical offset of the body from the suspension spring, in blocks. */
	public static final DataTicket<Float> SUSPENSION = DataTicket.create("voxelvelo_suspension", new TypeToken<>() {});
	public static final DataTicket<Float> DAMAGE = DataTicket.create("voxelvelo_damage", new TypeToken<>() {});

	private BikeDataTickets() {
	}
}
