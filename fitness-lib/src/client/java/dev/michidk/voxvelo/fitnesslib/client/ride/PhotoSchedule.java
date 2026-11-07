package dev.michidk.voxvelo.fitnesslib.client.ride;

/** Counts active riding time without catching up photos after stops or menus. */
public final class PhotoSchedule {
	private long last = -1;
	private long active;
	private boolean previouslyEligible;

	public boolean update(long now, boolean enabled, boolean eligible, int intervalSeconds) {
		long elapsed = last < 0 ? 0 : Math.max(0, Math.min(1000, now - last));
		last = now;
		boolean count = previouslyEligible && eligible;
		previouslyEligible = eligible;
		if (!enabled) { active = 0; return false; }
		if (!count) return false;
		active += elapsed;
		if (active < Math.max(30, Math.min(3600, intervalSeconds)) * 1000L) return false;
		active = 0;
		return true;
	}

	public void reset() { last = -1; active = 0; previouslyEligible = false; }
}
