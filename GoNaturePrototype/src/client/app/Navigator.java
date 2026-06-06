package client.app;

import client.service.NetworkService;
import client.view.BaseController;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns the content area inside MainShell and the map of registered screens.
 * Each screen is an FXML path plus a title/subtitle pair shown in the topbar.
 * Calling {@link #go(String)} loads the FXML, swaps it into the content area,
 * and toggles the :active pseudo-class on the matching sidebar button.
 */
public class Navigator {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    /** Describes one navigable screen. */
    public record Screen(String id, String icon, String label, String fxml,
                         String title, String subtitle) {}

    private final StackPane contentArea;
    private final Consumer<Screen> onScreenChange;
    private final NetworkService network;
    private final Session session;

    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private String currentId;
    // Tracked so onHide() can fire on the outgoing controller before we
    // load the next FXML. Not volatile — go() is only called from the FX
    // thread, so single-threaded ownership is sufficient.
    private BaseController currentController;

    public Navigator(StackPane contentArea, NetworkService network, Session session,
                     Consumer<Screen> onScreenChange) {
        this.contentArea    = contentArea;
        this.network        = network;
        this.session        = session;
        this.onScreenChange = onScreenChange;
    }

    public void register(Screen s) { screens.put(s.id(), s); }

    public void bindNavButton(String screenId, Button btn) {
        navButtons.put(screenId, btn);
        btn.setOnAction(e -> go(screenId));
    }

    public Screen currentScreen() { return screens.get(currentId); }

    public void go(String id) {
        Screen screen = screens.get(id);
        if (screen == null) throw new IllegalArgumentException("Unknown screen: " + id);

        // 1. Hide the outgoing screen first so its push-channel subscriptions
        //    are detached BEFORE the new content shows. If we swapped first,
        //    an event for the abandoned entity could still arrive in the
        //    brief window before onHide() runs.
        if (currentController != null) {
            currentController.onHide();
            currentController = null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(screen.fxml()));
            loader.setControllerFactory(type -> instantiate(type));
            Parent node = loader.load();
            contentArea.getChildren().setAll(node);

            // 2. Show the new screen after FXMLLoader has injected @FXML
            //    fields and run initialize(). Only push-aware controllers
            //    (BaseController subclasses) get the lifecycle hooks; the
            //    others stay oblivious and require no change.
            Object ctrl = loader.getController();
            if (ctrl instanceof BaseController bc) {
                currentController = bc;
                bc.onShow();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load " + screen.fxml(), ex);
        }

        navButtons.forEach((key, btn) ->
            btn.pseudoClassStateChanged(ACTIVE, key.equals(id)));

        currentId = id;
        if (onScreenChange != null) onScreenChange.accept(screen);
    }

    /**
     * Constructs a controller class via reflection, picking a constructor that
     * takes (NetworkService, Session, Navigator) if available, otherwise the
     * (NetworkService, Session) or no-arg form. This keeps screen controllers
     * declarative — they just declare the constructor they want.
     */
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

    /** Build a sidebar nav button styled by client.css; icon + label inside. */
    public static Button buildNavButton(String icon, String label) {
        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("nav-icon");
        Label textLbl = new Label(label);
        textLbl.getStyleClass().add("nav-label");

        HBox content = new HBox(10, iconLbl, textLbl);
        content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.getStyleClass().add("nav-button");
        return btn;
    }

    public Node contentNode() { return contentArea; }
}
