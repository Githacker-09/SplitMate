package com.splitmate.services;

import com.splitmate.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class SettlementService {

    private final BalanceService balanceService =
            new BalanceService();

    public static class SuggestedPayment {

        private final String from;
        private final String to;
        private final long amount;

        public SuggestedPayment(
                String from, String to, long amount) {

            this.from = from;
            this.to = to;
            this.amount = amount;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public long getAmount() {
            return amount;
        }
    }

    public static class SettlementRecord {

        private final String from;
        private final String to;
        private final long amount;
        private final String date;

        public SettlementRecord(
                String from, String to,
                long amount, String date) {

            this.from = from;
            this.to = to;
            this.amount = amount;
            this.date = date;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public long getAmount() {
            return amount;
        }

        public String getDate() {
            return date;
        }
    }

    // Generate payment suggestions
    public List<SuggestedPayment> getSuggestions(int groupId)
            throws SQLException {

        Map<String, Long> balances =
                balanceService.getGroupBalances(groupId);

        List<SuggestedPayment> suggestions =
                new ArrayList<>();

        List<Map.Entry<String, Long>> debtors =
                new ArrayList<>();

        List<Map.Entry<String, Long>> creditors =
                new ArrayList<>();

        for (Map.Entry<String, Long> entry : balances.entrySet()) {

            if (entry.getValue() < 0) {
                debtors.add(
                        new AbstractMap.SimpleEntry<>(
                                entry.getKey(), -entry.getValue()));
            }

            if (entry.getValue() > 0) {
                creditors.add(
                        new AbstractMap.SimpleEntry<>(
                                entry.getKey(), entry.getValue()));
            }
        }

        int i = 0;
        int j = 0;

        while (i < debtors.size() && j < creditors.size()) {

            var debtor = debtors.get(i);
            var creditor = creditors.get(j);

            long payment = Math.min(
                    debtor.getValue(),
                    creditor.getValue()
            );

            if (payment > 0) {
                suggestions.add(
                        new SuggestedPayment(
                                debtor.getKey(),
                                creditor.getKey(),
                                payment
                        )
                );
            }

            debtor.setValue(debtor.getValue() - payment);
            creditor.setValue(creditor.getValue() - payment);

            if (debtor.getValue() == 0) i++;
            if (creditor.getValue() == 0) j++;
        }

        return suggestions;
    }

    // Record a payment
    public void recordPayment(
            int groupId,
            String fromName,
            String toName,
            long amountPaise) throws SQLException {

        if (amountPaise <= 0) {
            throw new IllegalArgumentException(
                    "Payment must be greater than zero.");
        }

        if (fromName == null || toName == null
        || fromName.isBlank() || toName.isBlank()) {
    throw new IllegalArgumentException(
            "Select both sender and receiver.");
}

if (fromName.equals(toName)) {
            throw new IllegalArgumentException(
                    "A member cannot pay themselves.");
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                int fromId = getMemberId(con, groupId, fromName);
                int toId = getMemberId(con, groupId, toName);

                if (fromId == toId) {
                    throw new SQLException("Invalid members.");
                }

                // Validate the outstanding debt
                Map<String, Long> balances =
                        balanceService.getGroupBalances(groupId);

                long debt = balances.getOrDefault(fromName, 0L);

                if (debt >= 0 || amountPaise > -debt) {
                    throw new IllegalArgumentException(
                            "Payment exceeds the outstanding amount.");
                }

                long receivable =
                        balances.getOrDefault(toName, 0L);

                if (receivable <= 0) {
                    throw new IllegalArgumentException(
                            "The selected receiver is not owed money.");
                }

                if (amountPaise > receivable) {
                    throw new IllegalArgumentException(
                            "Payment exceeds the receiver's outstanding balance.");
                }

                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO settlements
                        (group_id, from_member, to_member, amount_paise)
                        VALUES (?, ?, ?, ?)
                        """)) {

                    ps.setInt(1, groupId);
                    ps.setInt(2, fromId);
                    ps.setInt(3, toId);
                    ps.setLong(4, amountPaise);

                    ps.executeUpdate();
                }

                con.commit();

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                throw new SQLException(e.getMessage(), e);

            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private int getMemberId(
            Connection con, int groupId, String name)
            throws SQLException {

        try (PreparedStatement ps = con.prepareStatement("""
                SELECT m.id
                FROM members m
                JOIN group_members gm ON gm.member_id = m.id
                WHERE gm.group_id = ? AND m.name = ?
                """)) {

            ps.setInt(1, groupId);
            ps.setString(2, name);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new SQLException(
                "Member does not belong to the selected group: " + name);
    }

    // Retrieve payment history
    public List<SettlementRecord> getHistory(int groupId)
            throws SQLException {

        List<SettlementRecord> records = new ArrayList<>();

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT fm.name, tm.name,
                            s.amount_paise, s.settlement_date
                     FROM settlements s
                     JOIN members fm ON fm.id = s.from_member
                     JOIN members tm ON tm.id = s.to_member
                     WHERE s.group_id = ?
                     ORDER BY s.id DESC
                     """)) {

            ps.setInt(1, groupId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    records.add(
                            new SettlementRecord(
                                    rs.getString(1),
                                    rs.getString(2),
                                    rs.getLong(3),
                                    rs.getString(4)
                            )
                    );
                }
            }
        }

        return records;
    }
}