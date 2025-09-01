package org.example.syncwatchservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.dto.MovieDTO;
import org.example.syncwatchservice.dto.MovieListDTO;
import org.example.syncwatchservice.entity.MovieFile;
import org.example.syncwatchservice.service.MinioService;
import org.example.syncwatchservice.service.VideoService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@Slf4j
@RequiredArgsConstructor
public class WebController {

    private final VideoService videoService;
    private final MinioService minioService;

    @GetMapping("/api/movies")
    public ResponseEntity<List<MovieListDTO>> getMoviesList() {
        try {
            List<MovieListDTO> movies = videoService.getMoviesList();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .body(movies);
        } catch (Exception e) {
            log.error("Ошибка получения списка фильмов: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @GetMapping("/api/movies/{movieId}")
    public ResponseEntity<MovieDTO> getMovieDetails(@PathVariable Long movieId) {
        try {
            Optional<MovieDTO> movie = videoService.getMovieDetails(movieId);

            if (movie.isEmpty()) {
                log.warn("Фильм не найден: ID {}", movieId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=600")
                    .body(movie.get());

        } catch (Exception e) {
            log.error("Ошибка получения деталей фильма ID {}: {}", movieId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/movies/{movieId}/stream")
    public ResponseEntity<InputStreamResource> streamVideo(
            @PathVariable Long movieId,
            HttpServletRequest request) {
        try {
            if (!videoService.movieExists(movieId)) {
                log.warn("Попытка стриминга несуществующего фильма ID: {}", movieId);
                return ResponseEntity.notFound().build();
            }

            Optional<MovieFile> videoFileOpt = videoService.getVideoFile(movieId);
            if (videoFileOpt.isEmpty()) {
                log.warn("Видеофайл не найден для фильма ID: {}", movieId);
                return ResponseEntity.notFound().build();
            }

            MovieFile videoFile = videoFileOpt.get();
            long fileSize = videoFile.getFileSize();

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(videoFile, rangeHeader, fileSize);
            } else {
                return handleFullFileRequest(videoFile, fileSize);
            }

        } catch (Exception e) {
            log.error("Ошибка стриминга видео для фильма ID {}: {}", movieId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/api/movies/{movieId}/cover")
    public ResponseEntity<?> getMovieCover(@PathVariable Long movieId) {
        try {
            Optional<String> coverUrl = videoService.getCoverUrl(movieId);

            if (coverUrl.isEmpty()) {
                log.debug("Обложка не найдена для фильма ID: {}", movieId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, coverUrl.get())
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .build();

        } catch (Exception e) {
            log.error("Ошибка получения обложки фильма ID {}: {}", movieId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<InputStreamResource> handleRangeRequest(
            MovieFile videoFile, String rangeHeader, long fileSize) {
        try {
            Pattern pattern = Pattern.compile("bytes=(\\d+)-(\\d*)");
            Matcher matcher = pattern.matcher(rangeHeader);

            if (!matcher.find()) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
            }

            long start = Long.parseLong(matcher.group(1));
            long end = matcher.group(2).isEmpty() ? fileSize - 1 : Long.parseLong(matcher.group(2));

            if (start >= fileSize || end >= fileSize || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
            }

            long contentLength = end - start + 1;

            InputStream inputStream = minioService.getObjectStream(
                    videoFile.getMinioBucket(),
                    videoFile.getMinioObjectKey(),
                    start,
                    contentLength
            );

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_TYPE, videoFile.getMimeType())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(new InputStreamResource(inputStream));

        } catch (Exception e) {
            log.error("Ошибка обработки Range запроса: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<InputStreamResource> handleFullFileRequest(MovieFile videoFile, long fileSize) {
        try {
            InputStream inputStream = minioService.getObjectStream(
                    videoFile.getMinioBucket(),
                    videoFile.getMinioObjectKey()
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, videoFile.getMimeType())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(new InputStreamResource(inputStream));

        } catch (Exception e) {
            log.error("Ошибка обработки полного файла: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
