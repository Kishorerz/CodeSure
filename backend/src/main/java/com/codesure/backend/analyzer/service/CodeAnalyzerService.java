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

    public static class RuleDefinition {
        public final String ruleId;
        public final String message;
        public final Severity severity;
        public final int priorityRank;
        public final String rootCause;
        public final String remediation;
        public final Pattern pattern;

        public RuleDefinition(
                String ruleId,
                String message,
                Severity severity,
                int priorityRank,
                String rootCause,
                String remediation,
                Pattern pattern
        ) {
            this.ruleId = ruleId;
            this.message = message;
            this.severity = severity;
            this.priorityRank = priorityRank;
            this.rootCause = rootCause;
            this.remediation = remediation;
            this.pattern = pattern;
        }
    }

    private static final List<RuleDefinition> RULES = List.of(
            new RuleDefinition(
                    "java.security.hardcoded-secret",
                    "Critical Security: Hardcoded credential, password, or token exposed in source code.",
                    Severity.CRITICAL,
                    1,
                    "API keys, private keys, or passwords were committed in plaintext. Anyone with read access to the repo or logs can compromise the linked resource.",
                    "Fix this #1 first: Revoke and rotate the exposed token immediately. Remove the secret from code and inject it via environment variables (e.g. System.getenv) or a cloud secret manager.",
                    Pattern.compile("(?:password|secret|apikey|token|private_key|auth_token)\\s*=\\s*[\"'][^\"']{4,}[\"']", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.security.sql-injection",
                    "High Security: Dynamic string concatenation detected in SQL query.",
                    Severity.HIGH,
                    2,
                    "Dynamic user input is directly concatenated into the SQL statement, breaking parameter boundary isolation and permitting arbitrary SQL injection.",
                    "Fix this second: Replace string concatenation with parameterized queries using PreparedStatement, Spring Data JPA @Query(\"... WHERE u.id = :id\"), or standard repository finder methods.",
                    Pattern.compile("(?:SELECT|INSERT|UPDATE|DELETE|FROM|WHERE).*\\+\\s*[a-zA-Z0-9_]+", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.security.insecure-crypto",
                    "High Security: Obsolete or broken cryptographic algorithm (MD5/SHA-1/DES) detected.",
                    Severity.HIGH,
                    3,
                    "Weak hash functions (MD5, SHA-1) and old ciphers (DES) are vulnerable to collision, hash-length extension, and brute-force attacks.",
                    "Fix this: Upgrade to cryptographic standards such as SHA-256 / SHA-512 for checksums and AES-256-GCM for symmetric encryption.",
                    Pattern.compile("MessageDigest\\.getInstance\\([\"'](MD5|SHA-1)[\"']\\)|Cipher\\.getInstance\\([\"']DES[\"']\\)", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.security.path-traversal",
                    "High Security: Potential Path Traversal vulnerability when opening files with external input.",
                    Severity.HIGH,
                    4,
                    "File paths constructed from external input without normalization permit directory traversal sequences ('../'), allowing attackers to read or overwrite unauthorized filesystem locations.",
                    "Fix this: Sanitize the input, call Path.normalize(), and verify with Path.startsWith(baseDirectory) before opening or creating files.",
                    Pattern.compile("new\\s+File\\([^)]*\\+[^)]*\\)|Paths\\.get\\([^)]*\\+[^)]*\\)", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.security.cors-wildcard",
                    "High Security: Overly permissive CORS wildcard configuration.",
                    Severity.HIGH,
                    5,
                    "Exposing endpoints with wildcard origin ('*') while allowing credentials exposes authenticated user data to arbitrary malicious third-party websites.",
                    "Fix this: Restrict allowedOrigins to explicit trusted domain names (e.g. https://yourdomain.com) instead of wildcard *.",
                    Pattern.compile("@CrossOrigin\\(origins\\s*=\\s*\"\\*\"\\)|allowedOrigins\\(\"\\*\"\\)", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "config.security.credential-exposure",
                    "Medium Risk: Database or service credentials detected in configuration file.",
                    Severity.MEDIUM,
                    6,
                    "Committing production or staging credentials to configuration files risks exposure across shared environments and code clones.",
                    "Fix this: Move credentials to application-local.properties (added to .gitignore) or supply them dynamically via environment variables.",
                    Pattern.compile("(?i)(?:spring\\.datasource\\.password|jwt\\.secret|aws\\.secret)\\s*=\\s*[^$\\s]{3,}")
            ),
            new RuleDefinition(
                    "java.concurrency.thread-sleep",
                    "Medium Quality: Blocking Thread.sleep detected in application logic.",
                    Severity.MEDIUM,
                    7,
                    "Thread.sleep blocks servlet worker threads, causing latency spikes and thread exhaustion under concurrent user traffic.",
                    "Fix this: Replace blocking sleep with scheduled executors, CompletableFuture, or reactive non-blocking paradigms.",
                    Pattern.compile("Thread\\.sleep\\(")
            ),
            new RuleDefinition(
                    "java.quality.generic-exception",
                    "Low Quality: Catching or throwing generic Exception / Throwable degrades resilience.",
                    Severity.LOW,
                    8,
                    "Generic catch (Exception e) masks unexpected bugs and system errors, making root cause diagnosis difficult in production.",
                    "Fix this: Catch specific, expected checked exceptions (e.g. IOException, SQLException) and let unexpected runtime exceptions bubble to global handlers.",
                    Pattern.compile("catch\\s*\\(\\s*(?:Exception|Throwable)\\s+[a-zA-Z0-9_]+\\s*\\)|throw\\s+new\\s+Exception\\(", Pattern.CASE_INSENSITIVE)
            ),
            new RuleDefinition(
                    "java.quality.system-out",
                    "Low Quality: System.out.println / printStackTrace used instead of structured logger.",
                    Severity.LOW,
                    9,
                    "Direct console printing bypasses logging levels (DEBUG/INFO/ERROR), log rotation, and centralized observability tools.",
                    "Fix this: Use a structured SLF4J logger (e.g. private static final Logger log = LoggerFactory.getLogger(...) or @Slf4j).",
                    Pattern.compile("System\\.(?:out|err)\\.print|\\.printStackTrace\\(\\)")
            )
    );

    public CodeAnalyzerService(FindingJpaRepository findingJpaRepository) {
        this.findingJpaRepository = findingJpaRepository;
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
                        6,
                        "Configuration files committed to git might contain plaintext secrets.",
                        "Move secrets to environment variables or local ignored files.",
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
                                rule.priorityRank,
                                rule.rootCause,
                                rule.remediation,
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
            int priorityRank,
            String rootCause,
            String remediation,
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
                    f.setPriorityRank(priorityRank);
                    f.setRootCause(rootCause);
                    f.setRemediation(remediation);
                    f.setLineNumber(startLine);
                    f.setEndLineNumber(endLine);
                    f.setAnalyzer(analyzer);
                    return findingJpaRepository.save(f);
                });
    }
}
