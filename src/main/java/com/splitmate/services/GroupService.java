package com.splitmate.services;

import com.splitmate.database.DatabaseConnection;
import com.splitmate.models.Group;
import com.splitmate.models.Member;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupService {

    // CREATE GROUP
    public void createGroup(String name) throws SQLException {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name required.");
        }

        String sql = """
            INSERT INTO expense_groups(group_name)
            VALUES(?)
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.executeUpdate();
        }
    }

    // GET ALL GROUPS
    public List<Group> getAllGroups() throws SQLException {

        List<Group> groups = new ArrayList<>();

        String sql = """
            SELECT id, group_name
            FROM expense_groups
            ORDER BY id DESC
        """;

        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                groups.add(new Group(
                    rs.getInt("id"),
                    rs.getString("group_name")
                ));
            }
        }

        return groups;
    }

    // ADD ACTIVE MEMBER TO GROUP
    public void addMemberToGroup(int groupId, int memberId)
            throws SQLException {

        String checkSql = """
            SELECT archived
            FROM members
            WHERE id = ?
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement check = con.prepareStatement(checkSql)) {

            check.setInt(1, memberId);

            try (ResultSet rs = check.executeQuery()) {

                if (!rs.next()) {
                    throw new SQLException("Member not found.");
                }

                if (rs.getInt("archived") == 1) {
                    throw new SQLException(
                        "Archived members cannot be added to new groups."
                    );
                }
            }

            String sql = """
                INSERT INTO group_members(group_id, member_id)
                VALUES(?, ?)
            """;

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, groupId);
                ps.setInt(2, memberId);
                ps.executeUpdate();
            }
        }
    }

    // GET ALL GROUP MEMBERS
    // Includes archived members to preserve historical records.
    public List<Member> getGroupMembers(int groupId)
            throws SQLException {

        List<Member> members = new ArrayList<>();

        String sql = """
            SELECT m.id, m.name
            FROM members m
            JOIN group_members gm ON m.id = gm.member_id
            WHERE gm.group_id = ?
            ORDER BY m.name
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, groupId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    members.add(new Member(
                        rs.getInt("id"),
                        rs.getString("name")
                    ));
                }
            }
        }

        return members;
    }

    // GET ONLY ACTIVE GROUP MEMBERS
    // Use this when creating new expenses.
    public List<Member> getActiveGroupMembers(int groupId)
            throws SQLException {

        List<Member> members = new ArrayList<>();

        String sql = """
            SELECT m.id, m.name
            FROM members m
            JOIN group_members gm ON m.id = gm.member_id
            WHERE gm.group_id = ?
              AND m.archived = 0
            ORDER BY m.name
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, groupId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    members.add(new Member(
                        rs.getInt("id"),
                        rs.getString("name")
                    ));
                }
            }
        }

        return members;
    }

    // REMOVE MEMBER FROM GROUP
    public void removeMember(int groupId, int memberId)
            throws SQLException {

        String sql = """
            DELETE FROM group_members
            WHERE group_id = ? AND member_id = ?
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ps.setInt(2, memberId);
            ps.executeUpdate();
        }
    }

    // DELETE GROUP
    public void deleteGroup(int groupId) throws SQLException {

        try (Connection con = DatabaseConnection.getConnection()) {

            // Prevent deletion if group contains expenses
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT COUNT(*) FROM expenses WHERE group_id = ?")) {

                check.setInt(1, groupId);

                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException(
                            "Cannot delete this group because it contains expenses."
                        );
                    }
                }
            }

            con.setAutoCommit(false);

            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM group_members WHERE group_id = ?")) {

                    ps.setInt(1, groupId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM expense_groups WHERE id = ?")) {

                    ps.setInt(1, groupId);
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