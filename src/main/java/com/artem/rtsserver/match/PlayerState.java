package com.artem.rtsserver.match;

public class PlayerState {

    private final int playerId;

    private int gold;
    private int lumber;

    private int usedSupply;
    private int maxSupply;

    public PlayerState(int playerId, int gold, int lumber, int usedSupply, int maxSupply) {
        this.playerId = playerId;
        this.gold = gold;
        this.lumber = lumber;
        this.usedSupply = usedSupply;
        this.maxSupply = maxSupply;
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getGold() {
        return gold;
    }

    public int getLumber() {
        return lumber;
    }

    public int getUsedSupply() {
        return usedSupply;
    }

    public int getMaxSupply() {
        return maxSupply;
    }

    public boolean hasEnoughResources(int goldCost, int lumberCost) {
        return gold >= goldCost && lumber >= lumberCost;
    }

    public void spendResources(int goldCost, int lumberCost) {
        gold -= goldCost;
        lumber -= lumberCost;
    }

    public boolean hasEnoughSupply(int supplyCost) {
        return usedSupply + supplyCost <= maxSupply;
    }

    public void addUsedSupply(int amount) {
        usedSupply += amount;
    }

    public void removeUsedSupply(int amount) {
        usedSupply -= amount;
        if (usedSupply < 0) usedSupply = 0;
    }

    public void addMaxSupply(int amount) {
        maxSupply += amount;
    }

    public void removeMaxSupply(int amount) {
        maxSupply -= amount;
        if (maxSupply < 0) maxSupply = 0;
        if (usedSupply > maxSupply) usedSupply = maxSupply;
    }
}