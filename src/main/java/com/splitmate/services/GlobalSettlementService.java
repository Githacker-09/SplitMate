package com.splitmate.services;

import com.splitmate.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

/** Consolidates each member's net position across every group. */
public class GlobalSettlementService {

    private final BalanceService balanceService = new BalanceService();

    public GlobalSettlementService() {
        ensureTable();
    }

    private void ensureTable() {
        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS global_settlements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_member INTEGER NOT NULL,
                    to_member INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
                    settlement_date TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(from_member) REFERENCES members(id),
                    FOREIGN KEY(to_member) REFERENCES members(id)
                )
                """);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize global settlements", e);
        }
    }

    public static class SuggestedPayment {
        private final String from;
        private final String to;
        private final long amount;
        public SuggestedPayment(String from, String to, long amount) {
            this.from = from; this.to = to; this.amount = amount;
        }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public long getAmount() { return amount; }
    }

    public static class SettlementRecord {
        private final String from, to, date;
        private final long amount;
        public SettlementRecord(String from, String to, long amount, String date) {
            this.from = from; this.to = to; this.amount = amount; this.date = date;
        }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public long getAmount() { return amount; }
        public String getDate() { return date; }
    }

    /** Positive means the member should receive; negative means they owe. */
    public Map<String, Long> getGlobalBalances() throws SQLException {
        Map<String, Long> balances = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement groups = con.prepareStatement("SELECT id FROM expense_groups ORDER BY id");
             ResultSet rs = groups.executeQuery()) {
            while (rs.next()) {
                Map<String, Long> groupBalances = balanceService.getGroupBalances(rs.getInt(1));
                groupBalances.forEach((name, amount) -> balances.merge(name, amount, Long::sum));
            }
        }

        // Apply global payments once, separately from group-specific settlements.
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT fm.name, tm.name, gs.amount_paise
                 FROM global_settlements gs
                 JOIN members fm ON fm.id = gs.from_member
                 JOIN members tm ON tm.id = gs.to_member
                 """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String from = rs.getString(1);
                String to = rs.getString(2);
                long amount = rs.getLong(3);
                balances.merge(from, amount, Long::sum);
                balances.merge(to, -amount, Long::sum);
            }
        }
        return balances;
    }

    public List<SuggestedPayment> getSuggestions() throws SQLException {
        Map<String, Long> balances = getGlobalBalances();
        List<Map.Entry<String, Long>> debtors = new ArrayList<>();
        List<Map.Entry<String, Long>> creditors = new ArrayList<>();
        for (var entry : balances.entrySet()) {
            if (entry.getValue() < 0) debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), -entry.getValue()));
            else if (entry.getValue() > 0) creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }
        List<SuggestedPayment> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            var debtor = debtors.get(i);
            var creditor = creditors.get(j);
            long amount = Math.min(debtor.getValue(), creditor.getValue());
            if (amount > 0) result.add(new SuggestedPayment(debtor.getKey(), creditor.getKey(), amount));
            debtor.setValue(debtor.getValue() - amount);
            creditor.setValue(creditor.getValue() - amount);
            if (debtor.getValue() == 0) i++;
            if (creditor.getValue() == 0) j++;
        }
        return result;
    }

    public void recordPayment(String fromName, String toName, long amountPaise) throws SQLException {
        if (amountPaise <= 0) throw new IllegalArgumentException("Enter an amount greater than zero.");
        if (fromName == null || toName == null
        || fromName.isBlank() || toName.isBlank()) {
    throw new IllegalArgumentException(
            "Select both sender and receiver.");
}

if (fromName.equalsIgnoreCase(toName)) {
    throw new IllegalArgumentException(
            "A member cannot pay themselves.");
}

        Map<String, Long> balances = getGlobalBalances();
        long debt = balances.getOrDefault(fromName, 0L);
        long receivable = balances.getOrDefault(toName, 0L);
        if (debt >= 0) throw new IllegalArgumentException(fromName + " does not currently owe money globally.");
        if (receivable <= 0) throw new IllegalArgumentException(toName + " is not currently owed money globally.");
        if (amountPaise > -debt || amountPaise > receivable)
            throw new IllegalArgumentException("Payment exceeds the current net outstanding amount.");

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int fromId = memberId(con, fromName);
                int toId = memberId(con, toName);
                try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO global_settlements(from_member, to_member, amount_paise)
                    VALUES (?, ?, ?)
                    """)) {
                    ps.setInt(1, fromId); ps.setInt(2, toId); ps.setLong(3, amountPaise);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException(e.getMessage(), e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private int memberId(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM members WHERE name = ? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Member not found: " + name);
    }

    public List<SettlementRecord> getHistory() throws SQLException {
        List<SettlementRecord> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                 SELECT fm.name, tm.name, gs.amount_paise, gs.settlement_date
                 FROM global_settlements gs
                 JOIN members fm ON fm.id = gs.from_member
                 JOIN members tm ON tm.id = gs.to_member
                 ORDER BY gs.id DESC
                 """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new SettlementRecord(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
        }
        return rows;
    }
}
