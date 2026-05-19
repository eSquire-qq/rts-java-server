package com.artem.rtsserver.match;

public class UnitState {
    private int id;
    private int ownerPlayerId;

    private float x, y;
    private float targetX, targetY;
    private boolean hasTarget;
    private final String unitType;

    private final UnitStats stats;

    private int hp;
    private int attackTargetId = -1;
    private float attackTimer = 0f;
    private boolean attackTargetIsBuilding = false;

    private int gatherTargetId = -1;
    private float gatherTimer = 0f;

    public UnitState(int id, int ownerPlayerId, float x, float y, float targetX, float targetY, boolean hasTarget,
                     UnitStats stats, String unitType) {
        this.id = id;
        this.ownerPlayerId = ownerPlayerId;
        this.x = x;
        this.y = y;
        this.targetX = targetX;
        this.targetY = targetY;
        this.hasTarget = hasTarget;
        this.stats = stats;
        this.hp = stats.getMaxHp();
        this.unitType = unitType;
    }

    public void setTarget(float x, float y) {
        this.targetX = x;
        this.targetY = y;
        this.hasTarget = true;

        clearAttackTarget();
        clearGatherTarget();
    }

    public void clearTarget() {
        hasTarget = false;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setAttackTarget(int targetId) {
        this.attackTargetId = targetId;
        this.hasTarget = false;
        this.gatherTargetId = -1;
        this.gatherTimer = 0f;
    }

    public void clearAttackTarget() {
        attackTargetId = -1;
        attackTargetIsBuilding = false;
    }

    public int getAttackTargetId() {
        return attackTargetId;
    }

    public boolean canAttack() {
        return attackTimer <= 0f;
    }

    public void resetAttackTimer() {
        attackTimer = stats.getAttackCooldown();
    }

    public void updateAttackTimer(float dt) {
        if (attackTimer > 0f)
            attackTimer -= dt;
    }

    public void damage(int dmg) {
        hp -= dmg;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public int getId() {
        return id;
    }

    public int getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getTargetX() {
        return targetX;
    }

    public float getTargetY() {
        return targetY;
    }

    public boolean getHasTarget() {
        return hasTarget;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return stats.getMaxHp();
    }

    public float getAttackRange() {
        return stats.getAttackRange();
    }

    public int getAttackDamage() {
        return stats.getAttackDamage();
    }

    public float getMoveSpeed() {
        return stats.getMoveSpeed();
    }

    public String getUnitType() {
        return unitType;
    }

    public int getGoldCost() {
        return stats.getGoldCost();
    }

    public int getLumberCost() {
        return stats.getLumberCost();
    }

    public int getSupplyCost() {
        return stats.getSupplyCost();
    }

    public void setAttackTargetIsBuilding(boolean value) {
        this.attackTargetIsBuilding = value;
    }

    public boolean isAttackTargetBuilding() {
        return attackTargetIsBuilding;
    }

    public void setGatherTarget(int resourceId) {
        this.gatherTargetId = resourceId;

        clearTarget();
        clearAttackTarget();
    }

    public int getGatherTarget() {
        return gatherTargetId;
    }

    public void clearGatherTarget() {
        this.gatherTargetId = -1;
        this.gatherTimer = 0f;
    }

    public void addGatherTime(float dt) {
        this.gatherTimer += dt;
    }

    public boolean isReadyToGather() {
        return gatherTimer >= 2f;
    }

    public void resetGatherTimer() {
        this.gatherTimer = 0f;
    }

    public boolean isAttacking() {
        return attackTargetId > 0 && !hasTarget && hp > 0;
    }
}