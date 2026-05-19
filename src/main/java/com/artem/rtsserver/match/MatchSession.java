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

	private final String matchId;
	private int tickNumber;
	private int saveTimer;

	private final List<LobbyPlayer> players;
	private final Queue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
	private ScheduledExecutorService scheduler;

	private final MatchManager matchManager;
	private final ObjectMapper mapper = new ObjectMapper();
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
	private static final float GATHER_RANGE = 1.5f;

	private volatile boolean running = true;

	public MatchSession(String matchId, List<LobbyPlayer> players, MatchManager matchManager, PlayerDAO playerDAO) {
		this.matchId = matchId;
		this.players = players;
		this.matchManager = matchManager;
		this.playerDAO = playerDAO;
		this.tickNumber = 0;
	}

	public List<LobbyPlayer> getPlayers() {
		return players;
	}

	public void start() {
		running = true;

		int player1Id = players.get(0).getPlayerId();

		unitStatsMap.put("swordsman", new UnitStats(100, 2f, 10, 1f, 3f, 100, 0, 2));
		unitStatsMap.put("archer", new UnitStats(70, 5f, 7, 1.2f, 2.5f, 125, 25, 2));
		unitStatsMap.put("worker", new UnitStats(50, 1f, 2, 1f, 3f, 50, 0, 1));

		resourceNodes.put(1, new ResourceNode(1, "gold", 1.5f, 1f, 1000));
		resourceNodes.put(2, new ResourceNode(2, "lumber", 3f, -1f, 1000));

		PlayerState loadedP1 = playerDAO.loadPlayer(player1Id);
		PlayerState p1;

		if (loadedP1 == null) {
			p1 = new PlayerState(player1Id, 500, 200, 0, 10);
		} else {
			p1 = new PlayerState(player1Id, loadedP1.getGold(), loadedP1.getLumber(), 0, 10);
		}

		playersState.put(player1Id, p1);

		UnitStats swordsmanStats = getStatsForUnitType("swordsman");
		UnitStats archerStats = getStatsForUnitType("archer");
		UnitStats workerStats = getStatsForUnitType("worker");

		units.put(1, new UnitState(1, player1Id, 0f, 0f, 0f, 0f, false, swordsmanStats, "swordsman"));

		units.put(3, new UnitState(3, player1Id, -2f, 0f, -2f, 0f, false, workerStats, "worker"));
		units.put(4, new UnitState(4, player1Id, 0f, 0f, 1f, 0f, false, workerStats, "worker"));

		p1.addUsedSupply(swordsmanStats.getSupplyCost());

		p1.addUsedSupply(workerStats.getSupplyCost());

		p1.addUsedSupply(workerStats.getSupplyCost());

		int enemyId;

		if (players.size() >= 2) {
			enemyId = players.get(1).getPlayerId();

			PlayerState loadedP2 = playerDAO.loadPlayer(enemyId);
			PlayerState p2;

			if (loadedP2 == null) {
				p2 = new PlayerState(enemyId, 500, 200, 0, 10);
			} else {
				p2 = new PlayerState(enemyId, loadedP2.getGold(), loadedP2.getLumber(), 0, 10);
			}

			playersState.put(enemyId, p2);
		} else {
			enemyId = -1;
			playersState.put(enemyId, new PlayerState(enemyId, 500, 200, 0, 10));
		}

		units.put(2, new UnitState(2, enemyId, 5f, 0f, 5f, 0f, false, archerStats, "archer"));
		playersState.get(enemyId).addUsedSupply(archerStats.getSupplyCost());

		buildings.put(1, new BuildingState(1, player1Id, "barracks", -1f, 0f, 300));
		buildings.put(2, new BuildingState(2, player1Id, "archery", -3f, 5f, 300));

		buildings.put(3, new BuildingState(3, enemyId, "barracks", 8f, 3f, 300));
		buildings.put(4, new BuildingState(4, enemyId, "archery", 10f, 0f, 300));

		scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> {
			if (!running)
				return;

			try {
				tick();
			} catch (Exception e) {
				if (running)
					e.printStackTrace();
			}
		}, 0, 50, TimeUnit.MILLISECONDS);
	}

	private void tick() throws JsonProcessingException {
		if (!running)
			return;

		tickNumber++;
		saveTimer++;

		if (saveTimer >= 100) {
			saveTimer = 0;

			for (PlayerState p : playersState.values()) {
				if (p.getPlayerId() > 0) {
					playerDAO.saveResources(p.getPlayerId(), p.getGold(), p.getLumber(), p.getUsedSupply(),
							p.getMaxSupply());
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

	private void handleCommand(PlayerCommand cmd) throws JsonProcessingException {
		if (!running)
			return;

		JsonNode root = mapper.readTree(cmd.json);
		String type = root.path("type").asText("");

		switch (type) {
		case "cmd_move":
			handleMove(cmd.playerId, root);
			break;

		case "cmd_attack":
			handleAttack(cmd.playerId, root);
			break;

		case "cmd_attack_building":
			handleAttackBuilding(cmd.playerId, root);
			break;

		case "cmd_gather":
			handleGather(cmd.playerId, root);
			break;

		case "cmd_stop":
			handleStop(cmd.playerId, root);
			break;

		case "cmd_train_unit":
			handleTrainUnit(cmd.playerId, root);
			break;

		case "cmd_build":
			handleBuild(cmd.playerId, root);
			break;

		case "cmd_end_match":
			handleEndMatch(cmd.playerId);
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

		UnitState unit = units.get(unitId);

		if (unit == null)
			return;

		if (unit.getOwnerPlayerId() != playerId)
			return;

		x = clamp(x, WORLD_MIN_X, WORLD_MAX_X);
		y = clamp(y, WORLD_MIN_Y, WORLD_MAX_Y);

		unit.clearAttackTarget();
		unit.clearGatherTarget();
		unit.setTarget(x, y);
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
		attacker.clearGatherTarget();
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
		attacker.clearGatherTarget();
		attacker.setAttackTarget(targetBuildingId);
		attacker.setAttackTargetIsBuilding(true);
	}

	private void handleGather(int playerId, JsonNode root) {
		int unitId = root.path("unitId").asInt(-1);
		int resourceId = root.path("resourceId").asInt(-1);

		if (unitId <= 0 || resourceId <= 0)
			return;

		UnitState unit = units.get(unitId);
		ResourceNode resource = resourceNodes.get(resourceId);

		if (unit == null || resource == null)
			return;

		if (unit.getOwnerPlayerId() != playerId)
			return;

		if (!"worker".equals(unit.getUnitType()))
			return;

		unit.clearAttackTarget();
		unit.clearTarget();
		unit.setGatherTarget(resourceId);
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
		unit.clearGatherTarget();
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

		if (!player.hasEnoughResources(stats.getGoldCost(), stats.getLumberCost())) {
			sendErrorToPlayer(playerId, "not_enough_resources");
			return;
		}

		if (!player.hasEnoughSupply(stats.getSupplyCost())) {
			sendErrorToPlayer(playerId, "not_enough_supply");
			return;
		}

		player.spendResources(stats.getGoldCost(), stats.getLumberCost());
		building.enqueueUnit(unitType);
	}

	private void handleBuild(int playerId, JsonNode root) {
		String buildingType = root.path("buildingType").asText("");

		if (buildingType.isBlank())
			return;

		PlayerState player = playersState.get(playerId);

		if (player == null)
			return;

		int goldCost = getBuildingGoldCost(buildingType);
		int lumberCost = getBuildingLumberCost(buildingType);

		if (goldCost < 0 || lumberCost < 0)
			return;

		if (!player.hasEnoughResources(goldCost, lumberCost)) {
			System.out.println("BUILD DENIED: not enough resources. type=" + buildingType);
			sendErrorToPlayer(playerId, "not_enough_resources");
			return;
		}

		float x = clamp((float) root.path("x").asDouble(), WORLD_MIN_X, WORLD_MAX_X);
		float y = clamp((float) root.path("y").asDouble(), WORLD_MIN_Y, WORLD_MAX_Y);

		player.spendResources(goldCost, lumberCost);

		int newBuildingId = generateNextBuildingId();

		buildings.put(newBuildingId, new BuildingState(newBuildingId, playerId, buildingType, x, y, 300));

		applyBuildingEffect(player, buildingType);

		System.out.println("BUILD SUCCESS type=" + buildingType + " gold=" + goldCost + " lumber=" + lumberCost);
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

				if (dist > attacker.getAttackRange()) {
					attacker.setTarget(targetBuilding.getX(), targetBuilding.getY());
					continue;
				}

				attacker.clearTarget();

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

			if (dist > attacker.getAttackRange()) {
				attacker.setTarget(target.getX(), target.getY());
				continue;
			}

			attacker.clearTarget();

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

				if (owner != null) {
					owner.removeUsedSupply(deadUnit.getSupplyCost());
				}
			}

			units.remove(deadUnitId);
		}

		if (deadBuildingId != null) {
			buildings.remove(deadBuildingId);
		}
	}

	private void simulateGathering(float dt) {
		for (UnitState unit : units.values()) {
			if (unit.getGatherTarget() <= 0)
				continue;

			if (!"worker".equals(unit.getUnitType())) {
				unit.clearGatherTarget();
				continue;
			}

			ResourceNode node = resourceNodes.get(unit.getGatherTarget());

			if (node == null) {
				unit.clearGatherTarget();
				continue;
			}

			float dx = node.x - unit.getX();
			float dy = node.y - unit.getY();
			float dist = (float) Math.sqrt(dx * dx + dy * dy);

			if (dist > GATHER_RANGE) {
				unit.setTarget(node.x, node.y);
				continue;
			}

			unit.clearTarget();
			unit.addGatherTime(dt);

			if (unit.isReadyToGather()) {
				unit.resetGatherTimer();

				PlayerState player = playersState.get(unit.getOwnerPlayerId());

				if (player == null)
					continue;

				if ("gold".equals(node.type)) {
					player.addGold(10);
				} else {
					player.addLumber(10);
				}

				node.amount -= 10;

				if (node.amount <= 0) {
					resourceNodes.remove(node.id);
					unit.clearGatherTarget();
				}
			}
		}
	}

	private void simulateBuildingProduction(float dt) {
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

	private void handleEndMatch(int playerId) {
		saveAllPlayers();
		matchManager.endMatchSession(matchId);
	}

	private void saveAllPlayers() {
		for (PlayerState p : playersState.values()) {
			if (p.getPlayerId() > 0) {
				playerDAO.saveResources(p.getPlayerId(), p.getGold(), p.getLumber(), p.getUsedSupply(),
						p.getMaxSupply());
			}
		}
	}

	private void sendErrorToPlayer(int playerId, String reason) {
		for (LobbyPlayer p : players) {
			if (p.getPlayerId() == playerId) {
				p.getConn().sendLine("{\"type\":\"error\",\"reason\":\"" + reason + "\"}");
				return;
			}
		}
	}

	private int getBuildingGoldCost(String buildingType) {
		return switch (buildingType) {
		case "house" -> 75;
		case "barracks" -> 200;
		case "archery" -> 150;
		default -> -1;
		};
	}

	private int getBuildingLumberCost(String buildingType) {
		return switch (buildingType) {
		case "house" -> 25;
		case "barracks" -> 50;
		case "archery" -> 100;
		default -> -1;
		};
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

	private int generateNextUnitId() {
		int maxId = 0;

		for (Integer id : units.keySet()) {
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

	private UnitStats getStatsForUnitType(String unitType) {
		return unitStatsMap.get(unitType);
	}

	private static float clamp(float value, float min, float max) {
		if (value < min)
			return min;

		if (value > max)
			return max;

		return value;
	}

	private String buildStateJson() {
		StringBuilder sb = new StringBuilder(512);

		sb.append("{\"type\":\"state\",\"tick\":").append(tickNumber);

		sb.append(",\"units\":[");
		boolean first = true;

		for (UnitState u : units.values()) {
			if (!first)
				sb.append(',');

			first = false;

			sb.append("{\"id\":").append(u.getId()).append(",\"owner\":").append(u.getOwnerPlayerId())
					.append(",\"unitType\":\"").append(u.getUnitType()).append("\"").append(",\"x\":").append(u.getX())
					.append(",\"y\":").append(u.getY()).append(",\"hp\":").append(u.getHp()).append(",\"maxHp\":")
					.append(u.getMaxHp()).append(",\"isAttacking\":").append(u.isAttacking()).append('}');
		}

		sb.append("]");

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

		sb.append("]");

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

		sb.append(",\"resources\":[");
		first = true;

		for (ResourceNode r : resourceNodes.values()) {
			if (!first)
				sb.append(',');

			first = false;

			sb.append("{\"id\":").append(r.id).append(",\"type\":\"").append(r.type).append("\"").append(",\"x\":")
					.append(r.x).append(",\"y\":").append(r.y).append(",\"amount\":").append(r.amount).append('}');
		}

		sb.append("]}");

		return sb.toString();
	}

	public void stop() {
		System.out.println("STOP MATCH SESSION: " + matchId);

		running = false;
		commandQueue.clear();

		saveAllPlayers();

		for (LobbyPlayer player : players) {
			player.getConn().clearMatchId();
		}

		if (scheduler != null) {
			scheduler.shutdownNow();

			try {
				if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
					System.out.println("Scheduler did not terminate cleanly");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				e.printStackTrace();
			}

			scheduler = null;
		}
	}

	public void enqueueCommand(int playerId, String json) {
		if (!running)
			return;

		commandQueue.add(new PlayerCommand(playerId, json));
	}

	public String getMatchId() {
		return matchId;
	}
}