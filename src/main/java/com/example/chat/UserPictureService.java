package com.example.chat;

import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Serves simple face avatar images for users. Each user is deterministically
 * assigned one of 8 face SVGs based on their user ID hash.
 * Returns {@code null} for excluded users (e.g. "charlie").
 */
@Service
public class UserPictureService {

    private static final int FACE_COUNT = 8;
    private final byte[][] faceData = new byte[FACE_COUNT][];

    public UserPictureService() {
        for (int i = 0; i < FACE_COUNT; i++) {
            String resource = "/META-INF/resources/faces/face" + (i + 1) + ".svg";
            try (InputStream is = getClass().getResourceAsStream(resource)) {
                if (is != null) {
                    faceData[i] = is.readAllBytes();
                } else {
                    faceData[i] = new byte[0];
                }
            } catch (IOException e) {
                faceData[i] = new byte[0];
            }
        }
    }

    /**
     * Returns a DownloadHandler that serves the face image assigned to
     * the given user ID, or {@code null} if no image is available.
     */
    public DownloadHandler getDownloadHandler(String userId) {
        if ("charlie".equals(userId)) {
            return null;
        }
        int index = Math.floorMod(userId.hashCode(), FACE_COUNT);
        byte[] data = faceData[index];

        return DownloadHandler.fromInputStream(event ->
                new DownloadResponse(
                        new ByteArrayInputStream(data),
                        "avatar.svg",
                        "image/svg+xml",
                        data.length
                )
        );
    }

    /**
     * Returns the static URL for the face image assigned to the given user ID,
     * or {@code null} if no image is available. Useful for components that
     * accept a URL string (e.g. {@code AvatarGroupItem.setImage}).
     */
    public String getImageUrl(String userId) {
        if ("charlie".equals(userId)) {
            return null;
        }
        int index = Math.floorMod(userId.hashCode(), FACE_COUNT);
        return "/faces/face" + (index + 1) + ".svg";
    }
}
