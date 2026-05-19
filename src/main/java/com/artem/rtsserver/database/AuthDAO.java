package com.artem.rtsserver.database;

import com.artem.rtsserver.auth.AuthResult;
import com.artem.rtsserver.auth.JwtService;
import com.artem.rtsserver.auth.PasswordService;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

@Component
public class AuthDAO {

    private final DataSource dataSource;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public AuthDAO(
            DataSource dataSource,
            PasswordService passwordService,
            JwtService jwtService
    ) {
        this.dataSource = dataSource;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    public AuthResult register(String username, String email, String password) {

        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            return AuthResult.error("Username, email and password are required");
        }

        String checkSql = """
            SELECT id
            FROM players
            WHERE username = ? OR email = ?
        """;

        String insertPlayerSql = """
            INSERT INTO players (
                username,
                email,
                password_hash,
                rating,
                created_at,
                is_active
            )
            VALUES (?, ?, ?, 1000, CURRENT_TIMESTAMP, TRUE)
        """;

        String insertResourcesSql = """
            INSERT INTO player_resources (
                player_id,
                gold_amount,
                lumber_amount,
                max_supply,
                used_supply
            )
            VALUES (?, 500, 200, 10, 0)
        """;

        String insertStatsSql = """
            INSERT INTO player_stats (
                player_id,
                total_matches,
                wins,
                losses,
                draws,
                win_rate,
                total_play_time_seconds,
                total_gold_collected,
                total_lumber_collected,
                total_units_created,
                total_buildings_built,
                updated_at
            )
            VALUES (?, 0, 0, 0, 0, 0.00, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)
        """;

        try (Connection conn = dataSource.getConnection()) {

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, username);
                checkStmt.setString(2, email);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return AuthResult.error("User with this username or email already exists");
                    }
                }
            }

            conn.setAutoCommit(false);

            int playerId;
            String passwordHash = passwordService.hash(password);

            try (PreparedStatement ps = conn.prepareStatement(insertPlayerSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, email);
                ps.setString(3, passwordHash);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        conn.rollback();
                        return AuthResult.error("Failed to create player");
                    }

                    playerId = keys.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertResourcesSql)) {
                ps.setInt(1, playerId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(insertStatsSql)) {
                ps.setInt(1, playerId);
                ps.executeUpdate();
            }

            String accessToken = jwtService.generateAccessToken(playerId, username, email);
            String refreshToken = jwtService.generateRefreshToken(playerId, username, email);

            saveRefreshToken(conn, playerId, refreshToken);

            conn.commit();

            return AuthResult.success(playerId, username, email, accessToken, refreshToken);

        } catch (Exception e) {
            e.printStackTrace();
            return AuthResult.error("Register failed");
        }
    }

    public AuthResult login(String email, String password) {

        if (isBlank(email) || isBlank(password)) {
            return AuthResult.error("Email and password are required");
        }

        String sql = """
            SELECT id, username, email, password_hash
            FROM players
            WHERE email = ? AND is_active = TRUE
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return AuthResult.error("Invalid email or password");
                }

                int playerId = rs.getInt("id");
                String username = rs.getString("username");
                String dbEmail = rs.getString("email");
                String passwordHash = rs.getString("password_hash");

                if (!passwordService.matches(password, passwordHash)) {
                    return AuthResult.error("Invalid email or password");
                }

                String accessToken = jwtService.generateAccessToken(playerId, username, dbEmail);
                String refreshToken = jwtService.generateRefreshToken(playerId, username, dbEmail);

                conn.setAutoCommit(false);

                updateLastLogin(conn, playerId);
                saveRefreshToken(conn, playerId, refreshToken);

                conn.commit();

                return AuthResult.success(playerId, username, dbEmail, accessToken, refreshToken);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return AuthResult.error("Login failed");
        }
    }

    private void saveRefreshToken(Connection conn, int playerId, String refreshToken) throws SQLException {

        String sql = """
            INSERT INTO refresh_tokens (
                player_id,
                token_hash,
                expires_at,
                revoked,
                created_at
            )
            VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 7 DAY), FALSE, CURRENT_TIMESTAMP)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            //ps.setString(2, passwordService.hash(refreshToken));
            ps.setString(2, sha256(refreshToken));
            ps.executeUpdate();
        }
    }

    private void updateLastLogin(Connection conn, int playerId) throws SQLException {

        String sql = """
            UPDATE players
            SET last_login_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ps.executeUpdate();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
    
}