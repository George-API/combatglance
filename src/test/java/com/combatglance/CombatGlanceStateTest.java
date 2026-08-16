package com.combatglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import org.junit.Test;

/**
 * {@link CombatGlanceState} refresh path, exercised against a minimal JDK-only fake
 * {@link Client} (see {@link #fakeClient}) rather than a mocking library — {@code Client} is a
 * huge interface, and the implementation brief explicitly prefers this over "brittle Mockito
 * theater." Weapon-style resolution itself is covered in isolation by {@link CombatStyleTest};
 * this class focuses on snapshot/reset behavior and prayer sampling.
 */
public class CombatGlanceStateTest
{
	@Test
	public void initialSnapshotIsLoggedOut()
	{
		CombatGlanceState state = new CombatGlanceState();
		assertFalse(state.snapshot().isLoggedIn());
		assertNull(state.snapshot().getOffensivePrayer());
		assertNull(state.snapshot().getOverheadPrayer());
	}

	@Test
	public void refreshWhenNotLoggedInResetsSnapshot()
	{
		CombatGlanceState state = new CombatGlanceState();
		Client client = fakeClient(GameState.LOGIN_SCREEN, EnumSet.noneOf(Prayer.class));

		state.refresh(client, false);

		assertFalse(state.snapshot().isLoggedIn());
		assertEquals("not-logged-in", state.getLastResetReason());
	}

	@Test
	public void refreshSamplesActivePrayersIntoOffenseAndOverheadSlots()
	{
		CombatGlanceState state = new CombatGlanceState();
		Client client = fakeClient(GameState.LOGGED_IN, EnumSet.of(Prayer.PIETY, Prayer.PROTECT_FROM_MELEE));

		state.refresh(client, false);

		CombatGlanceSnapshot snap = state.snapshot();
		assertTrue(snap.isLoggedIn());
		assertEquals(Prayer.PIETY, snap.getOffensivePrayer());
		assertEquals(Prayer.PROTECT_FROM_MELEE, snap.getOverheadPrayer());
	}

	@Test
	public void refreshWithNoActivePrayersLeavesSlotsEmpty()
	{
		CombatGlanceState state = new CombatGlanceState();
		Client client = fakeClient(GameState.LOGGED_IN, EnumSet.noneOf(Prayer.class));

		state.refresh(client, false);

		CombatGlanceSnapshot snap = state.snapshot();
		assertTrue(snap.isLoggedIn());
		assertNull(snap.getOffensivePrayer());
		assertNull(snap.getOverheadPrayer());
	}

	@Test
	public void resetClearsPrayersAndLoggedInFlag()
	{
		CombatGlanceState state = new CombatGlanceState();
		Client client = fakeClient(GameState.LOGGED_IN, EnumSet.of(Prayer.PIETY, Prayer.PROTECT_FROM_MELEE));
		state.refresh(client, false);
		assertTrue(state.snapshot().isLoggedIn());

		state.reset("test-reset");

		CombatGlanceSnapshot snap = state.snapshot();
		assertFalse(snap.isLoggedIn());
		assertNull(snap.getOffensivePrayer());
		assertNull(snap.getOverheadPrayer());
		assertEquals("test-reset", state.getLastResetReason());
	}

	@Test
	public void debugActivePrayerListIsUnmodifiableAndOnlyPopulatedWhenRequested()
	{
		CombatGlanceState state = new CombatGlanceState();
		Client client = fakeClient(GameState.LOGGED_IN, EnumSet.of(Prayer.PIETY));

		state.refresh(client, false);
		assertTrue(state.snapshot().getDebugActivePrayers().isEmpty());

		state.refresh(client, true);
		assertEquals(1, state.snapshot().getDebugActivePrayers().size());
		try
		{
			state.snapshot().getDebugActivePrayers().add(Prayer.RIGOUR);
			fail("expected UnsupportedOperationException");
		}
		catch (UnsupportedOperationException expected)
		{
			// list must stay unmodifiable — the overlay must never be able to mutate state
		}
	}

	/**
	 * Minimal JDK-only fake. Unconfigured methods return benign zero-value defaults so calls
	 * this test doesn't care about (weapon/style enum lookups) don't throw — CombatStyleResolver
	 * already catches that and falls back to MELEE, which these tests treat as an acceptable,
	 * honest default.
	 */
	private static Client fakeClient(GameState gameState, Set<Prayer> activePrayers)
	{
		Player player = (Player) Proxy.newProxyInstance(
			Client.class.getClassLoader(),
			new Class<?>[]{Player.class},
			(proxy, method, args) -> defaultValue(method.getReturnType()));

		Map<String, Object> answers = new HashMap<>();
		answers.put("getGameState", gameState);
		answers.put("getLocalPlayer", gameState == GameState.LOGGED_IN ? player : null);

		InvocationHandler handler = (proxy, method, args) ->
		{
			if ("isPrayerActive".equals(method.getName()) && args != null && args.length == 1)
			{
				return activePrayers.contains(args[0]);
			}
			if (answers.containsKey(method.getName()))
			{
				return answers.get(method.getName());
			}
			return defaultValue(method.getReturnType());
		};
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
	}

	private static Object defaultValue(Class<?> returnType)
	{
		if (returnType == void.class || !returnType.isPrimitive())
		{
			return null;
		}
		if (returnType == boolean.class)
		{
			return Boolean.FALSE;
		}
		if (returnType == byte.class)
		{
			return (byte) 0;
		}
		if (returnType == short.class)
		{
			return (short) 0;
		}
		if (returnType == long.class)
		{
			return 0L;
		}
		if (returnType == float.class)
		{
			return 0f;
		}
		if (returnType == double.class)
		{
			return 0d;
		}
		if (returnType == char.class)
		{
			return (char) 0;
		}
		return 0;
	}
}
