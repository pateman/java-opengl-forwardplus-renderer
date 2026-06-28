package pl.pateman.forwardplus.util;

import java.io.InputStream;

public final class IOUtils {

    private IOUtils() {
        // Private constructor to prevent instantiation.
    }

    public static String readResourceAsString(String resourceName) {
        try (InputStream inputStream = IOUtils.class.getResourceAsStream("/" + resourceName)) {
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + resourceName, e);
        }
    }

}
