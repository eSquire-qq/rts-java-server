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

        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setInt(1, playerId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    int gold = rs.getInt("gold_amount");
                    int lumber = rs.getInt("lumber_amount");
                    int usedSupply = rs.getInt("used_supply");
                    int maxSupply = rs.getInt("max_supply");

                    return new PlayerState(
                        playerId,
                        gold,
                        lumber,
                        usedSupply,
                        maxSupply
                    );
                }
            }

        } catch (Exception e) {
            System.err.println("[PlayerDAO] loadPlayer failed for playerId=" + playerId);
            e.printStackTrace();
        }

        return null;
    }

    public void saveResources(int playerId, int gold, int lumber, int usedSupply, int maxSupply) {
        String sql = """
            INSERT INTO player_resources (
                player_id,
                gold_amount,
                lumber_amount,
                used_supply,
                max_supply
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                gold_amount = VALUES(gold_amount),
                lumber_amount = VALUES(lumber_amount),
                used_supply = VALUES(used_supply),
                max_supply = VALUES(max_supply)
            """;

        try (
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, gold);
            stmt.setInt(3, lumber);
            stmt.setInt(4, usedSupply);
            stmt.setInt(5, maxSupply);

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