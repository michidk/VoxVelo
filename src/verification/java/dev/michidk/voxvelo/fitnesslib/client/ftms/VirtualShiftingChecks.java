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
		require(lowPower > 8 && lowPower < 15, "gear 1 at 90 rpm should demand about 11 W, got " + lowPower);
		require(highPower > 1250 && highPower < 1500, "gear 12 at 90 rpm should demand about 1.4 kW, got " + highPower);
		for (VirtualShifting.GearRange range : VirtualShifting.GearRange.values()) {
			near(VirtualShifting.ratio(1, range), range.lowest);
			near(VirtualShifting.ratio(12, range), range.highest);
			double step = VirtualShifting.ratio(2, range) / VirtualShifting.ratio(1, range);
			for (int gear = 2; gear <= 12; gear++) {
				near(VirtualShifting.ratio(gear, range) / VirtualShifting.ratio(gear - 1, range), step);
			}
		}
		near(VirtualShifting.ratio(0, null), VirtualShifting.GearRange.DEFAULT.lowest);
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
