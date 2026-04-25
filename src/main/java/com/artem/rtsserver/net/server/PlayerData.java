package com.artem.rtsserver.net.server;

public class PlayerData {
    public int playerId;
    public int gold;
    public int lumber;

    public PlayerData(int playerId, int gold, int lumber) {
        this.playerId = playerId;
        this.gold = gold;
        this.lumber = lumber;
    }
}
