package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.ContraventionDAO;
import com.regulation.contentieux.model.Contravention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service métier pour la gestion des contraventions
 * HARMONISÉ AVEC LES AUTRES SERVICES
 */
public class ContraventionService {

    private static final Logger logger = LoggerFactory.getLogger(ContraventionService.class);

    private final ContraventionDAO contraventionDAO;
    private final ValidationService validationService;

    public ContraventionService() {
        this.contraventionDAO = new ContraventionDAO();
        this.validationService = ValidationService.getInstance(); // Utiliser getInstance() au lieu de new
    }

    /**
     * Recherche de contraventions avec pagination - POUR ReferentielController
     */
    public List<Contravention> searchContraventions(String libelleOuCode, Boolean actifOnly, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return contraventionDAO.searchContraventions(libelleOuCode, actifOnly, offset, pageSize);
    }

    /**
     * Compte le nombre total de contraventions pour la recherche
     */
    public long countSearchContraventions(String libelleOuCode, Boolean actifOnly) {
        return contraventionDAO.countSearchContraventions(libelleOuCode, actifOnly);
    }

    /**
     * Trouve une contravention par son ID
     */
    public Optional<Contravention> findById(Long id) {
        return contraventionDAO.findById(id);
    }

    /**
     * Trouve une contravention par son code
     */
    public Optional<Contravention> findByCode(String code) {
        return contraventionDAO.findByCode(code);
    }

    /**
     * Sauvegarde une nouvelle contravention
     */
    public Contravention saveContravention(Contravention contravention) {
        // Validation des données
        validateContravention(contravention);

        // Génération automatique du code si nécessaire
        if (contravention.getCode() == null || contravention.getCode().trim().isEmpty()) {
            String nouveauCode = contraventionDAO.generateNextCodeContravention();
            contravention.setCode(nouveauCode);
            logger.info("Code généré automatiquement: {}", nouveauCode);
        }

        // Vérification de l'unicité du code
        if (contraventionDAO.existsByCode(contravention.getCode())) {
            throw new IllegalArgumentException("Ce code de contravention existe déjà: " + contravention.getCode());
        }

        // Sauvegarde
        Contravention savedContravention = contraventionDAO.save(contravention);
        logger.info("Contravention créée: {} - {}", savedContravention.getCode(), savedContravention.getLibelle());

        return savedContravention;
    }

    /**
     * Met à jour une contravention existante
     */
    public Contravention updateContravention(Contravention contravention) {
        // Validation des données
        validateContravention(contravention);

        // Vérification de l'existence
        if (!contraventionDAO.existsById(contravention.getId())) {
            throw new IllegalArgumentException("Contravention non trouvée avec l'ID: " + contravention.getId());
        }

        // Vérification de l'unicité du code (sauf pour l'enregistrement actuel)
        Optional<Contravention> existing = contraventionDAO.findByCode(contravention.getCode());
        if (existing.isPresent() && !existing.get().getId().equals(contravention.getId())) {
            throw new IllegalArgumentException("Ce code de contravention existe déjà: " + contravention.getCode());
        }

        // Mise à jour
        Contravention updatedContravention = contraventionDAO.update(contravention);
        logger.info("Contravention mise à jour: {} - {}", updatedContravention.getCode(), updatedContravention.getLibelle());

        return updatedContravention;
    }

    /**
     * Supprime une contravention
     */
    public void deleteContravention(Long id) {
        Optional<Contravention> contravention = contraventionDAO.findById(id);
        if (contravention.isEmpty()) {
            throw new IllegalArgumentException("Contravention non trouvée avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des affaires liées avant suppression
        // Pour l'instant, suppression directe
        contraventionDAO.deleteById(id);
        logger.info("Contravention supprimée: {} - {}", contravention.get().getCode(), contravention.get().getLibelle());
    }

    /**
     * Active/désactive une contravention
     */
    public void toggleActifContravention(Long id) {
        Optional<Contravention> optContravention = contraventionDAO.findById(id);
        if (optContravention.isEmpty()) {
            throw new IllegalArgumentException("Contravention non trouvée avec l'ID: " + id);
        }

        Contravention contravention = optContravention.get();
        contravention.setActif(!contravention.isActif());

        contraventionDAO.update(contravention);
        logger.info("Contravention {} {}: {}",
                contravention.getCode(),
                contravention.isActif() ? "activée" : "désactivée",
                contravention.getLibelle());
    }

    /**
     * Liste toutes les contraventions avec pagination
     */
    public List<Contravention> getAllContraventions(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return contraventionDAO.findAll(offset, pageSize);
    }

    /**
     * Compte le nombre total de contraventions
     */
    public long getTotalCount() {
        return contraventionDAO.count();
    }

    /**
     * Obtient les contraventions actives pour les ComboBox
     */
    public List<Contravention> getActiveContraventions() {
        return contraventionDAO.findAllActive();
    }

    /**
     * Génère le prochain code de contravention
     */
    public String generateNextCode() {
        return contraventionDAO.generateNextCodeContravention();
    }

    /**
     * Valide les données d'une contravention
     */
    private void validateContravention(Contravention contravention) {
        if (contravention == null) {
            throw new IllegalArgumentException("La contravention ne peut pas être null");
        }

        // Validation du code
        if (contravention.getCode() == null || contravention.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code est obligatoire");
        }
        if (contravention.getCode().length() < 2 || contravention.getCode().length() > 10) {
            throw new IllegalArgumentException("Le code doit contenir entre 2 et 10 caractères");
        }

        // Validation du libellé
        if (contravention.getLibelle() == null || contravention.getLibelle().trim().isEmpty()) {
            throw new IllegalArgumentException("Le libellé est obligatoire");
        }
        if (contravention.getLibelle().length() < 3 || contravention.getLibelle().length() > 200) {
            throw new IllegalArgumentException("Le libellé doit contenir entre 3 et 200 caractères");
        }

        // Validation de la description (optionnelle)
        if (contravention.getDescription() != null && contravention.getDescription().length() > 1000) {
            throw new IllegalArgumentException("La description ne peut pas dépasser 1000 caractères");
        }

        logger.debug("Validation réussie pour la contravention: {}", contravention.getCode());
    }

    /**
     * Méthode utilitaire pour valider une chaîne avec longueur min/max
     * Remplace l'appel à isValidString qui n'existe pas dans ValidationService
     */
    private boolean isValidString(String str, int minLength, int maxLength) {
        if (str == null || str.trim().isEmpty()) {
            return minLength == 0; // Retourne true si la chaîne peut être vide
        }
        String trimmed = str.trim();
        return trimmed.length() >= minLength && trimmed.length() <= maxLength;
    }

    /**
     * Vérifie si un code existe déjà
     */
    public boolean codeExists(String code) {
        return contraventionDAO.existsByCode(code);
    }
}