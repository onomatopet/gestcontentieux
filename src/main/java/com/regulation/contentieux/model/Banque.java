package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant une banque
 */
public class Banque {
    private Long id;
    private String codeBanque;
    private String nomBanque;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Constructeurs
    public Banque() {
        this.createdAt = LocalDateTime.now();
    }

    public Banque(String codeBanque, String nomBanque) {
        this();
        this.codeBanque = codeBanque;
        this.nomBanque = nomBanque;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeBanque() { return codeBanque; }
    public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }

    public String getNomBanque() { return nomBanque; }
    public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Banque banque = (Banque) o;
        return Objects.equals(id, banque.id) && Objects.equals(codeBanque, banque.codeBanque);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeBanque);
    }

    @Override
    public String toString() {
        return codeBanque + " - " + nomBanque;
    }
}