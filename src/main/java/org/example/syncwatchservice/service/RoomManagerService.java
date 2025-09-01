package org.example.syncwatchservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.dto.RoomInfo;
import org.example.syncwatchservice.dto.UserInfo;
import org.example.syncwatchservice.dto.VideoCommandResult;
import org.example.syncwatchservice.model.Room;
import org.example.syncwatchservice.model.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomManagerService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();
    private final VideoService videoService;

    private static final int CLEANUP_THRESHOLD_MINUTES = 30;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @PostConstruct
    public void init() {
        log.info("RoomManagerService инициализирован с поддержкой базы данных");
    }

    public Room createRoom(String roomId) {
        if (!rooms.containsKey(roomId)) {
            Room room = new Room(roomId);
            rooms.put(roomId, room);
            log.info("Создана новая комната: {}", roomId);
        }
        return rooms.get(roomId);
    }

    public List<RoomInfo> getRoomsList() {
        List<RoomInfo> roomsList = new ArrayList<>();
        rooms.forEach((roomId, room) -> {
            String videoTitle = "Не выбрано";
            if (room.getMovieId() != null) {
                // Получаем название фильма из базы данных
                videoTitle = videoService.getMovieDetails(room.getMovieId())
                        .map(movie -> movie.getTitle())
                        .orElse("Фильм недоступен");
            }

            roomsList.add(new RoomInfo(
                    roomId,
                    room.getUserCount(),
                    videoTitle,
                    room.isPlaying()
            ));
        });
        return roomsList;
    }

    public Room joinRoom(String socketId, String roomId, String nickname) {
        leaveRoom(socketId);

        Room room = createRoom(roomId);
        userRooms.put(socketId, roomId);

        User user = new User(nickname);
        user.setCurrentTime(room.getTime());
        room.getUsers().put(socketId, user);
        room.updateActivity();

        log.info("Пользователь {} ({}) присоединился к комнате {}", nickname, socketId, roomId);
        return room;
    }

    public String leaveRoom(String socketId) {
        String roomId = userRooms.get(socketId);
        if (roomId != null) {
            Room room = rooms.get(roomId);
            if (room != null) {
                room.getUsers().remove(socketId);
                room.getLastCommands().remove(socketId);
                room.updateActivity();
                log.info("Пользователь {} покинул комнату {}", socketId, roomId);

                if (room.getUserCount() == 0) {
                    cleanupEmptyRoom(roomId);
                }
            }
            userRooms.remove(socketId);
            return roomId;
        }
        return null;
    }

    private void cleanupEmptyRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null) {
            room.getUsers().clear();
            room.getLastCommands().clear();
            room.setMovieId(null);
            room.setTime(0);
            room.setPlaying(false);

            log.info("Комната {} удалена (стала пустой). Память освобождена.", roomId);
        }
    }

    public boolean forceCleanupRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.getUsers().keySet().forEach(userRooms::remove);

            cleanupEmptyRoom(roomId);

            log.info("Комната {} принудительно очищена", roomId);
            return true;
        }
        return false;
    }

    public RoomStats getRoomStats() {
        int totalUsers = userRooms.size();
        int totalRooms = rooms.size();
        int activeRooms = (int) rooms.values().stream()
                .filter(room -> room.getUserCount() > 0)
                .count();
        int emptyRooms = totalRooms - activeRooms;

        return new RoomStats(totalRooms, activeRooms, emptyRooms, totalUsers);
    }

    public static class RoomStats {
        public final int totalRooms;
        public final int activeRooms;
        public final int emptyRooms;
        public final int totalUsers;

        public RoomStats(int totalRooms, int activeRooms, int emptyRooms, int totalUsers) {
            this.totalRooms = totalRooms;
            this.activeRooms = activeRooms;
            this.emptyRooms = emptyRooms;
            this.totalUsers = totalUsers;
        }

        @Override
        public String toString() {
            return String.format("RoomStats{rooms=%d (active=%d, empty=%d), users=%d}",
                    totalRooms, activeRooms, emptyRooms, totalUsers);
        }
    }

    public boolean updateUserTime(String socketId, double time) {
        String roomId = userRooms.get(socketId);
        if (roomId == null) return false;

        Room room = rooms.get(roomId);
        if (room == null || !room.getUsers().containsKey(socketId)) return false;

        User user = room.getUsers().get(socketId);
        user.updateTime(time);
        room.updateActivity();
        return true;
    }

    public VideoCommandResult handleVideoCommand(String socketId, String command, double timestamp) {
        String roomId = userRooms.get(socketId);
        if (roomId == null) return null;

        Room room = rooms.get(roomId);
        if (room == null) return null;

        Room.LastCommand lastCommand = room.getLastCommands().computeIfAbsent(socketId, k -> new Room.LastCommand());

        int debounceTime = switch (command) {
            case "play", "pause" -> 100;
            case "seek" -> 200;
            default -> 100;
        };

        if (!lastCommand.canExecuteCommand(command, debounceTime)) {
            return null;
        }

        if ("seek".equals(command) && Math.abs(timestamp - room.getTime()) < 0.5) {
            return null;
        }

        room.setTime(timestamp);
        if ("play".equals(command)) room.setPlaying(true);
        if ("pause".equals(command)) room.setPlaying(false);

        lastCommand.updateCommand(command);
        room.updateActivity();

        log.info("Команда {} выполнена в комнате {} на позиции {}", command, roomId, timestamp);
        return new VideoCommandResult(roomId, command, timestamp);
    }

    public List<UserInfo> getUsersList(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) return new ArrayList<>();

        List<UserInfo> usersList = new ArrayList<>();
        room.getUsers().values().forEach(user -> {
            usersList.add(new UserInfo(
                    user.getNickname(),
                    user.getCurrentTime(),
                    user.getLastUpdate().format(FORMATTER)
            ));
        });
        return usersList;
    }

    public boolean selectMovie(String socketId, Long movieId) {
        String roomId = userRooms.get(socketId);
        if (roomId == null) return false;

        Room room = rooms.get(roomId);
        if (room == null) return false;

        if (!videoService.movieExists(movieId)) {
            log.warn("Попытка выбрать несуществующий фильм ID: {} в комнате {}", movieId, roomId);
            return false;
        }

        log.info("Выбран фильм ID {} в комнате {}", movieId, roomId);

        room.setMovieId(movieId);
        room.setTime(0);
        room.setPlaying(false);
        room.updateActivity();

        room.getUsers().values().forEach(user -> user.updateTime(0));

        return true;
    }

    public String getUserRoomId(String socketId) {
        return userRooms.get(socketId);
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupInactiveRooms() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(CLEANUP_THRESHOLD_MINUTES);
        RoomStats statsBefore = getRoomStats();

        int removedRooms = 0;
        var iterator = rooms.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            Room room = entry.getValue();
            String roomId = entry.getKey();

            if (room.getUserCount() == 0 && room.getLastActivity().isBefore(threshold)) {
                userRooms.entrySet().removeIf(userEntry ->
                        roomId.equals(userEntry.getValue()));

                room.getUsers().clear();
                room.getLastCommands().clear();
                room.setMovieId(null);

                iterator.remove();
                removedRooms++;

                log.debug("Удалена неактивная комната: {}", roomId);
            }
        }

        if (removedRooms > 0) {
            RoomStats statsAfter = getRoomStats();
            log.info("Периодическая очистка: удалено {} комнат. До: {}, После: {}",
                    removedRooms, statsBefore, statsAfter);

            if (removedRooms > 10) {
                System.gc();
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void logRoomStats() {
        RoomStats stats = getRoomStats();
        if (stats.totalRooms > 0) {
            log.debug("Статистика комнат: {}", stats);
        }
    }
}
