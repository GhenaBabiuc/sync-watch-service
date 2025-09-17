package org.example.syncwatchservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Episode;
import org.example.syncwatchservice.model.Movie;
import org.example.syncwatchservice.model.Room;
import org.example.syncwatchservice.model.Season;
import org.example.syncwatchservice.model.Series;
import org.example.syncwatchservice.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final MovieService movieService;
    private final SeriesService seriesService;

    public Room createMovieRoom(String roomName, Long movieId, String hostId) {
        Optional<Movie> movieOpt = movieService.getMovieById(movieId);
        if (movieOpt.isEmpty()) {
            throw new IllegalArgumentException("Movie not found with id: " + movieId);
        }

        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room(roomId, roomName, movieOpt.get(), hostId);
        rooms.put(roomId, room);

        log.info("Created movie room {} for movie {} with host {}", roomId, movieOpt.get().getTitle(), hostId);
        return room;
    }

    public Room createSeriesRoom(String roomName, Long seriesId, String hostId) {
        Optional<Series> seriesOpt = seriesService.getSeriesById(seriesId);
        if (seriesOpt.isEmpty()) {
            throw new IllegalArgumentException("Series not found with id: " + seriesId);
        }

        Optional<Episode> firstEpisodeOpt = seriesService.getFirstEpisode(seriesId);
        if (firstEpisodeOpt.isEmpty()) {
            throw new IllegalArgumentException("No episodes found for series: " + seriesId);
        }

        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room(roomId, roomName, seriesOpt.get(), firstEpisodeOpt.get().getId(), hostId);
        room.setCurrentEpisode(firstEpisodeOpt.get());
        rooms.put(roomId, room);

        log.info("Created series room {} for series {} starting with episode S{}E{} with host {}",
                roomId, seriesOpt.get().getTitle(),
                firstEpisodeOpt.get().getSeasonNumber(),
                firstEpisodeOpt.get().getEpisodeNumber(),
                hostId);
        return room;
    }

    public Optional<Room> getRoomById(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    public boolean joinRoom(String roomId, User user) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.addUser(user);
            log.info("User {} joined room {}", user.getUsername(), roomId);
            return true;
        }
        log.warn("Attempted to join non-existent room: {}", roomId);
        return false;
    }

    public boolean leaveRoom(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            boolean removed = room.getUsers().removeIf(user -> user.getId().equals(userId));

            if (removed) {
                log.info("User {} left room {}", userId, roomId);
            }

            if (room.getUsers().isEmpty()) {
                rooms.remove(roomId);
                log.info("Deleted empty room: {}", roomId);
            }
            return removed;
        }
        return false;
    }

    public void updateRoomState(String roomId, double currentTime, boolean isPlaying, String userId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.setCurrentTime(currentTime);
            room.setPlaying(isPlaying);
            room.setLastActionUserId(userId);

            log.debug("Updated room {} state: time={}, playing={}, user={}",
                    roomId, currentTime, isPlaying, userId);
        }
    }

    public void updateUserTime(String roomId, String userId, double currentTime) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.getUsers().stream()
                    .filter(user -> user.getId().equals(userId))
                    .findFirst()
                    .ifPresent(user -> {
                        user.setCurrentTime(currentTime);
                        user.setLastSeen(java.time.LocalDateTime.now());
                    });
        }
    }

    public void updateAllUsersTime(String roomId, double currentTime) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.getUsers().forEach(user -> {
                user.setCurrentTime(currentTime);
                user.setLastSeen(java.time.LocalDateTime.now());
            });
        }
    }

    public boolean isHost(String roomId, String userId) {
        Room room = rooms.get(roomId);
        return room != null && room.getHostId().equals(userId);
    }

    public void deleteRoom(String roomId) {
        Room removed = rooms.remove(roomId);
        if (removed != null) {
            log.info("Manually deleted room: {}", roomId);
        }
    }

    public boolean switchEpisode(String roomId, Long episodeId, String userId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.warn("Room not found: {}", roomId);
            return false;
        }

        if (room.getRoomType() != Room.RoomType.SERIES) {
            log.warn("Cannot switch episode in movie room: {}", roomId);
            return false;
        }

        if (!isHost(roomId, userId)) {
            log.warn("User {} is not host of room {}, cannot switch episode", userId, roomId);
            return false;
        }

        Optional<Episode> episodeOpt = seriesService.getEpisodeById(episodeId);
        if (episodeOpt.isEmpty()) {
            log.warn("Episode not found: {}", episodeId);
            return false;
        }

        Episode episode = episodeOpt.get();

        if (!episode.getSeriesId().equals(room.getSeries().getId())) {
            log.warn("Episode {} does not belong to series {} in room {}",
                    episodeId, room.getSeries().getId(), roomId);
            return false;
        }

        room.setCurrentEpisodeId(episodeId);
        room.setCurrentEpisode(episode);
        room.setCurrentTime(0.0);
        room.setPlaying(false);
        room.setLastActionUserId(userId);

        log.info("Switched to episode S{}E{} in room {} by user {}",
                episode.getSeasonNumber(), episode.getEpisodeNumber(), roomId, userId);
        return true;
    }

    public boolean switchToNextEpisode(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room == null || room.getRoomType() != Room.RoomType.SERIES) {
            return false;
        }

        if (!isHost(roomId, userId)) {
            log.warn("User {} is not host of room {}, cannot switch to next episode", userId, roomId);
            return false;
        }

        Optional<Episode> nextEpisodeOpt = seriesService.getNextEpisode(room.getCurrentEpisodeId());
        if (nextEpisodeOpt.isEmpty()) {
            log.info("No next episode found for room {}", roomId);
            return false;
        }

        return switchEpisode(roomId, nextEpisodeOpt.get().getId(), userId);
    }

    public boolean switchToPreviousEpisode(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room == null || room.getRoomType() != Room.RoomType.SERIES) {
            return false;
        }

        if (!isHost(roomId, userId)) {
            log.warn("User {} is not host of room {}, cannot switch to previous episode", userId, roomId);
            return false;
        }

        Optional<Episode> prevEpisodeOpt = seriesService.getPreviousEpisode(room.getCurrentEpisodeId());
        if (prevEpisodeOpt.isEmpty()) {
            log.info("No previous episode found for room {}", roomId);
            return false;
        }

        return switchEpisode(roomId, prevEpisodeOpt.get().getId(), userId);
    }

    public List<Episode> getAvailableEpisodes(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null || room.getRoomType() != Room.RoomType.SERIES) {
            return Collections.emptyList();
        }

        List<Season> seasons = seriesService.getSeasonsBySeries(room.getSeries().getId());
        List<Episode> allEpisodes = new ArrayList<>();

        for (Season season : seasons) {
            List<Episode> seasonEpisodes = seriesService.getEpisodesBySeason(season.getId());
            allEpisodes.addAll(seasonEpisodes);
        }

        allEpisodes.sort((e1, e2) -> {
            int seasonCompare = Integer.compare(e1.getSeasonNumber(), e2.getSeasonNumber());
            if (seasonCompare != 0) return seasonCompare;
            return Integer.compare(e1.getEpisodeNumber(), e2.getEpisodeNumber());
        });

        return allEpisodes;
    }

    public List<Room> getRoomsByMovieId(String movieId) {
        try {
            Long id = Long.parseLong(movieId);
            return rooms.values().stream()
                    .filter(room -> room.getRoomType() == Room.RoomType.MOVIE)
                    .filter(room -> room.getMovie() != null && room.getMovie().getId().equals(id))
                    .toList();
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    public List<Room> getRoomsBySeriesId(Long seriesId) {
        return rooms.values().stream()
                .filter(room -> room.getRoomType() == Room.RoomType.SERIES)
                .filter(room -> room.getSeries() != null && room.getSeries().getId().equals(seriesId))
                .toList();
    }

    public int getTotalRoomsCount() {
        return rooms.size();
    }

    public int getTotalUsersCount() {
        return rooms.values().stream()
                .mapToInt(Room::getUserCount)
                .sum();
    }
}
