package com.regulation.contentieux.model;

import com.regulation.contentieux.model.enums.StatutAffaire;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entité représentant une affaire contentieuse
 * Une affaire est liée à un contrevenant et peut avoir plusieurs contraventions et encaissements
 */
public class Affaire {

    private Long id;
    private String numeroAffaire;
    private LocalDate dateCreation;
    private LocalDate dateConstatation;
    private String lieuConstatation;
    private String description;
    private BigDecimal montantTotal;
    private BigDecimal montantEncaisse;
    private BigDecimal montantAmendeTotal; // Pour compatibilité avec AffaireDAO
    private StatutAffaire statut;
    private String observations;

    private BigDecimal soldeRestant;
    private Integer joursDepuisCreation;

    // Relations
    private Contrevenant contrevenant;
    private Agent agentVerbalisateur;
    private Bureau bureau;
    private Service service;
    private List<Contravention> contraventions;
    private List<Encaissement> encaissements;

    // IDs pour compatibilité avec AffaireDAO existant
    private Long contrevenantId;
    private Long contraventionId;
    private Long bureauId;
    private Long serviceId;

    // Métadonnées
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private boolean deleted;
    private String deletedBy;
    private LocalDate deletedAt;

    // Constructeurs
    public Affaire() {
        this.dateCreation = LocalDate.now();
        this.statut = StatutAffaire.OUVERTE;
        this.montantTotal = BigDecimal.ZERO;
        this.montantEncaisse = BigDecimal.ZERO;
        this.montantAmendeTotal = BigDecimal.ZERO;
        this.contraventions = new ArrayList<>();
        this.encaissements = new ArrayList<>();
        this.deleted = false;
        this.createdAt = LocalDateTime.now();
    }

    // Méthodes métier

    /**
     * Vérifie si l'affaire a des encaissements
     */
    public boolean hasEncaissements() {
        return encaissements != null && !encaissements.isEmpty();
    }

