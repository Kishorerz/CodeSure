package com.codesure.backend.github.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrResultDto {
    private boolean success;
    private String prUrl;
    private int prNumber;
    private String branchName;
    private String targetBranch;
    private String message;
    private String title;
}
