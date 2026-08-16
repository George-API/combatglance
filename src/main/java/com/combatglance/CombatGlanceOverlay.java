package com.combatglance;

import java.awt.Color;
import java.awt.Composite;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.api.Prayer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * RuneLite-native movable card: DEFENSE (overhead-prayer cell) on top, OFFENSE (attack-type
 * cell + offensive-prayer cell) below, left-aligned under the style cell so slot positions
 * never shift — see overview §5/§7.7 and implementation brief §9.
 */
class CombatGlanceOverlay extends Overlay
{
	// No panel backing and no outer border — just the cells themselves, for a minimal look.
	private static final Color HEADER_TEXT = new Color(150, 154, 158);
	private static final Color LABEL_TEXT = new Color(210, 212, 215);
	private static final Color LABEL_TEXT_DIM = new Color(130, 133, 136);
	private static final Color CELL_BORDER = new Color(70, 74, 80);
	private static final Color CELL_FILL = new Color(28, 30, 33, 220);
	private static final Color DEBUG_TEXT = new Color(150, 154, 158);

	private static final Color ACCENT_MELEE = new Color(200, 120, 70);
	private static final Color ACCENT_RANGED = new Color(90, 180, 100);
	private static final Color ACCENT_MAGIC = new Color(90, 140, 220);
	private static final Color FILL_MELEE = new Color(58, 44, 38, 225);
	private static final Color FILL_RANGED = new Color(38, 56, 44, 225);
	private static final Color FILL_MAGIC = new Color(38, 50, 66, 225);
	private static final Color MISMATCH_BORDER = new Color(210, 70, 60);

	// Same fill visual for both progress bars (tick-track bar and attack-timer bar): amber-to-mint
	// gradient reveal, dark track, white leading-edge marker. Matches TickFlow's square-mode tick
	// pulse (drawTickPulse).
	private static final Color PULSE_TRACK = new Color(40, 44, 48);
	private static final Color PULSE_MARKER = new Color(255, 255, 255);
	private static final Color PULSE_START = new Color(210, 175, 85);
	private static final Color PULSE_END = new Color(0, 220, 165);
	private static final int BASE_PULSE_H = 10;
	private static final int BASE_ATTACK_BAR_H = 6;

