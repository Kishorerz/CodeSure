package com.codesure.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPullRequestResponse {
    private Integer number;
    private String title;
    private String state;
    
    @JsonProperty("html_url")
    private String htmlUrl;
    
    private GitHubUser user;
    
    private BranchRef head;
    private BranchRef base;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("updated_at")
    private String updatedAt;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubUser {
        private String login;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BranchRef {
        private String ref;
        private String sha;
    }
}
