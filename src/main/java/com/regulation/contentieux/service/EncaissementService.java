package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service métier pour la gestion des encaissements
 * Suit la même logique que les autres services
 */
public class EncaissementService {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementService.class);

    private final EncaissementDAO encaissementDAO;
    private final ValidationService validationService;

    public EncaissementService() {
        this.encaissementDAO = new EncaissementDAO();
        this.validationService = new ValidationService();
    }

    /**
     * Recherche d'encaissements avec pagination
     */
    public List<Encaissement> searchEncaissements(String searchText, StatutEncaissement statut,
                                                  ModeReglement modeReglement, LocalDate dateDebut,
                                                  LocalDate dateFin, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return encaissementDAO.searchEncaissements(searchText, statut, modeReglement,
                dateDebut, dateFin, offset, pageSize);
    }

    /**
     * Compte le nombre total d'encaissements pour la recherche
     */
    public long countSearchEncaissements(String searchText, StatutEncaissement statut,
                                         ModeReglement modeReglement, LocalDate dateDebut,
                                         LocalDate dateFin) {
        return encaissementDAO.countSearchEncaissements(searchText, statut, modeReglement,
                dateDebut, dateFin);
    }

    /**
     * Trouve un encaissement par son ID
     */
    public Optional<Encaissement> findById(Long id) {
        return encaissementDAO.findById(id);
    }

    /**
     * Trouve un encaissement par sa référence
     */
    public Optional<Encaissement> findByReference(String reference) {
        return encaissementDAO.findByReference(reference);
    }

    /**
     * Sauvegarde un nouvel encaissement
     */
    public Encaissement saveEncaissement(Encaissement encaissement) {
        // Validation des données
        validateEncaissement(encaissement);

        // Validation des règles métier
        validateEncaissementBusinessRules(encaissement);

        // Génération de la référence si nécessaire
        if (encaissement.getReference() == null || encaissement.getReference().trim().isEmpty()) {
            String nextReference = encaissementDAO.generateNextReference(encaissement.getModeReglement());
            encaissement.setReference(nextReference);
        }

        // Vérification d'unicité de la référence
        if (encaissementDAO.existsByReference(encaissement.getReference())) {
            throw new IllegalArgumentException("Un encaissement avec cette référence existe déjà: " +
                    encaissement.getReference());
        }

        // Sauvegarde
        Encaissement saved = encaissementDAO.save(encaissement);
        logger.info("Nouvel encaissement créé: {} - {} FCFA",
                saved.getReference(), saved.getMontantEncaisse());

        return saved;
    }

    /**
     * Met à jour un encaissement existant
     */
    public Encaissement updateEncaissement(Encaissement encaissement) {
        if (encaissement.getId() == null) {
            throw new IllegalArgumentException("L'ID de l'encaissement est requis pour la mise à jour");
        }

        // Vérification que l'encaissement existe
        Optional<Encaissement> existing = encaissementDAO.findById(encaissement.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + encaissement.getId());
        }

        // Vérification que l'encaissement peut être modifié
        if (!existing.get().peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié (statut: " +
                    existing.get().getStatut().getLibelle() + ")");
        }

        // Validation des données
        validateEncaissement(encaissement);

        // Validation des règles métier
        validateEncaissementBusinessRules(encaissement);

        // Vérification d'unicité de la référence (sauf pour lui-même)
        Optional<Encaissement> byReference = encaissementDAO.findByReference(encaissement.getReference());
        if (byReference.isPresent() && !byReference.get().getId().equals(encaissement.getId())) {
            throw new IllegalArgumentException("Un autre encaissement utilise déjà cette référence: " +
                    encaissement.getReference());
        }

        // Mise à jour
        Encaissement updated = encaissementDAO.update(encaissement);
        logger.info("Encaissement mis à jour: {} - {} FCFA",
                updated.getReference(), updated.getMontantEncaisse());

        return updated;
    }

    /**
     * Supprime un encaissement
     */
    public void deleteEncaissement(Long id) {
        Optional<Encaissement> encaissement = encaissementDAO.findById(id);
        if (encaissement.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + id);
        }

        // Vérifier si l'encaissement peut être supprimé
        if (!encaissement.get().peutEtreAnnule()) {
            throw new IllegalStateException("Cet encaissement ne peut pas être supprimé (statut: " +
                    encaissement.get().getStatut().getLibelle() + ")");
        }

        // TODO: Vérifier s'il y a des répartitions liées avant suppression
        // Pour l'instant, suppression directe
        encaissementDAO.deleteById(id);
        logger.info("Encaissement supprimé: {} - {} FCFA",
                encaissement.get().getReference(), encaissement.get().getMontantEncaisse());
    }

    /**
     * Valide un encaissement
     */
    public boolean validerEncaissement(Long id) {
        Optional<Encaissement> encaissement = encaissementDAO.findById(id);
        if (encaissement.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + id);
        }

        Encaissement enc = encaissement.get();
        if (!enc.peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié");
        }

        String validatedBy = getCurrentUsername();
        enc.valider(validatedBy);

        encaissementDAO.update(enc);
        logger.info("Encaissement validé: {} par {}", enc.getReference(), validatedBy);

        return true;
    }

    /**
     * Rejette un encaissement
     */
    public boolean rejeterEncaissement(Long id) {
        Optional<Encaissement> encaissement = encaissementDAO.findById(id);
        if (encaissement.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + id);
        }

        Encaissement enc = encaissement.get();
        if (!enc.peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié");
        }

        String rejectedBy = getCurrentUsername();
        enc.rejeter(rejectedBy);

        encaissementDAO.update(enc);
        logger.info("Encaissement rejeté: {} par {}", enc.getReference(), rejectedBy);

        return true;
    }

    /**
     * Réactive un encaissement rejeté
     */
    public boolean reactiverEncaissement(Long id) {
        Optional<Encaissement> encaissement = encaissementDAO.findById(id);
        if (encaissement.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + id);
        }

        Encaissement enc = encaissement.get();
        if (enc.getStatut() != StatutEncaissement.REJETE) {
            throw new IllegalStateException("Seuls les encaissements rejetés peuvent être réactivés");
        }

        // Remettre en attente
        enc.setStatut(StatutEncaissement.EN_ATTENTE);
        enc.setUpdatedBy(getCurrentUsername());

        encaissementDAO.update(enc);
        logger.info("Encaissement réactivé: {} par {}", enc.getReference(), getCurrentUsername());

        return true;
    }

    /**
     * Liste tous les encaissements avec pagination
     */
    public List<Encaissement> getAllEncaissements(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return encaissementDAO.findAll(offset, pageSize);
    }

    /**
     * Compte le nombre total d'encaissements
     */
    public long getTotalCount() {
        return encaissementDAO.count();
    }

    /**
     * Trouve les encaissements par affaire
     */
    public List<Encaissement> findByAffaireId(Long affaireId) {
        return encaissementDAO.findByAffaireId(affaireId);
    }

    /**
     * Trouve les encaissements par mode de règlement
     */
    public List<Encaissement> findByModeReglement(ModeReglement modeReglement) {
        return encaissementDAO.findByModeReglement(modeReglement);
    }

    /**
     * Trouve les encaissements par statut
     */
    public List<Encaissement> findByStatut(StatutEncaissement statut) {
        return encaissementDAO.findByStatut(statut);
    }

    /**
     * Calcule le montant total encaissé pour une période
     */
    public Double getTotalMontantByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        return encaissementDAO.getTotalMontantByPeriod(dateDebut, dateFin);
    }

    /**
     * Calcule le montant total encaissé pour une affaire
     */
    public Double getTotalMontantByAffaire(Long affaireId) {
        return encaissementDAO.getTotalMontantByAffaire(affaireId);
    }

    /**
     * Obtient les encaissements récents pour le tableau de bord
     */
    public List<Encaissement> getRecentEncaissements(int limit) {
        return encaissementDAO.getRecentEncaissements(limit);
    }

    /**
     * Valide les données d'un encaissement
     */
    private void validateEncaissement(Encaissement encaissement) {
        if (encaissement == null) {
            throw new IllegalArgumentException("L'encaissement ne peut pas être null");
        }

        // Validation de l'affaire
        if (encaissement.getAffaireId() == null || encaissement.getAffaireId() <= 0) {
            throw new IllegalArgumentException("Une affaire valide doit être associée à l'encaissement");
        }

        // Validation du montant
        if (!validationService.isValidMontant(encaissement.getMontantEncaisse())) {
            throw new IllegalArgumentException("Le montant encaissé doit être positif et valide");
        }

        // Validation de la date d'encaissement
        if (encaissement.getDateEncaissement() == null) {
            throw new IllegalArgumentException("La date d'encaissement est requise");
        }

        if (!validationService.isValidDate(encaissement.getDateEncaissement())) {
            throw new IllegalArgumentException("La date d'encaissement est invalide");
        }

        // Validation du mode de règlement
        if (encaissement.getModeReglement() == null) {
            throw new IllegalArgumentException("Le mode de règlement est requis");
        }

        // Validation de la référence (si fournie)
        if (encaissement.getReference() != null && !encaissement.getReference().trim().isEmpty()) {
            if (!validationService.isValidReferenceBancaire(encaissement.getReference())) {
                throw new IllegalArgumentException("Format de référence invalide");
            }
        }

        // Validation conditionnelle selon le mode de règlement
        if (encaissement.necessiteBanque() && encaissement.getBanqueId() == null) {
            throw new IllegalArgumentException("Une banque doit être spécifiée pour le mode de règlement " +
                    encaissement.getModeReglement().getLibelle());
        }

        if (encaissement.necessiteReference() &&
                (encaissement.getReference() == null || encaissement.getReference().trim().isEmpty())) {
            throw new IllegalArgumentException("Une référence doit être spécifiée pour le mode de règlement " +
                    encaissement.getModeReglement().getLibelle());
        }
    }

    /**
     * Valide les règles métier pour un encaissement
     */
    private void validateEncaissementBusinessRules(Encaissement encaissement) {
        // TODO: Récupérer l'affaire pour vérifier le montant total
        // Pour l'instant, validation simplifiée

        // Règle : Le montant encaissé doit être positif
        validationService.validateEncaissementBusinessRules(
                encaissement.getMontantEncaisse(),
                null // montantAffaire sera récupéré plus tard
        );

        // Règle : La date d'encaissement ne peut pas être dans le futur
        if (encaissement.getDateEncaissement().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date d'encaissement ne peut pas être dans le futur");
        }

        // Règle : La date d'encaissement ne peut pas être trop ancienne (plus de 2 ans)
        if (encaissement.getDateEncaissement().isBefore(LocalDate.now().minusYears(2))) {
            throw new IllegalArgumentException("La date d'encaissement ne peut pas être antérieure à 2 ans");
        }
    }

    /**
     * Génère la prochaine référence pour un mode de règlement
     */
    public String generateNextReference(ModeReglement modeReglement) {
        return encaissementDAO.generateNextReference(modeReglement);
    }

    /**
     * Vérifie si une référence existe déjà
     */
    public boolean referenceExists(String reference) {
        return encaissementDAO.existsByReference(reference);
    }

    /**
     * Recherche rapide par référence ou affaire (pour autocomplete)
     */
    public List<Encaissement> searchQuick(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        return encaissementDAO.searchEncaissements(query.trim(), null, null, null, null, 0, limit);
    }

    /**
     * Obtient les encaissements pour un rapport
     */
    public List<Encaissement> getEncaissementsForReport(LocalDate dateDebut, LocalDate dateFin,
                                                        StatutEncaissement statut) {
        return encaissementDAO.searchEncaissements(null, statut, null, dateDebut, dateFin,
                0, Integer.MAX_VALUE);
    }

    /**
     * Statistiques des encaissements
     */
    public EncaissementStatistics getEncaissementStatistics() {
        long totalEncaissements = encaissementDAO.count();
        long encaissementsValides = encaissementDAO.countByStatut(StatutEncaissement.VALIDE);
        long encaissementsEnAttente = encaissementDAO.countByStatut(StatutEncaissement.EN_ATTENTE);
        long encaissementsRejetes = encaissementDAO.countByStatut(StatutEncaissement.REJETE);

        Double montantTotal = encaissementDAO.getTotalMontantByStatut(StatutEncaissement.VALIDE);
        Double montantEnAttente = encaissementDAO.getTotalMontantByStatut(StatutEncaissement.EN_ATTENTE);

        return new EncaissementStatistics(
                totalEncaissements, encaissementsValides, encaissementsEnAttente, encaissementsRejetes,
                montantTotal != null ? montantTotal : 0.0,
                montantEnAttente != null ? montantEnAttente : 0.0
        );
    }

    /**
     * Récupère le nom d'utilisateur actuel
     */
    private String getCurrentUsername() {
        // TODO: Récupérer depuis AuthenticationService
        return "system"; // Temporaire
    }

    /**
     * Classe pour encapsuler les statistiques des encaissements
     */
    public static class EncaissementStatistics {
        private final long totalEncaissements;
        private final long encaissementsValides;
        private final long encaissementsEnAttente;
        private final long encaissementsRejetes;
        private final double montantTotalValide;
        private final double montantTotalEnAttente;

        public EncaissementStatistics(long totalEncaissements, long encaissementsValides,
                                      long encaissementsEnAttente, long encaissementsRejetes,
                                      double montantTotalValide, double montantTotalEnAttente) {
            this.totalEncaissements = totalEncaissements;
            this.encaissementsValides = encaissementsValides;
            this.encaissementsEnAttente = encaissementsEnAttente;
            this.encaissementsRejetes = encaissementsRejetes;
            this.montantTotalValide = montantTotalValide;
            this.montantTotalEnAttente = montantTotalEnAttente;
        }

        public long getTotalEncaissements() { return totalEncaissements; }
        public long getEncaissementsValides() { return encaissementsValides; }
        public long getEncaissementsEnAttente() { return encaissementsEnAttente; }
        public long getEncaissementsRejetes() { return encaissementsRejetes; }
        public double getMontantTotalValide() { return montantTotalValide; }
        public double getMontantTotalEnAttente() { return montantTotalEnAttente; }

        public double getValidationRate() {
            return totalEncaissements > 0 ? (encaissementsValides * 100.0 / totalEncaissements) : 0.0;
        }

        public double getMontantTotal() {
            return montantTotalValide + montantTotalEnAttente;
        }
    }
}