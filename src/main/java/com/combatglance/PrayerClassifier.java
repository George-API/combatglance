package com.combatglance;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Prayer;

/**
 * Pure, allowlisted mapping from {@link Prayer} to {@link PrayerKind}, plus deterministic
 * "which one goes on the card" selection when several prayers of the same kind are active.
 *
 * <p>Classification is a fixed allowlist, never a name/string match: a future prayer the API
 * adds that isn't listed here classifies as {@link PrayerKind#OTHER} and stays off the card
 * (see overview §10 — explicit allowlist, not a fuzzy guess).
 */
final class PrayerClassifier
{
	private static final Map<Prayer, PrayerKind> KIND = new EnumMap<>(Prayer.class);

	static
	{
		// OVERHEAD — standard book.
		KIND.put(Prayer.PROTECT_FROM_MAGIC, PrayerKind.OVERHEAD);
		KIND.put(Prayer.PROTECT_FROM_MISSILES, PrayerKind.OVERHEAD);
		KIND.put(Prayer.PROTECT_FROM_MELEE, PrayerKind.OVERHEAD);
		KIND.put(Prayer.RETRIBUTION, PrayerKind.OVERHEAD);
		KIND.put(Prayer.REDEMPTION, PrayerKind.OVERHEAD);
		KIND.put(Prayer.SMITE, PrayerKind.OVERHEAD);

		// OVERHEAD — Ruinous Powers.
		KIND.put(Prayer.RP_DAMPEN_MAGIC, PrayerKind.OVERHEAD);
		KIND.put(Prayer.RP_DAMPEN_RANGED, PrayerKind.OVERHEAD);
		KIND.put(Prayer.RP_DAMPEN_MELEE, PrayerKind.OVERHEAD);
		KIND.put(Prayer.RP_WRATH, PrayerKind.OVERHEAD);

		// OFFENSE — combined / high-tier.
		KIND.put(Prayer.PIETY, PrayerKind.OFFENSE);
		KIND.put(Prayer.CHIVALRY, PrayerKind.OFFENSE);
		KIND.put(Prayer.RIGOUR, PrayerKind.OFFENSE);
		KIND.put(Prayer.AUGURY, PrayerKind.OFFENSE);
		KIND.put(Prayer.DEADEYE, PrayerKind.OFFENSE);
		KIND.put(Prayer.MYSTIC_VIGOUR, PrayerKind.OFFENSE);

		// OFFENSE — single-skill style prayers.
		KIND.put(Prayer.BURST_OF_STRENGTH, PrayerKind.OFFENSE);
		KIND.put(Prayer.SUPERHUMAN_STRENGTH, PrayerKind.OFFENSE);
		KIND.put(Prayer.ULTIMATE_STRENGTH, PrayerKind.OFFENSE);
		KIND.put(Prayer.CLARITY_OF_THOUGHT, PrayerKind.OFFENSE);
		KIND.put(Prayer.IMPROVED_REFLEXES, PrayerKind.OFFENSE);
		KIND.put(Prayer.INCREDIBLE_REFLEXES, PrayerKind.OFFENSE);
		KIND.put(Prayer.SHARP_EYE, PrayerKind.OFFENSE);
		KIND.put(Prayer.HAWK_EYE, PrayerKind.OFFENSE);
		KIND.put(Prayer.EAGLE_EYE, PrayerKind.OFFENSE);
		KIND.put(Prayer.MYSTIC_WILL, PrayerKind.OFFENSE);
		KIND.put(Prayer.MYSTIC_LORE, PrayerKind.OFFENSE);
		KIND.put(Prayer.MYSTIC_MIGHT, PrayerKind.OFFENSE);

		// OFFENSE — Ruinous Powers. Style-priority chains below only cover the standard book;
		// these classify as OFFENSE but fall through to the enum-order tie-break (step 4), since
		// Ruinous Powers replaces the standard book entirely and can never be active alongside it
		// in real play — an explicit style pairing here would be an unverified guess, not a fact.
		KIND.put(Prayer.RP_ANCIENT_STRENGTH, PrayerKind.OFFENSE);
		KIND.put(Prayer.RP_ANCIENT_SIGHT, PrayerKind.OFFENSE);
		KIND.put(Prayer.RP_ANCIENT_WILL, PrayerKind.OFFENSE);
		KIND.put(Prayer.RP_DECIMATE, PrayerKind.OFFENSE);
		KIND.put(Prayer.RP_ANNIHILATE, PrayerKind.OFFENSE);
		KIND.put(Prayer.RP_VAPORISE, PrayerKind.OFFENSE);

		// Everything else defaults to OTHER (see kindOf) — skin prayers, restores, Protect Item,
		// vows, and any prayer the API adds that isn't explicitly listed above.
	}

