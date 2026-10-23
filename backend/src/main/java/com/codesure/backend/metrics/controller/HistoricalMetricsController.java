package com.codesure.backend.metrics.controller;

import com.codesure.backend.metrics.dto.BusFactorAnalysisDto;
import com.codesure.backend.metrics.dto.HistoricalMetricsDto;
import com.codesure.backend.metrics.service.BusFactorService;
import com.codesure.backend.metrics.service.HistoricalMetricsService;
import com.codesure.backend.risk.dto.MergeSimulationDto;
import com.codesure.backend.risk.service.MergeSimulationService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class HistoricalMetricsController {

    private final HistoricalMetricsService historicalMetricsService;
    private final BusFactorService busFactorService;
    private final MergeSimulationService mergeSimulationService;

    public HistoricalMetricsController(
            HistoricalMetricsService historicalMetricsService,
            BusFactorService busFactorService,
            MergeSimulationService mergeSimulationService
    ) {
        this.historicalMetricsService = historicalMetricsService;
        this.busFactorService = busFactorService;
        this.mergeSimulationService = mergeSimulationService;
    }

    @GetMapping("/{id}/historical-metrics")
    public HistoricalMetricsDto getHistoricalMetrics(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch
    ) {
        return historicalMetricsService.calculateHistoricalMetrics(id, branch);
    }

    @GetMapping("/{id}/bus-factor")
    public BusFactorAnalysisDto getBusFactorAnalysis(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch
    ) {
        return busFactorService.analyzeBusFactor(id, branch);
    }

    @GetMapping("/{id}/merge-simulation")
    public MergeSimulationDto getMergeSimulation(
            @PathVariable UUID id,
            @RequestParam(required = false) String sourceBranch,
            @RequestParam(required = false) String targetBranch
    ) {
        return mergeSimulationService.simulateBranchMerge(id, sourceBranch, targetBranch);
    }
}
