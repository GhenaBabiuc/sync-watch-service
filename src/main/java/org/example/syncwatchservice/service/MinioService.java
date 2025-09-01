package org.example.syncwatchservice.service;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String defaultBucket;

    public String generateStreamingUrl(String bucket, String objectKey, int expiryHours) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiryHours, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Ошибка генерации streaming URL для {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("Не удалось сгенерировать URL для потокового воспроизведения", e);
        }
    }

    public String generateCoverUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(24, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Ошибка генерации cover URL для {}/{}: {}", bucket, objectKey, e.getMessage());
            return null;
        }
    }

    public InputStream getObjectStream(String bucket, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Ошибка получения объекта {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("Не удалось получить объект из MinIO", e);
        }
    }

    public InputStream getObjectStream(String bucket, String objectKey, long offset, long length) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .offset(offset)
                            .length(length)
                            .build()
            );
        } catch (Exception e) {
            log.error("Ошибка получения части объекта {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("Не удалось получить часть объекта из MinIO", e);
        }
    }

    public ObjectInfo getObjectInfo(String bucket, String objectKey) {
        try {
            var stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );

            return ObjectInfo.builder()
                    .size(stat.size())
                    .contentType(stat.contentType())
                    .etag(stat.etag())
                    .lastModified(stat.lastModified().toLocalDateTime())
                    .build();

        } catch (Exception e) {
            log.error("Ошибка получения информации об объекте {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("Не удалось получить информацию об объекте", e);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ObjectInfo {
        private long size;
        private String contentType;
        private String etag;
        private java.time.LocalDateTime lastModified;
    }
}
