package com.combatglance;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Prayer;

/**
 * Immutable render model consumed only by {@link CombatGlanceOverlay}. Built by
 * {@link CombatGlanceState}; never mutated after construction.
 */
final class CombatGlanceSnapshot
{
	private static final CombatGlanceSnapshot LOGGED_OUT = new CombatGlanceSnapshot(
		false, CombatStyle.MELEE, null, null, -1, -1, Collections.emptyList(), false, -1, -1);

	private final boolean loggedIn;
	private final CombatStyle combatStyle;
	@Nullable
	private final Prayer offensivePrayer;
	@Nullable
	private final Prayer overheadPrayer;
	private final int debugWeaponCategory;
	private final int debugStyleIndex;
	private final List<Prayer> debugActivePrayers;
	private final boolean recentlyInCombat;
	private final int ticksUntilAttackReady;
	private final double attackCycleFraction;

	CombatGlanceSnapshot(
		boolean loggedIn,
		CombatStyle combatStyle,
		@Nullable Prayer offensivePrayer,
		@Nullable Prayer overheadPrayer,
		int debugWeaponCategory,
		int debugStyleIndex,
		List<Prayer> debugActivePrayers,
		boolean recentlyInCombat,
		int ticksUntilAttackReady,
		double attackCycleFraction)
	{
		this.loggedIn = loggedIn;
		this.combatStyle = combatStyle;
		this.offensivePrayer = offensivePrayer;
		this.overheadPrayer = overheadPrayer;
		this.debugWeaponCategory = debugWeaponCategory;
		this.debugStyleIndex = debugStyleIndex;
		this.debugActivePrayers = debugActivePrayers;
		this.recentlyInCombat = recentlyInCombat;
		this.ticksUntilAttackReady = ticksUntilAttackReady;
		this.attackCycleFraction = attackCycleFraction;
	}

	static CombatGlanceSnapshot loggedOut()
	{
		return LOGGED_OUT;
	}

	boolean isLoggedIn()
	{
		return loggedIn;
	}

	CombatStyle getCombatStyle()
	{
		return combatStyle;
	}

	@Nullable
	Prayer getOffensivePrayer()
	{
		return offensivePrayer;
	}

	@Nullable
	Prayer getOverheadPrayer()
	{
		return overheadPrayer;
	}

	int getDebugWeaponCategory()
	{
		return debugWeaponCategory;
	}

	int getDebugStyleIndex()
	{
		return debugStyleIndex;
	}

	/** Unmodifiable; empty unless debug diagnostics requested the full active-prayer sample. */
	List<Prayer> getDebugActivePrayers()
	{
		return debugActivePrayers;
	}

	/** True if the player had an interacting target within the auto-hide grace window. */
	boolean isRecentlyInCombat()
	{
		return recentlyInCombat;
	}

	/**
	 * True when the active offensive prayer is designed for a different {@link CombatStyle} than
	 * the current one (e.g. Rigour while meleeing). Derived, not sampled — purely a function of
	 * the other fields. Rendering this is opt-in and off by default; see
	 * {@link PrayerClassifier#styleOf}.
	 */
	boolean isOffenseStyleMismatch()
	{
		if (offensivePrayer == null)
		{
			return false;
		}
		CombatStyle prayerStyle = PrayerClassifier.styleOf(offensivePrayer);
		return prayerStyle != null && prayerStyle != combatStyle;
	}

	/**
	 * True when the attack-timer bar (opt-in, off by default) has enough confidently observed
	 * cadence to render. False rather than a guess when unknown — see {@link AttackCycleTracker}.
	 */
	boolean isAttackTimerKnown()
	{
		return ticksUntilAttackReady >= 0 && attackCycleFraction >= 0;
	}

	/** Ticks remaining until the next attack is ready. Only meaningful when {@link #isAttackTimerKnown()}. */
	int getTicksUntilAttackReady()
	{
		return ticksUntilAttackReady;
	}

	/** Fraction of the current attack cycle elapsed, in [0, 1]. Only meaningful when {@link #isAttackTimerKnown()}. */
	double getAttackCycleFraction()
	{
		return attackCycleFraction;
	}
}
