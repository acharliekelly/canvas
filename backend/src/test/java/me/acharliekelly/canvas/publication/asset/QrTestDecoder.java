package me.acharliekelly.canvas.publication.asset;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import java.util.Map;
import javax.imageio.ImageIO;

final class QrTestDecoder {
    private QrTestDecoder() {
    }

    static String decode(byte[] png) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalArgumentException("QR image is not a readable PNG.");
            }
            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            return new MultiFormatReader().decode(
                    bitmap, Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
        } catch (Exception error) {
            throw new IllegalArgumentException("QR image could not be decoded.", error);
        }
    }
}
