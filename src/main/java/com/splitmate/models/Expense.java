package com.splitmate.models;

public class Expense {

    private int id;
    private String description;
    private long amount;
    private String paidBy;
    private int groupId;

    public Expense(int id, String description, long amount,
                   String paidBy, int groupId) {

        this.id = id;
        this.description = description;
        this.amount = amount;
        this.paidBy = paidBy;
        this.groupId = groupId;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public long getAmount() {
        return amount;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public int getGroupId() {
        return groupId;
    }
}