package com.combatglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CombatStyleTest
{
	@Test
	public void mapsStyleNamesToFamilies()
	{
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Accurate"));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Aggressive"));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Controlled"));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Defensive"));
		assertEquals(CombatStyle.RANGED, CombatStyle.fromAttackStyleName("Ranging"));
		assertEquals(CombatStyle.RANGED, CombatStyle.fromAttackStyleName("Longrange"));
		assertEquals(CombatStyle.MAGIC, CombatStyle.fromAttackStyleName("Casting"));
		assertEquals(CombatStyle.MAGIC, CombatStyle.fromAttackStyleName("Defensive casting"));
	}

	@Test
	public void unknownOrMissingNameFallsBackToMelee()
	{
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName(null));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName(""));
		assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackStyleName("Some Future Style"));
	}

	@Test
	public void nameMatchingIsCaseAndWhitespaceTolerant()
	{
		assertEquals(CombatStyle.RANGED, CombatStyle.fromAttackStyleName("  ranging  "));
		assertEquals(CombatStyle.MAGIC, CombatStyle.fromAttackStyleName("DEFENSIVE CASTING"));
	}
}
