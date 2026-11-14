package com.splitmate.services;

import com.splitmate.database.DatabaseConnection;
import com.splitmate.models.Expense;
import com.splitmate.models.Member;

import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public class ExpenseService {

    public static long toPaise(String amount) {
    if (amount == null || amount.isBlank()) {
        throw new IllegalArgumentException("Enter an amount.");
    }

    try {
        String cleaned = amount
                .replace("₹", "")
                .replace(",", "")
                .trim();

        java.math.BigDecimal value =
                new java.math.BigDecimal(cleaned);

        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero.");
        }

        return value
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValueExact();

    } catch (NumberFormatException | ArithmeticException e) {
        throw new IllegalArgumentException(
                "Enter a valid amount, e.g. ₹500 or ₹500.50.");
    }
}

    public static String rupees(long paise) {
    java.text.NumberFormat formatter =
            java.text.NumberFormat.getNumberInstance(
                    java.util.Locale.of("en", "IN"));

    formatter.setMinimumFractionDigits(2);
    formatter.setMaximumFractionDigits(2);

    return "₹" + formatter.format(
            BigDecimal.valueOf(paise, 2)
    );
}

    // ADD EXPENSE
    public void addExpense(String description, long amount,
                           int groupId, Member payer,
                           Map<Member, Long> shares) throws SQLException {

        if (description == null || description.isBlank()
                || amount <= 0 || payer == null
                || shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException(
                    "Enter valid expense details.");
        }

        long total = 0;

        for (Map.Entry<Member, Long> entry : shares.entrySet()) {

            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Invalid expense participant or share.");
            }

            long share = entry.getValue();

            if (share < 0) {
                throw new IllegalArgumentException(
                        "Shares cannot be negative.");
            }

            total = Math.addExact(total, share);
        }

        if (total != amount) {
            throw new IllegalArgumentException(
                    "Individual shares must equal the total expense.");
        }

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                // GET ONLY ACTIVE GROUP MEMBERS
                Set<Integer> groupMemberIds = new HashSet<>();

                String memberSql = """
                    SELECT gm.member_id
                    FROM group_members gm
                    JOIN members m ON gm.member_id = m.id
                    WHERE gm.group_id = ?
                      AND m.archived = 0
                """;

                try (PreparedStatement ps =
                             con.prepareStatement(memberSql)) {

                    ps.setInt(1, groupId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            groupMemberIds.add(rs.getInt(1));
                        }
                    }
                }

                // VALIDATE PAYER
                if (!groupMemberIds.contains(payer.getId())) {
                    throw new IllegalArgumentException(
                            "Payer must be an active member of the selected group.");
                }

                // VALIDATE EXPENSE PARTICIPANTS
                Set<Integer> participantIds = new HashSet<>();

                for (Member member : shares.keySet()) {

                    if (!groupMemberIds.contains(member.getId())
                            || !participantIds.add(member.getId())) {

                        throw new IllegalArgumentException(
                                "Invalid, archived, or duplicate expense participant.");
                    }
                }

                int expenseId;

                // INSERT EXPENSE
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO expenses
                        (description, amount, amount_paise, paid_by,
                         group_id, category, created_at)
                        VALUES (?, ?, ?, ?, ?, 'Other', CURRENT_TIMESTAMP)
                        """, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, description.trim());
                    ps.setDouble(2, amount / 100.0);
                    ps.setLong(3, amount);
                    ps.setString(4, payer.getName());
                    ps.setInt(5, groupId);

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {

                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not create expense.");
                        }

                        expenseId = keys.getInt(1);
                    }
                }

                // INSERT EXPENSE SPLITS
                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO expense_splits
                        (expense_id, member_id, share_paise)
                        VALUES (?, ?, ?)
                        """)) {

                    for (Map.Entry<Member, Long> entry
                            : shares.entrySet()) {

                        ps.setInt(1, expenseId);
                        ps.setInt(2, entry.getKey().getId());
                        ps.setLong(3, entry.getValue());

                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                con.commit();

            } catch (SQLException | RuntimeException e) {

                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(true);
            }
        }
    }

