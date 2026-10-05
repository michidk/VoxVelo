import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One-off seed generator for the three bike GeckoLib models and the shared animation file.
 * Run from the project root:  java tools/GenerateBikeModels.java src/main/resources
 *
 * After the files exist they are meant to be edited in Blockbench; this tool only documents how the
 * initial geometry was produced from the original procedural model.
 *
 * Coordinates below are written in the old vanilla-model convention (y points DOWN from the wheel axle
 * height, the front of the bike is -z) and converted to Bedrock geometry (y up from the ground).
 */
public class GenerateBikeModels {
	static final int FRAME = 0, TYRE = 24, TRIM = 48, METAL = 72;
	static final double R = 6.0, AXLE = 8.0;

	record Spec(double tyreWidth, double tube, double barHeight, double barWidth, boolean dropBars, boolean suspension) {}

	static class Bone {
		final String name;
		final String parent;
		final double[] pivot;
		final List<String> cubes = new ArrayList<>();

		Bone(String name, String parent, double x, double yUp, double z) {
			this.name = name;
			this.parent = parent;
			this.pivot = new double[] { x, yUp, z };
		}
	}

	static String n(double v) {
		return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("[.]$", "");
	}

	static String vec(double a, double b, double c) {
		return "[" + n(a) + ", " + n(b) + ", " + n(c) + "]";
	}

	/** Axis-aligned box in vanilla coordinates: y is measured downward from the axle height. */
	static void box(Bone b, int band, double x, double yDown, double z, double w, double h, double d) {
		b.cubes.add("{\"origin\": " + vec(x, 6.0 - (yDown + h), z) + ", \"size\": " + vec(w, h, d) + ", \"uv\": [0, " + band + "]}");
	}

	/** A box rotated about the x axis around the given bedrock pivot (y up). */
	static void rotBox(Bone b, int band, double x, double yUp, double z, double w, double h, double d, double pivotY, double pivotZ, double rxDeg) {
		b.cubes.add("{\"origin\": " + vec(x, yUp, z) + ", \"size\": " + vec(w, h, d) + ", \"pivot\": " + vec(0, pivotY, pivotZ)
			+ ", \"rotation\": " + vec(rxDeg, 0, 0) + ", \"uv\": [0, " + band + "]}");
	}

	/** Square tube between two points of the centre plane, endpoints given as (yDown, z). */
	static void tube(Bone b, int band, double t, double y1, double z1, double y2, double z2) {
		double dyUp = -(y2 - y1);
		double dz = z2 - z1;
		double length = Math.hypot(dyUp, dz);
		double cy = 6.0 - (y1 + y2) / 2.0;
		double cz = (z1 + z2) / 2.0;
		double rx = Math.toDegrees(Math.atan2(-dyUp, -dz));
		rotBox(b, band, -t / 2, cy - t / 2, cz - length / 2, t, t, length, cy, cz, rx);
	}

	static void wheel(Bone b, double tyreWidth, double zc) {
		box(b, METAL, -1.5, -1.0, zc - 1.0, 3.0, 2.0, 2.0);
		for (int i = 0; i < 8; i++) {
			// Segment 0 is a light reflector patch so wheel rotation is visible on a plain black tyre.
			rotBox(b, i == 0 ? TRIM : TYRE, -tyreWidth / 2, 6.0 + R - 1.3, zc - 2.4, tyreWidth, 1.3, 4.8, 6.0, zc, i * 45.0);
		}
		for (int i = 0; i < 4; i++) {
			rotBox(b, METAL, -0.25, 6.0 - (R - 1.0), zc - 0.25, 0.5, 2.0 * (R - 1.0), 0.5, 6.0, zc, i * 45.0);
		}
	}

