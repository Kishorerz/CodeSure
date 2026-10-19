package com.codesure.backend.github.service;

import com.codesure.backend.codechange.entity.ChangeType;
import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.github.client.GitHubClient;
import com.codesure.backend.github.dto.GitHubCommitResponse;
import com.codesure.backend.github.dto.GitHubCompareResponse;
import com.codesure.backend.github.dto.GitHubPullRequestResponse;
import com.codesure.backend.github.dto.GitHubRepoResponse;
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

    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/.*)?$");

    public GitHubService(
            GitHubClient gitHubClient,
            RepositoryJpaRepository repositoryJpaRepository,
            CommitJpaRepository commitJpaRepository,
            CodeChangeJpaRepository codeChangeJpaRepository,
            PullRequestJpaRepository pullRequestJpaRepository
    ) {
        this.gitHubClient = gitHubClient;
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.commitJpaRepository = commitJpaRepository;
        this.codeChangeJpaRepository = codeChangeJpaRepository;
        this.pullRequestJpaRepository = pullRequestJpaRepository;
    }

    public static String[] parseOwnerAndRepo(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository URL cannot be empty");
        }
        Matcher matcher = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + url);
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    @Transactional
    public Repository syncRepositoryFromUrl(String repositoryUrl) {
        String[] parts = parseOwnerAndRepo(repositoryUrl);
        String owner = parts[0];
        String repoName = parts[1];

        GitHubRepoResponse ghRepo = gitHubClient.getRepository(owner, repoName);
        if (ghRepo == null) {
            throw new IllegalArgumentException("Could not retrieve repository info from GitHub: " + repositoryUrl);
        }

        String canonicalUrl = ghRepo.getHtmlUrl() != null ? ghRepo.getHtmlUrl() : ("https://github.com/" + owner + "/" + repoName);

        Repository repository = repositoryJpaRepository.findByRepositoryUrl(canonicalUrl)
                .orElseGet(Repository::new);

        repository.setName(ghRepo.getName());
        repository.setRepositoryUrl(canonicalUrl);
        repository.setProvider("GITHUB");
        repository.setDefaultBranch(ghRepo.getDefaultBranch() != null ? ghRepo.getDefaultBranch() : "main");

        return repositoryJpaRepository.save(repository);
    }

    @Transactional
    public List<Commit> syncCommits(UUID repositoryId, int limit) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        List<GitHubCommitResponse> ghCommits = gitHubClient.getCommits(parts[0], parts[1], limit > 0 ? limit : 20);

        List<Commit> savedCommits = new ArrayList<>();
        if (ghCommits == null) {
            return savedCommits;
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

            savedCommits.add(commitJpaRepository.save(commit));
        }

        return savedCommits;
    }

    @Transactional
    public List<PullRequest> syncPullRequests(UUID repositoryId) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        List<GitHubPullRequestResponse> ghPrs = gitHubClient.getPullRequests(parts[0], parts[1], "all");

        List<PullRequest> savedPrs = new ArrayList<>();
        if (ghPrs == null) return savedPrs;

        for (GitHubPullRequestResponse ghPr : ghPrs) {
            if (ghPr.getNumber() == null) continue;

            PullRequest pr = pullRequestJpaRepository.findByRepositoryIdAndPrNumber(repositoryId, ghPr.getNumber())
                    .orElseGet(() -> {
                        PullRequest p = new PullRequest();
                        p.setRepository(repository);
                        p.setPrNumber(ghPr.getNumber());
                        return p;
                    });

            pr.setTitle(ghPr.getTitle() != null ? ghPr.getTitle() : "PR #" + ghPr.getNumber());
            pr.setAuthor(ghPr.getUser() != null ? ghPr.getUser().getLogin() : "unknown");
            pr.setBaseBranch(ghPr.getBase() != null ? ghPr.getBase().getRef() : repository.getDefaultBranch());
            pr.setHeadBranch(ghPr.getHead() != null ? ghPr.getHead().getRef() : "head");

            String stateStr = ghPr.getState() != null ? ghPr.getState().toUpperCase() : "OPEN";
            PullRequestState state;
            try {
                state = PullRequestState.valueOf(stateStr);
            } catch (Exception e) {
                state = PullRequestState.OPEN;
            }
            pr.setState(state);

            savedPrs.add(pullRequestJpaRepository.save(pr));
        }

        return savedPrs;
    }

    @Transactional
    public List<CodeChange> compareAndSaveDiff(UUID repositoryId, String baseSha, String targetSha) {
        Repository repository = repositoryJpaRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryId));

        String[] parts = parseOwnerAndRepo(repository.getRepositoryUrl());
        GitHubCompareResponse compare = gitHubClient.compareCommits(parts[0], parts[1], baseSha, targetSha);

        if (compare == null || compare.getFiles() == null) {
            return List.of();
        }

        // Ensure target commit is persisted
        Commit targetCommit = commitJpaRepository.findByRepositoryIdAndSha(repositoryId, targetSha)
                .orElseGet(() -> {
                    Commit c = new Commit();
                    c.setRepository(repository);
                    c.setSha(targetSha);
                    c.setMessage("Comparison target commit: " + targetSha);
                    c.setCommittedAt(LocalDateTime.now());
                    return commitJpaRepository.save(c);
                });

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

            ChangeType type = ChangeType.MODIFIED;
            if ("added".equalsIgnoreCase(file.getStatus())) {
                type = ChangeType.ADDED;
            } else if ("removed".equalsIgnoreCase(file.getStatus())) {
                type = ChangeType.DELETED;
            } else if ("renamed".equalsIgnoreCase(file.getStatus())) {
                type = ChangeType.RENAMED;
            }
            change.setChangeType(type);

            savedChanges.add(codeChangeJpaRepository.save(change));
        }

        return savedChanges;
    }
}
