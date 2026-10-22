package com.codesure.backend.github.controller;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.repository.FindingJpaRepository;
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

    public GitHubIntegrationController(
            GitHubService gitHubService,
            RiskEngineService riskEngineService,
            FindingJpaRepository findingJpaRepository
    ) {
        this.gitHubService = gitHubService;
        this.riskEngineService = riskEngineService;
        this.findingJpaRepository = findingJpaRepository;
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

    @PostMapping("/{id}/sync-commits")
    public List<Commit> syncCommits(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "15") int limit
    ) {
        return gitHubService.syncCommits(id, limit);
    }

    @GetMapping("/{id}/commits")
    public List<Commit> getCommits(@PathVariable UUID id) {
        return gitHubService.getRepositoryCommits(id);
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
    public List<Finding> getFindings(@PathVariable UUID id) {
        return findingJpaRepository.findByCodeChangeCommitRepositoryId(id);
    }

    @GetMapping("/{id}/risk-assessment")
    public RiskAssessmentResponse getRiskAssessment(@PathVariable UUID id) {
        return riskEngineService.calculateRepositoryRisk(id);
    }
}
