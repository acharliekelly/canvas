package org.canvas.storage;

import java.net.URI;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class StorageConfiguration {
    private static final DataSize MULTIPART_REQUEST_OVERHEAD = DataSize.ofMegabytes(1);

    @Bean
    MultipartConfigElement multipartConfigElement(
            @Value("${canvas.upload-max-size}") DataSize maximumUploadSize) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(maximumUploadSize);
        factory.setMaxRequestSize(DataSize.ofBytes(Math.addExact(
                maximumUploadSize.toBytes(), MULTIPART_REQUEST_OVERHEAD.toBytes())));
        return factory.createMultipartConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    S3Client s3Client(
            @Value("${canvas.storage.endpoint}") URI endpoint,
            @Value("${canvas.storage.access-key}") String accessKey,
            @Value("${canvas.storage.secret-key}") String secretKey,
            @Value("${canvas.storage.region}") String region) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean(name = "originalObjectStorage")
    @Primary
    @ConditionalOnMissingBean(name = "originalObjectStorage")
    ObjectStorage originalObjectStorage(S3Client client,
            @Value("${canvas.storage.originals-bucket}") String bucket) {
        return new S3ObjectStorage(client, bucket);
    }

    @Bean(name = "generatedObjectStorage")
    @ConditionalOnMissingBean(name = "generatedObjectStorage")
    ObjectStorage generatedObjectStorage(S3Client client,
            @Value("${canvas.storage.generated-bucket}") String bucket) {
        return new S3ObjectStorage(client, bucket);
    }
}
