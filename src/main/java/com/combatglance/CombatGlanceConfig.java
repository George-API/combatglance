package com.combatglance;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(CombatGlanceConfig.GROUP)
public interface CombatGlanceConfig extends Config
{
	String GROUP = "combatglance";

	@ConfigItem(
		keyName = "enabledOverlay",
		name = "Enable overlay",
		description = "Show the Combat Glance card",
		position = 0
	)
	default boolean enabledOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mode",
		name = "Overlay mode",
		description = "Learn: section headers and labels. Compact: same slots, no headers.",
		position = 1
	)
	default OverlayMode mode()
	{
		return OverlayMode.LEARN;
	}

	@ConfigItem(
		keyName = "showLabels",
		name = "Show text labels",
		description = "Show short text labels under each icon (Melee, Piety, Protect, None)",
		position = 2
	)
	default boolean showLabels()
	{
		return true;
	}

	@Range(min = 80, max = 140)
	@ConfigItem(
		keyName = "overlayScale",
		name = "Overlay scale %",
		description = "Scale the card size",
		position = 3
	)
	default int overlayScale()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "autoHideOutsideCombat",
		name = "Hide outside combat",
		description = "Hide the card when not recently in combat",
		position = 4
	)
	default boolean autoHideOutsideCombat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugMode",
		name = "Debug diagnostics",
		description = "Show raw prayer names, weapon category, and style index (off by default)",
		position = 5
	)
	default boolean debugMode()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightPrayerMismatch",
		name = "Highlight offense/style mismatch",
		description = "Off by default. Tint the offensive-prayer cell's border when the active "
			+ "offensive prayer doesn't match the current attack style (e.g. Rigour while "
			+ "meleeing). Purely observational — not a correctness judgement.",
		position = 6
	)
	default boolean highlightPrayerMismatch()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showAttackTimer",
		name = "Show attack timer bar",
		description = "Off by default. Horizontal bar under the attack-style icon showing your "
			+ "swing/cast/cooldown progress, same fill visual as the tick track bar. Best-effort: "
			+ "hidden whenever the cadence isn't confidently known, never a guess. A light taste "
			+ "of TickFlow's cooldown HUD — for a full tick timeline, use TickFlow instead.",
		position = 7
	)
	default boolean showAttackTimer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTickPulse",
		name = "Show tick track bar",
		description = "Off by default. Horizontal amber-to-mint bar next to the overhead-prayer "
			+ "cell showing live progress through the current game tick — a generic tick "
			+ "heartbeat, not attack-specific. Same fill visual as TickFlow's square-mode tick "
			+ "pulse.",
		position = 8
	)
	default boolean showTickPulse()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tickSound",
		name = "Tick sound",
		description = "Off by default. Play a soft metronome blip each game tick — same sound as "
			+ "TickFlow's tick sound. Unlike TickFlow, there are no mute/volume buttons on the "
			+ "overlay itself; this setting and the volume below are the only controls.",
		position = 9
	)
	default boolean tickSound()
	{
		return false;
	}

	@Range(min = 5, max = 100)
	@ConfigItem(
		keyName = "tickSoundVolume",
		name = "Tick sound volume",
		description = "Metronome volume (kept quiet by default)",
		position = 10
	)
	default int tickSoundVolume()
	{
		return 40;
	}
}
