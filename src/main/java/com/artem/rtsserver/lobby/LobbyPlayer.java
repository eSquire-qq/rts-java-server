package com.artem.rtsserver.lobby;

import com.artem.rtsserver.net.server.ClientConnection;

public class LobbyPlayer {

	private int playerId;
	private boolean ready;
	private final ClientConnection conn;

	public LobbyPlayer(ClientConnection conn) {
		this.conn = conn;
		this.playerId = conn.getPlayerId();
		this.ready = false;
	}

	public int getPlayerId() {
		return playerId;
	}

	public boolean getReady() {
		return ready;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public ClientConnection getConn() {
		return conn;
	}

}
