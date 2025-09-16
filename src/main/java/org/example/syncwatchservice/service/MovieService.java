package org.example.syncwatchservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Movie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MovieService {

    @Value("${app.movies.directory:./movies}")
    private String moviesDirectory;

    private final String[] supportedFormats = {".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm"};

    public List<Movie> getAllMovies() {
        try {
            Path moviesPath = Paths.get(moviesDirectory);
            if (!Files.exists(moviesPath)) {
                log.info("Creating movies directory: {}", moviesPath.toAbsolutePath());
                Files.createDirectories(moviesPath);
                return List.of();
            }

            List<Movie> movies = Files.list(moviesPath)
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFormat)
                    .map(this::createMovieFromFile)
                    .collect(Collectors.toList());

            log.info("Found {} movies in directory {}", movies.size(), moviesPath.toAbsolutePath());
            return movies;
        } catch (IOException e) {
            log.error("Error reading movies directory: {}", moviesDirectory, e);
            throw new RuntimeException("Error reading movies directory", e);
        }
    }

    public Optional<Movie> getMovieById(String movieId) {
        return getAllMovies().stream()
                .filter(movie -> movie.getId().equals(movieId))
                .findFirst();
    }

    private boolean isSupportedFormat(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        for (String format : supportedFormats) {
            if (fileName.endsWith(format)) {
                return true;
            }
        }
        return false;
    }

    private Movie createMovieFromFile(Path file) {
        String fileName = file.getFileName().toString();
        String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        String extension = fileName.substring(fileName.lastIndexOf('.'));

        Movie movie = new Movie();
        movie.setId(generateMovieId(fileName));
        movie.setTitle(formatTitle(fileNameWithoutExt));
        movie.setFilePath(file.toString());
        movie.setFormat(extension.substring(1).toLowerCase());
        movie.setDuration(estimateDuration());

        log.debug("Created movie: {} with id: {}", movie.getTitle(), movie.getId());
        return movie;
    }

    private String generateMovieId(String fileName) {
        return UUID.nameUUIDFromBytes(fileName.getBytes()).toString();
    }

    private String formatTitle(String fileName) {
        return fileName.replaceAll("[._-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private long estimateDuration() {
        return 90 * 60;
    }

    public String getMovieStreamUrl(String movieId) {
        return "/api/stream/" + movieId;
    }
}