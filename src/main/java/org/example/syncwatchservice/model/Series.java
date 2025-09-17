
package org.example.syncwatchservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Series {
    private Long id;
    private String title;
    private String description;
    private Integer year;
    private Integer totalSeasons;
    private Integer totalEpisodes;
    private List<Season> seasons;

    public String getCoverImageUrl() {
        return "/images/default-series-cover.jpg";
    }
}
