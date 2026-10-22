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
public class HistoricalMetricsDto {
    private UUID repositoryId;
    private String repositoryName;
    private String branch;
    private ChurnWindowDto churn7Days;
    private ChurnWindowDto churn30Days;
    private ChurnWindowDto churnAllTime;
    private double repositoryStabilityIndex;
    private String stabilityAssessment;
    private List<FileHotspotDto> topHotspots;
    private List<DailyActivityDto> dailyTimeline;
    private List<AuthorMetricDto> topAuthors;
}
