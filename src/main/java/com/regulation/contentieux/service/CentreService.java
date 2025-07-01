package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.CentreDAO;
import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service de gestion des centres
 * Gère la logique métier liée aux centres
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class CentreService {

    private static final Logger logger = LoggerFactory.getLogger(CentreService.class);

    private final CentreDAO centreDAO;
    private final ValidationService validationService;

    public CentreService() {
        this.centreDAO = new CentreDAO();
        this.validationService = ValidationService.getInstance();
    }

    /**
     * Récupère tous les centres
     * @return Liste de tous les centres
     */
    public List<Centre> getAllCentres() {
        logger.debug("Récupération de tous les centres");
        return centreDAO.findAll();
    }

    /**
     * Récupère tous les centres avec pagination
     * @param page Numéro de page (commence à 1)
     * @param pageSize Taille de la page
     * @return Liste paginée des centres
     */
    public List<Centre> getAllCentres(int page, int pageSize) {
        logger.debug("Récupération des centres - page: {}, taille: {}", page, pageSize);
        int offset = (page - 1) * pageSize;
        return centreDAO.findAll(); // TODO: Implémenter la pagination dans le DAO si nécessaire
    }

    /**
     * Récupère tous les centres actifs
     * @return Liste des centres actifs
     */
    public List<Centre> getAllCentresActifs() {
        logger.debug("Récupération des centres actifs");
        return centreDAO.findAllActive();
    }

    /**
     * Trouve un centre par son ID
     * @param id ID du centre
     * @return Centre trouvé ou Optional.empty()
     */
    public Optional<Centre> findById(Long id) {
        if (id == null) {
            logger.warn("Tentative de recherche avec un ID null");
            return Optional.empty();
        }
        return centreDAO.findById(id);
    }

    /**
     * Trouve un centre par son code
     * @param code Code du centre
     * @return Centre trouvé ou Optional.empty()
     */
    public Optional<Centre> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            logger.warn("Tentative de recherche avec un code vide");
            return Optional.empty();
        }
        return centreDAO.findByCodeCentre(code.trim());
    }

    /**
     * Crée un nouveau centre
     * @param centre Centre à créer
     * @return Centre créé avec son ID
     */
    public Centre createCentre(Centre centre) {
        logger.info("Création d'un nouveau centre: {}", centre.getNomCentre());

        // Validation
        validateCentre(centre);

        // Vérification d'unicité du code
        if (centreDAO.existsByCodeCentre(centre.getCodeCentre())) {
            throw new IllegalArgumentException("Un centre avec ce code existe déjà: " + centre.getCodeCentre());
        }

        // Normalisation
        centre.setNomCentre(validationService.normalizeText(centre.getNomCentre()));
        if (centre.getDescription() != null) {
            centre.setDescription(validationService.normalizeText(centre.getDescription()));
        }

        Centre saved = centreDAO.save(centre);
        logger.info("Centre créé avec succès - ID: {}, Code: {}", saved.getId(), saved.getCodeCentre());

        return saved;
    }

    /**
     * Met à jour un centre existant
     * @param centre Centre à mettre à jour
     * @return Centre mis à jour
     */
    public Centre updateCentre(Centre centre) {
        logger.info("Mise à jour du centre ID: {}", centre.getId());

        if (centre.getId() == null) {
            throw new IllegalArgumentException("L'ID du centre est requis pour la mise à jour");
        }

        // Vérifier l'existence
        Optional<Centre> existing = centreDAO.findById(centre.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Centre non trouvé avec l'ID: " + centre.getId());
        }

        // Validation
        validateCentre(centre);

        // Vérification d'unicité du code (sauf pour lui-même)
        Optional<Centre> byCode = centreDAO.findByCodeCentre(centre.getCodeCentre());
        if (byCode.isPresent() && !byCode.get().getId().equals(centre.getId())) {
            throw new IllegalArgumentException("Un autre centre utilise déjà ce code: " + centre.getCodeCentre());
        }

        // Normalisation
        centre.setNomCentre(validationService.normalizeText(centre.getNomCentre()));
        if (centre.getDescription() != null) {
            centre.setDescription(validationService.normalizeText(centre.getDescription()));
        }

        Centre updated = centreDAO.update(centre);
        logger.info("Centre mis à jour avec succès - Code: {}", updated.getCodeCentre());

        return updated;
    }

    /**
     * Supprime un centre
     * @param id ID du centre à supprimer
     */
    public void deleteCentre(Long id) {
        logger.info("Suppression du centre ID: {}", id);

        Optional<Centre> centre = centreDAO.findById(id);
        if (centre.isEmpty()) {
            throw new IllegalArgumentException("Centre non trouvé avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des services liés avant suppression

        centreDAO.deleteById(id);
        logger.info("Centre supprimé: {} - {}", centre.get().getCodeCentre(), centre.get().getNomCentre());
    }

    /**
     * Active/désactive un centre
     * @param id ID du centre
     * @param actif Nouvel état
     */
    public void toggleCentreActif(Long id, boolean actif) {
        Optional<Centre> optCentre = centreDAO.findById(id);
        if (optCentre.isEmpty()) {
            throw new IllegalArgumentException("Centre non trouvé avec l'ID: " + id);
        }

        Centre centre = optCentre.get();
        centre.setActif(actif);

        centreDAO.update(centre);
        logger.info("Centre {} {}: {}",
                centre.getCodeCentre(),
                actif ? "activé" : "désactivé",
                centre.getNomCentre());
    }

    /**
     * Recherche des centres
     * @param query Texte de recherche
     * @return Liste des centres correspondants
     */
    public List<Centre> searchCentres(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllCentres();
        }

        logger.debug("Recherche de centres avec: {}", query);
        return centreDAO.searchCentres(query.trim(), null, 0, 100);
    }

    /**
     * Compte le nombre total de centres
     * @return Nombre de centres
     */
    public long countCentres() {
        return centreDAO.count();
    }

    /**
     * Valide les données d'un centre
     * @param centre Centre à valider
     */
    private void validateCentre(Centre centre) {
        if (centre == null) {
            throw new IllegalArgumentException("Le centre ne peut pas être null");
        }

        // Code obligatoire
        if (centre.getCodeCentre() == null || centre.getCodeCentre().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code du centre est obligatoire");
        }

        // Nom obligatoire
        if (centre.getNomCentre() == null || centre.getNomCentre().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du centre est obligatoire");
        }

        // Validation du format du code
        if (!centre.getCodeCentre().matches("^[A-Z0-9]{2,10}$")) {
            throw new IllegalArgumentException("Le code du centre doit contenir entre 2 et 10 caractères alphanumériques majuscules");
        }

        // Validation de la longueur du nom
        if (centre.getNomCentre().trim().length() < 3 || centre.getNomCentre().trim().length() > 100) {
            throw new IllegalArgumentException("Le nom du centre doit contenir entre 3 et 100 caractères");
        }
    }

    /**
     * Génère le prochain code de centre
     * @return Nouveau code généré
     */
    public String generateNextCode() {
        // Récupérer le dernier code
        List<Centre> centres = centreDAO.findAll();
        if (centres.isEmpty()) {
            return "CTR001";
        }

        // Trouver le plus grand numéro
        int maxNum = 0;
        for (Centre c : centres) {
            String code = c.getCodeCentre();
            if (code != null && code.startsWith("CTR")) {
                try {
                    int num = Integer.parseInt(code.substring(3));
                    maxNum = Math.max(maxNum, num);
                } catch (NumberFormatException e) {
                    // Ignorer les codes non conformes
                }
            }
        }

        return String.format("CTR%03d", maxNum + 1);
    }
}