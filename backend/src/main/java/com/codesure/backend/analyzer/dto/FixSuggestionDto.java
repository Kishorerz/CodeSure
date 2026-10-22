package com.codesure.backend.analyzer.dto;

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
public class FixSuggestionDto {
    private UUID findingId;
    private String ruleId;
    private String filePath;
    private int lineNumber;
    private String title;
    private String beforeSnippet;
    private String afterSnippet;
    private String unifiedDiff;
    private String explanation;
    private String confidenceLevel; // HIGH, MEDIUM
    private List<String> securityBestPractices;
}
