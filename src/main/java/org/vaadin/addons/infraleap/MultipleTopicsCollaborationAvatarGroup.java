package org.vaadin.addons.infraleap;

import com.vaadin.collaborationengine.CollaborationAvatarGroup;
import com.vaadin.collaborationengine.PresenceManager;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.avatar.AvatarGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An AvatarGroup that shows the union of present users across multiple
 * Collaboration Engine topics. Uses the same {@link PresenceManager} mechanism
 * as {@link CollaborationAvatarGroup}, so presence is compatible between them.
 */
public class MultipleTopicsCollaborationAvatarGroup extends CollaborationAvatarGroup {

    private final UserInfo userInfo;

    // Per-topic set of present users (topic -> (userId -> UserInfo))
    private final Map<String, Map<String, UserInfo>> topicParticipants = new LinkedHashMap<>();

    // Per-topic PresenceManagers for cleanup
    private final Map<String, PresenceManager> topicManagers = new LinkedHashMap<>();

    private boolean ownAvatarVisible = true;

    public MultipleTopicsCollaborationAvatarGroup(UserInfo userInfo) {
        super(userInfo, null);
        // Disable the superclass's own avatar handling — we manage all avatars
        // ourselves in rebuildItems(), which respects our own ownAvatarVisible field.
        super.setOwnAvatarVisible(false);
        this.userInfo = userInfo;
    }

    public MultipleTopicsCollaborationAvatarGroup(UserInfo userInfo,
                                                   String... topicIds) {
        super(userInfo, null);
        super.setOwnAvatarVisible(false); // see comment in no-args constructor
        this.userInfo = userInfo;
        for (String topic : topicIds) {
            addTopic(topic);
        }
    }

    /**
     * Subscribes to a topic using {@link PresenceManager}. The current user is
     * automatically marked as present, and all present users on this topic
     * appear in the AvatarGroup. Adding a topic that is already subscribed
     * is a no-op.
     *
     * @param topic the topic id
     */
    public void addTopic(String topic) {
        if (topicManagers.containsKey(topic)) {
            return;
        }
        Map<String, UserInfo> participants = new LinkedHashMap<>();
        topicParticipants.put(topic, participants);

        PresenceManager manager = new PresenceManager(this, userInfo, topic);
        manager.markAsPresent(true);
        manager.setPresenceHandler(context -> {
            UserInfo user = context.getUser();
            participants.put(user.getId(), user);
            rebuildItems();
            return () -> {
                participants.remove(user.getId());
                rebuildItems();
            };
        });
        topicManagers.put(topic, manager);
    }

    /**
     * Unsubscribes from a topic and removes its participants from this AvatarGroup.
     *
     * @param topic the topic id to remove
     * @throws IllegalArgumentException if the topic was not previously added
     */
    public void removeTopic(String topic) {
        PresenceManager manager = topicManagers.remove(topic);
        if (manager == null) {
            throw new IllegalArgumentException(
                    "Topic not subscribed: " + topic);
        }
        manager.close();
        topicParticipants.remove(topic);
        rebuildItems();
    }

    /**
     * Replaces all current topics with the given ones.
     *
     * @param topics the new topic ids to subscribe to
     */
    public void setTopics(String... topics) {
        clearTopics();
        for (String topic : topics) {
            addTopic(topic);
        }
    }

    /**
     * Removes all topics and their participants from this AvatarGroup.
     */
    public void clearTopics() {
        if (topicManagers == null) {
            return;
        }
        for (String topic : new ArrayList<>(topicManagers.keySet())) {
            removeTopic(topic);
        }
    }

    @Override
    public void setTopic(String topicId) {
        clearTopics();
        if (topicId != null) {
            addTopic(topicId);
        }
    }

    @Override
    public void setOwnAvatarVisible(boolean ownAvatarVisible) {
        this.ownAvatarVisible = ownAvatarVisible;
        if (topicParticipants != null) {
            rebuildItems();
        }
    }

    @Override
    public boolean isOwnAvatarVisible() {
        return ownAvatarVisible;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setImageProvider(ImageProvider imageProvider) {
        throw new UnsupportedOperationException(
                "setImageProvider is deprecated; use setImageHandler instead");
    }

    @SuppressWarnings("deprecation")
    @Override
    public ImageProvider getImageProvider() {
        throw new UnsupportedOperationException(
                "getImageProvider is deprecated; use getImageHandler instead");
    }

    private void rebuildItems() {
        // Union of present users across all topics
        Map<String, UserInfo> union = new LinkedHashMap<>();
        topicParticipants.values().forEach(union::putAll);

        ImageHandler handler = getImageHandler();
        var items = union.entrySet().stream()
                .filter(entry -> ownAvatarVisible
                        || !entry.getKey().equals(userInfo.getId()))
                .map(entry -> {
                    UserInfo user = entry.getValue();
                    AvatarGroup.AvatarGroupItem item = new AvatarGroup.AvatarGroupItem();
                    item.setName(user.getName());
                    if (handler != null) {
                        var dh = handler.getDownloadHandler(user);
                        if (dh != null) {
                            item.setImageHandler(dh);
                        }
                    }
                    return item;
                })
                .toList();
        getContent().setItems(items);
    }
}
