package com.splitmate.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class DatabaseInitializer {

    public static void initialize() {

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {

            // MEMBERS
statement.execute("""
    CREATE TABLE IF NOT EXISTS members (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL,
        archived INTEGER NOT NULL DEFAULT 0
    )
""");

// Add archive support to existing databases
addColumnIfMissing(
        connection,
        "members",
        "archived",
        "INTEGER NOT NULL DEFAULT 0"
);

            // GROUPS
            statement.execute("""
                CREATE TABLE IF NOT EXISTS expense_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_name TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // GROUP MEMBERS
            statement.execute("""
                CREATE TABLE IF NOT EXISTS group_members (
                    group_id INTEGER,
                    member_id INTEGER,
                    PRIMARY KEY (group_id, member_id),
                    FOREIGN KEY (group_id)
                        REFERENCES expense_groups(id),
                    FOREIGN KEY (member_id)
                        REFERENCES members(id)
                )
            """);

            // EXISTING EXPENSE TABLE IS PRESERVED
            statement.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    description TEXT,
                    amount REAL,
                    paid_by TEXT
                )
            """);

            // NEW EXPENSE SPLITS
            statement.execute("""
                CREATE TABLE IF NOT EXISTS expense_splits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_id INTEGER NOT NULL,
                    member_id INTEGER NOT NULL,
                    share_paise INTEGER NOT NULL,
                    FOREIGN KEY (expense_id)
                        REFERENCES expenses(id),
                    FOREIGN KEY (member_id)
                        REFERENCES members(id)
                )
            """);

            // SETTLEMENTS
            // SETTLEMENTS
statement.execute("""
    CREATE TABLE IF NOT EXISTS settlements (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        group_id INTEGER NOT NULL,
        from_member INTEGER NOT NULL,
        to_member INTEGER NOT NULL,
        amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
        settlement_date TEXT DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (group_id)
            REFERENCES expense_groups(id),
        FOREIGN KEY (from_member)
            REFERENCES members(id),
        FOREIGN KEY (to_member)
            REFERENCES members(id)
    )
""");


            // ADDITIONAL EXPENSE COLUMNS
            addColumnIfMissing(connection,
        "settlements", "group_id", "INTEGER");
            addColumnIfMissing(connection, "expenses",
                    "group_id", "INTEGER");

            addColumnIfMissing(connection, "expenses",
                    "category", "TEXT DEFAULT 'Other'");

            addColumnIfMissing(connection, "expenses",
                    "created_at", "TEXT");

            addColumnIfMissing(connection, "expenses",
                    "amount_paise", "INTEGER");

            // MIGRATE EXISTING AMOUNTS
            statement.execute("""
                UPDATE expenses
                SET amount_paise = CAST(ROUND(amount * 100) AS INTEGER)
                WHERE amount_paise IS NULL
            """);

            // INDEXES
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_expense_group
                ON expenses(group_id)
            """);

            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_split_expense
                ON expense_splits(expense_id)
            """);

            System.out.println("SplitMate database initialized!");

        } catch(SQLException e) {
    throw new IllegalStateException(
        "Database initialization failed. " +
        "Check database connection and schema.",
        e
    );
}
    }

    private static void addColumnIfMissing(
            Connection connection,
            String table,
            String column,
            String definition) throws SQLException {

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "PRAGMA table_info(" + table + ")")) {

            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE " + table +
                    " ADD COLUMN " + column + " " + definition
            );
        }
    }
}