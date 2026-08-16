package com.combatglance;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;

/**
 * Loads and caches official OSRS sprites for the card, scaled to a generous glanceable size.
 *
 * <p>Unlike TickFlow's icon cache, this one deliberately upscales from native sprite resolution
 * (OSRS ships skill/prayer sprites at ~25-30px with no larger official variant to request) using
 * nearest-neighbor only, so pixel art stays crisp instead of blurring — see implementation brief
 * §4.7. Cache entries are keyed by (sprite id, target size) so changing {@code overlayScale}
 * simply requests a different cached size rather than stretching an already-rasterized bitmap.
 */
@Singleton
class CombatGlanceIcons
{
	/** Target size for primary slot icons at 100% overlay scale. */
	static final int BASE_ICON_SIZE = 38;

	private final SpriteManager spriteManager;
	private final Map<Long, BufferedImage> cache = new ConcurrentHashMap<>();
	private final Set<Long> pending = ConcurrentHashMap.newKeySet();

	@Inject
	CombatGlanceIcons(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	void clear()
	{
		cache.clear();
		pending.clear();
	}

	@Nullable
	BufferedImage attackIcon(CombatStyle style, int size)
	{
		int spriteId;
		switch (style)
		{
			case RANGED:
				spriteId = SpriteID.Staticons.RANGED;
				break;
			case MAGIC:
				spriteId = SpriteID.Staticons.MAGIC;
				break;
			case MELEE:
			default:
				spriteId = SpriteID.Staticons.ATTACK;
				break;
		}
		return sprite(spriteId, size);
	}

	@Nullable
	BufferedImage prayerIcon(@Nullable Prayer prayer, int size)
	{
		return sprite(PrayerSprites.spriteId(prayer), size);
	}

	@Nullable
	BufferedImage genericPrayerIcon(int size)
	{
		return sprite(PrayerSprites.GENERIC_SPRITE, size);
	}

	@Nullable
	private BufferedImage sprite(int spriteId, int size)
	{
		if (spriteId < 0 || size <= 0)
		{
			return null;
		}
		long key = key(spriteId, size);
		BufferedImage cached = cache.get(key);
		if (cached != null)
		{
			return cached;
		}
		request(spriteId, size, key);
		return cache.get(key);
	}

	private void request(int spriteId, int size, long key)
	{
		if (!pending.add(key))
		{
			return;
		}
		spriteManager.getSpriteAsync(spriteId, 0, image ->
		{
			pending.remove(key);
			if (image == null)
			{
				return;
			}
			cache.put(key, crispScale(image, size));
		});
	}

	private static long key(int spriteId, int size)
	{
		return ((long) spriteId << 20) | (size & 0xFFFFFL);
	}

	/**
	 * Scale to fit a {@code size}x{@code size} box with nearest-neighbor only, upscaling from
	 * native sprite resolution when the source is smaller — intentional here (see class doc).
	 */
	static BufferedImage crispScale(BufferedImage source, int size)
	{
		int sw = source.getWidth();
		int sh = source.getHeight();
		if (sw <= 0 || sh <= 0)
		{
			return source;
		}

		int dw;
		int dh;
		if (sw >= sh)
		{
			dw = size;
			dh = Math.max(1, (int) Math.round(sh * (size / (double) sw)));
		}
		else
		{
			dh = size;
			dw = Math.max(1, (int) Math.round(sw * (size / (double) sh)));
		}

		if (dw == sw && dh == sh && source.getType() == BufferedImage.TYPE_INT_ARGB)
		{
			return source;
		}

		BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
			g.drawImage(source, 0, 0, dw, dh, null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}
}
