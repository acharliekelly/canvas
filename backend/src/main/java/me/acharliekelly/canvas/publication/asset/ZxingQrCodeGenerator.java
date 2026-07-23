package me.acharliekelly.canvas.publication.asset;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ZxingQrCodeGenerator implements QrCodeGenerator {
    private static final int SIZE = 320;

    @Override
    public AudioGenerator.GeneratedBinary generate(URI publicUri) {
        try {
            var matrix = new QRCodeWriter().encode(publicUri.toASCIIString(), BarcodeFormat.QR_CODE,
                    SIZE, SIZE, Map.of(
                            EncodeHintType.MARGIN, 4,
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return new AudioGenerator.GeneratedBinary(output.toByteArray(), "image/png", cacheNamespace());
        } catch (WriterException | IOException error) {
            throw new IllegalStateException("QR code could not be generated.", error);
        }
    }
}
