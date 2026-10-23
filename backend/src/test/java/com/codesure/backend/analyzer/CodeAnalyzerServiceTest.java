package com.codesure.backend.analyzer;

import com.codesure.backend.analyzer.service.CodeAnalyzerService;
import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeAnalyzerServiceTest {

    @Mock
    private FindingJpaRepository findingJpaRepository;

    @InjectMocks
    private CodeAnalyzerService codeAnalyzerService;

    private CodeChange codeChange;

    @BeforeEach
    void setUp() {
        codeChange = new CodeChange();
        ReflectionTestUtils.setField(codeChange, "id", UUID.randomUUID());
        codeChange.setFilePath("src/main/java/com/example/UserService.java");
    }

    @Test
    void testFileFiltering_AcceptsCode_RejectsDocsAndAssets() {
        // Code files should be accepted
        assertTrue(CodeAnalyzerService.isAnalyzableFile("src/App.java"));
        assertTrue(CodeAnalyzerService.isAnalyzableFile("frontend/src/Dashboard.tsx"));
        assertTrue(CodeAnalyzerService.isAnalyzableFile("scripts/backup.py"));
        assertTrue(CodeAnalyzerService.isAnalyzableFile("server/index.js"));
        assertTrue(CodeAnalyzerService.isAnalyzableFile("db/migration.sql"));
        assertTrue(CodeAnalyzerService.isAnalyzableFile("application.properties"));

        // Non-code files, lockfiles, and fixtures should be rejected
        assertFalse(CodeAnalyzerService.isAnalyzableFile("README.md"));
        assertFalse(CodeAnalyzerService.isAnalyzableFile("docs/guide.txt"));
        assertFalse(CodeAnalyzerService.isAnalyzableFile("package-lock.json"));
        assertFalse(CodeAnalyzerService.isAnalyzableFile("yarn.lock"));
        assertFalse(CodeAnalyzerService.isAnalyzableFile("assets/logo.png"));
        assertFalse(CodeAnalyzerService.isAnalyzableFile("tests/fixtures/mock.json"));
        assertFalse(CodeAnalyzerService.isAnalyzableFile(null));
    }

    @Test
    void testCommentStripping_SkipsComments_DetectsRealCode() {
        // Comment lines should be flagged as comments
        assertTrue(CodeAnalyzerService.isCommentOrDocLine("// SELECT * FROM users WHERE id = " + "123"));
        assertTrue(CodeAnalyzerService.isCommentOrDocLine("/* Dynamic string concatenation: SELECT * + user */"));
        assertTrue(CodeAnalyzerService.isCommentOrDocLine("* SELECT * FROM users WHERE id = ' + id"));
        assertTrue(CodeAnalyzerService.isCommentOrDocLine("# SELECT * FROM users WHERE name = ' + user"));
        assertTrue(CodeAnalyzerService.isCommentOrDocLine("-- SELECT * FROM users WHERE id = " + "1"));
        assertTrue(CodeAnalyzerService.isCommentOrDocLine("\"\"\"SELECT * FROM users WHERE id = \" + id\"\"\""));

        // Real code should not be classified as comments
        assertFalse(CodeAnalyzerService.isCommentOrDocLine("String query = \"SELECT * FROM users WHERE id = \" + userId;"));
        assertFalse(CodeAnalyzerService.isCommentOrDocLine("subprocess.run(cmd, shell=True)"));
    }

    @Test
    void testSqlInjectionInComment_IsIgnoredWithoutFinding() {
        codeChange.setFilePath("src/main/java/com/example/QueryHelper.java");
        String diff = "@@ -10,1 +10,2 @@\n" +
                "+// Example tutorial note: SELECT * FROM users WHERE id = ' + userId\n" +
                "+// Another comment: WHERE status = ' + status\n";

        List<Finding> findings = codeAnalyzerService.analyzeCodeChange(codeChange, diff);
        assertTrue(findings.isEmpty(), "Comment lines containing SQL keywords should not produce findings");
        verify(findingJpaRepository, never()).save(any());
    }

    @Test
    void testMultiLanguageRuleDetection_AwsKeyAndReactXssAndPythonPickle() {
        when(findingJpaRepository.findByCodeChangeIdAndRuleIdAndLineNumber(any(), any(), anyInt()))
                .thenReturn(Optional.empty());
        when(findingJpaRepository.save(any(Finding.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 1. AWS Key in .java
        codeChange.setFilePath("src/main/java/com/example/AwsConfig.java");
        String diffAws = "@@ -5,1 +5,1 @@\n" +
                "+String key = \"AKIAIOSFODNN7EXAMPLE\";\n";
        List<Finding> awsFindings = codeAnalyzerService.analyzeCodeChange(codeChange, diffAws);
        assertEquals(1, awsFindings.size());
        assertEquals("secret.cloud.aws-access-key", awsFindings.get(0).getRuleId());
        assertEquals(Severity.CRITICAL, awsFindings.get(0).getSeverity());

        // 2. React XSS in .tsx
        codeChange.setFilePath("frontend/src/UserProfile.tsx");
        String diffReact = "@@ -20,1 +20,1 @@\n" +
                "+return <div dangerouslySetInnerHTML={{ __html: userBio }} />;\n";
        List<Finding> reactFindings = codeAnalyzerService.analyzeCodeChange(codeChange, diffReact);
        assertEquals(1, reactFindings.size());
        assertEquals("ts.react.dangerously-set-inner-html", reactFindings.get(0).getRuleId());

        // 3. Python pickle in .py
        codeChange.setFilePath("backend/worker.py");
        String diffPython = "@@ -15,1 +15,1 @@\n" +
                "+payload = pickle.loads(raw_data)\n";
        List<Finding> pythonFindings = codeAnalyzerService.analyzeCodeChange(codeChange, diffPython);
        assertEquals(1, pythonFindings.size());
        assertEquals("python.security.insecure-deserialization", pythonFindings.get(0).getRuleId());
    }

    @Test
    void testNonAnalyzableFile_MarkdownReadme_ReturnsZeroFindings() {
        codeChange.setFilePath("README.md");
        String diff = "@@ -1,5 +1,5 @@\n" +
                "+# Important tutorial\n" +
                "+String query = \"SELECT * FROM users WHERE name = \" + name;\n" +
                "+String key = \"AKIAIOSFODNN7EXAMPLE\";\n";

        List<Finding> findings = codeAnalyzerService.analyzeCodeChange(codeChange, diff);
        assertTrue(findings.isEmpty(), "Markdown documentation files must be skipped");
        verify(findingJpaRepository, never()).save(any());
    }
}
