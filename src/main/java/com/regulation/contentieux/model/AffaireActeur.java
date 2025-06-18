package com.regulation.contentieux.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.regulation.contentieux.model.enums.RoleSurAffaire;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité de liaison entre Affaire et Agent avec rôle
 */
public class AffaireActeur {
    private Long affaireId;
    private Long agentId;
    private RoleSurAffaire roleSurAffaire;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedAt;

    private String assignedBy;

    // Relations
    @JsonIgnore
    private Affaire affaire;
    @JsonIgnore
    private Agent agent;

    // Constructeurs
    public AffaireActeur() {
        this.assignedAt = LocalDateTime.now();
    }

    public AffaireActeur(Long affaireId, Long agentId, RoleSurAffaire roleSurAffaire) {
        this();
        this.affaireId = affaireId;
        this.agentId = agentId;
        this.roleSurAffaire = roleSurAffaire;
    }

    // Getters et Setters
    public Long getAffaireId() { return affaireId; }
    public void setAffaireId(Long affaireId) { this.affaireId = affaireId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public RoleSurAffaire getRoleSurAffaire() { return roleSurAffaire; }
    public void setRoleSurAffaire(RoleSurAffaire roleSurAffaire) { this.roleSurAffaire = roleSurAffaire; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public Affaire getAffaire() { return affaire; }
    public void setAffaire(Affaire affaire) { this.affaire = affaire; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AffaireActeur that = (AffaireActeur) o;
        return Objects.equals(affaireId, that.affaireId) &&
                Objects.equals(agentId, that.agentId) &&
                roleSurAffaire == that.roleSurAffaire;
    }

    @Override
    public int hashCode() {
        return Objects.hash(affaireId, agentId, roleSurAffaire);
    }
}
