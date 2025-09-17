package org.example.syncwatchservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Movie;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final StorageService storageService;

    public List<Movie> getAllMovies() {
        log.debug("Fetching all movies from storage service");
        return storageService.getAllMovies();
    }

    public Optional<Movie> getMovieById(Long movieId) {
        log.debug("Fetching movie with id: {}", movieId);
        return storageService.getMovieById(movieId);
    }

    public String getMovieStreamUrl(Long movieId) {
        log.debug("Getting stream URL for movie: {}", movieId);
        return storageService.getMovieStreamUrl(movieId);
    }

    public Optional<Movie> getMovieById(String movieId) {
        try {
            Long id = Long.parseLong(movieId);
            return getMovieById(id);
        } catch (NumberFormatException e) {
            log.warn("Invalid movie ID format: {}", movieId);
            return Optional.empty();
        }
    }

    public String getMovieStreamUrl(String movieId) {
        try {
            Long id = Long.parseLong(movieId);
            return getMovieStreamUrl(id);
        } catch (NumberFormatException e) {
            log.warn("Invalid movie ID format: {}", movieId);
            return null;
        }
    }
}
