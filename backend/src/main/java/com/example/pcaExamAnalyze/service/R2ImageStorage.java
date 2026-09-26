package com.example.pcaExamAnalyze.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Cloudflare R2 (S3-compatible) storage for question images. The bucket stays private:
 * students receive short-lived presigned links. Disabled when the R2_* settings are empty
 * (tests, local setups without R2), in which case images stay in the database.
 */
@Service
public class R2ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(R2ImageStorage.class);

    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public R2ImageStorage(@Value("${pca.storage.r2.account-id:}") String accountId,
                          @Value("${pca.storage.r2.access-key-id:}") String accessKeyId,
                          @Value("${pca.storage.r2.secret-access-key:}") String secretAccessKey,
                          @Value("${pca.storage.r2.bucket:}") String bucket) {
        this.bucket = bucket == null ? "" : bucket.trim();
        if (blank(accountId) || blank(accessKeyId) || blank(secretAccessKey) || this.bucket.isEmpty()) {
            this.client = null;
            this.presigner = null;
            log.info("R2 image storage is not configured; question images are stored in the database");
            return;
        }
        URI endpoint = URI.create("https://" + accountId.trim() + ".r2.cloudflarestorage.com");
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim()));
        S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .httpClient(UrlConnectionHttpClient.create())
                // R2 does not need the SDK's default extra CRC checksums.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .build();
        log.info("R2 image storage enabled (bucket {})", this.bucket);
    }

    public boolean enabled() {
        return client != null;
    }

    public void put(String key, byte[] data, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("private, max-age=2592000, immutable")
                        .build(),
                RequestBody.fromBytes(data));
    }

    /** A time-limited link the browser can load directly from R2. */
    public String presignedUrl(String key, Duration validity) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    /** Best effort: a leftover object only costs a little storage, so failures are logged, not thrown. */
    public void deleteQuietly(String key) {
        if (!enabled() || key == null || key.isBlank()) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ex) {
            log.warn("Could not delete R2 object {}: {}", key, ex.getMessage());
        }
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
        if (presigner != null) presigner.close();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
