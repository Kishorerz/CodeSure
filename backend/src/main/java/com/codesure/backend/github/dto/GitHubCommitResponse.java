package com.codesure.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCommitResponse {
    private String sha;
    
    @JsonProperty("html_url")
    private String htmlUrl;
    
    private CommitDetail commit;
    private GitHubAuthor author;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitDetail {
        private String message;
        private GitAuthor author;
        private GitAuthor committer;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitAuthor {
        private String name;
        private String email;
        private String date;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubAuthor {
        private String login;
        
        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
