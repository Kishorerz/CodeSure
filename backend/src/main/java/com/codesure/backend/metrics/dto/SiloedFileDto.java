package com.codesure.backend.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiloedFileDto {
    private String filePath;
    private String dominantAuthor;
    private double dominancePercentage;
    private int totalCommits;
    private int totalChurn;
    private int defectCount;
    private int criticalDefects;
    private String siloRiskLevel; // CRITICAL_SILO, HIGH_RISK, MODERATE
    private String recommendation;
}
