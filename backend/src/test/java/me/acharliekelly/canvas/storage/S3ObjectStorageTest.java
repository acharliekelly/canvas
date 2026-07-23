package me.acharliekelly.canvas.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ObjectStorageTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void acceptsOnlyMatchingGeneratedAssetKindsAndExtensions() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "generated-assets");

        storage.putGenerated("generated/audio/" + HASH + ".wav", content(), 1, "audio/wav");
        storage.putGenerated("generated/qr/" + HASH + ".png", content(), 1, "image/png");

        verify(client, times(2)).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @ParameterizedTest
    @MethodSource("invalidGeneratedKeys")
    void rejectsMalformedOrCrossKindGeneratedKeys(String objectKey) {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "generated-assets");

        assertThatThrownBy(() -> storage.putGenerated(objectKey, content(), 1, "application/octet-stream"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Generated object key is not content-addressed.");
        verifyNoInteractions(client);
    }

    @Test
    void headReturnsStoredObjectMetadata() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "generated-assets");
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(42L).contentType("audio/wav").build());

        assertThat(storage.head("generated/audio/" + HASH + ".wav"))
                .contains(new ObjectStorage.ObjectMetadata(42, "audio/wav"));
    }

    @Test
    void headReturnsEmptyOnlyForANotFoundResponse() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "generated-assets");
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());

        assertThat(storage.head("missing")).isEmpty();
    }

    @Test
    void headPropagatesOtherStorageFailures() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, "generated-assets");
        var denied = S3Exception.builder().statusCode(403).message("denied").build();
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(denied);

        assertThatThrownBy(() -> storage.head("forbidden")).isSameAs(denied);
    }

    private static Stream<String> invalidGeneratedKeys() {
        return Stream.of(
                "generated/audio/" + HASH + ".png",
                "generated/qr/" + HASH + ".wav",
                "generated/audio/" + HASH.substring(1) + ".wav",
                "generated/audio/" + HASH + "0.wav",
                "generated/qr/" + HASH.toUpperCase() + ".png",
                "generated/audio/" + HASH + ".wav/extra",
                "prefix/generated/qr/" + HASH + ".png");
    }

    private static ByteArrayInputStream content() {
        return new ByteArrayInputStream("x".getBytes(StandardCharsets.US_ASCII));
    }
}