    /**
     * Calcule le montant total encaissé
     */
    public BigDecimal getMontantEncaisseTotal() {
        if (encaissements == null || encaissements.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return encaissements.stream()
                .filter(e -> e.getStatut() == com.regulation.contentieux.model.enums.StatutEncaissement.VALIDE)
                .map(Encaissement::getMontantEncaisse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcule le solde restant dû
     */
    public BigDecimal getSoldeRestant() {
        BigDecimal encaisseTotal = getMontantEncaisseTotal();
        return getMontantTotal().subtract(encaisseTotal);
    }

    /**
     * Vérifie si l'affaire est entièrement payée
     */
    public boolean isFullyPaid() {
        return getSoldeRestant().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Ajoute une contravention à l'affaire
     */
    public void addContravention(Contravention contravention) {
        if (contraventions == null) {
            contraventions = new ArrayList<>();
        }
        contraventions.add(contravention);
        contravention.setAffaire(this);
        recalculerMontantTotal();
    }

    /**
     * Ajoute un encaissement à l'affaire
     */
    public void addEncaissement(Encaissement encaissement) {
        if (encaissements == null) {
            encaissements = new ArrayList<>();
        }
        encaissements.add(encaissement);
        encaissement.setAffaire(this);
        montantEncaisse = getMontantEncaisseTotal();
    }

    /**
     * Recalcule le montant total de l'affaire
     */
    private void recalculerMontantTotal() {
        if (contraventions != null && !contraventions.isEmpty()) {
            montantTotal = contraventions.stream()
                    .map(Contravention::getMontant)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            montantAmendeTotal = montantTotal; // Synchroniser pour compatibilité
        } else {
            montantTotal = BigDecimal.ZERO;
            montantAmendeTotal = BigDecimal.ZERO;
        }
    }

    /**
     * Retourne le nom d'affichage du contrevenant
     */
    public String getContrevenantDisplayName() {
        if (contrevenant != null) {
            return contrevenant.getDisplayName();
        }
        return "Non défini";
    }

    /**
     * Vérifie si l'affaire peut être clôturée
     */
    public boolean canBeClosed() {
        return isFullyPaid() && statut != StatutAffaire.CLOSE;
    }

    /**
     * Vérifie si l'affaire peut être modifiée
     */
    public boolean peutEtreModifiee() {
        return statut == StatutAffaire.OUVERTE || statut == StatutAffaire.EN_COURS;
    }

    /**
     * Vérifie si l'affaire peut recevoir un encaissement
     */
    public boolean peutRecevoirEncaissement() {
        return statut == StatutAffaire.OUVERTE || statut == StatutAffaire.EN_COURS;
    }

    /**
     * Synchronise les IDs des relations (pour compatibilité DAO)
     */
    public void synchronizeRelationIds() {
        if (contrevenant != null) {
            contrevenantId = contrevenant.getId();
        }
        if (bureau != null) {
            bureauId = bureau.getId();
        }
        if (service != null) {
            serviceId = service.getId();
        }
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setSoldeRestant(BigDecimal soldeRestant) {
        this.soldeRestant = soldeRestant;
    }

    public Integer getJoursDepuisCreation() {
        return joursDepuisCreation;
    }

    public void setJoursDepuisCreation(Integer joursDepuisCreation) {
        this.joursDepuisCreation = joursDepuisCreation;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroAffaire() {
        return numeroAffaire;
    }

    public void setNumeroAffaire(String numeroAffaire) {
        this.numeroAffaire = numeroAffaire;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDate getDateConstatation() {
        return dateConstatation;
    }

    public void setDateConstatation(LocalDate dateConstatation) {
        this.dateConstatation = dateConstatation;
    }

    public String getLieuConstatation() {
        return lieuConstatation;
    }

    public void setLieuConstatation(String lieuConstatation) {
        this.lieuConstatation = lieuConstatation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMontantTotal() {
        return montantTotal != null ? montantTotal : BigDecimal.ZERO;
    }

    public void setMontantTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
        this.montantAmendeTotal = montantTotal; // Synchroniser
    }

    public BigDecimal getMontantEncaisse() {
        return montantEncaisse;
    }

    public void setMontantEncaisse(BigDecimal montantEncaisse) {
        this.montantEncaisse = montantEncaisse;
    }

    public BigDecimal getMontantAmendeTotal() {
        return montantAmendeTotal != null ? montantAmendeTotal : montantTotal;
    }

    public void setMontantAmendeTotal(BigDecimal montantAmendeTotal) {
        this.montantAmendeTotal = montantAmendeTotal;
        this.montantTotal = montantAmendeTotal; // Synchroniser
    }

    public long getMontantAmende() {
        BigDecimal montant = getMontantAmendeTotal();
        return montant != null ? montant.longValue() : 0L;
    }

    public StatutAffaire getStatut() {
        return statut;
    }

    public void setStatut(StatutAffaire statut) {
        this.statut = statut;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public Contrevenant getContrevenant() {
        return contrevenant;
    }

    public void setContrevenant(Contrevenant contrevenant) {
        this.contrevenant = contrevenant;
        if (contrevenant != null) {
            this.contrevenantId = contrevenant.getId();
        }
    }

    public Agent getAgentVerbalisateur() {
        return agentVerbalisateur;
    }

    public void setAgentVerbalisateur(Agent agentVerbalisateur) {
        this.agentVerbalisateur = agentVerbalisateur;
    }

    public Bureau getBureau() {
        return bureau;
    }

    public void setBureau(Bureau bureau) {
        this.bureau = bureau;
        if (bureau != null) {
            this.bureauId = bureau.getId();
        }
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
        if (service != null) {
            this.serviceId = service.getId();
        }
    }

    public List<Contravention> getContraventions() {
        return contraventions;
    }

    public void setContraventions(List<Contravention> contraventions) {
        this.contraventions = contraventions;
        recalculerMontantTotal();
    }

    public List<Encaissement> getEncaissements() {
        return encaissements;
    }

    public void setEncaissements(List<Encaissement> encaissements) {
        this.encaissements = encaissements;
        this.montantEncaisse = getMontantEncaisseTotal();
    }

    // Getters/Setters pour IDs (compatibilité DAO)

    public Long getContrevenantId() {
        return contrevenantId != null ? contrevenantId :
                (contrevenant != null ? contrevenant.getId() : null);
    }

    public void setContrevenantId(Long contrevenantId) {
        this.contrevenantId = contrevenantId;
    }

    public Long getContraventionId() {
        return contraventionId;
    }

    public void setContraventionId(Long contraventionId) {
        this.contraventionId = contraventionId;
    }

    public Long getBureauId() {
        return bureauId != null ? bureauId :
                (bureau != null ? bureau.getId() : null);
    }

    public void setBureauId(Long bureauId) {
        this.bureauId = bureauId;
    }

    public Long getServiceId() {
        return serviceId != null ? serviceId :
                (service != null ? service.getId() : null);
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public LocalDate getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDate deletedAt) {
        this.deletedAt = deletedAt;
    }

    // Equals, HashCode et ToString

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Affaire affaire = (Affaire) o;
        return Objects.equals(id, affaire.id) &&
                Objects.equals(numeroAffaire, affaire.numeroAffaire);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, numeroAffaire);
    }

    @Override
    public String toString() {
        return "Affaire{" +
                "id=" + id +
                ", numeroAffaire='" + numeroAffaire + '\'' +
                ", dateCreation=" + dateCreation +
                ", montantTotal=" + montantTotal +
                ", statut=" + statut +
                '}';
    }
}