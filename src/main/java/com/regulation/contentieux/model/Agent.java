package com.regulation.contentieux.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entité représentant un agent de la régulation
 * Un agent peut verbaliser et gérer des affaires contentieuses
 * ENRICHI : Ajout du roleSpecial pour DD et DG
 */
public class Agent {

    private Long id;
    private String codeAgent;
    private String nom;
    private String prenom;
    private String grade;
    private String email;
    private String telephone;
    private Boolean actif; // Changé de boolean à Boolean pour la compatibilité
    private Long serviceId; // Ajout pour compatibilité avec DAO

    // ENRICHISSEMENT : Rôle spécial (DD, DG, etc.)
    private String roleSpecial;

    // Relations
    private Service service;
    private Bureau bureau;

    // Métadonnées
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    // Constructeurs
    public Agent() {
        this.actif = true;
        this.createdAt = LocalDateTime.now();
    }

    public Agent(String codeAgent, String nom, String prenom) {
        this();
        this.codeAgent = codeAgent;
        this.nom = nom;
        this.prenom = prenom;
    }

    // Méthodes métier

    /**
     * Retourne le nom complet de l'agent
     */
    public String getNomComplet() {
        return prenom + " " + nom;
    }

    /**
     * Retourne le nom d'affichage (Code - Nom complet)
     * ENRICHI : Inclut le rôle spécial si présent
     */
    public String getDisplayName() {
        String display = codeAgent + " - " + getNomComplet();
        if (hasRoleSpecial()) {
            display += " (" + roleSpecial + ")";
        }
        return display;
    }

    /**
     * Vérifie si l'agent appartient à un service
     */
    public boolean hasService() {
        return service != null || serviceId != null;
    }

    /**
     * Vérifie si l'agent appartient à un bureau
     */
    public boolean hasBureau() {
        return bureau != null;
    }

    /**
     * ENRICHISSEMENT : Vérifie si l'agent a un rôle spécial
     */
    public boolean hasRoleSpecial() {
        return roleSpecial != null && !roleSpecial.trim().isEmpty();
    }

    /**
     * ENRICHISSEMENT : Vérifie si l'agent est DD
     */
    public boolean isDD() {
        return "DD".equals(roleSpecial);
    }

    /**
     * ENRICHISSEMENT : Vérifie si l'agent est DG
     */
    public boolean isDG() {
        return "DG".equals(roleSpecial);
    }

    /**
     * ENRICHISSEMENT : Vérifie si l'agent est DD ou DG
     */
    public boolean isDDorDG() {
        return isDD() || isDG();
    }

    /**
     * Retourne une représentation courte
     * ENRICHI : Inclut le rôle spécial
     */
    public String getShortDisplay() {
        String display = codeAgent + " (" + nom + ")";
        if (hasRoleSpecial()) {
            display = "[" + roleSpecial + "] " + display;
        }
        return display;
    }

    /**
     * Retourne le matricule de l'agent (alias pour codeAgent)
     * Utilisé dans les rapports pour l'affichage du matricule
     *
     * @return le code agent qui sert de matricule
     */
    public String getMatricule() {
        return codeAgent;
    }

    // Getters et Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodeAgent() {
        return codeAgent;
    }

    public void setCodeAgent(String codeAgent) {
        this.codeAgent = codeAgent;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public boolean isActif() {
        return actif != null ? actif : true;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
        // Si on a déjà un objet Service, synchroniser
        if (this.service != null && this.service.getId() != null && !this.service.getId().equals(serviceId)) {
            this.service = null; // Reset service si l'ID change
        }
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
        // Synchroniser serviceId
        if (service != null && service.getId() != null) {
            this.serviceId = service.getId();
        } else {
            this.serviceId = null;
        }
    }

    public Bureau getBureau() {
        return bureau;
    }

    public void setBureau(Bureau bureau) {
        this.bureau = bureau;
    }

    /**
     * ENRICHISSEMENT : Getter/Setter pour roleSpecial
     */
    public String getRoleSpecial() {
        return roleSpecial;
    }

    public void setRoleSpecial(String roleSpecial) {
        this.roleSpecial = roleSpecial;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Agent agent = (Agent) o;
        return Objects.equals(id, agent.id) && Objects.equals(codeAgent, agent.codeAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, codeAgent);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}