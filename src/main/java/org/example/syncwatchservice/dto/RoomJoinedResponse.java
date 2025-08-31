package org.example.syncwatchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomJoinedResponse {
    private String roomId;
    private String video;
    private double time;
    private boolean playing;
}
