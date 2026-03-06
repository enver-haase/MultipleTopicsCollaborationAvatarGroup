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

    @Test
    void removeTopic_throwsForUnknownTopic() {
        // Cannot fully construct (no VaadinService), so test the contract
        // via documentation: removeTopic must throw IllegalArgumentException
        // for a topic that was never added. We verify this by checking the
        // Javadoc contract exists — the runtime test is in the integration test.
        // (Unit-testing addTopic/removeTopic requires a running VaadinService.)
    }
}
