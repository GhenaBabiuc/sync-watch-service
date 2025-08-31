package org.example.syncwatchservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.service.VideoService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class WebController {


    private final VideoService videoService;

    @GetMapping("/api/videos")
    public ResponseEntity<List<String>> getVideoList() {
        try {
            List<String> videoFiles = videoService.getVideoList();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=30")
                    .body(videoFiles);
        } catch (Exception e) {
            log.error("Ошибка получения списка видео: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @GetMapping("/videos/{filename:.+}")
    public ResponseEntity<Resource> getVideo(@PathVariable String filename,
                                             @RequestHeader(value = "Range", required = false) String rangeHeader) {
        try {
            Path videoPath = videoService.getVideoPath(filename);
            if (videoPath == null) {
                log.warn("Запрос несуществующего файла: {}", filename);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(videoPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Файл не найден или не читается: {}", filename);
                return ResponseEntity.notFound().build();
            }

            String contentType = getContentType(filename);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Ошибка при обработке видео файла {}: {}", filename, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Неожиданная ошибка при получении видео {}: {}", filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getContentType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".ogg" -> "video/ogg";
            case ".avi" -> "video/x-msvideo";
            case ".mov" -> "video/quicktime";
            case ".mkv" -> "video/x-matroska";
            case ".m4v" -> "video/x-m4v";
            default -> "video/mp4";
        };
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return "";
        return filename.substring(lastDot);
    }
}
