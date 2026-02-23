package com.artem.rtsserver.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.artem.rtsserver.lobby.LobbyPlayer;
import com.artem.rtsserver.net.server.ClientConnection;

public class MatchManager {
	
	private final Map<String, MatchSession> matchesById = new HashMap<>();
	private final Map<Integer, String> matchIdByPlayerId = new HashMap<>();
	
	public String createMatch(List<LobbyPlayer> players) {
		
		Random randomNumber = new Random();
		String matchId;
		
		do {
			int id = randomNumber.nextInt(900000) + 10000;
			matchId = String.valueOf(id);
		}while(matchesById.containsKey(matchId));
		
		MatchSession session = new MatchSession(matchId, players, this);
		matchesById.put(matchId, session);
		
		for(LobbyPlayer player : players) {
			matchIdByPlayerId.put(player.getPlayerId(), matchId);
		}
		
		session.start();
		
		return matchId;
	}
	
	public MatchSession getMatchSession(String matchId) {
		return matchesById.get(matchId);
	}
	
	public void endMatchSession(String matchId) {
		
		MatchSession session = matchesById.get(matchId);
		
		if(session == null) return;
			session.stop();
		
		
		for(LobbyPlayer player : session.getPlayers()){
			matchIdByPlayerId.remove(player.getPlayerId());
			player.getConn().clearMatchId();
		}
		matchesById.remove(matchId);
		
	}
	
	public MatchSession getMatchByPlayer(int playerId) {

	    String matchId = matchIdByPlayerId.get(playerId);

	    if (matchId == null) {
	        return null;
	    }

	    return matchesById.get(matchId);
	}
}
