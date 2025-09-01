package org.example.syncwatchservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
public class Room {
    private String id;
    private Long movieId;
    private double time;
    private boolean playing;
    private Map<String, User> users = new ConcurrentHashMap<>();
    private Map<String, LastCommand> lastCommands = new ConcurrentHashMap<>();
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;

    public Room(String id) {
        this.id = id;
        this.movieId = null;
        this.time = 0;
        this.playing = false;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    public int getUserCount() {
        return users.size();
    }

    @Data
    @NoArgsConstructor
    public static class LastCommand {
        private Long lastPlay;
        private Long lastPause;
        private Long lastSeek;

        public void updateCommand(String command) {
            long now = System.currentTimeMillis();
            switch (command) {
                case "play" -> lastPlay = now;
                case "pause" -> lastPause = now;
                case "seek" -> lastSeek = now;
            }
        }

        public boolean canExecuteCommand(String command, int debounceTime) {
            long now = System.currentTimeMillis();
            Long lastTime = switch (command) {
                case "play" -> lastPlay;
                case "pause" -> lastPause;
                case "seek" -> lastSeek;
                default -> null;
            };

            return lastTime == null || (now - lastTime) >= debounceTime;
        }
    }
}
