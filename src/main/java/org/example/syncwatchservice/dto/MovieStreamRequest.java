package org.example.syncwatchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieStreamRequest {
    private Long movieId;
    private double startTime;
}
