package com.artem.rtsserver.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Component
public class DatabaseManager {

    private static DataSource dataSource;

    @Autowired
    public DatabaseManager(DataSource dataSource) {
        DatabaseManager.dataSource = dataSource;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }    
}