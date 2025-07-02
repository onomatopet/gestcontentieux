package com.regulation.contentieux.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant la liaison entre une affaire et un agent
 * Définit le rôle d'un agent sur une affaire spécifique
 */
public class AffaireActeur {

    public static final String ROLE_CHEF = "Chef";
    public static final String ROLE_SAISISSANT = "Saisissant";
    public static final String ROLE_INDICATEUR = "INDICATEUR"; // Non stocké dans affaire_acteurs

    private Long affaireId;
    private Long agentId;
    private String roleSurAffaire;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedAt;

    private String assignedBy;

    // Relations optionnelles (pour éviter les chargements circulaires)
    @JsonIgnore
    private Affaire affaire;

    @JsonIgnore
    private Agent agent;

    // Constructeurs
    public AffaireActeur() {
        this.assignedAt = LocalDateTime.now();
    }

    public AffaireActeur(Long affaireId, Long agentId, String roleSurAffaire) {
        this();
        this.affaireId = affaireId;
        this.agentId = agentId;
        this.roleSurAffaire = roleSurAffaire;
    }

    public AffaireActeur(Long affaireId, Long agentId, String roleSurAffaire, String assignedBy) {
        this(affaireId, agentId, roleSurAffaire);
        this.assignedBy = assignedBy;
    }

    // Méthodes métier
    public boolean estChef() {
        return "CHEF".equals(roleSurAffaire);
    }

    public boolean estSaisissant() {
        return "SAISISSANT".equals(roleSurAffaire);
    }

    public boolean estVerificateur() {
        return "VERIFICATEUR".equals(roleSurAffaire);
    }

    public boolean estActeurPrincipal() {
        return estChef() || estSaisissant();
    }

    // Getters et Setters
    public Long getAffaireId() { return affaireId; }
    public void setAffaireId(Long affaireId) { this.affaireId = affaireId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getRoleSurAffaire() { return roleSurAffaire; }
    public void setRoleSurAffaire(String roleSurAffaire) { this.roleSurAffaire = roleSurAffaire; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public Affaire getAffaire() { return affaire; }
    public void setAffaire(Affaire affaire) { this.affaire = affaire; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) {
        this.agent = agent;
        // Synchroniser automatiquement l'agentId quand on définit l'agent
        if (agent != null && agent.getId() != null) {
            this.agentId = agent.getId();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AffaireActeur that = (AffaireActeur) o;
        return Objects.equals(affaireId, that.affaireId) &&
                Objects.equals(agentId, that.agentId) &&
                Objects.equals(roleSurAffaire, that.roleSurAffaire);
    }

    @Override
    public int hashCode() {
        return Objects.hash(affaireId, agentId, roleSurAffaire);
    }

    @Override
    public String toString() {
        return String.format("AffaireActeur[Affaire:%d, Agent:%d, Role:%s]",
                affaireId, agentId, roleSurAffaire);
    }
}