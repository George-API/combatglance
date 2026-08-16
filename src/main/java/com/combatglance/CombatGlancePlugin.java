package com.combatglance;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Combat Glance — passive PvM readout of current attack type, offensive prayer, and overhead
 * prayer. Observation only: never clicks, prays, or recommends anything (see product overview).
 *
 * <p>Event mapping:
 * <ul>
 *   <li>{@link GameTick} — stamps the tick-start time for the optional tick-track bar, advances
 *       the attack-cycle tick clock, then a periodic full refresh; the authoritative backstop</li>
 *   <li>{@link VarbitChanged} — immediate refresh on weapon-category / style / autocast /
 *       any individual prayer varbit, so overhead flicks and style swaps don't wait a tick</li>
 *   <li>{@link ItemContainerChanged} (worn equipment) — immediate refresh after a weapon swap,
 *       plus re-resolving weapon attack speed for the optional attack-timer bar</li>
 *   <li>{@link GameStateChanged} — reset/hide on login screen, hopping, connection loss,
 *       loading; refresh once loading finishes into {@code LOGGED_IN}</li>
 *   <li>{@link ActorDeath} (local player) — refresh; prayers deactivate on death, so this just
 *       reflects that promptly rather than waiting for the next tick</li>
 *   <li>{@link AnimationChanged} (local player) — best-effort attack observation for the
 *       optional, off-by-default attack-timer bar only (corroboration/drift-correction, not the
 *       sole trigger — see {@link AttackCycleTracker}); never affects the offense/defense
 *       prayer or style slots</li>
 *   <li>{@link ConfigChanged} — when {@code showAttackTimer} turns on, immediately resolves the
 *       currently equipped weapon's attack speed rather than waiting for the next weapon swap
 *       (otherwise the bar could stay hidden all session if toggled on with a weapon already
 *       equipped from before); also starts/stops/adjusts the optional tick sound</li>
 * </ul>
 *
 * <p>Optional tick sound: same {@link TickMetronome} sound-generation logic as TickFlow, but
 * unlike TickFlow there are no mute/volume buttons drawn on the overlay — {@code tickSound} and
 * {@code tickSoundVolume} are config-panel-only settings, so the card stays a passive readout.
 */
@Slf4j
@PluginDescriptor(
	name = "Combat Glance",
	description = "Glanceable attack type, offensive prayer, and overhead prayer for PvM.",
	tags = {"pvm", "combat", "prayer", "overlay", "melee", "ranged", "magic"}
)
public class CombatGlancePlugin extends Plugin
{
	private static final Set<GameState> RESET_STATES;
	/**
	 * Sorted, checked via {@link Arrays#binarySearch}, not a {@code Set<Integer>}: {@code
	 * VarbitChanged} fires very frequently for varbits that have nothing to do with this plugin
	 * (minimap state, run energy, interface widgets, ...), and a boxed-Integer HashSet lookup
	 * would allocate on every single one of those just to say "no". A sorted primitive array
	 * avoids that entirely.
	 */
	private static final int[] PRAYER_VARBITS;

