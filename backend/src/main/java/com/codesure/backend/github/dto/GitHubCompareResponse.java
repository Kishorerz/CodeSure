package com.codesure.backend.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCompareResponse {
    private String status;
    
    @JsonProperty("total_commits")
    private Integer totalCommits;
    
    private List<GitHubCommitResponse> commits;
    
    private List<GitHubDiffFile> files;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubDiffFile {
        private String sha;
        private String filename;
        private String status; // added, modified, removed, renamed
        private Integer additions;
        private Integer deletions;
        private Integer changes;
        
        @JsonProperty("previous_filename")
        private String previousFilename;
        
        private String patch;
    }
}
