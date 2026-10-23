package com.codesure.backend.github.controller;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.github.client.GitHubClient;
import com.codesure.backend.github.service.GitHubService;
import com.codesure.backend.pullrequest.entity.PullRequest;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.risk.dto.RiskAssessmentResponse;
import com.codesure.backend.risk.service.RiskEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class GitHubIntegrationController {

    private final GitHubService gitHubService;
    private final RiskEngineService riskEngineService;
    private final FindingJpaRepository findingJpaRepository;
    private final GitHubClient gitHubClient;

    public GitHubIntegrationController(
            GitHubService gitHubService,
            RiskEngineService riskEngineService,
            FindingJpaRepository findingJpaRepository,
            GitHubClient gitHubClient
    ) {
        this.gitHubService = gitHubService;
        this.riskEngineService = riskEngineService;
        this.findingJpaRepository = findingJpaRepository;
        this.gitHubClient = gitHubClient;
    }

    @PostMapping("/sync-url")
    @ResponseStatus(HttpStatus.CREATED)
    public Repository syncFromUrl(@RequestBody Map<String, String> request) {
        String url = request.get("repositoryUrl");
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("repositoryUrl is required");
        }
        return gitHubService.syncRepositoryFromUrl(url.trim());
    }

    @GetMapping("/{id}/branches")
    public List<String> getBranches(@PathVariable UUID id) {
        return gitHubService.getBranches(id);
    }

    @PostMapping("/{id}/sync-commits")
    public List<Commit> syncCommits(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return gitHubService.syncCommits(id, branch, limit);
    }

    @GetMapping("/{id}/commits")
    public List<Commit> getCommits(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch
    ) {
        return gitHubService.getRepositoryCommits(id, branch);
    }

    @PostMapping("/{id}/sync-pulls")
    public List<PullRequest> syncPullRequests(@PathVariable UUID id) {
        return gitHubService.syncPullRequests(id);
    }

    @GetMapping("/{id}/changes")
    public List<CodeChange> compareChanges(
            @PathVariable UUID id,
            @RequestParam String base,
            @RequestParam String target
    ) {
        return gitHubService.compareAndSaveDiff(id, base, target);
    }

    @GetMapping("/{id}/findings")
    public List<Finding> getFindings(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch
    ) {
        if (branch != null && !branch.trim().isEmpty()) {
            List<Commit> branchCommits = gitHubService.getRepositoryCommits(id, branch);
            if (!branchCommits.isEmpty()) {
                List<UUID> commitIds = branchCommits.stream().map(Commit::getId).toList();
                List<Finding> branchFindings = findingJpaRepository.findByCommitIdsOrderByPriorityRankAsc(commitIds);
                if (!branchFindings.isEmpty()) {
                    return branchFindings;
                }
            }
        }
        return findingJpaRepository.findByCodeChangeCommitRepositoryId(id);
    }

    @GetMapping("/{id}/risk-assessment")
    public RiskAssessmentResponse getRiskAssessment(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch
    ) {
        return riskEngineService.calculateRepositoryRisk(id, branch);
    }

    @PostMapping("/github-token")
    public Map<String, Object> setGitHubToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        gitHubClient.setGithubToken(token != null && !token.trim().isEmpty() ? token.trim() : null);
        return Map.of("success", true, "hasToken", token != null && !token.trim().isEmpty());
    }

    @GetMapping("/github-token/status")
    public Map<String, Object> getGitHubTokenStatus() {
        String token = gitHubClient.getGithubToken();
        boolean hasToken = token != null && !token.trim().isEmpty();
        return Map.of("hasToken", hasToken);
    }
}
