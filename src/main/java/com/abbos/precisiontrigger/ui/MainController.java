package com.abbos.precisiontrigger.ui;

import com.abbos.precisiontrigger.app.PrecisionTriggerFacade;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainController {
    private static final long BACKGROUND_REFRESH_INTERVAL_MILLIS = 2_000L;

    @FXML private TabPane tabs;
    @FXML private PasswordField tokenField;
    @FXML private TextField intervalValueField;
    @FXML private ComboBox<String> intervalUnitBox;
    @FXML private DatePicker targetDatePicker;
    @FXML private TextField targetTimeField;
    @FXML private Label appStateLabel;
    @FXML private Label serverTimeLabel;
    @FXML private Label zoneLabel;
    @FXML private Label latencyLabel;
    @FXML private Label confidenceLabel;
    @FXML private Label readinessLabel;
    @FXML private Label intervalLabel;
    @FXML private Label syncStatusLabel;
    @FXML private Label authStatusLabel;
    @FXML private Label targetLabel;
    @FXML private Label planVersionLabel;
    @FXML private Label executionStatusLabel;
    @FXML private TextArea historyArea;
    @FXML private TextArea auditArea;

    private PrecisionTriggerFacade facade;
    private final Timeline refreshTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> refresh()));
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ui-history-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean historyRefreshInFlight = new AtomicBoolean();
    private volatile long lastBackgroundRefreshStartedAt;

    public void setFacade(PrecisionTriggerFacade facade) {
        this.facade = facade;
        intervalUnitBox.getItems().setAll("SECONDS", "MINUTES");
        intervalUnitBox.getSelectionModel().selectFirst();
        intervalValueField.setText("60");
        targetDatePicker.setValue(LocalDate.now());
        targetTimeField.setText(LocalTime.now().plusMinutes(1).truncatedTo(ChronoUnit.MILLIS).toString());
        historyArea.setText("Loading timing history...");
        auditArea.setText("Loading execution audit...");
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
        refresh();
    }

    @FXML
    private void onApplyToken() {
        facade.applyToken(tokenField.getText());
        tokenField.clear();
        refresh();
    }

    @FXML
    private void onClearToken() {
        facade.clearToken();
        tokenField.clear();
        refresh();
    }

    @FXML
    private void onTestAuthentication() {
        facade.testAuthentication();
        refresh();
    }

    @FXML
    private void onSyncNow() {
        facade.syncNow();
        refresh();
    }

    @FXML
    private void onApplyInterval() {
        long value = Long.parseLong(intervalValueField.getText());
        java.time.Duration duration = switch (intervalUnitBox.getValue()) {
            case "MINUTES" -> java.time.Duration.ofMinutes(value);
            default -> java.time.Duration.ofSeconds(value);
        };
        facade.applySyncInterval(duration);
        refresh();
    }

    @FXML
    private void onArmTarget() {
        facade.setTarget(targetDatePicker.getValue(), LocalTime.parse(targetTimeField.getText()), ZoneId.systemDefault());
        facade.armTarget();
        refresh();
    }

    @FXML
    private void onCancelTarget() {
        facade.cancelArm();
        refresh();
    }

    private void refresh() {
        if (facade == null) {
            return;
        }
        UiSnapshot snapshot = facade.snapshot();
        ZoneId displayZone = ZoneId.systemDefault();
        appStateLabel.setText(snapshot.applicationState().name());
        serverTimeLabel.setText(format(snapshot.estimatedServerTime()));
        zoneLabel.setText(displayZone.getId());
        latencyLabel.setText("S1=" + format(snapshot.selectedS1()) + " S2=" + format(snapshot.selectedS2()) + " RTT=" + format(snapshot.currentRtt()) + " Jitter=" + format(snapshot.jitter()));
        confidenceLabel.setText(String.format("%.3f", snapshot.confidence()));
        readinessLabel.setText(snapshot.readiness().status().name() + " | " + snapshot.readiness().reason() + " | age=" + format(snapshot.readiness().clockAge()));
        intervalLabel.setText(format(snapshot.activeSyncInterval()));
        syncStatusLabel.setText(snapshot.syncStatus().status().name() + " | lastStarted=" + format(snapshot.syncStatus().lastStarted()) + " | lastCompleted=" + format(snapshot.syncStatus().lastCompleted()) + " | next=" + format(snapshot.syncStatus().nextPlanned()) + " | failure=" + String.valueOf(snapshot.syncStatus().lastFailure()));
        authStatusLabel.setText(snapshot.authStatus().state().name() + " | version=" + snapshot.authStatus().tokenVersion());
        targetLabel.setText(format(snapshot.targetTime()));
        planVersionLabel.setText(snapshot.executionPlan() == null ? "-" : Long.toString(snapshot.executionPlan().planVersion()));
        executionStatusLabel.setText(snapshot.actionResult() == null ? "-" : snapshot.actionResult().outcomeState().name() + " | " + String.valueOf(snapshot.actionResult().diagnostic()));
        refreshBackgroundViews();
    }

    private void refreshBackgroundViews() {
        long now = System.currentTimeMillis();
        if (historyRefreshInFlight.get() || now - lastBackgroundRefreshStartedAt < BACKGROUND_REFRESH_INTERVAL_MILLIS) {
            return;
        }
        if (!historyRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        lastBackgroundRefreshStartedAt = now;
        CompletableFuture
                .supplyAsync(() -> new HistoryPayload(facade.renderTimingHistory(12), facade.renderExecutionAudit(12)), backgroundExecutor)
                .whenComplete((payload, throwable) -> {
                    historyRefreshInFlight.set(false);
                    if (throwable != null) {
                        Platform.runLater(() -> {
                            historyArea.setText("Failed to load timing history: " + throwable.getMessage());
                            auditArea.setText("Failed to load execution audit: " + throwable.getMessage());
                        });
                        return;
                    }
                    Platform.runLater(() -> {
                        historyArea.setText(payload.history());
                        auditArea.setText(payload.audit());
                    });
                });
    }

    private static String format(TemporalAccessor value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Instant instant) {
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
    }

    private static String format(java.time.Duration duration) {
        return duration == null ? "-" : duration.toString();
    }

    private record HistoryPayload(String history, String audit) {
    }
}
