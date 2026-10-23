package com.codesure.backend.metrics.service;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.metrics.dto.*;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HistoricalMetricsService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CommitJpaRepository commitJpaRepository;
    private final CodeChangeJpaRepository codeChangeJpaRepository;
    private final FindingJpaRepository findingJpaRepository;

    public HistoricalMetricsService(
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

    public HistoricalMetricsDto calculateHistoricalMetrics(UUID repositoryId, String branch) {
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
            return HistoricalMetricsDto.builder()
                    .repositoryId(repo.getId())
                    .repositoryName(repo.getName())
                    .branch(effectiveBranch != null ? effectiveBranch : repo.getDefaultBranch())
                    .churn7Days(new ChurnWindowDto("7 Days", 0, 0, 0, 0, 0))
                    .churn30Days(new ChurnWindowDto("30 Days", 0, 0, 0, 0, 0))
                    .churnAllTime(new ChurnWindowDto("All Time", 0, 0, 0, 0, 0))
                    .repositoryStabilityIndex(100.0)
                    .stabilityAssessment("Robust & Low Volatility")
                    .topHotspots(List.of())
                    .dailyTimeline(List.of())
                    .topAuthors(List.of())
                    .build();
        }

        List<UUID> commitIds = commits.stream().map(Commit::getId).toList();
        List<CodeChange> codeChanges = codeChangeJpaRepository.findByCommitIdIn(commitIds);
        List<Finding> findings = findingJpaRepository.findByCommitIdsOrderByPriorityRankAsc(commitIds);
        if (findings.isEmpty()) {
            findings = findingJpaRepository.findByCodeChangeCommitRepositoryId(repositoryId);
        }

        // Map commit -> code changes
        Map<UUID, List<CodeChange>> changesByCommit = codeChanges.stream()
                .filter(c -> c.getCommit() != null)
                .collect(Collectors.groupingBy(c -> c.getCommit().getId()));

        // Map code change ID -> findings
        Map<UUID, List<Finding>> findingsByChangeId = findings.stream()
                .filter(f -> f.getCodeChange() != null)
                .collect(Collectors.groupingBy(f -> f.getCodeChange().getId()));

        // Determine anchor time (allows analyzing older or imported repositories realistically)
        LocalDateTime anchorTime = commits.stream()
                .map(Commit::getCommittedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime cutoff7d = anchorTime.minusDays(7);
        LocalDateTime cutoff30d = anchorTime.minusDays(30);

        List<Commit> commits7d = commits.stream()
                .filter(c -> c.getCommittedAt() != null && !c.getCommittedAt().isBefore(cutoff7d))
                .toList();

        List<Commit> commits30d = commits.stream()
                .filter(c -> c.getCommittedAt() != null && !c.getCommittedAt().isBefore(cutoff30d))
                .toList();

        ChurnWindowDto churn7 = buildChurnWindow("7 Days", commits7d, changesByCommit);
        ChurnWindowDto churn30 = buildChurnWindow("30 Days", commits30d, changesByCommit);
        ChurnWindowDto churnAll = buildChurnWindow("All Time", commits, changesByCommit);

        // File Hotspots Detection
        List<FileHotspotDto> hotspots = detectHotspots(codeChanges, findingsByChangeId);

        // Daily Activity Timeline
        List<DailyActivityDto> dailyTimeline = buildDailyTimeline(commits, changesByCommit);

        // Contributor Metrics
        List<AuthorMetricDto> topAuthors = buildAuthorMetrics(commits, changesByCommit, churnAll.getTotalChurn());

        // Stability Index Calculation
        int totalCritical = (int) findings.stream().filter(f -> f.getSeverity() == Severity.CRITICAL).count();
        int totalHigh = (int) findings.stream().filter(f -> f.getSeverity() == Severity.HIGH).count();
        int totalMedium = (int) findings.stream().filter(f -> f.getSeverity() == Severity.MEDIUM).count();

        double defectPenalty = Math.min(50.0, (totalCritical * 12.0) + (totalHigh * 6.0) + (totalMedium * 2.0));
        double churnPenalty = 0.0;
        if (churn30.getTotalChurn() > 0) {
            double ratio = (double) churn7.getTotalChurn() / churn30.getTotalChurn();
            if (ratio > 0.6) {
                churnPenalty = Math.min(25.0, (ratio - 0.5) * 45.0);
            }
        }

        double stabilityIndex = Math.max(10.0, Math.min(100.0, Math.round((100.0 - defectPenalty - churnPenalty) * 10.0) / 10.0));
        String stabilityAssessment;
        if (stabilityIndex >= 80.0) {
            stabilityAssessment = "Robust & Low Volatility";
        } else if (stabilityIndex >= 60.0) {
            stabilityAssessment = "Moderate Stability - Normal Evolution";
        } else if (stabilityIndex >= 40.0) {
            stabilityAssessment = "Elevated Churn & Defect Accumulation";
        } else {
            stabilityAssessment = "Critical Volatility - Hotspot Remediation Required";
        }

        return HistoricalMetricsDto.builder()
                .repositoryId(repo.getId())
                .repositoryName(repo.getName())
                .branch(effectiveBranch != null ? effectiveBranch : repo.getDefaultBranch())
                .churn7Days(churn7)
                .churn30Days(churn30)
                .churnAllTime(churnAll)
                .repositoryStabilityIndex(stabilityIndex)
                .stabilityAssessment(stabilityAssessment)
                .topHotspots(hotspots)
                .dailyTimeline(dailyTimeline)
                .topAuthors(topAuthors)
                .build();
    }

    private ChurnWindowDto buildChurnWindow(
            String windowName,
            List<Commit> windowCommits,
            Map<UUID, List<CodeChange>> changesByCommit
    ) {
        int commitCount = windowCommits.size();
        int additions = 0;
        int deletions = 0;
        Set<String> authors = new HashSet<>();

        for (Commit c : windowCommits) {
            if (c.getAuthorName() != null && !c.getAuthorName().isBlank()) {
                authors.add(c.getAuthorName().trim());
            }
            List<CodeChange> changes = changesByCommit.getOrDefault(c.getId(), List.of());
            for (CodeChange cc : changes) {
                additions += (cc.getAdditions() != null ? cc.getAdditions() : 0);
                deletions += (cc.getDeletions() != null ? cc.getDeletions() : 0);
            }
        }

        return ChurnWindowDto.builder()
                .windowName(windowName)
                .commitCount(commitCount)
                .additions(additions)
                .deletions(deletions)
                .totalChurn(additions + deletions)
                .activeAuthors(authors.size())
                .build();
    }

    private List<FileHotspotDto> detectHotspots(
            List<CodeChange> codeChanges,
            Map<UUID, List<Finding>> findingsByChangeId
    ) {
        Map<String, List<CodeChange>> changesByFile = codeChanges.stream()
                .filter(c -> c.getFilePath() != null && !c.getFilePath().isBlank())
                .collect(Collectors.groupingBy(CodeChange::getFilePath));

        List<FileHotspotDto> result = new ArrayList<>();

        for (Map.Entry<String, List<CodeChange>> entry : changesByFile.entrySet()) {
            String filePath = entry.getKey();
            List<CodeChange> fileChanges = entry.getValue();

            int changeCount = fileChanges.size();
            int additions = 0;
            int deletions = 0;
            int crit = 0;
            int high = 0;
            int med = 0;
            int low = 0;
            int totalFindings = 0;

            for (CodeChange cc : fileChanges) {
                additions += (cc.getAdditions() != null ? cc.getAdditions() : 0);
                deletions += (cc.getDeletions() != null ? cc.getDeletions() : 0);

                List<Finding> fList = findingsByChangeId.getOrDefault(cc.getId(), List.of());
                totalFindings += fList.size();
                for (Finding f : fList) {
                    if (f.getSeverity() == Severity.CRITICAL) crit++;
                    else if (f.getSeverity() == Severity.HIGH) high++;
                    else if (f.getSeverity() == Severity.MEDIUM) med++;
                    else if (f.getSeverity() == Severity.LOW) low++;
                }
            }

            int totalChurn = additions + deletions;
            double churnFactor = (changeCount * 4.0) + Math.min(25.0, Math.log1p(totalChurn) * 3.0);
            double defectFactor = (crit * 25.0) + (high * 15.0) + (med * 7.0) + (low * 2.0);
            double hotspotScore = Math.round((churnFactor + defectFactor) * 10.0) / 10.0;

            String riskLevel;
            String recommendation;
            if (hotspotScore >= 60.0 || crit > 0) {
                riskLevel = "CRITICAL";
                recommendation = "Critical Hotspot: High churn combined with critical security vulnerabilities. Immediate refactoring and static review advised.";
            } else if (hotspotScore >= 35.0 || high > 0) {
                riskLevel = "HIGH";
                recommendation = "High Defect Density: Significant findings detected across recurring modifications. Add automated regression tests.";
            } else if (hotspotScore >= 15.0 || totalFindings > 0) {
                riskLevel = "MEDIUM";
                recommendation = "Moderate Churn: Regular changes detected. Validate edge cases and maintain clean test coverage.";
            } else {
                riskLevel = "LOW";
                recommendation = "Stable: Low modification frequency and acceptable code quality profile.";
            }

            result.add(FileHotspotDto.builder()
                    .filePath(filePath)
                    .changeCount(changeCount)
                    .additions(additions)
                    .deletions(deletions)
                    .totalChurn(totalChurn)
                    .findingsCount(totalFindings)
                    .criticalFindings(crit)
                    .highFindings(high)
                    .mediumFindings(med)
                    .lowFindings(low)
                    .hotspotScore(hotspotScore)
                    .riskLevel(riskLevel)
                    .recommendation(recommendation)
                    .build());
        }

        result.sort((a, b) -> Double.compare(b.getHotspotScore(), a.getHotspotScore()));
        return result.stream().limit(15).toList();
    }

    private List<DailyActivityDto> buildDailyTimeline(
            List<Commit> commits,
            Map<UUID, List<CodeChange>> changesByCommit
    ) {
        Map<LocalDate, List<Commit>> byDate = commits.stream()
                .filter(c -> c.getCommittedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCommittedAt().toLocalDate()));

        List<DailyActivityDto> list = new ArrayList<>();

        for (Map.Entry<LocalDate, List<Commit>> entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<Commit> dayCommits = entry.getValue();

            int additions = 0;
            int deletions = 0;
            for (Commit c : dayCommits) {
                List<CodeChange> changes = changesByCommit.getOrDefault(c.getId(), List.of());
                for (CodeChange cc : changes) {
                    additions += (cc.getAdditions() != null ? cc.getAdditions() : 0);
                    deletions += (cc.getDeletions() != null ? cc.getDeletions() : 0);
                }
            }

            list.add(DailyActivityDto.builder()
                    .date(date.toString())
                    .commitCount(dayCommits.size())
                    .additions(additions)
                    .deletions(deletions)
                    .totalChurn(additions + deletions)
                    .build());
        }

        // Sort chronologically ascending
        list.sort(Comparator.comparing(DailyActivityDto::getDate));
        if (list.size() > 30) {
            return list.subList(list.size() - 30, list.size());
        }
        return list;
    }

    private List<AuthorMetricDto> buildAuthorMetrics(
            List<Commit> commits,
            Map<UUID, List<CodeChange>> changesByCommit,
            int totalRepoChurn
    ) {
        Map<String, List<Commit>> byAuthor = commits.stream()
                .collect(Collectors.groupingBy(c -> (c.getAuthorName() != null && !c.getAuthorName().isBlank())
                        ? c.getAuthorName().trim()
                        : "Unknown"));

        List<AuthorMetricDto> list = new ArrayList<>();

        for (Map.Entry<String, List<Commit>> entry : byAuthor.entrySet()) {
            String author = entry.getKey();
            List<Commit> authorCommits = entry.getValue();

            int additions = 0;
            int deletions = 0;
            for (Commit c : authorCommits) {
                List<CodeChange> changes = changesByCommit.getOrDefault(c.getId(), List.of());
                for (CodeChange cc : changes) {
                    additions += (cc.getAdditions() != null ? cc.getAdditions() : 0);
                    deletions += (cc.getDeletions() != null ? cc.getDeletions() : 0);
                }
            }

            int churn = additions + deletions;
            double churnPct = totalRepoChurn > 0
                    ? Math.round(((double) churn * 100.0 / totalRepoChurn) * 10.0) / 10.0
                    : 0.0;

            list.add(AuthorMetricDto.builder()
                    .authorName(author)
                    .commitCount(authorCommits.size())
                    .additions(additions)
                    .deletions(deletions)
                    .totalChurn(churn)
                    .churnPercentage(churnPct)
                    .build());
        }

        list.sort((a, b) -> Integer.compare(b.getTotalChurn(), a.getTotalChurn()));
        return list.stream().limit(10).toList();
    }
}
