package org.example.syncwatchservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    private Long id;
    private String fileType;
    private String originalFilename;
    private Long fileSize;
    private String mimeType;
    private String uploadStatus;
    private String downloadUrl;
}
