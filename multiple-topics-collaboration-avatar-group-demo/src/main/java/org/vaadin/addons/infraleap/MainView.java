package org.vaadin.addons.infraleap;

import com.vaadin.collaborationengine.CollaborationAvatarGroup;
import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationMap;
import com.vaadin.collaborationengine.CollaborationMessageList;
import com.vaadin.collaborationengine.MessageManager;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route("")
@PermitAll
public class MainView extends VerticalLayout {

    private static final String[] TOPICS = {
            "PET2001-talk", "VIC20-talk", "C64-talk", "AMIGA-talk"
    };

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "alice", "Alice Krzykalla",
            "bob", "Bob Krzykalla",
            "charlie", "Charlie Krzykalla"
    );

    private final AuthenticationContext authContext;
    private final UserInfo userInfo;
    private final MultipleTopicsCollaborationAvatarGroup globalAvatarGroup;

    // Collected cleanup actions to run before logout
    private final List<Runnable> cleanupActions = new ArrayList<>();

    private final UserPictureService pictureService;

    public MainView(AuthenticationContext authContext,
                    UserPictureService pictureService) {
        this.authContext = authContext;
        this.pictureService = pictureService;

        UserDetails userDetails = authContext.getAuthenticatedUser(UserDetails.class).orElseThrow();
        String userId = userDetails.getUsername();
        String displayName = DISPLAY_NAMES.getOrDefault(userId, userId);
        this.userInfo = new UserInfo(userId, displayName);

        // Global AvatarGroup subscribing to all four participant topics
        globalAvatarGroup = new MultipleTopicsCollaborationAvatarGroup(userInfo);
        globalAvatarGroup.setImageHandler(
                ui -> pictureService.getDownloadHandler(ui.getId()));
        globalAvatarGroup.setWidth("250px");
        globalAvatarGroup.setTopics(
                Arrays.stream(TOPICS).map(t -> t + "-participants").toArray(String[]::new));

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        add(buildHeader(userId.toUpperCase()));
        add(buildChatGrid());
    }

    private HorizontalLayout buildHeader(String displayName) {
        H2 logo = new H2("RetroChat!");
        logo.getStyle()
                .set("margin", "0")
                .set("font-family", "monospace");

        HorizontalLayout left = new HorizontalLayout(logo, globalAvatarGroup);
        left.setAlignItems(Alignment.CENTER);
        left.setSpacing(true);

        Span userLabel = new Span(displayName);
        userLabel.getStyle().set("font-weight", "bold");

        Button logoutButton = new Button("Logout", e -> {
            cleanupActions.forEach(Runnable::run);
            authContext.logout();
        });

        HorizontalLayout right = new HorizontalLayout(userLabel, logoutButton);
        right.setAlignItems(Alignment.CENTER);
        right.setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(left, right);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.getStyle().set("padding-bottom", "var(--lumo-space-m)");

        return header;
    }

    private VerticalLayout buildChatGrid() {
        HorizontalLayout topRow = new HorizontalLayout(
                buildChatPanel(TOPICS[0]),
                buildChatPanel(TOPICS[1]));
        topRow.setWidthFull();
        topRow.setSpacing(true);

        HorizontalLayout bottomRow = new HorizontalLayout(
                buildChatPanel(TOPICS[2]),
                buildChatPanel(TOPICS[3]));
        bottomRow.setWidthFull();
        bottomRow.setSpacing(true);

        VerticalLayout grid = new VerticalLayout(topRow, bottomRow);
        grid.setSizeFull();
        grid.setSpacing(true);
        grid.setPadding(false);

        return grid;
    }

    private VerticalLayout buildChatPanel(String topic) {
        String participantsTopic = topic + "-participants";

        // --- AvatarGroup for this topic's participants ---
        CollaborationAvatarGroup avatarGroup =
                new CollaborationAvatarGroup(userInfo, participantsTopic);
        avatarGroup.setImageHandler(
                ui -> pictureService.getDownloadHandler(ui.getId()));

        // --- Title ---
        Span title = new Span(topic);
        title.getStyle()
                .set("font-weight", "bold")
                .set("font-family", "monospace");

        HorizontalLayout titleBar = new HorizontalLayout(title, avatarGroup);
        titleBar.setWidthFull();
        titleBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        titleBar.setAlignItems(Alignment.CENTER);

        // --- Message list ---
        CollaborationMessageList messageList =
                new CollaborationMessageList(userInfo, topic);
        messageList.setSizeFull();

        // --- Message input: TextField + Button + MessageManager ---
        // Keystrokes broadcast live via CollaborationMap; Enter/Send commits the message
        MessageManager messageManager =
                new MessageManager(this, userInfo, topic);

        // Live typing area: shows what each user is currently typing
        String typingTopic = topic + "-typing";
        VerticalLayout typingArea = new VerticalLayout();
        typingArea.setPadding(false);
        typingArea.setSpacing(false);
        typingArea.getStyle()
                .set("font-family", "monospace")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        // Mirror of who is typing what
        Map<String, String> typingMirror = new LinkedHashMap<>();
        Map<String, Span> typingLabels = new LinkedHashMap<>();

        // Stored reference to the typing map (populated once on connection)
        final CollaborationMap[] typingMapRef = {null};

        CollaborationEngine.getInstance().openTopicConnection(
                this, typingTopic, userInfo, topicConnection -> {
                    CollaborationMap typingMap =
                            topicConnection.getNamedMap("typing");
                    typingMapRef[0] = typingMap;

                    typingMap.subscribe(event -> {
                        String typingUserId = event.getKey();
                        String typingText = event.getValue(String.class);

                        if (typingText != null && !typingText.isEmpty()) {
                            typingMirror.put(typingUserId, typingText);
                        } else {
                            typingMirror.remove(typingUserId);
                        }
                        rebuildTypingArea(typingArea, typingMirror, typingLabels);
                    });

                    // Register cleanup for explicit logout
                    cleanupActions.add(() -> typingMap.put(userInfo.getId(), null));

                    // Cleanup on deactivation: clear this user's typing entry
                    return () -> typingMap.put(userInfo.getId(), null);
                });

        TextField inputField = new TextField();
        inputField.setPlaceholder("Type...");
        inputField.setWidthFull();
        inputField.setValueChangeMode(ValueChangeMode.EAGER);

        // Broadcast every keystroke using stored map reference
        inputField.addValueChangeListener(e -> {
            String text = e.getValue();
            if (typingMapRef[0] != null) {
                typingMapRef[0].put(userInfo.getId(),
                        text == null ? "" : text);
            }
        });

        Button sendButton = new Button("Send", e -> {
            String text = inputField.getValue();
            if (text != null && !text.isBlank()) {
                messageManager.submit(text);
                inputField.clear();
            }
        });

        inputField.addKeyPressListener(
                com.vaadin.flow.component.Key.ENTER,
                e -> sendButton.click());

        HorizontalLayout inputBar = new HorizontalLayout(inputField, sendButton);
        inputBar.setWidthFull();
        inputBar.expand(inputField);
        inputBar.setSpacing(true);

        // --- Panel assembly ---
        VerticalLayout panel = new VerticalLayout(titleBar, messageList, typingArea, inputBar);
        panel.setSizeFull();
        panel.setSpacing(false);
        panel.setPadding(true);
        panel.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");
        panel.expand(messageList);

        // --- Enable/Disable toggle (outside the panel so opacity doesn't affect it) ---
        Button toggleButton = new Button("Disable");
        toggleButton.addClickListener(e -> {
            boolean enabling = toggleButton.getText().equals("Enable");
            if (enabling) {
                avatarGroup.setTopic(participantsTopic);
                globalAvatarGroup.addTopic(participantsTopic);
                inputField.setEnabled(true);
                sendButton.setEnabled(true);
                panel.getStyle().remove("opacity");
                toggleButton.setText("Disable");
            } else {
                avatarGroup.setTopic(null);
                globalAvatarGroup.removeTopic(participantsTopic);
                if (typingMapRef[0] != null) {
                    typingMapRef[0].put(userInfo.getId(), null);
                }
                inputField.clear();
                inputField.setEnabled(false);
                sendButton.setEnabled(false);
                panel.getStyle().set("opacity", "0.4");
                toggleButton.setText("Enable");
            }
        });

        VerticalLayout wrapper = new VerticalLayout(panel, toggleButton);
        wrapper.setSizeFull();
        wrapper.setSpacing(false);
        wrapper.setPadding(false);
        wrapper.expand(panel);
        wrapper.setHorizontalComponentAlignment(Alignment.END, toggleButton);

        return wrapper;
    }

    private void rebuildTypingArea(VerticalLayout typingArea,
                                   Map<String, String> typingMirror,
                                   Map<String, Span> typingLabels) {
        typingArea.removeAll();
        typingLabels.clear();
        typingMirror.forEach((userId, text) -> {
            Span label = new Span(text);
            typingLabels.put(userId, label);
            typingArea.add(label);
        });
    }
}
