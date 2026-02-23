package com.artem.rtsserver.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lobby {

	private final String lobbyId;
	private final List<LobbyPlayer> players = new ArrayList<>();
	private boolean started;
	private final int maxPlayers = 2;

	public String getLobbyId() {
		return lobbyId;
	}

	public List<LobbyPlayer> getPlayers() {
		return Collections.unmodifiableList(players);
	}

	public boolean isStarted() {
		return started;
	}

	public Lobby(String lobbyId) {
		this.lobbyId = lobbyId;
	}

	public void addPlayer(LobbyPlayer lobbyPLayer) {
		players.add(lobbyPLayer);
	}

	public void start() {
		this.started = true;
	}

	public boolean isFull() {
		return players.size() >= maxPlayers;
	}
	
	public void removePlayer(int playerId) {
		players.removeIf(p -> p.getPlayerId() == playerId);
	}

}
