package com.codesure.backend.codechange.repository;

import com.codesure.backend.codechange.entity.CodeChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeChangeJpaRepository extends JpaRepository<CodeChange, UUID> {
    Optional<CodeChange> findByCommitIdAndFilePath(UUID commitId, String filePath);
    List<CodeChange> findByCommitId(UUID commitId);
}
