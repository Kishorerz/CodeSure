package com.codesure.backend.risk.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RiskAssessmentResponse {
    private UUID repositoryId;
    private String repositoryName;
    private String branchName;
    private double overallRiskScore;
    private String riskLevel; // CRITICAL, HIGH, MEDIUM, LOW
    private int totalFilesChanged;
    private int totalAdditions;
    private int totalDeletions;
    private int totalFindings;
    private int criticalFindings;
    private int highFindings;
    private int mediumFindings;
    private int lowFindings;
    private List<String> keyRiskFactors;
    private String aiExplanation;
    private List<PrioritizedFindingDto> prioritizedFindings;
}
