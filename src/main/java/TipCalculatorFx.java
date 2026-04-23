import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TipCalculatorFx extends Application {

    private static final double SERVER_WAGE = 3.00;
    private static final double HOST_WAGE = 11.50;
    private static final double TA_WAGE = 12.00;

    private static final String DB_URL = "jdbc:sqlite:tip_calculator.db";

    private final Label statusLabel = new Label("Ready");
    private final Label pageTitle = new Label("Log Shift");
    private final StackPane content = new StackPane();
    private final ObservableList<ShiftRow> shiftRows = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

    private Button activeNavButton;

    @Override
    public void start(Stage stage) {
        initDatabase();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        VBox sidebar = buildSidebar();
        BorderPane main = buildMainArea();

        root.setLeft(sidebar);
        root.setCenter(main);

        Scene scene = new Scene(root, 1020, 680);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());

        stage.setTitle("Baoding Tip Calculator");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        showLogShiftView();
    }

    private VBox buildSidebar() {
        Label brand = new Label("Tip Calculator");
        brand.getStyleClass().add("brand-title");

        Label subtitle = new Label("Baoding shift tracker");
        subtitle.getStyleClass().add("brand-subtitle");

        Button logButton = navButton("Log Shift");
        Button summaryButton = navButton("Monthly Summary");
        Button shiftsButton = navButton("Shift History");
        Button deleteButton = navButton("Delete Shifts");
        Button helpButton = navButton("Help");

        logButton.setOnAction(e -> showLogShiftView());
        summaryButton.setOnAction(e -> showMonthlySummaryView());
        shiftsButton.setOnAction(e -> showShiftHistoryView());
        deleteButton.setOnAction(e -> showDeleteView());
        helpButton.setOnAction(e -> showHelpView());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("SQLite data saved locally");
        footer.getStyleClass().add("sidebar-footer");

        VBox sidebar = new VBox(10, brand, subtitle, gap(12), logButton, summaryButton, shiftsButton, deleteButton, helpButton, spacer, footer);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(230);
        return sidebar;
    }

    private BorderPane buildMainArea() {
        pageTitle.getStyleClass().add("page-title");

        statusLabel.getStyleClass().add("status-pill");
        statusLabel.setMinWidth(Region.USE_PREF_SIZE);

        HBox topBar = new HBox(14, pageTitle, growingSpace(), statusLabel);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        content.getStyleClass().add("content-host");

        BorderPane main = new BorderPane();
        main.setTop(topBar);
        main.setCenter(content);
        return main;
    }

    private Button navButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        return button;
    }

    private void activate(Button button) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-button-active");
        }
        activeNavButton = button;
        if (!button.getStyleClass().contains("nav-button-active")) {
            button.getStyleClass().add("nav-button-active");
        }
    }

    private void showLogShiftView() {
        pageTitle.setText("Log Shift");
        activate((Button) ((VBox) ((BorderPane) content.getScene().getRoot()).getLeft()).getChildren().get(3));

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);

        ToggleGroup roleGroup = new ToggleGroup();
        ToggleButton serverButton = roleButton("Server", "SERVER", roleGroup);
        ToggleButton hostButton = roleButton("Host", "HOST", roleGroup);
        ToggleButton taButton = roleButton("TA", "TA", roleGroup);
        serverButton.setSelected(true);

        HBox rolePicker = new HBox(8, serverButton, hostButton, taButton);
        rolePicker.getStyleClass().add("segmented");

        TextField hoursField = new TextField();
        hoursField.setPromptText("5.5");

        TextField tipsField = new TextField();
        tipsField.setPromptText("120");

        Label wageLabel = new Label(wageText("SERVER"));
        wageLabel.getStyleClass().add("hint-label");

        Label estimateLabel = new Label("Enter hours and tips to preview earnings.");
        estimateLabel.getStyleClass().add("result-line");

        Button saveButton = primaryButton("Save Shift");
        Button clearButton = secondaryButton("Clear");

        Runnable updateEstimate = () -> {
            String role = selectedRole(roleGroup);
            double wage = wageForRole(role);
            wageLabel.setText(wageText(role));

            boolean isTa = "TA".equals(role);
            tipsField.setDisable(isTa);
            tipsField.setPromptText(isTa ? "No tips for TA" : "120");

            Double hours = parseDouble(hoursField.getText());
            Double tips = isTa ? 0.0 : parseDouble(tipsField.getText());

            if (hours == null || hours <= 0 || tips == null || tips < 0) {
                estimateLabel.setText("Enter hours and tips to preview earnings.");
                return;
            }

            double total = tips + (hours * wage);
            estimateLabel.setText("Preview total: " + currency.format(total) + " at " + currency.format(total / hours) + "/hr");
        };

        roleGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> updateEstimate.run());
        hoursField.textProperty().addListener((obs, oldValue, newValue) -> updateEstimate.run());
        tipsField.textProperty().addListener((obs, oldValue, newValue) -> updateEstimate.run());

        VBox snapshotMetrics = new VBox(10);
        Runnable refreshSnapshot = () -> snapshotMetrics.getChildren().setAll(summaryCards(YearMonth.now(), getMonthlySummary(YearMonth.now())));
        refreshSnapshot.run();

        clearButton.setOnAction(e -> {
            datePicker.setValue(LocalDate.now());
            serverButton.setSelected(true);
            hoursField.clear();
            tipsField.clear();
            setStatus("Form cleared");
            updateEstimate.run();
        });

        saveButton.setOnAction(e -> {
            String role = selectedRole(roleGroup);
            LocalDate date = datePicker.getValue();
            Double hours = parseDouble(hoursField.getText());
            Double tips = "TA".equals(role) ? 0.0 : parseDouble(tipsField.getText());

            if (date == null) {
                showError("Pick a date before saving the shift.");
                return;
            }
            if (hours == null || hours <= 0) {
                showError("Hours must be greater than 0.");
                return;
            }
            if (tips == null || tips < 0) {
                showError("Tips must be 0 or more.");
                return;
            }

            double wage = wageForRole(role);
            insertShift(date, role, hours, tips, wage);

            double total = tips + (hours * wage);
            estimateLabel.setText("Saved " + role + " shift for " + currency.format(total) + ".");
            setStatus("Shift saved");
            refreshSnapshot.run();
            hoursField.clear();
            tipsField.clear();
        });

        GridPane form = new GridPane();
        form.getStyleClass().add("form-grid");
        form.add(fieldLabel("Date"), 0, 0);
        form.add(datePicker, 1, 0);
        form.add(fieldLabel("Role"), 0, 1);
        form.add(rolePicker, 1, 1);
        form.add(wageLabel, 1, 2);
        form.add(fieldLabel("Hours worked"), 0, 3);
        form.add(hoursField, 1, 3);
        form.add(fieldLabel("Tips"), 0, 4);
        form.add(tipsField, 1, 4);

        HBox actions = new HBox(10, saveButton, clearButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox formPanel = panel("New Shift", "Add a shift with the correct role, wage, hours, and tips.", form, estimateLabel, actions);
        VBox summaryPanel = panel("This Month", "A quick read on the current month after each saved shift.", snapshotMetrics);

        HBox layout = new HBox(18, formPanel, summaryPanel);
        layout.getStyleClass().add("two-column");
        HBox.setHgrow(formPanel, Priority.ALWAYS);
        HBox.setHgrow(summaryPanel, Priority.ALWAYS);

        setContent(layout);
    }

    private void showMonthlySummaryView() {
        pageTitle.setText("Monthly Summary");
        activate((Button) ((VBox) ((BorderPane) content.getScene().getRoot()).getLeft()).getChildren().get(4));

        DatePicker monthPicker = new DatePicker(LocalDate.now());
        Button loadButton = primaryButton("Load Summary");

        VBox metrics = new VBox(12);
        metrics.getStyleClass().add("metrics-grid");

        Runnable loadSummary = () -> {
            LocalDate date = monthPicker.getValue();
            if (date == null) {
                showError("Pick any date in the month you want to view.");
                return;
            }

            YearMonth ym = YearMonth.from(date);
            MonthlySummary summary = getMonthlySummary(ym);
            metrics.getChildren().setAll(summaryCards(ym, summary));
            setStatus("Loaded " + ym);
        };

        loadButton.setOnAction(e -> loadSummary.run());

        HBox controls = new HBox(10, fieldLabel("Month"), monthPicker, loadButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox panel = panel("Month Totals", "Pick any day in the month to see totals and average hourly earnings.", controls, metrics);
        setContent(panel);
        loadSummary.run();
    }

    private void showShiftHistoryView() {
        pageTitle.setText("Shift History");
        activate((Button) ((VBox) ((BorderPane) content.getScene().getRoot()).getLeft()).getChildren().get(5));

        DatePicker monthPicker = new DatePicker(LocalDate.now());
        Button loadButton = primaryButton("Load Shifts");
        Button deleteSelectedButton = dangerButton("Delete Selected");
        deleteSelectedButton.setTooltip(new Tooltip("Deletes the selected table row"));

        TableView<ShiftRow> table = buildShiftTable();
        table.setItems(shiftRows);

        Runnable loadRows = () -> {
            LocalDate date = monthPicker.getValue();
            if (date == null) {
                showError("Pick any date in the month you want to view.");
                return;
            }

            YearMonth ym = YearMonth.from(date);
            shiftRows.setAll(fetchShiftsForMonth(ym));
            setStatus("Loaded " + shiftRows.size() + " shift(s)");
        };

        loadButton.setOnAction(e -> loadRows.run());
        deleteSelectedButton.setOnAction(e -> {
            ShiftRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Select a shift in the table first.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete shift");
            confirm.setHeaderText("Delete the selected " + selected.getRole() + " shift?");
            confirm.setContentText(selected.getDate() + " - " + currency.format(selected.getTotal()));

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                deleteShiftById(selected.getId());
                loadRows.run();
                setStatus("Shift deleted");
            }
        });

        HBox controls = new HBox(10, fieldLabel("Month"), monthPicker, loadButton, deleteSelectedButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox panel = panel("Saved Shifts", "Review shifts for a month, then select a row to delete only that entry.", controls, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setContent(panel);
        loadRows.run();
    }

    private void showDeleteView() {
        pageTitle.setText("Delete Shifts");
        activate((Button) ((VBox) ((BorderPane) content.getScene().getRoot()).getLeft()).getChildren().get(6));

        DatePicker datePicker = new DatePicker(LocalDate.now());
        Button deleteButton = dangerButton("Delete Date");
        Label result = new Label("Choose a date to remove every shift logged on that date.");
        result.getStyleClass().add("result-line");

        deleteButton.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            if (date == null) {
                showError("Pick a date first.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete shifts");
            confirm.setHeaderText("Delete all shifts on " + date + "?");
            confirm.setContentText("This action removes every shift logged for that date.");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                setStatus("Delete canceled");
                return;
            }

            int deleted = deleteShiftsByDate(date);
            result.setText("Deleted " + deleted + " shift(s) on " + date + ".");
            setStatus("Delete complete");
        });

        HBox controls = new HBox(10, fieldLabel("Date"), datePicker, deleteButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox panel = panel("Delete by Date", "Use this when a whole date was entered incorrectly.", controls, result);
        setContent(panel);
    }

    private void showHelpView() {
        pageTitle.setText("Help");
        activate((Button) ((VBox) ((BorderPane) content.getScene().getRoot()).getLeft()).getChildren().get(7));

        VBox rates = new VBox(
            8,
            detailRow("Server", currency.format(SERVER_WAGE) + "/hr plus tips"),
            detailRow("Host", currency.format(HOST_WAGE) + "/hr plus tips"),
            detailRow("TA", currency.format(TA_WAGE) + "/hr, no tips")
        );

        VBox workflow = new VBox(
            8,
            detailRow("Log Shift", "Save today or a past shift."),
            detailRow("Monthly Summary", "View total shifts, hours, tips, earnings, and average hourly pay."),
            detailRow("Shift History", "Load a monthly table and delete one selected shift."),
            detailRow("Delete Shifts", "Delete every shift on a selected date.")
        );

        HBox layout = new HBox(18, panel("Pay Rules", "Current role rates used by the calculator.", rates), panel("Where Things Are", "Quick map of the app buttons.", workflow));
        layout.getStyleClass().add("two-column");
        setContent(layout);
    }

    private ToggleButton roleButton(String label, String role, ToggleGroup group) {
        ToggleButton button = new ToggleButton(label);
        button.setUserData(role);
        button.setToggleGroup(group);
        button.getStyleClass().add("role-button");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }

    private List<Region> summaryCards(YearMonth ym, MonthlySummary summary) {
        String average = summary.totalHours > 0 ? currency.format(summary.totalEarnings / summary.totalHours) : "N/A";
        return List.of(
            metricCard(monthLabel(ym), "Month"),
            metricCard(String.valueOf(summary.shiftCount), "Shifts"),
            metricCard(round2(summary.totalHours), "Hours"),
            metricCard(currency.format(summary.totalTips), "Tips"),
            metricCard(currency.format(summary.totalEarnings), "Total earnings"),
            metricCard(average, "Average per hour")
        );
    }

    private TableView<ShiftRow> buildShiftTable() {
        TableView<ShiftRow> table = new TableView<>();
        table.getStyleClass().add("shift-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<ShiftRow, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<ShiftRow, String> role = new TableColumn<>("Role");
        role.setCellValueFactory(new PropertyValueFactory<>("role"));

        TableColumn<ShiftRow, Double> hours = new TableColumn<>("Hours");
        hours.setCellValueFactory(new PropertyValueFactory<>("hours"));
        hours.setCellFactory(col -> numberCell());

        TableColumn<ShiftRow, Double> tips = new TableColumn<>("Tips");
        tips.setCellValueFactory(new PropertyValueFactory<>("tips"));
        tips.setCellFactory(col -> moneyCell());

        TableColumn<ShiftRow, Double> wage = new TableColumn<>("Wage");
        wage.setCellValueFactory(new PropertyValueFactory<>("wage"));
        wage.setCellFactory(col -> moneyCell());

        TableColumn<ShiftRow, Double> total = new TableColumn<>("Total");
        total.setCellValueFactory(new PropertyValueFactory<>("total"));
        total.setCellFactory(col -> moneyCell());

        table.getColumns().addAll(date, role, hours, tips, wage, total);
        table.setPlaceholder(new Label("No shifts saved for this month."));
        return table;
    }

    private TableCell<ShiftRow, Double> moneyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : currency.format(value));
            }
        };
    }

    private TableCell<ShiftRow, Double> numberCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : round2(value));
            }
        };
    }

    private VBox panel(String title, String subtitle, javafx.scene.Node... children) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("panel-subtitle");
        subtitleLabel.setWrapText(true);

        VBox panel = new VBox(14, titleLabel, subtitleLabel);
        panel.getChildren().addAll(children);
        panel.getStyleClass().add("panel");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Region metricCard(String value, String label) {
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("metric-label");

        VBox card = new VBox(4, valueLabel, labelLabel);
        card.getStyleClass().add("metric-card");
        return card;
    }

    private HBox detailRow(String label, String value) {
        Label left = new Label(label);
        left.getStyleClass().add("detail-label");

        Label right = new Label(value);
        right.getStyleClass().add("detail-value");
        right.setWrapText(true);

        HBox row = new HBox(14, left, right);
        row.getStyleClass().add("detail-row");
        HBox.setHgrow(right, Priority.ALWAYS);
        return row;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setFocusTraversable(false);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setFocusTraversable(false);
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        button.setFocusTraversable(false);
        return button;
    }

    private Region gap(double height) {
        Region gap = new Region();
        gap.setMinHeight(height);
        gap.setPrefHeight(height);
        return gap;
    }

    private Region growingSpace() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void setContent(javafx.scene.Node node) {
        content.getChildren().setAll(node);
        StackPane.setMargin(node, new Insets(24));
    }

    private void setStatus(String message) {
        statusLabel.setText(message == null || message.isBlank() ? "Ready" : message);
    }

    private void showError(String message) {
        setStatus(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Check your entry");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS shifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                shift_date TEXT NOT NULL,
                role TEXT NOT NULL,
                hours_worked REAL NOT NULL,
                tips REAL NOT NULL,
                wage_rate REAL NOT NULL
            );
            """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println("Failed to init DB: " + e.getMessage());
        }
    }

    private static void insertShift(LocalDate date, String role, double hours, double tips, double wage) {
        String sql = "INSERT INTO shifts VALUES (NULL, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ps.setString(2, role);
            ps.setDouble(3, hours);
            ps.setDouble(4, tips);
            ps.setDouble(5, wage);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
        }
    }

    private static int deleteShiftsByDate(LocalDate date) {
        String sql = "DELETE FROM shifts WHERE shift_date = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
            return 0;
        }
    }

    private static int deleteShiftById(int id) {
        String sql = "DELETE FROM shifts WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
            return 0;
        }
    }

    private static MonthlySummary getMonthlySummary(YearMonth ym) {
        String sql = """
            SELECT
                COALESCE(COUNT(*), 0),
                COALESCE(SUM(hours_worked), 0),
                COALESCE(SUM(tips), 0),
                COALESCE(SUM(tips + hours_worked * wage_rate), 0)
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?
            """;

        MonthlySummary summary = new MonthlySummary();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ym.atDay(1).toString());
            ps.setString(2, ym.atEndOfMonth().toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.shiftCount = rs.getInt(1);
                    summary.totalHours = rs.getDouble(2);
                    summary.totalTips = rs.getDouble(3);
                    summary.totalEarnings = rs.getDouble(4);
                }
            }
        } catch (SQLException e) {
            System.out.println("Summary failed: " + e.getMessage());
        }

        return summary;
    }

    private static ObservableList<ShiftRow> fetchShiftsForMonth(YearMonth ym) {
        ObservableList<ShiftRow> rows = FXCollections.observableArrayList();
        String sql = """
            SELECT id, shift_date, role, hours_worked, tips, wage_rate,
                   (tips + hours_worked * wage_rate) AS total_earnings
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?
            ORDER BY shift_date DESC, id DESC
            """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ym.atDay(1).toString());
            ps.setString(2, ym.atEndOfMonth().toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ShiftRow(
                        rs.getInt("id"),
                        rs.getString("shift_date"),
                        rs.getString("role"),
                        rs.getDouble("hours_worked"),
                        rs.getDouble("tips"),
                        rs.getDouble("wage_rate"),
                        rs.getDouble("total_earnings")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("List failed: " + e.getMessage());
        }

        return rows;
    }

    private static String selectedRole(ToggleGroup roleGroup) {
        if (roleGroup.getSelectedToggle() == null) {
            return "SERVER";
        }
        return roleGroup.getSelectedToggle().getUserData().toString();
    }

    private static Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double wageForRole(String role) {
        if (role == null) {
            return SERVER_WAGE;
        }

        return switch (role.toUpperCase()) {
            case "HOST" -> HOST_WAGE;
            case "TA" -> TA_WAGE;
            default -> SERVER_WAGE;
        };
    }

    private String wageText(String role) {
        return "Wage: " + currency.format(wageForRole(role)) + "/hr" + ("TA".equals(role) ? ", no tips" : " plus tips");
    }

    private static String monthLabel(YearMonth ym) {
        String month = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.US);
        return month + " " + ym.getYear();
    }

    private static String round2(double value) {
        return String.format("%.2f", value);
    }

    private static class MonthlySummary {
        int shiftCount;
        double totalHours;
        double totalTips;
        double totalEarnings;
    }

    public static class ShiftRow {
        private final SimpleIntegerProperty id;
        private final SimpleStringProperty date;
        private final SimpleStringProperty role;
        private final SimpleDoubleProperty hours;
        private final SimpleDoubleProperty tips;
        private final SimpleDoubleProperty wage;
        private final SimpleDoubleProperty total;

        public ShiftRow(int id, String date, String role, double hours, double tips, double wage, double total) {
            this.id = new SimpleIntegerProperty(id);
            this.date = new SimpleStringProperty(date);
            this.role = new SimpleStringProperty(role);
            this.hours = new SimpleDoubleProperty(hours);
            this.tips = new SimpleDoubleProperty(tips);
            this.wage = new SimpleDoubleProperty(wage);
            this.total = new SimpleDoubleProperty(total);
        }

        public int getId() {
            return id.get();
        }

        public String getDate() {
            return date.get();
        }

        public String getRole() {
            return role.get();
        }

        public double getHours() {
            return hours.get();
        }

        public double getTips() {
            return tips.get();
        }

        public double getWage() {
            return wage.get();
        }

        public double getTotal() {
            return total.get();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