	static String geometry(String type, Spec s) {
		Bone frame = new Bone("frame", null, 0, 6, 0);
		Bone rear = new Bone("rear_wheel", null, 0, 6, AXLE);
		Bone front = new Bone("front_assembly", null, 0, 6, -AXLE);
		Bone frontWheel = new Bone("front_wheel", "front_assembly", 0, 6, -AXLE);
		Bone crank = new Bone("crank", null, 0, 6, 1);
		Bone pedalRight = new Bone("pedal_right", "crank", 4.5, 0.5, 1);
		Bone pedalLeft = new Bone("pedal_left", "crank", -4.5, 11.5, 1);

		wheel(rear, s.tyreWidth, AXLE);
		wheel(frontWheel, s.tyreWidth, -AXLE);

		double t = s.tube;
		tube(frame, FRAME, t * 0.8, 0, 1, 0, AXLE);
		tube(frame, FRAME, t * 0.8, -13, 3, 0, AXLE);
		tube(frame, FRAME, t, 0, 1, -13, 3);
		tube(frame, FRAME, t, -13, 3, -13, -8);
		tube(frame, FRAME, t * 1.15, 0, 1, -11, -8);
		tube(frame, FRAME, t * 1.1, -11, -8, -15, -8);
		if (s.suspension) {
			tube(frame, TRIM, 1.5, -9, 2.2, -2, 5);
		}
		box(frame, METAL, -0.5, -16, 2.5, 1, 3, 1);
		box(frame, TRIM, -2, -17, 0.5, 4, 1.5, 7);

		double leg = s.suspension ? 1.7 : 1.0;
		box(front, METAL, -3 - leg / 2, -12, -AXLE - leg / 2, leg, 12, leg);
		box(front, METAL, 3 - leg / 2, -12, -AXLE - leg / 2, leg, 12, leg);
		box(front, METAL, -3.5, -12.5, -AXLE - 0.9, 7, 1.2, 1.8);
		if (s.suspension) {
			box(front, TRIM, -3.6, -16, -AXLE - 0.6, 1.2, 3.8, 1.2);
			box(front, TRIM, 2.4, -16, -AXLE - 0.6, 1.2, 3.8, 1.2);
		}
		double top = s.barHeight;
		double half = s.barWidth / 2;
		box(front, METAL, -0.75, -(top - 1), -AXLE - 0.75, 1.5, top - 13, 1.5);
		box(front, TRIM, -half, -top, -AXLE, s.barWidth, 1.2, 1.2);
		box(front, TRIM, -half - 1, -top - 0.3, -AXLE - 0.4, 1.6, 1.8, 2.0);
		box(front, TRIM, half - 0.6, -top - 0.3, -AXLE - 0.4, 1.6, 1.8, 2.0);
		if (s.dropBars) {
			double flare = s.barWidth > 9 ? 1 : 0;
			for (int side = -1; side <= 1; side += 2) {
				double x = side * (half + flare);
				box(front, TRIM, x - 0.6, -top, -AXLE - 3.4, 1.2, 1.2, 3.6);
				box(front, TRIM, x - 0.6, -top, -AXLE - 3.4, 1.2, 5.0, 1.2);
			}
		}

		box(crank, METAL, -3, -0.75, 0.25, 6, 1.5, 1.5);
		box(crank, METAL, 2.5, -0.5, 0.5, 1, 6, 1);
		box(crank, METAL, -3.5, -5.5, 0.5, 1, 6, 1);
		box(pedalRight, TRIM, 3, 5, -0.5, 3, 1, 3);
		box(pedalLeft, TRIM, -6, -6, -0.5, 3, 1, 3);

		StringBuilder sb = new StringBuilder();
		sb.append("{\n\t\"format_version\": \"1.12.0\",\n\t\"minecraft:geometry\": [\n\t\t{\n");
		sb.append("\t\t\t\"description\": {\n\t\t\t\t\"identifier\": \"geometry.voxelvelo.").append(type).append("_bike\",\n");
		sb.append("\t\t\t\t\"texture_width\": 64,\n\t\t\t\t\"texture_height\": 96,\n");
		sb.append("\t\t\t\t\"visible_bounds_width\": 4,\n\t\t\t\t\"visible_bounds_height\": 4,\n\t\t\t\t\"visible_bounds_offset\": [0, 1, 0]\n\t\t\t},\n");
		sb.append("\t\t\t\"bones\": [\n");
		Bone[] all = { frame, rear, front, frontWheel, crank, pedalRight, pedalLeft };
		for (int i = 0; i < all.length; i++) {
			Bone b = all[i];
			sb.append("\t\t\t\t{\n\t\t\t\t\t\"name\": \"").append(b.name).append("\",\n");
			if (b.parent != null) {
				sb.append("\t\t\t\t\t\"parent\": \"").append(b.parent).append("\",\n");
			}
			sb.append("\t\t\t\t\t\"pivot\": ").append(vec(b.pivot[0], b.pivot[1], b.pivot[2])).append(",\n");
			sb.append("\t\t\t\t\t\"cubes\": [\n");
			for (int c = 0; c < b.cubes.size(); c++) {
				sb.append("\t\t\t\t\t\t").append(b.cubes.get(c)).append(c + 1 < b.cubes.size() ? ",\n" : "\n");
			}
			sb.append("\t\t\t\t\t]\n\t\t\t\t}").append(i + 1 < all.length ? ",\n" : "\n");
		}
		sb.append("\t\t\t]\n\t\t}\n\t]\n}\n");
		return sb.toString();
	}

	static final String ANIMATION = """
		{
			"format_version": "1.8.0",
			"animations": {
				"animation.voxelvelo.bicycle.pedal": {
					"loop": true,
					"animation_length": 1.0,
					"bones": {
						"crank": { "rotation": { "0.0": [0, 0, 0], "0.5": [180, 0, 0], "1.0": [360, 0, 0] } },
						"pedal_right": { "rotation": { "0.0": [0, 0, 0], "0.5": [-180, 0, 0], "1.0": [-360, 0, 0] } },
						"pedal_left": { "rotation": { "0.0": [0, 0, 0], "0.5": [-180, 0, 0], "1.0": [-360, 0, 0] } }
					}
				}
			}
		}
		""";

	static void write(File file, String content) throws Exception {
		file.getParentFile().mkdirs();
		try (FileWriter w = new FileWriter(file)) {
			w.write(content);
		}
	}

	public static void main(String[] args) throws Exception {
		File base = new File(args[0], "assets/voxelvelo/geckolib");
		write(new File(base, "models/entity/road_bike.geo.json"), geometry("road", new Spec(1.4, 1.2, 16, 8, true, false)));
		write(new File(base, "models/entity/gravel_bike.geo.json"), geometry("gravel", new Spec(2.2, 1.5, 17, 11, true, false)));
		write(new File(base, "models/entity/mountain_bike.geo.json"), geometry("mountain", new Spec(3.2, 1.9, 19, 17, false, true)));
		write(new File(base, "animations/entity/bicycle.animation.json"), ANIMATION);
	}
}
