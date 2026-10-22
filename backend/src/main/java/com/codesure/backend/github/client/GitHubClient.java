package com.codesure.backend.github.client;

import com.codesure.backend.github.dto.GitHubBranchResponse;
import com.codesure.backend.github.dto.GitHubCommitResponse;
import com.codesure.backend.github.dto.GitHubCompareResponse;
import com.codesure.backend.github.dto.GitHubPullRequestResponse;
import com.codesure.backend.github.dto.GitHubRepoResponse;
import com.codesure.backend.github.dto.GitHubSingleCommitResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final RestTemplate restTemplate;

    @Value("${codesure.github.token:${GITHUB_TOKEN:}}")
    private String githubToken;

    public GitHubClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "CodeSure-Risk-Platform");
        if (githubToken != null && !githubToken.trim().isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken.trim());
        }
        return headers;
    }

    public GitHubRepoResponse getRepository(String owner, String repo) {
        String url = String.format("https://api.github.com/repos/%s/%s", owner, repo);
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<GitHubRepoResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GitHubRepoResponse.class
        );
        return response.getBody();
    }

    public List<GitHubBranchResponse> getBranches(String owner, String repo) {
        String url = String.format("https://api.github.com/repos/%s/%s/branches?per_page=100", owner, repo);
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        try {
            ResponseEntity<Map[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map[].class
            );
            List<GitHubBranchResponse> result = new ArrayList<>();
            if (response.getBody() != null) {
                for (Map item : response.getBody()) {
                    if (item.containsKey("name") && item.get("name") != null) {
                        GitHubBranchResponse b = new GitHubBranchResponse();
                        b.setName(item.get("name").toString());
                        result.add(b);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("GitHub getBranches notice: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<GitHubCommitResponse> getCommits(String owner, String repo, int perPage) {
        return getCommits(owner, repo, null, 1, perPage);
    }

    public List<GitHubCommitResponse> getCommits(String owner, String repo, int page, int perPage) {
        return getCommits(owner, repo, null, page, perPage);
    }

    public List<GitHubCommitResponse> getCommits(String owner, String repo, String branch, int page, int perPage) {
        String url;
        if (branch != null && !branch.trim().isEmpty()) {
            url = String.format("https://api.github.com/repos/%s/%s/commits?sha=%s&page=%d&per_page=%d",
                    owner, repo, branch.trim(), page, perPage);
        } else {
            url = String.format("https://api.github.com/repos/%s/%s/commits?page=%d&per_page=%d",
                    owner, repo, page, perPage);
        }
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<List<GitHubCommitResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GitHubCommitResponse>>() {}
        );
        return response.getBody();
    }

    public List<GitHubCommitResponse> getAllCommitsUpTo(String owner, String repo, int maxCommits) {
        return getAllCommitsUpTo(owner, repo, null, maxCommits);
    }

    public List<GitHubCommitResponse> getAllCommitsUpTo(String owner, String repo, String branch, int maxCommits) {
        List<GitHubCommitResponse> all = new ArrayList<>();
        int page = 1;
        int perPage = Math.min(100, Math.max(10, maxCommits));
        
        while (all.size() < maxCommits) {
            try {
                List<GitHubCommitResponse> batch = getCommits(owner, repo, branch, page, perPage);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                all.addAll(batch);
                if (batch.size() < perPage || all.size() >= maxCommits) {
                    break;
                }
                page++;
            } catch (Exception e) {
                System.err.println("Notice: stopped commit pagination at page " + page + ": " + e.getMessage());
                break;
            }
        }
        return all;
    }

    public GitHubSingleCommitResponse getCommitDetails(String owner, String repo, String sha) {
        String url = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<GitHubSingleCommitResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GitHubSingleCommitResponse.class
        );
        return response.getBody();
    }

    public GitHubCompareResponse compareCommits(String owner, String repo, String baseSha, String headSha) {
        String url = String.format("https://api.github.com/repos/%s/%s/compare/%s...%s", owner, repo, baseSha, headSha);
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<GitHubCompareResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GitHubCompareResponse.class
        );
        return response.getBody();
    }

    public List<GitHubPullRequestResponse> getPullRequests(String owner, String repo, String state) {
        String url = String.format("https://api.github.com/repos/%s/%s/pulls?state=%s", owner, repo, state);
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<List<GitHubPullRequestResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GitHubPullRequestResponse>>() {}
        );
        return response.getBody();
    }
}
