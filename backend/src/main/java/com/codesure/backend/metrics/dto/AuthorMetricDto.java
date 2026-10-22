package com.codesure.backend.metrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorMetricDto {
    private String authorName;
    private int commitCount;
    private int additions;
    private int deletions;
    private int totalChurn;
    private double churnPercentage;
}
