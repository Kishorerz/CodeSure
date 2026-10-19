package com.codesure.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepoResponse {
    private String name;
    
    @JsonProperty("full_name")
    private String fullName;
    
    private String description;
    
    @JsonProperty("html_url")
    private String htmlUrl;
    
    @JsonProperty("default_branch")
    private String defaultBranch;
    
    private GitHubOwner owner;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubOwner {
        private String login;
        
        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
