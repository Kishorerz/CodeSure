package com.codesure.backend.analyzer;

import com.codesure.backend.analyzer.dto.FixSuggestionDto;
import com.codesure.backend.analyzer.service.RemediationFixService;
import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.finding.entity.Analyzer;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemediationFixServiceTest {

    @Mock
    private FindingJpaRepository findingJpaRepository;

    @InjectMocks
    private RemediationFixService remediationFixService;

    private UUID findingId;
    private Finding testFinding;
    private CodeChange testCodeChange;

    @BeforeEach
    void setUp() {
        findingId = UUID.randomUUID();
        testFinding = new Finding();
        ReflectionTestUtils.setField(testFinding, "id", findingId);

        testCodeChange = new CodeChange();
        testCodeChange.setFilePath("src/main/java/com/example/AuthService.java");
        testFinding.setCodeChange(testCodeChange);
        testFinding.setLineNumber(42);
        testFinding.setAnalyzer(Analyzer.SEMGREP);
        testFinding.setSeverity(Severity.CRITICAL);
    }

    @Test
    void testHardcodedSecretFixGeneration() {
        testFinding.setRuleId("java.security.hardcoded-secret");
        when(findingJpaRepository.findById(findingId)).thenReturn(Optional.of(testFinding));

        FixSuggestionDto fix = remediationFixService.generateFixSuggestion(findingId);

        assertNotNull(fix);
        assertEquals(findingId, fix.getFindingId());
        assertEquals("HIGH", fix.getConfidenceLevel());
        assertTrue(fix.getBeforeSnippet().contains("API_SECRET"));
        assertTrue(fix.getAfterSnippet().contains("System.getenv"));
        assertTrue(fix.getUnifiedDiff().contains("--- a/src/main/java/com/example/AuthService.java"));
        assertFalse(fix.getSecurityBestPractices().isEmpty());
    }

    @Test
    void testSqlInjectionFixGeneration() {
        testFinding.setRuleId("java.security.sql-injection");
        when(findingJpaRepository.findById(findingId)).thenReturn(Optional.of(testFinding));

        FixSuggestionDto fix = remediationFixService.generateFixSuggestion(findingId);

        assertNotNull(fix);
        assertEquals("HIGH", fix.getConfidenceLevel());
        assertTrue(fix.getTitle().contains("PreparedStatement"));
        assertTrue(fix.getAfterSnippet().contains("PreparedStatement pstmt"));
        assertTrue(fix.getUnifiedDiff().contains("+"));
    }
}
