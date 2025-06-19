package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un centre
 */
public class Centre {
    private Long id;
    private String codeCentre;
    private String nomCentre;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Centre() {
        this.createdAt = LocalDateTime.now();
    }

    public Centre(String codeCentre, String nomCentre) {
        this();
        this.codeCentre = codeCentre;
        this.nomCentre = nomCentre;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeCentre() { return codeCentre; }
    public void setCodeCentre(String codeCentre) { this.codeCentre = codeCentre; }

    public String getNomCentre() { return nomCentre; }
    public void setNomCentre(String nomCentre) { this.nomCentre = nomCentre; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Centre centre = (Centre) o;
        return Objects.equals(id, centre.id) && Objects.equals(codeCentre, centre.codeCentre);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeCentre);
    }

    @Override
    public String toString() {
        return codeCentre + " - " + nomCentre;
    }
}