package com.artem.rtsserver.match;

public class PlayerCommand {
	
	public final int playerId;
    public final String json;

    public PlayerCommand(int playerId, String json) {
        this.playerId = playerId;
        this.json = json;
    }
}
