package com.artem.rtsserver.match;

public class UnitState {

    private int id;
    private int ownerPlayerId;

    private float x, y;
    private float targetX, targetY;
    private boolean hasTarget;

    // --- combat ---
    private int hp = 100;
    private int maxHp = 100;

    private int attackTargetId = -1;

    private float attackRange = 2f;
    private int attackDamage = 10;

    private float attackCooldown = 1f;
    private float attackTimer = 0f;

    public UnitState(int id, int ownerPlayerId, float x, float y, float targetX, float targetY, boolean hasTarget) {
        this.id = id;
        this.ownerPlayerId = ownerPlayerId;
        this.x = x;
        this.y = y;
        this.targetX = targetX;
        this.targetY = targetY;
        this.hasTarget = hasTarget;
    }

    // ---------------- movement ----------------

    public void setTarget(float x, float y) {
        this.targetX = x;
        this.targetY = y;
        this.hasTarget = true;
    }

    public void clearTarget() {
        hasTarget = false;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    // ---------------- combat ----------------

    public void setAttackTarget(int targetId) {
        this.attackTargetId = targetId;
    }

    public int getAttackTargetId() {
        return attackTargetId;
    }

    public boolean canAttack() {
        return attackTimer <= 0f;
    }

    public void resetAttackTimer() {
        attackTimer = attackCooldown;
    }

    public void updateAttackTimer(float dt) {
        if (attackTimer > 0f) {
            attackTimer -= dt;
        }
    }

    public void damage(int dmg) {
        hp -= dmg;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    // ---------------- getters ----------------

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
        return maxHp;
    }

    public float getAttackRange() {
        return attackRange;
    }

    public int getAttackDamage() {
        return attackDamage;
    }
}