package com.codesure.backend.codechange.entity;

import com.codesure.backend.commit.entity.Commit;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "code_changes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_code_change_commit_file",
            columnNames = {"commit_id", "file_path"}
        )
    }
)
public class CodeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private Commit commit;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "previous_file_path")
    private String previousFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(nullable = false)
    private Integer additions;

    @Column(nullable = false)
    private Integer deletions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CodeChange() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Commit getCommit() {
        return commit;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getPreviousFilePath() {
        return previousFilePath;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public Integer getAdditions() {
        return additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCommit(Commit commit) {
        this.commit = commit;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setPreviousFilePath(String previousFilePath) {
        this.previousFilePath = previousFilePath;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public void setAdditions(Integer additions) {
        this.additions = additions;
    }

    public void setDeletions(Integer deletions) {
        this.deletions = deletions;
    }
}