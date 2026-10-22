package com.codesure.backend.metrics.dto;

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
public class BusFactorAnalysisDto {
    private UUID repositoryId;
    private String repositoryName;
    private String branch;
    private int overallBusFactor;
    private String busFactorStatus; // CRITICAL_RISK, MODERATE_RISK, RESILIENT
    private double coreAuthorsConcentration;
    private int totalSiloedFiles;
    private String executiveSummary;
    private List<SiloedFileDto> highRiskSilos;
    private List<ContributorProfileDto> contributorProfiles;
    private List<String> teamRecommendations;
}
