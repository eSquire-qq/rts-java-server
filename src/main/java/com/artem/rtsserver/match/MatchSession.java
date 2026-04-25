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

import com.artem.rtsserver.database.PlayerDAO;
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
	private int saveTimer;

	private final List<LobbyPlayer> players;
	private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
	private ScheduledExecutorService scheduler;

	private final MatchManager matchManager;
	private final ObjectMapper mapper = new ObjectMapper();

	// ✅ PlayerDAO тепер передається через конструктор
	private final PlayerDAO playerDAO;

	private final Map<Integer, UnitState> units = new HashMap<>();
	private final Map<Integer, BuildingState> buildings = new HashMap<>();
	private final Map<Integer, PlayerState> playersState = new HashMap<>();
	private final Map<String, UnitStats> unitStatsMap = new HashMap<>();
	private final Map<Integer, ResourceNode> resourceNodes = new HashMap<>();

	private static final float WORLD_MIN_X = -50f;
	private static final float WORLD_MAX_X = 50f;
	private static final float WORLD_MIN_Y = -50f;
	private static final float WORLD_MAX_Y = 50f;

	// ✅ Додали PlayerDAO в конструктор
	public MatchSession(String matchId, List<LobbyPlayer> players, MatchManager matchManager, PlayerDAO playerDAO) {
		this.matchId = matchId;
		this.players = players;
		this.tickNumber = 0;
		this.matchManager = matchManager;
		this.playerDAO = playerDAO;
	}

	public void start() {
	    int player1Id = players.get(0).getPlayerId();

	    // --- UNIT STATS ---
	    unitStatsMap.put("swordsman", new UnitStats(100, 2f, 10, 1f, 3f, 100, 0, 2));
	    unitStatsMap.put("archer", new UnitStats(70, 5f, 7, 1.2f, 2.5f, 125, 25, 2));
	    unitStatsMap.put("worker", new UnitStats(50, 1f, 2, 1f, 3f, 50, 0, 1));

	    // --- RESOURCES ON MAP ---
	    resourceNodes.put(1, new ResourceNode(1, "gold", 0f, 5f, 1000));
	    resourceNodes.put(2, new ResourceNode(2, "lumber", 3f, 6f, 1000));

	    // --- PLAYER 1 (з БД) ---
	    PlayerState p1 = playerDAO.loadPlayer(player1Id);

	    if (p1 == null) {
	        PlayerDAO.createPlayer(player1Id);
	        p1 = new PlayerState(player1Id, 500, 200, 0, 10);
	    }

	    playersState.put(player1Id, p1);

	    // --- СТАТИ ---
	    UnitStats swordsmanStats = getStatsForUnitType("swordsman");
	    UnitStats archerStats = getStatsForUnitType("archer");
	    UnitStats workerStats = getStatsForUnitType("worker");

	    // --- СПАВН ЮНІТІВ ДЛЯ PLAYER 1 ---
	    units.put(1, new UnitState(
	        1,
	        player1Id,
	        0f, 0f,
	        0f, 0f,
	        false,
	        swordsmanStats,
	        "swordsman"
	    ));

	    units.put(3, new UnitState(
	        3,
	        player1Id,
	        -2f, 0f,
	        -2f, 0f,
	        false,
	        workerStats,
	        "worker"
	    ));

	    playersState.get(player1Id).addUsedSupply(swordsmanStats.getSupplyCost());
	    playersState.get(player1Id).addUsedSupply(workerStats.getSupplyCost());

	    // --- ENEMY ---
	    int enemyId;

	    if (players.size() >= 2) {
	        // ✅ РЕАЛЬНИЙ ГРАВЕЦЬ
	        enemyId = players.get(1).getPlayerId();

	        PlayerState p2 = playerDAO.loadPlayer(enemyId);
	        if (p2 == null) {
	            PlayerDAO.createPlayer(enemyId);
	            p2 = new PlayerState(enemyId, 500, 200, 0, 10);
	        }

	        playersState.put(enemyId, p2);
	    } else {
	        enemyId = -1;
	        playersState.put(enemyId, new PlayerState(enemyId, 500, 200, 0, 10));
	    }

	    // --- ENEMY UNIT ---
	    units.put(2, new UnitState(
	        2,
	        enemyId,
	        5f, 0f,
	        5f, 0f,
	        false,
	        archerStats,
	        "archer"
	    ));

	    playersState.get(enemyId).addUsedSupply(archerStats.getSupplyCost());

	    // --- BUILDINGS ---
	    buildings.put(1, new BuildingState(1, player1Id, "barracks", -4f, 0f, 300));
	    buildings.put(2, new BuildingState(2, player1Id, "archery", -6f, 0f, 300));

	    buildings.put(3, new BuildingState(3, enemyId, "barracks", 9f, 0f, 300));
	    buildings.put(4, new BuildingState(4, enemyId, "archery", 11f, 0f, 300));

	    // --- GAME LOOP ---
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
		saveTimer++;

		// ✅ Зберігаємо в БД раз на 100 тіків (~5 секунд)
		if (saveTimer >= 100) {
			saveTimer = 0;
			for (PlayerState p : playersState.values()) {
				if (p.getPlayerId() > 0) { // не зберігаємо dummy AI гравця
					playerDAO.saveResources(p.getPlayerId(), p.getGold(), p.getLumber());
				}
			}
		}

		PlayerCommand cmd;
		while ((cmd = commandQueue.poll()) != null) {
			handleCommand(cmd);
		}

		simulateUnits(0.05f);
		simulateCombat(0.05f);
		simulateBuildingProduction(0.05f);
		simulateGathering(0.05f);

		String stateJson = buildStateJson();
		for (LobbyPlayer player : players) {
			player.getConn().sendLine(stateJson);
		}
	}

	private void simulateGathering(float dt) {
		for (UnitState unit : units.values()) {
			if (unit.getGatherTarget() <= 0)
				continue;

			ResourceNode node = resourceNodes.get(unit.getGatherTarget());
			if (node == null)
				continue;

			float dx = node.x - unit.getX();
			float dy = node.y - unit.getY();
			float dist = (float) Math.sqrt(dx * dx + dy * dy);

			if (dist > 1.5f) {
				unit.setTarget(node.x, node.y);
				continue;
			}

			unit.addGatherTime(dt);

			if (unit.isReadyToGather()) {
				unit.resetGatherTimer();

				PlayerState player = playersState.get(unit.getOwnerPlayerId());
				if (player == null)
					continue;
				if (!"worker".equals(unit.getUnitType()))
					continue;

				if ("gold".equals(node.type)) {
					player.addGold(10);
				} else {
					player.addLumber(10);
				}

				node.amount -= 10;

				if (node.amount <= 0) {
					resourceNodes.remove(node.id);
					unit.setGatherTarget(-1);
				}
			}
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
		case "cmd_build":
			handleBuild(cmd.playerId, root);
			break;
		case "cmd_gather":
			handleGather(cmd.playerId, root);
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
			return;

		UnitState attacker = units.get(attackerId);
		UnitState target = units.get(targetId);

		if (attacker == null || target == null)
			return;
		if (attacker.getOwnerPlayerId() != playerId)
			return;
		if (attacker.getOwnerPlayerId() == target.getOwnerPlayerId())
			return;

		attacker.clearTarget();
		attacker.setAttackTarget(targetId);
		attacker.setAttackTargetIsBuilding(false);
	}

	private void handleGather(int playerId, JsonNode root) {
		int unitId = root.path("unitId").asInt(-1);
		int resourceId = root.path("resourceId").asInt(-1);

		UnitState unit = units.get(unitId);
		ResourceNode resource = resourceNodes.get(resourceId);

		if (unit == null || resource == null)
			return;
		if (unit.getOwnerPlayerId() != playerId)
			return;

		unit.setGatherTarget(resourceId);
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

		if (buildingId <= 0 || unitType.isEmpty())
			return;

		BuildingState building = buildings.get(buildingId);
		if (building == null)
			return;
		if (building.getOwnerPlayerId() != playerId)
			return;

		PlayerState player = playersState.get(playerId);
		if (player == null)
			return;

		UnitStats stats = getStatsForUnitType(unitType);
		if (stats == null)
			return;
		if (!canTrainInBuilding(unitType, building.getBuildingType()))
			return;
		if (!player.hasEnoughResources(stats.getGoldCost(), stats.getLumberCost()))
			return;
		if (!player.hasEnoughSupply(stats.getSupplyCost()))
			return;

		player.spendResources(stats.getGoldCost(), stats.getLumberCost());
		building.enqueueUnit(unitType);
	}

	private void handleBuild(int playerId, JsonNode root) {
		String buildingType = root.path("buildingType").asText("");
		float x = (float) root.path("x").asDouble();
		float y = (float) root.path("y").asDouble();

		if (buildingType.isEmpty())
			return;

		PlayerState player = playersState.get(playerId);
		if (player == null)
			return;

		int goldCost = 0, lumberCost = 0;
		switch (buildingType) {
		case "barracks":
			goldCost = 200;
			lumberCost = 50;
			break;
		case "archery":
			goldCost = 150;
			lumberCost = 100;
			break;
		case "house":
			goldCost = 100;
			lumberCost = 50;
			break;
		default:
			return;
		}

		if (!player.hasEnoughResources(goldCost, lumberCost)) {
			System.out.println("BUILD DENIED: not enough resources");
			return;
		}

		x = clamp(x, WORLD_MIN_X, WORLD_MAX_X);
		y = clamp(y, WORLD_MIN_Y, WORLD_MAX_Y);

		player.spendResources(goldCost, lumberCost);

		int newBuildingId = generateNextBuildingId();
		buildings.put(newBuildingId, new BuildingState(newBuildingId, playerId, buildingType, x, y, 300));
		applyBuildingEffect(player, buildingType);
	}

	private void applyBuildingEffect(PlayerState player, String buildingType) {
		if ("house".equals(buildingType)) {
			player.addMaxSupply(5);
		}
	}

	private int generateNextBuildingId() {
		int maxId = 0;
		for (Integer id : buildings.keySet()) {
			if (id > maxId)
				maxId = id;
		}
		return maxId + 1;
	}

	private boolean canTrainInBuilding(String unitType, String buildingType) {
		if ("swordsman".equals(unitType) && "barracks".equals(buildingType))
			return true;
		if ("archer".equals(unitType) && "archery".equals(buildingType))
			return true;
		return false;
	}

	private void handleEndMatch(int playerId) {
		// ✅ Зберігаємо стан перед завершенням матчу
		for (PlayerState p : playersState.values()) {
			if (p.getPlayerId() > 0) {
				playerDAO.saveResources(p.getPlayerId(), p.getGold(), p.getLumber());
			}
		}
		matchManager.endMatchSession(matchId);
	}

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
			UnitState deadUnit = units.get(deadUnitId);
			if (deadUnit != null) {
				PlayerState owner = playersState.get(deadUnit.getOwnerPlayerId());
				if (owner != null)
					owner.removeUsedSupply(deadUnit.getSupplyCost());
			}
			units.remove(deadUnitId);
		}

		if (deadBuildingId != null) {
			buildings.remove(deadBuildingId);
		}
	}

	private void simulateBuildingProduction(float dt) {
		// ✅ Прибрали мертвий код з spawnFromBuildingId — він ніколи не спрацьовував
		for (BuildingState b : buildings.values()) {
			b.updateTraining(dt);

			if (b.hasUnitReady()) {
				String unitType = b.takeTrainedUnit();
				spawnUnitNearBuilding(b.getId(), unitType);
			}
		}
	}

	private void spawnUnitNearBuilding(int buildingId, String unitType) {
		BuildingState building = buildings.get(buildingId);
		if (building == null)
			return;

		UnitStats stats = getStatsForUnitType(unitType);
		if (stats == null)
			return;

		int newUnitId = generateNextUnitId();
		float spawnX = building.getX() + 1.5f;
		float spawnY = building.getY();

		units.put(newUnitId, new UnitState(newUnitId, building.getOwnerPlayerId(), spawnX, spawnY, spawnX, spawnY,
				false, stats, unitType));

		PlayerState player = playersState.get(building.getOwnerPlayerId());
		if (player != null) {
			player.addUsedSupply(stats.getSupplyCost());
		}
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
			sb.append("{\"id\":").append(u.getId()).append(",\"owner\":").append(u.getOwnerPlayerId())
					.append(",\"unitType\":\"").append(u.getUnitType()).append("\"").append(",\"x\":").append(u.getX())
					.append(",\"y\":").append(u.getY()).append(",\"hp\":").append(u.getHp()).append(",\"maxHp\":")
					.append(u.getMaxHp()).append('}');
		}
		sb.append("]");

		// buildings
		// ✅ Дужка ']' тепер після циклу, а не всередині
		sb.append(",\"buildings\":[");
		first = true;
		for (BuildingState b : buildings.values()) {
			if (!first)
				sb.append(',');
			first = false;
			sb.append("{\"id\":").append(b.getId()).append(",\"owner\":").append(b.getOwnerPlayerId())
					.append(",\"type\":\"").append(b.getBuildingType()).append("\"").append(",\"x\":").append(b.getX())
					.append(",\"y\":").append(b.getY()).append(",\"hp\":").append(b.getHp()).append(",\"maxHp\":")
					.append(b.getMaxHp()).append(",\"currentUnit\":\"").append(b.getCurrentUnitType()).append("\"")
					.append(",\"trainTime\":").append(b.getTrainingTimer()).append(",\"queueSize\":")
					.append(b.getQueueSize()).append('}');
		}
		sb.append("]"); // ✅ ось тут — після for, не всередині

		// players
		sb.append(",\"players\":[");
		first = true;
		for (PlayerState p : playersState.values()) {
			if (!first)
				sb.append(',');
			first = false;
			sb.append("{\"playerId\":").append(p.getPlayerId()).append(",\"gold\":").append(p.getGold())
					.append(",\"lumber\":").append(p.getLumber()).append(",\"usedSupply\":").append(p.getUsedSupply())
					.append(",\"maxSupply\":").append(p.getMaxSupply()).append('}');
		}
		sb.append("]");

		// resources
		sb.append(",\"resources\":[");
		first = true;
		for (ResourceNode r : resourceNodes.values()) {
			if (!first)
				sb.append(',');
			first = false;
			sb.append("{\"id\":").append(r.id).append(",\"type\":\"").append(r.type).append("\"").append(",\"x\":")
					.append(r.x).append(",\"y\":").append(r.y).append(",\"amount\":").append(r.amount).append('}');
		}
		sb.append("]");

		sb.append("}");
		return sb.toString();
	}

	private int generateNextUnitId() {
		int maxId = 0;
		for (Integer id : units.keySet()) {
			if (id > maxId)
				maxId = id;
		}
		return maxId + 1;
	}

	private UnitStats getStatsForUnitType(String unitType) {
		return unitStatsMap.get(unitType);
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