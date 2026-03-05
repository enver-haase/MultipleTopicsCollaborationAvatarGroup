**MultipleTopicsCollaborationAvatarGroup** extends Vaadin's `CollaborationAvatarGroup` to show the union of participants across multiple Collaboration Engine topics in a single avatar group.

In a typical Collaboration Engine app, each topic has its own participant list. This component aggregates them: a user appears once in the avatar group as soon as they are registered as a participant in any of the added topics. When they leave all topics, they disappear.

## Features

- `addTopic(topicId)` / `removeTopic(topicId)` — subscribe to participant topics dynamically
- `setTopic(topicId)` — replaces all current topics (single-topic mode)
- `setOwnAvatarVisible(boolean)` — show or hide the current user's avatar
- `setImageHandler(handler)` — custom avatar images per user
- Fully compatible with `CollaborationEngine.openTopicConnection` participant maps

## Usage

```java
MultipleTopicsCollaborationAvatarGroup avatarGroup =
    new MultipleTopicsCollaborationAvatarGroup(userInfo);
avatarGroup.addTopic("chat-room-1-participants");
avatarGroup.addTopic("chat-room-2-participants");
avatarGroup.addTopic("chat-room-3-participants");
```

## Requirements

- Vaadin 25
- Collaboration Engine
