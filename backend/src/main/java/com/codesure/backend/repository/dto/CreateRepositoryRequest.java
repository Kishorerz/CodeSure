package com.codesure.backend.repository.dto;

public class CreateRepositoryRequest {

    private String name;
    private String repositoryUrl;
    private String provider;
    private String defaultBranch;

    public String getName() {
        return name;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getProvider() {
        return provider;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
}