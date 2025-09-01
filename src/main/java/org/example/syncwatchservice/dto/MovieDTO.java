package org.example.syncwatchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieDTO {
    private Long id;
    private String title;
    private String description;
    private Integer year;
    private Integer duration;
    private String coverUrl;
    private String videoUrl;
    private Long fileSize;
}
