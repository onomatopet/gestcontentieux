package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.BureauDAO;
import com.regulation.contentieux.model.Bureau;
import com.regulation.contentieux.util.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service de gestion des bureaux
 * Gère la logique métier liée aux bureaux
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class BureauService {

    private static final Logger logger = LoggerFactory.getLogger(BureauService.class);

    private final BureauDAO bureauDAO;
    private final ValidationService validationService;

    public BureauService() {
        this.bureauDAO = new BureauDAO();
        this.validationService = ValidationService.getInstance();
    }

    /**
     * Récupère tous les bureaux
     * @return Liste de tous les bureaux
     */
    public List<Bureau> getAllBureaux() {
        logger.debug("Récupération de tous les bureaux");
        return bureauDAO.findAll();
    }

    /**
     * Récupère tous les bureaux avec pagination
     * @param page Numéro de page (commence à 1)
     * @param pageSize Taille de la page
     * @return Liste paginée des bureaux
     */
    public List<Bureau> getAllBureaux(int page, int pageSize) {
        logger.debug("Récupération des bureaux - page: {}, taille: {}", page, pageSize);
        int offset = (page - 1) * pageSize;
        return bureauDAO.findAll(); // TODO: Implémenter la pagination dans le DAO si nécessaire
    }

    /**
     * Récupère tous les bureaux actifs
     * @return Liste des bureaux actifs
     */
    public List<Bureau> getAllBureauxActifs() {
        logger.debug("Récupération des bureaux actifs");
        return bureauDAO.findAllActive();
    }

    /**
     * Récupère les bureaux d'un service
     * @param serviceId ID du service
     * @return Liste des bureaux du service
     */
    public List<Bureau> getBureauxByService(Long serviceId) {
        if (serviceId == null) {
            logger.warn("Tentative de recherche avec un serviceId null");
            return List.of();
        }
        return bureauDAO.findByServiceId(serviceId);
    }

    /**
     * Trouve un bureau par son ID
     * @param id ID du bureau
     * @return Bureau trouvé ou Optional.empty()
     */
    public Optional<Bureau> findById(Long id) {
        if (id == null) {
            logger.warn("Tentative de recherche avec un ID null");
            return Optional.empty();
        }
        return bureauDAO.findById(id);
    }

    /**
     * Trouve un bureau par son code
     * @param code Code du bureau
     * @return Bureau trouvé ou Optional.empty()
     */
    public Optional<Bureau> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            logger.warn("Tentative de recherche avec un code vide");
            return Optional.empty();
        }
        return bureauDAO.findByCodeBureau(code.trim());
    }

    /**
     * Crée un nouveau bureau
     * @param bureau Bureau à créer
     * @return Bureau créé avec son ID
     */
    public Bureau createBureau(Bureau bureau) {
        logger.info("Création d'un nouveau bureau: {}", bureau.getNomBureau());

        // Validation
        validateBureau(bureau);

        // Vérification d'unicité du code
        if (bureauDAO.existsByCodeBureau(bureau.getCodeBureau())) {
            throw new IllegalArgumentException("Un bureau avec ce code existe déjà: " + bureau.getCodeBureau());
        }

        // Normalisation
        bureau.setNomBureau(validationService.normalizeText(bureau.getNomBureau()));
        if (bureau.getDescription() != null) {
            bureau.setDescription(validationService.normalizeText(bureau.getDescription()));
        }

        Bureau saved = bureauDAO.save(bureau);
        logger.info("Bureau créé avec succès - ID: {}, Code: {}", saved.getId(), saved.getCodeBureau());

        return saved;
    }

    /**
     * Met à jour un bureau existant
     * @param bureau Bureau à mettre à jour
     * @return Bureau mis à jour
     */
    public Bureau updateBureau(Bureau bureau) {
        logger.info("Mise à jour du bureau ID: {}", bureau.getId());

        if (bureau.getId() == null) {
            throw new IllegalArgumentException("L'ID du bureau est requis pour la mise à jour");
        }

        // Vérifier l'existence
        Optional<Bureau> existing = bureauDAO.findById(bureau.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Bureau non trouvé avec l'ID: " + bureau.getId());
        }

        // Validation
        validateBureau(bureau);

        // Vérification d'unicité du code (sauf pour lui-même)
        Optional<Bureau> byCode = bureauDAO.findByCodeBureau(bureau.getCodeBureau());
        if (byCode.isPresent() && !byCode.get().getId().equals(bureau.getId())) {
            throw new IllegalArgumentException("Un autre bureau utilise déjà ce code: " + bureau.getCodeBureau());
        }

        // Normalisation
        bureau.setNomBureau(validationService.normalizeText(bureau.getNomBureau()));
        if (bureau.getDescription() != null) {
            bureau.setDescription(validationService.normalizeText(bureau.getDescription()));
        }

        Bureau updated = bureauDAO.update(bureau);
        logger.info("Bureau mis à jour avec succès - Code: {}", updated.getCodeBureau());

        return updated;
    }

    /**
     * Supprime un bureau
     * @param id ID du bureau à supprimer
     */
    public void deleteBureau(Long id) {
        logger.info("Suppression du bureau ID: {}", id);

        Optional<Bureau> bureau = bureauDAO.findById(id);
        if (bureau.isEmpty()) {
            throw new IllegalArgumentException("Bureau non trouvé avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des agents liés avant suppression

        bureauDAO.deleteById(id);
        logger.info("Bureau supprimé: {} - {}", bureau.get().getCodeBureau(), bureau.get().getNomBureau());
    }

    /**
     * Active/désactive un bureau
     * @param id ID du bureau
     * @param actif Nouvel état
     */
    public void toggleBureauActif(Long id, boolean actif) {
        Optional<Bureau> optBureau = bureauDAO.findById(id);
        if (optBureau.isEmpty()) {
            throw new IllegalArgumentException("Bureau non trouvé avec l'ID: " + id);
        }

        Bureau bureau = optBureau.get();
        bureau.setActif(actif);

        bureauDAO.update(bureau);
        logger.info("Bureau {} {}: {}",
                bureau.getCodeBureau(),
                actif ? "activé" : "désactivé",
                bureau.getNomBureau());
    }

    /**
     * Recherche des bureaux
     * @param query Texte de recherche
     * @return Liste des bureaux correspondants
     */
    public List<Bureau> searchBureaux(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllBureaux();
        }

        logger.debug("Recherche de bureaux avec: {}", query);
        return bureauDAO.searchBureaux(query.trim(), null, 0, 100);
    }

    /**
     * Compte le nombre total de bureaux
     * @return Nombre de bureaux
     */
    public long countBureaux() {
        return bureauDAO.count();
    }

    /**
     * Compte le nombre de bureaux par service
     * @param serviceId ID du service
     * @return Nombre de bureaux
     */
    public long countBureauxByService(Long serviceId) {
        if (serviceId == null) {
            return 0;
        }
        return bureauDAO.countByServiceId(serviceId);
    }

    /**
     * Valide les données d'un bureau
     * @param bureau Bureau à valider
     */
    private void validateBureau(Bureau bureau) {
        if (bureau == null) {
            throw new IllegalArgumentException("Le bureau ne peut pas être null");
        }

        // Code obligatoire
        if (bureau.getCodeBureau() == null || bureau.getCodeBureau().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code du bureau est obligatoire");
        }

        // Nom obligatoire
        if (bureau.getNomBureau() == null || bureau.getNomBureau().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du bureau est obligatoire");
        }

        // Service obligatoire
        if (bureau.getService() == null && bureau.getServiceId() == null) {
            throw new IllegalArgumentException("Le service du bureau est obligatoire");
        }

        // Validation du format du code
        if (!bureau.getCodeBureau().matches("^[A-Z0-9]{2,10}$")) {
            throw new IllegalArgumentException("Le code du bureau doit contenir entre 2 et 10 caractères alphanumériques majuscules");
        }

        // Validation de la longueur du nom
        if (bureau.getNomBureau().trim().length() < 3 || bureau.getNomBureau().trim().length() > 100) {
            throw new IllegalArgumentException("Le nom du bureau doit contenir entre 3 et 100 caractères");
        }
    }

    /**
     * Génère le prochain code de bureau
     * @return Nouveau code généré
     */
    public String generateNextCode() {
        // Récupérer le dernier code
        List<Bureau> bureaux = bureauDAO.findAll();
        if (bureaux.isEmpty()) {
            return "BUR001";
        }

        // Trouver le plus grand numéro
        int maxNum = 0;
        for (Bureau b : bureaux) {
            String code = b.getCodeBureau();
            if (code != null && code.startsWith("BUR")) {
                try {
                    int num = Integer.parseInt(code.substring(3));
                    maxNum = Math.max(maxNum, num);
                } catch (NumberFormatException e) {
                    // Ignorer les codes non conformes
                }
            }
        }

        return String.format("BUR%03d", maxNum + 1);
    }
}