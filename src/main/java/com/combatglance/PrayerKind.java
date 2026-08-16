package com.combatglance;

/**
 * Card-relevant classification of a {@link net.runelite.api.Prayer}.
 */
enum PrayerKind
{
	/** Boosts attack, strength, ranged, or magic — eligible for the offense slot. */
	OFFENSE,
	/** Appears over the player's head and protects/reacts in combat — eligible for the defense slot. */
	OVERHEAD,
	/** Everything else (skin prayers, restores, Protect Item, vows, ...). Never shown on the card. */
	OTHER
}
