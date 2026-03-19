package com.artem.rtsserver.net.router;

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
	private final ObjectMapper mapper = new ObjectMapper();

	public MessageRouter(LobbyManager lobbyManager, MatchManager matchManager) {
		this.lobbyManager = lobbyManager;
		this.matchManager = matchManager;
	}

	public void handle(ClientConnection client, String json) {
		if (client.isInMatch()) {
			MatchSession session = matchManager.getMatchByPlayer(client.getPlayerId());
			if (session == null) {
				System.out
						.println("WARN: player " + client.getPlayerId() + " inMatch but no session; clearing matchId");
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
			case "create_lobby":
				String lobbyId = lobbyManager.createLobby(client);
				client.sendLine(lobbyId == null ? "{\"type\":\"error\",\"reason\":\"create_failed\"}"
						: "{\"type\":\"lobby_created\",\"lobbyId\":\"" + lobbyId + "\"}");
				break;
			case "join_lobby":
				boolean ok = lobbyManager.joinLobby(root.path("lobbyId").asText(""), client);
				if (!ok)
					client.sendLine("{\"type\":\"error\",\"reason\":\"join_failed\"}");
				break;
			case "set_ready":
				lobbyManager.setReady(client.getPlayerId(), root.path("ready").asBoolean(false));
				break;
			case "disconnect":
				lobbyManager.onDisconnection(client.getPlayerId());
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
}