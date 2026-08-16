package com.combatglance;

import java.util.Arrays;
import net.runelite.api.gameval.AnimationID;

/**
 * Bounded, hand-curated allowlist of the local player's animation IDs that represent a genuine
 * attack swing/shot/cast — melee, ranged, and magic combined, since {@link AttackCycleTracker}
 * only needs "was that an attack" (re-anchor the cycle), not which combat style it was (the
 * style cell already gets that from {@link CombatStyleResolver}).
 *
 * <p>Same philosophy as {@link PrayerClassifier}: an explicit, documented list beats a guess.
 * The naive {@code animation > 0} check this replaced fired on eating, fletching, emotes, and
 * every other idle animation while in combat, which is what made the old attack-timer bar
 * re-anchor on the wrong ticks. This list is not exhaustive — it deliberately covers the common
 * weapon categories (generic melee stances, bows/crossbows/blowpipe/thrown ammo, standard-book
 * spellcasting) rather than every special-attack and boss-specific animation in the game, mirroring
 * the scope of the reference implementation this was checked against (ngraves95/attacktimer,
 * {@code AnimationData.java}). A missed animation just means one fewer re-anchor opportunity —
 * {@link AttackCycleTracker}'s free-running cycle keeps the bar showing regardless (see its
 * class javadoc), so under-covering here is safe; the list only needs to be broad enough that
 * most attacks get a fresh, precise re-anchor.
 */
final class AttackAnimations
{
	private static final int[] IDS;

	static
	{
		int[] ids = {
			// Melee — generic weapon-stance animations shared across the vast majority of
			// melee weapons (scimitars, swords, axes, maces, spears, whips, staves used to
			// melee, unarmed).
			AnimationID.HUMAN_UNARMEDPUNCH,
			AnimationID.HUMAN_UNARMEDKICK,
			AnimationID.HUMAN_SWORD_SLASH,
			AnimationID.HUMAN_SWORD_STAB,
			AnimationID.HUMAN_AXE_CHOP,
			AnimationID.HUMAN_AXE_HACK,
			AnimationID.HUMAN_BLUNT_POUND,
			AnimationID.HUMAN_BLUNT_SPIKE,
			AnimationID.HUMAN_DHSWORD_SLASH,
			AnimationID.HUMAN_DHSWORD_CHOP,
			AnimationID.HUMAN_DDAGGER_LUNGE,
			AnimationID.HUMAN_KNIFE_CHOP,
			AnimationID.HUMAN_KNIFE_SLASH,
			AnimationID.HUMAN_SPEAR_SPIKE,
			AnimationID.HUMAN_SPEAR_LUNGE,
			AnimationID.HUMAN_SCYTHE_SWEEP,
			AnimationID.HUMAN_STAFF_PUMMEL,
			AnimationID.HUMAN_STAFFORB_PUMMEL,
			AnimationID.HUMAN_CROSSBOW,
			AnimationID.SLAYER_ABYSSAL_WHIP_ATTACK,

			// Ranged — bows, crossbows, blowpipe, thrown ammo, ballista.
			AnimationID.HUMAN_BOW,
			AnimationID.XBOWS_HUMAN_FIRE_AND_RELOAD,
			AnimationID.XBOWS_HUMAN_FIRE_AND_RELOAD_PVN,
			AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK,
			AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK_ORNAMENT,
			AnimationID.HUMAN_STAKE2,
			AnimationID.HUMAN_STAKE2_PVN,
			AnimationID.II_HUMAN_DART_THROW,
			AnimationID.II_HUMAN_DART_THROW_PVN,
			AnimationID.BALLISTA_ATTACK,
			AnimationID.BALLISTA_ATTACK_PVN,

			// Magic — standard spellbook combat casts (god spells, strike/bolt/blast, wave, surge),
			// both bare-handed and staff variants.
			AnimationID.HUMAN_CASTING,
			AnimationID.HUMAN_CASTSTRIKE,
			AnimationID.HUMAN_CASTSTRIKE_STAFF,
			AnimationID.HUMAN_CASTSTRIKE_WALKMERGE,
			AnimationID.HUMAN_CASTSTRIKE_STAFF_WALKMERGE,
			AnimationID.HUMAN_CASTWAVE,
			AnimationID.HUMAN_CASTWAVE_STAFF,
			AnimationID.HUMAN_CASTWAVE_WALKMERGE,
			AnimationID.HUMAN_CASTWAVE_STAFF_WALKMERGE,
			AnimationID.HUMAN_CAST_SURGE,
			AnimationID.HUMAN_CAST_SURGE_WALKMERGE,
		};
		Arrays.sort(ids);
		IDS = ids;
	}

	private AttackAnimations()
	{
	}

	static boolean isAttack(int animationId)
	{
		return animationId >= 0 && Arrays.binarySearch(IDS, animationId) >= 0;
	}
}
