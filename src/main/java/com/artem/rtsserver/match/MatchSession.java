package com.artem.rtsserver.match;

import java.util.HashMap;
import java.util.Iterator;
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

	private final Map<Integer, UnitState> units = new HashMap<>();
	private final Map<Integer, BuildingState> buildings = new HashMap<>();

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

	    UnitStats swordsmanStats = new UnitStats(100, 2f, 10, 1f, 3f);
	    UnitStats archerStats = new UnitStats(70, 5f, 7, 1.2f, 2.5f);

	    units.put(1, new UnitState(
	        1,
	        player1Id,
	        0f, 0f,
	        0f, 0f,
	        false,
	        swordsmanStats,
	        "swordsman"
	    ));

	    if (players.size() >= 2) {
	        int player2Id = players.get(1).getPlayerId();

	        units.put(2, new UnitState(
	            2,
	            player2Id,
	            5f, 0f,
	            5f, 0f,
	            false,
	            archerStats,
	            "archer"
	        ));

	        buildings.put(1, new BuildingState(1, player1Id, "base", -4f, 0f, 300));
	        buildings.put(2, new BuildingState(2, player2Id, "base", 9f, 0f, 300));
	    } else {
	        int dummyEnemyOwnerId = -1;

	        units.put(2, new UnitState(
	            2,
	            dummyEnemyOwnerId,
	            5f, 0f,
	            5f, 0f,
	            false,
	            archerStats,
	            "archer"
	        ));

	        buildings.put(1, new BuildingState(1, player1Id, "base", -4f, -3f, 300));
	        buildings.put(2, new BuildingState(2, dummyEnemyOwnerId, "base", 7f, 0f, 300));
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


	private void tick() throws JsonProcessingException {
		tickNumber++;

		PlayerCommand cmd;
		while ((cmd = commandQueue.poll()) != null) {
			handleCommand(cmd);
		}

		simulateUnits(0.05f);
		simulateCombat(0.05f);
		simulateBuildingProduction(0.05f);
		
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

		case "cmd_attack":
			handleAttack(cmd.playerId, root);
			break;

		case "cmd_end_match":
			handleEndMatch(cmd.playerId);
			break;

		case "cmd_stop":
			handleStop(cmd.playerId, root);
			break;

		case "cmd_attack_building":
			handleAttackBuilding(cmd.playerId, root);
			break;

		case "cmd_train_unit":
		    handleTrainUnit(cmd.playerId, root);
		    break;

		default:
			System.out.println("[MATCH " + matchId + "] unknown cmd type=" + type + " json=" + cmd.json);
			break;
		}
	}

	private void handleMove(int playerId, JsonNode root) {
		int unitId = root.path("unitId").asInt(-1);
		float x = (float) root.path("x").asDouble(Double.NaN);
		float y = (float) root.path("y").asDouble(Double.NaN);

		if (unitId <= 0)
			return;
		if (!Float.isFinite(x) || !Float.isFinite(y))
			return;

		x = clamp(x, WORLD_MIN_X, WORLD_MAX_X);
		y = clamp(y, WORLD_MIN_Y, WORLD_MAX_Y);

		UnitState unit = units.get(unitId);
		if (unit == null)
			return;
		if (unit.getOwnerPlayerId() != playerId)
			return;

		unit.setTarget(x, y);
		unit.clearAttackTarget();

	}

	private void handleAttack(int playerId, JsonNode root) {
		int attackerId = root.path("unitId").asInt(-1);
		int targetId = root.path("targetId").asInt(-1);

		if (attackerId <= 0 || targetId <= 0)
			return;
		if (attackerId == targetId)
			return; // не можна атакувати себе

		UnitState attacker = units.get(attackerId);
		UnitState target = units.get(targetId);

		if (attacker == null || target == null)
			return;

		// можна командувати тільки своїм юнітом
		if (attacker.getOwnerPlayerId() != playerId)
			return;

		// не можна атакувати союзника
		if (attacker.getOwnerPlayerId() == target.getOwnerPlayerId())
			return;

		attacker.clearTarget();
		attacker.setAttackTarget(targetId);
		attacker.setAttackTargetIsBuilding(false);
	}

	private void handleAttackBuilding(int playerId, JsonNode root) {
		int attackerId = root.path("unitId").asInt(-1);
		int targetBuildingId = root.path("targetId").asInt(-1);

		if (attackerId <= 0 || targetBuildingId <= 0)
			return;

		UnitState attacker = units.get(attackerId);
		BuildingState target = buildings.get(targetBuildingId);

		if (attacker == null || target == null)
			return;
		if (attacker.getOwnerPlayerId() != playerId)
			return;
		if (attacker.getOwnerPlayerId() == target.getOwnerPlayerId())
			return;

		attacker.clearTarget();
		attacker.setAttackTarget(targetBuildingId);
		attacker.setAttackTargetIsBuilding(true);
	}

	private void handleStop(int playerId, JsonNode root) {
		int unitId = root.path("unitId").asInt(-1);
		if (unitId <= 0)
			return;

		UnitState unit = units.get(unitId);
		if (unit == null)
			return;
		if (unit.getOwnerPlayerId() != playerId)
			return;

		unit.clearTarget();
		unit.clearAttackTarget();
	}
	
	private void handleTrainUnit(int playerId, JsonNode root) {
	    int buildingId = root.path("buildingId").asInt(-1);
	    String unitType = root.path("unitType").asText("");

	    if (buildingId <= 0) return;
	    if (unitType.isEmpty()) return;

	    BuildingState building = buildings.get(buildingId);
	    if (building == null) return;

	    if (building.getOwnerPlayerId() != playerId) return;
	    if (building.isTraining()) return;

	    if (!"base".equals(building.getBuildingType())) return;

	    // Поки що тільки один тип юніта для MVP
	    if (!"swordsman".equals(unitType)) return;

	    building.startTraining(unitType, 3f); // 3 секунди тренування
	}


	private void handleEndMatch(int playerId) {
		matchManager.endMatchSession(matchId);
	}

	/*
	 * private void simulateUnits(float dt) { float speed = 3f; float maxStep =
	 * speed * dt;
	 * 
	 * for (UnitState u : units.values()) { if (!u.getHasTarget()) continue;
	 * 
	 * float dx = u.getTargetX() - u.getX(); float dy = u.getTargetY() - u.getY();
	 * float distSq = dx * dx + dy * dy;
	 * 
	 * if (distSq < 0.0001f) { u.setPosition(u.getTargetX(), u.getTargetY());
	 * u.clearTarget(); continue; }
	 * 
	 * float dist = (float) Math.sqrt(distSq);
	 * 
	 * if (dist <= maxStep) { u.setPosition(u.getTargetX(), u.getTargetY());
	 * u.clearTarget(); } else { float nx = dx / dist; float ny = dy / dist;
	 * u.setPosition(u.getX() + nx * maxStep, u.getY() + ny * maxStep); } } }
	 */

	private void simulateUnits(float dt) {
		for (UnitState u : units.values()) {
			if (!u.getHasTarget())
				continue;

			float speed = u.getMoveSpeed();
			float maxStep = speed * dt;

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

	private void simulateCombat(float dt) {
		for (UnitState u : units.values()) {
			u.updateAttackTimer(dt);
		}

		Integer deadUnitId = null;
		Integer deadBuildingId = null;

		Iterator<UnitState> it = units.values().iterator();
		while (it.hasNext()) {
			UnitState attacker = it.next();

			int targetId = attacker.getAttackTargetId();
			if (targetId <= 0)
				continue;

			// --- Атака по будівлі ---
			if (attacker.isAttackTargetBuilding()) {
				BuildingState targetBuilding = buildings.get(targetId);
				if (targetBuilding == null) {
					attacker.clearAttackTarget();
					continue;
				}

				if (attacker.getOwnerPlayerId() == targetBuilding.getOwnerPlayerId()) {
					attacker.clearAttackTarget();
					continue;
				}

				float dx = targetBuilding.getX() - attacker.getX();
				float dy = targetBuilding.getY() - attacker.getY();
				float dist = (float) Math.sqrt(dx * dx + dy * dy);

				if (dist > attacker.getAttackRange())
					continue;
				if (!attacker.canAttack())
					continue;

				targetBuilding.damage(attacker.getAttackDamage());
				attacker.resetAttackTimer();

				if (targetBuilding.isDead()) {
					deadBuildingId = targetBuilding.getId();
					attacker.clearAttackTarget();
					break;
				}

				continue;
			}

			// --- Атака по юніту ---
			UnitState target = units.get(targetId);
			if (target == null) {
				attacker.clearAttackTarget();
				continue;
			}

			if (attacker.getId() == target.getId()) {
				attacker.clearAttackTarget();
				continue;
			}

			if (attacker.getOwnerPlayerId() == target.getOwnerPlayerId()) {
				attacker.clearAttackTarget();
				continue;
			}

			float dx = target.getX() - attacker.getX();
			float dy = target.getY() - attacker.getY();
			float dist = (float) Math.sqrt(dx * dx + dy * dy);

			if (dist > attacker.getAttackRange())
				continue;
			if (!attacker.canAttack())
				continue;

			target.damage(attacker.getAttackDamage());
			attacker.resetAttackTimer();

			if (target.isDead()) {
				deadUnitId = target.getId();
				attacker.clearAttackTarget();
				break;
			}
		}

		if (deadUnitId != null) {
			units.remove(deadUnitId);
		}

		if (deadBuildingId != null) {
			buildings.remove(deadBuildingId);
		}
	}

	private void simulateBuildingProduction(float dt) {
	    Integer spawnFromBuildingId = null;
	    String spawnUnitType = null;

	    for (BuildingState b : buildings.values()) {
	        if (!b.isTraining()) continue;

	        b.updateTraining(dt);

	        if (b.isTrainingFinished()) {
	            spawnFromBuildingId = b.getId();
	            spawnUnitType = b.getTrainingUnitType();
	            b.clearTraining();
	            break;
	        }
	    }

	    if (spawnFromBuildingId != null && spawnUnitType != null) {
	        spawnUnitNearBuilding(spawnFromBuildingId, spawnUnitType);
	    }
	}

	private void spawnUnitNearBuilding(int buildingId, String unitType) {
	    BuildingState building = buildings.get(buildingId);
	    if (building == null) return;

	    int newUnitId = generateNextUnitId();

	    UnitStats stats;
	    if ("swordsman".equals(unitType)) {
	        stats = new UnitStats(100, 2f, 10, 1f, 3f);
	    } else if ("archer".equals(unitType)) {
	        stats = new UnitStats(70, 5f, 7, 1.2f, 2.5f);
	    } else {
	        return;
	    }

	    float spawnX = building.getX() + 1.5f;
	    float spawnY = building.getY();

	    UnitState newUnit = new UnitState(
	        newUnitId,
	        building.getOwnerPlayerId(),
	        spawnX,
	        spawnY,
	        spawnX,
	        spawnY,
	        false,
	        stats,
	        unitType
	    );

	    units.put(newUnitId, newUnit);
	}

	
	private static float clamp(float v, float min, float max) {
		if (v < min)
			return min;
		if (v > max)
			return max;
		return v;
	}

	private String buildStateJson() {
		StringBuilder sb = new StringBuilder(512);

		sb.append("{\"type\":\"state\",\"tick\":").append(tickNumber);

		// units
		sb.append(",\"units\":[");
		boolean first = true;
		for (UnitState u : units.values()) {
			if (!first)
				sb.append(',');
			first = false;

			sb.append("{\"id\":").append(u.getId())
			  .append(",\"owner\":").append(u.getOwnerPlayerId())
			  .append(",\"unitType\":\"").append(u.getUnitType()).append("\"")
			  .append(",\"x\":").append(u.getX())
			  .append(",\"y\":").append(u.getY())
			  .append(",\"hp\":").append(u.getHp())
			  .append(",\"maxHp\":").append(u.getMaxHp())
			  //.append(",\"training\":").append(b.isTraining())
			  //.append(",\"trainingRemaining\":").append(b.getTrainingRemaining())
			  .append('}');

		}
		sb.append("]");

		// buildings
		sb.append(",\"buildings\":[");
		first = true;
		for (BuildingState b : buildings.values()) {
			if (!first)
				sb.append(',');
			first = false;

			sb.append("{\"id\":").append(b.getId()).append(",\"owner\":").append(b.getOwnerPlayerId())
					.append(",\"type\":\"").append(b.getBuildingType()).append("\"").append(",\"x\":").append(b.getX())
					.append(",\"y\":").append(b.getY()).append(",\"hp\":").append(b.getHp()).append(",\"maxHp\":")
					.append(b.getMaxHp()).append('}');
		}
		sb.append("]");

		sb.append("}");
		return sb.toString();
	}

	private int generateNextUnitId() {
	    int maxId = 0;
	    for (Integer id : units.keySet()) {
	        if (id > maxId) maxId = id;
	    }
	    return maxId + 1;
	}

	
	public void stop() {
		if (scheduler != null)
			scheduler.shutdown();
	}

	public void enqueueCommand(int playerId, String json) {
		commandQueue.add(new PlayerCommand(playerId, json));
	}

	public String getMatchId() {
		return matchId;
	}
}