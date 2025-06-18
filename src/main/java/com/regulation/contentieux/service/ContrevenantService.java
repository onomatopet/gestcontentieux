package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.model.Contrevenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service métier pour la gestion des contrevenants
 * Suit la même logique que les autres services
 */
public class ContrevenantService {

    private static final Logger logger = LoggerFactory.getLogger(ContrevenantService.class);

    private final ContrevenantDAO contrevenantDAO;
    private final ValidationService validationService;

    public ContrevenantService() {
        this.contrevenantDAO = new ContrevenantDAO();
        this.validationService = new ValidationService();
    }

    /**
     * Recherche de contrevenants avec pagination
     */
    public List<Contrevenant> searchContrevenants(String nomOuCode, String typePersonne,
                                                  int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return contrevenantDAO.searchContrevenants(nomOuCode, typePersonne, offset, pageSize);
    }

    /**
     * Compte le nombre total de contrevenants pour la recherche
     */
    public long countSearchContrevenants(String nomOuCode, String typePersonne) {
        return contrevenantDAO.countSearchContrevenants(nomOuCode, typePersonne);
    }

    /**
     * Trouve un contrevenant par son ID
     */
    public Optional<Contrevenant> findById(Long id) {
        return contrevenantDAO.findById(id);
    }

    /**
     * Trouve un contrevenant par son code
     */
    public Optional<Contrevenant> findByCode(String code) {
        return contrevenantDAO.findByCode(code);
    }

    /**
     * Sauvegarde un nouveau contrevenant
     */
    public Contrevenant saveContrevenant(Contrevenant contrevenant) {
        // Validation des données
        validateContrevenant(contrevenant);

        // Génération du code si nécessaire
        if (contrevenant.getCode() == null || contrevenant.getCode().trim().isEmpty()) {
            String nextCode = contrevenantDAO.generateNextCode();
            contrevenant.setCode(nextCode);
        }

        // Vérification d'unicité du code
        if (contrevenantDAO.existsByCode(contrevenant.getCode())) {
            throw new IllegalArgumentException("Un contrevenant avec ce code existe déjà: " + contrevenant.getCode());
        }

        // Sauvegarde
        Contrevenant saved = contrevenantDAO.save(contrevenant);
        logger.info("Nouveau contrevenant créé: {} - {}", saved.getCode(), saved.getNomComplet());

        return saved;
    }

    /**
     * Met à jour un contrevenant existant
     */
    public Contrevenant updateContrevenant(Contrevenant contrevenant) {
        if (contrevenant.getId() == null) {
            throw new IllegalArgumentException("L'ID du contrevenant est requis pour la mise à jour");
        }

        // Validation des données
        validateContrevenant(contrevenant);

        // Vérification que le contrevenant existe
        Optional<Contrevenant> existing = contrevenantDAO.findById(contrevenant.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Contrevenant non trouvé avec l'ID: " + contrevenant.getId());
        }

        // Vérification d'unicité du code (sauf pour lui-même)
        Optional<Contrevenant> byCode = contrevenantDAO.findByCode(contrevenant.getCode());
        if (byCode.isPresent() && !byCode.get().getId().equals(contrevenant.getId())) {
            throw new IllegalArgumentException("Un autre contrevenant utilise déjà ce code: " + contrevenant.getCode());
        }

        // Mise à jour
        Contrevenant updated = contrevenantDAO.update(contrevenant);
        logger.info("Contrevenant mis à jour: {} - {}", updated.getCode(), updated.getNomComplet());

        return updated;
    }

    /**
     * Supprime un contrevenant
     */
    public void deleteContrevenant(Long id) {
        Optional<Contrevenant> contrevenant = contrevenantDAO.findById(id);
        if (contrevenant.isEmpty()) {
            throw new IllegalArgumentException("Contrevenant non trouvé avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des affaires liées avant suppression
        // Pour l'instant, suppression directe
        contrevenantDAO.deleteById(id);
        logger.info("Contrevenant supprimé: {} - {}", contrevenant.get().getCode(), contrevenant.get().getNomComplet());
    }

    /**
     * Liste tous les contrevenants avec pagination
     */
    public List<Contrevenant> getAllContrevenants(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return contrevenantDAO.findAll(offset, pageSize);
    }

    /**
     * Compte le nombre total de contrevenants
     */
    public long getTotalCount() {
        return contrevenantDAO.count();
    }

    /**
     * Trouve les contrevenants par type de personne
     */
    public List<Contrevenant> findByTypePersonne(String typePersonne) {
        return contrevenantDAO.findByTypePersonne(typePersonne);
    }

    /**
     * Obtient les contrevenants récents pour le tableau de bord
     */
    public List<Contrevenant> getRecentContrevenants(int limit) {
        return contrevenantDAO.getRecentContrevenants(limit);
    }

    /**
     * Valide les données d'un contrevenant
     */
    private void validateContrevenant(Contrevenant contrevenant) {
        if (contrevenant == null) {
            throw new IllegalArgumentException("Le contrevenant ne peut pas être null");
        }

        // Validation du nom complet
        if (!validationService.isValidString(contrevenant.getNomComplet(), 2, 200)) {
            throw new IllegalArgumentException("Le nom complet doit contenir entre 2 et 200 caractères");
        }

        // Validation du code (si fourni)
        if (contrevenant.getCode() != null && !contrevenant.getCode().trim().isEmpty()) {
            if (!validationService.isValidCode(contrevenant.getCode())) {
                throw new IllegalArgumentException("Format de code invalide");
            }
        }

        // Validation de l'email (si fourni)
        if (contrevenant.getEmail() != null && !contrevenant.getEmail().trim().isEmpty()) {
            if (!validationService.isValidEmail(contrevenant.getEmail())) {
                throw new IllegalArgumentException("Format d'email invalide");
            }
        }

        // Validation du téléphone (si fourni)
        if (contrevenant.getTelephone() != null && !contrevenant.getTelephone().trim().isEmpty()) {
            if (!validationService.isValidPhone(contrevenant.getTelephone())) {
                throw new IllegalArgumentException("Format de téléphone invalide");
            }
        }

        // Validation du type de personne
        if (contrevenant.getTypePersonne() != null) {
            if (!contrevenant.getTypePersonne().equals("PHYSIQUE") &&
                    !contrevenant.getTypePersonne().equals("MORALE")) {
                throw new IllegalArgumentException("Type de personne doit être PHYSIQUE ou MORALE");
            }
        }
    }

    /**
     * Génère le prochain code contrevenant
     */
    public String generateNextCode() {
        return contrevenantDAO.generateNextCode();
    }

    /**
     * Vérifie si un code existe déjà
     */
    public boolean codeExists(String code) {
        return contrevenantDAO.existsByCode(code);
    }

    /**
     * Recherche rapide par nom ou code (pour autocomplete)
     */
    public List<Contrevenant> searchQuick(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        return contrevenantDAO.searchContrevenants(query.trim(), null, 0, limit);
    }
}