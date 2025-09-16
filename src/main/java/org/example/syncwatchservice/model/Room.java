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
    private Movie movie;
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
}