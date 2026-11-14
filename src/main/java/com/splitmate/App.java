package com.splitmate;

import com.splitmate.database.DatabaseInitializer;
import com.splitmate.models.Expense;
import com.splitmate.models.Group;
import com.splitmate.models.Member;
import com.splitmate.services.BalanceService;
import com.splitmate.services.ExpenseService;
import com.splitmate.services.GlobalSettlementService;
import com.splitmate.services.GroupService;
import com.splitmate.services.MemberService;
import com.splitmate.services.SettlementService;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.*;

public class App extends Application {

    private Stage stage;
    private BorderPane root;

    private final MemberService memberService = new MemberService();
    private final GroupService groupService = new GroupService();
    private final ExpenseService expenseService = new ExpenseService();
    private final BalanceService balanceService = new BalanceService();
    private final SettlementService settlementService = new SettlementService();
    private final GlobalSettlementService globalSettlementService =
            new GlobalSettlementService();

    private final ObservableList<Member> memberList =
            FXCollections.observableArrayList();
    private final ObservableList<Group> groupList =
            FXCollections.observableArrayList();
    private final ObservableList<Member> groupMemberList =
            FXCollections.observableArrayList();
    private final ObservableList<Expense> expenseList =
            FXCollections.observableArrayList();

    private TableView<Member> memberTable;
    private TableView<Group> groupTable;
    private TableView<Member> groupMemberTable;
    private TableView<Expense> expenseTable;

    private VBox sidebar;
    private String activePage = "Dashboard";

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        try {
            DatabaseInitializer.initialize();
        } catch (Exception e) {
            showError("Database Initialization Failed", e.getMessage());
            return;
        }

        root = new BorderPane();
        root.setLeft(createSidebar());
        showDashboard();

        Scene scene = new Scene(root, 1180, 760);
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/css/app.css")
                    ).toExternalForm()
            );
        } catch (Exception ignored) {
            // The application remains usable if the optional stylesheet is absent.
        }

        stage.setTitle("SplitMate - Expense Splitter");
        stage.setMinWidth(820);
stage.setMinHeight(580);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createSidebar() {
        sidebar = new VBox(12);
        sidebar.setPadding(new Insets(28, 16, 24, 16));
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPrefWidth(225);

        Label logo = new Label("SPLITMATE");
        logo.getStyleClass().add("brand-logo");

        Label tagline = new Label("Split fairly. Settle easily.");
        tagline.getStyleClass().add("brand-tagline");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.NEVER);

        sidebar.getChildren().addAll(
                logo, tagline, new Separator(),
                navButton("Dashboard", "▦"),
                navButton("Members", "♙"),
                navButton("Groups", "▣"),
                navButton("Expenses", "₹"),
                navButton("Settlements", "⇄"),
                navButton("Global Settlements", "◎")
        );

        return sidebar;
    }

    private Button navButton(String name, String icon) {
        Button button = new Button(icon + "   " + name);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        button.getStyleClass().add("nav-button");
        if (name.equals(activePage)) button.getStyleClass().add("active");

        button.setOnAction(e -> {
            activePage = name;
            updateNavigation();
            switch (name) {
                case "Dashboard" -> showDashboard();
                case "Members" -> showMembers();
                case "Groups" -> showGroups();
                case "Expenses" -> showExpenses();
                case "Settlements" -> showSettlements();
                case "Global Settlements" -> showGlobalSettlements();
                default -> showDashboard();
            }
        });

        return button;
    }

    private void updateNavigation() {

    for (Node node : sidebar.getChildren()) {

        if (node instanceof Button button) {

            button.getStyleClass().remove("active");

            String buttonText = button.getText();

            int separatorIndex = buttonText.indexOf("   ");

            if (separatorIndex >= 0) {

                String pageName =
                        buttonText.substring(separatorIndex + 3);

                if (pageName.equals(activePage)) {
                    button.getStyleClass().add("active");
                }
            }
        }
    }
}

    // DASHBOARD

    private void showDashboard() {

    VBox main = pageContainer(
            "Dashboard",
            "Your financial overview across all groups."
    );

    // MEMBER SELECTOR
    ComboBox<Member> memberBox = new ComboBox<>();
    memberBox.setPromptText("Select member");
    memberBox.setMaxWidth(260);

    try {
        memberBox.getItems().setAll(memberService.getAllMembers());
    } catch (SQLException e) {
        showError("Member Loading Error", e.getMessage());
    }

    // PERSONAL FINANCIAL CARDS
    FlowPane cards = new FlowPane();
    cards.setHgap(14);
    cards.setVgap(14);
    cards.setPrefWrapLength(1100);
    cards.setMaxWidth(Double.MAX_VALUE);

    Label expenseShare = new Label("₹0.00");
    Label paymentsMade = new Label("₹0.00");
    Label receive = new Label("₹0.00");
    Label pay = new Label("₹0.00");

    cards.getChildren().addAll(
            createCard("TOTAL EXPENSE SHARE", expenseShare),
            createCard("AMOUNT SETTLED", paymentsMade),
            createCard("YOU WILL RECEIVE", receive),
            createCard("YOU NEED TO PAY", pay)
    );

    // MEMBER DASHBOARD CALCULATIONS
    memberBox.setOnAction(e -> {

        Member selected = memberBox.getValue();
        if (selected == null) return;

        try {
            long totalShare = 0;
            long sentPayments = 0;

            try (var con =
                    com.splitmate.database.DatabaseConnection.getConnection()) {

                try (var ps = con.prepareStatement(
                        "SELECT COALESCE(SUM(share_paise),0) " +
                        "FROM expense_splits WHERE member_id=?")) {

                    ps.setInt(1, selected.getId());

                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) totalShare = rs.getLong(1);
                    }
                }

                try (var ps = con.prepareStatement(
                        "SELECT COALESCE(SUM(amount_paise),0) " +
                        "FROM settlements WHERE from_member=?")) {

                    ps.setInt(1, selected.getId());

                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) sentPayments += rs.getLong(1);
                    }
                }

                try (var ps = con.prepareStatement(
                        "SELECT COALESCE(SUM(amount_paise),0) " +
                        "FROM global_settlements WHERE from_member=?")) {

                    ps.setInt(1, selected.getId());

                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) sentPayments += rs.getLong(1);
                    }
                }
            }

            Map<String, Long> balances =
                    globalSettlementService.getGlobalBalances();

            long net = balances.getOrDefault(
                    selected.getName(), 0L);

            expenseShare.setText(
                    ExpenseService.rupees(totalShare));

            paymentsMade.setText(
                    ExpenseService.rupees(sentPayments));

            receive.setText(
                    ExpenseService.rupees(Math.max(net, 0)));

            pay.setText(
                    ExpenseService.rupees(Math.max(-net, 0)));

        } catch (Exception ex) {
            showError("Dashboard Error", ex.getMessage());
        }
    });

    // FINANCIAL OVERVIEW
    Label totalExpenses = new Label("₹0.00");
    Label totalReceivable = new Label("₹0.00");
    Label totalPayable = new Label("₹0.00");
    Label totalMembers = new Label("0");

    VBox overview = new VBox(16);
    overview.getStyleClass().add("metric-card");
    overview.setPadding(new Insets(22));
    overview.setMinWidth(250);

    overview.getChildren().addAll(
            heading("FINANCIAL OVERVIEW", 18),
            createOverviewRow("Total Group Expenses", totalExpenses),
            createOverviewRow("Outstanding Receivables", totalReceivable),
            createOverviewRow("Outstanding Payments", totalPayable),
            createOverviewRow("Active Members", totalMembers)
    );

    // RECENT ACTIVITY
    VBox activity = new VBox(12);
    activity.getStyleClass().add("metric-card");
    activity.setPadding(new Insets(22));
    activity.setMinWidth(300);

    activity.getChildren().add(
            heading("RECENT ACTIVITY", 18)
    );

    try {
        List<Expense> expenses =
                new ArrayList<>(expenseService.getExpenses());

        expenses.sort(
                Comparator.comparingInt(Expense::getId).reversed()
        );

        int count = 0;

        for (Expense expense : expenses) {

            if (count >= 5) break;

            Label item = new Label(
                    "₹ " + ExpenseService.rupees(expense.getAmount())
                    + "  •  " + expense.getDescription()
                    + "\nPaid by " + expense.getPaidBy()
            );

            item.setWrapText(true);
            item.getStyleClass().add("activity-item");

            activity.getChildren().add(item);

            count++;
        }

        List<GlobalSettlementService.SettlementRecord> history =
                globalSettlementService.getHistory();

        if (!history.isEmpty()) {

            GlobalSettlementService.SettlementRecord latest =
                    history.get(0);

            Label settlementItem = new Label(
                    "Global settlement\n"
                    + latest.getFrom() + " paid "
                    + latest.getTo() + " • "
                    + ExpenseService.rupees(latest.getAmount())
            );

            settlementItem.setWrapText(true);
            settlementItem.getStyleClass().add("activity-item");

            activity.getChildren().add(settlementItem);
        }

        if (count == 0 && history.isEmpty()) {
            Label empty = new Label(
                    "No activity yet. Add your first expense!"
            );

            empty.setWrapText(true);
            activity.getChildren().add(empty);
        }

    } catch (Exception ex) {
        showError("Activity Loading Error", ex.getMessage());
    }

    // LOAD OVERALL FINANCIAL FIGURES
    try {

        List<Expense> expenses =
                expenseService.getExpenses();

        long expenseTotal = expenses.stream()
                .mapToLong(Expense::getAmount)
                .sum();

        Map<String, Long> balances =
                globalSettlementService.getGlobalBalances();

        long receivable = balances.values().stream()
                .filter(v -> v > 0)
                .mapToLong(Long::longValue)
                .sum();

        long payable = balances.values().stream()
                .filter(v -> v < 0)
                .mapToLong(v -> -v)
                .sum();

        totalExpenses.setText(
                ExpenseService.rupees(expenseTotal));

        totalReceivable.setText(
                ExpenseService.rupees(receivable));

        totalPayable.setText(
                ExpenseService.rupees(payable));

        totalMembers.setText(
                String.valueOf(memberService.getAllMembers().size()));

    } catch (Exception ex) {
        showError("Overview Loading Error", ex.getMessage());
    }

    // OVERVIEW AND ACTIVITY LAYOUT
    HBox informationSection = new HBox(18);
    informationSection.getChildren().addAll(
            overview,
            activity
    );

    HBox.setHgrow(overview, Priority.ALWAYS);
    HBox.setHgrow(activity, Priority.ALWAYS);

    overview.setMaxWidth(Double.MAX_VALUE);
    activity.setMaxWidth(Double.MAX_VALUE);

    // QUICK ACTIONS
    FlowPane shortcuts = new FlowPane();
    shortcuts.setHgap(12);
    shortcuts.setVgap(10);

    Button expensesButton = greenButton("＋ Add / View Expenses");
    Button membersButton = new Button("View Members");
    Button settlementButton = new Button("Group Settlements");
    Button globalButton = new Button("Global Settlements");

    expensesButton.setOnAction(e -> navigate("Expenses"));
    membersButton.setOnAction(e -> navigate("Members"));
    settlementButton.setOnAction(e -> navigate("Settlements"));
    globalButton.setOnAction(e -> navigate("Global Settlements"));

    shortcuts.getChildren().addAll(
            expensesButton,
            membersButton,
            settlementButton,
            globalButton
    );

    main.getChildren().addAll(
            memberBox,
            cards,
            informationSection,
            heading("QUICK ACTIONS", 18),
            shortcuts
    );

    setMainContent(main);
}

