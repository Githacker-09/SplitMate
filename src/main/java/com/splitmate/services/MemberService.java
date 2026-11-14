package com.splitmate.services;

import com.splitmate.database.DatabaseConnection;
import com.splitmate.models.Member;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MemberService {

    // ADD MEMBER
    public void addMember(String name) throws SQLException {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Member name cannot be empty.");
        }

        String sql = "INSERT INTO members(name) VALUES(?)";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.executeUpdate();
        }
    }

    // GET ACTIVE MEMBERS
    public List<Member> getAllMembers() throws SQLException {

        List<Member> members = new ArrayList<>();

        String sql = """
                SELECT id, name
                FROM members
                WHERE archived = 0
                ORDER BY name
                """;

        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                members.add(new Member(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }
        }

        return members;
    }

    // GET ARCHIVED MEMBERS
    public List<Member> getArchivedMembers() throws SQLException {

        List<Member> members = new ArrayList<>();

        String sql = """
                SELECT id, name
                FROM members
                WHERE archived = 1
                ORDER BY name
                """;

        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                members.add(new Member(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }
        }

        return members;
    }

    // ARCHIVE MEMBER
    public void archiveMember(int memberId) throws SQLException {

        String sql = """
                UPDATE members
                SET archived = 1
                WHERE id = ? AND archived = 0
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, memberId);

            if (ps.executeUpdate() == 0) {
                throw new SQLException(
                        "Member not found or already archived.");
            }
        }
    }

    // RESTORE MEMBER
    public void restoreMember(int memberId) throws SQLException {

        String sql = """
                UPDATE members
                SET archived = 0
                WHERE id = ? AND archived = 1
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, memberId);

            if (ps.executeUpdate() == 0) {
                throw new SQLException(
                        "Archived member not found.");
            }
        }
    }

    // CHECK FINANCIAL REFERENCES
    private boolean hasFinancialReferences(
            Connection con, int memberId) throws SQLException {

        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM expense_splits
                     WHERE member_id = ?) +

                    (SELECT COUNT(*) FROM expenses
                     WHERE paid_by =
                     (SELECT name FROM members WHERE id = ?)) +

                    (SELECT COUNT(*) FROM settlements
                     WHERE from_member = ? OR to_member = ?) +

                    (SELECT COUNT(*) FROM global_settlements
                     WHERE from_member = ? OR to_member = ?)
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            for (int i = 1; i <= 6; i++) {
                ps.setInt(i, memberId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // PERMANENT DELETE
    // Allowed when there is no financial history.
    // Group memberships are removed automatically.
    public void deleteMember(int memberId) throws SQLException {

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                // Confirm member exists.
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT id FROM members WHERE id = ?")) {

                    check.setInt(1, memberId);

                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Member not found.");
                        }
                    }
                }

                // Never delete financial history.
                if (hasFinancialReferences(con, memberId)) {
                    throw new SQLException(
                            "This member has expense or settlement history. "
                            + "Archive the member instead.");
                }

                // Remove group memberships first.
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM group_members WHERE member_id = ?")) {

                    ps.setInt(1, memberId);
                    ps.executeUpdate();
                }

                // Permanently delete the member.
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM members WHERE id = ?")) {

                    ps.setInt(1, memberId);

                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Member could not be deleted.");
                    }
                }

                con.commit();

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                throw new SQLException(
                        "Member deletion failed.", e);

            } finally {
                con.setAutoCommit(true);
            }
        }
    }
}