package org.example.syncwatchservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Season {
    private Long id;
    private Long seriesId;
    private Integer seasonNumber;
    private String title;
    private String description;
    private Integer totalEpisodes;
    private String seriesTitle;
    private List<Episode> episodes;
}
