package com.codesure.backend.github.service;

import com.codesure.backend.analyzer.service.CodeAnalyzerService;
import com.codesure.backend.codechange.entity.ChangeType;
import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.github.client.GitHubClient;
import com.codesure.backend.github.dto.*;
import com.codesure.backend.pullrequest.entity.PullRequest;
import com.codesure.backend.pullrequest.entity.PullRequestState;
import com.codesure.backend.pullrequest.repository.PullRequestJpaRepository;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubService {

    private final GitHubClient gitHubClient;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CommitJpaRepository commitJpaRepository;
    private final CodeChangeJpaRepository codeChangeJpaRepository;
    private final PullRequestJpaRepository pullRequestJpaRepository;
    private final CodeAnalyzerService codeAnalyzerService;

    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/.*)?$");

    public GitHubService(
            GitHubClient gitHubClient,
            RepositoryJpaRepository repositoryJpaRepository,
            CommitJpaRepository commitJpaRepository,
            CodeChangeJpaRepository codeChangeJpaRepository,
            PullRequestJpaRepository pullRequestJpaRepository,
            CodeAnalyzerService codeAnalyzerService
    ) {
        this.gitHubClient = gitHubClient;
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.commitJpaRepository = commitJpaRepository;
        this.codeChangeJpaRepository = codeChangeJpaRepository;
        this.pullRequestJpaRepository = pullRequestJpaRepository;
        this.codeAnalyzerService = codeAnalyzerService;
    }

    public static String[] parseOwnerAndRepo(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository URL cannot be empty");
        }
        Matcher matcher = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + url + ". Expected format: https://github.com/owner/repository");
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    @Transactional
    public Repository syncRepositoryFromUrl(String repositoryUrl) {
        String[] parts = parseOwnerAndRepo(repositoryUrl);
        String owner = parts[0];
        String repoName = parts[1];
        String canonicalUrl = "https://github.com/" + owner + "/" + repoName;

        Repository repository = repositoryJpaRepository.findByRepositoryUrl(canonicalUrl)
                .orElseGet(Repository::new);

        String name = repoName;
        String defaultBranch = "main";

        try {
            GitHubRepoResponse ghRepo = gitHubClient.getRepository(owner, repoName);
            if (ghRepo != null) {
                if (ghRepo.getName() != null) name = ghRepo.getName();
                if (ghRepo.getHtmlUrl() != null) canonicalUrl = ghRepo.getHtmlUrl();
                if (ghRepo.getDefaultBranch() != null) defaultBranch = ghRepo.getDefaultBranch();
            }
        } catch (Exception e) {
            System.err.println("Notice: GitHub API fetch returned (" + e.getMessage() + "), using parsed URL fallback for " + canonicalUrl);
        }

        repository.setName(name);
        repository.setRepositoryUrl(canonicalUrl);
        repository.setProvider("GITHUB");
        repository.setDefaultBranch(defaultBranch);

        Repository saved = repositoryJpaRepository.save(repository);

        // Auto-sync commit history and analyze changes on registration
        try {
            syncCommits(saved.getId(), defaultBranch, 30);
        } catch (Exception e) {
            System.err.println("Notice: Auto-syncing initial commits completed with warning: " + e.getMessage());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<String> getBranches(UUID repositoryId) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        List<String> branchNames = new ArrayList<>();
        try {
            List<GitHubBranchResponse> ghBranches = gitHubClient.getBranches(parts[0], parts[1]);
            if (ghBranches != null && !ghBranches.isEmpty()) {
                for (GitHubBranchResponse b : ghBranches) {
                    if (b.getName() != null && !b.getName().trim().isEmpty()) {
                        branchNames.add(b.getName().trim());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Notice: Could not list branches from GitHub (" + e.getMessage() + "), using fallback.");
        }

        String def = repository.getDefaultBranch() != null ? repository.getDefaultBranch() : "main";
        if (!branchNames.contains(def)) {
            branchNames.add(0, def);
        }
        return branchNames;
    }

    @Transactional
    public List<Commit> syncCommits(UUID repositoryId, String branch, int limit) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String targetBranch = (branch != null && !branch.trim().isEmpty())
                ? branch.trim()
                : (repository.getDefaultBranch() != null ? repository.getDefaultBranch() : "main");

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        List<GitHubCommitResponse> ghCommits = null;
        try {
            int max = limit > 0 ? limit : 30;
            ghCommits = gitHubClient.getAllCommitsUpTo(parts[0], parts[1], targetBranch, max);
        } catch (Exception e) {
            System.err.println("Notice: GitHub commit sync failed for branch " + targetBranch + " (" + e.getMessage() + "), returning existing commits.");
            List<Commit> existing = commitJpaRepository.findByRepositoryIdAndBranch(repositoryId, targetBranch);
            return !existing.isEmpty() ? existing : commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
        }

        List<Commit> savedCommits = new ArrayList<>();
        if (ghCommits == null || ghCommits.isEmpty()) {
            List<Commit> existing = commitJpaRepository.findByRepositoryIdAndBranch(repositoryId, targetBranch);
            return !existing.isEmpty() ? existing : commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
        }

        for (GitHubCommitResponse ghc : ghCommits) {
            if (ghc.getSha() == null) continue;

            Commit commit = commitJpaRepository.findByRepositoryIdAndSha(repositoryId, ghc.getSha())
                    .orElseGet(() -> {
                        Commit c = new Commit();
                        c.setRepository(repository);
                        c.setSha(ghc.getSha());
                        return c;
                    });

            String message = (ghc.getCommit() != null && ghc.getCommit().getMessage() != null)
                    ? ghc.getCommit().getMessage()
                    : "No commit message";
            if (message.length() > 2000) {
                message = message.substring(0, 1997) + "...";
            }
            commit.setMessage(message);

            String authorName = null;
            String authorEmail = null;
            LocalDateTime committedAt = LocalDateTime.now();

            if (ghc.getCommit() != null && ghc.getCommit().getAuthor() != null) {
                authorName = ghc.getCommit().getAuthor().getName();
                authorEmail = ghc.getCommit().getAuthor().getEmail();
                if (ghc.getCommit().getAuthor().getDate() != null) {
                    try {
                        committedAt = ZonedDateTime.parse(ghc.getCommit().getAuthor().getDate(), DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
                    } catch (Exception ignored) {}
                }
            } else if (ghc.getAuthor() != null) {
                authorName = ghc.getAuthor().getLogin();
            }

            commit.setAuthorName(authorName);
            commit.setAuthorEmail(authorEmail);
            commit.setCommittedAt(committedAt);
            commit.getBranches().add(targetBranch);

            savedCommits.add(commitJpaRepository.save(commit));
        }

        // Deep diff inspection and static code analysis on the most recent commits of this branch
        analyzeRecentCommits(parts[0], parts[1], savedCommits);

        return savedCommits;
    }

    @Transactional
    public List<Commit> syncCommits(UUID repositoryId, int limit) {
        return syncCommits(repositoryId, null, limit);
    }

    private void analyzeRecentCommits(String owner, String repo, List<Commit> commits) {
        int countToAnalyze = Math.min(10, commits.size());
        for (int i = 0; i < countToAnalyze; i++) {
            Commit commit = commits.get(i);
            try {
                GitHubSingleCommitResponse details = gitHubClient.getCommitDetails(owner, repo, commit.getSha());
                if (details != null && details.getFiles() != null) {
                    for (GitHubCompareResponse.GitHubDiffFile file : details.getFiles()) {
                        if (file.getFilename() == null) continue;

                        CodeChange change = codeChangeJpaRepository.findByCommitIdAndFilePath(commit.getId(), file.getFilename())
                                .orElseGet(() -> {
                                    CodeChange cc = new CodeChange();
                                    cc.setCommit(commit);
                                    cc.setFilePath(file.getFilename());
                                    return cc;
                                });

                        change.setAdditions(file.getAdditions() != null ? file.getAdditions() : 0);
                        change.setDeletions(file.getDeletions() != null ? file.getDeletions() : 0);
                        change.setPreviousFilePath(file.getPreviousFilename());
                        change.setPatch(file.getPatch());

                        ChangeType type = ChangeType.MODIFIED;
                        if ("added".equalsIgnoreCase(file.getStatus())) {
                            type = ChangeType.ADDED;
                        } else if ("removed".equalsIgnoreCase(file.getStatus())) {
                            type = ChangeType.DELETED;
                        } else if ("renamed".equalsIgnoreCase(file.getStatus())) {
                            type = ChangeType.RENAMED;
                        }
                        change.setChangeType(type);

                        CodeChange savedChange = codeChangeJpaRepository.save(change);
                        codeAnalyzerService.analyzeCodeChange(savedChange, file.getPatch());
                    }
                }
            } catch (Exception e) {
                System.err.println("Notice: analyzing commit diff failed (" + e.getMessage() + "), skipping detailed inspection for " + commit.getSha());
            }
        }
    }

    @Transactional
    public List<PullRequest> syncPullRequests(UUID repositoryId) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        List<GitHubPullRequestResponse> ghPulls = gitHubClient.getPullRequests(parts[0], parts[1], "all");

        List<PullRequest> savedPulls = new ArrayList<>();
        for (GitHubPullRequestResponse ghp : ghPulls) {
            PullRequest pr = pullRequestJpaRepository.findByRepositoryIdAndPrNumber(repositoryId, ghp.getNumber())
                    .orElseGet(() -> {
                        PullRequest p = new PullRequest();
                        p.setRepository(repository);
                        p.setPrNumber(ghp.getNumber());
                        return p;
                    });

            pr.setTitle(ghp.getTitle() != null ? ghp.getTitle() : "No title");
            pr.setHeadBranch(ghp.getHead() != null && ghp.getHead().getRef() != null ? ghp.getHead().getRef() : "unknown");
            pr.setBaseBranch(ghp.getBase() != null && ghp.getBase().getRef() != null ? ghp.getBase().getRef() : "main");

            PullRequestState state = PullRequestState.OPEN;
            if ("closed".equalsIgnoreCase(ghp.getState())) {
                state = ghp.getMergedAt() != null ? PullRequestState.MERGED : PullRequestState.CLOSED;
            }
            pr.setState(state);

            if (ghp.getUser() != null && ghp.getUser().getLogin() != null) {
                pr.setAuthor(ghp.getUser().getLogin());
            } else {
                pr.setAuthor("unknown");
            }

            savedPulls.add(pullRequestJpaRepository.save(pr));
        }

        return savedPulls;
    }

    @Transactional
    public List<CodeChange> compareAndSaveDiff(UUID repositoryId, String baseRef, String targetRef) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        GitHubCompareResponse compare = gitHubClient.compareCommits(parts[0], parts[1], baseRef, targetRef);

        if (compare == null || compare.getFiles() == null) {
            return new ArrayList<>();
        }

        List<Commit> repoCommits = commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
        if (repoCommits.isEmpty()) {
            throw new IllegalStateException("Sync commits before comparing diffs");
        }
        Commit targetCommit = repoCommits.get(0);

        List<CodeChange> savedChanges = new ArrayList<>();
        for (GitHubCompareResponse.GitHubDiffFile file : compare.getFiles()) {
            if (file.getFilename() == null) continue;

            CodeChange change = codeChangeJpaRepository.findByCommitIdAndFilePath(targetCommit.getId(), file.getFilename())
                    .orElseGet(() -> {
                        CodeChange cc = new CodeChange();
                        cc.setCommit(targetCommit);
                        cc.setFilePath(file.getFilename());
                        return cc;
                    });

            change.setAdditions(file.getAdditions() != null ? file.getAdditions() : 0);
            change.setDeletions(file.getDeletions() != null ? file.getDeletions() : 0);
            change.setPreviousFilePath(file.getPreviousFilename());
            change.setPatch(file.getPatch());

            ChangeType type = ChangeType.MODIFIED;
            if ("added".equalsIgnoreCase(file.getStatus())) {
                type = ChangeType.ADDED;
            } else if ("removed".equalsIgnoreCase(file.getStatus())) {
                type = ChangeType.DELETED;
            } else if ("renamed".equalsIgnoreCase(file.getStatus())) {
                type = ChangeType.RENAMED;
            }
            change.setChangeType(type);

            CodeChange savedChange = codeChangeJpaRepository.save(change);
            codeAnalyzerService.analyzeCodeChange(savedChange, file.getPatch());
            savedChanges.add(savedChange);
        }

        return savedChanges;
    }

    @Transactional(readOnly = true)
    public List<Commit> getRepositoryCommits(UUID repositoryId, String branch) {
        if (branch != null && !branch.trim().isEmpty()) {
            List<Commit> branchCommits = commitJpaRepository.findByRepositoryIdAndBranch(repositoryId, branch.trim());
            if (!branchCommits.isEmpty()) {
                return branchCommits;
            }
        }
        return commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repositoryId);
    }

    @Transactional(readOnly = true)
    public List<Commit> getRepositoryCommits(UUID repositoryId) {
        return getRepositoryCommits(repositoryId, null);
    }
}
