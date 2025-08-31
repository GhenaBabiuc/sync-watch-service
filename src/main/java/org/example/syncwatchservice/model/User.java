package org.example.syncwatchservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String nickname;
    private double currentTime;
    private LocalDateTime lastUpdate;

    public User(String nickname) {
        this.nickname = nickname;
        this.currentTime = 0;
        this.lastUpdate = LocalDateTime.now();
    }

    public void updateTime(double time) {
        this.currentTime = time;
        this.lastUpdate = LocalDateTime.now();
    }
}
