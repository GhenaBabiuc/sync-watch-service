package org.example.syncwatchservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Movie;
import org.example.syncwatchservice.service.MovieService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StreamController {

    private final MovieService movieService;

    private static final long CHUNK_SIZE = 1024 * 1024;

    @GetMapping("/stream/{movieId}")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable String movieId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletRequest request) throws IOException {

        Movie movie = movieService.getMovieById(movieId).orElse(null);
        if (movie == null) {
            log.warn("Movie not found for streaming: {}", movieId);
            return ResponseEntity.notFound().build();
        }

        Path videoPath = Paths.get(movie.getFilePath());
        if (!Files.exists(videoPath)) {
            log.error("Video file not found: {}", videoPath);
            return ResponseEntity.notFound().build();
        }

        long fileSize = Files.size(videoPath);
        String contentType = determineContentType(movie.getFormat());

        log.debug("Streaming {} (size: {} bytes) with range: {}",
                movie.getTitle(), fileSize, rangeHeader);

        if (rangeHeader == null) {
            return buildPartialResponse(videoPath, 0, Math.min(CHUNK_SIZE - 1, fileSize - 1),
                    fileSize, contentType);
        }

        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileSize);
            long end = range.getRangeEnd(fileSize);

            if (end - start + 1 > CHUNK_SIZE) {
                end = start + CHUNK_SIZE - 1;
            }

            return buildPartialResponse(videoPath, start, end, fileSize, contentType);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid range header: {}", rangeHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    private ResponseEntity<Resource> buildPartialResponse(Path videoPath, long start, long end,
                                                          long fileSize, String contentType) throws IOException {

        long contentLength = end - start + 1;

        log.debug("Serving range {}-{}/{} ({}KB)", start, end, fileSize, contentLength / 1024);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", contentType);
        headers.add("Accept-Ranges", "bytes");
        headers.add("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        headers.add("Content-Length", String.valueOf(contentLength));
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        InputStream inputStream = new BufferedInputStream(
                new FileInputStream(videoPath.toFile())
        );

        inputStream.skip(start);

        InputStream limitedStream = new LimitedInputStream(inputStream, contentLength);

        Resource resource = new InputStreamResource(limitedStream);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(resource);
    }

    private String determineContentType(String format) {
        return switch (format.toLowerCase()) {
            case "mp4" -> "video/mp4";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "mov" -> "video/quicktime";
            case "wmv" -> "video/x-ms-wmv";
            case "flv" -> "video/x-flv";
            case "webm" -> "video/webm";
            default -> "video/mp4";
        };
    }

    private static class LimitedInputStream extends InputStream {
        private final InputStream inputStream;
        private long remaining;

        public LimitedInputStream(InputStream inputStream, long limit) {
            this.inputStream = inputStream;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int result = inputStream.read();
            if (result != -1) {
                remaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int result = inputStream.read(b, off, toRead);
            if (result > 0) {
                remaining -= result;
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}