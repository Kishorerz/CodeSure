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

        if (ruleId.contains("aws-access-key")) {
            return buildAwsKeyFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("github-token")) {
            return buildGitHubTokenFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("command-injection")) {
            return buildCommandInjectionFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("dangerously-set-inner-html")) {
            return buildDangerouslySetInnerHTMLFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("insecure-deserialization")) {
            return buildPickleFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("subprocess-shell-true")) {
            return buildSubprocessShellFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("dom-xss-eval")) {
            return buildDomXssEvalFix(findingId, ruleId, filePath, line);
        } else if (ruleId.contains("hardcoded-secret")) {
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

    private FixSuggestionDto buildAwsKeyFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\nString awsAccessKey = \"AKIAIOSFODNN7EXAMPLE\";", line, filePath);
        String after = "// Secure: Retrieve AWS credentials via AWS IAM Roles or environment variables\n" +
                "String awsAccessKey = System.getenv(\"AWS_ACCESS_KEY_ID\");\n\n" +
                "// Recommended: Use DefaultCredentialsProvider which checks IAM instance profiles automatically:\n" +
                "// AmazonS3 s3 = AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain()).build();";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,2 @@\n-String awsAccessKey = \"AKIAIOSFODNN7EXAMPLE\";\n+String awsAccessKey = System.getenv(\"AWS_ACCESS_KEY_ID\");",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Revoke and Externalize AWS Access Key")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Hardcoded AWS keys committed to version control can be scraped by automated bots in seconds. Externalizing to IAM Instance Profiles or environment variables prevents account compromise.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Deactivate and delete the exposed AKIA key immediately in AWS IAM Console.",
                        "Use AWS Secrets Manager or IAM Roles for Amazon EC2 / ECS instead of static keys.",
                        "Check CloudTrail logs for unauthorized API calls performed with the compromised key."
                ))
                .build();
    }

    private FixSuggestionDto buildGitHubTokenFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s\nconst token = \"ghp_abcdefghijklmnopqrstuvwxyz0123456789\";", line, filePath);
        String after = "// Secure: Inject GitHub Token via environment variable\n" +
                "const token = process.env.GITHUB_TOKEN;\n" +
                "if (!token) {\n" +
                "  throw new Error('GITHUB_TOKEN environment variable is required');\n" +
                "}";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,4 @@\n-const token = \"ghp_abcdefghijklmnopqrstuvwxyz0123456789\";\n+const token = process.env.GITHUB_TOKEN;\n+if (!token) throw new Error('Missing GITHUB_TOKEN');",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Revoke and Inject GitHub Token via Environment")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Exposed GitHub personal access tokens grant unauthorized repository access and pull request modification rights. Rotate the token and inject via process.env.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Revoke the token immediately at github.com/settings/tokens.",
                        "Use fine-grained Personal Access Tokens with the minimum required repository permissions.",
                        "Add .env to your repository's .gitignore file."
                ))
                .build();
    }

    private FixSuggestionDto buildCommandInjectionFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s - Vulnerable shell command concatenation\n" +
                "const { exec } = require('child_process');\n" +
                "exec('ping -c 4 ' + targetHost, (err, stdout) => { ... });", line, filePath);
        String after = "// Secure: Use execFile or spawn with argument array to avoid invoking system shell\n" +
                "const { execFile } = require('child_process');\n" +
                "execFile('ping', ['-c', '4', targetHost], (err, stdout) => {\n" +
                "  if (err) return console.error(err);\n" +
                "  console.log(stdout);\n" +
                "});";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,2 +%d,3 @@\n" +
                "-exec('ping -c 4 ' + targetHost, (err, stdout) => { ... });\n" +
                "+const { execFile } = require('child_process');\n" +
                "+execFile('ping', ['-c', '4', targetHost], (err, stdout) => { ... });",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Bypass Shell Parsing with execFile / spawn Argument Arrays")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("String concatenation in child_process.exec allows command chaining (e.g. '; rm -rf /'). Using execFile or spawn ensures user input is passed as a safe literal argument, not shell code.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Never pass user-controlled input into shell interpreters (/bin/sh, cmd.exe).",
                        "Validate user arguments against strict regular expressions (e.g. IP address or hostname format).",
                        "Prefer native Node.js APIs or libraries over external shell utilities whenever possible."
                ))
                .build();
    }

    private FixSuggestionDto buildDangerouslySetInnerHTMLFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s - Unsanitized XSS injection\n" +
                "<div dangerouslySetInnerHTML={{ __html: userProvidedHtml }} />", line, filePath);
        String after = "// Secure: Sanitize HTML with DOMPurify before rendering\n" +
                "import DOMPurify from 'dompurify';\n\n" +
                "const cleanHtml = DOMPurify.sanitize(userProvidedHtml);\n" +
                "<div dangerouslySetInnerHTML={{ __html: cleanHtml }} />";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,1 +%d,4 @@\n" +
                "-<div dangerouslySetInnerHTML={{ __html: userProvidedHtml }} />\n" +
                "+import DOMPurify from 'dompurify';\n" +
                "+const cleanHtml = DOMPurify.sanitize(userProvidedHtml);\n" +
                "+<div dangerouslySetInnerHTML={{ __html: cleanHtml }} />",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Sanitize Raw HTML with DOMPurify to Prevent XSS")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("dangerouslySetInnerHTML renders raw markup, enabling malicious users to inject <script> tags or onerror handlers. DOMPurify strips active exploit payloads before DOM insertion.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Install and configure dompurify (npm install dompurify @types/dompurify).",
                        "Render standard React components and text nodes instead of raw HTML whenever feasible.",
                        "Implement Content Security Policy (CSP) headers to block unauthorized script execution."
                ))
                .build();
    }

    private FixSuggestionDto buildPickleFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("# Line %d in %s - Insecure pickle deserialization\n" +
                "import pickle\n" +
                "data = pickle.loads(raw_user_payload)", line, filePath);
        String after = "# Secure: Use safe serialization like JSON or Protocol Buffers\n" +
                "import json\n\n" +
                "# json.loads does not execute arbitrary Python bytecode\n" +
                "data = json.loads(raw_user_payload.decode('utf-8'))";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,2 +%d,3 @@\n" +
                "-import pickle\n-data = pickle.loads(raw_user_payload)\n" +
                "+import json\n+data = json.loads(raw_user_payload.decode('utf-8'))",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Replace Python pickle with Safe JSON Deserialization")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("Python's pickle format can execute arbitrary code by design via __reduce__ methods. Deserializing untrusted pickle streams allows attackers to achieve complete remote system takeover.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Never unpickle data received from untrusted clients, network sockets, or caches.",
                        "Use JSON, MessagePack, or Protocol Buffers for cross-service data interchange.",
                        "Sign serialized messages with HMAC if binary caching is strictly required."
                ))
                .build();
    }

    private FixSuggestionDto buildSubprocessShellFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("# Line %d in %s - Shell metacharacter injection\n" +
                "import subprocess\n" +
                "subprocess.run(f\"backup.sh {user_folder}\", shell=True)", line, filePath);
        String after = "# Secure: Disable shell=True and pass arguments as an explicit list\n" +
                "import subprocess\n\n" +
                "# Arguments are passed directly to execve without /bin/sh interpolation\n" +
                "subprocess.run([\"./backup.sh\", user_folder], shell=False, check=True)";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,2 +%d,3 @@\n" +
                "-subprocess.run(f\"backup.sh {user_folder}\", shell=True)\n" +
                "+subprocess.run([\"./backup.sh\", user_folder], shell=False, check=True)",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Disable shell=True in Subprocess Invocation")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("When shell=True is set, Python routes the string through a system shell, allowing attackers to inject command delimiters (; || && `) into user_folder.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Always set shell=False (the default in subprocess).",
                        "Pass command arguments as an array/list of strings rather than a single formatted string.",
                        "Use shlex.quote() only if dynamic shell expansion is unavoidable."
                ))
                .build();
    }

    private FixSuggestionDto buildDomXssEvalFix(UUID findingId, String ruleId, String filePath, int line) {
        String before = String.format("// Line %d in %s - Insecure dynamic evaluation\n" +
                "const data = eval('(' + userJsonInput + ')');\n" +
                "document.write('<div>Welcome ' + userName + '</div>');", line, filePath);
        String after = "// Secure: Parse structured data with JSON.parse and assign textContent safely\n" +
                "const data = JSON.parse(userJsonInput);\n\n" +
                "const userDiv = document.createElement('div');\n" +
                "userDiv.textContent = 'Welcome ' + userName;\n" +
                "document.body.appendChild(userDiv);";
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ -%d,2 +%d,4 @@\n" +
                "-const data = eval('(' + userJsonInput + ')');\n-document.write('<div>Welcome ' + userName + '</div>');\n" +
                "+const data = JSON.parse(userJsonInput);\n+const el = document.createElement('div');\n+el.textContent = 'Welcome ' + userName;\n+document.body.appendChild(el);",
                filePath, filePath, line, line);

        return FixSuggestionDto.builder()
                .findingId(findingId)
                .ruleId(ruleId)
                .filePath(filePath)
                .lineNumber(line)
                .title("Replace eval() and document.write() with Safe DOM Methods")
                .beforeSnippet(before)
                .afterSnippet(after)
                .unifiedDiff(diff)
                .explanation("eval() and document.write() execute input as live JavaScript inside the client's session. JSON.parse and textContent prevent script execution while safely rendering data.")
                .confidenceLevel("HIGH")
                .securityBestPractices(List.of(
                        "Ban eval(), Function(), and document.write() in your ESLint configuration (no-eval, no-implied-eval).",
                        "Always use JSON.parse() when decoding JSON payloads.",
                        "Use textContent instead of innerHTML to avoid XSS injections."
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