// UPDATE EXISTING EXPENSE
public void updateExpense(int expenseId, String description,
                          long amount, Member payer,
                          Map<Member, Long> shares) throws SQLException {

    if (description == null || description.isBlank()
            || amount <= 0 || payer == null
            || shares == null || shares.isEmpty()) {
        throw new IllegalArgumentException("Enter valid expense details.");
    }

    long total = 0;

    for (Long share : shares.values()) {
        if (share == null || share < 0) {
            throw new IllegalArgumentException("Invalid expense share.");
        }
        total = Math.addExact(total, share);
    }

    if (total != amount) {
        throw new IllegalArgumentException(
                "Individual shares must equal the total expense.");
    }

    try (Connection con = DatabaseConnection.getConnection()) {
        con.setAutoCommit(false);

        try {
            int groupId;

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT group_id FROM expenses WHERE id = ?")) {
                ps.setInt(1, expenseId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Expense not found.");
                    }
                    groupId = rs.getInt("group_id");
                }
            }

            Set<Integer> activeMembers = new HashSet<>();

            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT gm.member_id
                    FROM group_members gm
                    JOIN members m ON m.id = gm.member_id
                    WHERE gm.group_id = ? AND m.archived = 0
                    """)) {

                ps.setInt(1, groupId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        activeMembers.add(rs.getInt(1));
                    }
                }
            }

            if (!activeMembers.contains(payer.getId())) {
                throw new IllegalArgumentException(
                        "Payer must be an active group member.");
            }

            Set<Integer> participants = new HashSet<>();

            for (Member member : shares.keySet()) {
                if (member == null
                        || !activeMembers.contains(member.getId())
                        || !participants.add(member.getId())) {
                    throw new IllegalArgumentException(
                            "Invalid or archived expense participant.");
                }
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    UPDATE expenses
                    SET description = ?, amount = ?,
                        amount_paise = ?, paid_by = ?
                    WHERE id = ?
                    """)) {

                ps.setString(1, description.trim());
                ps.setDouble(2, amount / 100.0);
                ps.setLong(3, amount);
                ps.setString(4, payer.getName());
                ps.setInt(5, expenseId);

                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM expense_splits WHERE expense_id = ?")) {
                ps.setInt(1, expenseId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO expense_splits
                    (expense_id, member_id, share_paise)
                    VALUES (?, ?, ?)
                    """)) {

                for (Map.Entry<Member, Long> entry : shares.entrySet()) {
                    ps.setInt(1, expenseId);
                    ps.setInt(2, entry.getKey().getId());
                    ps.setLong(3, entry.getValue());
                    ps.addBatch();
                }

                ps.executeBatch();
            }

            con.commit();

        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }
}// GET EXISTING PARTICIPANTS AND THEIR SHARES
public Map<Integer, Long> getExpenseShares(int expenseId)
        throws SQLException {

    Map<Integer, Long> shares = new LinkedHashMap<>();

    String sql = """
            SELECT member_id, share_paise
            FROM expense_splits
            WHERE expense_id = ?
            """;

    try (Connection con = DatabaseConnection.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setInt(1, expenseId);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                shares.put(
                        rs.getInt("member_id"),
                        rs.getLong("share_paise")
                );
            }
        }
    }

    return shares;
}
    // GET ALL EXPENSES
    // Historical expenses remain visible, including archived members.
    public List<Expense> getExpenses() throws SQLException {

        List<Expense> expenses = new ArrayList<>();

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT id, description, amount_paise, paid_by, group_id
                     FROM expenses
                     WHERE group_id IS NOT NULL
                     ORDER BY id DESC
                     """);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                expenses.add(new Expense(
                        rs.getInt("id"),
                        rs.getString("description"),
                        rs.getLong("amount_paise"),
                        rs.getString("paid_by"),
                        rs.getInt("group_id")
                ));
            }
        }

        return expenses;
    }

    // DELETE EXPENSE
    public void deleteExpense(int id) throws SQLException {

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM expense_splits WHERE expense_id=?")) {

                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM expenses WHERE id=?")) {

                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                con.commit();

            } catch (SQLException e) {

                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(true);
            }
        }
    }
}