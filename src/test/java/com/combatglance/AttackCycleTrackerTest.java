package com.combatglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class AttackCycleTrackerTest
{
	private AttackCycleTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new AttackCycleTracker();
	}

	@Test
	public void unknownUntilSpeedAndAnchorAreBothSet()
	{
		assertFalse(tracker.isKnown());
		tracker.setWeaponSpeed(4);
		assertFalse(tracker.isKnown());
		tracker.noteEngagement(10, "goblin#1");
		assertTrue(tracker.isKnown());
	}

	@Test
	public void freeRunsFromTheEngagementAnchorAtTheKnownSpeed()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(10, "goblin#1");

		assertEquals(3, tracker.ticksUntilReady(10));
		assertEquals(2, tracker.ticksUntilReady(11));
		assertEquals(0, tracker.ticksUntilReady(13));
		assertEquals(3, tracker.ticksUntilReady(14));
		assertEquals(2, tracker.ticksUntilReady(15));
	}

	@Test
	public void doesNotRequireReconfirmingEveryIndividualAttack()
	{
		// This is the whole point of the redesign: once speed + anchor are known, the cycle
		// keeps counting correctly on its own, even if onAttackObserved is never called again —
		// unlike the old design, which required a fresh, correctly-timed observation every
		// single attack just to keep showing anything.
		tracker.setWeaponSpeed(5);
		tracker.noteEngagement(0, "goblin#1");

		for (int cycle = 0; cycle < 20; cycle++)
		{
			// Last waiting tick of each cycle — the bar should read fully ready right before the
			// next attack fires, not on the (ambiguous) boundary tick shared with the next cycle.
			long tick = (long) cycle * 5 + 4;
			assertEquals("cycle " + cycle, 0, tracker.ticksUntilReady(tick));
		}
	}

	@Test
	public void observedAttackReanchorsToSelfCorrectDrift()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(0, "goblin#1");
		// Real cadence turns out to be 1 tick faster than the estimate (e.g. a style speed
		// modifier) — the observation re-anchors rather than being rejected as "impossible".
		tracker.onAttackObserved(3);

		assertEquals(3, tracker.ticksUntilReady(3));
		assertEquals(2, tracker.ticksUntilReady(4));
	}

	@Test
	public void weaponChangeClearsTheAnchorUntilTheNextEngagement()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(0, "goblin#1");
		assertTrue(tracker.isKnown());

		tracker.setWeaponSpeed(5);
		assertFalse(tracker.isKnown());

		tracker.noteEngagement(10, "goblin#1");
		assertTrue(tracker.isKnown());
		assertEquals(4, tracker.ticksUntilReady(10));
	}

	@Test
	public void sameWeaponSpeedPushedAgainDoesNotResetTheAnchor()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(0, "goblin#1");
		tracker.setWeaponSpeed(4);
		assertEquals(1, tracker.ticksUntilReady(2));
	}

	@Test
	public void targetChangeReanchorsCleanly()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(0, "goblin#1");
		tracker.noteEngagement(2, "goblin#1");
		assertEquals(1, tracker.ticksUntilReady(2));

		tracker.noteEngagement(2, "cow#2");
		assertEquals(3, tracker.ticksUntilReady(2));
	}

	@Test
	public void unknownSpeedHidesReadinessRatherThanGuessing()
	{
		tracker.noteEngagement(0, "goblin#1");
		assertFalse(tracker.isKnown());
		assertEquals(-1, tracker.ticksUntilReady(1));
		assertEquals(-1.0, tracker.elapsedFraction(1), 0.0001);
	}

	@Test
	public void resetOnInactivityClearsSpeedAndAnchor()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(0, "goblin#1");
		tracker.onTick(AttackCycleTracker.INACTIVITY_TIMEOUT_TICKS);

		assertFalse(tracker.isKnown());
	}

	@Test
	public void elapsedFractionTracksProgressThroughTheCycleAndFillsCompletelyOnTheFinalTick()
	{
		tracker.setWeaponSpeed(4);
		tracker.noteEngagement(0, "goblin#1");

		assertEquals(0.25, tracker.elapsedFraction(0), 0.0001);
		assertEquals(0.75, tracker.elapsedFraction(2), 0.0001);
		// Final waiting tick before the next attack fires — bar must read fully filled here, not
		// just short of it.
		assertEquals(1.0, tracker.elapsedFraction(3), 0.0001);
		assertEquals(0.25, tracker.elapsedFraction(4), 0.0001);
	}

	@Test
	public void invalidSpeedIsTreatedAsUnknown()
	{
		tracker.setWeaponSpeed(0);
		tracker.noteEngagement(0, "goblin#1");
		assertFalse(tracker.isKnown());

		tracker.setWeaponSpeed(999);
		assertFalse(tracker.isKnown());
	}
}
