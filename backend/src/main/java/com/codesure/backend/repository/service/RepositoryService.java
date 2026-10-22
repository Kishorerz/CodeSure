package com.codesure.backend.repository.service;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.pullrequest.entity.PullRequest;
import com.codesure.backend.pullrequest.repository.PullRequestJpaRepository;
import com.codesure.backend.repository.dto.CreateRepositoryRequest;
import com.codesure.backend.repository.dto.RepositoryResponse;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RepositoryService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final CommitJpaRepository commitJpaRepository;
    private final CodeChangeJpaRepository codeChangeJpaRepository;
    private final FindingJpaRepository findingJpaRepository;
    private final PullRequestJpaRepository pullRequestJpaRepository;

    public RepositoryService(
            RepositoryJpaRepository repositoryJpaRepository,
            CommitJpaRepository commitJpaRepository,
            CodeChangeJpaRepository codeChangeJpaRepository,
            FindingJpaRepository findingJpaRepository,
            PullRequestJpaRepository pullRequestJpaRepository
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.commitJpaRepository = commitJpaRepository;
        this.codeChangeJpaRepository = codeChangeJpaRepository;
        this.findingJpaRepository = findingJpaRepository;
        this.pullRequestJpaRepository = pullRequestJpaRepository;
    }

    public RepositoryResponse createRepository(
            CreateRepositoryRequest request
    ) {
        if (repositoryJpaRepository
                .findByRepositoryUrl(request.getRepositoryUrl())
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Repository already exists"
            );
        }

        Repository repository = new Repository();

        repository.setName(request.getName());
        repository.setRepositoryUrl(request.getRepositoryUrl());
        repository.setProvider(request.getProvider());
        repository.setDefaultBranch(request.getDefaultBranch());

        Repository savedRepository =
                repositoryJpaRepository.save(repository);

        return mapToResponse(savedRepository);
    }

    public List<RepositoryResponse> getAllRepositories() {
        return repositoryJpaRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public RepositoryResponse getRepositoryById(UUID id) {
        Repository repository = repositoryJpaRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Repository not found"
                ));

        return mapToResponse(repository);
    }

    @Transactional
    public void deleteRepository(UUID id) {
        Repository repository = repositoryJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + id));

        // 1. Delete all findings for this repository
        List<Finding> findings = findingJpaRepository.findByCodeChangeCommitRepositoryId(id);
        if (!findings.isEmpty()) {
            findingJpaRepository.deleteAll(findings);
        }

        // 2. Delete all code changes for each commit in this repository
        List<Commit> commits = commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(id);
        for (Commit c : commits) {
            List<CodeChange> changes = codeChangeJpaRepository.findByCommitId(c.getId());
            if (!changes.isEmpty()) {
                codeChangeJpaRepository.deleteAll(changes);
            }
        }

        // 3. Delete all pull requests
        List<PullRequest> prs = pullRequestJpaRepository.findByRepositoryIdOrderByPrNumberDesc(id);
        if (!prs.isEmpty()) {
            pullRequestJpaRepository.deleteAll(prs);
        }

        // 4. Delete all commits (and associated commit_branches)
        if (!commits.isEmpty()) {
            commitJpaRepository.deleteAll(commits);
        }

        // 5. Delete repository
        repositoryJpaRepository.delete(repository);
    }

    private RepositoryResponse mapToResponse(
            Repository repository
    ) {
        RepositoryResponse response =
                new RepositoryResponse();

        response.setId(repository.getId());
        response.setName(repository.getName());
        response.setRepositoryUrl(repository.getRepositoryUrl());
        response.setProvider(repository.getProvider());
        response.setDefaultBranch(repository.getDefaultBranch());
        response.setCreatedAt(repository.getCreatedAt());
        response.setUpdatedAt(repository.getUpdatedAt());

        return response;
    }
}
