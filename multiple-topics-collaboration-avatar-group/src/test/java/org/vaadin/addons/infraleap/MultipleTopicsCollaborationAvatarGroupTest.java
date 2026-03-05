package org.vaadin.addons.infraleap;

import com.vaadin.collaborationengine.UserInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultipleTopicsCollaborationAvatarGroupTest {

    @Test
    void constructorWithUserInfoOnly_doesNotThrowNPE() {
        UserInfo userInfo = new UserInfo("test-user", "Test User");
        // The constructor will fail with IllegalStateException (no VaadinService)
        // deep in the superclass, but it must NOT fail with NullPointerException
        // from our own code (the initialization-order bug in clearTopics).
        Exception ex = assertThrows(Exception.class,
                () -> new MultipleTopicsCollaborationAvatarGroup(userInfo));
        assertInstanceOf(IllegalStateException.class, ex);
        assertTrue(ex.getMessage().contains("VaadinService"));
    }
}
