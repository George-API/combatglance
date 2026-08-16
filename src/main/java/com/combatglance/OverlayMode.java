package com.combatglance;

/**
 * Card layout — see overview §6. Only two modes; do not add a third in MVP.
 *
 * <p>Must be {@code public}: it's the return type of a {@code @ConfigItem} getter on
 * {@link CombatGlanceConfig}, and RuneLite's config system backs that interface with a dynamic
 * proxy in a different module at runtime — a package-private enum here throws
 * {@link IllegalAccessError} on every call (and since {@code mode()} is read from
 * {@link CombatGlanceOverlay#render}, that means every render frame).
 */
public enum OverlayMode
{
	/** Section headers, generous labels — the noob-friendly default. */
	LEARN,
	/** Same slots and order, no headers, labels optional via config. */
	COMPACT
}
