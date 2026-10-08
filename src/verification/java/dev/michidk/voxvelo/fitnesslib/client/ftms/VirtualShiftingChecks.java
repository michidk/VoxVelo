package dev.michidk.voxvelo.fitnesslib.client.ftms;

/** Regression checks for physical behavior and the trainer wire command; no Bluetooth needed. */
public final class VirtualShiftingChecks {
	public static void main(String[] args) {
		for (double grade : new double[] {0, 5, 12}) {
			TrainerSimulation.Target road = new TrainerSimulation.Target(grade, 0.004);
			near(target(road, 6, 8).gradePercent(), grade);
			double previous = -11;
			for (int gear = 1; gear <= 12; gear++) {
				double actual = target(road, gear, 8).gradePercent();
				require(actual >= previous, "higher gear must not reduce uphill/flat load");
				previous = actual;
			}
		}
		TrainerSimulation.Target flat = new TrainerSimulation.Target(0, 0.004);
		require(target(flat, 12, 10).gradePercent() > target(flat, 12, 2).gradePercent(), "aero load must grow with speed");
		for (int gear = 1; gear <= 12; gear++) {
			TrainerSimulation.Target downhill = target(new TrainerSimulation.Target(-10, 0.004), gear, 3);
			double slope = downhill.gradePercent() / 100;
			double force = 85 * 9.81 * (slope + downhill.crr()) / Math.sqrt(1 + slope * slope) + 0.5 * 0.51 * 9;
			require(force >= -1e-7, "downhill must not request motor assistance");
		}
		near(VirtualShifting.speed(null, 80.0, 2.5, 2.1), 7);
		near(VirtualShifting.speed(36.0, 80.0, 2.5, 2.1), 10);
		near(VirtualShifting.speed(null, null, 2.5, 2.1), 0);
		TrainerSimulation.Target invalid = VirtualShifting.target(new TrainerSimulation.Target(Double.NaN, Double.NaN), 999,
			Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN);
		require(Double.isFinite(invalid.gradePercent()), "invalid telemetry must stay finite");
		TrainerSimulation.Target high = target(new TrainerSimulation.Target(15, 0.0255), 12, 30);
		require(high.gradePercent() <= 15 && high.gradePercent() >= -10, "grade limit");
		byte[] bytes = TrainerSimulation.encode(new TrainerSimulation.Target(-1.25, 0.004));
		require(bytes.length == 7 && bytes[0] == 0x11 && bytes[3] == (byte) 0x83 && bytes[4] == (byte) 0xff
			&& bytes[5] == 40 && bytes[6] == 51, "FTMS signed grade and field widths");
		System.out.println("Virtual shifting checks passed");
	}

	private static TrainerSimulation.Target target(TrainerSimulation.Target road, int gear, double speed) {
		return VirtualShifting.target(road, gear, 2.5, speed, 85, 0.51);
	}
	private static void near(double actual, double expected) {
		require(Math.abs(actual - expected) < 1e-7, "expected " + expected + ", got " + actual);
	}
	private static void require(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
