package com.codesure.backend.finding.entity;

import com.codesure.backend.codechange.entity.CodeChange;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "findings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_finding_change_rule_line",
            columnNames = {"code_change_id", "rule_id", "line_number"}
        )
    }
)
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "code_change_id", nullable = false)
    private CodeChange codeChange;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "end_line_number")
    private Integer endLineNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Analyzer analyzer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Finding() {
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

    public CodeChange getCodeChange() {
        return codeChange;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public Integer getEndLineNumber() {
        return endLineNumber;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setCodeChange(CodeChange codeChange) {
        this.codeChange = codeChange;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setEndLineNumber(Integer endLineNumber) {
        this.endLineNumber = endLineNumber;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }
}