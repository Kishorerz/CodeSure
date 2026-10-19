package com.codesure.backend.github.client;

import com.codesure.backend.github.dto.GitHubCommitResponse;
import com.codesure.backend.github.dto.GitHubCompareResponse;
import com.codesure.backend.github.dto.GitHubPullRequestResponse;
import com.codesure.backend.github.dto.GitHubRepoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class GitHubClient {

    private final RestTemplate restTemplate;

    @Value("${codesure.github.token:}")
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

    public List<GitHubCommitResponse> getCommits(String owner, String repo, int perPage) {
        String url = String.format("https://api.github.com/repos/%s/%s/commits?per_page=%d", owner, repo, perPage);
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<List<GitHubCommitResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
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
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
        );
        return response.getBody();
    }
}
