package com.artem.rtsserver.lobby;

import java.util.HashMap;

import java.util.Map;
import java.util.Random;

import com.artem.rtsserver.match.MatchManager;
import com.artem.rtsserver.match.MatchSession;
import com.artem.rtsserver.net.server.ClientConnection;

public class LobbyManager {

	private final Map<String, Lobby> lobbiesById = new HashMap<>();
	private final Map<Integer, String> lobbyIdByPlayersId = new HashMap<>();
	private final MatchManager matchManager;

	public LobbyManager(MatchManager matchManager) {
		this.matchManager = matchManager;
	}

	public String createLobby(ClientConnection host) {

		int playerId = host.getPlayerId();
		String lobbyID;
		Random randomNumber = new Random();

		if (lobbyIdByPlayersId.containsKey(playerId)) {
			return null;
		}

		do {
			int Id = randomNumber.nextInt(900000) + 10000;
			lobbyID = String.valueOf(Id);
		} while (lobbiesById.containsKey(lobbyID));

		Lobby lobby = new Lobby(lobbyID);
		LobbyPlayer player = new LobbyPlayer(host);

		lobby.addPlayer(player);

		lobbiesById.put(lobbyID, lobby);
		lobbyIdByPlayersId.put(playerId, lobbyID);

		broadcastLobbyState(lobby);

		System.out.println("Created lobbyId=" + lobbyID);
		return lobbyID;
	}

	public boolean joinLobby(String lobbyId, ClientConnection player) {

		int playerId = player.getPlayerId();

		if (lobbyIdByPlayersId.containsKey(playerId))
			return false;

		Lobby lobby = lobbiesById.get(lobbyId);

		if (lobby == null)
			return false;
		if (lobby.isStarted())
			return false;
		if (lobby.isFull())
			return false;

		lobby.addPlayer(new LobbyPlayer(player));
		lobbyIdByPlayersId.put(playerId, lobbyId);

		broadcastLobbyState(lobby);
		maybeStartMatch(lobby);

		return true;
	}

	public void setReady(int playerId, boolean ready) {

		Lobby lobby = getLobbyByPlayer(playerId);
		if (lobby == null)
			return;

		LobbyPlayer lp = findPlayer(lobby, playerId);
		if (lp == null)
			return;

		lp.setReady(ready);

		broadcastLobbyState(lobby);
		maybeStartMatch(lobby);

	}

	public void onDisconnection(int playerId) {

		Lobby lobby = getLobbyByPlayer(playerId);
		if(lobby != null) {
			lobby.removePlayer(playerId);
			lobbyIdByPlayersId.remove(playerId);
			
			if(lobby.getPlayers().isEmpty()) {
				lobbiesById.remove(lobby.getLobbyId());
			}else { 
				broadcastLobbyState(lobby);
			}
			return;
		}
		
		MatchSession matchSession = matchManager.getMatchByPlayer(playerId);
		if(matchSession != null) {
			String matchId = matchSession.getMatchId();
	        matchManager.endMatchSession(matchId);
		}
		
	}

	private void broadcastLobbyState(Lobby lobby) {
		StringBuilder sb = new StringBuilder();

		sb.append("{\"type\":\"lobby_state\",");
		sb.append("\"lobbyId\":\"").append(lobby.getLobbyId()).append("\",");
		sb.append("\"players\":[");

		boolean first = true;
		for (LobbyPlayer p : lobby.getPlayers()) {
			if (!first)
				sb.append(",");
			first = false;

			sb.append("{\"id\":").append(p.getPlayerId()).append(",\"ready\":").append(p.getReady()).append("}");
		}

		sb.append("]}");

		String json = sb.toString();

		for (LobbyPlayer p : lobby.getPlayers()) {
			p.getConn().sendLine(json);
		}
	}

	private void maybeStartMatch(Lobby lobby) {

		boolean allReady = true;

		for (LobbyPlayer player : lobby.getPlayers()) {
			if (!player.getReady()) {
				allReady = false;
				break;
			}
		}

		if (!lobby.isStarted() && lobby.getPlayers().size() == 2 && allReady) {
			lobby.start();

			String matchId = matchManager.createMatch(lobby.getPlayers());

			for (LobbyPlayer player : lobby.getPlayers()) {
				player.getConn().setMatchId(matchId);
				player.getConn().sendLine("{\"type\":\"match_start\",\"matchId\":\"" + matchId + "\"}");
				lobbyIdByPlayersId.remove(player.getPlayerId());
			}
			System.out.println("MATCH START: " + lobby.getLobbyId());
			lobbiesById.remove(lobby.getLobbyId());
		}
	}

	private Lobby getLobbyByPlayer(int playerId) {

		String lobbyId = lobbyIdByPlayersId.get(playerId);
		if (lobbyId == null)
			return null;

		return lobbiesById.get(lobbyId);
	}

	private LobbyPlayer findPlayer(Lobby lobby, int playerId) {

		for (LobbyPlayer player : lobby.getPlayers()) {
			if (player.getPlayerId() == playerId) {
				return player;
			}
		}
		return null;
	}
}
