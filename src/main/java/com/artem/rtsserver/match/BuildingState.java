package com.artem.rtsserver.match;

public class BuildingState {

    private final int id;
    private final int ownerPlayerId;
    private final String buildingType;

    private final float x;
    private final float y;

    private int hp;
    private final int maxHp;

    public BuildingState(int id, int ownerPlayerId, String buildingType, float x, float y, int maxHp) {
        this.id = id;
        this.ownerPlayerId = ownerPlayerId;
        this.buildingType = buildingType;
        this.x = x;
        this.y = y;
        this.maxHp = maxHp;
        this.hp = maxHp;
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

    public String getBuildingType() {
        return buildingType;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }
}