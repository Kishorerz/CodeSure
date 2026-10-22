package com.codesure.backend.risk.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class PrioritizedFindingDto {
    private UUID id;
    private int priorityRank;
    private String severity;
    private String ruleId;
    private String message;
    private String filePath;
    private int lineNumber;
    private String commitSha;
    private String commitMessage;
    private String rootCause;
    private String remediation;
    private String criticalityReason;
}
