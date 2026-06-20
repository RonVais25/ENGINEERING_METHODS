package client.app;

import client.service.NetworkService;
import client.view.BaseController;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns the content area inside the main shell and provides safe, animated
 * navigation between role-authorized screens.
 *
 * <p>Only screens registered by {@code MainShellController} can be opened. Since
 * that controller registers screens according to the active {@link Session}, the
 * navigator is also the second client-side guard against opening screens that do
 * not belong to the current user type. A call to {@link #go(String)} for an
 * unregistered id is ignored gracefully rather than crashing the UI.</p>
 */
public class Navigator {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final Duration TRANSITION_TIME = Duration.millis(180);

    /** Describes one navigable screen. */
    public record Screen(String id, String icon, String label, String fxml,
                         String title, String subtitle) {}

    private final StackPane contentArea;
    private final Consumer<Screen> onScreenChange;
    private final NetworkService network;
    private final Session session;

    /** Registered screens only. Hidden role screens are intentionally absent. */
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    private String currentId;
    private BaseController currentController;

    /**
     * Creates a navigator for a single shell instance.
     *
     * @param contentArea the stack pane into which FXML screens are loaded
     * @param network shared network facade
     * @param session active login session
     * @param onScreenChange callback used to update shell title/subtitle
     */
    public Navigator(StackPane contentArea, NetworkService network, Session session,
                     Consumer<Screen> onScreenChange) {
        this.contentArea    = contentArea;
        this.network        = network;
        this.session        = session;
        this.onScreenChange = onScreenChange;
    }

    /** Registers a screen that is allowed for the current user type. */
    public void register(Screen s) { screens.put(s.id(), s); }

    /** @return {@code true} only if this screen was registered for the user. */
    public boolean canGo(String id) { return screens.containsKey(id); }

    /** Binds a sidebar button to a registered screen id. */
    public void bindNavButton(String screenId, Button btn) {
        navButtons.put(screenId, btn);
        btn.setOnAction(e -> go(screenId));
    }

    /** @return the current screen, or {@code null} before the first navigation */
    public Screen currentScreen() { return screens.get(currentId); }

    /**
     * Loads and displays a registered screen using a short professional entrance
     * animation. Calls {@link BaseController#onHide()} on the outgoing screen and
     * {@link BaseController#onShow()} on the incoming one.
     *
     * @param id registered screen id
     */
    public void go(String id) {
        Screen screen = screens.get(id);
        if (screen == null) {
            return; // role-hidden or unknown screen: keep the current screen stable.
        }
        if (id.equals(currentId)) {
            return;
        }

        if (currentController != null) {
            currentController.onHide();
            currentController = null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(screen.fxml()));
            loader.setControllerFactory(this::instantiate);
            Parent node = loader.load();

            prepareForEntrance(node);
            contentArea.getChildren().setAll(node);
            playEntrance(node);

            Object ctrl = loader.getController();
            if (ctrl instanceof BaseController bc) {
                currentController = bc;
                bc.onShow();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load " + screen.fxml(), ex);
        }

        navButtons.forEach((key, btn) -> btn.pseudoClassStateChanged(ACTIVE, key.equals(id)));

        currentId = id;
        if (onScreenChange != null) onScreenChange.accept(screen);
    }

    /** Builds the controller requested by FXMLLoader. */
    private Object instantiate(Class<?> type) {
        try {
            try {
                return type.getDeclaredConstructor(NetworkService.class, Session.class, Navigator.class)
                           .newInstance(network, session, this);
            } catch (NoSuchMethodException ignored) {}
            try {
                return type.getDeclaredConstructor(NetworkService.class, Session.class)
                           .newInstance(network, session);
            } catch (NoSuchMethodException ignored) {}
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Cannot instantiate controller " + type.getName(), ex);
        }
    }

    /** Applies the initial visual state before the screen entrance animation. */
    private void prepareForEntrance(Node node) {
        node.setOpacity(0.0);
        node.setTranslateY(14.0);
    }

    /** Plays a fade + slight vertical movement for a polished screen transition. */
    private void playEntrance(Node node) {
        FadeTransition fade = new FadeTransition(TRANSITION_TIME, node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(TRANSITION_TIME, node);
        slide.setFromY(14.0);
        slide.setToY(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide).play();
    }

    /** Builds a styled sidebar navigation button with icon and label. */
    public static Button buildNavButton(String icon, String label) {
        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("nav-icon");
        Label textLbl = new Label(label);
        textLbl.getStyleClass().add("nav-label");

        HBox content = new HBox(10, iconLbl, textLbl);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("nav-button-content");

        Button btn = new Button();
        btn.setGraphic(content);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    /** @return the shell content stack pane. */
    public Node contentNode() { return contentArea; }
}
