package com.artem.rtsserver.net.server;

import java.io.PrintWriter;
import java.net.Socket;

public class ClientConnection {

    private final int playerId;
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
        try { socket.close(); } catch (Exception ignored) {}
    }
}