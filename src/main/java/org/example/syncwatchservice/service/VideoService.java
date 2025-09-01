package org.example.syncwatchservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.dto.MovieDTO;
import org.example.syncwatchservice.dto.MovieListDTO;
import org.example.syncwatchservice.entity.Movie;
import org.example.syncwatchservice.entity.MovieFile;
import org.example.syncwatchservice.repository.MovieFileRepository;
import org.example.syncwatchservice.repository.MovieRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VideoService {

    private final MovieRepository movieRepository;
    private final MovieFileRepository movieFileRepository;
    private final MinioService minioService;

    @Cacheable(value = "moviesList", unless = "#result == null || #result.isEmpty()")
    public List<MovieListDTO> getMoviesList() {
        try {
            List<Movie> movies = movieRepository.findMoviesWithVideo();
            log.info("Найдено {} фильмов с видеофайлами", movies.size());

            return movies.stream()
                    .map(this::mapToMovieListDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Ошибка получения списка фильмов: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Cacheable(value = "movieDetails", key = "#movieId")
    public Optional<MovieDTO> getMovieDetails(Long movieId) {
        try {
            return movieRepository.findByIdWithFiles(movieId)
                    .map(this::mapToMovieDTO);
        } catch (Exception e) {
            log.error("Ошибка получения деталей фильма ID {}: {}", movieId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<String> getVideoStreamingUrl(Long movieId) {
        try {
            Optional<MovieFile> videoFile = movieFileRepository.findVideoByMovieId(movieId);

            if (videoFile.isEmpty()) {
                log.warn("Видеофайл не найден для фильма ID: {}", movieId);
                return Optional.empty();
            }

            MovieFile file = videoFile.get();
            String streamingUrl = minioService.generateStreamingUrl(
                    file.getMinioBucket(),
                    file.getMinioObjectKey(),
                    2
            );

            log.info("Сгенерирован streaming URL для фильма ID {}: {}", movieId,
                    streamingUrl.substring(0, Math.min(50, streamingUrl.length())) + "...");

            return Optional.of(streamingUrl);

        } catch (Exception e) {
            log.error("Ошибка генерации streaming URL для фильма ID {}: {}", movieId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<String> getCoverUrl(Long movieId) {
        try {
            Optional<MovieFile> coverFile = movieFileRepository.findCoverByMovieId(movieId);

            if (coverFile.isEmpty()) {
                log.debug("Обложка не найдена для фильма ID: {}", movieId);
                return Optional.empty();
            }

            MovieFile file = coverFile.get();
            String coverUrl = minioService.generateCoverUrl(
                    file.getMinioBucket(),
                    file.getMinioObjectKey()
            );

            return Optional.ofNullable(coverUrl);

        } catch (Exception e) {
            log.error("Ошибка генерации URL обложки для фильма ID {}: {}", movieId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public boolean movieExists(Long movieId) {
        try {
            return movieRepository.existsById(movieId) &&
                    movieFileRepository.findVideoByMovieId(movieId).isPresent();
        } catch (Exception e) {
            log.error("Ошибка проверки существования фильма ID {}: {}", movieId, e.getMessage());
            return false;
        }
    }

    public Optional<MovieFile> getVideoFile(Long movieId) {
        try {
            return movieFileRepository.findVideoByMovieId(movieId);
        } catch (Exception e) {
            log.error("Ошибка получения видеофайла для фильма ID {}: {}", movieId, e.getMessage());
            return Optional.empty();
        }
    }

    private MovieListDTO mapToMovieListDTO(Movie movie) {
        MovieListDTO dto = new MovieListDTO();
        dto.setId(movie.getId());
        dto.setTitle(movie.getTitle());
        dto.setDescription(movie.getDescription());
        dto.setYear(movie.getYear());
        dto.setDuration(movie.getDuration());

        getCoverUrl(movie.getId()).ifPresent(dto::setCoverUrl);

        return dto;
    }

    private MovieDTO mapToMovieDTO(Movie movie) {
        MovieDTO dto = new MovieDTO();
        dto.setId(movie.getId());
        dto.setTitle(movie.getTitle());
        dto.setDescription(movie.getDescription());
        dto.setYear(movie.getYear());
        dto.setDuration(movie.getDuration());

        getCoverUrl(movie.getId()).ifPresent(dto::setCoverUrl);
        getVideoStreamingUrl(movie.getId()).ifPresent(dto::setVideoUrl);

        getVideoFile(movie.getId()).ifPresent(file -> dto.setFileSize(file.getFileSize()));

        return dto;
    }

    @org.springframework.cache.annotation.CacheEvict(value = {"moviesList", "movieDetails"}, allEntries = true)
    public void clearCache() {
        log.info("Кеш фильмов очищен");
    }
}
