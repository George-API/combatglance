package com.combatglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Prayer;
import org.junit.Test;

public class PrayerClassifierTest
{
	@Test
	public void combinedPrayersAreOffense()
	{
		assertEquals(PrayerKind.OFFENSE, PrayerClassifier.kindOf(Prayer.PIETY));
		assertEquals(PrayerKind.OFFENSE, PrayerClassifier.kindOf(Prayer.RIGOUR));
		assertEquals(PrayerKind.OFFENSE, PrayerClassifier.kindOf(Prayer.AUGURY));
	}

	@Test
	public void standardProtectPrayersAreOverhead()
	{
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.PROTECT_FROM_MELEE));
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.PROTECT_FROM_MISSILES));
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.PROTECT_FROM_MAGIC));
	}

	@Test
	public void ruinousDampenAndWrathAreOverhead()
	{
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.RP_DAMPEN_MAGIC));
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.RP_DAMPEN_RANGED));
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.RP_DAMPEN_MELEE));
		assertEquals(PrayerKind.OVERHEAD, PrayerClassifier.kindOf(Prayer.RP_WRATH));
	}

	@Test
	public void utilityAndSkinPrayersAreOther()
	{
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.THICK_SKIN));
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.ROCK_SKIN));
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.STEEL_SKIN));
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.RAPID_RESTORE));
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.RAPID_HEAL));
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.PROTECT_ITEM));
		assertEquals(PrayerKind.OTHER, PrayerClassifier.kindOf(Prayer.PRESERVE));
	}

	@Test
	public void primaryOffensePrefersCombinedOverSingleSkill()
	{
		Set<Prayer> active = EnumSet.of(Prayer.PIETY, Prayer.ULTIMATE_STRENGTH);
		assertEquals(Prayer.PIETY, PrayerClassifier.primaryOffense(active, CombatStyle.MELEE));
	}

	@Test
	public void primaryOffenseWithoutCombinedIsDeterministic()
	{
		Set<Prayer> active = EnumSet.of(Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES);
		Prayer first = PrayerClassifier.primaryOffense(active, CombatStyle.MELEE);
		Prayer second = PrayerClassifier.primaryOffense(active, CombatStyle.MELEE);
		assertEquals(first, second);
		assertEquals(Prayer.ULTIMATE_STRENGTH, first);
	}

	@Test
	public void primaryOffensePrefersRigourForRangedStyleEvenIfMeleePrayerActive()
	{
		Set<Prayer> active = EnumSet.of(Prayer.PIETY, Prayer.RIGOUR);
		assertEquals(Prayer.RIGOUR, PrayerClassifier.primaryOffense(active, CombatStyle.RANGED));
	}

	@Test
	public void primaryOffenseFallsBackToAnyCombinedWhenStyleDoesNotMatch()
	{
		// Only a melee combined prayer is active while the style is ranged — no ranged
		// candidate exists, so step 2 (any remaining combined prayer) picks it up.
		Set<Prayer> active = EnumSet.of(Prayer.PIETY);
		assertEquals(Prayer.PIETY, PrayerClassifier.primaryOffense(active, CombatStyle.RANGED));
	}

	@Test
	public void noActiveOffenseReturnsNull()
	{
		assertNull(PrayerClassifier.primaryOffense(EnumSet.noneOf(Prayer.class), CombatStyle.MELEE));
	}

	@Test
	public void noActiveOverheadReturnsNull()
	{
		assertNull(PrayerClassifier.primaryOverhead(EnumSet.noneOf(Prayer.class)));
	}

	@Test
	public void primaryOverheadPicksTheSingleActiveOne()
	{
		Set<Prayer> active = EnumSet.of(Prayer.PROTECT_FROM_MELEE);
		assertEquals(Prayer.PROTECT_FROM_MELEE, PrayerClassifier.primaryOverhead(active));
	}

	@Test
	public void offenseSelectionIgnoresOtherAndOverheadPrayers()
	{
		Set<Prayer> active = EnumSet.of(Prayer.PROTECT_FROM_MELEE, Prayer.STEEL_SKIN, Prayer.PROTECT_ITEM);
		assertNull(PrayerClassifier.primaryOffense(active, CombatStyle.MELEE));
	}

	@Test
	public void styleOfMapsStandardOffensePrayersToOneStyle()
	{
		assertEquals(CombatStyle.MELEE, PrayerClassifier.styleOf(Prayer.PIETY));
		assertEquals(CombatStyle.MELEE, PrayerClassifier.styleOf(Prayer.ULTIMATE_STRENGTH));
		assertEquals(CombatStyle.RANGED, PrayerClassifier.styleOf(Prayer.RIGOUR));
		assertEquals(CombatStyle.RANGED, PrayerClassifier.styleOf(Prayer.EAGLE_EYE));
		assertEquals(CombatStyle.MAGIC, PrayerClassifier.styleOf(Prayer.AUGURY));
		assertEquals(CombatStyle.MAGIC, PrayerClassifier.styleOf(Prayer.MYSTIC_MIGHT));
	}

	@Test
	public void styleOfReturnsNullForRuinousAndNonOffensePrayers()
	{
		// No verified style pairing for Ruinous Powers offense prayers — never flagged.
		assertNull(PrayerClassifier.styleOf(Prayer.RP_DECIMATE));
		assertNull(PrayerClassifier.styleOf(Prayer.RP_ANCIENT_SIGHT));
		assertNull(PrayerClassifier.styleOf(Prayer.STEEL_SKIN));
		assertNull(PrayerClassifier.styleOf(Prayer.PROTECT_FROM_MELEE));
	}
}
