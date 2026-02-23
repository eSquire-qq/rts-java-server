package com.artem.rtsserver.dev;

import com.artem.rtsserver.lobby.LobbyManager;
import com.artem.rtsserver.match.MatchManager;
import com.artem.rtsserver.net.router.MessageRouter;
import com.artem.rtsserver.net.server.ClientConnection;

public class DevTestMain {

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        MatchManager matchManager = new MatchManager();
        LobbyManager lobbyManager = new LobbyManager(matchManager);
        MessageRouter router = new MessageRouter(lobbyManager, matchManager);

        ClientConnection p1 = new ClientConnection(1);
        ClientConnection p2 = new ClientConnection(2);

        System.out.println("=== TEST 1: Lobby create + join + disconnect in lobby ===");
        String lobbyId = lobbyManager.createLobby(p1);
        System.out.println("TEST lobbyId=" + lobbyId);

        boolean joined = lobbyManager.joinLobby(lobbyId, p2);
        System.out.println("joinLobby=" + joined);

        System.out.println("-> disconnect player2 while still in lobby");
        lobbyManager.onDisconnection(p2.getPlayerId());
        sleepMs(300);

        System.out.println("-> re-join player2");
        joined = lobbyManager.joinLobby(lobbyId, p2);
        System.out.println("joinLobby=" + joined);
        sleepMs(300);

        System.out.println("\n=== TEST 2: Start match + move + disconnect in match ===");
        lobbyManager.setReady(p1.getPlayerId(), true);
        lobbyManager.setReady(p2.getPlayerId(), true);

        // Let match start and a few ticks run
        sleepMs(400);

        System.out.println("-> cmd_move unit 1 by player1 (10,3)");
        router.handle(p1, "{\"type\":\"cmd_move\",\"unitId\":1,\"x\":10,\"y\":3}");
        sleepMs(400);

        System.out.println("-> attempt to move чужий unit 1 by player2 (999,999) [should be ignored]");
        router.handle(p2, "{\"type\":\"cmd_move\",\"unitId\":1,\"x\":999,\"y\":999}");
        sleepMs(300);

        System.out.println("-> cmd_move unit 2 by player2 (-5,0)");
        router.handle(p2, "{\"type\":\"cmd_move\",\"unitId\":2,\"x\":-5,\"y\":0}");
        sleepMs(400);

        System.out.println("-> disconnect player2 during match (should end match)");
        lobbyManager.onDisconnection(p2.getPlayerId());
        sleepMs(400);

        System.out.println("p1.isInMatch=" + p1.isInMatch() + " p2.isInMatch=" + p2.isInMatch());
        System.out.println("-> after match end, try cmd_move again (should not be enqueued if p1 not in match)");
        router.handle(p1, "{\"type\":\"cmd_move\",\"unitId\":1,\"x\":0,\"y\":0}");
        sleepMs(300);

        System.out.println("\n=== TEST done ===");
    }
}