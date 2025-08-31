package org.example.syncwatchservice.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConfigurationProperties(prefix = "app.video")
@Data
@Slf4j
public class VideoService {

    private String directory = "videos";
    private long cacheTtl = 30000;

    private List<String> cachedVideoList = null;
    private LocalDateTime lastCacheUpdate = null;

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".mkv", ".webm", ".ogg", ".avi", ".mov", ".m4v"
    );

    public List<String> getVideoList() {
        if (cachedVideoList != null && lastCacheUpdate != null) {
            if (lastCacheUpdate.plusSeconds(cacheTtl / 1000).isAfter(LocalDateTime.now())) {
                return cachedVideoList;
            }
        }

        try {
            Path videosPath = Paths.get(directory);

            if (!Files.exists(videosPath)) {
                log.info("Папка videos не существует, создаем...");
                Files.createDirectories(videosPath);
                cachedVideoList = new ArrayList<>();
                lastCacheUpdate = LocalDateTime.now();
                return cachedVideoList;
            }

            List<String> videoFiles = Files.list(videosPath)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(this::isVideoFile)
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());

            cachedVideoList = videoFiles;
            lastCacheUpdate = LocalDateTime.now();

            log.info("Найдено {} видео файлов", videoFiles.size());
            if (!videoFiles.isEmpty()) {
                log.debug("Первые файлы: {}",
                        videoFiles.stream().limit(5).collect(Collectors.joining(", ")));
                if (videoFiles.size() > 5) {
                    log.debug("... и еще {} файлов", videoFiles.size() - 5);
                }
            }

            return videoFiles;

        } catch (IOException e) {
            log.error("Ошибка чтения папки videos: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean isVideoFile(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return VIDEO_EXTENSIONS.contains(extension);
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return "";
        return filename.substring(lastDot);
    }

    public boolean isValidVideoFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }

        Path videoPath = Paths.get(directory, filename);
        return Files.exists(videoPath) && Files.isRegularFile(videoPath) && isVideoFile(filename);
    }

    public Path getVideoPath(String filename) {
        if (!isValidVideoFile(filename)) {
            return null;
        }
        return Paths.get(directory, filename);
    }

    public void clearCache() {
        cachedVideoList = null;
        lastCacheUpdate = null;
        log.debug("Кеш видео файлов очищен");
    }
}
