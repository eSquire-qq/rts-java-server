package com.artem.rtsserver.net.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.artem.rtsserver.lobby.LobbyManager;
import com.artem.rtsserver.net.router.MessageRouter;

@Component
public class TcpServer {

    private final MessageRouter router;
    private final LobbyManager lobbyManager;

    private final AtomicInteger nextPlayerId = new AtomicInteger(1);

    public TcpServer(MessageRouter router, LobbyManager lobbyManager) {
        this.router = router;
        this.lobbyManager = lobbyManager;
    }

    public void start(int port) {
        Thread serverThread = new Thread(() -> runServer(port), "tcp-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void runServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                int playerId = nextPlayerId.getAndIncrement();

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                ClientConnection client = new ClientConnection(playerId, socket, out);

                System.out.println("Client connected: " + socket.getRemoteSocketAddress() + " as playerId=" + playerId);

                client.sendLine("{\"type\":\"hello\",\"playerId\":" + playerId + "}");

                Thread clientThread = new Thread(() -> handleClient(client, in), "client-" + playerId);
                clientThread.setDaemon(true);
                clientThread.start();
            }

        } catch (Exception e) {
            System.out.println("TCP Server failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClient(ClientConnection client, BufferedReader in) {
        int playerId = client.getPlayerId();

        try {
            String line;
            while ((line = in.readLine()) != null) {

                System.out.println("[FROM player=" + playerId + "] " + line);
                router.handle(client, line);
                System.out.println("RAW FROM CLIENT: [" + line + "]");
            }
        } catch (Exception e) {
            System.out.println("Client error playerId=" + playerId + ": " + e.getMessage());
        } finally {
            System.out.println("Client disconnected playerId=" + playerId);

            lobbyManager.onDisconnection(playerId);

            client.closeQuietly();
        }
        
    }
}