private HBox createOverviewRow(String title, Label value) {

    Label name = new Label(title);
    name.getStyleClass().add("overview-label");

    value.getStyleClass().add("overview-value");

    HBox row = new HBox(10, name, value);
    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    HBox.setHgrow(name, Priority.ALWAYS);

    return row;
}

    private VBox createCard(String title, Label value) {

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("metric-title");

    VBox card = new VBox(12, titleLabel, value);

    card.getStyleClass().add("metric-card");

    card.setMinWidth(190);
    card.setPrefWidth(220);
    card.setMaxWidth(260);

    value.getStyleClass().add("metric-value");

    return card;
}

    private void navigate(String page) {
        activePage = page;
        updateNavigation();
        switch (page) {
            case "Members" -> showMembers();
            case "Groups" -> showGroups();
            case "Expenses" -> showExpenses();
            case "Settlements" -> showSettlements();
            case "Global Settlements" -> showGlobalSettlements();
            default -> showDashboard();
        }
    }

    // MEMBERS

    private void showMembers() {

    VBox main = pageContainer(
            "Members",
            "Manage active and archived SplitMate members."
    );

    // ACTION BUTTONS
    FlowPane actions = new FlowPane();
    actions.setHgap(12);
    actions.setVgap(10);

    Button addButton = greenButton("＋ Add Member");
    Button deleteButton = dangerButton("Delete Selected");
    Button archiveButton = dangerButton("Archive Selected");
    Button archivedButton = new Button("View Archived");

    // SEARCH FIELD
    TextField searchField = new TextField();
    searchField.setPromptText("Search by member name or ID...");
    searchField.setMaxWidth(350);

    // MEMBER TABLE
    memberTable = new TableView<>();

    TableColumn<Member, Integer> idColumn =
            new TableColumn<>("ID");

    idColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleIntegerProperty(
                    data.getValue().getId()).asObject());

    TableColumn<Member, String> nameColumn =
            new TableColumn<>("Member Name");

    nameColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getName()));

    memberTable.getColumns().addAll(
            List.of(idColumn, nameColumn));

    memberTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    // LOAD MEMBERS
    try {
        memberList.setAll(memberService.getAllMembers());
    } catch (SQLException e) {
        showError("Member Loading Error", e.getMessage());
    }

    // SEARCH FILTER
    javafx.collections.transformation.FilteredList<Member>
            filteredMembers =
            new javafx.collections.transformation.FilteredList<>(
                    memberList, member -> true);

    searchField.textProperty().addListener(
            (observable, oldValue, newValue) -> {

        String query = newValue == null
                ? ""
                : newValue.trim().toLowerCase();

        filteredMembers.setPredicate(member -> {

            if (query.isEmpty()) return true;

            return member.getName()
                    .toLowerCase()
                    .contains(query)
                    || String.valueOf(member.getId())
                    .contains(query);
        });
    });

    memberTable.setItems(filteredMembers);

    // EMPTY STATE
    Label emptyMessage = new Label("No members found.");
    emptyMessage.getStyleClass().add("page-subtitle");
    memberTable.setPlaceholder(emptyMessage);

    // DYNAMIC TABLE HEIGHT
    // DYNAMIC TABLE HEIGHT
