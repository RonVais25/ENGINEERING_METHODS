package client.view;

import client.app.Session;
import client.net.EventBus;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.ServerEvent;
import common.dto.SubscriptionKey;
import common.dto.WaitlistEntryDTO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "My Waiting List" screen (visitor-facing): the logged-in visitor's WAITING
 * reservations, fetched via {@code LIST_WAITLIST}. Each row carries the queued
 * reservation plus the entry's grab-offer columns, so a row with a live offer is
 * shown prominently with a one-second countdown to {@code grab_expires_at} and an
 * Accept button ({@code ACCEPT_GRAB}); every row has a Leave/Decline button
 * ({@code LEAVE_WAITLIST}). The server is the source of truth — these buttons just
 * fire the ops; the price was already set server-side at join and is never
 * recomputed here.
 *
 * <p><strong>Realtime.</strong> A grab offer does not change the reservation row
 * (it stays WAITING) — its only push is the notification the shell already pops up.
 * To make the offer/Accept appear live, this screen attaches a <em>local-only</em>
 * {@link EventBus} callback to the actor's {@code ("notification", actorId)} key and
 * reloads on any notification. It must not send its own SUBSCRIBE/UNSUBSCRIBE for
 * that key: the shell owns the per-connection server subscription, and a per-screen
 * UNSUBSCRIBE on hide would tear down the shell's popup feed. It additionally
 * subscribes (via {@link BaseController#subscribe}) to each WAITING reservation id
 * so an accept/leave/cancel committed elsewhere also refreshes the list.
 */
public class WaitlistController extends BaseController {

    /** Parses the DB {@code DATETIME} strings the server sends ({@code yyyy-MM-dd HH:mm:ss}). */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private Button refreshBtn;
    @FXML private Label  resultLabel;
    @FXML private Label  cardHeaderLabel;
    @FXML private VBox   tableBox;

    private final Session session;

    /** Park id → display name, loaded once from {@code LIST_PARKS}. */
    private final Map<Integer, String> parkNames = new HashMap<>();

    /** Offered rows whose countdown the timeline updates each second. */
    private final List<TickTarget> tickTargets = new ArrayList<>();

    /** The logged-in visitor's national id; their waiting list is the one shown. */
    private long visitorId = -1;

    /** Drives the one-second countdown refresh; stopped on hide. */
    private Timeline countdown;

    /** Local-only piggyback on the shell's notification subscription (see class javadoc). */
    private EventBus.Subscription offerSub;

    public WaitlistController(NetworkService network, Session session) {
        super(network);
        this.session = session;
    }

    @FXML
    private void initialize() {
        visitorId = session.getActorId();
        subscribeToOffers();
        startCountdown();
        // Park names first (for nicer labels), then the waiting list. Live callbacks
        // call load() directly once names are cached; a race just shows "Park #id".
        loadParks();
    }

    @FXML
    private void onRefresh() {
        load();
    }

    /* ---------- data load -------------------------------------------------- */

    private void loadParks() {
        network.listParks().thenAccept(res -> {
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                for (Object o : raw) {
                    ParkDTO p = (ParkDTO) o;
                    parkNames.put(p.getId(), p.getName());
                }
            }
            load();
        });
    }

    /** Loads the visitor's WAITING entries and repaints. */
    private void load() {
        if (visitorId < 0) return;
        refreshBtn.setDisable(true);
        network.listWaitlist(visitorId).thenAccept(res -> {
            refreshBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            List<WaitlistEntryDTO> rows = new ArrayList<>();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) rows.add((WaitlistEntryDTO) o);
            }
            populate(rows);
            resubscribe(rows);
        });
    }

    private void populate(List<WaitlistEntryDTO> rows) {
        tickTargets.clear();

        int offers = 0;
        for (WaitlistEntryDTO e : rows) {
            if (isOffered(e)) offers++;
        }
        cardHeaderLabel.setText("YOUR WAITING LIST (" + rows.size()
                + " · " + offers + (offers == 1 ? " offer)" : " offers)"));
        tableBox.getChildren().setAll(headerRow());

        if (rows.isEmpty()) {
            Label none = new Label("You're not on any waiting list.");
            none.getStyleClass().addAll("history-cell", "muted");
            HBox row = new HBox(none);
            row.getStyleClass().add("history-row");
            tableBox.getChildren().add(row);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            tableBox.getChildren().add(dataRow(rows.get(i), i < rows.size() - 1));
        }
        tick(); // paint the initial countdown values without waiting a second
    }

    /* ---------- realtime --------------------------------------------------- */

    /**
     * Points this screen's per-reservation subscriptions at exactly the WAITING
     * reservation ids on screen, dropping the previous set first (also sends the
     * matching UNSUBSCRIBE). The notification piggyback is separate and untouched.
     */
    private void resubscribe(List<WaitlistEntryDTO> rows) {
        unsubscribeAll();
        for (WaitlistEntryDTO e : rows) {
            subscribe("reservation", e.getReservationId(), ev -> onReservationEvent(ev));
        }
    }

    private void onReservationEvent(ServerEvent ev) {
        load();
    }

    /**
     * Attaches a local-only callback to the actor's notification key so a freshly
     * arrived grab offer (whose only push is the notification) refreshes this list
     * live. Deliberately sends no wire SUBSCRIBE — the shell owns that.
     */
    private void subscribeToOffers() {
        if (visitorId < 0) return;
        SubscriptionKey key = new SubscriptionKey("notification", visitorId);
        offerSub = EventBus.getInstance().subscribe(key, ev -> load());
    }

    @Override
    protected void onHideHook() {
        if (countdown != null) countdown.stop();
        if (offerSub != null) {
            offerSub.unsubscribe(); // local detach only — no wire UNSUBSCRIBE for the shell's key
            offerSub = null;
        }
    }

    /* ---------- countdown -------------------------------------------------- */

    private void startCountdown() {
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    /** Recomputes each offered row's time-left, flipping to "expired" at zero. */
    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (TickTarget t : tickTargets) {
            long secs = ChronoUnit.SECONDS.between(now, t.expiry);
            if (secs > 0) {
                t.label.setText("⏳ " + formatRemaining(secs) + " left");
            } else {
                t.label.setText("Offer expired");
                t.acceptBtn.setDisable(true);
            }
        }
    }

    private static String formatRemaining(long secs) {
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    /* ---------- ops -------------------------------------------------------- */

    private void accept(int reservationId) {
        network.acceptGrab(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) load();
        });
    }

    private void leave(int reservationId) {
        network.leaveWaitlist(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) load();
        });
    }

    /* ---------- row building ----------------------------------------------- */

    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
                headerCell("PARK",    150),
                headerCell("DATE",    110),
                headerCell("PARTY",    70),
                headerCell("STATUS",  150),
                headerCell("ACTIONS",   0));
        return row;
    }

    private HBox dataRow(WaitlistEntryDTO e, boolean withDivider) {
        Label parkLbl  = cell(parkNames.getOrDefault(e.getParkId(), "Park #" + e.getParkId()), "num", 150);
        Label dateLbl  = cell(e.getVisitDate(), null, 110);
        Label partyLbl = cell(String.valueOf(e.getPartySize()), null, 70);

        HBox row;
        if (isOffered(e)) {
            // Prominent offer row: live countdown + Accept + Decline.
            Label countdownLbl = new Label("⏳ …");
            countdownLbl.getStyleClass().add("wl-countdown");
            countdownLbl.setPrefWidth(150);

            Button acceptBtn = new Button("Accept");
            acceptBtn.getStyleClass().add("btn-primary");
            acceptBtn.setOnAction(ev -> accept(e.getReservationId()));

            Button declineBtn = new Button("Decline");
            declineBtn.getStyleClass().add("btn-secondary");
            declineBtn.setOnAction(ev -> leave(e.getReservationId()));

            HBox actions = new HBox(8, acceptBtn, declineBtn);
            actions.setAlignment(Pos.CENTER_LEFT);

            row = new HBox(parkLbl, dateLbl, partyLbl, countdownLbl, actions);
            row.getStyleClass().addAll("history-row", "wl-offer");

            tickTargets.add(new TickTarget(LocalDateTime.parse(e.getGrabExpiresAt(), TS), countdownLbl, acceptBtn));
        } else {
            // Plain waiting row: a status pill + Leave.
            Label statusTag = new Label("WAITING");
            statusTag.getStyleClass().addAll("status-tag", "waiting");
            statusTag.setPrefWidth(150);

            Button leaveBtn = new Button("Leave");
            leaveBtn.getStyleClass().add("btn-secondary");
            leaveBtn.setOnAction(ev -> leave(e.getReservationId()));

            HBox actions = new HBox(8, leaveBtn);
            actions.setAlignment(Pos.CENTER_LEFT);

            row = new HBox(parkLbl, dateLbl, partyLbl, statusTag, actions);
            row.getStyleClass().add("history-row");
        }

        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    /** True when the entry currently holds a parseable grab offer (offered + has an expiry). */
    private boolean isOffered(WaitlistEntryDTO e) {
        return e.getGrabOfferedAt() != null && parseTs(e.getGrabExpiresAt()) != null;
    }

    private static LocalDateTime parseTs(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s, TS);
        } catch (Exception ex) {
            return null;
        }
    }

    private Label headerCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-header-cell");
        if (w > 0) l.setPrefWidth(w);
        return l;
    }

    private Label cell(String text, String modifier, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-cell");
        if (modifier != null) l.getStyleClass().add(modifier);
        l.setPrefWidth(w);
        return l;
    }

    /** One offered row's countdown state: where to write, and which Accept to disable at zero. */
    private static final class TickTarget {
        final LocalDateTime expiry;
        final Label label;
        final Button acceptBtn;

        TickTarget(LocalDateTime expiry, Label label, Button acceptBtn) {
            this.expiry = expiry;
            this.label = label;
            this.acceptBtn = acceptBtn;
        }
    }
}
