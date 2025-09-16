package org.example.syncwatchservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Movie {
    private String id;
    private String title;
    private String filePath;
    private String thumbnail;
    private long duration; // in seconds
    private String format;

    public Movie(String id, String title, String filePath) {
        this.id = id;
        this.title = title;
        this.filePath = filePath;
    }

    public String getFormattedDuration() {
        long hours = duration / 3600;
        long minutes = (duration % 3600) / 60;
        if (hours > 0) {
            return String.format("%d:%02d:00", hours, minutes);
        } else {
            return String.format("%d:00", minutes);
        }
    }
}