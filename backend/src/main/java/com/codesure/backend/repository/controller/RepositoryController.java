package com.codesure.backend.repository.controller;

import com.codesure.backend.repository.dto.CreateRepositoryRequest;
import com.codesure.backend.repository.dto.RepositoryResponse;
import com.codesure.backend.repository.service.RepositoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepositoryResponse createRepository(
            @RequestBody CreateRepositoryRequest request
    ) {
        return repositoryService.createRepository(request);
    }

    @GetMapping
    public List<RepositoryResponse> getAllRepositories() {
        return repositoryService.getAllRepositories();
    }

    @GetMapping("/{id}")
    public RepositoryResponse getRepositoryById(
            @PathVariable UUID id
    ) {
        return repositoryService.getRepositoryById(id);
    }
}