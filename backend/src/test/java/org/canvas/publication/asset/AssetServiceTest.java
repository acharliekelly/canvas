package org.canvas.publication.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioSystem;
import org.canvas.publication.asset.AudioGenerator.ApprovedDescriptionInput;
import org.canvas.publication.asset.AudioGenerator.GeneratedBinary;
import org.canvas.publication.PublicationRepository;
import org.canvas.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AssetServiceTest {
    private static final UUID REVISION_ID = UUID.fromString("b6f0ca24-5cb2-4f50-b749-c534eb74e14d");
    private static final URI PUBLIC_URI = URI.create("https://canvas.example/artworks/blue-study-123");
    private static final byte[] AUDIO = "RIFF-test-WAVE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] QR = "png-test".getBytes(StandardCharsets.US_ASCII);

    @Autowired AssetService assets;
    @Autowired GeneratedAssetRepository repository;
    @Autowired PublicationRepository publicationRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean AudioGenerator audioGenerator;
    @MockitoBean QrCodeGenerator qrCodeGenerator;
    @MockitoBean(name = "generatedObjectStorage") ObjectStorage storage;

    private final Map<String, ObjectStorage.ObjectMetadata> storedObjects = new HashMap<>();

    @BeforeEach
    void cleanDatabaseAndConfigureGenerators() {
        publicationRepository.deleteAll();
        repository.deleteAll();
        storedObjects.clear();
        when(audioGenerator.cacheNamespace()).thenReturn("placeholder-audio-v1");
        when(qrCodeGenerator.cacheNamespace()).thenReturn("zxing-qr-v1");
        when(audioGenerator.generate(any())).thenReturn(
                new GeneratedBinary(AUDIO, "audio/wav", "placeholder-audio-v1"));
        when(qrCodeGenerator.generate(any())).thenReturn(
                new GeneratedBinary(QR, "image/png", "zxing-qr-v1"));
        when(storage.putGenerated(anyString(), any(), anyLong(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            storedObjects.put(key, new ObjectStorage.ObjectMetadata(
                    invocation.getArgument(2), invocation.getArgument(3)));
            return new ObjectStorage.StoredObject(key);
        });
        when(storage.head(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedObjects.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            storedObjects.remove(invocation.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
    }

    @Test
    void usesSha256ContentKeysAndReusesAudioForTheSameApprovedRevisionInput() {
        ApprovedDescriptionInput input = new ApprovedDescriptionInput(
                REVISION_ID, "Objective", "A blue square.");

        GeneratedAsset first = assets.audioFor(input);
        GeneratedAsset repeated = assets.audioFor(input);
        GeneratedAsset lookedUp = assets.existingAudio(input);

        assertThat(first.getInputKey())
                .isEqualTo("f35ed7b35b7808a9018522f27830ac06e880ad5ef78890a316acf945263ba564");
        assertThat(first.getInputKey()).matches("[0-9a-f]{64}");
        assertThat(first.getObjectKey()).isEqualTo("generated/audio/" + first.getInputKey() + ".wav");
        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(lookedUp.getId()).isEqualTo(first.getId());
        assertThat(repository.count()).isOne();
        verify(audioGenerator, times(1)).generate(input);
        verify(storage, times(1)).putGenerated(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void audioKeyDoesNotCollideWhenNewlinesAreRepartitionedBetweenLabelAndText() {
        GeneratedAsset labelContainsNewline = assets.audioFor(new ApprovedDescriptionInput(
                REVISION_ID, "Objective\nA", "blue"));
        GeneratedAsset textContainsNewline = assets.audioFor(new ApprovedDescriptionInput(
                REVISION_ID, "Objective", "A\nblue"));

        assertThat(labelContainsNewline.getInputKey())
                .isEqualTo("01d5be39591454766e4479cf72cc25514ac2e964d21f312414ad1565482dec61");
        assertThat(textContainsNewline.getInputKey())
                .isEqualTo("e0ebc2fce6a72b91cf866893325c2aa9f98884210123c0975929337aa0460634");
        assertThat(textContainsNewline.getId()).isNotEqualTo(labelContainsNewline.getId());
        assertThat(repository.count()).isEqualTo(2);
        verify(audioGenerator, times(2)).generate(any());
    }

    @Test
    void changedApprovedRevisionInputCreatesReplacementAudioWithoutDeletingHistory() {
        GeneratedAsset original = assets.audioFor(new ApprovedDescriptionInput(
                REVISION_ID, "Objective", "A blue square."));
        GeneratedAsset replacement = assets.audioFor(new ApprovedDescriptionInput(
                UUID.fromString("f18fcb1b-acde-4977-89e8-a58708f34487"),
                "Objective", "A cobalt square."));

        assertThat(replacement.getId()).isNotEqualTo(original.getId());
        assertThat(replacement.getInputKey()).isNotEqualTo(original.getInputKey());
        assertThat(repository.count()).isEqualTo(2);
        verify(audioGenerator, times(2)).generate(any());
    }

    @Test
    void missingCachedObjectIsRegeneratedWithoutDuplicatingMetadata() {
        ApprovedDescriptionInput input = new ApprovedDescriptionInput(
                REVISION_ID, "Objective", "A blue square.");
        GeneratedAsset first = assets.audioFor(input);

        storage.delete(first.getObjectKey());
        GeneratedAsset repaired = assets.audioFor(input);

        assertThat(repaired.getId()).isEqualTo(first.getId());
        assertThat(repository.count()).isOne();
        verify(audioGenerator, times(2)).generate(input);
        verify(storage, times(2)).putGenerated(eq(first.getObjectKey()), any(), anyLong(), anyString());
    }

    @Test
    void reusesOneQrAssetForTheStablePublicUrlAcrossPublications() {
        GeneratedAsset first = assets.qrFor(PUBLIC_URI,
                UUID.fromString("8d5e32ca-86f2-45da-a987-f8c7d5c37060"));
        GeneratedAsset repeated = assets.qrFor(PUBLIC_URI,
                UUID.fromString("2c205ea2-7254-4a11-ae15-41c657d49b5d"));
        GeneratedAsset lookedUp = assets.existingQr(PUBLIC_URI);

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(lookedUp.getId()).isEqualTo(first.getId());
        assertThat(repository.count()).isOne();
        assertThat(first.getInputKey())
                .isEqualTo("ba0d56c362758851b9d9102242eddab32f3e924e0e45ae5be6ec2699a8c4cd23");
        verify(qrCodeGenerator, times(1)).generate(PUBLIC_URI);
    }

    @Test
    void deletesNewStorageObjectWhenTheSurroundingTransactionRollsBack() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            assets.audioFor(new ApprovedDescriptionInput(
                    REVISION_ID, "Objective", "A blue square."));
            throw new IllegalStateException("later publication persistence failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
        verify(storage).delete("generated/audio/"
                + "f35ed7b35b7808a9018522f27830ac06e880ad5ef78890a316acf945263ba564.wav");
    }

    @Test
    void checkedInPlaceholderIsAReadableWaveFileAndIdentifiesItsAdapter() throws Exception {
        GeneratedBinary generated = new PlaceholderAudioGenerator().generate(
                new ApprovedDescriptionInput(REVISION_ID, "Objective", "A blue square."));

        assertThat(generated.mediaType()).isEqualTo("audio/wav");
        assertThat(generated.generator()).isEqualTo("placeholder-audio-v1");
        assertThat(generated.bytes()).startsWith("RIFF".getBytes(StandardCharsets.US_ASCII));
        assertThat(new String(generated.bytes(), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WAVE");
        assertThat(AudioSystem.getAudioInputStream(new ByteArrayInputStream(generated.bytes()))
                .getFormat().getSampleRate()).isPositive();
    }

    @Test
    void qrGeneratorProducesAHighContrastPngWithAQuietZoneAndDecodableStableUrl() throws Exception {
        GeneratedBinary generated = new ZxingQrCodeGenerator().generate(PUBLIC_URI);
        var image = ImageIO.read(new ByteArrayInputStream(generated.bytes()));

        assertThat(generated.mediaType()).isEqualTo("image/png");
        assertThat(generated.generator()).isEqualTo("zxing-qr-v1");
        assertThat(image.getWidth()).isEqualTo(320);
        assertThat(image.getHeight()).isEqualTo(320);
        assertThat(image.getRGB(0, 0) & 0x00ffffff).isEqualTo(0x00ffffff);
        assertThat(image.getRGB(image.getWidth() - 1, image.getHeight() - 1) & 0x00ffffff)
                .isEqualTo(0x00ffffff);
        assertThat(QrTestDecoder.decode(generated.bytes())).isEqualTo(PUBLIC_URI.toASCIIString());
    }

}
