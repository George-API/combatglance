package com.combatglance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

public class AttackAnimationsTest
{
	@Test
	public void recognizesMeleeRangedAndMagicAttackAnimations()
	{
		assertTrue(AttackAnimations.isAttack(AnimationID.HUMAN_SWORD_SLASH));
		assertTrue(AttackAnimations.isAttack(AnimationID.SLAYER_ABYSSAL_WHIP_ATTACK));
		assertTrue(AttackAnimations.isAttack(AnimationID.HUMAN_BOW));
		assertTrue(AttackAnimations.isAttack(AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK));
		assertTrue(AttackAnimations.isAttack(AnimationID.HUMAN_CASTSTRIKE_STAFF_WALKMERGE));
	}

	@Test
	public void rejectsNonAttackAnimations()
	{
		assertFalse(AttackAnimations.isAttack(AnimationID.HUMAN_EAT));
		assertFalse(AttackAnimations.isAttack(AnimationID.HUMAN_FLETCHING));
	}

	@Test
	public void rejectsNoneAndNegativeAnimation()
	{
		assertFalse(AttackAnimations.isAttack(-1));
	}
}
