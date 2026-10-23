package com.codesure.backend.github.service;

import com.codesure.backend.analyzer.dto.FixSuggestionDto;
import com.codesure.backend.analyzer.service.RemediationFixService;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.github.client.GitHubClient;
import com.codesure.backend.github.dto.CreatePrResultDto;
import com.codesure.backend.repository.entity.Repository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GitHubPrService {

    private final FindingJpaRepository findingJpaRepository;
    private final RemediationFixService remediationFixService;
    private final GitHubClient gitHubClient;
    private final RestTemplate restTemplate = new RestTemplate();

    public GitHubPrService(
            FindingJpaRepository findingJpaRepository,
            RemediationFixService remediationFixService,
            GitHubClient gitHubClient
    ) {
        this.findingJpaRepository = findingJpaRepository;
        this.remediationFixService = remediationFixService;
        this.gitHubClient = gitHubClient;
    }

    public CreatePrResultDto createFixPullRequest(UUID findingId, String targetBranch, String userToken) {
        Finding finding = findingJpaRepository.findById(findingId)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + findingId));

        Repository repo = null;
        if (finding.getCodeChange() != null && finding.getCodeChange().getCommit() != null) {
            repo = finding.getCodeChange().getCommit().getRepository();
        }

        if (repo == null) {
            return CreatePrResultDto.builder()
                    .success(false)
                    .message("Associated repository not found for finding " + findingId)
                    .build();
        }

        String token = (userToken != null && !userToken.trim().isEmpty())
                ? userToken.trim()
                : gitHubClient.getGithubToken();

        if (token == null || token.trim().isEmpty()) {
            return CreatePrResultDto.builder()
                    .success(false)
                    .message("GitHub Personal Access Token is required to create a Pull Request. Please add your token in the top bar.")
                    .build();
        }

        String[] ownerRepo = parseOwnerAndRepo(repo.getRepositoryUrl());
        String owner = ownerRepo[0];
        String repoName = ownerRepo[1];

        String baseBranch = (targetBranch != null && !targetBranch.trim().isEmpty())
                ? targetBranch.trim()
                : (repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main");

        FixSuggestionDto fix = remediationFixService.generateFixSuggestion(findingId);
        String ruleSlug = finding.getRuleId() != null
                ? finding.getRuleId().replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase()
                : "security-fix";
        String uniqueId = UUID.randomUUID().toString().substring(0, 6);
        String newBranchName = "codesure/fix-" + ruleSlug + "-" + uniqueId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("Authorization", "Bearer " + token);
        headers.set("User-Agent", "CodeSure-Platform");

        try {
            // 1. Get base branch latest commit SHA
            String refUrl = String.format("https://api.github.com/repos/%s/%s/git/ref/heads/%s", owner, repoName, baseBranch);
            ResponseEntity<Map> refRes = restTemplate.exchange(refUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map refBody = refRes.getBody();
            if (refBody == null || !refBody.containsKey("object")) {
                return CreatePrResultDto.builder()
                        .success(false)
                        .message("Could not resolve base branch '" + baseBranch + "' on GitHub repository.")
                        .build();
            }
            Map objectMap = (Map) refBody.get("object");
            String baseSha = (String) objectMap.get("sha");

            // 2. Create the new fix branch
            String createRefUrl = String.format("https://api.github.com/repos/%s/%s/git/refs", owner, repoName);
            Map<String, Object> createRefPayload = Map.of(
                    "ref", "refs/heads/" + newBranchName,
                    "sha", baseSha
            );
            restTemplate.exchange(createRefUrl, HttpMethod.POST, new HttpEntity<>(createRefPayload, headers), Map.class);

            // 3. Get existing file content & SHA from base branch
            String filePath = fix.getFilePath();
            String contentUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                    owner, repoName, filePath, baseBranch);
            
            String fileSha = null;
            String originalFileContent = "";
            try {
                ResponseEntity<Map> fileRes = restTemplate.exchange(contentUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (fileRes.getBody() != null) {
                    fileSha = (String) fileRes.getBody().get("sha");
                    String encoded = (String) fileRes.getBody().get("content");
                    if (encoded != null) {
                        originalFileContent = new String(Base64.getMimeDecoder().decode(encoded.replaceAll("\\s", "")), StandardCharsets.UTF_8);
                    }
                }
            } catch (HttpClientErrorException.NotFound ignored) {
                // File does not exist remotely, we will create it fresh with the remediated snippet
            }

            // 4. Update the content with the remediation fix
            String updatedContent;
            if (!originalFileContent.isEmpty()) {
                // Replace snippet or prepend safe fix
                if (fix.getBeforeSnippet() != null && originalFileContent.contains(fix.getBeforeSnippet())) {
                    updatedContent = originalFileContent.replace(fix.getBeforeSnippet(), fix.getAfterSnippet());
                } else {
                    // Safe comment insertion at top of file
                    updatedContent = "// [CodeSure Automated Remediation Applied for " + finding.getRuleId() + "]\n" +
                            fix.getAfterSnippet() + "\n\n" + originalFileContent;
                }
            } else {
                updatedContent = "// [CodeSure Automated Remediation for " + finding.getRuleId() + "]\n" + fix.getAfterSnippet();
            }

            String base64NewContent = Base64.getEncoder().encodeToString(updatedContent.getBytes(StandardCharsets.UTF_8));

            // Commit the updated file to the new branch
            String updateFileUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s", owner, repoName, filePath);
            Map<String, Object> updatePayload = new HashMap<>();
            updatePayload.put("message", "fix(security): auto-remediate " + finding.getRuleId() + " via CodeSure");
            updatePayload.put("content", base64NewContent);
            updatePayload.put("branch", newBranchName);
            if (fileSha != null) {
                updatePayload.put("sha", fileSha);
            }
            restTemplate.exchange(updateFileUrl, HttpMethod.PUT, new HttpEntity<>(updatePayload, headers), Map.class);

            // 5. Open the Pull Request
            String prsUrl = String.format("https://api.github.com/repos/%s/%s/pulls", owner, repoName);
            String prTitle = "[CodeSure Auto-Fix] " + fix.getTitle();
            String prBody = buildPrBodyMarkdown(finding, fix, baseBranch, newBranchName);

            Map<String, Object> prPayload = Map.of(
                    "title", prTitle,
                    "head", newBranchName,
                    "base", baseBranch,
                    "body", prBody
            );

            ResponseEntity<Map> prRes = restTemplate.exchange(prsUrl, HttpMethod.POST, new HttpEntity<>(prPayload, headers), Map.class);
            Map prData = prRes.getBody();

            String prUrl = prData != null ? (String) prData.get("html_url") : "";
            int prNumber = prData != null && prData.containsKey("number") ? ((Number) prData.get("number")).intValue() : 0;

            return CreatePrResultDto.builder()
                    .success(true)
                    .prUrl(prUrl)
                    .prNumber(prNumber)
                    .branchName(newBranchName)
                    .targetBranch(baseBranch)
                    .title(prTitle)
                    .message("Pull Request #" + prNumber + " successfully created on GitHub!")
                    .build();

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            return CreatePrResultDto.builder()
                    .success(false)
                    .message("GitHub authorization failed (403/401). Ensure your Personal Access Token has 'repo' (write) scope.")
                    .build();
        } catch (Exception e) {
            return CreatePrResultDto.builder()
                    .success(false)
                    .message("Failed to create Pull Request: " + e.getMessage())
                    .build();
        }
    }

    private String buildPrBodyMarkdown(Finding finding, FixSuggestionDto fix, String baseBranch, String branchName) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🛡️ CodeSure Automated Remediation\n\n");
        sb.append("This Pull Request was generated autonomously by **CodeSure Risk Intelligence Platform**.\n\n");
        sb.append("### 🚨 Vulnerability Summary\n");
        sb.append("- **Rule ID**: `").append(finding.getRuleId()).append("`\n");
        sb.append("- **Severity**: **").append(finding.getSeverity()).append("**\n");
        sb.append("- **Target File**: `").append(fix.getFilePath()).append(":").append(fix.getLineNumber()).append("`\n");
        sb.append("- **Target Base Branch**: `").append(baseBranch).append("`\n\n");

        sb.append("### 🔍 Root Cause Analysis\n");
        sb.append(fix.getExplanation()).append("\n\n");

        sb.append("### 📋 Applied Git Patch\n");
        sb.append("```diff\n").append(fix.getUnifiedDiff()).append("\n```\n\n");

        if (fix.getSecurityBestPractices() != null && !fix.getSecurityBestPractices().isEmpty()) {
            sb.append("### 🛡️ Prescriptive Security Checklist\n");
            for (String bp : fix.getSecurityBestPractices()) {
                sb.append("- [ ] ").append(bp).append("\n");
            }
        }
        sb.append("\n---\n*Created by [CodeSure](https://github.com/Kishorerz/CodeSure) — Automated Developer Risk & Remediation Engine.*");
        return sb.toString();
    }

    public static String[] parseOwnerAndRepo(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new String[]{"owner", "repo"};
        }
        String clean = url.trim().replaceAll("\\.git$", "");
        if (clean.contains("github.com/")) {
            String path = clean.substring(clean.indexOf("github.com/") + "github.com/".length());
            String[] parts = path.split("/");
            if (parts.length >= 2) {
                return new String[]{parts[0], parts[1]};
            }
        }
        return new String[]{"owner", "repo"};
    }
}
