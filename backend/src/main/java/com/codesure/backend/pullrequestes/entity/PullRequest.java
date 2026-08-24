package com.codesure.backend.pullrequest.entity;

import com.codesure.backend.repository.entity.Repository;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "pull_requests",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_pull_request_repository_number",
            columnNames = {"repository_id", "pr_number"}
        )
    }
)
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PullRequestState state;

    @Column(name = "base_branch", nullable = false)
    private String baseBranch;

    @Column(name = "head_branch", nullable = false)
    private String headBranch;

    @Column(nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PullRequest() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Repository getRepository() {
        return repository;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public String getTitle() {
        return title;
    }

    public PullRequestState getState() {
        return state;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public String getAuthor() {
        return author;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setState(PullRequestState state) {
        this.state = state;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public void setHeadBranch(String headBranch) {
        this.headBranch = headBranch;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}