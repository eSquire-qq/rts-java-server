package com.artem.rtsserver.database;

import com.artem.rtsserver.match.PlayerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class PlayerDAO {

    private final DataSource dataSource;

    @Autowired
    public PlayerDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PlayerState loadPlayer(int playerId) {
        String sql = "SELECT * FROM player_resources WHERE player_id=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerState(
                        playerId,
                        rs.getInt("gold"),
                        rs.getInt("lumber"),
                        0,   // used_supply — в БД немає, стартуємо з 0
                        10   // max_supply — в БД немає, стартуємо з 10
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("[PlayerDAO] loadPlayer failed for playerId=" + playerId);
            e.printStackTrace();
        }
        return null;
    }

    public void saveResources(int playerId, int gold, int lumber) {
        String sql = "INSERT INTO player_resources (player_id, gold, lumber) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE gold=?, lumber=?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playerId);
            stmt.setInt(2, gold);
            stmt.setInt(3, lumber);
            stmt.setInt(4, gold);
            stmt.setInt(5, lumber);
            stmt.executeUpdate();

        } catch (Exception e) {
            System.err.println("[PlayerDAO] saveResources failed for playerId=" + playerId);
            e.printStackTrace();
        }
    }
    
    public static void createPlayer(int playerId) {
        String sql = "INSERT INTO players (id) VALUES (?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, playerId);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
}