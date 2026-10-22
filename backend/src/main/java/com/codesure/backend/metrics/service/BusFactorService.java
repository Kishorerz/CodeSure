package com.codesure.backend.metrics.service;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.metrics.dto.BusFactorAnalysisDto;
import com.codesure.backend.metrics.dto.ContributorProfileDto;
import com.codesure.backend.metrics.dto.SiloedFileDto;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BusFactorService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CommitJpaRepository commitJpaRepository;
    private final CodeChangeJpaRepository codeChangeJpaRepository;
    private final FindingJpaRepository findingJpaRepository;

    public BusFactorService(
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

    public BusFactorAnalysisDto analyzeBusFactor(UUID repositoryId, String branch) {
        Repository repo = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        String effectiveBranch = (branch != null && !branch.trim().isEmpty() && !branch.equalsIgnoreCase("all"))
                ? branch.trim()
                : null;

        List<Commit> commits;
        if (effectiveBranch != null) {
            commits = commitJpaRepository.findByRepositoryIdAndBranch(repositoryId, effectiveBranch);
            if (commits.isEmpty()) {
                commits = commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
            }
        } else {
            commits = commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
        }

        if (commits.isEmpty()) {
            return BusFactorAnalysisDto.builder()
                    .repositoryId(repo.getId())
                    .repositoryName(repo.getName())
                    .branch(effectiveBranch != null ? effectiveBranch : repo.getDefaultBranch())
                    .overallBusFactor(0)
                    .busFactorStatus("RESILIENT")
                    .coreAuthorsConcentration(0.0)
                    .totalSiloedFiles(0)
                    .executiveSummary("No commits found to evaluate contributor bus factor or knowledge concentration.")
                    .highRiskSilos(List.of())
                    .contributorProfiles(List.of())
                    .teamRecommendations(List.of("Ingest commits to assess team knowledge distribution."))
                    .build();
        }

        List<UUID> commitIds = commits.stream().map(Commit::getId).toList();
        List<CodeChange> codeChanges = codeChangeJpaRepository.findByCommitIdIn(commitIds);
        List<Finding> findings = findingJpaRepository.findByCommitIdsOrderByPriorityRankAsc(commitIds);

        Map<UUID, Commit> commitMap = commits.stream()
                .collect(Collectors.toMap(Commit::getId, c -> c, (a, b) -> a));

        Map<UUID, List<Finding>> findingsByChangeId = findings.stream()
                .filter(f -> f.getCodeChange() != null)
                .collect(Collectors.groupingBy(f -> f.getCodeChange().getId()));

        // Group code changes by commit
        Map<UUID, List<CodeChange>> changesByCommit = codeChanges.stream()
                .filter(c -> c.getCommit() != null)
                .collect(Collectors.groupingBy(c -> c.getCommit().getId()));

        // 1. Author Churn & Repository Bus Factor
        Map<String, Integer> authorTotalChurn = new HashMap<>();
        Map<String, Integer> authorCommitCount = new HashMap<>();
        Map<String, Integer> authorAdditions = new HashMap<>();
        Map<String, Integer> authorDeletions = new HashMap<>();

        for (Commit c : commits) {
            String author = (c.getAuthorName() != null && !c.getAuthorName().isBlank())
                    ? c.getAuthorName().trim()
                    : "Unknown";
            authorCommitCount.put(author, authorCommitCount.getOrDefault(author, 0) + 1);

            List<CodeChange> changes = changesByCommit.getOrDefault(c.getId(), List.of());
            int churn = 0;
            int adds = 0;
            int dels = 0;
            for (CodeChange cc : changes) {
                int a = cc.getAdditions() != null ? cc.getAdditions() : 0;
                int d = cc.getDeletions() != null ? cc.getDeletions() : 0;
                adds += a;
                dels += d;
                churn += (a + d);
            }
            authorAdditions.put(author, authorAdditions.getOrDefault(author, 0) + adds);
            authorDeletions.put(author, authorDeletions.getOrDefault(author, 0) + dels);
            authorTotalChurn.put(author, authorTotalChurn.getOrDefault(author, 0) + churn);
        }

        int totalRepoChurn = authorTotalChurn.values().stream().mapToInt(Integer::intValue).sum();

        // Sort authors descending by churn
        List<Map.Entry<String, Integer>> sortedAuthors = authorTotalChurn.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        // Calculate repository-wide Bus Factor (minimum authors to reach 70% churn)
        int busFactor = 0;
        int accumulatedChurn = 0;
        double targetThreshold = totalRepoChurn * 0.70;

        for (Map.Entry<String, Integer> entry : sortedAuthors) {
            busFactor++;
            accumulatedChurn += entry.getValue();
            if (accumulatedChurn >= targetThreshold || busFactor == sortedAuthors.size()) {
                break;
            }
        }
        if (busFactor == 0 && !sortedAuthors.isEmpty()) busFactor = 1;

        double coreConcentration = totalRepoChurn > 0
                ? Math.round((accumulatedChurn * 100.0 / totalRepoChurn) * 10.0) / 10.0
                : 100.0;

        String busFactorStatus;
        if (busFactor <= 1) {
            busFactorStatus = "CRITICAL_RISK";
        } else if (busFactor == 2) {
            busFactorStatus = "MODERATE_RISK";
        } else {
            busFactorStatus = "RESILIENT";
        }

        // 2. Identify File-Level Knowledge Silos
        Map<String, List<CodeChange>> changesByFile = codeChanges.stream()
                .filter(c -> c.getFilePath() != null && !c.getFilePath().isBlank())
                .collect(Collectors.groupingBy(CodeChange::getFilePath));

        List<SiloedFileDto> siloedFiles = new ArrayList<>();
        Map<String, Integer> authorSiloCount = new HashMap<>();
        Map<String, Integer> authorDefectCount = new HashMap<>();
        Map<String, Integer> authorCriticalDefectCount = new HashMap<>();

        for (Map.Entry<String, List<CodeChange>> entry : changesByFile.entrySet()) {
            String filePath = entry.getKey();
            List<CodeChange> fileChanges = entry.getValue();

            Map<String, Integer> authorEdits = new HashMap<>();
            int fileAdditions = 0;
            int fileDeletions = 0;
            int fileDefects = 0;
            int fileCritDefects = 0;

            for (CodeChange cc : fileChanges) {
                fileAdditions += (cc.getAdditions() != null ? cc.getAdditions() : 0);
                fileDeletions += (cc.getDeletions() != null ? cc.getDeletions() : 0);

                Commit commit = commitMap.get(cc.getCommit().getId());
                String author = (commit != null && commit.getAuthorName() != null && !commit.getAuthorName().isBlank())
                        ? commit.getAuthorName().trim()
                        : "Unknown";

                authorEdits.put(author, authorEdits.getOrDefault(author, 0) + 1);

                List<Finding> fList = findingsByChangeId.getOrDefault(cc.getId(), List.of());
                fileDefects += fList.size();
                for (Finding f : fList) {
                    if (f.getSeverity() == Severity.CRITICAL) fileCritDefects++;
                }

                // Associate defects to author
                authorDefectCount.put(author, authorDefectCount.getOrDefault(author, 0) + fList.size());
                if (fileCritDefects > 0) {
                    authorCriticalDefectCount.put(author, authorCriticalDefectCount.getOrDefault(author, 0) + fileCritDefects);
                }
            }

            int totalEdits = fileChanges.size();
            // Dominant author
            Map.Entry<String, Integer> dominant = authorEdits.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (dominant != null && totalEdits > 0) {
                double dominancePct = Math.round((dominant.getValue() * 100.0 / totalEdits) * 10.0) / 10.0;
                if (dominancePct >= 75.0) {
                    String author = dominant.getKey();
                    authorSiloCount.put(author, authorSiloCount.getOrDefault(author, 0) + 1);

                    String siloRisk;
                    String reco;
                    if (fileCritDefects > 0) {
                        siloRisk = "CRITICAL_SILO";
                        reco = String.format("Critical Knowledge Silo: Exclusively edited by %s (%.0f%%) with %d critical security defect(s). Immediate architectural pairing and secondary review required.",
                                author, dominancePct, fileCritDefects);
                    } else if (fileDefects > 0 || totalEdits >= 4) {
                        siloRisk = "HIGH_RISK";
                        reco = String.format("High Dependency: %s authored %.0f%% of changes. Introduce a second maintainer to mitigate key-person failure risk.",
                                author, dominancePct);
                    } else {
                        siloRisk = "MODERATE";
                        reco = String.format("Single Contributor: %s owns this file. Ensure code comments and integration tests are up to date.",
                                author);
                    }

                    siloedFiles.add(SiloedFileDto.builder()
                            .filePath(filePath)
                            .dominantAuthor(author)
                            .dominancePercentage(dominancePct)
                            .totalCommits(totalEdits)
                            .totalChurn(fileAdditions + fileDeletions)
                            .defectCount(fileDefects)
                            .criticalDefects(fileCritDefects)
                            .siloRiskLevel(siloRisk)
                            .recommendation(reco)
                            .build());
                }
            }
        }

        // Sort siloed files by severity & defects descending
        siloedFiles.sort((a, b) -> {
            int critComp = Integer.compare(b.getCriticalDefects(), a.getCriticalDefects());
            if (critComp != 0) return critComp;
            int defectComp = Integer.compare(b.getDefectCount(), a.getDefectCount());
            if (defectComp != 0) return defectComp;
            return Double.compare(b.getDominancePercentage(), a.getDominancePercentage());
        });

        // 3. Build Contributor Profiles
        List<ContributorProfileDto> profiles = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedAuthors) {
            String author = entry.getKey();
            int churn = entry.getValue();
            int commitsCount = authorCommitCount.getOrDefault(author, 0);
            int adds = authorAdditions.getOrDefault(author, 0);
            int dels = authorDeletions.getOrDefault(author, 0);
            double churnPct = totalRepoChurn > 0
                    ? Math.round((churn * 100.0 / totalRepoChurn) * 10.0) / 10.0
                    : 0.0;
            int silosOwned = authorSiloCount.getOrDefault(author, 0);
            int defects = authorDefectCount.getOrDefault(author, 0);
            int critDefects = authorCriticalDefectCount.getOrDefault(author, 0);

            String contributorRisk;
            String recommendation;
            if (silosOwned >= 3 && critDefects > 0) {
                contributorRisk = "CRITICAL_KEY_PERSON";
                recommendation = "High single-point-of-failure risk. This contributor controls major siloed modules containing critical findings. Mandate peer review and pair programming.";
            } else if (silosOwned >= 2 || churnPct >= 65.0) {
                contributorRisk = "HIGH_DEPENDENCY";
                recommendation = "Substantial code monopoly. Require secondary review on pull requests to spread architectural knowledge across the team.";
            } else {
                contributorRisk = "BALANCED";
                recommendation = "Balanced contributor profile with shared ownership and standard review workflow.";
            }

            profiles.add(ContributorProfileDto.builder()
                    .authorName(author)
                    .commitCount(commitsCount)
                    .additions(adds)
                    .deletions(dels)
                    .totalChurn(churn)
                    .churnPercentage(churnPct)
                    .siloedFilesCount(silosOwned)
                    .defectsAssociated(defects)
                    .criticalDefectsAssociated(critDefects)
                    .contributorRiskLevel(contributorRisk)
                    .recommendation(recommendation)
                    .build());
        }

        // 4. Executive Summary & Team Recommendations
        String executiveSummary;
        if (busFactor <= 1) {
            executiveSummary = String.format(
                    "Warning: Repository Bus Factor is 1. A single contributor (%s) accounts for %.1f%% of codebase churn, and %d file(s) are severe knowledge silos. If this person is unavailable, development velocity will stall.",
                    sortedAuthors.isEmpty() ? "Unknown" : sortedAuthors.get(0).getKey(),
                    coreConcentration,
                    siloedFiles.size()
            );
        } else if (busFactor == 2) {
            executiveSummary = String.format(
                    "Moderate Key-Person Risk: The repository Bus Factor is 2. The top two contributors drive %.1f%% of activity across %d siloed files. Cross-training is advised.",
                    coreConcentration,
                    siloedFiles.size()
            );
        } else {
            executiveSummary = String.format(
                    "Healthy Resilience: The repository Bus Factor is %d. Code modifications are distributed across multiple core contributors with minimal isolated silos.",
                    busFactor
            );
        }

        List<String> recommendations = new ArrayList<>();
        if (busFactor <= 1) {
            recommendations.add("Enforce branch protection requiring at least 1 mandatory approval from another teammate before merging.");
            recommendations.add("Conduct architectural knowledge-transfer workshops on identified critical silo files.");
            recommendations.add("Rotate code ownership for hotspot modules to expand reviewer familiarities.");
        } else if (busFactor == 2) {
            recommendations.add("Introduce secondary reviewers on pull requests touching high-churn modules.");
            recommendations.add("Maintain comprehensive documentation and unit tests for siloed source files.");
        } else {
            recommendations.add("Maintain balanced code review distribution across team members.");
            recommendations.add("Continue collaborative pair review on high-severity security findings.");
        }

        return BusFactorAnalysisDto.builder()
                .repositoryId(repo.getId())
                .repositoryName(repo.getName())
                .branch(effectiveBranch != null ? effectiveBranch : repo.getDefaultBranch())
                .overallBusFactor(busFactor)
                .busFactorStatus(busFactorStatus)
                .coreAuthorsConcentration(coreConcentration)
                .totalSiloedFiles(siloedFiles.size())
                .executiveSummary(executiveSummary)
                .highRiskSilos(siloedFiles.stream().limit(12).toList())
                .contributorProfiles(profiles)
                .teamRecommendations(recommendations)
                .build();
    }
}