	static
	{
		RESET_STATES = new HashSet<>();
		RESET_STATES.add(GameState.LOGIN_SCREEN);
		RESET_STATES.add(GameState.LOGIN_SCREEN_AUTHENTICATOR);
		RESET_STATES.add(GameState.LOGGING_IN);
		RESET_STATES.add(GameState.HOPPING);
		RESET_STATES.add(GameState.CONNECTION_LOST);
		RESET_STATES.add(GameState.LOADING);

		Prayer[] prayers = Prayer.values();
		PRAYER_VARBITS = new int[prayers.length];
		for (int i = 0; i < prayers.length; i++)
		{
			PRAYER_VARBITS[i] = prayers[i].getVarbit();
		}
		Arrays.sort(PRAYER_VARBITS);
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CombatGlanceConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CombatGlanceOverlay overlay;

	@Inject
	private CombatGlanceIcons icons;

	@Inject
	private ItemManager itemManager;

	@Inject
	private TickMetronome metronome;

	private final CombatGlanceState state = new CombatGlanceState();

	private int cachedWeaponId = -1;
	private volatile long lastGameTickMs;

	@Provides
	CombatGlanceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CombatGlanceConfig.class);
	}

	@Override
	protected void startUp()
	{
		state.reset("startup");
		resetEquipmentCache();
		overlayManager.add(overlay);
		metronome.setVolumePercent(config.tickSoundVolume());
		if (config.tickSound())
		{
			metronome.start();
		}
		// Plugin list toggles startUp on the EDT — never touch the client there.
		clientThread.invoke(() ->
		{
			refreshIfLoggedIn();
			refreshEquipmentCache();
		});
		log.debug("Combat Glance started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		icons.clear();
		state.reset("shutdown");
		resetEquipmentCache();
		lastGameTickMs = 0L;
		metronome.stop();
		log.debug("Combat Glance stopped");
	}

	CombatGlanceSnapshot getSnapshot()
	{
		return state.snapshot();
	}

	/**
	 * Progress through the current game tick in {@code [0, 1]}, for the optional tick-track bar
	 * (§9.4 — a generic tick heartbeat, unrelated to attack readiness). Safe to call from render.
	 */
	double getTickProgress()
	{
		if (lastGameTickMs <= 0L)
		{
			return 0;
		}
		long elapsed = System.currentTimeMillis() - lastGameTickMs;
		if (elapsed <= 0)
		{
			return 0;
		}
		if (elapsed >= Constants.GAME_TICK_LENGTH)
		{
			return 1;
		}
		return elapsed / (double) Constants.GAME_TICK_LENGTH;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		lastGameTickMs = System.currentTimeMillis();
		if (config.tickSound())
		{
			metronome.play();
		}
		state.onGameTick();
		refreshIfLoggedIn();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() == VarPlayerID.COM_MODE
			|| event.getVarbitId() == VarbitID.COMBAT_WEAPON_CATEGORY
			|| event.getVarbitId() == VarbitID.AUTOCAST_DEFMODE
			|| Arrays.binarySearch(PRAYER_VARBITS, event.getVarbitId()) >= 0)
		{
			refreshIfLoggedIn();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			refreshEquipmentCache();
			refreshIfLoggedIn();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (RESET_STATES.contains(gameState))
		{
			state.reset("game-state:" + gameState);
			resetEquipmentCache();
			lastGameTickMs = 0L;
		}
		else if (gameState == GameState.LOGGED_IN)
		{
			refreshEquipmentCache();
			refreshIfLoggedIn();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player local = client.getLocalPlayer();
		if (local != null && event.getActor() == local)
		{
			// Prayers deactivate on death; refresh promptly rather than waiting for the next tick.
			refreshIfLoggedIn();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CombatGlanceConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("showAttackTimer".equals(event.getKey()))
		{
			// Weapon attack speed is normally only (re-)resolved on an actual weapon swap. If the
			// player enables this toggle mid-session with a weapon already equipped since before —
			// the common case — nothing would ever push that weapon's known speed into the
			// tracker until the next swap. Resolve it immediately instead of waiting.
			if (config.showAttackTimer())
			{
				clientThread.invoke(() -> state.onWeaponSpeedResolved(resolveWeaponAspeed(cachedWeaponId)));
			}
		}
		else if ("tickSound".equals(event.getKey()))
		{
			if (config.tickSound())
			{
				metronome.start();
			}
			else
			{
				metronome.stop();
			}
		}
		else if ("tickSoundVolume".equals(event.getKey()))
		{
			metronome.setVolumePercent(config.tickSoundVolume());
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.showAttackTimer())
		{
			return;
		}
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (local == null || actor != local)
		{
			return;
		}
		if (local.getInteracting() != null && AttackAnimations.isAttack(local.getAnimation()))
		{
			state.onAttackObserved();
		}
	}

	private void refreshIfLoggedIn()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			state.refresh(client, config.debugMode());
		}
	}

	private void resetEquipmentCache()
	{
		cachedWeaponId = -1;
	}

	/**
	 * Re-resolve the equipped weapon's attack speed for the optional attack-timer bar when the
	 * weapon actually changes. Only the weapon id matters here — shield/ammo swaps don't affect
	 * attack cadence, so unlike an earlier version of this method, they're not tracked at all.
	 * No-op-safe to call often.
	 */
	private void refreshEquipmentCache()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		int weaponId = equipment == null ? -1 : itemId(equipment.getItems(), EquipmentInventorySlot.WEAPON);
		if (weaponId == cachedWeaponId)
		{
			return;
		}
		cachedWeaponId = weaponId;
		state.onWeaponSpeedResolved(resolveWeaponAspeed(weaponId));
	}

	private int resolveWeaponAspeed(int weaponId)
	{
		if (weaponId <= 0)
		{
			return -1;
		}
		ItemStats stats = itemManager.getItemStats(weaponId);
		if (stats == null)
		{
			return -1;
		}
		ItemEquipmentStats equipmentStats = stats.getEquipment();
		if (equipmentStats == null)
		{
			return -1;
		}
		return equipmentStats.getAspeed();
	}

	private static int itemId(Item[] items, EquipmentInventorySlot slot)
	{
		int idx = slot.getSlotIdx();
		if (items == null || idx < 0 || idx >= items.length || items[idx] == null)
		{
			return -1;
		}
		return items[idx].getId();
	}
}
