package org.example.syncwatchservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Episode;
import org.example.syncwatchservice.model.Season;
import org.example.syncwatchservice.model.Series;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeriesService {

    private final StorageService storageService;

    public List<Series> getAllSeries() {
        log.debug("Fetching all series from storage service");
        return storageService.getAllSeries();
    }

    public Optional<Series> getSeriesById(Long seriesId) {
        log.debug("Fetching series with id: {}", seriesId);
        return storageService.getSeriesById(seriesId);
    }

    public List<Season> getSeasonsBySeries(Long seriesId) {
        log.debug("Fetching seasons for series: {}", seriesId);
        return storageService.getSeasonsBySeries(seriesId);
    }

    public List<Episode> getEpisodesBySeason(Long seasonId) {
        log.debug("Fetching episodes for season: {}", seasonId);
        return storageService.getEpisodesBySeason(seasonId);
    }

    public Optional<Episode> getEpisodeById(Long episodeId) {
        log.debug("Fetching episode with id: {}", episodeId);
        return storageService.getEpisodeById(episodeId);
    }

    public Optional<Episode> getFirstEpisode(Long seriesId) {
        log.debug("Getting first episode for series: {}", seriesId);
        return storageService.getFirstEpisode(seriesId);
    }

    public String getEpisodeStreamUrl(Long episodeId) {
        log.debug("Getting stream URL for episode: {}", episodeId);
        return storageService.getEpisodeStreamUrl(episodeId);
    }

    public Optional<Episode> getNextEpisode(Long currentEpisodeId) {
        Optional<Episode> currentEpisodeOpt = getEpisodeById(currentEpisodeId);
        if (currentEpisodeOpt.isEmpty()) {
            return Optional.empty();
        }

        Episode currentEpisode = currentEpisodeOpt.get();
        List<Episode> seasonEpisodes = getEpisodesBySeason(currentEpisode.getSeasonId());

        Optional<Episode> nextInSeason = seasonEpisodes.stream()
                .filter(ep -> ep.getEpisodeNumber() == currentEpisode.getEpisodeNumber() + 1)
                .findFirst();

        if (nextInSeason.isPresent()) {
            return nextInSeason;
        }

        List<Season> seriesSeasons = getSeasonsBySeries(currentEpisode.getSeriesId());
        Optional<Season> nextSeason = seriesSeasons.stream()
                .filter(season -> season.getSeasonNumber() == currentEpisode.getSeasonNumber() + 1)
                .findFirst();

        if (nextSeason.isPresent()) {
            List<Episode> nextSeasonEpisodes = getEpisodesBySeason(nextSeason.get().getId());
            return nextSeasonEpisodes.stream()
                    .min((e1, e2) -> Integer.compare(e1.getEpisodeNumber(), e2.getEpisodeNumber()));
        }

        return Optional.empty();
    }

    public Optional<Episode> getPreviousEpisode(Long currentEpisodeId) {
        Optional<Episode> currentEpisodeOpt = getEpisodeById(currentEpisodeId);
        if (currentEpisodeOpt.isEmpty()) {
            return Optional.empty();
        }

        Episode currentEpisode = currentEpisodeOpt.get();
        List<Episode> seasonEpisodes = getEpisodesBySeason(currentEpisode.getSeasonId());

        Optional<Episode> prevInSeason = seasonEpisodes.stream()
                .filter(ep -> ep.getEpisodeNumber() == currentEpisode.getEpisodeNumber() - 1)
                .findFirst();

        if (prevInSeason.isPresent()) {
            return prevInSeason;
        }

        List<Season> seriesSeasons = getSeasonsBySeries(currentEpisode.getSeriesId());
        Optional<Season> prevSeason = seriesSeasons.stream()
                .filter(season -> season.getSeasonNumber() == currentEpisode.getSeasonNumber() - 1)
                .findFirst();

        if (prevSeason.isPresent()) {
            List<Episode> prevSeasonEpisodes = getEpisodesBySeason(prevSeason.get().getId());
            return prevSeasonEpisodes.stream()
                    .max((e1, e2) -> Integer.compare(e1.getEpisodeNumber(), e2.getEpisodeNumber()));
        }

        return Optional.empty();
    }
}
