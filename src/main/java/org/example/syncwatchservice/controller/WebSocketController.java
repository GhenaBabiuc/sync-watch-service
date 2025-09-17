package org.example.syncwatchservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.syncwatchservice.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    private final Map<String, LastAction> lastActions = new ConcurrentHashMap<>();

    private static final long DEBOUNCE_TIME = 500;

    private static class LastAction {
        final String action;
        final double currentTime;
        final String userId;
        final long timestamp;

        LastAction(String action, double currentTime, String userId, long timestamp) {
            this.action = action;
            this.currentTime = currentTime;
            this.userId = userId;
            this.timestamp = timestamp;
        }
    }

    @MessageMapping("/room/{roomId}/play")
    public void handlePlay(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        Double currentTime = ((Number) payload.get("currentTime")).doubleValue();

        log.info("Play action in room {} by user {} at time {}", roomId, userId, currentTime);

        if (shouldIgnoreAction(roomId, "play", currentTime, userId)) {
            log.debug("Ignoring duplicate play action from user {} at time {}", userId, currentTime);
            return;
        }

        roomService.updateRoomState(roomId, currentTime, true, userId);
        roomService.updateAllUsersTime(roomId, currentTime);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/sync", Map.of(
                "action", "play",
                "currentTime", currentTime,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        ));

        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/pause")
    public void handlePause(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        Double currentTime = ((Number) payload.get("currentTime")).doubleValue();

        log.info("Pause action in room {} by user {} at time {}", roomId, userId, currentTime);

        if (shouldIgnoreAction(roomId, "pause", currentTime, userId)) {
            log.debug("Ignoring duplicate pause action from user {} at time {}", userId, currentTime);
            return;
        }

        roomService.updateRoomState(roomId, currentTime, false, userId);
        roomService.updateAllUsersTime(roomId, currentTime);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/sync", Map.of(
                "action", "pause",
                "currentTime", currentTime,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        ));

        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/seek")
    public void handleSeek(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        Double currentTime = ((Number) payload.get("currentTime")).doubleValue();

        log.info("Seek action in room {} by user {} to time {}", roomId, userId, currentTime);

        if (shouldIgnoreAction(roomId, "seek", currentTime, userId)) {
            log.debug("Ignoring duplicate seek action from user {} at time {}", userId, currentTime);
            return;
        }

        roomService.updateRoomState(roomId, currentTime, false, userId);
        roomService.updateAllUsersTime(roomId, currentTime);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/sync", Map.of(
                "action", "seek",
                "currentTime", currentTime,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        ));

        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/timeUpdate")
    public void handleTimeUpdate(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        Double currentTime = ((Number) payload.get("currentTime")).doubleValue();

        roomService.updateUserTime(roomId, userId, currentTime);
        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/join")
    public void handleJoin(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        log.info("User {} joined room {} via WebSocket", userId, roomId);

        updateRoomUsers(roomId);

        roomService.getRoomById(roomId).ifPresent(room -> {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/room/" + roomId + "/state",
                    Map.of(
                            "currentTime", room.getCurrentTime(),
                            "isPlaying", room.isPlaying(),
                            "lastActionUserId", room.getLastActionUserId() != null ? room.getLastActionUserId() : "",
                            "streamUrl", room.getStreamUrl() != null ? room.getStreamUrl() : "",
                            "roomType", room.getRoomType().name(),
                            "currentEpisodeId", room.getCurrentEpisodeId() != null ? room.getCurrentEpisodeId() : 0
                    )
            );
        });
    }

    @MessageMapping("/room/{roomId}/leave")
    public void handleLeave(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        log.info("User {} left room {} via WebSocket", userId, roomId);

        roomService.leaveRoom(roomId, userId);
        updateRoomUsers(roomId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/userLeft", Map.of(
                "userId", userId
        ));
    }

    @MessageMapping("/room/{roomId}/switchEpisode")
    public void handleSwitchEpisode(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        Long episodeId = ((Number) payload.get("episodeId")).longValue();

        log.info("Switch episode action in room {} by user {} to episode {}", roomId, userId, episodeId);

        if (!roomService.isHost(roomId, userId)) {
            log.warn("User {} is not host of room {}, cannot switch episode", userId, roomId);
            messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                    "error", "Only host can switch episodes"
            ));
            return;
        }

        boolean success = roomService.switchEpisode(roomId, episodeId, userId);

        if (success) {
            roomService.getRoomById(roomId).ifPresent(room -> {
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/episodeChanged", Map.of(
                        "action", "episodeChanged",
                        "episodeId", episodeId,
                        "streamUrl", room.getStreamUrl(),
                        "episode", room.getCurrentEpisode(),
                        "userId", userId,
                        "timestamp", System.currentTimeMillis()
                ));
            });
        } else {
            messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                    "error", "Failed to switch episode"
            ));
        }

        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/nextEpisode")
    public void handleNextEpisode(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        log.info("Next episode action in room {} by user {}", roomId, userId);

        if (!roomService.isHost(roomId, userId)) {
            log.warn("User {} is not host of room {}, cannot switch to next episode", userId, roomId);
            messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                    "error", "Only host can switch episodes"
            ));
            return;
        }

        boolean success = roomService.switchToNextEpisode(roomId, userId);

        if (success) {
            roomService.getRoomById(roomId).ifPresent(room -> {
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/episodeChanged", Map.of(
                        "action", "nextEpisode",
                        "episodeId", room.getCurrentEpisodeId(),
                        "streamUrl", room.getStreamUrl(),
                        "episode", room.getCurrentEpisode(),
                        "userId", userId,
                        "timestamp", System.currentTimeMillis()
                ));
            });
        } else {
            messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                    "error", "No next episode available"
            ));
        }

        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/previousEpisode")
    public void handlePreviousEpisode(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        log.info("Previous episode action in room {} by user {}", roomId, userId);

        if (!roomService.isHost(roomId, userId)) {
            log.warn("User {} is not host of room {}, cannot switch to previous episode", userId, roomId);
            messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                    "error", "Only host can switch episodes"
            ));
            return;
        }

        boolean success = roomService.switchToPreviousEpisode(roomId, userId);

        if (success) {
            roomService.getRoomById(roomId).ifPresent(room -> {
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/episodeChanged", Map.of(
                        "action", "previousEpisode",
                        "episodeId", room.getCurrentEpisodeId(),
                        "streamUrl", room.getStreamUrl(),
                        "episode", room.getCurrentEpisode(),
                        "userId", userId,
                        "timestamp", System.currentTimeMillis()
                ));
            });
        } else {
            messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                    "error", "No previous episode available"
            ));
        }

        updateRoomUsers(roomId);
    }

    @MessageMapping("/room/{roomId}/getAvailableEpisodes")
    public void handleGetAvailableEpisodes(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        log.debug("Get available episodes request from user {} in room {}", userId, roomId);

        var availableEpisodes = roomService.getAvailableEpisodes(roomId);

        messagingTemplate.convertAndSendToUser(userId, "/queue/room/" + roomId + "/availableEpisodes", Map.of(
                "episodes", availableEpisodes,
                "timestamp", System.currentTimeMillis()
        ));
    }

    @MessageMapping("/room/{roomId}/getRoomInfo")
    public void handleGetRoomInfo(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        log.debug("Get room info request from user {} for room {}", userId, roomId);

        roomService.getRoomById(roomId).ifPresentOrElse(
                room -> {
                    messagingTemplate.convertAndSendToUser(userId, "/queue/room/" + roomId + "/roomInfo", Map.of(
                            "room", Map.of(
                                    "id", room.getId(),
                                    "name", room.getName(),
                                    "roomType", room.getRoomType().name(),
                                    "currentTime", room.getCurrentTime(),
                                    "isPlaying", room.isPlaying(),
                                    "userCount", room.getUserCount(),
                                    "streamUrl", room.getStreamUrl() != null ? room.getStreamUrl() : "",
                                    "currentEpisodeId", room.getCurrentEpisodeId() != null ? room.getCurrentEpisodeId() : 0,
                                    "contentTitle", room.getContentTitle()
                            ),
                            "timestamp", System.currentTimeMillis()
                    ));
                },
                () -> {
                    messagingTemplate.convertAndSendToUser(userId, "/queue/error", Map.of(
                            "error", "Room not found: " + roomId
                    ));
                }
        );
    }

    @MessageMapping("/room/{roomId}/changeQuality")
    public void handleChangeQuality(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String quality = (String) payload.get("quality");

        log.info("Quality change request in room {} by user {} to quality {}", roomId, userId, quality);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/qualityChanged", Map.of(
                "quality", quality,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        ));
    }

    private boolean shouldIgnoreAction(String roomId, String action, double currentTime, String userId) {
        String key = roomId + ":" + action;
        LastAction lastAction = lastActions.get(key);
        long currentTimestamp = System.currentTimeMillis();

        if (lastAction != null) {
            boolean sameTime = Math.abs(lastAction.currentTime - currentTime) < 1.0;
            boolean recentAction = (currentTimestamp - lastAction.timestamp) < DEBOUNCE_TIME;
            boolean sameUser = lastAction.userId.equals(userId);

            if (sameTime && recentAction && !sameUser) {
                return true;
            }
        }

        lastActions.put(key, new LastAction(action, currentTime, userId, currentTimestamp));

        cleanupOldActions(currentTimestamp);

        return false;
    }

    private void cleanupOldActions(long currentTimestamp) {
        lastActions.entrySet().removeIf(entry ->
                currentTimestamp - entry.getValue().timestamp > DEBOUNCE_TIME * 2
        );
    }

    private void updateRoomUsers(String roomId) {
        roomService.getRoomById(roomId).ifPresent(room -> {
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/users",
                    room.getUsers());
        });
    }

    @MessageMapping("/room/{roomId}/ping")
    public void handlePing(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        messagingTemplate.convertAndSendToUser(userId, "/queue/pong", Map.of(
                "timestamp", System.currentTimeMillis()
        ));
    }

    @MessageMapping("/room/{roomId}/heartbeat")
    public void handleHeartbeat(@DestinationVariable String roomId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        roomService.getRoomById(roomId).ifPresent(room -> {
            room.getUsers().stream()
                    .filter(user -> user.getId().equals(userId))
                    .findFirst()
                    .ifPresent(user -> {
                        user.setLastSeen(java.time.LocalDateTime.now());
                        user.setConnected(true);
                    });
        });
    }
}
