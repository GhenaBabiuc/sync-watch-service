package org.example.syncwatchservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.model.Movie;
import org.example.syncwatchservice.model.Room;
import org.example.syncwatchservice.model.User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(String roomName, Movie movie, String hostId) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room(roomId, roomName, movie, hostId);
        rooms.put(roomId, room);

        log.info("Created room {} for movie {} with host {}", roomId, movie.getTitle(), hostId);
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

    public List<Room> getRoomsByMovieId(String movieId) {
        return rooms.values().stream()
                .filter(room -> room.getMovie().getId().equals(movieId))
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