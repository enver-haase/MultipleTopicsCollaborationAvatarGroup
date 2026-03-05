package com.example.chat;

import com.vaadin.collaborationengine.CollaborationAvatarGroup;
import com.vaadin.collaborationengine.CollaborationEngine;
import com.vaadin.collaborationengine.CollaborationMap;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.avatar.AvatarGroup;
import com.vaadin.flow.shared.Registration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * An AvatarGroup that shows the union of participants across multiple
 * Collaboration Engine topics. A user appears once they have been registered
 * as a participant in any of the added topics.
 */
public class MultipleTopicsCollaborationAvatarGroup extends CollaborationAvatarGroup {

    private final UserInfo userInfo;

    // Per-topic participant maps (topic -> (userId -> displayName))
    private final Map<String, Map<String, String>> topicParticipants = new LinkedHashMap<>();

    // Per-topic connection registrations for cleanup
    private final Map<String, Registration> topicRegistrations = new LinkedHashMap<>();

    // Optional function mapping userId to image URL
    private Function<String, String> imageUrlProvider;

    private boolean ownAvatarVisible = true;

    public MultipleTopicsCollaborationAvatarGroup(UserInfo userInfo) {
        super(userInfo, null);
        super.setOwnAvatarVisible(false);
        this.userInfo = userInfo;
    }

    public MultipleTopicsCollaborationAvatarGroup(UserInfo userInfo,
                                                   String... topicIds) {
        super(userInfo, null);
        super.setOwnAvatarVisible(false);
        this.userInfo = userInfo;
        for (String topic : topicIds) {
            addTopic(topic);
        }
    }

    /**
     * Subscribes to a participant topic. Any user registered in that topic's
     * {@code "participants"} CollaborationMap will appear in this AvatarGroup.
     *
     * @param topic the participant topic id (e.g. "PET2001-talk-participants")
     */
    public void addTopic(String topic) {
        Map<String, String> participants = new LinkedHashMap<>();
        topicParticipants.put(topic, participants);

        Registration registration = CollaborationEngine.getInstance().openTopicConnection(
                this, topic, userInfo, topicConnection -> {
                    CollaborationMap participantsMap =
                            topicConnection.getNamedMap("participants");

                    participantsMap.subscribe(event -> {
                        String value = event.getValue(String.class);
                        if (value != null) {
                            participants.put(event.getKey(), value);
                        } else {
                            participants.remove(event.getKey());
                        }
                        rebuildItems();
                    });

                    return null;
                });
        topicRegistrations.put(topic, registration);
    }

    /**
     * Unsubscribes from a participant topic and removes its participants
     * from this AvatarGroup.
     *
     * @param topic the participant topic id to remove
     */
    public void removeTopic(String topic) {
        Registration registration = topicRegistrations.remove(topic);
        if (registration != null) {
            registration.remove();
        }
        topicParticipants.remove(topic);
        rebuildItems();
    }

    @Override
    public void setOwnAvatarVisible(boolean ownAvatarVisible) {
        this.ownAvatarVisible = ownAvatarVisible;
        rebuildItems();
    }

    @Override
    public boolean isOwnAvatarVisible() {
        return ownAvatarVisible;
    }

    public void setImageUrlProvider(Function<String, String> imageUrlProvider) {
        this.imageUrlProvider = imageUrlProvider;
    }

    private void rebuildItems() {
        // Union of participants across all remaining topics
        Map<String, String> union = new LinkedHashMap<>();
        topicParticipants.values().forEach(union::putAll);

        var items = union.entrySet().stream()
                .filter(entry -> ownAvatarVisible
                        || !entry.getKey().equals(userInfo.getId()))
                .map(entry -> {
                    AvatarGroup.AvatarGroupItem item = new AvatarGroup.AvatarGroupItem();
                    item.setName(entry.getValue());
                    if (imageUrlProvider != null) {
                        String imageUrl = imageUrlProvider.apply(entry.getKey());
                        if (imageUrl != null) {
                            item.setImage(imageUrl);
                        }
                    }
                    return item;
                })
                .toList();
        getContent().setItems(items);
    }
}
