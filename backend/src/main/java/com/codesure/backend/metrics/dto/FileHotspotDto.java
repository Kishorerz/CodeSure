package com.codesure.backend.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileHotspotDto {
    private String filePath;
    private int changeCount;
    private int additions;
    private int deletions;
    private int totalChurn;
    private int findingsCount;
    private int criticalFindings;
    private int highFindings;
    private int mediumFindings;
    private int lowFindings;
    private double hotspotScore;
    private String riskLevel; // CRITICAL, HIGH, MEDIUM, LOW
    private String recommendation;
}
