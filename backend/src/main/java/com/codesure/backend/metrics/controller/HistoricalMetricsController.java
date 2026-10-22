package com.codesure.backend.metrics.controller;

import com.codesure.backend.metrics.dto.HistoricalMetricsDto;
import com.codesure.backend.metrics.service.HistoricalMetricsService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class HistoricalMetricsController {

    private final HistoricalMetricsService historicalMetricsService;

    public HistoricalMetricsController(HistoricalMetricsService historicalMetricsService) {
        this.historicalMetricsService = historicalMetricsService;
    }

    @GetMapping("/{id}/historical-metrics")
    public HistoricalMetricsDto getHistoricalMetrics(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch
    ) {
        return historicalMetricsService.calculateHistoricalMetrics(id, branch);
    }
}
