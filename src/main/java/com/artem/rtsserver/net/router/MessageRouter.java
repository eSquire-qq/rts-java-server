package com.artem.rtsserver.net.router;

import com.artem.rtsserver.auth.AuthResult;
import com.artem.rtsserver.database.AuthDAO;
import org.springframework.stereotype.Component;

import com.artem.rtsserver.lobby.LobbyManager;
import com.artem.rtsserver.match.MatchManager;
import com.artem.rtsserver.match.MatchSession;
import com.artem.rtsserver.net.server.ClientConnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class MessageRouter {

    private final LobbyManager lobbyManager;
    private final MatchManager matchManager;
    private final AuthDAO authDAO;
    private final ObjectMapper mapper = new ObjectMapper();

    public MessageRouter(
            LobbyManager lobbyManager,
            MatchManager matchManager,
            AuthDAO authDAO
    ) {
        this.lobbyManager = lobbyManager;
        this.matchManager = matchManager;
        this.authDAO = authDAO;
    }

    public void handle(ClientConnection client, String json) {

        if (client.isInMatch()) {
            MatchSession session = matchManager.getMatchByPlayer(client.getPlayerId());

            if (session == null) {
                System.out.println("WARN: player " + client.getPlayerId() + " inMatch but no session; clearing matchId");
                client.clearMatchId();
                return;
            }

            JsonNode root;
            try {
                root = mapper.readTree(json);
            } catch (Exception e) {
                System.out.println("BAD JSON (parse fail) in match: [" + json + "]");
                client.sendLine("{\"type\":\"error\",\"reason\":\"bad_json\"}");
                return;
            }

            if (!root.path("type").asText("").startsWith("cmd_")) {
                client.sendLine("{\"type\":\"error\",\"reason\":\"in_match_only_cmd\"}");
                return;
            }

            session.enqueueCommand(client.getPlayerId(), json);
            return;
        }

        JsonNode root;
        try {
            System.out.println("ROUTER IN: [" + json + "]");
            root = mapper.readTree(json);
        } catch (Exception e) {
            System.out.println("BAD JSON (parse fail) in lobby: [" + json + "]");
            client.sendLine("{\"type\":\"error\",\"reason\":\"bad_json\"}");
            return;
        }

        try {
            String type = root.path("type").asText("");

            if (type.startsWith("cmd_")) {
                client.sendLine("{\"type\":\"error\",\"reason\":\"not_in_match\"}");
                return;
            }

            switch (type) {

                case "register":
                    handleRegister(client, root);
                    break;

                case "login":
                    handleLogin(client, root);
                    break;

                case "create_lobby":
                    if (!requireAuth(client)) return;

                    String lobbyId = lobbyManager.createLobby(client);
                    client.sendLine(lobbyId == null
                            ? "{\"type\":\"error\",\"reason\":\"create_failed\"}"
                            : "{\"type\":\"lobby_created\",\"lobbyId\":\"" + lobbyId + "\"}");
                    break;

                case "join_lobby":
                    if (!requireAuth(client)) return;

                    boolean ok = lobbyManager.joinLobby(root.path("lobbyId").asText(""), client);
                    if (!ok) {
                        client.sendLine("{\"type\":\"error\",\"reason\":\"join_failed\"}");
                    }
                    break;

                case "set_ready":
                    if (!requireAuth(client)) return;

                    lobbyManager.setReady(client.getPlayerId(), root.path("ready").asBoolean(false));
                    break;

                case "disconnect":
                    if (client.isAuthenticated()) {
                        lobbyManager.onDisconnection(client.getPlayerId());
                    }
                    break;

                default:
                    client.sendLine("{\"type\":\"error\",\"reason\":\"unknown_type\"}");
            }

        } catch (Exception e) {
            System.out.println("ROUTER EXCEPTION while handling json=[" + json + "]");
            e.printStackTrace();
            client.sendLine("{\"type\":\"error\",\"reason\":\"server_exception\"}");
        }
    }

    private void handleRegister(ClientConnection client, JsonNode root) {
        String username = root.path("username").asText("");
        String email = root.path("email").asText("");
        String password = root.path("password").asText("");

        AuthResult result = authDAO.register(username, email, password);

        if (!result.isSuccess()) {
            client.sendLine("{\"type\":\"auth_error\",\"message\":\"" + escape(result.getMessage()) + "\"}");
            return;
        }

        client.authenticate(result.getPlayerId(), result.getUsername(), result.getAccessToken());

        client.sendLine(
                "{\"type\":\"auth_success\"" +
                        ",\"playerId\":" + result.getPlayerId() +
                        ",\"username\":\"" + escape(result.getUsername()) + "\"" +
                        ",\"email\":\"" + escape(result.getEmail()) + "\"" +
                        ",\"accessToken\":\"" + escape(result.getAccessToken()) + "\"" +
                        ",\"refreshToken\":\"" + escape(result.getRefreshToken()) + "\"" +
                        "}"
        );
    }

    private void handleLogin(ClientConnection client, JsonNode root) {
        String email = root.path("email").asText("");
        String password = root.path("password").asText("");

        AuthResult result = authDAO.login(email, password);

        if (!result.isSuccess()) {
            client.sendLine("{\"type\":\"auth_error\",\"message\":\"" + escape(result.getMessage()) + "\"}");
            return;
        }

        client.authenticate(result.getPlayerId(), result.getUsername(), result.getAccessToken());

        client.sendLine(
                "{\"type\":\"auth_success\"" +
                        ",\"playerId\":" + result.getPlayerId() +
                        ",\"username\":\"" + escape(result.getUsername()) + "\"" +
                        ",\"email\":\"" + escape(result.getEmail()) + "\"" +
                        ",\"accessToken\":\"" + escape(result.getAccessToken()) + "\"" +
                        ",\"refreshToken\":\"" + escape(result.getRefreshToken()) + "\"" +
                        "}"
        );
    }

    private boolean requireAuth(ClientConnection client) {
        if (!client.isAuthenticated()) {
            client.sendLine("{\"type\":\"error\",\"reason\":\"unauthorized\"}");
            return false;
        }

        return true;
    }

    private String escape(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}