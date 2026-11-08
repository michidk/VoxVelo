package dev.michidk.voxvelo.fitnesslib.client.ftms;

/** Checks road power against measured trainer power without Bluetooth or a running game. */
public final class VirtualShiftingChecks {
	public static void main(String[] args) {
		TrainerSimulation.Target flat = new TrainerSimulation.Target(0, 0.004);
		for (double grade : new double[] {0, 5, 12}) {
			TrainerSimulation.Target road = new TrainerSimulation.Target(grade, 0.004);
			double matchingSpeed = VirtualShifting.virtualSpeed(90, VirtualShifting.ratio(6), 2.1);
			near(target(road, 6, 90, matchingSpeed).gradePercent(), grade);
			double previous = -11;
			for (int gear = 1; gear <= 12; gear++) {
				double actual = target(road, gear, 90, 10).gradePercent();
				require(actual >= previous, "higher gear must not reduce uphill/flat load");
				previous = actual;
			}
		}
		double lowPower = power(target(flat, 1, 90, 10), 10);
		double highPower = power(target(flat, 12, 90, 10), 10);
		require(lowPower > 15 && lowPower < 25, "gear 1 at 90 rpm should demand about 18 W, got " + lowPower);
		require(highPower > 500 && highPower < 600, "gear 12 at 90 rpm should demand about 550 W, got " + highPower);
		double gameAir = power(VirtualShifting.targetRatio(flat, VirtualShifting.ratio(9), 90.0, 36.0, 2.1, 85, 0.51, 0.30), 10);
		double trainerAir = power(VirtualShifting.targetRatio(flat, VirtualShifting.ratio(9), 90.0, 36.0, 2.1, 85, 0.51, 0), 10);
		require(gameAir < trainerAir, "a slipperier vehicle (CdA 0.30, Cw 0.37) must demand less than the trainer default Cw 0.51");
		near(VirtualShifting.targetRatio(flat, VirtualShifting.ratio(9), 90.0, 36.0, 2.1, 85, 0.51, Double.NaN).gradePercent(),
			VirtualShifting.targetRatio(flat, VirtualShifting.ratio(9), 90.0, 36.0, 2.1, 85, 0.51, 0).gradePercent());
		for (VirtualShifting.GearRange range : VirtualShifting.GearRange.values()) {
			near(VirtualShifting.ratio(1, range), range.lowest);
			near(VirtualShifting.ratio(12, range), range.highest);
			double step = VirtualShifting.ratio(2, range) / VirtualShifting.ratio(1, range);
			for (int gear = 2; gear <= 12; gear++) {
				near(VirtualShifting.ratio(gear, range) / VirtualShifting.ratio(gear - 1, range), step);
			}
		}
		near(VirtualShifting.ratio(0, null), VirtualShifting.GearRange.COMPACT.lowest);
		near(VirtualShifting.ratio(99, VirtualShifting.GearRange.WIDE), 6.5);
		for (int gear : new int[] {1, 6, 9}) {
			for (double speed : new double[] {6, 8, 12}) {
				near(power(target(flat, gear, 90, speed), speed), power(target(flat, gear, 90, 10), 10));
			}
		}
		require(power(target(flat, 6, 100, 10), 10) > power(target(flat, 6, 60, 10), 10), "cadence must set road power");
		for (int gear = 1; gear <= 12; gear++) {
			for (double speed : new double[] {3, 10, 15}) {
				TrainerSimulation.Target downhill = target(new TrainerSimulation.Target(-10, 0.004), gear, 90, speed);
				require(downhill.gradePercent() < 0, "steep descent must never become a climb at 90 rpm");
				near(power(downhill, speed), 0);
			}
		}
		for (Double cadence : new Double[] {null, Double.NaN, -1.0, Double.POSITIVE_INFINITY}) {
			require(VirtualShifting.target(flat, 12, cadence, 36.0, 2.1, 85, 0.51).equals(flat), "missing cadence uses plain road target");
		}
		for (Double speed : new Double[] {null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
			require(VirtualShifting.target(flat, 12, 90.0, speed, 2.1, 85, 0.51).equals(flat), "missing speed must not be inferred from a physical gear");
		}
		near(power(target(flat, 12, 0, 10), 10), 0);
		near(VirtualShifting.virtualSpeed(80, 2.5, 2.1), 7);
		TrainerSimulation.Target invalid = VirtualShifting.target(new TrainerSimulation.Target(Double.NaN, Double.NaN), 999,
			90.0, 36.0, Double.NaN, Double.NaN, Double.NaN);
		require(Double.isFinite(invalid.gradePercent()), "invalid settings must stay finite");
		TrainerSimulation.Target high = target(new TrainerSimulation.Target(15, 0.0255), 12, 250, 3);
		require(high.gradePercent() == 15, "grade limit");
		byte[] bytes = TrainerSimulation.encode(new TrainerSimulation.Target(-1.25, 0.004));
		require(bytes.length == 7 && bytes[0] == 0x11 && bytes[3] == (byte) 0x83 && bytes[4] == (byte) 0xff
			&& bytes[5] == 40 && bytes[6] == 51, "FTMS signed grade and field widths");
		// The grade low-pass filter: a time constant of 0 passes measurements on, a steady slope comes through by
		// about two thirds after one time constant, and a single block bump (a few ticks of slope) is mostly lost.
		require(TrainerFeedback.smooth(0, 10, 0, 0.05) == 10, "no smoothing passes the measurement on");
		double steady = 0;
		for (int tick = 0; tick < 30; tick++) steady = TrainerFeedback.smooth(steady, 10, 1.5, 0.05);
		require(steady > 6.0 && steady < 6.6, "two thirds of a steady slope after one time constant, got " + steady);
		double bump = 0;
		for (int tick = 0; tick < 4; tick++) bump = TrainerFeedback.smooth(bump, 10, 1.5, 0.05);
		require(bump < 1.3, "a four tick bump of ten percent is mostly filtered, got " + bump);
		for (int tick = 0; tick < 40; tick++) bump = TrainerFeedback.smooth(bump, 0, 1.5, 0.05);
		require(bump < 0.4, "the filtered bump dies away");
		require(TrainerFeedback.smooth(0, 10, Double.NaN, 0.05) == 10, "an invalid time constant does not stall the filter");
		System.out.println("Virtual shifting checks passed");
	}

	private static TrainerSimulation.Target target(TrainerSimulation.Target road, int gear, double cadence, double speed) {
		return VirtualShifting.target(road, gear, cadence, speed * 3.6, 2.1, 85, 0.51);
	}

	private static double power(TrainerSimulation.Target target, double speed) {
		double slope = target.gradePercent() / 100;
		return (85 * 9.81 * (slope + target.crr()) / Math.sqrt(1 + slope * slope) + 0.5 * 0.51 * speed * speed) * speed;
	}

	private static void near(double actual, double expected) {
		require(Math.abs(actual - expected) < 1e-7, "expected " + expected + ", got " + actual);
	}

	private static void require(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
