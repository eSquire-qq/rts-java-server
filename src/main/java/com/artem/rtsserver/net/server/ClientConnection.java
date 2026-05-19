package com.artem.rtsserver.net.server;

import java.io.PrintWriter;
import java.net.Socket;

public class ClientConnection {

    private int playerId;
    private String username;
    private String accessToken;

    private final Socket socket;
    private final PrintWriter out;

    private volatile String matchId;

    public ClientConnection(int playerId, Socket socket, PrintWriter out) {
        this.playerId = playerId;
        this.socket = socket;
        this.out = out;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void authenticate(int playerId, String username, String accessToken) {
        this.playerId = playerId;
        this.username = username;
        this.accessToken = accessToken;
    }

    public boolean isAuthenticated() {
        return playerId > 0 && accessToken != null && !accessToken.isBlank();
    }

    public String getUsername() {
        return username;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void sendLine(String json) {
        out.println(json);
        out.flush();

        System.out.println("[TO player=" + playerId + "] " + json);
    }

    public boolean isInMatch() {
        return matchId != null;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public String getMatchId() {
        return matchId;
    }

    public void clearMatchId() {
        this.matchId = null;
    }

    public void closeQuietly() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}