package com.codesure.backend.analyzer.service;

import com.codesure.backend.analyzer.dto.FixSuggestionDto;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RemediationFixService {

    private final FindingJpaRepository findingJpaRepository;

    public RemediationFixService(FindingJpaRepository findingJpaRepository) {
        this.findingJpaRepository = findingJpaRepository;
    }

    public FixSuggestionDto generateFixSuggestion(UUID findingId) {
        Finding finding = findingJpaRepository.findById(findingId)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + findingId));

        String ruleId = finding.getRuleId() != null ? finding.getRuleId() : "general.code-quality";
        String filePath = (finding.getCodeChange() != null && finding.getCodeChange().getFilePath() != null)
                ? finding.getCodeChange().getFilePath()
                : "src/main/AppService.java";
        int line = finding.getLineNumber() > 0 ? finding.getLineNumber() : 1;

        if (ruleId.contains("hardcoded-secret")) {
            return buildHardcodedSecretFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("sql-injection")) {
            return buildSqlInjectionFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("insecure-crypto")) {
            return buildInsecureCryptoFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("path-traversal")) {
            return buildPathTraversalFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("cors-wildcard")) {
            return buildCorsWildcardFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("credential-exposure")) {
            return buildCredentialExposureFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("thread-sleep")) {
            return buildThreadSleepFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("generic-exception")) {
            return buildGenericExceptionFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("system-out")) {
            return buildSystemOutFix(findingId, ruleId, filePath, line);
        } else {
            return buildGenericRemediationFix(finding, findingId, ruleId, filePath, line);
        }
    }

    private FixSuggestionDto buildHardcodedSecretFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\nprivate static final String API_SECRET = \"sk_live_9948271638491024\";", line, filePath);
        String after = "// Secure: Retrieve secret securely from environment variable or secret vault\n" +
                "private static final String API_SECRET = System.getenv(\"API_SECRET\");\n\n" +
                "// In Spring Boot applications, inject via property:\n" +
                "// @Value(\"${app.api.secret}\")\n" +
                "// private String apiSecret;";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,2 @@\n-private static final String API_SECRET = \"sk_live_9948271638491024\";\n+private static final String API_SECRET = System.getenv(\"API_SECRET\");",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Extract Hardcoded Secret to Environment Variable")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Hardcoded credentials committed to git expose services to anyone with repository access. Extracting them to environment variables protects sensitive infrastructure and complies with 12-Factor App standards.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Immediately revoke and rotate the exposed secret or API key in the provider console.",
                        "Add .env or secret configuration files to .gitignore.",
                        "Use AWS Secrets Manager, Azure Key Vault, or HashiCorp Vault for production deployments."
                ))
                .build();
    }

    private FixSuggestionDto buildSqlInjectionFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s - Vulnerable raw SQL string concatenation\n" +
                "String query = \"SELECT * FROM users WHERE username = '\" + username + \"' AND status = 1\";\n" +
                "Statement stmt = connection.createStatement();\n" +
                "ResultSet rs = stmt.executeQuery(query);", line, filePath);
        String after = "// Secure: PreparedStatement parameterization separates SQL syntax from user data\n" +
                "String query = \"SELECT * FROM users WHERE username = ? AND status = 1\";\n" +
                "try (PreparedStatement pstmt = connection.prepareStatement(query)) {\n" +
                "    pstmt.setString(1, username);\n" +
                "    try (ResultSet rs = pstmt.executeQuery()) {\n" +
                "        while (rs.next()) {\n" +
                "            // process verified records safely\n" +
                "        }\n" +
                "    }\n" +
                "}";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,3 +%d,7 @@\n" +
                "-String query = \"SELECT * FROM users WHERE username = '\" + username + \"' AND status = 1\";\n" +
                "-Statement stmt = connection.createStatement();\n" +
                "-ResultSet rs = stmt.executeQuery(query);\n" +
                "+String query = \"SELECT * FROM users WHERE username = ? AND status = 1\";\n" +
                "+try (PreparedStatement pstmt = connection.prepareStatement(query)) {\n" +
                "+    pstmt.setString(1, username);\n" +
                "+    ResultSet rs = pstmt.executeQuery();\n" +
                "+}", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Parameterize SQL Query using PreparedStatement")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Dynamic string concatenation permits attackers to escape the SQL string boundary and inject arbitrary commands (e.g. ' OR '1'='1). Parameterized queries ensure input is strictly treated as data.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Always use PreparedStatement or Spring Data JPA parameterized queries (@Param).",
                        "Avoid constructing SQL statements using StringBuilder or string formatting with user input.",
                        "Enforce strict input validation and least-privilege DB permissions."
                ))
                .build();
    }

    private FixSuggestionDto buildInsecureCryptoFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s - Obsolete hash algorithm\n" +
                "MessageDigest md = MessageDigest.getInstance(\"MD5\");\n" +
                "byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));", line, filePath);
        String after = "// Secure: Use SHA-256 for data verification, or BCrypt for password hashing\n" +
                "MessageDigest md = MessageDigest.getInstance(\"SHA-256\");\n" +
                "byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));\n\n" +
                "// For user passwords, use an adaptive key-derivation function:\n" +
                "// PasswordEncoder encoder = new BCryptPasswordEncoder(12);\n" +
                "// String hashedPassword = encoder.encode(rawPassword);";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,2 +%d,2 @@\n" +
                "-MessageDigest md = MessageDigest.getInstance(\"MD5\");\n" +
                "+MessageDigest md = MessageDigest.getInstance(\"SHA-256\");", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Upgrade Cryptographic Hash to SHA-256 / BCrypt")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("MD5 and SHA-1 have known collision and pre-image vulnerabilities. Upgrading to SHA-256 for integrity and BCrypt/Argon2 for passwords provides strong cryptographic security.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Do not use MD5, SHA-1, or DES for any security or integrity checks.",
                        "Use BCrypt, PBKDF2, or Argon2 for hashing authentication credentials.",
                        "Use AES-256-GCM for authenticated encryption of confidential payloads."
                ))
                .build();
    }

    private FixSuggestionDto buildPathTraversalFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s - Insecure path resolution\n" +
                "File targetFile = new File(baseDirectory, userInputFileName);", line, filePath);
        String after = "// Secure: Normalize path and verify it stays inside the base directory\n" +
                "Path basePath = Paths.get(baseDirectory).toRealPath();\n" +
                "Path targetPath = basePath.resolve(userInputFileName).normalize();\n" +
                "if (!targetPath.startsWith(basePath)) {\n" +
                "    throw new SecurityException(\"Unauthorized directory traversal detected!\");\n" +
                "}\n" +
                "File targetFile = targetPath.toFile();";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,6 @@\n" +
                "-File targetFile = new File(baseDirectory, userInputFileName);\n" +
                "+Path basePath = Paths.get(baseDirectory).toRealPath();\n" +
                "+Path targetPath = basePath.resolve(userInputFileName).normalize();\n" +
                "+if (!targetPath.startsWith(basePath)) throw new SecurityException(\"Invalid path\");\n" +
                "+File targetFile = targetPath.toFile();", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Enforce Canonical Path Traversal Boundary Check")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Attackers can submit inputs like '../../etc/passwd' to access arbitrary files on the server. Validating that the resolved canonical path starts with the designated root folder prevents traversal.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Always call normalize() and verify path.startsWith(basePath).",
                        "Sanitize filenames with a strict allowlist (e.g. ^[a-zA-Z0-9._-]+$).",
                        "Avoid storing uploaded user files in system root or executable directories."
                ))
                .build();
    }

    private FixSuggestionDto buildCorsWildcardFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\n@CrossOrigin(origins = \"*\")\n@RestController", line, filePath);
        String after = "// Secure: Explicitly list authorized domain origins\n" +
                "@CrossOrigin(origins = {\"https://app.codesure.com\", \"http://localhost:5173\"}, allowCredentials = \"true\")\n" +
                "@RestController";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,1 @@\n" +
                "-@CrossOrigin(origins = \"*\")\n" +
                "+@CrossOrigin(origins = {\"https://app.codesure.com\", \"http://localhost:5173\"}, allowCredentials = \"true\")",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Restrict CORS Allowed Origins to Trusted Domains")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("A wildcard '*' CORS configuration permits any website visited by a user to trigger authenticated requests to your API. Explicitly defining allowed origins neutralizes cross-origin data theft.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Specify exact production domain names in CORS configuration.",
                        "Do not combine wildcard '*' with allowCredentials = true.",
                        "Use environment-based origin configurations for dev and prod."
                ))
                .build();
    }

    private FixSuggestionDto buildCredentialExposureFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("# Line %d in %s\nspring.datasource.password=ProdPassword987!", line, filePath);
        String after = "# Secure: Use placeholder populated by environment variable\nspring.datasource.password=${DB_PASSWORD}";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,1 @@\n" +
                "-spring.datasource.password=ProdPassword987!\n" +
                "+spring.datasource.password=${DB_PASSWORD}", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Replace Static Credential with Property Placeholder")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Configuration files in version control are accessible to developers, CI/CD runners, and forks. Using variable substitution (${DB_PASSWORD}) ensures secrets remain decoupled.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Store production database passwords in environment variables.",
                        "Use git-crypt or SOPS if repository secrets must be versioned."
                ))
                .build();
    }

    private FixSuggestionDto buildThreadSleepFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\nThread.sleep(3000);", line, filePath);
        String after = "// Secure: Non-blocking asynchronous delay\n" +
                "ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();\n" +
                "scheduler.schedule(() -> {\n" +
                "    // execute delayed task without blocking thread pool\n" +
                "}, 3, TimeUnit.SECONDS);";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,4 @@\n" +
                "-Thread.sleep(3000);\n" +
                "+CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {\n" +
                "+    // execute asynchronous task\n" +
                "+});", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Replace Blocking Thread.sleep with Non-Blocking Scheduler")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Thread.sleep holds servlet worker threads idle, degrading throughput and causing starvation during high traffic.")
                .confidenceLevel("MEDIUM")
                .securityBestPractices(List.of(
                        "Use CompletableFuture, ScheduledExecutorService, or reactive delays instead of blocking calls."
                ))
                .build();
    }

    private FixSuggestionDto buildGenericExceptionFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\ncatch (Exception e) {\n    e.printStackTrace();\n}", line, filePath);
        String after = "// Secure: Catch specific expected exceptions and rethrow or handle explicitly\n" +
                "catch (IOException | SQLException e) {\n" +
                "    log.warn(\"Operation encountered recoverable error: {}\", e.getMessage(), e);\n" +
                "    throw new BusinessServiceException(\"Service execution failed\", e);\n" +
                "}";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,3 +%d,3 @@\n" +
                "-catch (Exception e) {\n" +
                "-    e.printStackTrace();\n" +
                "+catch (IOException | SQLException e) {\n" +
                "+    log.warn(\"Recoverable error: {}\", e.getMessage(), e);\n" +
                "}", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Handle Specific Exceptions instead of Generic Catch")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Catching generic Exception masks unintended runtime errors, NullPointerExceptions, and OutOfMemoryErrors, making root cause detection difficult.")
                .confidenceLevel("MEDIUM")
                .securityBestPractices(List.of(
                        "Catch only specific, expected exceptions.",
                        "Preserve cause when rethrowing custom exceptions."
                ))
                .build();
    }

    private FixSuggestionDto buildSystemOutFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\nSystem.out.println(\"Processing item: \" + itemId);", line, filePath);
        String after = "// Secure: Use structured SLF4J logger with proper log level\n" +
                "log.info(\"Processing item: {}\", itemId);";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,1 @@\n" +
                "-System.out.println(\"Processing item: \" + itemId);\n" +
                "+log.info(\"Processing item: {}\", itemId);", filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Replace Standard Output with Structured Logger")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("System.out lacks severity levels, timestamp formatting, and log-file rotation, preventing centralized monitoring.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Use SLF4J / Logback with parameterized logging (log.info(\"{}\", val)).",
                        "Never log sensitive user data or passwords."
                ))
                .build();
    }

    private FixSuggestionDto buildGenericRemediationFix(Finding finding, UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\n// Defect flagged: %s", line, filePath, finding.getMessage());
        String after = String.format("// Recommended Remediation:\n// %s\n// Ensure input validation and unit test coverage.",
                finding.getRemediation() != null ? finding.getRemediation() : "Refactor code to follow clean code standards.");
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,1 @@\n-// Review defect: %s\n+// Applied remediation for %s",
                filePath, filePath, line, line, ruleId, ruleId);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Code Remediation Suggestion for " + ruleId)
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation(finding.getRootCause() != null ? finding.getRootCause() : "Static inspection identified a code quality or security risk.")
                .confidenceLevel("MEDIUM")
                .securityBestPractices(List.of(
                        "Perform unit and integration testing after applying changes.",
                        "Request peer review before merging to primary branch."
                ))
                .build();
    }
}
