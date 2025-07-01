package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.BanqueDAO;
import com.regulation.contentieux.model.Banque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service de gestion des banques
 * Gère la logique métier liée aux banques
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class BanqueService {

    private static final Logger logger = LoggerFactory.getLogger(BanqueService.class);

    private final BanqueDAO banqueDAO;
    private final ValidationService validationService;

    public BanqueService() {
        this.banqueDAO = new BanqueDAO();
        this.validationService = ValidationService.getInstance();
    }

    /**
     * Récupère toutes les banques
     * @return Liste de toutes les banques
     */
    public List<Banque> getAllBanques() {
        logger.debug("Récupération de toutes les banques");
        return banqueDAO.findAll();
    }

    /**
     * Récupère toutes les banques avec pagination
     * @param page Numéro de page (commence à 1)
     * @param pageSize Taille de la page
     * @return Liste paginée des banques
     */
    public List<Banque> getAllBanques(int page, int pageSize) {
        logger.debug("Récupération des banques - page: {}, taille: {}", page, pageSize);
        int offset = (page - 1) * pageSize;
        return banqueDAO.findAll(); // TODO: Implémenter la pagination dans le DAO si nécessaire
    }

    /**
     * Récupère toutes les banques actives
     * @return Liste des banques actives
     */
    public List<Banque> getAllBanquesActives() {
        logger.debug("Récupération des banques actives");
        return banqueDAO.findAllActive();
    }

    /**
     * Trouve une banque par son ID
     * @param id ID de la banque
     * @return Banque trouvée ou Optional.empty()
     */
    public Optional<Banque> findById(Long id) {
        if (id == null) {
            logger.warn("Tentative de recherche avec un ID null");
            return Optional.empty();
        }
        return banqueDAO.findById(id);
    }

    /**
     * Trouve une banque par son code
     * @param code Code de la banque
     * @return Banque trouvée ou Optional.empty()
     */
    public Optional<Banque> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            logger.warn("Tentative de recherche avec un code vide");
            return Optional.empty();
        }
        return banqueDAO.findByCodeBanque(code.trim());
    }

    /**
     * Crée une nouvelle banque
     * @param banque Banque à créer
     * @return Banque créée avec son ID
     */
    public Banque createBanque(Banque banque) {
        logger.info("Création d'une nouvelle banque: {}", banque.getNomBanque());

        // Validation
        validateBanque(banque);

        // Vérification d'unicité du code
        if (banqueDAO.existsByCodeBanque(banque.getCodeBanque())) {
            throw new IllegalArgumentException("Une banque avec ce code existe déjà: " + banque.getCodeBanque());
        }

        // Normalisation
        banque.setNomBanque(validationService.normalizeText(banque.getNomBanque()));
        if (banque.getDescription() != null) {
            banque.setDescription(validationService.normalizeText(banque.getDescription()));
        }
        if (banque.getAdresse() != null) {
            banque.setAdresse(validationService.normalizeText(banque.getAdresse()));
        }

        // Validation email si présent
        if (banque.getEmail() != null && !banque.getEmail().isEmpty()) {
            if (!validationService.isValidEmail(banque.getEmail())) {
                throw new IllegalArgumentException("Format d'email invalide: " + banque.getEmail());
            }
        }

        // Validation téléphone si présent
        if (banque.getTelephone() != null && !banque.getTelephone().isEmpty()) {
            banque.setTelephone(validationService.formatPhoneNumber(banque.getTelephone()));
        }

        Banque saved = banqueDAO.save(banque);
        logger.info("Banque créée avec succès - ID: {}, Code: {}", saved.getId(), saved.getCodeBanque());

        return saved;
    }

    /**
     * Met à jour une banque existante
     * @param banque Banque à mettre à jour
     * @return Banque mise à jour
     */
    public Banque updateBanque(Banque banque) {
        logger.info("Mise à jour de la banque ID: {}", banque.getId());

        if (banque.getId() == null) {
            throw new IllegalArgumentException("L'ID de la banque est requis pour la mise à jour");
        }

        // Vérifier l'existence
        Optional<Banque> existing = banqueDAO.findById(banque.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Banque non trouvée avec l'ID: " + banque.getId());
        }

        // Validation
        validateBanque(banque);

        // Vérification d'unicité du code (sauf pour elle-même)
        Optional<Banque> byCode = banqueDAO.findByCodeBanque(banque.getCodeBanque());
        if (byCode.isPresent() && !byCode.get().getId().equals(banque.getId())) {
            throw new IllegalArgumentException("Une autre banque utilise déjà ce code: " + banque.getCodeBanque());
        }

        // Normalisation
        banque.setNomBanque(validationService.normalizeText(banque.getNomBanque()));
        if (banque.getDescription() != null) {
            banque.setDescription(validationService.normalizeText(banque.getDescription()));
        }
        if (banque.getAdresse() != null) {
            banque.setAdresse(validationService.normalizeText(banque.getAdresse()));
        }

        // Validation email si présent
        if (banque.getEmail() != null && !banque.getEmail().isEmpty()) {
            if (!validationService.isValidEmail(banque.getEmail())) {
                throw new IllegalArgumentException("Format d'email invalide: " + banque.getEmail());
            }
        }

        // Validation téléphone si présent
        if (banque.getTelephone() != null && !banque.getTelephone().isEmpty()) {
            banque.setTelephone(validationService.formatPhoneNumber(banque.getTelephone()));
        }

        Banque updated = banqueDAO.update(banque);
        logger.info("Banque mise à jour avec succès - Code: {}", updated.getCodeBanque());

        return updated;
    }

    /**
     * Supprime une banque
     * @param id ID de la banque à supprimer
     */
    public void deleteBanque(Long id) {
        logger.info("Suppression de la banque ID: {}", id);

        Optional<Banque> banque = banqueDAO.findById(id);
        if (banque.isEmpty()) {
            throw new IllegalArgumentException("Banque non trouvée avec l'ID: " + id);
        }

        // TODO: Vérifier s'il y a des encaissements liés avant suppression

        banqueDAO.deleteById(id);
        logger.info("Banque supprimée: {} - {}", banque.get().getCodeBanque(), banque.get().getNomBanque());
    }

    /**
     * Active/désactive une banque
     * @param id ID de la banque
     * @param actif Nouvel état
     */
    public void toggleBanqueActif(Long id, boolean actif) {
        Optional<Banque> optBanque = banqueDAO.findById(id);
        if (optBanque.isEmpty()) {
            throw new IllegalArgumentException("Banque non trouvée avec l'ID: " + id);
        }

        Banque banque = optBanque.get();
        banque.setActif(actif);

        banqueDAO.update(banque);
        logger.info("Banque {} {}: {}",
                banque.getCodeBanque(),
                actif ? "activée" : "désactivée",
                banque.getNomBanque());
    }

    /**
     * Recherche des banques
     * @param query Texte de recherche
     * @return Liste des banques correspondantes
     */
    public List<Banque> searchBanques(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllBanques();
        }

        logger.debug("Recherche de banques avec: {}", query);
        return banqueDAO.searchBanques(query.trim(), null, 0, 100);
    }

    /**
     * Compte le nombre total de banques
     * @return Nombre de banques
     */
    public long countBanques() {
        return banqueDAO.count();
    }

    /**
     * Valide les données d'une banque
     * @param banque Banque à valider
     */
    private void validateBanque(Banque banque) {
        if (banque == null) {
            throw new IllegalArgumentException("La banque ne peut pas être null");
        }

        // Code obligatoire
        if (banque.getCodeBanque() == null || banque.getCodeBanque().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code de la banque est obligatoire");
        }

        // Nom obligatoire
        if (banque.getNomBanque() == null || banque.getNomBanque().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom de la banque est obligatoire");
        }

        // Validation du format du code
        if (!banque.getCodeBanque().matches("^[A-Z0-9]{2,10}$")) {
            throw new IllegalArgumentException("Le code de la banque doit contenir entre 2 et 10 caractères alphanumériques majuscules");
        }

        // Validation de la longueur du nom
        if (banque.getNomBanque().trim().length() < 3 || banque.getNomBanque().trim().length() > 100) {
            throw new IllegalArgumentException("Le nom de la banque doit contenir entre 3 et 100 caractères");
        }
    }

    /**
     * Génère le prochain code de banque
     * @return Nouveau code généré
     */
    public String generateNextCode() {
        // Récupérer le dernier code
        List<Banque> banques = banqueDAO.findAll();
        if (banques.isEmpty()) {
            return "BNK001";
        }

        // Trouver le plus grand numéro
        int maxNum = 0;
        for (Banque b : banques) {
            String code = b.getCodeBanque();
            if (code != null && code.startsWith("BNK")) {
                try {
                    int num = Integer.parseInt(code.substring(3));
                    maxNum = Math.max(maxNum, num);
                } catch (NumberFormatException e) {
                    // Ignorer les codes non conformes
                }
            }
        }

        return String.format("BNK%03d", maxNum + 1);
    }
}