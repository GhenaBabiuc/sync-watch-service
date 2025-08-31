package org.example.syncwatchservice.service;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.dto.JoinRoomRequest;
import org.example.syncwatchservice.dto.LoadVideoRequest;
import org.example.syncwatchservice.dto.RoomJoinedResponse;
import org.example.syncwatchservice.dto.VideoCommandResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketIOService {

    private final SocketIOServer server;
    private final RoomManagerService roomManager;
    private final VideoService videoService;

    public void startServer() {
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());

        server.addEventListener("create-room", String.class, this::onCreateRoom);
        server.addEventListener("join-room", JoinRoomRequest.class, this::onJoinRoom);
        server.addEventListener("leave-room", Void.class, this::onLeaveRoom);
        server.addEventListener("get-rooms", Void.class, this::onGetRooms);
        server.addEventListener("update-time", Double.class, this::onUpdateTime);
        server.addEventListener("select-video", String.class, this::onSelectVideo);
        server.addEventListener("play", Double.class, this::onPlay);
        server.addEventListener("pause", Double.class, this::onPause);
        server.addEventListener("seek", Double.class, this::onSeek);

        server.start();
        log.info("Socket.IO сервер запущен на порту {}", server.getConfiguration().getPort());
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            server.stop();
            log.info("Socket.IO сервер остановлен");
        }
    }

    private ConnectListener onConnected() {
        return client -> {
            log.info("Новое подключение: {}", client.getSessionId());
            client.sendEvent("rooms-list", roomManager.getRoomsList());
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String socketId = client.getSessionId().toString();
            log.info("Отключение пользователя: {}", socketId);

            String roomId = roomManager.leaveRoom(socketId);
            if (roomId != null) {
                server.getRoomOperations(roomId).sendEvent("users-list",
                        roomManager.getUsersList(roomId));
            }

            server.getBroadcastOperations().sendEvent("rooms-list", roomManager.getRoomsList());
        };
    }

    private void onCreateRoom(com.corundumstudio.socketio.SocketIOClient client, String roomId, com.corundumstudio.socketio.AckRequest ackSender) {
        if (roomId == null || roomId.trim().isEmpty()) {
            client.sendEvent("error", "Некорректное название комнаты");
            return;
        }

        roomId = roomId.trim();
        if (roomId.length() > 50) {
            client.sendEvent("error", "Название комнаты слишком длинное");
            return;
        }

        log.info("Создание комнаты: {} пользователем: {}", roomId, client.getSessionId());
        roomManager.createRoom(roomId);
        client.sendEvent("room-created", roomId);

        server.getBroadcastOperations().sendEvent("rooms-list", roomManager.getRoomsList());
    }

    private void onJoinRoom(com.corundumstudio.socketio.SocketIOClient client, JoinRoomRequest request, com.corundumstudio.socketio.AckRequest ackSender) {
        if (request.getRoomId() == null || request.getNickname() == null ||
                request.getRoomId().trim().isEmpty() || request.getNickname().trim().isEmpty()) {
            client.sendEvent("error", "Некорректные данные для входа в комнату");
            return;
        }

        String roomId = request.getRoomId().trim();
        String nickname = request.getNickname().trim();

        if (nickname.length() < 2 || nickname.length() > 20) {
            client.sendEvent("error", "Никнейм должен быть от 2 до 20 символов");
            return;
        }

        log.info("Пользователь {} ({}) присоединяется к комнате {}", nickname, client.getSessionId(), roomId);

        var room = roomManager.joinRoom(client.getSessionId().toString(), roomId, nickname);
        client.joinRoom(roomId);

        RoomJoinedResponse response = new RoomJoinedResponse(
                roomId, room.getVideo(), room.getTime(), room.isPlaying()
        );
        client.sendEvent("room-joined", response);

        server.getRoomOperations(roomId).sendEvent("users-list", roomManager.getUsersList(roomId));
        server.getBroadcastOperations().sendEvent("rooms-list", roomManager.getRoomsList());
    }

    private void onLeaveRoom(com.corundumstudio.socketio.SocketIOClient client, Void data, com.corundumstudio.socketio.AckRequest ackSender) {
        String socketId = client.getSessionId().toString();
        log.info("Пользователь покидает комнату: {}", socketId);

        String roomId = roomManager.leaveRoom(socketId);
        if (roomId != null) {
            client.leaveRoom(roomId);
            server.getRoomOperations(roomId).sendEvent("users-list", roomManager.getUsersList(roomId));
            server.getBroadcastOperations().sendEvent("rooms-list", roomManager.getRoomsList());
        }
    }

    private void onGetRooms(com.corundumstudio.socketio.SocketIOClient client, Void data, com.corundumstudio.socketio.AckRequest ackSender) {
        client.sendEvent("rooms-list", roomManager.getRoomsList());
    }

    private void onUpdateTime(com.corundumstudio.socketio.SocketIOClient client, Double time, com.corundumstudio.socketio.AckRequest ackSender) {
        if (time == null || Double.isNaN(time) || time < 0) return;
        roomManager.updateUserTime(client.getSessionId().toString(), time);
    }

    private void onSelectVideo(com.corundumstudio.socketio.SocketIOClient client, String filename, com.corundumstudio.socketio.AckRequest ackSender) {
        if (filename == null || filename.trim().isEmpty()) {
            client.sendEvent("error", "Некорректное имя файла");
            return;
        }

        String socketId = client.getSessionId().toString();
        String roomId = roomManager.getUserRoomId(socketId);

        if (roomId == null) {
            client.sendEvent("error", "Вы не находитесь в комнате");
            return;
        }

        if (!videoService.isValidVideoFile(filename)) {
            client.sendEvent("error", "Небезопасное имя файла или файл не найден");
            return;
        }

        log.info("Выбрано видео {} в комнате {}", filename, roomId);

        roomManager.selectVideo(socketId, filename);

        LoadVideoRequest loadRequest = new LoadVideoRequest(filename, 0);
        server.getRoomOperations(roomId).sendEvent("load-video", loadRequest);

        server.getRoomOperations(roomId).sendEvent("users-list", roomManager.getUsersList(roomId));
        server.getBroadcastOperations().sendEvent("rooms-list", roomManager.getRoomsList());
    }

    private void onPlay(com.corundumstudio.socketio.SocketIOClient client, Double timestamp, com.corundumstudio.socketio.AckRequest ackSender) {
        if (timestamp == null || Double.isNaN(timestamp)) return;

        VideoCommandResult result = roomManager.handleVideoCommand(
                client.getSessionId().toString(), "play", timestamp
        );

        if (result != null) {
            log.info("Воспроизведение в комнате {} с позиции {}", result.getRoomId(), timestamp);
            server.getRoomOperations(result.getRoomId()).getClients().stream()
                    .filter(c -> !c.getSessionId().equals(client.getSessionId()))
                    .forEach(c -> c.sendEvent("sync-play", timestamp));
        }
    }

    private void onPause(com.corundumstudio.socketio.SocketIOClient client, Double timestamp, com.corundumstudio.socketio.AckRequest ackSender) {
        if (timestamp == null || Double.isNaN(timestamp)) return;

        VideoCommandResult result = roomManager.handleVideoCommand(
                client.getSessionId().toString(), "pause", timestamp
        );

        if (result != null) {
            log.info("Пауза в комнате {} на позиции {}", result.getRoomId(), timestamp);
            server.getRoomOperations(result.getRoomId()).getClients().stream()
                    .filter(c -> !c.getSessionId().equals(client.getSessionId()))
                    .forEach(c -> c.sendEvent("sync-pause", timestamp));
        }
    }

    private void onSeek(com.corundumstudio.socketio.SocketIOClient client, Double timestamp, com.corundumstudio.socketio.AckRequest ackSender) {
        if (timestamp == null || Double.isNaN(timestamp)) return;

        VideoCommandResult result = roomManager.handleVideoCommand(
                client.getSessionId().toString(), "seek", timestamp
        );

        if (result != null) {
            log.info("Перемотка в комнате {} на позицию {}", result.getRoomId(), timestamp);
            server.getRoomOperations(result.getRoomId()).getClients().stream()
                    .filter(c -> !c.getSessionId().equals(client.getSessionId()))
                    .forEach(c -> c.sendEvent("sync-seek", timestamp));
        }
    }

    @Scheduled(fixedRate = 2000)
    public void broadcastUsersList() {
        roomManager.getRoomsList().forEach(room -> {
            if (room.getUsers() > 0) {
                server.getRoomOperations(room.getId()).sendEvent("users-list",
                        roomManager.getUsersList(room.getId()));
            }
        });
    }
}
