package com.codesure.backend.analyzer.service;

import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.finding.entity.Analyzer;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeAnalyzerService {

    private final FindingJpaRepository findingJpaRepository;

    private static final List<RuleDefinition> RULES = List.of(
            new RuleDefinition(
                    "java.security.sql-injection",
                    "Potential SQL Injection: Dynamic concatenation detected in SQL query.",
                    Severity.HIGH,
                    Pattern.compile("(?:SELECT|INSERT|UPDATE|DELETE|FROM|WHERE).*\\+\\s*[a-zA-Z0-9_]+", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.security.hardcoded-secret",
                    "Hardcoded secret, password or credential token detected in source code.",
                    Severity.CRITICAL,
                    Pattern.compile("(?:password|secret|apikey|token|private_key)\\s*=\\s*[\"'][^\"']{4,}[\"']", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.security.insecure-crypto",
                    "Insecure cryptographic algorithm (MD5/SHA-1/DES) detected.",
                    Severity.HIGH,
                    Pattern.compile("MessageDigest\\.getInstance\\([\"'](MD5|SHA-1)[\"']\\)|Cipher\\.getInstance\\([\"']DES[\"']\\)", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.quality.generic-exception",
                    "Catching or throwing generic Exception / Throwable degrades error handling specificity.",
                    Severity.LOW,
                    Pattern.compile("catch\\s*\\(\\s*(?:Exception|Throwable)\\s+[a-zA-Z0-9_]+\\s*\\)|throw\\s+new\\s+Exception\\(", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.quality.system-out",
                    "System.out.println / printStackTrace used instead of a structured logging framework.",
                    Severity.LOW,
                    Pattern.compile("System\\.(?:out|err)\\.print|\\.printStackTrace\\(\\)")
            ),
            new RuleDefinition(
                    "java.security.path-traversal",
                    "Potential Path Traversal vulnerability when constructing file paths with external input.",
                    Severity.HIGH,
                    Pattern.compile("new\\s+File\\([^)]*\\+[^)]*\\)", Pattern.CASE_INSENSITIVE)
            )
    );

    public CodeAnalyzerService(FindingJpaRepository findingJpaRepository) {
        this.findingJpaRepository = findingJpaRepository;
    }

    public static class RuleDefinition {
        final String ruleId;
        final String message;
        final Severity severity;
        final Pattern pattern;

        public RuleDefinition(String ruleId, String message, Severity severity, Pattern pattern) {
            this.ruleId = ruleId;
            this.message = message;
            this.severity = severity;
            this.pattern = pattern;
        }
    }

    @Transactional
    public List<Finding> analyzeCodeChange(CodeChange codeChange, String diffPatch) {
        List<Finding> findings = new ArrayList<>();
        if (diffPatch == null || diffPatch.trim().isEmpty()) {
            // Heuristic analysis based on file type & path
            if (codeChange.getFilePath() != null && codeChange.getFilePath().contains("properties") && codeChange.getAdditions() > 0) {
                Finding f = createFindingIfAbsent(
                        codeChange,
                        "config.security.credential-exposure",
                        "Configuration file modified: verify no sensitive secrets are committed.",
                        Severity.MEDIUM,
                        1, 1,
                        Analyzer.SEMGREP
                );
                if (f != null) findings.add(f);
            }
            return findings;
        }

        String[] lines = diffPatch.split("\r?\n");
        int currentLine = 1;

        for (String line : lines) {
            if (line.startsWith("@@")) {
                Matcher m = Pattern.compile("@@\\s*-[0-9]+(?:,[0-9]+)?\\s*\\+([0-9]+)").matcher(line);
                if (m.find()) {
                    currentLine = Integer.parseInt(m.group(1));
                }
                continue;
            }

            if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedCode = line.substring(1);
                for (RuleDefinition rule : RULES) {
                    if (rule.pattern.matcher(addedCode).find()) {
                        Finding finding = createFindingIfAbsent(
                                codeChange,
                                rule.ruleId,
                                rule.message,
                                rule.severity,
                                currentLine,
                                currentLine,
                                Analyzer.SEMGREP
                        );
                        if (finding != null) {
                            findings.add(finding);
                        }
                    }
                }
                currentLine++;
            } else if (!line.startsWith("-")) {
                currentLine++;
            }
        }

        return findings;
    }

    private Finding createFindingIfAbsent(
            CodeChange codeChange,
            String ruleId,
            String message,
            Severity severity,
            int startLine,
            int endLine,
            Analyzer analyzer
    ) {
        return findingJpaRepository.findByCodeChangeIdAndRuleIdAndLineNumber(codeChange.getId(), ruleId, startLine)
                .orElseGet(() -> {
                    Finding f = new Finding();
                    f.setCodeChange(codeChange);
                    f.setRuleId(ruleId);
                    f.setMessage(message);
                    f.setSeverity(severity);
                    f.setLineNumber(startLine);
                    f.setEndLineNumber(endLine);
                    f.setAnalyzer(analyzer);
                    return findingJpaRepository.save(f);
                });
    }
}
