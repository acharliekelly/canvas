package me.acharliekelly.canvas.publication.asset;

import java.net.URI;
import me.acharliekelly.canvas.publication.asset.AudioGenerator.GeneratedBinary;

/**
 * Generates QR-code binaries for stable public publication URIs.
 */
public interface QrCodeGenerator {
    default String cacheNamespace() {
        return "zxing-qr-v1";
    }

    /**
     * Encodes the exact supplied public URI and returns generator and media metadata used by
     * generated-asset cache repair validation.
     */
    GeneratedBinary generate(URI publicUri);
}
