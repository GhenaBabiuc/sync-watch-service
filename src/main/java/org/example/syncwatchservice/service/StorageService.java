package org.example.syncwatchservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Episode;
import org.example.syncwatchservice.model.FileInfo;
import org.example.syncwatchservice.model.Movie;
import org.example.syncwatchservice.model.Season;
import org.example.syncwatchservice.model.Series;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class StorageService {

    private final RestTemplate restTemplate;
    private final String storageApiUrl;

    public StorageService(RestTemplate restTemplate,
                          @Value("${app.storage.api.url:http://localhost:8081/api}") String storageApiUrl) {
        this.restTemplate = restTemplate;
        this.storageApiUrl = storageApiUrl;
    }

    public List<Movie> getAllMovies() {
        try {
            String url = storageApiUrl + "/movies?size=100";
            ResponseEntity<PageResponse<Movie>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<PageResponse<Movie>>() {
                    }
            );

            if (response.getBody() != null && response.getBody().getContent() != null) {
                List<Movie> movies = response.getBody().getContent();
                movies.forEach(movie -> {
                    movie.setStreamUrl(storageApiUrl + "/stream/movies/" + movie.getId());
                    movie.setCoverUrl(getCoverUrl(movie.getFiles()));
                });
                return movies;
            }
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Error fetching movies from storage service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<Movie> getMovieById(Long movieId) {
        try {
            String url = storageApiUrl + "/movies/" + movieId;
            ResponseEntity<Movie> response = restTemplate.getForEntity(url, Movie.class);

            if (response.getBody() != null) {
                Movie movie = response.getBody();
                movie.setStreamUrl(storageApiUrl + "/stream/movies/" + movieId);
                movie.setCoverUrl(getCoverUrl(movie.getFiles()));
                return Optional.of(movie);
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("Error fetching movie {} from storage service: {}", movieId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<Series> getAllSeries() {
        try {
            String url = storageApiUrl + "/series?size=100";
            ResponseEntity<PageResponse<Series>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<PageResponse<Series>>() {
                    }
            );

            if (response.getBody() != null && response.getBody().getContent() != null) {
                return response.getBody().getContent();
            }
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Error fetching series from storage service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<Series> getSeriesById(Long seriesId) {
        try {
            String url = storageApiUrl + "/series/" + seriesId;
            ResponseEntity<Series> response = restTemplate.getForEntity(url, Series.class);

            if (response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("Error fetching series {} from storage service: {}", seriesId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<Season> getSeasonsBySeries(Long seriesId) {
        try {
            String url = storageApiUrl + "/series/" + seriesId + "/seasons?size=100";
            ResponseEntity<PageResponse<Season>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<PageResponse<Season>>() {
                    }
            );

            if (response.getBody() != null && response.getBody().getContent() != null) {
                return response.getBody().getContent();
            }
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Error fetching seasons for series {} from storage service: {}", seriesId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Episode> getEpisodesBySeason(Long seasonId) {
        try {
            String url = storageApiUrl + "/series/seasons/" + seasonId + "/episodes?size=100";
            ResponseEntity<PageResponse<Episode>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<PageResponse<Episode>>() {
                    }
            );

            if (response.getBody() != null && response.getBody().getContent() != null) {
                List<Episode> episodes = response.getBody().getContent();

                episodes.forEach(episode -> {
                    episode.setStreamUrl(storageApiUrl + "/stream/episodes/" + episode.getId());
                    episode.setCoverUrl(getCoverUrl(episode.getFiles()));
                });
                return episodes;
            }
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("Error fetching episodes for season {} from storage service: {}", seasonId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<Episode> getEpisodeById(Long episodeId) {
        try {
            String url = storageApiUrl + "/stream/episodes/" + episodeId + "/info";
            ResponseEntity<MediaInfo> response = restTemplate.getForEntity(url, MediaInfo.class);

            if (response.getBody() != null) {
                MediaInfo mediaInfo = response.getBody();
                Episode episode = convertMediaInfoToEpisode(mediaInfo);
                episode.setStreamUrl(mediaInfo.getStreamUrl());
                return Optional.of(episode);
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("Error fetching episode {} from storage service: {}", episodeId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Episode> getFirstEpisode(Long seriesId) {
        List<Season> seasons = getSeasonsBySeries(seriesId);
        if (seasons.isEmpty()) {
            return Optional.empty();
        }

        Season firstSeason = seasons.stream()
                .min((s1, s2) -> Integer.compare(s1.getSeasonNumber(), s2.getSeasonNumber()))
                .orElse(seasons.get(0));

        List<Episode> episodes = getEpisodesBySeason(firstSeason.getId());
        if (episodes.isEmpty()) {
            return Optional.empty();
        }

        return episodes.stream()
                .min((e1, e2) -> Integer.compare(e1.getEpisodeNumber(), e2.getEpisodeNumber()));
    }

    public String getMovieStreamUrl(Long movieId) {
        return storageApiUrl + "/stream/movies/" + movieId;
    }

    public String getEpisodeStreamUrl(Long episodeId) {
        return storageApiUrl + "/stream/episodes/" + episodeId;
    }

    private String getCoverUrl(List<FileInfo> files) {
        if (files != null) {
            return files.stream()
                    .filter(f -> "COVER".equals(f.getFileType()))
                    .filter(f -> "COMPLETED".equals(f.getUploadStatus()))
                    .map(FileInfo::getDownloadUrl)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private Episode convertMediaInfoToEpisode(MediaInfo mediaInfo) {
        Episode episode = new Episode();
        episode.setId(mediaInfo.getId());
        episode.setTitle(mediaInfo.getTitle());
        episode.setDescription(mediaInfo.getDescription());
        episode.setDuration(mediaInfo.getDuration());
        episode.setEpisodeNumber(mediaInfo.getEpisodeNumber());
        episode.setSeasonId(mediaInfo.getSeasonId());
        episode.setSeasonNumber(mediaInfo.getSeasonNumber());
        episode.setSeriesId(mediaInfo.getSeriesId());
        episode.setSeriesTitle(mediaInfo.getSeriesTitle());
        return episode;
    }

    public static class PageResponse<T> {
        private List<T> content;
        private int totalElements;
        private int totalPages;
        private int size;
        private int number;

        public List<T> getContent() {
            return content;
        }

        public void setContent(List<T> content) {
            this.content = content;
        }

        public int getTotalElements() {
            return totalElements;
        }

        public void setTotalElements(int totalElements) {
            this.totalElements = totalElements;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getNumber() {
            return number;
        }

        public void setNumber(int number) {
            this.number = number;
        }
    }

    public static class MediaInfo {
        private Long id;
        private String type;
        private String title;
        private String description;
        private Integer year;
        private Integer duration;
        private String streamUrl;
        private Integer episodeNumber;
        private Long seasonId;
        private Integer seasonNumber;
        private Long seriesId;
        private String seriesTitle;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getYear() {
            return year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public Integer getDuration() {
            return duration;
        }

        public void setDuration(Integer duration) {
            this.duration = duration;
        }

        public String getStreamUrl() {
            return streamUrl;
        }

        public void setStreamUrl(String streamUrl) {
            this.streamUrl = streamUrl;
        }

        public Integer getEpisodeNumber() {
            return episodeNumber;
        }

        public void setEpisodeNumber(Integer episodeNumber) {
            this.episodeNumber = episodeNumber;
        }

        public Long getSeasonId() {
            return seasonId;
        }

        public void setSeasonId(Long seasonId) {
            this.seasonId = seasonId;
        }

        public Integer getSeasonNumber() {
            return seasonNumber;
        }

        public void setSeasonNumber(Integer seasonNumber) {
            this.seasonNumber = seasonNumber;
        }

        public Long getSeriesId() {
            return seriesId;
        }

        public void setSeriesId(Long seriesId) {
            this.seriesId = seriesId;
        }

        public String getSeriesTitle() {
            return seriesTitle;
        }

        public void setSeriesTitle(String seriesTitle) {
            this.seriesTitle = seriesTitle;
        }
    }
}
