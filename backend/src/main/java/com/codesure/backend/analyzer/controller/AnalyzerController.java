package com.codesure.backend.analyzer.controller;

import com.codesure.backend.analyzer.dto.FixSuggestionDto;
import com.codesure.backend.analyzer.service.CodeAnalyzerService;
import com.codesure.backend.analyzer.service.RemediationFixService;
import com.codesure.backend.codechange.entity.CodeChange;
import com.codesure.backend.codechange.repository.CodeChangeJpaRepository;
import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.repository.FindingJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analyzer")
public class AnalyzerController {

    private final CodeAnalyzerService codeAnalyzerService;
    private final CodeChangeJpaRepository codeChangeJpaRepository;
    private final FindingJpaRepository findingJpaRepository;
    private final RemediationFixService remediationFixService;

    public AnalyzerController(
            CodeAnalyzerService codeAnalyzerService,
            CodeChangeJpaRepository codeChangeJpaRepository,
            FindingJpaRepository findingJpaRepository,
            RemediationFixService remediationFixService
    ) {
        this.codeAnalyzerService = codeAnalyzerService;
        this.codeChangeJpaRepository = codeChangeJpaRepository;
        this.findingJpaRepository = findingJpaRepository;
        this.remediationFixService = remediationFixService;
    }

    @PostMapping("/scan-change/{codeChangeId}")
    public List<Finding> scanChange(
            @PathVariable UUID codeChangeId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        CodeChange codeChange = codeChangeJpaRepository.findById(codeChangeId)
                .orElseThrow(() -> new IllegalArgumentException("CodeChange not found: " + codeChangeId));

        String patch = body != null ? body.get("patch") : null;
        return codeAnalyzerService.analyzeCodeChange(codeChange, patch);
    }

    @GetMapping("/findings-by-change/{codeChangeId}")
    public List<Finding> getFindingsByChange(@PathVariable UUID codeChangeId) {
        return findingJpaRepository.findByCodeChangeId(codeChangeId);
    }

    @GetMapping("/fix-suggestion/{findingId}")
    public FixSuggestionDto getFixSuggestion(@PathVariable UUID findingId) {
        return remediationFixService.generateFixSuggestion(findingId);
    }
}
