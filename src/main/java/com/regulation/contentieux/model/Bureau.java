package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un bureau
 */
public class Bureau {
    private Long id;
    private String codeBureau;
    private String nomBureau;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Bureau() {
        this.createdAt = LocalDateTime.now();
    }

    public Bureau(String codeBureau, String nomBureau) {
        this();
        this.codeBureau = codeBureau;
        this.nomBureau = nomBureau;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeBureau() { return codeBureau; }
    public void setCodeBureau(String codeBureau) { this.codeBureau = codeBureau; }

    public String getNomBureau() { return nomBureau; }
    public void setNomBureau(String nomBureau) { this.nomBureau = nomBureau; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bureau bureau = (Bureau) o;
        return Objects.equals(id, bureau.id) && Objects.equals(codeBureau, bureau.codeBureau);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeBureau);
    }

    @Override
    public String toString() {
        return codeBureau + " - " + nomBureau;
    }
}