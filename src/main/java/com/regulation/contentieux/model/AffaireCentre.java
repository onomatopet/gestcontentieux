package com.regulation.contentieux.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité représentant la relation entre une affaire et un centre
 * Table de liaison avec les montants de répartition
 */
public class AffaireCentre {
    private Long id;
    private Long affaireId;
    private Long centreId;
    private BigDecimal montantBase;
    private BigDecimal montantIndicateur;
    private LocalDateTime dateImport;
    private String source;

    // Relations
    private Affaire affaire;
    private Centre centre;

    // Constructeurs
    public AffaireCentre() {
        this.montantBase = BigDecimal.ZERO;
        this.montantIndicateur = BigDecimal.ZERO;
        this.dateImport = LocalDateTime.now();
        this.source = "SAISIE";
    }

    // Méthodes métier
    public BigDecimal getMontantTotal() {
        return montantBase.add(montantIndicateur);
    }

    public boolean isFromMigration() {
        return "MIGRATION".equals(source);
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAffaireId() { return affaireId; }
    public void setAffaireId(Long affaireId) { this.affaireId = affaireId; }

    public Long getCentreId() { return centreId; }
    public void setCentreId(Long centreId) { this.centreId = centreId; }

    public BigDecimal getMontantBase() { return montantBase; }
    public void setMontantBase(BigDecimal montantBase) { this.montantBase = montantBase; }

    public BigDecimal getMontantIndicateur() { return montantIndicateur; }
    public void setMontantIndicateur(BigDecimal montantIndicateur) { this.montantIndicateur = montantIndicateur; }

    public LocalDateTime getDateImport() { return dateImport; }
    public void setDateImport(LocalDateTime dateImport) { this.dateImport = dateImport; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Affaire getAffaire() { return affaire; }
    public void setAffaire(Affaire affaire) { this.affaire = affaire; }

    public Centre getCentre() { return centre; }
    public void setCentre(Centre centre) { this.centre = centre; }
}