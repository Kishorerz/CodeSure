package com.codesure.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubBranchResponse {
    private String name;
    private Boolean isProtected;
}
