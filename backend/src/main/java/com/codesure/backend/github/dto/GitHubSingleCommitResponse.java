package com.codesure.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubSingleCommitResponse {
    private String sha;

    @JsonProperty("html_url")
    private String htmlUrl;

    private GitHubCommitResponse.CommitDetail commit;
    private GitHubCommitResponse.GitHubAuthor author;

    private CommitStats stats;

    private List<GitHubCompareResponse.GitHubDiffFile> files;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitStats {
        private Integer total;
        private Integer additions;
        private Integer deletions;
    }
}
