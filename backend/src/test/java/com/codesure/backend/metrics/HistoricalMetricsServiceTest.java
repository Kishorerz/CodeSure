package com.codesure.backend.metrics;

import com.codesure.backend.codechange.entity.ChangeType;
import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.commit.entity.Commit;
import com.codesure.backend.commit.repository.CommitJpaRepository;
import com.codesure.backend.finding.entity.Analyzer;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import com.codesure.backend.metrics.dto.HistoricalMetricsDto;
import com.codesure.backend.metrics.service.HistoricalMetricsService;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalMetricsServiceTest {

    @Mock
    private RepositoryJpaRepository repositoryJpaRepository;

    @Mock
    private CommitJpaRepository commitJpaRepository;

    @Mock
    private CodeChangeJpaRepository codeChangeJpaRepository;

    @Mock
    private FindingJpaRepository findingJpaRepository;

    @InjectMocks
    private HistoricalMetricsService historicalMetricsService;

    private Repository testRepo;
    private UUID repoId;

    @BeforeEach
    void setUp() {
        repoId = UUID.randomUUID();
        testRepo = new Repository();
        ReflectionTestUtils.setField(testRepo, "id", repoId);
        testRepo.setName("test-repo");
        testRepo.setDefaultBranch("main");
    }

    @Test
    void testEmptyRepositoryMetrics() {
        when(repositoryJpaRepository.findById(repoId)).thenReturn(Optional.of(testRepo));
        when(commitJpaRepository.findByRepositoryIdOrderByCommittedAtDesc(repoId)).thenReturn(List.of());

        HistoricalMetricsDto dto = historicalMetricsService.calculateHistoricalMetrics(repoId, null);

        assertNotNull(dto);
        assertEquals("test-repo", dto.getRepositoryName());
        assertEquals(100.0, dto.getRepositoryStabilityIndex());
        assertEquals(0, dto.getChurn7Days().getCommitCount());
        assertTrue(dto.getTopHotspots().isEmpty());
    }

    @Test
    void testMetricsWithCommitsAndHotspots() {
        when(repositoryJpaRepository.findById(repoId)).thenReturn(Optional.of(testRepo));

        Commit c1 = new Commit();
        UUID c1Id = UUID.randomUUID();
        ReflectionTestUtils.setField(c1, "id", c1Id);
        c1.setSha("sha1");
        c1.setAuthorName("Alice");
        c1.setCommittedAt(LocalDateTime.now().minusDays(2));
        c1.setBranches(Set.of("main"));

        Commit c2 = new Commit();
        UUID c2Id = UUID.randomUUID();
        ReflectionTestUtils.setField(c2, "id", c2Id);
        c2.setSha("sha2");
        c2.setAuthorName("Bob");
        c2.setCommittedAt(LocalDateTime.now().minusDays(10));
        c2.setBranches(Set.of("main"));

        when(commitJpaRepository.findByRepositoryIdAndBranch(eq(repoId), eq("main")))
                .thenReturn(List.of(c1, c2));

        CodeChange ch1 = new CodeChange();
        UUID ch1Id = UUID.randomUUID();
        ReflectionTestUtils.setField(ch1, "id", ch1Id);
        ch1.setCommit(c1);
        ch1.setFilePath("src/main/AuthService.java");
        ch1.setChangeType(ChangeType.MODIFIED);
        ch1.setAdditions(50);
        ch1.setDeletions(10);

        CodeChange ch2 = new CodeChange();
        UUID ch2Id = UUID.randomUUID();
        ReflectionTestUtils.setField(ch2, "id", ch2Id);
        ch2.setCommit(c2);
        ch2.setFilePath("src/main/AuthService.java");
        ch2.setChangeType(ChangeType.MODIFIED);
        ch2.setAdditions(20);
        ch2.setDeletions(5);

        when(codeChangeJpaRepository.findByCommitIdIn(any())).thenReturn(List.of(ch1, ch2));

        Finding f1 = new Finding();
        ReflectionTestUtils.setField(f1, "id", UUID.randomUUID());
        f1.setCodeChange(ch1);
        f1.setRuleId("sql-injection");
        f1.setSeverity(Severity.CRITICAL);
        f1.setMessage("Potential SQL Injection vulnerability");
        f1.setLineNumber(45);
        f1.setAnalyzer(Analyzer.SEMGREP);

        when(findingJpaRepository.findByCommitIdsOrderByPriorityRankAsc(any())).thenReturn(List.of(f1));

        HistoricalMetricsDto dto = historicalMetricsService.calculateHistoricalMetrics(repoId, "main");

        assertNotNull(dto);
        assertEquals("main", dto.getBranch());
        assertEquals(1, dto.getChurn7Days().getCommitCount());
        assertEquals(2, dto.getChurn30Days().getCommitCount());
        assertEquals(2, dto.getChurnAllTime().getCommitCount());
        assertEquals(85, dto.getChurnAllTime().getTotalChurn());

        assertFalse(dto.getTopHotspots().isEmpty());
        assertEquals("src/main/AuthService.java", dto.getTopHotspots().get(0).getFilePath());
        assertEquals(1, dto.getTopHotspots().get(0).getCriticalFindings());
        assertEquals("CRITICAL", dto.getTopHotspots().get(0).getRiskLevel());

        assertFalse(dto.getTopAuthors().isEmpty());
        assertTrue(dto.getRepositoryStabilityIndex() < 100.0);
    }
}
