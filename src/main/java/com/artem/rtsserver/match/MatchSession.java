package com.artem.rtsserver.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.artem.rtsserver.lobby.LobbyPlayer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MatchSession {

	public List<LobbyPlayer> getPlayers() {
		return players;
	}

	private final String matchId;
	private int tickNumber;

	private final List<LobbyPlayer> players;
	private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
	private ScheduledExecutorService scheduler;

	private final MatchManager matchManager;

	private final ObjectMapper mapper = new ObjectMapper();

	Map<Integer, UnitState> units = new HashMap<>();

	public MatchSession(String matchId, List<LobbyPlayer> players, MatchManager matchManager) {
		this.matchId = matchId;
		this.players = players;
		this.tickNumber = 0;
		this.matchManager = matchManager;
	}

	public void start() {

		int player1Id = players.get(0).getPlayerId();
		int player2Id = players.get(1).getPlayerId();

		units.put(1, new UnitState(1, player1Id, 0f, 0f, 0f, 0f, false));
		units.put(2, new UnitState(2, player2Id, 5f, 0f, 5f, 0f, false));

		scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> {
			try {
				tick();
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}, 0, 50, TimeUnit.MILLISECONDS);
	}

	private void tick() throws JsonMappingException, JsonProcessingException {

		PlayerCommand cmd;
		tickNumber++;

		while ((cmd = commandQueue.poll()) != null) {
			if (cmd.json.contains("\"type\":\"cmd_end_match\"")) {
				matchManager.endMatchSession(matchId);
				return;
			}
			if (cmd.json.contains("\"type\":\"cmd_move\"")) {
				JsonNode root = mapper.readTree(cmd.json);

				int unitId = root.path("unitId").asInt(-1);
				float x = (float) root.path("x").asDouble();
				float y = (float) root.path("y").asDouble();

				UnitState unitState = units.get(unitId);

				if (unitState == null) {
					continue;
				}

				if (unitState.getOwnerPlayerId() != cmd.playerId) {
					continue;
				}
				unitState.setTarget(x, y);
			}

			System.out.println("tick=" + tickNumber + " cmd=" + cmd.json);
		}

		simulateUnits(0.05f);
		String stateJson = buildStateJson();

		for (LobbyPlayer player : players) {
			player.getConn().sendLine(stateJson);
		}
	}

	private String buildStateJson() {
		StringBuilder sb = new StringBuilder(256);
		sb.append("{\"type\":\"state\",\"tick\":").append(tickNumber).append(",\"units\":[");

		boolean first = true;
		for (UnitState u : units.values()) {
			if (!first)
				sb.append(',');
			first = false;

			sb.append("{\"id\":").append(u.getId()).append(",\"owner\":").append(u.getOwnerPlayerId()).append(",\"x\":")
					.append(u.getX()).append(",\"y\":").append(u.getY()).append('}');
		}

		sb.append("]}");
		return sb.toString();
	}

	public void stop() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
	}

	public void handleCommand(int playerId, String json) {
		System.out.println("[MATCH " + matchId + "] cmd from " + playerId + ": " + json);
	}

	public void enqueueCommand(int playerId, String json) {
		commandQueue.add(new PlayerCommand(playerId, json));
	}

	private void simulateUnits(float dt) {
		float speed = 3f;
		float maxStep = speed * dt;

		for (UnitState u : units.values()) {
			if (!u.getHasTarget())
				continue;

			float dx = u.getTargetX() - u.getX();
			float dy = u.getTargetY() - u.getY();
			float distSq = dx * dx + dy * dy;

			if (distSq < 0.0001f) {
				u.setPosition(u.getTargetX(), u.getTargetY());
				u.clearTarget();
				continue;
			}

			float dist = (float) Math.sqrt(distSq);

			if (dist <= maxStep) {
				u.setPosition(u.getTargetX(), u.getTargetY());
				u.clearTarget();
			} else {
				float nx = dx / dist;
				float ny = dy / dist;
				u.setPosition(u.getX() + nx * maxStep, u.getY() + ny * maxStep);
			}
		}
	}

	public String getMatchId() {
		return matchId;
	}

}
