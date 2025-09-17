package org.example.syncwatchservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class Room {
    private String id;
    private String name;
    private RoomType roomType;
    private Movie movie;
    private Series series;
    private Long currentEpisodeId;
    private Episode currentEpisode;
    private double currentTime;
    private boolean isPlaying;
    private String hostId;
    private LocalDateTime createdAt;
    private Set<User> users;
    private String lastActionUserId;

    public Room(String id, String name, Movie movie, String hostId) {
        this.id = id;
        this.name = name;
        this.movie = movie;
        this.roomType = RoomType.MOVIE;
        this.hostId = hostId;
        this.users = ConcurrentHashMap.newKeySet();
        this.createdAt = LocalDateTime.now();
        this.currentTime = 0.0;
        this.isPlaying = false;
    }

    public Room(String id, String name, Series series, Long initialEpisodeId, String hostId) {
        this.id = id;
        this.name = name;
        this.series = series;
        this.currentEpisodeId = initialEpisodeId;
        this.roomType = RoomType.SERIES;
        this.hostId = hostId;
        this.users = ConcurrentHashMap.newKeySet();
        this.createdAt = LocalDateTime.now();
        this.currentTime = 0.0;
        this.isPlaying = false;
    }

    public void addUser(User user) {
        this.users.add(user);
    }

    public void removeUser(User user) {
        this.users.remove(user);
    }

    public int getUserCount() {
        return users.size();
    }

    public void setUsers(Set<User> users) {
        if (users == null) {
            this.users = ConcurrentHashMap.newKeySet();
        } else {
            this.users = users;
        }
    }

    public String getStreamUrl() {
        if (roomType == RoomType.MOVIE && movie != null) {
            return movie.getStreamUrl();
        } else if (roomType == RoomType.SERIES && currentEpisode != null) {
            return currentEpisode.getStreamUrl();
        }
        return null;
    }

    public String getContentTitle() {
        if (roomType == RoomType.MOVIE && movie != null) {
            return movie.getTitle();
        } else if (roomType == RoomType.SERIES && series != null) {
            if (currentEpisode != null) {
                return String.format("%s - S%dE%d: %s",
                        series.getTitle(),
                        currentEpisode.getSeasonNumber(),
                        currentEpisode.getEpisodeNumber(),
                        currentEpisode.getEpisodeTitle());
            }
            return series.getTitle();
        }
        return "Unknown Content";
    }

    public String getCoverImageUrl() {
        if (roomType == RoomType.MOVIE && movie != null) {
            return movie.getCoverImageUrl();
        } else if (roomType == RoomType.SERIES) {
            if (currentEpisode != null) {
                return currentEpisode.getCoverImageUrl();
            } else if (series != null) {
                return series.getCoverImageUrl();
            }
        }
        return "/images/default-cover.jpg";
    }

    public enum RoomType {
        MOVIE, SERIES
    }
}
