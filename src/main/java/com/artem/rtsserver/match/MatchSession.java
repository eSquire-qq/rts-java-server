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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MatchSession {

    public List<LobbyPlayer> getPlayers() { return players; }

    private final String matchId;
    private int tickNumber;

    private final List<LobbyPlayer> players;
    private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler;

    private final MatchManager matchManager;

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<Integer, UnitState> units = new HashMap<>();

    // простий world bounds (потім можна замінити на карту/колізії)
    private static final float WORLD_MIN_X = -50f;
    private static final float WORLD_MAX_X = 50f;
    private static final float WORLD_MIN_Y = -50f;
    private static final float WORLD_MAX_Y = 50f;

    public MatchSession(String matchId, List<LobbyPlayer> players, MatchManager matchManager) {
        this.matchId = matchId;
        this.players = players;
        this.tickNumber = 0;
        this.matchManager = matchManager;
    }

    public void start() {

        int player1Id = players.get(0).getPlayerId();
        units.put(1, new UnitState(1, player1Id, 0f, 0f, 0f, 0f, false));

        if (players.size() >= 2) {
            int player2Id = players.get(1).getPlayerId();
            units.put(2, new UnitState(2, player2Id, 5f, 0f, 5f, 0f, false));
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }
    
    /*public void start() {
        int player1Id = players.get(0).getPlayerId();
        int player2Id = players.get(1).getPlayerId();

        units.put(1, new UnitState(1, player1Id, 0f, 0f, 0f, 0f, false));
        units.put(2, new UnitState(2, player2Id, 5f, 0f, 5f, 0f, false));

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
   } */

    private void tick() throws JsonProcessingException {
        tickNumber++;

        PlayerCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            handleCommand(cmd);
        }

        simulateUnits(0.05f);

        String stateJson = buildStateJson();
        for (LobbyPlayer player : players) {
            player.getConn().sendLine(stateJson);
        }
    }

    private void handleCommand(PlayerCommand cmd) throws JsonProcessingException {
        JsonNode root = mapper.readTree(cmd.json);
        String type = root.path("type").asText("");

        switch (type) {
            case "cmd_move":
                handleMove(cmd.playerId, root);
                break;

            case "cmd_end_match":
                handleEndMatch(cmd.playerId);
                break;

            default:
                // невідомі команди в матчі просто ігноруємо (або лог)
                System.out.println("[MATCH " + matchId + "] unknown cmd type=" + type + " json=" + cmd.json);
                break;
        }
    }

    private void handleMove(int playerId, JsonNode root) {
        int unitId = root.path("unitId").asInt(-1);
        float x = (float) root.path("x").asDouble(Double.NaN);
        float y = (float) root.path("y").asDouble(Double.NaN);

        if (unitId <= 0) return;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;

        // bounds clamp або reject (я зроблю clamp, щоб було м’якше)
        x = clamp(x, WORLD_MIN_X, WORLD_MAX_X);
        y = clamp(y, WORLD_MIN_Y, WORLD_MAX_Y);

        UnitState unit = units.get(unitId);
        if (unit == null) return;

        // owner-check: гравець може керувати тільки своїми
        if (unit.getOwnerPlayerId() != playerId) return;

        unit.setTarget(x, y);
    }

    private void handleEndMatch(int playerId) {
        // тут пізніше можна перевіряти "чи має право" завершувати матч
        matchManager.endMatchSession(matchId);
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private String buildStateJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"type\":\"state\",\"tick\":").append(tickNumber).append(",\"units\":[");

        boolean first = true;
        for (UnitState u : units.values()) {
            if (!first) sb.append(',');
            first = false;

            sb.append("{\"id\":").append(u.getId())
              .append(",\"owner\":").append(u.getOwnerPlayerId())
              .append(",\"x\":").append(u.getX())
              .append(",\"y\":").append(u.getY())
              .append('}');
        }

        sb.append("]}");
        return sb.toString();
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdown();
    }

    public void enqueueCommand(int playerId, String json) {
        commandQueue.add(new PlayerCommand(playerId, json));
    }

    private void simulateUnits(float dt) {
        float speed = 3f;
        float maxStep = speed * dt;

        for (UnitState u : units.values()) {
            if (!u.getHasTarget()) continue;

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