memberTable.prefHeightProperty().bind(
        javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(
                        430.0,
                        Math.max(
                                150.0,
                                52.0 + memberList.size() * 46.0
                        )
                ),
                memberList
        )
);

    // LIVE MEMBER COUNT
    Label memberCount = new Label();
    memberCount.getStyleClass().add("overview-value");

    memberCount.textProperty().bind(
            javafx.beans.binding.Bindings.concat(
                    javafx.beans.binding.Bindings.size(memberList),
                    " ACTIVE MEMBERS"
            )
    );

    HBox tableHeading = new HBox(
            memberCount
    );

    tableHeading.setAlignment(
            javafx.geometry.Pos.CENTER_LEFT
    );

    // BUTTON ACTIONS
    addButton.setOnAction(e -> addMemberDialog());
    deleteButton.setOnAction(e -> deleteSelectedMember());
    archiveButton.setOnAction(e -> archiveSelectedMember());
    archivedButton.setOnAction(e -> showArchivedMembers());

    actions.getChildren().addAll(
            addButton,
            deleteButton,
            archiveButton,
            archivedButton
    );

    // FINAL LAYOUT
    main.getChildren().addAll(
            actions,
            searchField,
            tableHeading,
            memberTable
    );

    setMainContent(main);
}

    private void addMemberDialog() {
        TextInputDialog dialog = new TextInputDialog();
        styleDialog(dialog);
        dialog.setTitle("Add Member");
        dialog.setHeaderText("Add a new SplitMate member");
        dialog.setContentText("Member name:");

        dialog.showAndWait().ifPresent(name -> {
            try {
                memberService.addMember(name.trim());
                showMembers();
            } catch (Exception e) {
                showError("Unable to Add Member", e.getMessage());
            }
        });
    }
    

    private void deleteSelectedMember() {

    Member selected =
            memberTable.getSelectionModel().getSelectedItem();

    if (selected == null) {
        showAlert("Selection Required",
                "Please select a member to delete.");
        return;
    }

    showConfirmation(
            "Permanently Delete Member",
            "Permanently delete " + selected.getName()
                    + "?\n\nThis is allowed only if the member "
                    + "has no financial history.",

            () -> {
                try {
                    memberService.deleteMember(selected.getId());

                    showMembers();

                    showAlert("Member Deleted",
                            selected.getName()
                                    + " was permanently deleted.");

                } catch (Exception e) {
                    showError("Unable to Delete Member",
                            e.getMessage());
                }
            }
    );
}

    private void archiveSelectedMember() {

    Member selected =
            memberTable.getSelectionModel().getSelectedItem();

    if (selected == null) {
        showAlert("Selection Required",
                "Please select a member to archive.");
        return;
    }

    showConfirmation(
            "Archive Member",
            "Archive " + selected.getName() +
                    "?\nTheir financial history will be preserved.",

            () -> {
                try {
                    memberService.archiveMember(selected.getId());

                    showMembers();

                    showAlert("Member Archived",
                            selected.getName() +
                                    " has been archived successfully.");

                } catch (Exception e) {
                    showError("Archive Failed", e.getMessage());
                }
            }
    );
}
private void showArchivedMembers() {

    Dialog<ButtonType> dialog = new Dialog<>();

    styleDialog(dialog);

    dialog.setTitle("Archived Members");
    dialog.setHeaderText(
            "View and restore previously archived members.");

    ButtonType closeButton = new ButtonType(
            "Close", ButtonBar.ButtonData.CANCEL_CLOSE);

    dialog.getDialogPane().getButtonTypes().add(closeButton);

    TableView<Member> archivedTable = new TableView<>();

    TableColumn<Member, Integer> idColumn =
            new TableColumn<>("ID");

    idColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleIntegerProperty(
                    data.getValue().getId()).asObject());

    TableColumn<Member, String> nameColumn =
            new TableColumn<>("Member Name");

    nameColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getName()));

    archivedTable.getColumns().addAll(
            List.of(idColumn, nameColumn));

    archivedTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    archivedTable.setPrefHeight(300);

    try {
        archivedTable.getItems().setAll(
                memberService.getArchivedMembers());
    } catch (SQLException e) {
        showError("Loading Error", e.getMessage());
        return;
    }

    Button restoreButton = greenButton("Restore Selected");

    restoreButton.setOnAction(e -> {

        Member selected =
                archivedTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert("Selection Required",
                    "Select a member to restore.");
            return;
        }

        try {
            memberService.restoreMember(selected.getId());

            archivedTable.getItems().remove(selected);

            showMembers();

            showAlert("Member Restored",
                    selected.getName() +
                            " is now an active member.");

        } catch (Exception ex) {
            showError("Restore Failed", ex.getMessage());
        }
    });

    VBox content = new VBox(15,
            archivedTable,
            restoreButton
    );

    content.setPadding(new Insets(15));

    dialog.getDialogPane().setContent(content);

    dialog.showAndWait();
}

    // GROUPS

    private void showGroups() {

    VBox main = pageContainer(
            "Group Management",
            "Create groups and manage their participants."
    );

    // ACTION BUTTONS
    FlowPane actions = new FlowPane();
    actions.setHgap(12);
    actions.setVgap(10);

    Button createButton = greenButton("＋ Create Group");
    Button addMemberButton = greenButton("＋ Add Member");
    Button removeMemberButton = new Button("Remove Member");
    Button deleteButton = dangerButton("Delete Group");

    actions.getChildren().addAll(
            createButton,
            addMemberButton,
            removeMemberButton,
            deleteButton
    );

    // GROUP SEARCH
    TextField searchField = new TextField();
    searchField.setPromptText("Search groups by name or ID...");
    searchField.setMaxWidth(350);

    // GROUP TABLE
    groupTable = new TableView<>();

    TableColumn<Group, Integer> idColumn =
            new TableColumn<>("Group ID");

    idColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleIntegerProperty(
                    data.getValue().getId()).asObject());

    TableColumn<Group, String> nameColumn =
            new TableColumn<>("Group Name");

    nameColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getGroupName()));

    groupTable.getColumns().addAll(
            List.of(idColumn, nameColumn));

    groupTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    // LOAD GROUPS
    loadGroups();

    javafx.collections.transformation.FilteredList<Group>
            filteredGroups =
            new javafx.collections.transformation.FilteredList<>(
                    groupList, group -> true);

    searchField.textProperty().addListener(
            (observable, oldValue, newValue) -> {

        String query = newValue == null
                ? ""
                : newValue.trim().toLowerCase();

        filteredGroups.setPredicate(group -> {

            if (query.isEmpty()) return true;

            return group.getGroupName()
                    .toLowerCase()
                    .contains(query)
                    || String.valueOf(group.getId())
                    .contains(query);
        });
    });

    groupTable.setItems(filteredGroups);

    Label groupEmptyMessage =
            new Label("No groups found. Create your first group!");

    groupEmptyMessage.getStyleClass().add("page-subtitle");
    groupTable.setPlaceholder(groupEmptyMessage);

    // GROUP COUNT
    Label groupCount = new Label();
    groupCount.getStyleClass().add("overview-value");

    groupCount.textProperty().bind(
            javafx.beans.binding.Bindings.concat(
                    javafx.beans.binding.Bindings.size(groupList),
                    " GROUPS"
            )
    );

    // GROUP MEMBERS TABLE
    groupMemberTable = new TableView<>();

    TableColumn<Member, Integer> memberIdColumn =
            new TableColumn<>("Member ID");

    memberIdColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleIntegerProperty(
                    data.getValue().getId()).asObject());

    TableColumn<Member, String> memberNameColumn =
            new TableColumn<>("Member Name");

    memberNameColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getName()));

    groupMemberTable.getColumns().addAll(
            List.of(memberIdColumn, memberNameColumn));

    groupMemberTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    Label memberEmptyMessage =
            new Label("Select a group to view its members.");

    memberEmptyMessage.getStyleClass().add("page-subtitle");

    groupMemberTable.setPlaceholder(memberEmptyMessage);

    // MEMBER COUNT
    Label memberCount = new Label("0 MEMBERS");
    memberCount.getStyleClass().add("overview-value");

    groupMemberList.addListener(
            (javafx.collections.ListChangeListener<Member>) change -> {

        memberCount.setText(
                groupMemberList.size() + " MEMBERS");
    });

    // DYNAMIC GROUP TABLE HEIGHT
    groupTable.prefHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(
                            430.0,
                            Math.max(
                                    100.0,
                                    52.0 + groupList.size() * 46.0
                            )
                    ),
                    groupList
            )
    );

    // DYNAMIC MEMBER TABLE HEIGHT
    groupMemberTable.prefHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(
                            350.0,
                            Math.max(
                                    150.0,
                                    52.0 + groupMemberList.size() * 46.0
                            )
                    ),
                    groupMemberList
            )
    );

    // GROUP SELECTION
    groupTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldValue, newValue) -> {

        if (newValue == null) {
            memberEmptyMessage.setText(
                    "Select a group to view its members.");
        } else {
            memberEmptyMessage.setText(
                    "This group has no members yet.");
        }

        loadSelectedGroupMembers();
    });

    // BUTTON ACTIONS
    createButton.setOnAction(e -> createGroupDialog());
    addMemberButton.setOnAction(e -> addMemberToSelectedGroup());
    removeMemberButton.setOnAction(
            e -> removeSelectedMemberFromGroup());
    deleteButton.setOnAction(e -> deleteSelectedGroup());

    // SECTION HEADINGS
    HBox groupHeading = new HBox(groupCount);
    groupHeading.setAlignment(
            javafx.geometry.Pos.CENTER_LEFT);

    HBox memberHeading = new HBox(memberCount);
    memberHeading.setAlignment(
            javafx.geometry.Pos.CENTER_LEFT);

    // FINAL LAYOUT
    main.getChildren().addAll(
            actions,
            searchField,
            heading("YOUR GROUPS", 18),
            groupHeading,
            groupTable,
            heading("GROUP MEMBERS", 18),
            memberHeading,
            groupMemberTable
    );

    setMainContent(main);
}

    private void loadGroups() {
        try {
            groupList.setAll(groupService.getAllGroups());
            groupTable.setItems(groupList);
        } catch (SQLException e) {
            showError("Group Loading Error", e.getMessage());
        }
    }

    private void createGroupDialog() {
        TextInputDialog dialog = new TextInputDialog();
                styleDialog(dialog);
        dialog.setTitle("Create Group");
        dialog.setHeaderText("Create a new expense group");
        dialog.setContentText("Group name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                showError("Invalid Group", "Please enter a group name.");
                return;
            }
            try {
                groupService.createGroup(name.trim());
                showGroups();
            } catch (Exception e) {
                showError("Group Creation Failed", e.getMessage());
            }
        });
    }

    private void addMemberToSelectedGroup() {
        Group selectedGroup = groupTable.getSelectionModel().getSelectedItem();
        if (selectedGroup == null) {
            showAlert("Select Group", "Please select a group first.");
            return;
        }

        try {
            List<Member> available = new ArrayList<>(memberService.getAllMembers());
            Set<Integer> existing = new HashSet<>();
            for (Member m : groupService.getGroupMembers(selectedGroup.getId())) {
                existing.add(m.getId());
            }
            available.removeIf(m -> existing.contains(m.getId()));

            if (available.isEmpty()) {
                showAlert("No Available Members",
                        "All members are already in this group.");
                return;
            }

            ChoiceDialog<Member> dialog =
                    new ChoiceDialog<>(available.get(0), available);
            styleDialog(dialog);
            dialog.setTitle("Add Group Member");
            dialog.setHeaderText("Add a member to " +
                    selectedGroup.getGroupName());
            dialog.setContentText("Select member:");

            dialog.showAndWait().ifPresent(member -> {
                try {
                    groupService.addMemberToGroup(
                            selectedGroup.getId(), member.getId());
                    loadSelectedGroupMembers();
                } catch (Exception e) {
                    showError("Unable to Add Member", e.getMessage());
                }
            });
        } catch (Exception e) {
            showError("Member Loading Error", e.getMessage());
        }
    }

    private void loadSelectedGroupMembers() {

    if (groupTable == null || groupMemberTable == null) {
        return;
    }

    Group selected =
            groupTable.getSelectionModel().getSelectedItem();

    groupMemberList.clear();

    if (selected == null) {
        return;
    }

    try {

        groupMemberList.setAll(
                groupService.getGroupMembers(selected.getId())
        );

        groupMemberTable.setItems(groupMemberList);

    } catch (SQLException e) {

        showError(
                "Unable to Load Group Members",
                e.getMessage()
        );
    }
}

    private void removeSelectedMemberFromGroup() {
        Group group = groupTable.getSelectionModel().getSelectedItem();
        Member member = groupMemberTable.getSelectionModel().getSelectedItem();

        if (group == null || member == null) {
            showAlert("Selection Required",
                    "Select a group and one of its members.");
            return;
        }

        showConfirmation("Remove Group Member",
                "Remove " + member.getName() + " from " +
                        group.getGroupName() + "?",
                () -> {
                    try {
                        groupService.removeMember(group.getId(), member.getId());
                        loadSelectedGroupMembers();
                    } catch (Exception e) {
                        showError("Unable to Remove Member", e.getMessage());
                    }
                });
    }

    private void deleteSelectedGroup() {
        Group group = groupTable.getSelectionModel().getSelectedItem();
        if (group == null) {
            showAlert("Selection Required", "Please select a group first.");
            return;
        }

        showConfirmation("Delete Group",
                "Delete " + group.getGroupName() + "?",
                () -> {
                    try {
                        groupService.deleteGroup(group.getId());
                        showGroups();
                    } catch (Exception e) {
                        showError("Unable to Delete Group", e.getMessage());
                    }
                });
    }

    // EXPENSES

    private void showExpenses() {

    VBox main = pageContainer(
            "Expense Management",
            "Track shared expenses across your groups."
    );

    // ACTION BUTTONS
    FlowPane actions = new FlowPane();
    actions.setHgap(12);
    actions.setVgap(10);

    Button addButton = greenButton("＋ Add Expense");
    Button editButton = greenButton("✎ Edit Expense");
    Button deleteButton = dangerButton("Delete Expense");
    Button refreshButton = new Button("Refresh");

    actions.getChildren().addAll(
            addButton,
            editButton,
            deleteButton,
            refreshButton
    );

    // SEARCH FIELD
    TextField searchField = new TextField();
    searchField.setPromptText(
            "Search by description, payer or expense ID...");
    searchField.setMaxWidth(380);

    // EXPENSE TABLE
    expenseTable = new TableView<>();

    TableColumn<Expense, Integer> idColumn =
            new TableColumn<>("ID");

    idColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleIntegerProperty(
                    data.getValue().getId()).asObject());

    TableColumn<Expense, String> descriptionColumn =
            new TableColumn<>("Description");

    descriptionColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getDescription()));

    TableColumn<Expense, String> amountColumn =
            new TableColumn<>("Amount");

    amountColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    ExpenseService.rupees(
                            data.getValue().getAmount())));

    TableColumn<Expense, String> payerColumn =
            new TableColumn<>("Paid By");

    payerColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getPaidBy()));

    expenseTable.getColumns().addAll(
            List.of(
                    idColumn,
                    descriptionColumn,
                    amountColumn,
                    payerColumn
            )
    );

    expenseTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    // LOAD EXPENSES
    loadExpenses();

    // SEARCH FILTER
    javafx.collections.transformation.FilteredList<Expense>
            filteredExpenses =
            new javafx.collections.transformation.FilteredList<>(
                    expenseList, expense -> true);

    searchField.textProperty().addListener(
            (observable, oldValue, newValue) -> {

        String query = newValue == null
                ? ""
                : newValue.trim().toLowerCase();

        filteredExpenses.setPredicate(expense -> {

            if (query.isEmpty()) return true;

            return String.valueOf(expense.getId())
                    .contains(query)
                    || expense.getDescription()
                    .toLowerCase()
                    .contains(query)
                    || expense.getPaidBy()
                    .toLowerCase()
                    .contains(query);
        });
    });

    expenseTable.setItems(filteredExpenses);

    // EMPTY STATE
    Label emptyMessage =
            new Label("No expenses found. Add your first expense!");

    emptyMessage.getStyleClass().add("page-subtitle");
    expenseTable.setPlaceholder(emptyMessage);

    // LIVE EXPENSE COUNT
    Label expenseCount = new Label();

    expenseCount.getStyleClass().add("overview-value");

    expenseCount.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                    () -> filteredExpenses.size() + " RECORDS",
                    filteredExpenses
            )
    );

    // TOTAL EXPENSE AMOUNT
    Label totalAmount = new Label();

    totalAmount.getStyleClass().add("overview-value");

    totalAmount.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                    () -> "TOTAL RECORDED: "
                            + ExpenseService.rupees(
                                    expenseList.stream()
                                            .mapToLong(Expense::getAmount)
                                            .sum()),
                    expenseList
            )
    );

    // DYNAMIC TABLE HEIGHT
    expenseTable.prefHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(
                            430.0,
                            Math.max(
                                    150.0,
                                    52.0 + filteredExpenses.size() * 46.0
                            )
                    ),
                    filteredExpenses
            )
    );

    // SECTION INFORMATION
    HBox expenseSummary = new HBox(20);
    expenseSummary.setAlignment(
            javafx.geometry.Pos.CENTER_LEFT);

    expenseSummary.getChildren().addAll(
            expenseCount,
            totalAmount
    );

    // BUTTON ACTIONS
    addButton.setOnAction(e -> addExpenseDialog());

    editButton.setOnAction(e -> editSelectedExpense());

    deleteButton.setOnAction(e -> deleteSelectedExpense());

    refreshButton.setOnAction(e -> loadExpenses());

    // FINAL LAYOUT
    main.getChildren().addAll(
            actions,
            searchField,
            heading("ALL EXPENSES", 18),
            expenseSummary,
            expenseTable
    );

    setMainContent(main);
}

    private void loadExpenses() {
        try {
            expenseList.setAll(expenseService.getExpenses());
            expenseTable.setItems(expenseList);
        } catch (SQLException e) {
            showError("Expense Loading Error", e.getMessage());
        }
    }

    private void addExpenseDialog() {
        List<Group> groups;
        try {
            groups = groupService.getAllGroups();
        } catch (SQLException e) {
            showError("Loading Error", e.getMessage());
            return;
        }

        if (groups.isEmpty()) {
            showAlert("No Groups", "Create a group before adding an expense.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.setTitle("Add Expense");
        dialog.setHeaderText("Enter expense details");

        ButtonType saveType = new ButtonType(
                "Save Expense", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(
                saveType, ButtonType.CANCEL);

        TextField description = new TextField();
        description.setPromptText("Expense description");

        TextField amount = new TextField();
        amount.setPromptText("Amount in ₹ (example: 500.00)");

        ComboBox<Group> groupBox = new ComboBox<>();
        groupBox.getItems().setAll(groups);
        groupBox.setPromptText("Select group");

        ComboBox<Member> payerBox = new ComboBox<>();
        payerBox.setPromptText("Select who paid");

        VBox splitBox = new VBox(8);
        Map<Member, CheckBox> checks = new LinkedHashMap<>();

        groupBox.setOnAction(e -> {
            payerBox.getItems().clear();
            splitBox.getChildren().clear();
            checks.clear();

            Group selected = groupBox.getValue();
            if (selected == null) return;

            try {
                List<Member> groupMembers =
                        groupService.getActiveGroupMembers(selected.getId());
                payerBox.getItems().setAll(groupMembers);
                splitBox.getChildren().add(
                        new Label("Select members sharing this expense:"));

                for (Member member : groupMembers) {
                    CheckBox check = new CheckBox(member.getName());
                    check.setSelected(true);
                    checks.put(member, check);
                    splitBox.getChildren().add(check);
                }
            } catch (SQLException ex) {
                showError("Group Members Error", ex.getMessage());
            }
        });

        VBox content = new VBox(12,
                new Label("Description"), description,
                new Label("Amount (₹)"), amount,
                new Label("Group"), groupBox,
                new Label("Paid By"), payerBox,
                splitBox);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(new ScrollPane(content));

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveType) return;

            try {
                if (description.getText().isBlank())
                    throw new IllegalArgumentException(
                            "Enter an expense description.");

                Group selectedGroup = groupBox.getValue();
                Member payer = payerBox.getValue();

                if (selectedGroup == null || payer == null)
                    throw new IllegalArgumentException(
                            "Select a group and payer.");

                long totalPaise = ExpenseService.toPaise(amount.getText());
                List<Member> selectedMembers = new ArrayList<>();

                for (var entry : checks.entrySet()) {
                    if (entry.getValue().isSelected())
                        selectedMembers.add(entry.getKey());
                }

                if (selectedMembers.isEmpty())
                    throw new IllegalArgumentException(
                            "Select at least one member.");

                Map<Member, Long> shares = new LinkedHashMap<>();
                long each = totalPaise / selectedMembers.size();
                long remainder = totalPaise % selectedMembers.size();

                for (int i = 0; i < selectedMembers.size(); i++) {
                    shares.put(selectedMembers.get(i),
                            each + (i == 0 ? remainder : 0));
                }

                expenseService.addExpense(
                        description.getText().trim(),
                        totalPaise,
                        selectedGroup.getId(),
                        payer,
                        shares);

                loadExpenses();
                showAlert("Expense Added",
                        "Expense saved successfully.");
            } catch (Exception ex) {
                showError("Unable to Add Expense", ex.getMessage());
            }
        });
    }

    private void editSelectedExpense() {

    Expense selected = expenseTable.getSelectionModel()
            .getSelectedItem();

    if (selected == null) {
        showAlert("Select Expense",
                "Please select an expense to edit.");
        return;
    }

    try {

        // LOAD ORIGINAL SHARES
        Map<Integer, Long> existingShares =
                expenseService.getExpenseShares(selected.getId());

        List<Member> members =
                groupService.getActiveGroupMembers(
                        selected.getGroupId());

        Set<Integer> activeMemberIds = new HashSet<>();

        for (Member member : members) {
            activeMemberIds.add(member.getId());
        }

        if (!activeMemberIds.containsAll(existingShares.keySet())) {
            showAlert("Archived Member",
                    "This expense includes an archived member. " +
                    "Restore that member before editing.");
            return;
        }

        boolean payerIsActive = members.stream()
                .anyMatch(m ->
                        m.getName().equals(selected.getPaidBy()));

        if (!payerIsActive) {
            showAlert("Archived Payer",
                    "The original payer is archived. " +
                    "Restore that member before editing.");
            return;
        }

        // DIALOG
        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);

        dialog.setTitle("Edit Expense");
        dialog.setHeaderText("Update expense details");

        ButtonType saveType = new ButtonType(
                "Update Expense",
                ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                saveType, ButtonType.CANCEL);

        TextField description = new TextField(
                selected.getDescription());

        TextField amount = new TextField(
                ExpenseService.rupees(selected.getAmount()));

        ComboBox<Member> payerBox = new ComboBox<>();
        payerBox.getItems().setAll(members);

        for (Member member : members) {
            if (member.getName().equals(selected.getPaidBy())) {
                payerBox.setValue(member);
                break;
            }
        }

        // SPLIT MODE
        ComboBox<String> splitMode = new ComboBox<>();

        splitMode.getItems().addAll(
                "Keep Existing Shares",
                "Equal Split",
                "Custom Split"
        );

        splitMode.setValue("Keep Existing Shares");
        splitMode.setMaxWidth(Double.MAX_VALUE);

        // PARTICIPANTS AND CUSTOM SHARE FIELDS
        VBox splitBox = new VBox(10);

        Map<Member, CheckBox> checks = new LinkedHashMap<>();
        Map<Member, TextField> shareFields = new LinkedHashMap<>();

        for (Member member : members) {

            CheckBox check = new CheckBox(member.getName());

            check.setSelected(
                    existingShares.containsKey(member.getId()));

            TextField shareField = new TextField();

            long originalShare =
                    existingShares.getOrDefault(member.getId(), 0L);

            shareField.setText(
                    ExpenseService.rupees(originalShare));

            shareField.setPromptText("Share in ₹");
            shareField.setPrefWidth(140);

            checks.put(member, check);
            shareFields.put(member, shareField);

            HBox row = new HBox(12, check, shareField);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            splitBox.getChildren().add(row);
        }

        // SHOW CUSTOM FIELDS ONLY WHEN REQUIRED
        Runnable updateSplitFields = () -> {

            boolean custom =
                    "Custom Split".equals(splitMode.getValue());

            for (TextField field : shareFields.values()) {
                field.setVisible(custom);
                field.setManaged(custom);
            }
        };

        splitMode.setOnAction(e -> updateSplitFields.run());

        updateSplitFields.run();

        Label splitHint = new Label(
                "Keep Existing Shares preserves the original amounts. " +
                "Choose Equal or Custom if you change participants."
        );

        splitHint.setWrapText(true);
        splitHint.getStyleClass().add("page-subtitle");

        VBox content = new VBox(12,
                new Label("Description"),
                description,

                new Label("Amount (₹)"),
                amount,

                new Label("Paid By"),
                payerBox,

                new Label("Split Mode"),
                splitMode,

                splitHint,

                new Label("Participants / Custom Shares"),
                splitBox
        );

        content.setPadding(new Insets(20));

        dialog.getDialogPane().setContent(
                new ScrollPane(content));

        dialog.getDialogPane().setPrefWidth(460);

        // SAVE
        dialog.showAndWait().ifPresent(result -> {

            if (result != saveType) return;

            try {

                if (description.getText().isBlank()) {
                    throw new IllegalArgumentException(
                            "Enter an expense description.");
                }

                if (payerBox.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select who paid.");
                }

                long totalPaise =
                        ExpenseService.toPaise(amount.getText());

                List<Member> selectedMembers = new ArrayList<>();

                for (var entry : checks.entrySet()) {
                    if (entry.getValue().isSelected()) {
                        selectedMembers.add(entry.getKey());
                    }
                }

                if (selectedMembers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Select at least one participant.");
                }

                Map<Member, Long> shares = new LinkedHashMap<>();

                String mode = splitMode.getValue();

                if ("Keep Existing Shares".equals(mode)) {

                    Set<Integer> selectedIds = new HashSet<>();

                    for (Member member : selectedMembers) {
                        selectedIds.add(member.getId());
                    }

                    if (!selectedIds.equals(existingShares.keySet())) {
                        throw new IllegalArgumentException(
                                "You changed the participants. " +
                                "Choose Equal Split or Custom Split " +
                                "to update the distribution.");
                    }

                    long existingTotal = existingShares.values()
                            .stream()
                            .mapToLong(Long::longValue)
                            .sum();

                    if (existingTotal != totalPaise) {
                        throw new IllegalArgumentException(
                                "The amount has changed. " +
                                "Choose Equal Split or Custom Split " +
                                "to redistribute the new amount.");
                    }

                    for (Member member : selectedMembers) {

                        shares.put(
                                member,
                                existingShares.get(member.getId())
                        );
                    }

                } else if ("Equal Split".equals(mode)) {

                    long each = totalPaise / selectedMembers.size();
                    long remainder = totalPaise % selectedMembers.size();

                    for (int i = 0; i < selectedMembers.size(); i++) {

                        shares.put(
                                selectedMembers.get(i),
                                each + (i == 0 ? remainder : 0)
                        );
                    }

                } else {

                    for (Member member : selectedMembers) {

                        long share = ExpenseService.toPaise(
                                shareFields.get(member).getText());

                        shares.put(member, share);
                    }
                }

                // UPDATE DATABASE
                expenseService.updateExpense(
                        selected.getId(),
                        description.getText().trim(),
                        totalPaise,
                        payerBox.getValue(),
                        shares
                );

                loadExpenses();

                showAlert(
                        "Expense Updated",
                        "Expense details and participant shares " +
                        "updated successfully."
                );

            } catch (Exception ex) {

                showError(
                        "Unable to Update Expense",
                        ex.getMessage()
                );
            }
        });

    } catch (Exception e) {

        showError(
                "Edit Expense Error",
                e.getMessage()
        );
    }
}

    private void deleteSelectedExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select Expense",
                    "Please select an expense to delete.");
            return;
        }

        showConfirmation("Delete Expense",
                "Delete \"" + selected.getDescription() + "\"?",
                () -> {
                    try {
                        expenseService.deleteExpense(selected.getId());
                        loadExpenses();
                    } catch (Exception e) {
                        showError("Delete Failed", e.getMessage());
                    }
                });
    }

    // GROUP SETTLEMENTS

    private void showSettlements() {

    VBox main = pageContainer(
            "Group Settlements",
            "View balances and settle payments within a group."
    );

    // GROUP SELECTOR
    ComboBox<Group> groupBox = new ComboBox<>();
    groupBox.setPromptText("Select a group");
    groupBox.setPrefWidth(220);

    // ACTION BUTTONS
    Button refreshButton = greenButton("Refresh Balances");
    Button suggestionsButton = new Button("Payment Suggestions");
    Button recordButton = greenButton("Record Payment");

    FlowPane actions = new FlowPane();
    actions.setHgap(12);
    actions.setVgap(10);

    actions.getChildren().addAll(
            groupBox,
            refreshButton,
            suggestionsButton,
            recordButton
    );

    // BALANCE SUMMARY CARDS
    Label memberValue = new Label("0");
    Label receivableValue = new Label("₹0.00");
    Label payableValue = new Label("₹0.00");

    FlowPane summaryCards = new FlowPane();
    summaryCards.setHgap(14);
    summaryCards.setVgap(14);
    summaryCards.setPrefWrapLength(1000);

    summaryCards.getChildren().addAll(
            createCard("TOTAL MEMBERS", memberValue),
            createCard("TOTAL RECEIVABLE", receivableValue),
            createCard("TOTAL PAYABLE", payableValue)
    );

    // MEMBER BALANCES TABLE
    TableView<Map.Entry<String, Long>> table =
            new TableView<>();

    TableColumn<Map.Entry<String, Long>, String> memberCol =
            new TableColumn<>("Member");

    memberCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getKey()
            )
    );

    TableColumn<Map.Entry<String, Long>, String> balanceCol =
            new TableColumn<>("Net Balance");

    balanceCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    ExpenseService.rupees(
                            data.getValue().getValue()
                    )
            )
    );

    // BALANCE COLOUR INDICATORS
    balanceCol.setCellFactory(column ->
            new TableCell<Map.Entry<String, Long>, String>() {

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }

            setText(item);

            Map.Entry<String, Long> entry =
                    getTableRow().getItem();

            if (entry != null) {

                long balance = entry.getValue();

                if (balance > 0) {
                    setStyle(
                            "-fx-text-fill: #397C68;" +
                            "-fx-font-weight: bold;"
                    );
                } else if (balance < 0) {
                    setStyle(
                            "-fx-text-fill: #C45B50;" +
                            "-fx-font-weight: bold;"
                    );
                } else {
                    setStyle(
                            "-fx-text-fill: #78847C;"
                    );
                }
            }
        }
    });

    table.getColumns().addAll(
            List.of(memberCol, balanceCol)
    );

    table.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
    );

    javafx.collections.ObservableList<Map.Entry<String, Long>>
            balanceRows = FXCollections.observableArrayList();

    table.setItems(balanceRows);

    Label emptyMessage =
            new Label("Select a group to view member balances.");

    emptyMessage.getStyleClass().add("page-subtitle");
    table.setPlaceholder(emptyMessage);

    // BALANCE SUMMARY MESSAGE
    Label summary = new Label(
            "Select a group to view balances."
    );

    summary.setWrapText(true);
    summary.getStyleClass().add("page-subtitle");

    // DYNAMIC TABLE HEIGHT
    table.prefHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(
                            430.0,
                            Math.max(
                                    150.0,
                                    52.0 + balanceRows.size() * 46.0
                            )
                    ),
                    balanceRows
            )
    );

    // LOAD GROUPS
    try {
        groupBox.getItems().setAll(
                groupService.getAllGroups()
        );
    } catch (SQLException e) {
        showError("Loading Error", e.getMessage());
    }

    // REFRESH BALANCES
    Runnable refresh = () -> {

        Group selected = groupBox.getValue();

        if (selected == null) {
            return;
        }

        try {

            Map<String, Long> balances =
                    balanceService.getGroupBalances(
                            selected.getId()
                    );

            balanceRows.setAll(balances.entrySet());

            long receivable = balances.values().stream()
                    .filter(value -> value > 0)
                    .mapToLong(Long::longValue)
                    .sum();

            long payable = balances.values().stream()
                    .filter(value -> value < 0)
                    .mapToLong(value -> -value)
                    .sum();

            memberValue.setText(
                    String.valueOf(balances.size())
            );

            receivableValue.setText(
                    ExpenseService.rupees(receivable)
            );

            payableValue.setText(
                    ExpenseService.rupees(payable)
            );

            summary.setText(
                    balanceService.getBalanceSummary(
                            selected.getId()
                    )
            );

            emptyMessage.setText(
                    balances.isEmpty()
                            ? "No member balances available."
                            : "No balances to display."
            );

        } catch (SQLException e) {
            showError("Balance Error", e.getMessage());
        }
    };

    // GROUP SELECTION
    groupBox.setOnAction(e -> refresh.run());

    refreshButton.setOnAction(e -> refresh.run());

    // PAYMENT SUGGESTIONS
    suggestionsButton.setOnAction(e -> {

        Group selected = groupBox.getValue();

        if (selected == null) {
            showAlert("Select Group", "Choose a group first.");
            return;
        }

        try {

            List<SettlementService.SuggestedPayment> suggestions =
                    settlementService.getSuggestions(
                            selected.getId()
                    );

            StringBuilder text = new StringBuilder();

            if (suggestions.isEmpty()) {

                text.append("Everyone is settled!");

            } else {

                for (var payment : suggestions) {

                    text.append(payment.getFrom())
                            .append(" pays ")
                            .append(payment.getTo())
                            .append(" : ")
                            .append(
                                    ExpenseService.rupees(
                                            payment.getAmount()
                                    )
                            )
                            .append("\n");
                }
            }

            showAlert(
                    "Payment Suggestions",
                    text.toString()
            );

        } catch (Exception ex) {
            showError("Suggestion Error", ex.getMessage());
        }
    });

    // RECORD PAYMENT
    recordButton.setOnAction(e ->
            recordGroupPayment(groupBox, refresh)
    );

    // FINAL LAYOUT
    main.getChildren().addAll(
            actions,
            heading("BALANCE OVERVIEW", 18),
            summaryCards,
            heading("MEMBER BALANCES", 18),
            table,
            heading("SETTLEMENT SUMMARY", 18),
            summary
    );

    setMainContent(main);
}

    private void recordGroupPayment(
            ComboBox<Group> groupBox, Runnable refresh) {
        Group group = groupBox.getValue();
        if (group == null) {
            showAlert("Select Group", "Choose a group first.");
            return;
        }

        List<Member> members;
        try {
            members = groupService.getGroupMembers(group.getId());
        } catch (SQLException e) {
            showError("Loading Error", e.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);
        dialog.setTitle("Record Payment");
        dialog.setHeaderText("Record a group settlement");

        ButtonType save = new ButtonType(
                "Record", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(
                save, ButtonType.CANCEL);

        ComboBox<Member> from = new ComboBox<>();
        from.getItems().setAll(members);
        from.setPromptText("Paid by");

        ComboBox<Member> to = new ComboBox<>();
        to.getItems().setAll(members);
        to.setPromptText("Paid to");

        TextField amount = new TextField();
        amount.setPromptText("Amount in ₹");

        VBox content = new VBox(12,
                new Label("From"), from,
                new Label("To"), to,
                new Label("Amount (₹)"), amount);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result != save) return;
            try {
                if (from.getValue() == null || to.getValue() == null)
                    throw new IllegalArgumentException(
                            "Select both members.");

                long paise = ExpenseService.toPaise(amount.getText());
                settlementService.recordPayment(
                        group.getId(),
                        from.getValue().getName(),
                        to.getValue().getName(),
                        paise);

                refresh.run();
                showAlert("Payment Recorded",
                        "Group settlement recorded successfully.");
            } catch (Exception ex) {
                showError("Payment Failed", ex.getMessage());
            }
        });
    }

    // GLOBAL SETTLEMENTS

    private void showGlobalSettlements() {

    VBox main = pageContainer(
            "Global Settlements",
            "Combine balances from all groups and reduce unnecessary payments."
    );

    // ACTION BUTTONS
    Button refreshButton = greenButton("Refresh");
    Button suggestionsButton = new Button("Global Suggestions");
    Button recordPaymentButton =
            greenButton("＋ Record Global Payment");

    FlowPane globalActions = new FlowPane();
    globalActions.setHgap(12);
    globalActions.setVgap(10);

    globalActions.getChildren().addAll(
            refreshButton,
            suggestionsButton,
            recordPaymentButton
    );

    // SUMMARY CARDS
    Label memberValue = new Label("0");
    Label receivableValue = new Label("₹0.00");
    Label payableValue = new Label("₹0.00");

    FlowPane summaryCards = new FlowPane();
    summaryCards.setHgap(14);
    summaryCards.setVgap(14);
    summaryCards.setPrefWrapLength(1000);

    summaryCards.getChildren().addAll(
            createCard("TOTAL MEMBERS", memberValue),
            createCard("TOTAL RECEIVABLE", receivableValue),
            createCard("TOTAL PAYABLE", payableValue)
    );

    // GLOBAL BALANCE TABLE
    TableView<Map.Entry<String, Long>> table =
            new TableView<>();

    TableColumn<Map.Entry<String, Long>, String> memberCol =
            new TableColumn<>("Member");

    memberCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getKey()
            )
    );

    TableColumn<Map.Entry<String, Long>, String> balanceCol =
            new TableColumn<>("Global Balance");

    balanceCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    ExpenseService.rupees(
                            data.getValue().getValue()
                    )
            )
    );

    // COLOUR-CODED BALANCES
    balanceCol.setCellFactory(column ->
            new TableCell<Map.Entry<String, Long>, String>() {

        @Override
        protected void updateItem(String item, boolean empty) {

            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }

            setText(item);

            Map.Entry<String, Long> entry =
                    getTableRow().getItem();

            if (entry != null) {

                long balance = entry.getValue();

                if (balance > 0) {
                    setStyle(
                            "-fx-text-fill: #397C68;" +
                            "-fx-font-weight: bold;"
                    );
                } else if (balance < 0) {
                    setStyle(
                            "-fx-text-fill: #C45B50;" +
                            "-fx-font-weight: bold;"
                    );
                } else {
                    setStyle(
                            "-fx-text-fill: #78847C;"
                    );
                }
            }
        }
    });

    table.getColumns().addAll(
            List.of(memberCol, balanceCol)
    );

    table.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
    );

    javafx.collections.ObservableList<Map.Entry<String, Long>>
            balanceRows = FXCollections.observableArrayList();

    table.setItems(balanceRows);

    Label balanceEmpty = new Label(
            "No global balances available."
    );

    balanceEmpty.getStyleClass().add("page-subtitle");
    table.setPlaceholder(balanceEmpty);

    // DYNAMIC BALANCE TABLE HEIGHT
    table.prefHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(
                            430.0,
                            Math.max(
                                    150.0,
                                    52.0 + balanceRows.size() * 46.0
                            )
                    ),
                    balanceRows
            )
    );

    // GLOBAL SETTLEMENT HISTORY TABLE
    TableView<GlobalSettlementService.SettlementRecord>
            historyTable = new TableView<>();

    TableColumn<GlobalSettlementService.SettlementRecord, String>
            fromColumn = new TableColumn<>("Paid By");

    fromColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getFrom()
            )
    );

    TableColumn<GlobalSettlementService.SettlementRecord, String>
            toColumn = new TableColumn<>("Paid To");

    toColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getTo()
            )
    );

    TableColumn<GlobalSettlementService.SettlementRecord, String>
            amountColumn = new TableColumn<>("Amount");

    amountColumn.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(
                    ExpenseService.rupees(
                            data.getValue().getAmount()
                    )
            )
    );

    historyTable.getColumns().addAll(
            List.of(fromColumn, toColumn, amountColumn)
    );

    historyTable.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
    );

    Label historyEmpty = new Label(
            "No global payments recorded yet."
    );

    historyEmpty.getStyleClass().add("page-subtitle");
    historyTable.setPlaceholder(historyEmpty);

    // DYNAMIC HISTORY TABLE HEIGHT
    historyTable.prefHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.min(
                            300.0,
                            Math.max(
                                    120.0,
                                    52.0 + historyTable.getItems().size() * 46.0
                            )
                    ),
                    historyTable.getItems()
            )
    );

    // INFORMATION NOTE
    Label note = new Label(
            "Positive balance = receives money  |  "
                    + "Negative balance = owes money."
    );

    note.setWrapText(true);
    note.getStyleClass().add("page-subtitle");

    // REFRESH DATA
    Runnable refresh = () -> {

        try {

            Map<String, Long> balances =
                    globalSettlementService.getGlobalBalances();

            balanceRows.setAll(balances.entrySet());

            long receivable = balances.values().stream()
                    .filter(value -> value > 0)
                    .mapToLong(Long::longValue)
                    .sum();

            long payable = balances.values().stream()
                    .filter(value -> value < 0)
                    .mapToLong(value -> -value)
                    .sum();

            memberValue.setText(
                    String.valueOf(balances.size())
            );

            receivableValue.setText(
                    ExpenseService.rupees(receivable)
            );

            payableValue.setText(
                    ExpenseService.rupees(payable)
            );

            // LOAD GLOBAL PAYMENT HISTORY
            historyTable.getItems().setAll(
                    globalSettlementService.getHistory()
            );

        } catch (Exception e) {

            showError(
                    "Global Balance Error",
                    e.getMessage()
            );
        }
    };

    // BUTTON ACTIONS
    refreshButton.setOnAction(e -> refresh.run());

    recordPaymentButton.setOnAction(
            e -> recordGlobalPayment(refresh)
    );

    suggestionsButton.setOnAction(e -> {

        try {

            List<GlobalSettlementService.SuggestedPayment>
                    suggestions =
                    globalSettlementService.getSuggestions();

            StringBuilder text = new StringBuilder();

            if (suggestions.isEmpty()) {

                text.append("All members are globally settled!");

            } else {

                for (var payment : suggestions) {

                    text.append(payment.getFrom())
                            .append(" pays ")
                            .append(payment.getTo())
                            .append(" : ")
                            .append(
                                    ExpenseService.rupees(
                                            payment.getAmount()
                                    )
                            )
                            .append("\n");
                }
            }

            showAlert(
                    "Global Payment Suggestions",
                    text.toString()
            );

        } catch (Exception ex) {

            showError(
                    "Global Suggestion Error",
                    ex.getMessage()
            );
        }
    });

    // FINAL LAYOUT
    main.getChildren().addAll(
            globalActions,

            heading("GLOBAL OVERVIEW", 18),
            summaryCards,

            heading("GLOBAL MEMBER BALANCES", 18),
            table,
            note,

            heading("GLOBAL SETTLEMENT HISTORY", 18),
            historyTable
    );

    setMainContent(main);

    refresh.run();
}
// RECORD GLOBAL PAYMENT

