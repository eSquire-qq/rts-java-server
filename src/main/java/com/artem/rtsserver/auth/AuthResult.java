package com.artem.rtsserver.auth;

public class AuthResult {

    private boolean success;
    private int playerId;
    private String username;
    private String email;
    private String accessToken;
    private String refreshToken;
    private String message;

    public static AuthResult success(
            int playerId,
            String username,
            String email,
            String accessToken,
            String refreshToken
    ) {
        AuthResult r = new AuthResult();
        r.success = true;
        r.playerId = playerId;
        r.username = username;
        r.email = email;
        r.accessToken = accessToken;
        r.refreshToken = refreshToken;
        r.message = "OK";
        return r;
    }

    public static AuthResult error(String message) {
        AuthResult r = new AuthResult();
        r.success = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getMessage() {
        return message;
    }
}