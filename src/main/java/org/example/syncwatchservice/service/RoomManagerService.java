package org.example.syncwatchservice.service;

import jakarta.annotation.PostConstruct;
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
public class RoomManagerService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();

    private static final int CLEANUP_THRESHOLD_MINUTES = 30;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @PostConstruct
    public void init() {
        log.info("RoomManagerService инициализирован");
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
            roomsList.add(new RoomInfo(
                    roomId,
                    room.getUserCount(),
                    room.getVideo() != null ? room.getVideo() : "Не выбрано",
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
            }
            userRooms.remove(socketId);
            return roomId;
        }
        return null;
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

    public void selectVideo(String socketId, String filename) {
        String roomId = userRooms.get(socketId);
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null) return;

        log.info("Выбрано видео {} в комнате {}", filename, roomId);

        room.setVideo(filename);
        room.setTime(0);
        room.setPlaying(false);
        room.updateActivity();

        room.getUsers().values().forEach(user -> user.updateTime(0));
    }

    public String getUserRoomId(String socketId) {
        return userRooms.get(socketId);
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    @Scheduled(fixedRate = 600000)
    public void cleanupInactiveRooms() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(CLEANUP_THRESHOLD_MINUTES);

        rooms.entrySet().removeIf(entry -> {
            Room room = entry.getValue();
            if (room.getUserCount() == 0 && room.getLastActivity().isBefore(threshold)) {
                log.info("Удалена неактивная комната: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
