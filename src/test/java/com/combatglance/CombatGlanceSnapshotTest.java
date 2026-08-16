package com.combatglance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import net.runelite.api.Prayer;
import org.junit.Test;

public class CombatGlanceSnapshotTest
{
	@Test
	public void noMismatchWhenOffenseSlotIsEmpty()
	{
		CombatGlanceSnapshot snap = snapshot(CombatStyle.MELEE, null);
		assertFalse(snap.isOffenseStyleMismatch());
	}

	@Test
	public void noMismatchWhenOffenseMatchesStyle()
	{
		CombatGlanceSnapshot snap = snapshot(CombatStyle.MELEE, Prayer.PIETY);
		assertFalse(snap.isOffenseStyleMismatch());
	}

	@Test
	public void mismatchWhenOffenseIsForADifferentStyle()
	{
		CombatGlanceSnapshot snap = snapshot(CombatStyle.MELEE, Prayer.RIGOUR);
		assertTrue(snap.isOffenseStyleMismatch());
	}

	@Test
	public void ruinousOffensePrayersAreNeverFlaggedMismatched()
	{
		CombatGlanceSnapshot snap = snapshot(CombatStyle.MELEE, Prayer.RP_DECIMATE);
		assertFalse(snap.isOffenseStyleMismatch());
	}

	@Test
	public void attackTimerUnknownByDefault()
	{
		CombatGlanceSnapshot snap = snapshot(CombatStyle.MELEE, null);
		assertFalse(snap.isAttackTimerKnown());
	}

	@Test
	public void attackTimerKnownWhenBothFieldsAreNonNegative()
	{
		CombatGlanceSnapshot snap = new CombatGlanceSnapshot(
			true, CombatStyle.MELEE, null, null, -1, -1, Collections.emptyList(), false, 2, 0.5);
		assertTrue(snap.isAttackTimerKnown());
	}

	private static CombatGlanceSnapshot snapshot(CombatStyle style, Prayer offense)
	{
		return new CombatGlanceSnapshot(
			true, style, offense, null, -1, -1, Collections.emptyList(), false, -1, -1);
	}
}
