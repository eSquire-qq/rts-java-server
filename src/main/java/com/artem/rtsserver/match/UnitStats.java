package com.artem.rtsserver.match;

public class UnitStats {
	private final int maxHp;
	private final float attackRange;
	private final int attackDamage;
	private final float attackCooldown;
	private final float moveSpeed;

	public UnitStats(int maxHp, float attackRange, int attackDamage, float attackCooldown, float moveSpeed) {
		this.maxHp = maxHp;
		this.attackRange = attackRange;
		this.attackDamage = attackDamage;
		this.attackCooldown = attackCooldown;
		this.moveSpeed = moveSpeed;
	}

	public int getMaxHp() {
		return maxHp;
	}

	public float getAttackRange() {
		return attackRange;
	}

	public int getAttackDamage() {
		return attackDamage;
	}

	public float getAttackCooldown() {
		return attackCooldown;
	}

	public float getMoveSpeed() {
		return moveSpeed;
	}
}