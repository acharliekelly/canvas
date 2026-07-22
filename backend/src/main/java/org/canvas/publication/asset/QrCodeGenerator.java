package org.canvas.publication.asset;

import java.net.URI;
import org.canvas.publication.asset.AudioGenerator.GeneratedBinary;

public interface QrCodeGenerator {
    default String cacheNamespace() {
        return "zxing-qr-v1";
    }

    GeneratedBinary generate(URI publicUri);
}
