package com.combatglance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Owns the current session snapshot. Client-thread confined: {@link #refresh} and
 * {@link #reset} must only be called from RuneLite event handlers on the client thread.
 * The overlay must only read {@link #snapshot()} — never mutate state from render.
 */
final class CombatGlanceState
{
	private static final Prayer[] PRAYERS = Prayer.values();
	/** Grace window for "hide outside combat": keep showing the card briefly after the last
	 *  interacting target, so it doesn't flicker off between kills or a brief reposition. */
	private static final long COMBAT_GRACE_MS = 10_000L;

	private volatile CombatGlanceSnapshot snapshot = CombatGlanceSnapshot.loggedOut();
	@Nullable
	private volatile String lastResetReason;
	private volatile long lastCombatMillis;

	private final AttackCycleTracker attackCycleTracker = new AttackCycleTracker();
	private long tickIndex = -1;

	CombatGlanceSnapshot snapshot()
	{
		return snapshot;
	}

	/** Diagnostic only — why the last reset happened (e.g. "game-state:HOPPING"). */
	@Nullable
	String getLastResetReason()
	{
		return lastResetReason;
	}

	/** Call once per {@code GameTick}, before {@link #refresh}. Advances the attack-cycle clock. */
	void onGameTick()
	{
		tickIndex++;
		attackCycleTracker.onTick(tickIndex);
	}

	/**
	 * Best-effort attack observation for the optional attack-timer bar — re-anchors the cycle to
	 * self-correct drift when it arrives. Safe to call more than once per tick or not at all;
	 * see {@link AttackCycleTracker} for why this is corroboration, not the sole trigger.
	 */
	void onAttackObserved()
	{
		if (tickIndex >= 0)
		{
			attackCycleTracker.onAttackObserved(tickIndex);
		}
	}

	/** Apply the equipped weapon's attack speed, or clear it when unknown/unequipped. */
	void onWeaponSpeedResolved(int aspeed)
	{
		attackCycleTracker.setWeaponSpeed(aspeed);
	}

	/**
	 * Sample current client state and replace the published snapshot. No-op-safe to call
	 * often — GameTick as a backstop plus varbit/equipment nudges for snappier updates.
	 */
	void refresh(Client client, boolean captureDebugActive)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			reset("not-logged-in");
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			reset("no-local-player");
			return;
		}

		CombatStyle style = CombatStyleResolver.resolve(client);
		Set<Prayer> active = sampleActivePrayers(client);
		Prayer offense = PrayerClassifier.primaryOffense(active, style);
		Prayer overhead = PrayerClassifier.primaryOverhead(active);

		int weaponCategory = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		List<Prayer> debugActive = captureDebugActive
			? Collections.unmodifiableList(new ArrayList<>(active))
			: Collections.emptyList();

		Actor interacting = local.getInteracting();
		String interactingId = targetIdentity(interacting);
		if (interactingId != null)
		{
			attackCycleTracker.noteEngagement(tickIndex, interactingId);
		}
		else
		{
			attackCycleTracker.noteDisengaged();
		}

		long now = System.currentTimeMillis();
		if (interacting != null)
		{
			lastCombatMillis = now;
		}
		boolean recentlyInCombat = lastCombatMillis != 0L && (now - lastCombatMillis) <= COMBAT_GRACE_MS;

		int ticksUntilAttackReady = tickIndex >= 0 ? attackCycleTracker.ticksUntilReady(tickIndex) : -1;
		double attackCycleFraction = tickIndex >= 0 ? attackCycleTracker.elapsedFraction(tickIndex) : -1;

		snapshot = new CombatGlanceSnapshot(
			true, style, offense, overhead, weaponCategory, styleIndex, debugActive, recentlyInCombat,
			ticksUntilAttackReady, attackCycleFraction);
	}

	void reset(String reason)
	{
		lastResetReason = reason;
		lastCombatMillis = 0L;
		attackCycleTracker.reset();
		snapshot = CombatGlanceSnapshot.loggedOut();
	}

	@Nullable
	private static String targetIdentity(@Nullable Actor interacting)
	{
		if (interacting == null)
		{
			return null;
		}
		String name = interacting.getName();
		return (name == null ? "actor" : name) + "#" + System.identityHashCode(interacting);
	}

	private static Set<Prayer> sampleActivePrayers(Client client)
	{
		EnumSet<Prayer> active = EnumSet.noneOf(Prayer.class);
		for (Prayer prayer : PRAYERS)
		{
			if (isReallyActive(client, prayer))
			{
				active.add(prayer);
			}
		}
		return active;
	}

	/**
	 * {@code Client#isPrayerActive} is deprecated: per its own javadoc it "does not properly
	 * handle deadeye/eagle eye or mystic vigour/might", since those tier pairs share the same
	 * underlying prayer-book slot once the higher tier is unlocked. Mirrors the exact workaround
	 * core RuneLite's Prayer plugin uses ({@code PrayerType#isEnabled}) so the offense slot never
	 * shows the wrong tier once a player has unlocked Deadeye or Mystic Vigour.
	 */
	@SuppressWarnings("deprecation")
	private static boolean isReallyActive(Client client, Prayer prayer)
	{
		if (!client.isPrayerActive(prayer))
		{
			return false;
		}
		if (prayer == Prayer.EAGLE_EYE && isUnlocked(client, VarbitID.PRAYER_DEADEYE_UNLOCKED))
		{
			return false;
		}
		if (prayer == Prayer.MYSTIC_MIGHT && isUnlocked(client, VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED))
		{
			return false;
		}
		return true;
	}

	private static boolean isUnlocked(Client client, int unlockVarbit)
	{
		boolean inLms = client.getVarbitValue(VarbitID.BR_INGAME) != 0;
		boolean unlocked = client.getVarbitValue(unlockVarbit) != 0;
		return unlocked && !inLms;
	}
}
