package com.splitmate.services;

import com.splitmate.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

public class BalanceService {

    public Map<String, Long> getGroupBalances(int groupId)
            throws SQLException {

        Map<String, Long> balances = new LinkedHashMap<>();

        try (Connection con = DatabaseConnection.getConnection()) {

            // Initialize group members
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT m.name
                    FROM members m
                    JOIN group_members gm ON gm.member_id = m.id
                    WHERE gm.group_id = ?
                    ORDER BY m.name
                    """)) {

                ps.setInt(1, groupId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        balances.put(rs.getString(1), 0L);
                    }
                }
            }

            // Add amounts paid
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT paid_by, COALESCE(SUM(amount_paise), 0)
                    FROM expenses
                    WHERE group_id = ?
                    GROUP BY paid_by
                    """)) {

                ps.setInt(1, groupId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        balances.merge(
                                rs.getString(1),
                                rs.getLong(2),
                                Long::sum
                        );
                    }
                }
            }

            // Subtract individual expense shares
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT m.name, COALESCE(SUM(s.share_paise), 0)
                    FROM expense_splits s
                    JOIN expenses e ON e.id = s.expense_id
                    JOIN members m ON m.id = s.member_id
                    WHERE e.group_id = ?
                    GROUP BY m.id, m.name
                    """)) {

                ps.setInt(1, groupId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        balances.merge(
                                rs.getString(1),
                                -rs.getLong(2),
                                Long::sum
                        );
                    }
                }
            }

            // Apply recorded settlements
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT fm.name, tm.name, s.amount_paise
                    FROM settlements s
                    JOIN members fm ON fm.id = s.from_member
                    JOIN members tm ON tm.id = s.to_member
                    WHERE s.group_id = ?
                    """)) {

                ps.setInt(1, groupId);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        String from = rs.getString(1);
                        String to = rs.getString(2);
                        long amount = rs.getLong(3);

                        // Payer owes less
                        balances.merge(from, amount, Long::sum);

                        // Receiver gets less
                        balances.merge(to, -amount, Long::sum);
                    }
                }
            }
        }

        return balances;
    }

    public String getBalanceSummary(int groupId)
            throws SQLException {

        Map<String, Long> balances = getGroupBalances(groupId);

        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, Long> entry : balances.entrySet()) {

            long amount = entry.getValue();

            result.append(entry.getKey()).append("  →  ");

            if (amount > 0) {
                result.append("Gets back ");
            } else if (amount < 0) {
                result.append("Owes ");
            } else {
                result.append("Settled ");
            }

            result.append(
                    ExpenseService.rupees(Math.abs(amount))
            ).append("\n");
        }

        return result.toString();
    }
}