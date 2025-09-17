package org.example.syncwatchservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Episode {
    private Long id;
    private Long seasonId;
    private Integer episodeNumber;
    private String title;
    private String description;
    private Integer duration;
    private Integer seasonNumber;
    private Long seriesId;
    private String seriesTitle;
    private String coverUrl;
    private String streamUrl;
    private List<FileInfo> files;

    public String getFormattedDuration() {
        if (duration == null) return "0:00";
        long hours = duration / 60;
        long minutes = duration % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, 0);
        } else {
            return String.format("%d:%02d", minutes, 0);
        }
    }

    public String getCoverImageUrl() {
        if (coverUrl != null) return coverUrl;

        if (files != null) {
            return files.stream()
                    .filter(f -> "COVER".equals(f.getFileType()))
                    .map(FileInfo::getDownloadUrl)
                    .findFirst()
                    .orElse("/images/default-episode-cover.jpg");
        }

        return "/images/default-episode-cover.jpg";
    }

    public String getEpisodeTitle() {
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        return String.format("Episode %d", episodeNumber);
    }
}
