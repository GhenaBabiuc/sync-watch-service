package org.example.syncwatchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieListDTO {
    private Long id;
    private String title;
    private String description;
    private Integer year;
    private Integer duration;
    private String coverUrl;
}
