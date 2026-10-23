package com.codesure.backend.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeSimulationDto {
    private UUID repositoryId;
    private String repositoryName;
    private String sourceBranch;
    private String targetBranch;

    private double sourceRiskScore;
    private double targetRiskScore;
    private double riskScoreDelta;
    private String sourceRiskLevel;
    private String targetRiskLevel;

    private int sourceCriticalFindings;
    private int targetCriticalFindings;
    private int newCriticalFindings;

    private int sourceHighFindings;
    private int targetHighFindings;
    private int newHighFindings;

    private int sourceTotalFindings;
    private int targetTotalFindings;
    private int newTotalFindings;

    private int sourceAdditions;
    private int sourceDeletions;
    private int totalChurn;

    private String mergeVerdict; // "BLOCKED", "NEEDS_REVIEW", "SAFE_TO_MERGE"
    private String verdictReason;
    private List<String> mergeBlockers;
    private List<String> recommendations;
    private String executiveSummary;
}
