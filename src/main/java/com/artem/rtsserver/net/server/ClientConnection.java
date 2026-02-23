package com.artem.rtsserver.net.server;

public class ClientConnection {
	
	private final int playerId;
	private volatile String matchId;
	
	public ClientConnection (int playerId) {
		this.playerId = playerId;
	}

	public int getPlayerId() {
		return playerId;
	}
	
	public void sendLine(String json) {
		//System.out.println(json);
		System.out.println("[TO player=" + playerId + "] " + json);
	}
	
	public boolean isInMatch() {
		return matchId != null;
	}
	
	public void setMatchId(String matchId) {
		this.matchId = matchId;
	}
	
	public String getMatchId() {
		return matchId;
	}
	
	public void clearMatchId() {
		this.matchId = null;
	}
}
