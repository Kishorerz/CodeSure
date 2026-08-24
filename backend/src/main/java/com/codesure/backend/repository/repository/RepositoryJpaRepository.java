package com.codesure.backend.repository.repository;

import com.codesure.backend.repository.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepositoryJpaRepository extends JpaRepository<Repository, UUID> {

    Optional<Repository> findByRepositoryUrl(String repositoryUrl);

}