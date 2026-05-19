package com.artem.rtsserver.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.artem.rtsserver.database.PlayerDAO;
import com.artem.rtsserver.lobby.LobbyPlayer;

@Component
public class MatchManager {

    private final Map<String, MatchSession> matchesById = new HashMap<>();
    private final Map<Integer, String> matchIdByPlayerId = new HashMap<>();

    private final PlayerDAO playerDAO;

    @Autowired
    public MatchManager(PlayerDAO playerDAO) {
        this.playerDAO = playerDAO;
    }

    public String createMatch(List<LobbyPlayer> players) {
        Random randomNumber = new Random();
        String matchId;

        do {
            int id = randomNumber.nextInt(900000) + 10000;
            matchId = String.valueOf(id);
        } while (matchesById.containsKey(matchId));

        MatchSession session = new MatchSession(matchId, players, this, playerDAO);
        matchesById.put(matchId, session);

        for (LobbyPlayer player : players) {
            matchIdByPlayerId.put(player.getPlayerId(), matchId);
        }

        session.start();
        return matchId;
    }

    public MatchSession getMatchSession(String matchId) {
        return matchesById.get(matchId);
    }

    public void endMatchSession(String matchId) {
        MatchSession session = matchesById.remove(matchId);

        if (session == null)
            return;

        session.stop();

        for (LobbyPlayer player : session.getPlayers()) {
            matchIdByPlayerId.remove(player.getPlayerId());
            player.getConn().clearMatchId();
        }

        System.out.println("MATCH ENDED: " + matchId);
    }

    public MatchSession getMatchByPlayer(int playerId) {
        String matchId = matchIdByPlayerId.get(playerId);
        if (matchId == null) return null;
        return matchesById.get(matchId);
    }
}