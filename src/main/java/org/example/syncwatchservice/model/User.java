package org.example.syncwatchservice.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    @EqualsAndHashCode.Include
    private String id;
    private String username;
    private double currentTime;
    private LocalDateTime joinedAt;
    private LocalDateTime lastSeen;
    private boolean isConnected;

    public User(String id, String username) {
        this.id = id;
        this.username = username;
        this.joinedAt = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
        this.isConnected = true;
        this.currentTime = 0.0;
    }

    public String getFormattedCurrentTime() {
        long totalSeconds = (long) currentTime;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
