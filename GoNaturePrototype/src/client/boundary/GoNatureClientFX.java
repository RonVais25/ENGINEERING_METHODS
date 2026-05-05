package client.boundary;

import client.net.ClientConnection;
import common.dto.*;
import javafx.application.*;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GoNatureClientFX extends Application {

    // Design tokens
    private static final String G800  = "#1a3320";
    private static final String G700  = "#254d2e";
    private static final String G600  = "#2e6638";
    private static final String G500  = "#3d8c4a";
    private static final String G400  = "#55b363";
    private static final String G300  = "#80cb8b";
    private static final String G200  = "#c2e5c8";
    private static final String G100  = "#e5f3e8";
    private static final String G50   = "#f2f9f3";
    private static final String RED   = "#c94040";

    private final ClientConnection client = new ClientConnection("localhost", 5555);
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    private String currentScreen = "dashboard";
    private VBox mainArea;
    private Label topbarTitle;
    private Label topbarSubtitle;

    // ─── Application entry ────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        HBox root = new HBox();

        VBox sidebar = buildSidebar();
        mainArea = new VBox();
        mainArea.getChildren().add(buildTopbar());
        HBox.setHgrow(mainArea, Priority.ALWAYS);

        root.getChildren().addAll(sidebar, mainArea);

        Scene scene = new Scene(root, 1000, 640);
        stage.setScene(scene);
        stage.setTitle("GoNature Park System");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();

        switchScreen("dashboard");
    }

    public static void main(String[] args) { launch(args); }

    // ─── Sidebar ──────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(230);
        sidebar.setStyle("-fx-background-color:" + G800 + ";");

        // Logo
        HBox logo = new HBox(10);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPadding(new Insets(16));
        logo.setStyle("-fx-border-color:rgba(255,255,255,0.07); -fx-border-width:0 0 1 0;");

        StackPane iconTile = new StackPane();
        iconTile.setPrefSize(38, 38);
        iconTile.setMinSize(38, 38);
        iconTile.setStyle("-fx-background-color:" + G500 + "; -fx-background-radius:11;");
        SVGPath leaf = new SVGPath();
        leaf.setContent("M11 2C6.5 2 3 6 3 10.5c0 3 2 6.5 8 9.5 6-3 8-6.5 8-9.5C19 6 15.5 2 11 2z");
        leaf.setFill(Color.WHITE);
        leaf.setScaleX(0.8);
        leaf.setScaleY(0.8);
        iconTile.getChildren().add(leaf);

        VBox logoText = new VBox(1);
        Label appName = new Label("GoNature");
        appName.setStyle("-fx-font-size:16; -fx-font-weight:bold; -fx-text-fill:white;");
        Label appSub = new Label("PARK SYSTEM");
        appSub.setStyle("-fx-font-size:10; -fx-font-weight:bold; -fx-text-fill:" + G300 + ";");
        logoText.getChildren().addAll(appName, appSub);

        logo.getChildren().addAll(iconTile, logoText);

        // Nav label
        Label menuLabel = new Label("MENU");
        menuLabel.setStyle("-fx-font-size:10; -fx-font-weight:bold; -fx-text-fill:" + G400 +
                           "; -fx-padding:20 0 8 12;");

        // Nav items
        VBox nav = new VBox(2);
        nav.setPadding(new Insets(0, 10, 0, 10));

        String[][] items = {
            {"dashboard", "⊞", "Dashboard"},
            {"get",       "⊕", "Get Order"},
            {"update",    "✎", "Update Order"},
            {"new",       "+", "New Booking"},
            {"history",   "☰", "History"},
        };
        for (String[] item : items) {
            Button btn = buildNavButton(item[0], item[1], item[2]);
            navButtons.put(item[0], btn);
            nav.getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Connection status
        HBox connPill = new HBox(8);
        connPill.setAlignment(Pos.CENTER_LEFT);
        connPill.setPadding(new Insets(8, 12, 8, 12));
        connPill.setStyle("-fx-background-color:rgba(255,255,255,0.05); -fx-background-radius:10;");
        Circle dot = new Circle(3.5, Color.web("#64c864"));
        VBox connText = new VBox(1);
        Label host = new Label("localhost:5555");
        host.setStyle("-fx-font-size:12; -fx-font-weight:bold; -fx-text-fill:" + G200 + ";");
        Label connStatus = new Label("Connected");
        connStatus.setStyle("-fx-font-size:11; -fx-text-fill:" + G300 + ";");
        connText.getChildren().addAll(host, connStatus);
        connPill.getChildren().addAll(dot, connText);

        HBox connWrapper = new HBox(connPill);
        connWrapper.setPadding(new Insets(8, 10, 8, 10));
        HBox.setHgrow(connPill, Priority.ALWAYS);

        // User row
        StackPane avatar = new StackPane();
        avatar.setPrefSize(34, 34);
        avatar.setMinSize(34, 34);
        avatar.setStyle("-fx-background-color:" + G500 + "; -fx-background-radius:50;");
        Label initials = new Label("RN");
        initials.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:white;");
        avatar.getChildren().add(initials);

        VBox userText = new VBox(1);
        Label userName = new Label("Ron N.");
        userName.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:white;");
        Label userId = new Label("Subscriber #4821");
        userId.setStyle("-fx-font-size:11; -fx-text-fill:" + G400 + ";");
        userText.getChildren().addAll(userName, userId);

        HBox userRow = new HBox(10);
        userRow.setAlignment(Pos.CENTER_LEFT);
        userRow.setPadding(new Insets(14, 12, 14, 12));
        userRow.setStyle("-fx-border-color:rgba(255,255,255,0.06); -fx-border-width:1 0 0 0;");
        userRow.getChildren().addAll(avatar, userText);

        sidebar.getChildren().addAll(logo, menuLabel, nav, spacer, connWrapper, userRow);
        return sidebar;
    }

    private Button buildNavButton(String screen, String icon, String label) {
        Label iconLbl = new Label(icon);
        iconLbl.setPrefWidth(20);
        iconLbl.setAlignment(Pos.CENTER);

        Label textLbl = new Label(label);

        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(iconLbl, textLbl);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPadding(new Insets(10, 12, 10, 12));
        btn.setCursor(Cursor.HAND);
        applyNavStyle(btn, iconLbl, textLbl, false);

        btn.setOnMouseEntered(e -> { if (!currentScreen.equals(screen)) applyNavStyle(btn, iconLbl, textLbl, true);  });
        btn.setOnMouseExited(e  -> { if (!currentScreen.equals(screen)) applyNavStyle(btn, iconLbl, textLbl, false); });
        btn.setOnAction(e -> switchScreen(screen));
        return btn;
    }

    private void applyNavStyle(Button btn, Label icon, Label text, boolean hover) {
        btn.setStyle("-fx-background-color:" + (hover ? "rgba(255,255,255,0.06)" : "transparent") +
                     "; -fx-background-radius:10; -fx-border-color:transparent;");
        String fg = hover ? "white" : G300;
        icon.setStyle("-fx-font-size:16; -fx-text-fill:" + fg + "; -fx-opacity:" + (hover ? "1.0" : "0.7") + ";");
        text.setStyle("-fx-font-size:14; -fx-font-weight:500; -fx-text-fill:" + fg + ";");
    }

    private void applyNavActiveStyle(Button btn, Label icon, Label text) {
        btn.setStyle("-fx-background-color:" + G600 + "; -fx-background-radius:10; -fx-border-color:transparent;");
        icon.setStyle("-fx-font-size:16; -fx-text-fill:white; -fx-opacity:1.0;");
        text.setStyle("-fx-font-size:14; -fx-font-weight:500; -fx-text-fill:white;");
    }

    // ─── Topbar ───────────────────────────────────────────────────────────────

    private HBox buildTopbar() {
        topbarTitle    = new Label("Dashboard");
        topbarSubtitle = new Label("Welcome back");
        topbarTitle.setStyle("-fx-font-size:18; -fx-font-weight:bold; -fx-text-fill:" + G800 + ";");
        topbarSubtitle.setStyle("-fx-font-size:12; -fx-text-fill:" + G400 + ";");

        VBox titleBox = new VBox(2, topbarTitle, topbarSubtitle);

        Label version = new Label("v1.0");
        version.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G600 +
                         "; -fx-background-color:" + G100 + "; -fx-background-radius:100;" +
                         " -fx-border-color:" + G200 + "; -fx-border-radius:100; -fx-border-width:1;" +
                         " -fx-padding:4 10 4 10;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topbar = new HBox();
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.setPadding(new Insets(0, 24, 0, 28));
        topbar.setPrefHeight(64);
        topbar.setMinHeight(64);
        topbar.setStyle("-fx-background-color:white; -fx-border-color:" + G200 + "; -fx-border-width:0 0 1 0;");
        topbar.getChildren().addAll(titleBox, spacer, version);
        return topbar;
    }

    // ─── Screen switching ─────────────────────────────────────────────────────

    private void switchScreen(String screen) {
        currentScreen = screen;

        navButtons.forEach((key, btn) -> {
            HBox content = (HBox) btn.getGraphic();
            Label icon = (Label) content.getChildren().get(0);
            Label text = (Label) content.getChildren().get(1);
            if (key.equals(screen)) applyNavActiveStyle(btn, icon, text);
            else                    applyNavStyle(btn, icon, text, false);
        });

        String[][] titles = {
            {"dashboard", "Dashboard",    "Welcome back"},
            {"get",       "Get Order",    "Look up an existing order"},
            {"update",    "Update Order", "Modify order details"},
            {"new",       "New Booking",  "Reserve your next park visit"},
            {"history",   "History",      "All your past orders"},
        };
        for (String[] t : titles) {
            if (t[0].equals(screen)) {
                topbarTitle.setText(t[1]);
                topbarSubtitle.setText(t[2]);
                break;
            }
        }

        Node screenNode = switch (screen) {
            case "dashboard" -> buildDashboardScreen();
            case "get"       -> buildGetOrderScreen();
            case "update"    -> buildUpdateOrderScreen();
            case "new"       -> buildNewBookingScreen();
            case "history"   -> buildHistoryScreen();
            default          -> buildDashboardScreen();
        };

        if (mainArea.getChildren().size() > 1) mainArea.getChildren().set(1, screenNode);
        else                                   mainArea.getChildren().add(screenNode);
        VBox.setVgrow(screenNode, Priority.ALWAYS);
    }

    // ─── Screen: Dashboard ────────────────────────────────────────────────────

    private Node buildDashboardScreen() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(26, 28, 26, 28));
        content.setStyle("-fx-background-color:" + G50 + ";");

        HBox statsRow = new HBox(14);
        statsRow.getChildren().addAll(
            buildStatCard("Active Orders", "3",      "This month"),
            buildStatCard("Next Visit",    "Jun 15", "Order #1055"),
            buildStatCard("Subscriber",    "#4821",  "Member since 2024")
        );

        VBox recentCard = buildCard();
        Label recentTitle = new Label("RECENT ORDERS");
        recentTitle.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + "; -fx-padding:0 0 14 0;");

        VBox list = new VBox(0);
        Object[][] orders = {
            {1023, "Jun 12, 2026", 4, "Confirmed"},
            {1055, "Jun 15, 2026", 2, "Pending"},
            {1087, "Jun 28, 2026", 6, "Confirmed"},
        };
        for (Object[] o : orders) {
            HBox row = buildRecentOrderRow((int) o[0], (String) o[1], (int) o[2], (String) o[3]);
            row.setOnMouseClicked(e -> switchScreen("get"));
            list.getChildren().add(row);
        }

        recentCard.getChildren().addAll(recentTitle, list);
        VBox.setVgrow(recentCard, Priority.ALWAYS);
        content.getChildren().addAll(statsRow, recentCard);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent; -fx-background-color:transparent;");
        return scroll;
    }

    private VBox buildStatCard(String label, String value, String sub) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(20, 22, 20, 22));
        card.setStyle("-fx-background-color:white; -fx-background-radius:14;" +
                      " -fx-border-color:" + G200 + "; -fx-border-radius:14; -fx-border-width:1;");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbl = new Label(label.toUpperCase());
        lbl.setStyle("-fx-font-size:11; -fx-text-fill:" + G400 + ";");
        Label val = new Label(value);
        val.setStyle("-fx-font-size:32; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");
        Label s = new Label(sub);
        s.setStyle("-fx-font-size:12; -fx-text-fill:" + G400 + ";");

        card.getChildren().addAll(lbl, val, s);
        return card;
    }

    private HBox buildRecentOrderRow(int num, String date, int visitors, String status) {
        String dotColor = status.equals("Confirmed") ? G400 : "#d4a847";
        Circle dot = new Circle(4, Color.web(dotColor));

        VBox info = new VBox(2);
        Label name = new Label("Order #" + num);
        name.setStyle("-fx-font-size:14; -fx-font-weight:bold; -fx-text-fill:" + G800 + ";");
        Label details = new Label(date + " · " + visitors + " visitors");
        details.setStyle("-fx-font-size:12; -fx-text-fill:" + G400 + ";");
        info.getChildren().addAll(name, details);

        HBox left = new HBox(10);
        left.setAlignment(Pos.CENTER_LEFT);
        left.getChildren().addAll(dot, info);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 4, 10, 4));
        row.getChildren().addAll(left, spacer, buildStatusTag(status));
        row.setCursor(Cursor.HAND);
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:" + G50 + "; -fx-background-radius:8; -fx-padding:10 4 10 4;"));
        row.setOnMouseExited(e  -> row.setStyle("-fx-background-color:transparent; -fx-padding:10 4 10 4;"));
        return row;
    }

    // ─── Screen: Get Order ────────────────────────────────────────────────────

    private Node buildGetOrderScreen() {
        VBox resultPanel = buildResultPanel();
        VBox logBox      = buildLogBox();

        VBox left = new VBox(20);
        left.setPadding(new Insets(26, 14, 26, 28));
        VBox.setVgrow(left, Priority.ALWAYS);

        // Lookup card
        VBox lookupCard = buildCard();
        Label fieldLabel = new Label("Order Number");
        fieldLabel.setStyle(fieldLabelStyle());
        Label fieldHint = new Label("Try 1023, 1055, or 1087");
        fieldHint.setStyle("-fx-font-size:11; -fx-text-fill:" + G400 + ";");
        TextField orderInput = new TextField();
        orderInput.setStyle(largeInputStyle());
        orderInput.setPromptText("Order number…");
        Button getBtn = buildPrimaryButton("Get Order", "⊕");
        Label toast = buildToastLabel();

        getBtn.setOnAction(e -> doGetOrder(orderInput.getText(), getBtn, "Get Order", resultPanel, logBox, toast));
        orderInput.setOnAction(e -> doGetOrder(orderInput.getText(), getBtn, "Get Order", resultPanel, logBox, toast));

        lookupCard.getChildren().addAll(fieldLabel, fieldHint, orderInput, getBtn, toast);

        // Quick access card
        VBox quickCard = new VBox(8);
        quickCard.setPadding(new Insets(22, 24, 22, 24));
        quickCard.setStyle("-fx-background-color:" + G50 + "; -fx-background-radius:14;" +
                           " -fx-border-color:" + G100 + "; -fx-border-radius:14; -fx-border-width:1;");
        Label quickLabel = new Label("QUICK ACCESS");
        quickLabel.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");
        HBox quickBtns = new HBox(8);
        for (String n : new String[]{"#1023", "#1055", "#1087"}) {
            Button qb = buildSecondaryButton(n);
            qb.setOnAction(e -> {
                orderInput.setText(n.substring(1));
                doGetOrder(n.substring(1), getBtn, "Get Order", resultPanel, logBox, toast);
            });
            quickBtns.getChildren().add(qb);
        }
        quickCard.getChildren().addAll(quickLabel, quickBtns);

        left.getChildren().addAll(lookupCard, quickCard);

        HBox layout = new HBox();
        layout.setStyle("-fx-background-color:" + G50 + ";");
        HBox.setHgrow(left, Priority.ALWAYS);
        layout.getChildren().addAll(left, buildRightPanel(resultPanel, logBox));
        return layout;
    }

    // ─── Screen: Update Order ─────────────────────────────────────────────────

    private Node buildUpdateOrderScreen() {
        int[]    step      = {0};
        int[]    orderNum  = {-1};

        VBox resultPanel = buildResultPanel();
        VBox logBox      = buildLogBox();

        VBox left = new VBox(20);
        left.setPadding(new Insets(26, 14, 26, 28));
        VBox.setVgrow(left, Priority.ALWAYS);

        HBox[] stepHolder = {buildStepIndicator(0)};
        left.getChildren().add(stepHolder[0]);

        // Step 0 — Find Order
        VBox step0 = buildCard();
        Label s0label = new Label("Order Number");
        s0label.setStyle(fieldLabelStyle());
        TextField s0input = new TextField();
        s0input.setStyle(largeInputStyle());
        s0input.setPromptText("Enter order number…");
        Button findBtn = buildPrimaryButton("Find Order", "⊕");
        Label  s0toast = buildToastLabel();
        step0.getChildren().addAll(s0label, s0input, findBtn, s0toast);

        // Step 1 — Edit Details (hidden initially)
        VBox step1 = buildCard();
        step1.setVisible(false);
        step1.setManaged(false);

        Label s1title = new Label("Edit Order");
        s1title.setStyle("-fx-font-size:14; -fx-font-weight:bold; -fx-text-fill:" + G800 + ";");

        Label dateLabel = new Label("Visit Date");
        dateLabel.setStyle(fieldLabelStyle());
        DatePicker datePicker = buildStyledDatePicker();

        Label visitorsLabel = new Label("Number of Visitors");
        visitorsLabel.setStyle(fieldLabelStyle());
        Spinner<Integer> spinner = new Spinner<>(1, 50, 5);
        spinner.setMaxWidth(Double.MAX_VALUE);
        spinner.setEditable(true);

        VBox dateBox     = new VBox(6, dateLabel, datePicker);
        VBox visitorsBox = new VBox(6, visitorsLabel, spinner);
        HBox.setHgrow(dateBox, Priority.ALWAYS);
        HBox.setHgrow(visitorsBox, Priority.ALWAYS);
        HBox editRow = new HBox(14, dateBox, visitorsBox);

        Button backBtn  = buildSecondaryButton("← Back");
        Button applyBtn = buildPrimaryButton("Apply Changes", "✎");
        HBox.setHgrow(applyBtn, Priority.ALWAYS);
        HBox btnRow = new HBox(10, backBtn, applyBtn);
        Label s1toast = buildToastLabel();

        step1.getChildren().addAll(s1title, editRow, btnRow, s1toast);
        left.getChildren().addAll(step0, step1);

        // Wire — Find Order
        Runnable doFind = () -> {
            String raw = s0input.getText().trim();
            if (raw.isEmpty()) { showToast(s0toast, false, "Please enter an order number"); return; }
            try {
                int n = Integer.parseInt(raw);
                findBtn.setText("Searching…");
                findBtn.setDisable(true);
                Task<ServerResponse> t = new Task<>() {
                    @Override protected ServerResponse call() throws Exception {
                        Thread.sleep(500);
                        ClientRequest req = new ClientRequest(RequestType.GET_ORDER);
                        req.put("orderNumber", n);
                        return client.sendRequest(req);
                    }
                };
                t.setOnSucceeded(ev -> {
                    findBtn.setText("Find Order");
                    findBtn.setDisable(false);
                    ServerResponse res = t.getValue();
                    if (res.isSuccess()) {
                        OrderDTO dto = (OrderDTO) res.getData();
                        orderNum[0] = n;
                        populateResultPanel(resultPanel, dto);
                        addLog(logBox, true, "Loaded order #" + n);
                        s1title.setText("Edit Order #" + n);
                        try { datePicker.setValue(LocalDate.parse(dto.getOrderDate())); }
                        catch (Exception ex) { datePicker.setValue(LocalDate.now().plusDays(7)); }
                        spinner.getValueFactory().setValue(dto.getNumberOfVisitors());

                        step[0] = 1;
                        HBox newInd = buildStepIndicator(1);
                        left.getChildren().set(0, newInd);
                        step0.setVisible(false); step0.setManaged(false);
                        step1.setVisible(true);  step1.setManaged(true);
                    } else {
                        showToast(s0toast, false, res.getMessage());
                        addLog(logBox, false, "Error: " + res.getMessage());
                    }
                });
                t.setOnFailed(ev -> {
                    findBtn.setText("Find Order");
                    findBtn.setDisable(false);
                    showToast(s0toast, false, "Connection error");
                });
                new Thread(t).start();
            } catch (NumberFormatException ex) {
                showToast(s0toast, false, "Enter a valid order number");
            }
        };
        findBtn.setOnAction(e -> doFind.run());
        s0input.setOnAction(e -> doFind.run());

        // Wire — Back
        backBtn.setOnAction(e -> {
            step[0] = 0;
            left.getChildren().set(0, buildStepIndicator(0));
            step0.setVisible(true);  step0.setManaged(true);
            step1.setVisible(false); step1.setManaged(false);
            clearResultPanel(resultPanel);
        });

        // Wire — Apply
        applyBtn.setOnAction(e -> {
            if (datePicker.getValue() == null) { showToast(s1toast, false, "Please select a date"); return; }
            String newDate    = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);
            int    newVisit   = spinner.getValue();
            int    n          = orderNum[0];

            applyBtn.setText("Updating…");
            applyBtn.setDisable(true);

            Task<ServerResponse> t = new Task<>() {
                @Override protected ServerResponse call() throws Exception {
                    Thread.sleep(500);
                    ClientRequest req = new ClientRequest(RequestType.UPDATE_ORDER);
                    req.put("orderNumber", n);
                    req.put("newDate",     newDate);
                    req.put("newVisitors", newVisit);
                    return client.sendRequest(req);
                }
            };
            t.setOnSucceeded(ev -> {
                applyBtn.setText("Apply Changes");
                applyBtn.setDisable(false);
                ServerResponse res = t.getValue();
                if (res.isSuccess()) {
                    showToast(s1toast, true, "Order updated successfully");
                    addLog(logBox, true, "Updated order #" + n);

                    // Step 2 indicator briefly
                    left.getChildren().set(0, buildStepIndicator(2));

                    // Re-fetch to refresh result panel
                    Task<ServerResponse> refetch = new Task<>() {
                        @Override protected ServerResponse call() {
                            ClientRequest req = new ClientRequest(RequestType.GET_ORDER);
                            req.put("orderNumber", n);
                            return client.sendRequest(req);
                        }
                    };
                    refetch.setOnSucceeded(ev2 -> {
                        ServerResponse r2 = refetch.getValue();
                        if (r2.isSuccess()) populateResultPanel(resultPanel, (OrderDTO) r2.getData());
                    });
                    new Thread(refetch).start();
                } else {
                    showToast(s1toast, false, res.getMessage());
                    addLog(logBox, false, "Update failed: " + res.getMessage());
                }
            });
            t.setOnFailed(ev -> {
                applyBtn.setText("Apply Changes");
                applyBtn.setDisable(false);
                showToast(s1toast, false, "Connection error");
            });
            new Thread(t).start();
        });

        HBox layout = new HBox();
        layout.setStyle("-fx-background-color:" + G50 + ";");
        HBox.setHgrow(left, Priority.ALWAYS);
        layout.getChildren().addAll(left, buildRightPanel(resultPanel, logBox));
        return layout;
    }

    private HBox buildStepIndicator(int active) {
        String[] labels = {"Find Order", "Edit Details", "Confirm"};
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < 3; i++) {
            StackPane circle = new StackPane();
            circle.setPrefSize(28, 28);
            circle.setMinSize(28, 28);
            circle.setMaxSize(28, 28);

            String circBg, numColor;
            if      (i < active)  { circBg = G500; numColor = "white"; }
            else if (i == active) { circBg = G600; numColor = "white"; }
            else                  { circBg = G200; numColor = G500; }
            circle.setStyle("-fx-background-color:" + circBg + "; -fx-background-radius:50;");

            Label num = new Label(i < active ? "✓" : String.valueOf(i + 1));
            num.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + numColor + ";");
            circle.getChildren().add(num);

            Label lbl = new Label(labels[i]);
            lbl.setStyle("-fx-font-size:13; -fx-text-fill:" + (i == active ? G700 : G400) +
                         (i == active ? "; -fx-font-weight:500;" : ";"));

            HBox step = new HBox(8, circle, lbl);
            step.setAlignment(Pos.CENTER_LEFT);
            hbox.getChildren().add(step);

            if (i < 2) {
                Region line = new Region();
                line.setPrefWidth(36);
                line.setPrefHeight(1.5);
                line.setMinHeight(1.5);
                line.setMaxHeight(1.5);
                line.setStyle("-fx-background-color:" + (i < active ? G500 : G200) + ";");
                hbox.getChildren().add(line);
            }
        }
        return hbox;
    }

    // ─── Screen: New Booking ──────────────────────────────────────────────────

    private Node buildNewBookingScreen() {
        StackPane outer = new StackPane();
        outer.setStyle("-fx-background-color:" + G50 + ";");
        outer.setPadding(new Insets(26, 28, 26, 28));

        VBox card = buildCard();
        card.setMaxWidth(520);
        card.setSpacing(16);

        Label cardTitle = new Label("NEW PARK VISIT BOOKING");
        cardTitle.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");

        Label dateLabel = new Label("Visit Date");
        dateLabel.setStyle(fieldLabelStyle());
        DatePicker datePicker = buildStyledDatePicker();

        Label visitLabel = new Label("Number of Visitors");
        visitLabel.setStyle(fieldLabelStyle());
        TextField visitField = new TextField();
        visitField.setPromptText("How many people?");
        visitField.setStyle(inputStyle());

        Button confirmBtn = buildPrimaryButton("Confirm Booking", "+");
        confirmBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(confirmBtn, new Insets(20, 0, 0, 0));

        Label toast = buildToastLabel();

        card.getChildren().addAll(cardTitle, dateLabel, datePicker, visitLabel, visitField, confirmBtn, toast);

        confirmBtn.setOnAction(e -> {
            if (datePicker.getValue() == null || visitField.getText().trim().isEmpty()) {
                showToast(toast, false, "Please fill in all fields");
                return;
            }
            try {
                int v = Integer.parseInt(visitField.getText().trim());
                if (v < 1 || v > 50) { showToast(toast, false, "Visitors must be between 1 and 50"); return; }
            } catch (NumberFormatException ex) {
                showToast(toast, false, "Enter a valid number of visitors");
                return;
            }

            String dateStr = datePicker.getValue().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
            outer.getChildren().clear();

            StackPane checkCircle = new StackPane();
            checkCircle.setPrefSize(64, 64);
            checkCircle.setStyle("-fx-background-color:" + G100 + "; -fx-background-radius:50;");
            Label check = new Label("✓");
            check.setStyle("-fx-font-size:28; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");
            checkCircle.getChildren().add(check);

            Label sTitle = new Label("Booking Submitted!");
            sTitle.setStyle("-fx-font-size:20; -fx-font-weight:bold; -fx-text-fill:" + G800 + ";");

            Label sMsg = new Label("Your visit for " + dateStr + " has been received.\nA confirmation will be sent to your account.");
            sMsg.setStyle("-fx-font-size:14; -fx-text-fill:" + G500 + ";");
            sMsg.setWrapText(true);
            sMsg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            VBox success = new VBox(16, checkCircle, sTitle, sMsg);
            success.setAlignment(Pos.CENTER);
            success.setMaxWidth(400);

            outer.getChildren().add(success);
        });

        outer.getChildren().add(card);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        return outer;
    }

    // ─── Screen: History ──────────────────────────────────────────────────────

    private Node buildHistoryScreen() {
        VBox outer = new VBox(12);
        outer.setPadding(new Insets(26, 28, 26, 28));
        outer.setStyle("-fx-background-color:" + G50 + ";");

        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color:white; -fx-background-radius:14;" +
                           " -fx-border-color:" + G200 + "; -fx-border-radius:14; -fx-border-width:1;");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        HBox cardHeader = new HBox();
        cardHeader.setPadding(new Insets(18, 24, 18, 24));
        cardHeader.setStyle("-fx-border-color:" + G100 + "; -fx-border-width:0 0 1 0;");
        Label headerLabel = new Label("ALL ORDERS — SUBSCRIBER #4821");
        headerLabel.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");
        cardHeader.getChildren().add(headerLabel);

        VBox table = new VBox(0);
        table.getChildren().add(buildTableHeaderRow());

        Object[][] data = {
            {1087, "Jun 28, 2026", 6, "CONF-2891", "Confirmed", "May 2, 2026"},
            {1055, "Jun 15, 2026", 2, "CONF-1774", "Pending",   "Apr 28, 2026"},
            {1023, "Jun 12, 2026", 4, "CONF-0392", "Confirmed", "Apr 20, 2026"},
        };
        for (int i = 0; i < data.length; i++) {
            Object[] r = data[i];
            table.getChildren().add(buildTableDataRow(
                (int) r[0], (String) r[1], (int) r[2],
                (String) r[3], (String) r[4], (String) r[5], i == data.length - 1));
        }

        ScrollPane tableScroll = new ScrollPane(table);
        tableScroll.setFitToWidth(true);
        tableScroll.setStyle("-fx-background:transparent; -fx-background-color:transparent;");

        tableCard.getChildren().addAll(cardHeader, tableScroll);

        Label footer = new Label("Showing 3 orders · Connected to localhost:5555");
        footer.setStyle("-fx-font-size:12; -fx-text-fill:" + G400 + ";");

        outer.getChildren().addAll(tableCard, footer);

        ScrollPane scroll = new ScrollPane(outer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:transparent; -fx-background-color:transparent;");
        return scroll;
    }

    private HBox buildTableHeaderRow() {
        HBox row = new HBox(0);
        row.setPadding(new Insets(10, 24, 10, 24));
        row.setStyle("-fx-border-color:" + G200 + "; -fx-border-width:0 0 1 0;");
        String[][] cols = {{"Order #","80"},{"Visit Date","110"},{"Visitors","90"},
                           {"Confirmation","110"},{"Status","90"},{"Placed","110"}};
        for (String[] c : cols) {
            Label h = new Label(c[0].toUpperCase());
            h.setStyle("-fx-font-size:10; -fx-font-weight:bold; -fx-text-fill:" + G400 + ";");
            h.setPrefWidth(Double.parseDouble(c[1]));
            row.getChildren().add(h);
        }
        return row;
    }

    private HBox buildTableDataRow(int num, String date, int visitors,
                                   String conf, String status, String placed, boolean last) {
        String baseBorder = last ? "" : "-fx-border-color:" + G100 + "; -fx-border-width:0 0 1 0;";
        HBox row = new HBox(0);
        row.setPadding(new Insets(12, 24, 12, 24));
        row.setStyle(baseBorder);
        row.setCursor(Cursor.HAND);
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:" + G50 + "; -fx-padding:12 24 12 24; " + baseBorder));
        row.setOnMouseExited(e  -> row.setStyle("-fx-padding:12 24 12 24; " + baseBorder));

        Label numLbl = new Label("#" + num);
        numLbl.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + G700 + ";");
        numLbl.setPrefWidth(80);

        Label dateLbl = new Label(date);
        dateLbl.setStyle("-fx-font-size:13; -fx-text-fill:" + G700 + ";");
        dateLbl.setPrefWidth(110);

        Label visitLbl = new Label(String.valueOf(visitors));
        visitLbl.setStyle("-fx-font-size:13; -fx-text-fill:" + G700 + ";");
        visitLbl.setPrefWidth(90);

        Label confLbl = new Label(conf);
        confLbl.setStyle("-fx-font-size:12; -fx-font-family:monospace; -fx-text-fill:" + G700 + ";");
        confLbl.setPrefWidth(110);

        Label statusTag = buildStatusTag(status);
        statusTag.setPrefWidth(90);

        Label placedLbl = new Label(placed);
        placedLbl.setStyle("-fx-font-size:13; -fx-text-fill:" + G400 + ";");
        placedLbl.setPrefWidth(110);

        row.getChildren().addAll(numLbl, dateLbl, visitLbl, confLbl, statusTag, placedLbl);
        return row;
    }

    // ─── Shared components ────────────────────────────────────────────────────

    private VBox buildCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(22, 24, 22, 24));
        card.setStyle("-fx-background-color:white; -fx-background-radius:14;" +
                      " -fx-border-color:" + G200 + "; -fx-border-radius:14; -fx-border-width:1;");
        return card;
    }

    private VBox buildResultPanel() {
        VBox panel = new VBox(20);
        showEmptyResultPanel(panel);
        return panel;
    }

    private void showEmptyResultPanel(VBox panel) {
        panel.getChildren().clear();

        Label title = new Label("Order Details");
        title.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + G700 + ";");

        VBox empty = new VBox(10);
        empty.setAlignment(Pos.CENTER);
        empty.setPrefHeight(160);
        empty.setPadding(new Insets(20));
        empty.setStyle("-fx-border-color:" + G200 + "; -fx-border-width:1.5; -fx-border-radius:14;" +
                       " -fx-border-style:dashed; -fx-background-radius:14;");

        Label icon = new Label("🌿");
        icon.setStyle("-fx-font-size:28; -fx-opacity:0.3;");
        Label msg  = new Label("No order loaded");
        msg.setStyle("-fx-font-size:13; -fx-text-fill:" + G400 + ";");
        Label hint = new Label("Enter an order number above");
        hint.setStyle("-fx-font-size:11; -fx-text-fill:" + G300 + ";");
        empty.getChildren().addAll(icon, msg, hint);

        panel.getChildren().addAll(title, empty);
    }

    private void populateResultPanel(VBox panel, OrderDTO dto) {
        panel.getChildren().clear();

        Label title = new Label("Order Details");
        title.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + G700 + ";");

        VBox resultCard = new VBox(0);
        resultCard.setStyle("-fx-background-radius:14; -fx-border-color:" + G200 +
                            "; -fx-border-radius:14; -fx-border-width:1;");

        // Header
        HBox header = new HBox();
        header.setPadding(new Insets(16, 18, 16, 18));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:" + G600 + "; -fx-background-radius:14 14 0 0;");

        VBox orderInfo = new VBox(2);
        Label orderLbl = new Label("ORDER NUMBER");
        orderLbl.setStyle("-fx-font-size:10; -fx-font-weight:bold; -fx-text-fill:rgba(255,255,255,0.65);");
        Label orderNum = new Label("#" + dto.getOrderNumber());
        orderNum.setStyle("-fx-font-size:24; -fx-font-weight:bold; -fx-text-fill:white;");
        orderInfo.getChildren().addAll(orderLbl, orderNum);

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Label statusTag = new Label("Confirmed");
        statusTag.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:white;" +
                           " -fx-background-color:rgba(255,255,255,0.15);" +
                           " -fx-background-radius:100; -fx-padding:3 10 3 10;");

        header.getChildren().addAll(orderInfo, hSpacer, statusTag);

        // Body
        VBox body = new VBox(0);
        body.setStyle("-fx-background-color:white; -fx-background-radius:0 0 14 14; -fx-padding:0 18 0 18;");

        String[][] rows = {
            {"Visit Date",    dto.getOrderDate()},
            {"Visitors",      String.valueOf(dto.getNumberOfVisitors())},
            {"Status",        "Confirmed"},
            {"Confirmation",  String.valueOf(dto.getConfirmationCode())},
            {"Subscriber ID", String.valueOf(dto.getSubscriberId())},
            {"Order Placed",  dto.getDateOfPlacingOrder()},
        };
        for (int i = 0; i < rows.length; i++) {
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 0, 10, 0));
            if (i > 0) row.setStyle("-fx-border-color:" + G100 + "; -fx-border-width:1 0 0 0; -fx-padding:10 0 10 0;");

            Label key = new Label(rows[i][0]);
            key.setStyle("-fx-font-size:12; -fx-font-weight:500; -fx-text-fill:" + G500 + ";");
            key.setPrefWidth(110);

            String valColor = rows[i][0].equals("Visit Date") ? G600 : G800;
            Label val = new Label(rows[i][1]);
            val.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + valColor + ";");

            row.getChildren().addAll(key, val);
            body.getChildren().add(row);
        }

        resultCard.getChildren().addAll(header, body);
        panel.getChildren().addAll(title, resultCard);
    }

    private void clearResultPanel(VBox panel) { showEmptyResultPanel(panel); }

    private VBox buildLogBox() {
        VBox box = new VBox(4);
        Label empty = new Label("No activity yet…");
        empty.setStyle("-fx-font-size:11; -fx-font-style:italic; -fx-text-fill:" + G300 + ";");
        box.getChildren().add(empty);
        return box;
    }

    private VBox buildRightPanel(VBox resultPanel, VBox logBox) {
        VBox panel = new VBox(20);
        panel.setPrefWidth(310);
        panel.setMinWidth(310);
        panel.setMaxWidth(310);
        panel.setPadding(new Insets(26, 24, 26, 14));
        panel.setStyle("-fx-background-color:" + G50 + ";");

        Label logLabel = new Label("ACTIVITY LOG");
        logLabel.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G400 + ";");

        ScrollPane logScroll = new ScrollPane(logBox);
        logScroll.setFitToWidth(true);
        logScroll.setMinHeight(72);
        logScroll.setMaxHeight(120);
        logScroll.setStyle("-fx-background:" + G50 + "; -fx-background-color:" + G50 + ";" +
                           " -fx-border-color:" + G200 + "; -fx-border-radius:10; -fx-border-width:1;");

        VBox logSection = new VBox(6, logLabel, logScroll);
        VBox.setVgrow(resultPanel, Priority.ALWAYS);

        panel.getChildren().addAll(resultPanel, logSection);
        return panel;
    }

    private void addLog(VBox logBox, boolean ok, String msg) {
        // Remove empty-state label if present
        if (!logBox.getChildren().isEmpty()) {
            Node first = logBox.getChildren().get(0);
            if (first instanceof Label l && l.getStyle().contains("italic"))
                logBox.getChildren().clear();
        }
        String time  = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String color = ok ? G600 : RED;
        Label entry  = new Label(time + "  " + msg);
        entry.setStyle("-fx-font-size:11; -fx-font-family:monospace; -fx-text-fill:" + color + "; -fx-wrap-text:true;");
        entry.setWrapText(true);
        logBox.getChildren().add(0, entry);
    }

    private void doGetOrder(String text, Button btn, String btnLabel,
                            VBox resultPanel, VBox logBox, Label toast) {
        if (text.trim().isEmpty()) { showToast(toast, false, "Please enter an order number"); return; }
        try {
            int n = Integer.parseInt(text.trim());
            btn.setText("Searching…");
            btn.setDisable(true);

            Task<ServerResponse> task = new Task<>() {
                @Override protected ServerResponse call() throws Exception {
                    Thread.sleep(500);
                    ClientRequest req = new ClientRequest(RequestType.GET_ORDER);
                    req.put("orderNumber", n);
                    return client.sendRequest(req);
                }
            };
            task.setOnSucceeded(ev -> {
                btn.setText(btnLabel);
                btn.setDisable(false);
                ServerResponse res = task.getValue();
                if (res.isSuccess()) {
                    populateResultPanel(resultPanel, (OrderDTO) res.getData());
                    addLog(logBox, true, "Fetched order #" + n);
                } else {
                    showToast(toast, false, res.getMessage());
                    clearResultPanel(resultPanel);
                    addLog(logBox, false, "Error: " + res.getMessage());
                }
            });
            task.setOnFailed(ev -> {
                btn.setText(btnLabel);
                btn.setDisable(false);
                showToast(toast, false, "Connection error");
                addLog(logBox, false, "Connection error");
            });
            new Thread(task).start();
        } catch (NumberFormatException ex) {
            showToast(toast, false, "Enter a valid order number");
        }
    }

    private void showToast(Label toast, boolean ok, String msg) {
        toast.setText((ok ? "✓ " : "✕ ") + msg);
        String bg     = ok ? G100      : "#fef2f2";
        String border = ok ? G200      : "#fca5a5";
        String fg     = ok ? G600      : RED;
        toast.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + fg +
                       "; -fx-background-color:" + bg + "; -fx-background-radius:10;" +
                       " -fx-border-color:" + border + "; -fx-border-radius:10; -fx-border-width:1;" +
                       " -fx-padding:10 16 10 16;");
        toast.setVisible(true);
        toast.setManaged(true);
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> { toast.setVisible(false); toast.setManaged(false); });
        }).start();
    }

    // ─── Style helpers ────────────────────────────────────────────────────────

    private Button buildPrimaryButton(String text, String icon) {
        Button btn = new Button(icon + "  " + text);
        btn.setMaxWidth(Double.MAX_VALUE);
        String base  = "-fx-font-size:14; -fx-font-weight:bold; -fx-text-fill:white; -fx-background-radius:10; -fx-padding:12 18 12 18; -fx-cursor:hand;";
        btn.setStyle(base + "-fx-background-color:" + G500 + ";");
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-background-color:" + G600 + "; -fx-translate-y:-1;"));
        btn.setOnMouseExited(e  -> btn.setStyle(base + "-fx-background-color:" + G500 + ";"));
        VBox.setMargin(btn, new Insets(18, 0, 0, 0));
        return btn;
    }

    private Button buildSecondaryButton(String text) {
        Button btn = new Button(text);
        String base = "-fx-font-size:14; -fx-font-weight:bold; -fx-text-fill:" + G700 +
                      "; -fx-background-radius:10; -fx-border-radius:10; -fx-border-width:1.5;" +
                      " -fx-padding:12 18 12 18; -fx-cursor:hand;";
        btn.setStyle(base + "-fx-background-color:" + G100 + "; -fx-border-color:" + G200 + ";");
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-background-color:" + G200 + "; -fx-border-color:" + G200 + ";"));
        btn.setOnMouseExited(e  -> btn.setStyle(base + "-fx-background-color:" + G100 + "; -fx-border-color:" + G200 + ";"));
        return btn;
    }

    private Label buildStatusTag(String status) {
        Label tag = new Label(status);
        if (status.equals("Confirmed")) {
            tag.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G600 +
                         "; -fx-background-color:" + G100 + "; -fx-background-radius:100; -fx-padding:3 10 3 10;");
        } else {
            tag.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:#8a6a1a;" +
                         " -fx-background-color:#f5e8c0; -fx-background-radius:100; -fx-padding:3 10 3 10;");
        }
        return tag;
    }

    private Label buildToastLabel() {
        Label l = new Label();
        l.setVisible(false);
        l.setManaged(false);
        l.setWrapText(true);
        return l;
    }

    private String fieldLabelStyle() {
        return "-fx-font-size:12; -fx-font-weight:bold; -fx-text-fill:" + G600 + ";";
    }

    private String inputStyle() {
        return "-fx-font-size:14; -fx-background-color:" + G50 + "; -fx-border-color:" + G200 +
               "; -fx-border-width:1.5; -fx-border-radius:10; -fx-background-radius:10; -fx-padding:11 14 11 14;";
    }

    private String largeInputStyle() {
        return "-fx-font-size:20; -fx-font-weight:bold; -fx-background-color:" + G50 +
               "; -fx-border-color:" + G200 + "; -fx-border-width:1.5;" +
               " -fx-border-radius:12; -fx-background-radius:12; -fx-padding:13 16 13 16;";
    }

    private DatePicker buildStyledDatePicker() {
        DatePicker dp = new DatePicker();
        dp.setMaxWidth(Double.MAX_VALUE);
        dp.setPromptText("dd / mm / yyyy");
        dp.setStyle("-fx-font-size:14; -fx-background-color:" + G50 +
                    "; -fx-border-color:" + G200 + "; -fx-border-width:1.5;" +
                    " -fx-border-radius:10; -fx-background-radius:10;");
        dp.getEditor().setStyle("-fx-background-color:" + G50 +
                                "; -fx-font-size:14; -fx-padding:11 14 11 14;" +
                                " -fx-background-radius:10;");
        dp.skinProperty().addListener((obs, old, skin) -> {
            if (skin == null) return;
            javafx.scene.Node btn = dp.lookup(".arrow-button");
            if (btn != null)
                btn.setStyle("-fx-background-color:" + G100 + "; -fx-background-radius:0 9 9 0;");
            javafx.scene.Node arrow = dp.lookup(".arrow");
            if (arrow != null)
                arrow.setStyle("-fx-background-color:" + G500 + ";");
        });
        return dp;
    }
}
