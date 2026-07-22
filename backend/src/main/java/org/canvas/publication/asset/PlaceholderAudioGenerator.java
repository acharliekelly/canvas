package org.canvas.publication.asset;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

@Component
public class PlaceholderAudioGenerator implements AudioGenerator {
    private static final String RESOURCE = "/audio/placeholder.wav";

    @Override
    public GeneratedBinary generate(ApprovedDescriptionInput input) {
        try (InputStream content = PlaceholderAudioGenerator.class.getResourceAsStream(RESOURCE)) {
            if (content == null) {
                throw new IllegalStateException("Placeholder audio resource is missing.");
            }
            return new GeneratedBinary(content.readAllBytes(), "audio/wav", cacheNamespace());
        } catch (IOException error) {
            throw new IllegalStateException("Placeholder audio could not be read.", error);
        }
    }
}
