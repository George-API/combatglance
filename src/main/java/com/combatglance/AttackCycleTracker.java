package com.combatglance;

import javax.annotation.Nullable;

/**
 * Attack-cycle model powering the optional, off-by-default attack-timer bar (overview §5.2/§9.4,
 * implementation brief §4.2/§9.3).
 *
 * <p><b>Design (rewritten after QA — read this before changing the logic again):</b> the first
 * version required a fresh, correctly-timed observation of every single attack to keep the bar
 * showing, ported from TickFlow's rolling-timeline tracker. That's the wrong shape for this
 * plugin: animation events don't reliably fire once per attack (many weapons don't return to an
 * idle animation between swings, so {@code AnimationChanged} simply doesn't re-fire), and a
 * single observation that didn't match the previous prediction nuked all learned state back to
 * "unknown" — so in practice the bar stayed hidden for most of a fight.
 *
 * <p>This version needs an attack-speed value and exactly one anchor point (when combat with the
 * current target began), then free-runs the cycle every {@code speedTicks} ticks from there —
 * pure tick arithmetic, no drift, no per-attack reconfirmation required. An observed attack
 * (best-effort, animation-based) re-anchors to self-correct any phase error when it does arrive,
 * but a mistimed or missed observation never discards the known weapon speed and never hides the
 * bar. Precision is intentionally traded for reliability: this is "roughly which part of the
 * swing/cast/cooldown am I in", not a tick-perfect timeline (that's TickFlow).
 */
final class AttackCycleTracker
{
	static final int MIN_SPEED_TICKS = 1;
	static final int MAX_SPEED_TICKS = 10;
	static final int INACTIVITY_TIMEOUT_TICKS = 25;

	private int speedTicks = -1;
	private long anchorTick = -1;
	private long lastActivityTick = -1;
	@Nullable
	private String targetIdentity;

	/**
	 * Apply the equipped weapon's attack speed, or clear it when unknown/out of a sane range.
	 * A genuine change re-anchors on the next engagement rather than carrying over a cycle phase
	 * that belonged to a different weapon's cadence.
	 */
	void setWeaponSpeed(int aspeed)
	{
		int resolved = (aspeed >= MIN_SPEED_TICKS && aspeed <= MAX_SPEED_TICKS) ? aspeed : -1;
		if (resolved != speedTicks)
		{
			speedTicks = resolved;
			anchorTick = -1;
		}
	}

	/**
	 * The player has an interacting target this tick. Establishes an anchor the first tick this
	 * becomes true, and re-anchors cleanly on a target change; otherwise a cheap no-op so it's
	 * safe to call on every refresh, not just once per tick.
	 */
	void noteEngagement(long tickIndex, String target)
	{
		if (!target.equals(targetIdentity))
		{
			targetIdentity = target;
			anchorTick = tickIndex;
		}
		else if (anchorTick < 0)
		{
			anchorTick = tickIndex;
		}
		lastActivityTick = tickIndex;
	}

	/** No interacting target this tick — stop tracking a target identity, but keep the last
	 *  known cadence and anchor until the inactivity timeout actually clears them. */
	void noteDisengaged()
	{
		targetIdentity = null;
	}

	/**
	 * Best-effort attack observation (animation-based; see class javadoc for why this is
	 * corroboration only, not the sole source of truth). Re-anchors to the observed tick when a
	 * speed is already known; otherwise ignored — never invents timing from nothing.
	 */
	void onAttackObserved(long tickIndex)
	{
		if (speedTicks <= 0)
		{
			return;
		}
		anchorTick = tickIndex;
		lastActivityTick = tickIndex;
	}

	/** @return {@code true} if the tracker was cleared due to inactivity */
	boolean onTick(long tickIndex)
	{
		if (lastActivityTick >= 0 && tickIndex - lastActivityTick >= INACTIVITY_TIMEOUT_TICKS)
		{
			anchorTick = -1;
			lastActivityTick = -1;
			targetIdentity = null;
			return true;
		}
		return false;
	}

	boolean isKnown()
	{
		return speedTicks > 0 && anchorTick >= 0;
	}

	/**
	 * Ticks remaining until ready, or -1 when not known — never a guess. 0 means ready now, i.e.
	 * this is the last tick of the wait — matches {@link #elapsedFraction} reading 1.0 (full bar)
	 * on that same tick; see that method's javadoc for why the current tick counts as fully
	 * elapsed rather than just-started.
	 */
	int ticksUntilReady(long tickIndex)
	{
		if (!isKnown())
		{
			return -1;
		}
		long intoCycle = Math.floorMod(tickIndex - anchorTick, speedTicks);
		return (int) (speedTicks - 1 - intoCycle);
	}

	/**
	 * Fraction of the current cycle elapsed, in {@code (0, 1]}, for the bar sweep. -1 when
	 * unknown. The tick a game tick is currently on counts as fully elapsed for that whole tick's
	 * ~0.6s render duration — i.e. {@code intoCycle} is 1-indexed, not 0-indexed — so the last
	 * waiting tick reads 1.0 (bar completely full) for its entire duration rather than only for a
	 * single boundary instant that's immediately overwritten by the next re-anchor. A 0-indexed
	 * fraction (the first version of this method) tops out at {@code (speedTicks-1)/speedTicks}
	 * and never visibly reaches the end of the bar — exactly the "ends just before the end of the
	 * bar" bug this was rewritten to fix.
	 */
	double elapsedFraction(long tickIndex)
	{
		if (!isKnown())
		{
			return -1;
		}
		long intoCycle = Math.floorMod(tickIndex - anchorTick, speedTicks);
		return (intoCycle + 1) / (double) speedTicks;
	}

	/** Full reset — logout, hop, death, plugin disable. Clears the known weapon speed too. */
	void reset()
	{
		speedTicks = -1;
		anchorTick = -1;
		lastActivityTick = -1;
		targetIdentity = null;
	}
}
