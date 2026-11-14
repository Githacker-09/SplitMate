package com.splitmate.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Path;

public class DatabaseConnection {

    private static final Path DB_PATH =
            Path.of(System.getProperty("user.dir"))
                    .resolve("splitmate.db")
                    .toAbsolutePath()
                    .normalize();

    private static final String URL =
            "jdbc:sqlite:" + DB_PATH;

    public static Connection getConnection()
            throws SQLException {

        Connection connection =
                DriverManager.getConnection(URL);

        try (var statement = connection.createStatement()) {

            // Wait up to 5 seconds if the database is locked
            statement.execute("PRAGMA busy_timeout = 5000");

            // Enable foreign key constraints
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }

    public static String getDatabasePath() {
        return DB_PATH.toString();
    }
}