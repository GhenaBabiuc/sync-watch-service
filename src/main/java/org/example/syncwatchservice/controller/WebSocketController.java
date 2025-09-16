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
        // Update time for all users in the room to sync them
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
        // Update time for all users in the room to sync them
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
        // Update time for all users in the room to sync them
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
                            "lastActionUserId", room.getLastActionUserId() != null ? room.getLastActionUserId() : ""
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
}