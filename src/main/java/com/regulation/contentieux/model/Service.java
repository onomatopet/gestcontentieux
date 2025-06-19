package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un service
 */
public class Service {
    private Long id;
    private String codeService;
    private String nomService;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Service() {
        this.createdAt = LocalDateTime.now();
    }

    public Service(String codeService, String nomService) {
        this();
        this.codeService = codeService;
        this.nomService = nomService;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeService() { return codeService; }
    public void setCodeService(String codeService) { this.codeService = codeService; }

    public String getNomService() { return nomService; }
    public void setNomService(String nomService) { this.nomService = nomService; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return Objects.equals(id, service.id) && Objects.equals(codeService, service.codeService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeService);
    }

    @Override
    public String toString() {
        return codeService + " - " + nomService;
    }
}