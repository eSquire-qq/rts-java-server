package com.artem.rtsserver.net.router;

import com.artem.rtsserver.lobby.LobbyManager;
import com.artem.rtsserver.match.MatchManager;
import com.artem.rtsserver.match.MatchSession;
import com.artem.rtsserver.net.server.ClientConnection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageRouter {

    private final LobbyManager lobbyManager;
    private final MatchManager matchManager;

    private final ObjectMapper mapper = new ObjectMapper();

    public MessageRouter(LobbyManager lobbyManager, MatchManager matchManager) {
        this.lobbyManager = lobbyManager;
        this.matchManager = matchManager;
    }

    public void handle(ClientConnection client, String json) {

        if (client.isInMatch()) {
            MatchSession session = matchManager.getMatchByPlayer(client.getPlayerId());
            if (session == null) {
                System.out.println("WARN: player " + client.getPlayerId()
                        + " inMatch but no session; clearing matchId");
                client.clearMatchId();
                return;
            }

            // В матчі приймаємо тільки cmd_*
            if (!json.contains("\"type\":\"cmd_")) {
                client.sendLine("{\"type\":\"error\",\"reason\":\"in_match_only_cmd\"}");
                return;
            }

            session.enqueueCommand(client.getPlayerId(), json);
            return;
        }

        // LOBBY mode
        try {
            JsonNode root = mapper.readTree(json);
            String type = root.path("type").asText("");

            // Команди матчу в лобі заборонені
            if (type.startsWith("cmd_")) {
                client.sendLine("{\"type\":\"error\",\"reason\":\"not_in_match\"}");
                return;
            }

            switch (type) {
                case "create_lobby": {
                    String lobbyId = lobbyManager.createLobby(client);
                    if (lobbyId == null) {
                        client.sendLine("{\"type\":\"error\",\"reason\":\"create_failed\"}");
                    } else {
                        client.sendLine("{\"type\":\"lobby_created\",\"lobbyId\":\"" + lobbyId + "\"}");
                    }
                    break;
                }

                case "join_lobby": {
                    String lobbyId = root.path("lobbyId").asText("");
                    boolean ok = lobbyManager.joinLobby(lobbyId, client);
                    if (!ok) {
                        client.sendLine("{\"type\":\"error\",\"reason\":\"join_failed\"}");
                    }
                    break;
                }

                case "set_ready": {
                    boolean ready = root.path("ready").asBoolean(false);
                    lobbyManager.setReady(client.getPlayerId(), ready);
                    break;
                }

                case "disconnect": {
                    lobbyManager.onDisconnection(client.getPlayerId());
                    break;
                }

                default:
                    client.sendLine("{\"type\":\"error\",\"reason\":\"unknown_type\"}");
            }

        } catch (Exception e) {
            client.sendLine("{\"type\":\"error\",\"reason\":\"bad_json\"}");
        }
    }
}