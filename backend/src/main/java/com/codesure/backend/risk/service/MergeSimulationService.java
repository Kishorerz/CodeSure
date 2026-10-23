package com.codesure.backend.risk.service;

import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import com.codesure.backend.risk.dto.MergeSimulationDto;
import com.codesure.backend.risk.dto.PrioritizedFindingDto;
import com.codesure.backend.risk.dto.RiskAssessmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MergeSimulationService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final RiskEngineService riskEngineService;

    public MergeSimulationService(
            RepositoryJpaRepository repositoryJpaRepository,
            RiskEngineService riskEngineService
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.riskEngineService = riskEngineService;
    }

    public MergeSimulationDto simulateBranchMerge(UUID repositoryId, String sourceBranch, String targetBranch) {
        Repository repo = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        String defaultBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
        String effectiveTarget = (targetBranch != null && !targetBranch.trim().isEmpty())
                ? targetBranch.trim()
                : defaultBranch;
        String effectiveSource = (sourceBranch != null && !sourceBranch.trim().isEmpty())
                ? sourceBranch.trim()
                : defaultBranch;

        RiskAssessmentResponse sourceAssessment = riskEngineService.calculateRepositoryRisk(repositoryId, effectiveSource);
        RiskAssessmentResponse targetAssessment = riskEngineService.calculateRepositoryRisk(repositoryId, effectiveTarget);

        double sourceScore = sourceAssessment.getOverallRiskScore();
        double targetScore = targetAssessment.getOverallRiskScore();
        double deltaScore = Math.round((sourceScore - targetScore) * 10.0) / 10.0;

        int newCritical = Math.max(0, sourceAssessment.getCriticalFindings() - targetAssessment.getCriticalFindings());
        int newHigh = Math.max(0, sourceAssessment.getHighFindings() - targetAssessment.getHighFindings());
        int newTotal = Math.max(0, sourceAssessment.getTotalFindings() - targetAssessment.getTotalFindings());

        int totalChurn = sourceAssessment.getTotalAdditions() + sourceAssessment.getTotalDeletions();

        // Determine Verdict
        String verdict;
        String reason;
        List<String> blockers = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (sourceAssessment.getCriticalFindings() > 0 || newCritical > 0 || sourceScore >= 75) {
            verdict = "BLOCKED";
            reason = String.format("Merge is BLOCKED. Branch '%s' contains %d critical vulnerabilities or exceeds maximum risk tolerance (Score: %.0f/100).",
                    effectiveSource, sourceAssessment.getCriticalFindings(), sourceScore);
        } else if (sourceAssessment.getHighFindings() > 0 || deltaScore > 15.0) {
            verdict = "NEEDS_REVIEW";
            reason = String.format("Merge requires SENIOR REVIEW. Branch '%s' introduces %d high-severity findings and increases risk by +%.1f points.",
                    effectiveSource, sourceAssessment.getHighFindings(), deltaScore);
        } else {
            verdict = "SAFE_TO_MERGE";
            reason = String.format("Merge quality gate PASSED. Branch '%s' introduces zero critical or high-severity regressions compared to '%s'.",
                    effectiveSource, effectiveTarget);
        }

        // Gather Specific Blockers from Prioritized Findings
        if (sourceAssessment.getPrioritizedFindings() != null) {
            for (PrioritizedFindingDto pf : sourceAssessment.getPrioritizedFindings()) {
                if ("CRITICAL".equalsIgnoreCase(pf.getSeverity()) || "HIGH".equalsIgnoreCase(pf.getSeverity())) {
                    blockers.add(String.format("[%s] %s on %s:%d (%s)",
                            pf.getSeverity(), pf.getRuleId(),
                            pf.getFilePath() != null ? pf.getFilePath() : "file",
                            pf.getLineNumber(), pf.getMessage()));
                }
            }
        }

        if (blockers.isEmpty() && sourceScore > targetScore) {
            blockers.add(String.format("Volatile code changes on '%s' (+%d / -%d lines) without corresponding test coverage.",
                    effectiveSource, sourceAssessment.getTotalAdditions(), sourceAssessment.getTotalDeletions()));
        }

        // Recommendations
        if (sourceAssessment.getCriticalFindings() > 0) {
            recommendations.add("Apply 1-Click Auto-Fix on all Critical ranked items before opening Pull Request.");
        }
        if (newHigh > 0) {
            recommendations.add(String.format("Remediate %d high-severity security findings to bring risk delta under +15 pts.", newHigh));
        }
        if (totalChurn > 1000) {
            recommendations.add("High churn velocity detected: split large changes into modular, smaller PRs for safer code review.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Code changes passed all quality gates. Safe to merge into " + effectiveTarget + ".");
        }

        String summary = String.format(
                "Merging '%s' into '%s' results in a Risk Score of %.0f/100 (delta: %s%.1f). %d total findings detected (%d Critical, %d High). Gate verdict: %s.",
                effectiveSource, effectiveTarget, sourceScore, (deltaScore >= 0 ? "+" : ""), deltaScore,
                sourceAssessment.getTotalFindings(), sourceAssessment.getCriticalFindings(), sourceAssessment.getHighFindings(),
                verdict
        );

        return MergeSimulationDto.builder()
                .repositoryId(repositoryId)
                .repositoryName(repo.getName())
                .sourceBranch(effectiveSource)
                .targetBranch(effectiveTarget)
                .sourceRiskScore(sourceScore)
                .targetRiskScore(targetScore)
                .riskScoreDelta(deltaScore)
                .sourceRiskLevel(sourceAssessment.getRiskLevel())
                .targetRiskLevel(targetAssessment.getRiskLevel())
                .sourceCriticalFindings(sourceAssessment.getCriticalFindings())
                .targetCriticalFindings(targetAssessment.getCriticalFindings())
                .newCriticalFindings(newCritical)
                .sourceHighFindings(sourceAssessment.getHighFindings())
                .targetHighFindings(targetAssessment.getHighFindings())
                .newHighFindings(newHigh)
                .sourceTotalFindings(sourceAssessment.getTotalFindings())
                .targetTotalFindings(targetAssessment.getTotalFindings())
                .newTotalFindings(newTotal)
                .sourceAdditions(sourceAssessment.getTotalAdditions())
                .sourceDeletions(sourceAssessment.getTotalDeletions())
                .totalChurn(totalChurn)
                .mergeVerdict(verdict)
                .verdictReason(reason)
                .mergeBlockers(blockers)
                .recommendations(recommendations)
                .executiveSummary(summary)
                .build();
    }
}
