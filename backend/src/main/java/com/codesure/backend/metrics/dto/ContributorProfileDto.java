package com.codesure.backend.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributorProfileDto {
    private String authorName;
    private int commitCount;
    private int additions;
    private int deletions;
    private int totalChurn;
    private double churnPercentage;
    private int siloedFilesCount;
    private int defectsAssociated;
    private int criticalDefectsAssociated;
    private String contributorRiskLevel; // CRITICAL_KEY_PERSON, HIGH_DEPENDENCY, BALANCED
    private String recommendation;
}
