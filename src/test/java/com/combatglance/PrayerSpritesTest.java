package com.combatglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

public class PrayerSpritesTest
{
	@Test
	public void mapsProtectFromMeleeToTheOnSprite()
	{
		assertEquals(SpriteID.Prayeron.PROTECT_FROM_MELEE, PrayerSprites.spriteId(Prayer.PROTECT_FROM_MELEE));
	}

	@Test
	public void nullPrayerUsesGenericOrbSprite()
	{
		assertEquals(SpriteID.OrbIcon.PRAYER, PrayerSprites.spriteId(null));
		assertEquals(SpriteID.OrbIcon.PRAYER, PrayerSprites.GENERIC_SPRITE);
	}

	@Test
	public void shortLabelsFitACardSlot()
	{
		assertEquals("Protect", PrayerSprites.shortLabel(Prayer.PROTECT_FROM_MELEE));
		assertEquals("Protect", PrayerSprites.shortLabel(Prayer.PROTECT_FROM_MISSILES));
		assertEquals("Protect", PrayerSprites.shortLabel(Prayer.PROTECT_FROM_MAGIC));
		assertEquals("Piety", PrayerSprites.shortLabel(Prayer.PIETY));
		assertEquals("Rigour", PrayerSprites.shortLabel(Prayer.RIGOUR));
		assertEquals("Augury", PrayerSprites.shortLabel(Prayer.AUGURY));
		assertEquals("Dampen", PrayerSprites.shortLabel(Prayer.RP_DAMPEN_MELEE));
		assertEquals("None", PrayerSprites.shortLabel(null));
		for (Prayer prayer : Prayer.values())
		{
			assertNotNull(PrayerSprites.shortLabel(prayer));
		}
	}
}
