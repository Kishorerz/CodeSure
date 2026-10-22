package com.codesure.backend.metrics.controller;

import com.codesure.backend.metrics.dto.BusFactorAnalysisDto;
import com.codesure.backend.metrics.dto.HistoricalMetricsDto;
import com.codesure.backend.metrics.service.BusFactorService;
import com.codesure.backend.metrics.service.HistoricalMetricsService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class HistoricalMetricsController {

    private final HistoricalMetricsService historicalMetricsService;
    private final BusFactorService busFactorService;

    public HistoricalMetricsController(
            HistoricalMetricsService historicalMetricsService,
            BusFactorService busFactorService
    ) {
        this.historicalMetricsService = historicalMetricsService;
        this.busFactorService = busFactorService;
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
}
