package com.example.chat;

import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationMap;
import com.vaadin.collaborationengine.CollaborationMessageList;
import com.vaadin.collaborationengine.MessageManager;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.avatar.AvatarGroup;
import com.vaadin.flow.component.avatar.AvatarGroup.AvatarGroupItem;
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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route("")
@PermitAll
public class MainView extends VerticalLayout {

    private static final String[] TOPICS = {
            "PET2001-talk", "VIC20-talk", "C64-talk", "AMIGA-talk"
    };

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

        OidcUser oidcUser = authContext.getAuthenticatedUser(OidcUser.class).orElseThrow();
        String userId = oidcUser.getPreferredUsername();
        String displayName = oidcUser.getFullName();
        this.userInfo = new UserInfo(userId, displayName);

        // Global AvatarGroup subscribing to all four participant topics
        globalAvatarGroup = new MultipleTopicsCollaborationAvatarGroup(userInfo);
        globalAvatarGroup.setImageHandler(
                ui -> pictureService.getDownloadHandler(ui.getId()));
        globalAvatarGroup.setWidth("250px");
        for (String t : TOPICS) {
            globalAvatarGroup.addTopic(t + "-participants");
        }

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        add(buildHeader(oidcUser.getPreferredUsername().toUpperCase()));
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
        // --- AvatarGroup for this topic's participants ---
        AvatarGroup avatarGroup = new AvatarGroup();

        // Local mirror of the CollaborationMap (no iteration API on CollaborationMap)
        Map<String, String> participantsMirror = new LinkedHashMap<>();

        // Stored reference to the participants map (populated once on connection)
        final CollaborationMap[] participantsMapRef = {null};

        String participantsTopic = topic + "-participants";
        CollaborationEngine.getInstance().openTopicConnection(
                this, participantsTopic, userInfo, topicConnection -> {
                    CollaborationMap participantsMap =
                            topicConnection.getNamedMap("participants");
                    participantsMapRef[0] = participantsMap;

                    participantsMap.subscribe(event -> {
                        String value = event.getValue(String.class);
                        if (value != null) {
                            participantsMirror.put(event.getKey(), value);
                        } else {
                            participantsMirror.remove(event.getKey());
                        }
                        rebuildAvatarGroup(avatarGroup, participantsMirror);
                    });

                    // Register cleanup for explicit logout
                    cleanupActions.add(() -> participantsMap.put(userInfo.getId(), null));

                    // Cleanup on deactivation (tab close / session end)
                    return () -> participantsMap.put(userInfo.getId(), null);
                });

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

        boolean[] participantRegistered = {false};

        // Broadcast every keystroke using stored map references (no new connections)
        inputField.addValueChangeListener(e -> {
            String text = e.getValue();
            // Register as participant on first keystroke
            if (!participantRegistered[0] && participantsMapRef[0] != null
                    && text != null && !text.isEmpty()) {
                participantsMapRef[0].put(userInfo.getId(), userInfo.getName());
                participantRegistered[0] = true;
            }
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

        return panel;
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

    private void rebuildAvatarGroup(AvatarGroup avatarGroup,
                                    Map<String, String> participants) {
        avatarGroup.setItems(
                participants.entrySet().stream()
                        .map(entry -> {
                            AvatarGroupItem item = new AvatarGroupItem();
                            item.setName(entry.getValue());
                            var dh = pictureService.getDownloadHandler(entry.getKey());
                            if (dh != null) {
                                item.setImageHandler(dh);
                            }
                            return item;
                        })
                        .toList());
    }
}
