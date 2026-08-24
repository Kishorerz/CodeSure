package com.codesure.backend.repository.service;

import com.codesure.backend.repository.dto.CreateRepositoryRequest;
import com.codesure.backend.repository.dto.RepositoryResponse;
import com.codesure.backend.repository.entity.Repository;
import com.codesure.backend.repository.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RepositoryService {

    private final RepositoryJpaRepository repositoryJpaRepository;

    public RepositoryService(
            RepositoryJpaRepository repositoryJpaRepository
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
    }

    public RepositoryResponse createRepository(
            CreateRepositoryRequest request
    ) {
        if (repositoryJpaRepository
                .findByRepositoryUrl(request.getRepositoryUrl())
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Repository already exists"
            );
        }

        Repository repository = new Repository();

        repository.setName(request.getName());
        repository.setRepositoryUrl(request.getRepositoryUrl());
        repository.setProvider(request.getProvider());
        repository.setDefaultBranch(request.getDefaultBranch());

        Repository savedRepository =
                repositoryJpaRepository.save(repository);

        return mapToResponse(savedRepository);
    }

    public List<RepositoryResponse> getAllRepositories() {
        return repositoryJpaRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public RepositoryResponse getRepositoryById(UUID id) {
        Repository repository = repositoryJpaRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Repository not found"
                ));

        return mapToResponse(repository);
    }

    private RepositoryResponse mapToResponse(
            Repository repository
    ) {
        RepositoryResponse response =
                new RepositoryResponse();

        response.setId(repository.getId());
        response.setName(repository.getName());
        response.setRepositoryUrl(repository.getRepositoryUrl());
        response.setProvider(repository.getProvider());
        response.setDefaultBranch(repository.getDefaultBranch());
        response.setCreatedAt(repository.getCreatedAt());
        response.setUpdatedAt(repository.getUpdatedAt());

        return response;
    }
}