	/** Style-specific priority chain, highest preference first. Standard book only — see above. */
	private static final Prayer[] MELEE_PRIORITY = {
		Prayer.PIETY, Prayer.CHIVALRY,
		Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES,
		Prayer.SUPERHUMAN_STRENGTH, Prayer.IMPROVED_REFLEXES,
		Prayer.BURST_OF_STRENGTH, Prayer.CLARITY_OF_THOUGHT,
	};
	private static final Prayer[] RANGED_PRIORITY = {
		Prayer.RIGOUR, Prayer.DEADEYE, Prayer.EAGLE_EYE, Prayer.HAWK_EYE, Prayer.SHARP_EYE,
	};
	private static final Prayer[] MAGIC_PRIORITY = {
		Prayer.AUGURY, Prayer.MYSTIC_VIGOUR, Prayer.MYSTIC_MIGHT, Prayer.MYSTIC_LORE, Prayer.MYSTIC_WILL,
	};
	/** Step 2: any remaining combined prayer, checked regardless of current style. */
	private static final Prayer[] COMBINED_FALLBACK = {
		Prayer.PIETY, Prayer.CHIVALRY, Prayer.RIGOUR, Prayer.AUGURY,
	};

	private PrayerClassifier()
	{
	}

	static PrayerKind kindOf(Prayer prayer)
	{
		PrayerKind kind = KIND.get(prayer);
		return kind != null ? kind : PrayerKind.OTHER;
	}

	/**
	 * Deterministic primary offensive prayer when several {@link PrayerKind#OFFENSE} prayers are
	 * active at once. Priority (documented in the overview/implementation brief):
	 * <ol>
	 *   <li>The combined/style-matching prayer chain for the current {@link CombatStyle}</li>
	 *   <li>Any remaining combined prayer (Piety / Chivalry / Rigour / Augury)</li>
	 *   <li>(covered by 1 above for the active style) any style-matching single-skill prayer</li>
	 *   <li>Any other active OFFENSE prayer, stable enum-order tie-break</li>
	 * </ol>
	 */
	@Nullable
	static Prayer primaryOffense(Set<Prayer> active, CombatStyle style)
	{
		if (active.isEmpty())
		{
			return null;
		}

		Prayer[] styleChain = styleChain(style);
		for (Prayer candidate : styleChain)
		{
			if (active.contains(candidate))
			{
				return candidate;
			}
		}
		for (Prayer candidate : COMBINED_FALLBACK)
		{
			if (active.contains(candidate))
			{
				return candidate;
			}
		}
		for (Prayer prayer : Prayer.values())
		{
			if (active.contains(prayer) && kindOf(prayer) == PrayerKind.OFFENSE)
			{
				return prayer;
			}
		}
		return null;
	}

	/**
	 * The single active overhead prayer, or null. Only one overhead can be active in normal
	 * play; if the client somehow reports more than one, pick by stable enum order.
	 */
	@Nullable
	static Prayer primaryOverhead(Set<Prayer> active)
	{
		for (Prayer prayer : Prayer.values())
		{
			if (active.contains(prayer) && kindOf(prayer) == PrayerKind.OVERHEAD)
			{
				return prayer;
			}
		}
		return null;
	}

	private static Prayer[] styleChain(CombatStyle style)
	{
		if (style == CombatStyle.RANGED)
		{
			return RANGED_PRIORITY;
		}
		if (style == CombatStyle.MAGIC)
		{
			return MAGIC_PRIORITY;
		}
		return MELEE_PRIORITY;
	}

	/**
	 * The single {@link CombatStyle} an OFFENSE prayer is designed for, or null when there is no
	 * verified single-style pairing. Used only for the optional, off-by-default mismatch
	 * highlight (overview §5.3/§7.2 exception) — never for prayer selection itself.
	 *
	 * <p>Ruinous Powers offense prayers deliberately return null here, for the same reason they
	 * are absent from the style-priority chains above: no verified style pairing, so they are
	 * never flagged as mismatched rather than risk a wrong guess.
	 */
	@Nullable
	static CombatStyle styleOf(Prayer prayer)
	{
		switch (prayer)
		{
			case BURST_OF_STRENGTH:
			case SUPERHUMAN_STRENGTH:
			case ULTIMATE_STRENGTH:
			case CLARITY_OF_THOUGHT:
			case IMPROVED_REFLEXES:
			case INCREDIBLE_REFLEXES:
			case CHIVALRY:
			case PIETY:
				return CombatStyle.MELEE;
			case SHARP_EYE:
			case HAWK_EYE:
			case EAGLE_EYE:
			case DEADEYE:
			case RIGOUR:
				return CombatStyle.RANGED;
			case MYSTIC_WILL:
			case MYSTIC_LORE:
			case MYSTIC_MIGHT:
			case MYSTIC_VIGOUR:
			case AUGURY:
				return CombatStyle.MAGIC;
			default:
				return null;
		}
	}
}
