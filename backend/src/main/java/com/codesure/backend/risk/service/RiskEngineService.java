package com.codesure.backend.risk.service;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import com.codesure.backend.risk.dto.PrioritizedFindingDto;
import com.codesure.backend.risk.dto.RiskAssessmentResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RiskEngineService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CommitJpaRepository commitJpaRepository;
    private final CodeChangeJpaRepository codeChangeJpaRepository;
    private final FindingJpaRepository findingJpaRepository;

    public RiskEngineService(
            RepositoryJpaRepository repositoryJpaRepository,
            CommitJpaRepository commitJpaRepository,
            CodeChangeJpaRepository codeChangeJpaRepository,
            FindingJpaRepository findingJpaRepository
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.commitJpaRepository = commitJpaRepository;
        this.codeChangeJpaRepository = codeChangeJpaRepository;
        this.findingJpaRepository = findingJpaRepository;
    }

    public RiskAssessmentResponse calculateRepositoryRisk(UUID repositoryId) {
        return calculateRepositoryRisk(repositoryId, null);
    }

    public RiskAssessmentResponse calculateRepositoryRisk(UUID repositoryId, String branch) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        List<Commit> commits;
        List<Finding> allFindings;
        String effectiveBranch = (branch != null && !branch.trim().isEmpty())
                ? branch.trim()
                : (repository.getDefaultBranch() != null ? repository.getDefaultBranch() : "main");

        if (branch != null && !branch.trim().isEmpty()) {
            List<Commit> branchCommits = commitJpaRepository.findByRepositoryIdAndBranch(repositoryId, branch.trim());
            if (!branchCommits.isEmpty()) {
                commits = branchCommits;
                List<UUID> commitIds = commits.stream().map(Commit::getId).toList();
                allFindings = findingJpaRepository.findByCommitIdsOrderByPriorityRankAsc(commitIds);
            } else {
                commits = commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
                allFindings = findingJpaRepository.findByCodeChangeCommitRepositoryId(repositoryId);
            }
        } else {
            commits = commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
            allFindings = findingJpaRepository.findByCodeChangeCommitRepositoryId(repositoryId);
        }

        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;

        for (Finding f : allFindings) {
            if (f.getSeverity() == Severity.CRITICAL) critical++;
            else if (f.getSeverity() == Severity.HIGH) high++;
            else if (f.getSeverity() == Severity.MEDIUM) medium++;
            else if (f.getSeverity() == Severity.LOW) low++;
        }

        int totalAdditions = 0;
        int totalDeletions = 0;
        int totalFilesChanged = 0;

        for (Commit c : commits) {
            List<CodeChange> changes = codeChangeJpaRepository.findByCommitId(c.getId());
            totalFilesChanged += changes.size();
            for (CodeChange ch : changes) {
                totalAdditions += (ch.getAdditions() != null ? ch.getAdditions() : 0);
                totalDeletions += (ch.getDeletions() != null ? ch.getDeletions() : 0);
            }
        }

        // Formula: Evidence weighted scoring
        double findingScore = (critical * 35.0) + (high * 20.0) + (medium * 8.0) + (low * 2.0);
        double churnScore = Math.min(25.0, (totalAdditions + totalDeletions) / 50.0);
        double rawScore = findingScore + churnScore;

        double finalScore = Math.min(100.0, Math.max(5.0, rawScore));
        if (allFindings.isEmpty() && totalFilesChanged == 0) {
            finalScore = 10.0;
        }

        String level;
        if (finalScore >= 80.0 || critical > 0) {
            level = "CRITICAL";
        } else if (finalScore >= 60.0 || high > 0) {
            level = "HIGH";
        } else if (finalScore >= 35.0) {
            level = "MEDIUM";
        } else {
            level = "LOW";
        }

        List<String> factors = new ArrayList<>();
        if (critical > 0) {
            factors.add(String.format("%d critical security vulnerabilities detected on branch '%s'.", critical, effectiveBranch));
        }
        if (high > 0) {
            factors.add(String.format("%d high-severity static analysis issues found on branch '%s'.", high, effectiveBranch));
        }
        if (totalAdditions + totalDeletions > 200) {
            factors.add(String.format("High code churn on branch '%s' (+%d / -%d lines across %d files).", effectiveBranch, totalAdditions, totalDeletions, totalFilesChanged));
        }
        if (commits.size() > 5) {
            factors.add(String.format("Active commit velocity on branch '%s' (%d commits analyzed).", effectiveBranch, commits.size()));
        }
        if (factors.isEmpty()) {
            factors.add(String.format("Standard development churn within safe operating thresholds on branch '%s'.", effectiveBranch));
        }

        // Sort findings by priority rank
        allFindings.sort((a, b) -> {
            int rankA = a.getPriorityRank() != null ? a.getPriorityRank() : 999;
            int rankB = b.getPriorityRank() != null ? b.getPriorityRank() : 999;
            return Integer.compare(rankA, rankB);
        });

        List<PrioritizedFindingDto> prioritizedList = new ArrayList<>();
        int currentRank = 1;
        for (Finding f : allFindings) {
            String filePath = f.getCodeChange() != null ? f.getCodeChange().getFilePath() : "source file";
            String commitSha = (f.getCodeChange() != null && f.getCodeChange().getCommit() != null)
                    ? f.getCodeChange().getCommit().getSha() : null;
            String commitMsg = (f.getCodeChange() != null && f.getCodeChange().getCommit() != null)
                    ? f.getCodeChange().getCommit().getMessage() : null;

            String criticalityReason = switch (f.getSeverity()) {
                case CRITICAL -> "Priority 1 (CRITICAL): Block merge immediately. Credentials or critical flaw exposed.";
                case HIGH -> "Priority 2 (HIGH): Exploit risk present. Fix before production deployment.";
                case MEDIUM -> "Priority 3 (MEDIUM): Architectural or configuration exposure. Schedule prompt fix.";
                case LOW -> "Priority 4 (LOW): Code quality/maintainability smell. Clean up during review.";
            };

            prioritizedList.add(PrioritizedFindingDto.builder()
                    .id(f.getId())
                    .priorityRank(currentRank++)
                    .severity(f.getSeverity().name())
                    .ruleId(f.getRuleId())
                    .message(f.getMessage())
                    .filePath(filePath)
                    .lineNumber(f.getLineNumber())
                    .commitSha(commitSha != null && commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha)
                    .commitMessage(commitMsg)
                    .rootCause(f.getRootCause() != null ? f.getRootCause() : "Potential issue flagged by static analysis pattern.")
                    .remediation(f.getRemediation() != null ? f.getRemediation() : "Review the modified line and sanitize inputs or adhere to secure coding patterns.")
                    .criticalityReason(criticalityReason)
                    .build());
        }

        // Evidence-Based Explanation (Rule 11: Explain WHY based on evidence)
        StringBuilder explanation = new StringBuilder();
        explanation.append("DevSentinel analyzed branch '").append(effectiveBranch).append("' of ")
                .append(repository.getName())
                .append(" with an evidence-backed score of ").append(String.format("%.1f", finalScore))
                .append("/100 (Risk Level: ").append(level).append(").\n\n");
        explanation.append("Key Evidence Observed:\n");
        for (String factor : factors) {
            explanation.append("• ").append(factor).append("\n");
        }

        if (!prioritizedList.isEmpty()) {
            explanation.append("\nTop Priority Action Items for Developer (Fix on '").append(effectiveBranch).append("' before merge):\n");
            int previewCount = Math.min(3, prioritizedList.size());
            for (int i = 0; i < previewCount; i++) {
                PrioritizedFindingDto item = prioritizedList.get(i);
                explanation.append(String.format("  [%d] %s (%s, line %d)\n", item.getPriorityRank(), item.getMessage(), item.getFilePath(), item.getLineNumber()));
                explanation.append(String.format("      - Why: %s\n", item.getRootCause()));
                explanation.append(String.format("      - Fix first: %s\n", item.getRemediation()));
            }
        }

        explanation.append("\nRecommended Developer Action: ");
        if ("CRITICAL".equals(level)) {
            explanation.append("Immediate triage required on '").append(effectiveBranch).append("'. Block merge into main until critical findings are remediated.");
        } else if ("HIGH".equals(level)) {
            explanation.append("Review high-severity security findings on '").append(effectiveBranch).append("' before approving PR or deploying.");
        } else if ("MEDIUM".equals(level)) {
            explanation.append("Ensure adequate unit test coverage for changed modules and review flagged code smells on '").append(effectiveBranch).append("'.");
        } else {
            explanation.append("Low change risk detected on '").append(effectiveBranch).append("'. Safe to proceed with standard code review.");
        }

        return RiskAssessmentResponse.builder()
                .repositoryId(repositoryId)
                .repositoryName(repository.getName())
                .branchName(effectiveBranch)
                .overallRiskScore(Math.round(finalScore * 10.0) / 10.0)
                .riskLevel(level)
                .totalFilesChanged(totalFilesChanged)
                .totalAdditions(totalAdditions)
                .totalDeletions(totalDeletions)
                .totalFindings(allFindings.size())
                .criticalFindings(critical)
                .highFindings(high)
                .mediumFindings(medium)
                .lowFindings(low)
                .keyRiskFactors(factors)
                .aiExplanation(explanation.toString())
                .prioritizedFindings(prioritizedList)
                .build();
    }
}
