package com.artem.rtsserver.dev;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.artem.rtsserver.lobby.LobbyManager;
import com.artem.rtsserver.match.MatchManager;
import com.artem.rtsserver.net.router.MessageRouter;
import com.artem.rtsserver.net.server.ClientConnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DevTestMain {

    // ====== Test client that captures server outputs ======
    static class TestClientConnection extends ClientConnection {
        private final ConcurrentLinkedQueue<String> inbox = new ConcurrentLinkedQueue<>();

        public TestClientConnection(int playerId) {
            super(playerId);
        }

        @Override
        public void sendLine(String json) {
            inbox.add(json);
            System.out.println("[TO player=" + getPlayerId() + "] " + json);
        }

        public String poll() {
            return inbox.poll();
        }

        public void clearInbox() {
            inbox.clear();
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        MatchManager matchManager = new MatchManager();
        LobbyManager lobbyManager = new LobbyManager(matchManager);
        MessageRouter router = new MessageRouter(lobbyManager, matchManager);

        TestClientConnection p1 = new TestClientConnection(1);
        TestClientConnection p2 = new TestClientConnection(2);

        // ---------------- TEST 1 ----------------
        System.out.println("\n=== TEST 1: create lobby ===");
        router.handle(p1, "{\"type\":\"create_lobby\"}");

        String lobbyId = waitForTypeAndField(p1, "lobby_created", "lobbyId", 1500);
        System.out.println("TEST lobbyId=" + lobbyId);

        // ---------------- TEST 2 ----------------
        System.out.println("\n=== TEST 2: join lobby ===");
        router.handle(p2, "{\"type\":\"join_lobby\",\"lobbyId\":\"" + lobbyId + "\"}");
        // очікуємо lobby_state (в обох)
        waitForType(p1, "lobby_state", 1500);
        waitForType(p2, "lobby_state", 1500);

        // ---------------- TEST 3 ----------------
        System.out.println("\n=== TEST 3: disconnect player2 in lobby (should remove player2, lobby still exists) ===");
        lobbyManager.onDisconnection(p2.getPlayerId());

        // p1 має отримати оновлений lobby_state (без player2)
        waitForType(p1, "lobby_state", 1500);

        // player2 вже не в лобі — пробуємо знову приєднатись
        System.out.println("\n=== TEST 4: player2 re-join lobby after disconnect ===");
        router.handle(p2, "{\"type\":\"join_lobby\",\"lobbyId\":\"" + lobbyId + "\"}");
        waitForType(p1, "lobby_state", 1500);
        waitForType(p2, "lobby_state", 1500);

        // ---------------- TEST 5 ----------------
        System.out.println("\n=== TEST 5: set_ready both => match_start ===");
        router.handle(p1, "{\"type\":\"set_ready\",\"ready\":true}");
        router.handle(p2, "{\"type\":\"set_ready\",\"ready\":true}");

        String matchId1 = waitForTypeAndField(p1, "match_start", "matchId", 2000);
        String matchId2 = waitForTypeAndField(p2, "match_start", "matchId", 2000);

        System.out.println("TEST matchId p1=" + matchId1 + " p2=" + matchId2);
        System.out.println("p1.isInMatch=" + p1.isInMatch() + " p2.isInMatch=" + p2.isInMatch());

        // даємо тікам піти
        Thread.sleep(250);

        // ---------------- TEST 6 ----------------
        System.out.println("\n=== TEST 6: cmd_move in match (unit 1 should move) ===");
        p1.clearInbox();
        p2.clearInbox();

        router.handle(p1, "{\"type\":\"cmd_move\",\"unitId\":1,\"x\":10,\"y\":3}");

        // почекаємо кілька state пакетів і перевіримо що x/y у unitId=1 не (0,0)
        boolean moved = waitUntilUnitMoved(p1, 1, 1500);
        System.out.println("TEST unit moved=" + moved);

        // ---------------- TEST 7 ----------------
        System.out.println("\n=== TEST 7: disconnect player2 during match => match ends ===");
        lobbyManager.onDisconnection(p2.getPlayerId());

        // дати матчу зупинитись та почистити matchId
        Thread.sleep(250);
        System.out.println("after disconnect: p1.isInMatch=" + p1.isInMatch() + " p2.isInMatch=" + p2.isInMatch());

        // ---------------- TEST 8 ----------------
        System.out.println("\n=== TEST 8: cmd_move AFTER match end (should be rejected with not_in_match) ===");
        p1.clearInbox();
        router.handle(p1, "{\"type\":\"cmd_move\",\"unitId\":1,\"x\":0,\"y\":0}");

        String errReason = waitForTypeAndField(p1, "error", "reason", 1000);
        System.out.println("TEST error.reason=" + errReason);

        System.out.println("\n=== TEST done ===");
    }

    // ===== Helpers =====

    private static JsonNode parseSafe(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static void waitForType(TestClientConnection c, String type, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String msg = c.poll();
            if (msg != null) {
                JsonNode root = parseSafe(msg);
                if (root != null && type.equals(root.path("type").asText())) {
                    return;
                }
            } else {
                Thread.sleep(10);
            }
        }
        throw new RuntimeException("Timeout waiting type='" + type + "' for player " + c.getPlayerId());
    }

    private static String waitForTypeAndField(TestClientConnection c, String type, String field, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String msg = c.poll();
            if (msg != null) {
                JsonNode root = parseSafe(msg);
                if (root != null && type.equals(root.path("type").asText())) {
                    JsonNode v = root.get(field);
                    return v == null ? null : v.asText();
                }
            } else {
                Thread.sleep(10);
            }
        }
        throw new RuntimeException("Timeout waiting type='" + type + "' field='" + field + "' for player " + c.getPlayerId());
    }

    private static boolean waitUntilUnitMoved(TestClientConnection c, int unitId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            String msg = c.poll();
            if (msg == null) {
                Thread.sleep(10);
                continue;
            }

            JsonNode root = parseSafe(msg);
            if (root == null) continue;

            if (!"state".equals(root.path("type").asText())) continue;

            JsonNode units = root.path("units");
            if (!units.isArray()) continue;

            for (JsonNode u : units) {
                if (u.path("id").asInt(-1) == unitId) {
                    float x = (float) u.path("x").asDouble(0);
                    float y = (float) u.path("y").asDouble(0);

                    // старт у вас (0,0), тому будь-який рух = (x != 0 || y != 0)
                    if (Math.abs(x) > 0.0001f || Math.abs(y) > 0.0001f) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}