private void recordGlobalPayment(Runnable refresh) {

    try {
        Map<String, Long> balances =
                globalSettlementService.getGlobalBalances();

        List<String> debtors = new ArrayList<>();
        List<String> creditors = new ArrayList<>();

        for (var entry : balances.entrySet()) {
            if (entry.getValue() < 0) {
                debtors.add(entry.getKey());
            } else if (entry.getValue() > 0) {
                creditors.add(entry.getKey());
            }
        }

        if (debtors.isEmpty() || creditors.isEmpty()) {
            showAlert("No Outstanding Payments",
                    "There are no global payments to record.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        styleDialog(dialog);

        dialog.setTitle("Record Global Payment");
        dialog.setHeaderText(
                "Settle outstanding balances across all groups");

        ButtonType save = new ButtonType(
                "Record Payment",
                ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                save, ButtonType.CANCEL);

        ComboBox<String> from = new ComboBox<>();
        from.getItems().setAll(debtors);
        from.setPromptText("Select paying member");

        ComboBox<String> to = new ComboBox<>();
        to.getItems().setAll(creditors);
        to.setPromptText("Select receiving member");

        TextField amount = new TextField();
        amount.setPromptText("Enter amount");

        Label currency = new Label("Amount (₹)");

        Runnable updateAmount = () -> {

            if (from.getValue() == null ||
                    to.getValue() == null) {
                return;
            }

            long debt = -balances.get(from.getValue());
            long receivable = balances.get(to.getValue());

            long suggested = Math.min(debt, receivable);

            amount.setText(String.format(
                    Locale.US, "%.2f", suggested / 100.0));
        };

        from.setOnAction(e -> updateAmount.run());
        to.setOnAction(e -> updateAmount.run());

        VBox content = new VBox(12,
                new Label("Paying Member"),
                from,
                new Label("Receiving Member"),
                to,
                currency,
                amount
        );

        content.setPadding(new Insets(20));

        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {

            if (result != save) return;

            try {
                if (from.getValue() == null ||
                        to.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Select both members.");
                }

                long paise = ExpenseService.toPaise(
                        amount.getText());

                globalSettlementService.recordPayment(
                        from.getValue(),
                        to.getValue(),
                        paise
                );

                refresh.run();

                showAlert("Payment Successful",
                        "Global payment recorded successfully.");

            } catch (Exception ex) {
                showError("Payment Failed", ex.getMessage());
            }
        });

    } catch (Exception ex) {
        showError("Global Payment Error", ex.getMessage());
    }
}
    // COMMON UI HELPERS

    private VBox pageContainer(String title, String subtitle) {
        VBox box = new VBox(18);
        box.setPadding(new Insets(28));

        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");

        Label description = new Label(subtitle);
        description.getStyleClass().add("page-subtitle");

        box.getChildren().addAll(heading, description);
        return box;
    }

    private Label heading(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("section-heading");
        label.setStyle("-fx-font-size: " + size +
                "px; -fx-font-weight: bold;");
        return label;
    }

    private Button greenButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        return button;
    }

    /** Applies the application's stylesheet to JavaFX's separate dialog window. */
    private void styleDialog(Dialog<?> dialog) {
        dialog.initOwner(stage);
        dialog.getDialogPane().getStyleClass().add("splitmate-dialog");
        var stylesheet = getClass().getResource("/css/app.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        styleDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        styleDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(
                message == null ? "An unexpected error occurred." : message);
        alert.showAndWait();
    }

    private void showConfirmation(
            String title, String message, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        styleDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) onConfirm.run();
        });
    }

    private void setMainContent(VBox main) {

    ScrollPane scrollPane = new ScrollPane(main);

    scrollPane.setFitToWidth(true);
    scrollPane.setFitToHeight(false);
    scrollPane.setPannable(true);
    scrollPane.setHbarPolicy(
            ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(
            ScrollPane.ScrollBarPolicy.AS_NEEDED);

    root.setCenter(scrollPane);
}

    public static void main(String[] args) {
        launch(args);
    }
}
