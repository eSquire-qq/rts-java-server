package com.artem.rtsserver.match;

public class UnitStats {
    private final int maxHp;
    private final float attackRange;
    private final int attackDamage;
    private final float attackCooldown;
    private final float moveSpeed;

    private final int goldCost;
    private final int lumberCost;
    private final int supplyCost;
    
    public UnitStats(
            int maxHp,
            float attackRange,
            int attackDamage,
            float attackCooldown,
            float moveSpeed,
            int goldCost,
            int lumberCost,
            int supplyCost
    ) {
        this.maxHp = maxHp;
        this.attackRange = attackRange;
        this.attackDamage = attackDamage;
        this.attackCooldown = attackCooldown;
        this.moveSpeed = moveSpeed;
        this.goldCost = goldCost;
        this.lumberCost = lumberCost;
        this.supplyCost = supplyCost;
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

    public int getGoldCost() {
        return goldCost;
    }

    public int getLumberCost() {
        return lumberCost;
    }

    public int getSupplyCost() {
        return supplyCost;
    }
}