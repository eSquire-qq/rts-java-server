package com.artem.rtsserver.net.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PlayerRepository {

    private final Connection conn;

    public PlayerRepository(Connection conn) {
        this.conn = conn;
    }

    public PlayerData loadPlayer(int playerId) throws SQLException {
        String sql = "SELECT gold, lumber FROM players WHERE id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, playerId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return new PlayerData(
                playerId,
                rs.getInt("gold"),
                rs.getInt("lumber")
            );
        }

        return null;
    }

    public void savePlayer(int playerId, int gold, int lumber) throws SQLException {
        String sql = "UPDATE players SET gold=?, lumber=? WHERE id=?";
        PreparedStatement ps = conn.prepareStatement(sql);

        ps.setInt(1, gold);
        ps.setInt(2, lumber);
        ps.setInt(3, playerId);

        ps.executeUpdate();
    }
}