package com.combatglance;

import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Resolves the player's current combat skill family from the equipped weapon category and
 * selected attack style — the same client enums the core Attack Styles plugin uses.
 *
 * <p>Never returns a recommendation. Unknown or exotic weapons fall back to MELEE, the honest
 * conservative default (matches unarmed), rather than guessing MAGIC or RANGED.
 */
final class CombatStyleResolver
{
	private CombatStyleResolver()
	{
	}

	static CombatStyle resolve(Client client)
	{
		try
		{
			int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
			int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
			int castingMode = client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
			String styleName = styleNameAt(client, weaponType, styleIndex, castingMode);
			if (styleName != null)
			{
				return CombatStyle.fromAttackStyleName(styleName);
			}
		}
		catch (Exception ignored)
		{
			// Prefer the conservative melee default over crashing the overlay when enums are unavailable.
		}
		return CombatStyle.MELEE;
	}

	@Nullable
	private static String styleNameAt(Client client, int weaponType, int attackStyleIndex, int castingMode)
	{
		String[] names = weaponTypeStyleNames(client, weaponType);
		if (names.length == 0)
		{
			return null;
		}
		int index = attackStyleIndex;
		// Staff defensive casting uses slot 4 + casting-mode offset (script 4525).
		if (index == 4)
		{
			index += castingMode;
		}
		if (index < 0 || index >= names.length)
		{
			return null;
		}
		return names[index];
	}

	private static String[] weaponTypeStyleNames(Client client, int weaponType)
	{
		EnumComposition weaponStyles = client.getEnum(EnumID.WEAPON_STYLES);
		int weaponStyleEnum = weaponStyles.getIntValue(weaponType);
		if (weaponStyleEnum == -1)
		{
			return fallbackStyleNames(weaponType);
		}

		int[] structs = client.getEnum(weaponStyleEnum).getIntVals();
		String[] names = new String[structs.length];
		for (int i = 0; i < structs.length; i++)
		{
			StructComposition styleStruct = client.getStructComposition(structs[i]);
			String name = styleStruct.getStringValue(ParamID.ATTACK_STYLE_NAME);
			// Slot 5 "Defensive" on casting weapons is defensive casting.
			if (i == 5 && name != null && "Defensive".equalsIgnoreCase(name))
			{
				names[i] = "Defensive casting";
			}
			else
			{
				names[i] = name;
			}
		}
		return names;
	}

	/**
	 * Hard-coded fallbacks for weapon categories missing from {@link EnumID#WEAPON_STYLES}.
	 */
	private static String[] fallbackStyleNames(int weaponType)
	{
		if (weaponType == 22)
		{
			// Blue moon spear
			return new String[]{"Accurate", "Aggressive", null, "Defensive", "Casting", "Defensive casting"};
		}
		if (weaponType == 30)
		{
			// Partisan
			return new String[]{"Accurate", "Aggressive", "Aggressive", "Defensive"};
		}
		return new String[0];
	}
}