	private static final AlphaComposite DIM_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f);

	private static final int BASE_PAD = 8;
	private static final int BASE_GAP = 8;
	private static final int BASE_CELL_PAD = 6;
	private static final int BASE_LABEL_H = 14;
	private static final int BASE_HEADER_H = 14;
	private static final int BASE_SECTION_GAP = 6;
	private static final int BASE_DEBUG_LINE_H = 12;

	private final CombatGlancePlugin plugin;
	private final CombatGlanceConfig config;
	private final CombatGlanceIcons icons;

	@Inject
	private CombatGlanceOverlay(CombatGlancePlugin plugin, CombatGlanceConfig config, CombatGlanceIcons icons)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.icons = icons;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Combat Glance overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enabledOverlay())
		{
			return null;
		}
		CombatGlanceSnapshot snap = plugin.getSnapshot();
		if (snap == null || !snap.isLoggedIn())
		{
			return null;
		}
		if (config.autoHideOutsideCombat() && !snap.isRecentlyInCombat())
		{
			return null;
		}

		boolean learn = config.mode() == OverlayMode.LEARN;
		boolean showLabels = config.showLabels();
		boolean debug = config.debugMode();
		int scalePct = clampScale(config.overlayScale());

		int iconSize = scale(CombatGlanceIcons.BASE_ICON_SIZE, scalePct);
		int pad = scale(BASE_PAD, scalePct);
		int gap = scale(BASE_GAP, scalePct);
		int cellPad = scale(BASE_CELL_PAD, scalePct);
		int labelH = showLabels ? scale(BASE_LABEL_H, scalePct) : 0;
		int headerH = learn ? scale(BASE_HEADER_H, scalePct) : 0;
		int sectionGap = scale(BASE_SECTION_GAP, scalePct);
		int debugLineH = scale(BASE_DEBUG_LINE_H, scalePct);
		int pulseH = scale(BASE_PULSE_H, scalePct);
		int attackBarH = scale(BASE_ATTACK_BAR_H, scalePct);

		int cellSize = iconSize + cellPad * 2;
		int cellHeight = cellSize + labelH;
		int contentW = cellSize * 2 + gap;

		List<String> debugLines = debug ? buildDebugLines(snap) : List.of();
		int debugH = debugLines.isEmpty() ? 0 : (sectionGap + debugLines.size() * debugLineH);

		int width = contentW + pad * 2;
		int height = pad + headerH + cellHeight + sectionGap + headerH + cellHeight + debugH + pad;

		configureCrispGraphics(graphics);

		Font headerFont = FontManager.getRunescapeSmallFont();
		Font labelFont = FontManager.getRunescapeSmallFont();

		int y = pad;
		int styleX = pad;
		int offenseX = pad + cellSize + gap;

		if (learn)
		{
			drawSectionHeader(graphics, headerFont, "DEFENSE", styleX, y);
			y += headerH;
		}

		Prayer overhead = snap.getOverheadPrayer();
		BufferedImage overheadIcon = overhead != null
			? icons.prayerIcon(overhead, iconSize)
			: icons.genericPrayerIcon(iconSize);
		drawCell(graphics, labelFont, styleX, y, cellSize, iconSize, labelH, showLabels,
			overheadIcon, PrayerSprites.shortLabel(overhead), null, null, overhead == null);

		if (config.showTickPulse())
		{
			int pulseY = y + (cellSize - pulseH) / 2;
			drawBar(graphics, offenseX, pulseY, cellSize, pulseH, plugin.getTickProgress());
		}

		y += cellHeight + sectionGap;

		if (learn)
		{
			drawSectionHeader(graphics, headerFont, "OFFENSE", styleX, y);
			y += headerH;
		}

		BufferedImage styleIcon = icons.attackIcon(snap.getCombatStyle(), iconSize);
		drawCell(graphics, labelFont, styleX, y, cellSize, iconSize, labelH, showLabels,
			styleIcon, styleLabel(snap.getCombatStyle()), accentBorder(snap.getCombatStyle()), accentFill(snap.getCombatStyle()), false);
		if (config.showAttackTimer() && snap.isAttackTimerKnown())
		{
			int barInset = Math.max(2, cellPad / 2);
			int barY = y + cellSize - attackBarH - barInset;
			double progress = snap.getTicksUntilAttackReady() == 0 ? 1.0 : snap.getAttackCycleFraction();
			drawBar(graphics, styleX + barInset, barY, cellSize - barInset * 2, attackBarH, progress);
		}

		Prayer offense = snap.getOffensivePrayer();
		BufferedImage offenseIcon = offense != null
			? icons.prayerIcon(offense, iconSize)
			: icons.genericPrayerIcon(iconSize);
		// The offense cell's family color always matches the prayer's own style (e.g. Rigour is
		// always green), independent of whether it matches the current attack style — that's a
		// separate, opt-in signal (below). Same accent palette as the attack-type cell so a
		// matching prayer+style pair reads as "the same color twice" at a glance.
		CombatStyle offenseStyle = offense != null ? PrayerClassifier.styleOf(offense) : null;
		Color offenseBorder = offenseStyle != null ? accentBorder(offenseStyle) : null;
		Color offenseFill = offenseStyle != null ? accentFill(offenseStyle) : null;
		if (config.highlightPrayerMismatch() && snap.isOffenseStyleMismatch())
		{
			offenseBorder = MISMATCH_BORDER;
		}
		drawCell(graphics, labelFont, offenseX, y, cellSize, iconSize, labelH, showLabels,
			offenseIcon, PrayerSprites.shortLabel(offense), offenseBorder, offenseFill, offense == null);

		y += cellHeight;

		if (!debugLines.isEmpty())
		{
			y += sectionGap;
			graphics.setFont(headerFont);
			graphics.setColor(DEBUG_TEXT);
			FontMetrics fm = graphics.getFontMetrics();
			for (String line : debugLines)
			{
				graphics.drawString(line, pad, y + fm.getAscent());
				y += debugLineH;
			}
		}

		return new Dimension(width, height);
	}

	private void drawSectionHeader(Graphics2D g, Font font, String text, int x, int y)
	{
		g.setFont(font);
		g.setColor(HEADER_TEXT);
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, x, y + fm.getAscent());
	}

	private void drawCell(
		Graphics2D g,
		Font labelFont,
		int x,
		int y,
		int cellSize,
		int iconSize,
		int labelH,
		boolean showLabels,
		@Nullable BufferedImage icon,
		String label,
		@Nullable Color border,
		@Nullable Color fill,
		boolean dimmed)
	{
		g.setColor(fill != null ? fill : CELL_FILL);
		g.fillRect(x, y, cellSize, cellSize);
		g.setColor(border != null ? border : CELL_BORDER);
		g.drawRect(x, y, cellSize - 1, cellSize - 1);

		if (icon != null)
		{
			int ix = x + (cellSize - icon.getWidth()) / 2;
			int iy = y + (cellSize - icon.getHeight()) / 2;
			if (dimmed)
			{
				Composite old = g.getComposite();
				g.setComposite(DIM_COMPOSITE);
				g.drawImage(icon, ix, iy, null);
				g.setComposite(old);
			}
			else
			{
				g.drawImage(icon, ix, iy, null);
			}
		}
		else
		{
			// Sprite still loading asynchronously (e.g. right after a scale change) — brief
			// text fallback so the cell is never blank.
			g.setFont(FontManager.getRunescapeSmallFont());
			g.setColor(dimmed ? LABEL_TEXT_DIM : LABEL_TEXT);
			FontMetrics fm = g.getFontMetrics();
			String glyph = label.isEmpty() ? "?" : label.substring(0, Math.min(3, label.length()));
			int gx = x + Math.max(0, (cellSize - fm.stringWidth(glyph)) / 2);
			int gy = y + (cellSize + fm.getAscent()) / 2;
			g.drawString(glyph, gx, gy);
		}

		if (showLabels)
		{
			g.setFont(labelFont);
			g.setColor(dimmed ? LABEL_TEXT_DIM : LABEL_TEXT);
			FontMetrics fm = g.getFontMetrics();
			int lx = x + Math.max(0, (cellSize - fm.stringWidth(label)) / 2);
			int ly = y + cellSize + fm.getAscent();
			g.drawString(label, lx, ly);
		}
	}

	/**
	 * Horizontal progress bar — shared visual for both the tick-track bar
	 * ({@link CombatGlanceConfig#showTickPulse}, a generic tick heartbeat) and the attack-timer
	 * bar ({@link CombatGlanceConfig#showAttackTimer}, opt-in and only drawn once the caller has
	 * confirmed {@link CombatGlanceSnapshot#isAttackTimerKnown()} — never a guessed number).
	 * Matches TickFlow's square-mode tick pulse ({@code drawTickPulse}) exactly: dark track,
	 * amber-to-mint gradient reveal, white leading-edge marker.
	 */
	private void drawBar(Graphics2D g, int x, int y, int width, int height, double progress)
	{
		int trackH = Math.max(4, height - 2);
		int trackY = y + (height - trackH) / 2;
		int innerX = x + 1;
		int innerW = width - 2;
		int innerY = trackY + 1;
		int innerH = trackH - 2;

		g.setColor(PULSE_TRACK);
		g.fillRect(x, trackY, width, trackH);
		g.setColor(CELL_BORDER);
		g.drawRect(x, trackY, width - 1, trackH - 1);

		double p = Math.max(0, Math.min(1, progress));
		int fillW = (int) Math.round(innerW * p);
		if (fillW > 0 && innerW > 0)
		{
			Paint oldPaint = g.getPaint();
			g.setPaint(new GradientPaint(innerX, innerY, PULSE_START, innerX + innerW, innerY, PULSE_END));
			g.fillRect(innerX, innerY, fillW, innerH);
			g.setPaint(oldPaint);
		}

		int markerX = innerX + Math.max(0, fillW - 1);
		g.setColor(PULSE_MARKER);
		g.fillRect(markerX, trackY - 1, 2, trackH + 2);
	}

	private List<String> buildDebugLines(CombatGlanceSnapshot snap)
	{
		StringBuilder active = new StringBuilder();
		for (Prayer prayer : snap.getDebugActivePrayers())
		{
			if (active.length() > 0)
			{
				active.append(", ");
			}
			active.append(prayer.name());
		}
		return List.of(
			"Style: " + snap.getCombatStyle(),
			"Weapon category: " + snap.getDebugWeaponCategory(),
			"COM_MODE: " + snap.getDebugStyleIndex(),
			"Offense: " + nullSafe(snap.getOffensivePrayer()),
			"Overhead: " + nullSafe(snap.getOverheadPrayer()),
			"Active: " + (active.length() == 0 ? "-" : active),
			"Attack timer: " + (snap.isAttackTimerKnown() ? snap.getTicksUntilAttackReady() + " ticks" : "unknown")
		);
	}

	private static String nullSafe(@Nullable Prayer prayer)
	{
		return prayer == null ? "-" : prayer.name();
	}

	private static String styleLabel(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return "Ranged";
			case MAGIC:
				return "Magic";
			case MELEE:
			default:
				return "Melee";
		}
	}

	private static Color accentBorder(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return ACCENT_RANGED;
			case MAGIC:
				return ACCENT_MAGIC;
			case MELEE:
			default:
				return ACCENT_MELEE;
		}
	}

	private static Color accentFill(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return FILL_RANGED;
			case MAGIC:
				return FILL_MAGIC;
			case MELEE:
			default:
				return FILL_MELEE;
		}
	}

	private static void configureCrispGraphics(Graphics2D graphics)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
	}

	private static int clampScale(int scalePct)
	{
		if (scalePct < 80)
		{
			return 80;
		}
		if (scalePct > 140)
		{
			return 140;
		}
		return scalePct;
	}

	private static int scale(int base, int scalePct)
	{
		return Math.max(1, (base * scalePct + 50) / 100);
	}
}
