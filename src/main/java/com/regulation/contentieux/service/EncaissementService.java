package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service métier pour la gestion des encaissements - COMPLÉTÉ
 * Suit la même logique que AgentService et ContrevenantService
 */
public class EncaissementService {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementService.class);

    private final EncaissementDAO encaissementDAO;
    private final AffaireDAO affaireDAO;
    private final ValidationService validationService;

    public EncaissementService() {
        this.encaissementDAO = new EncaissementDAO();
        this.affaireDAO = new AffaireDAO();
        this.validationService = new ValidationService();
    }

    /**
     * Recherche d'encaissements avec pagination - SUIT LE PATTERN ÉTABLI
     */
    public List<Encaissement> searchEncaissements(String reference, StatutEncaissement statut,
                                                  ModeReglement modeReglement, LocalDate dateDebut,
                                                  LocalDate dateFin, Long affaireId,
                                                  int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return encaissementDAO.searchEncaissements(reference, statut, modeReglement,
                dateDebut, dateFin, affaireId, offset, pageSize);
    }

    /**
     * Compte le nombre total d'encaissements pour la recherche
     */
    public long countSearchEncaissements(String reference, StatutEncaissement statut,
                                         ModeReglement modeReglement, LocalDate dateDebut,
                                         LocalDate dateFin, Long affaireId) {
        return encaissementDAO.countSearchEncaissements(reference, statut, modeReglement,
                dateDebut, dateFin, affaireId);
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
     * Sauvegarde un nouvel encaissement - AVEC GÉNÉRATION DU NUMÉRO
     */
    public Encaissement saveEncaissement(Encaissement encaissement) {
        // Validation des données
        validateEncaissement(encaissement);

        // Validation de l'affaire liée
        validateAffaireLiee(encaissement.getAffaireId());

        // Génération de la référence si nécessaire (selon le format YYMMRNNNN)
        if (encaissement.getReference() == null || encaissement.getReference().trim().isEmpty()) {
            String nextReference = encaissementDAO.generateNextNumeroEncaissement();
            encaissement.setReference(nextReference);
        }

        // Vérification d'unicité de la référence
        if (encaissementDAO.existsByReference(encaissement.getReference())) {
            throw new IllegalArgumentException("Un encaissement avec cette référence existe déjà: " +
                    encaissement.getReference());
        }

        // Validation du mode de règlement
        validateModeReglement(encaissement);

        // Sauvegarde
        Encaissement saved = encaissementDAO.save(encaissement);
        logger.info("Nouvel encaissement créé: {} - {} pour l'affaire {}",
                saved.getReference(),
                saved.getModeReglementLibelle(),
                saved.getAffaireId());

        return saved;
    }

    /**
     * Met à jour un encaissement existant
     */
    public Encaissement updateEncaissement(Encaissement encaissement) {
        if (encaissement.getId() == null) {
            throw new IllegalArgumentException("L'ID de l'encaissement est requis pour la mise à jour");
        }

        // Validation des données
        validateEncaissement(encaissement);

        // Vérification que l'encaissement existe
        Optional<Encaissement> existing = encaissementDAO.findById(encaissement.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + encaissement.getId());
        }

        // Vérification que l'encaissement peut être modifié
        if (!existing.get().peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié (statut: " +
                    existing.get().getStatutLibelle() + ")");
        }

        // Vérification d'unicité de la référence (sauf pour lui-même)
        Optional<Encaissement> byReference = encaissementDAO.findByReference(encaissement.getReference());
        if (byReference.isPresent() && !byReference.get().getId().equals(encaissement.getId())) {
            throw new IllegalArgumentException("Un autre encaissement utilise déjà cette référence: " +
                    encaissement.getReference());
        }

        // Validation du mode de règlement
        validateModeReglement(encaissement);

        // Mise à jour
        Encaissement updated = encaissementDAO.update(encaissement);
        logger.info("Encaissement mis à jour: {} - {}",
                updated.getReference(), updated.getModeReglementLibelle());

        return updated;
    }

    /**
     * Valide un encaissement
     */
    public boolean validerEncaissement(Long encaissementId, String validatedBy) {
        Optional<Encaissement> encaissementOpt = encaissementDAO.findById(encaissementId);
        if (encaissementOpt.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + encaissementId);
        }

        Encaissement encaissement = encaissementOpt.get();

        if (!encaissement.peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié");
        }

        boolean result = encaissementDAO.updateStatut(encaissementId, StatutEncaissement.VALIDE, validatedBy);

        if (result) {
            logger.info("Encaissement validé: {} par {}", encaissement.getReference(), validatedBy);
        }

        return result;
    }

    /**
     * Rejette un encaissement
     */
    public boolean rejeterEncaissement(Long encaissementId, String rejectedBy) {
        Optional<Encaissement> encaissementOpt = encaissementDAO.findById(encaissementId);
        if (encaissementOpt.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + encaissementId);
        }

        Encaissement encaissement = encaissementOpt.get();

        if (!encaissement.peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut plus être modifié");
        }

        boolean result = encaissementDAO.updateStatut(encaissementId, StatutEncaissement.REJETE, rejectedBy);

        if (result) {
            logger.info("Encaissement rejeté: {} par {}", encaissement.getReference(), rejectedBy);
        }

        return result;
    }

    /**
     * Annule un encaissement
     */
    public boolean annulerEncaissement(Long encaissementId, String cancelledBy) {
        Optional<Encaissement> encaissementOpt = encaissementDAO.findById(encaissementId);
        if (encaissementOpt.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + encaissementId);
        }

        Encaissement encaissement = encaissementOpt.get();

        if (!encaissement.peutEtreAnnule()) {
            throw new IllegalStateException("Cet encaissement ne peut pas être annulé");
        }

        boolean result = encaissementDAO.updateStatut(encaissementId, StatutEncaissement.ANNULE, cancelledBy);

        if (result) {
            logger.info("Encaissement annulé: {} par {}", encaissement.getReference(), cancelledBy);
        }

        return result;
    }

    /**
     * Supprime un encaissement
     */
    public void deleteEncaissement(Long id) {
        Optional<Encaissement> encaissement = encaissementDAO.findById(id);
        if (encaissement.isEmpty()) {
            throw new IllegalArgumentException("Encaissement non trouvé avec l'ID: " + id);
        }

        if (!encaissement.get().peutEtreModifie()) {
            throw new IllegalStateException("Cet encaissement ne peut pas être supprimé (statut: " +
                    encaissement.get().getStatutLibelle() + ")");
        }

        encaissementDAO.deleteById(id);
        logger.info("Encaissement supprimé: {} - {}",
                encaissement.get().getReference(), encaissement.get().getModeReglementLibelle());
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
     * Trouve les encaissements par statut
     */
    public List<Encaissement> findByStatut(StatutEncaissement statut) {
        return encaissementDAO.findByStatut(statut);
    }

    /**
     * Calcule le montant total encaissé pour une affaire
     */
    public BigDecimal getTotalEncaisseByAffaire(Long affaireId) {
        // Le DAO retourne déjà BigDecimal, pas Double
        BigDecimal total = encaissementDAO.getTotalEncaisseByAffaire(affaireId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Génère le prochain numéro d'encaissement - FORMAT YYMMRNNNN
     */
    public String generateNextNumeroEncaissement() {
        return encaissementDAO.generateNextNumeroEncaissement();
    }

    /**
     * Vérifie si une référence existe déjà
     */
    public boolean referenceExists(String reference) {
        return encaissementDAO.existsByReference(reference);
    }

    /**
     * Validation des données d'un encaissement - SUIT LE PATTERN ÉTABLI
     */
    private void validateEncaissement(Encaissement encaissement) {
        if (encaissement == null) {
            throw new IllegalArgumentException("L'encaissement ne peut pas être null");
        }

        // Validation de l'affaire liée
        if (encaissement.getAffaireId() == null) {
            throw new IllegalArgumentException("L'affaire liée est obligatoire");
        }

        // Validation du montant
        if (encaissement.getMontantEncaisse() == null ||
                encaissement.getMontantEncaisse().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant encaissé doit être positif");
        }

        // Validation de la date d'encaissement
        if (encaissement.getDateEncaissement() == null) {
            throw new IllegalArgumentException("La date d'encaissement est obligatoire");
        }

        // Validation du mode de règlement
        if (encaissement.getModeReglement() == null) {
            throw new IllegalArgumentException("Le mode de règlement est obligatoire");
        }

        // Validation de la référence si fournie
        if (encaissement.getReference() != null && !encaissement.getReference().trim().isEmpty()) {
            if (!validationService.isValidEncaissementReference(encaissement.getReference())) {
                throw new IllegalArgumentException("Format de référence d'encaissement invalide");
            }
        }
    }

    /**
     * Validation de l'affaire liée
     */
    private void validateAffaireLiee(Long affaireId) {
        Optional<Affaire> affaire = affaireDAO.findById(affaireId);
        if (affaire.isEmpty()) {
            throw new IllegalArgumentException("L'affaire avec l'ID " + affaireId + " n'existe pas");
        }

        if (!affaire.get().peutRecevoirEncaissement()) {
            throw new IllegalStateException("Cette affaire ne peut pas recevoir d'encaissement (statut: " +
                    affaire.get().getStatut().getLibelle() + ")");
        }
    }

    /**
     * Validation du mode de règlement et de ses contraintes
     */
    private void validateModeReglement(Encaissement encaissement) {
        ModeReglement mode = encaissement.getModeReglement();

        // Vérification banque obligatoire selon le mode
        if (mode.isNecessiteBanque() && encaissement.getBanqueId() == null) {
            throw new IllegalArgumentException("Une banque est obligatoire pour le mode de règlement: " +
                    mode.getLibelle());
        }

        // Vérification référence obligatoire selon le mode
        if (mode.isNecessiteReference() &&
                (encaissement.getReference() == null || encaissement.getReference().trim().isEmpty())) {
            throw new IllegalArgumentException("Une référence est obligatoire pour le mode de règlement: " +
                    mode.getLibelle());
        }

        // Vérification banque non nécessaire
        if (!mode.isNecessiteBanque() && encaissement.getBanqueId() != null) {
            logger.warn("Banque renseignée pour un mode qui ne la nécessite pas: {}", mode.getLibelle());
        }
    }

    /**
     * Recherche rapide pour autocomplete
     */
    public List<Encaissement> searchQuick(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        return encaissementDAO.searchEncaissements(query.trim(), null, null, null, null, null, 0, limit);
    }

    /**
     * Obtient les encaissements pour un rapport
     */
    public List<Encaissement> getEncaissementsForReport(StatutEncaissement statut, LocalDate dateDebut,
                                                        LocalDate dateFin) {
        return encaissementDAO.searchEncaissements(null, statut, null, dateDebut, dateFin, null,
                0, Integer.MAX_VALUE);
    }

    /**
     * Statistiques des encaissements
     */
    public EncaissementStatistics getEncaissementStatistics() {
        long totalEncaissements = encaissementDAO.count();
        long validEncaissements = encaissementDAO.findByStatut(StatutEncaissement.VALIDE).size();
        long pendingEncaissements = encaissementDAO.findByStatut(StatutEncaissement.EN_ATTENTE).size();

        // LIGNE 414 - CORRECTION: Le DAO retourne BigDecimal, pas Double
        BigDecimal totalMontant = encaissementDAO.getTotalEncaissementsByPeriod(null, null, StatutEncaissement.VALIDE);

        return new EncaissementStatistics(totalEncaissements, validEncaissements, pendingEncaissements, totalMontant);
    }

    /**
     * Classe pour encapsuler les statistiques des encaissements
     */
    public static class EncaissementStatistics {
        private final long totalEncaissements;
        private final long validEncaissements;
        private final long pendingEncaissements;
        // LIGNE 271 - CORRECTION: Changer le type de Double à BigDecimal
        private final BigDecimal totalMontant;

        public EncaissementStatistics(long totalEncaissements, long validEncaissements,
                                      long pendingEncaissements, BigDecimal totalMontant) {
            this.totalEncaissements = totalEncaissements;
            this.validEncaissements = validEncaissements;
            this.pendingEncaissements = pendingEncaissements;
            // LIGNE 271 - La conversion automatique BigDecimal -> BigDecimal.ZERO fonctionne maintenant
            this.totalMontant = totalMontant != null ? totalMontant : BigDecimal.ZERO;
        }

        public long getTotalEncaissements() { return totalEncaissements; }
        public long getValidEncaissements() { return validEncaissements; }
        public long getPendingEncaissements() { return pendingEncaissements; }

        // CORRECTION: Changer le type de retour de Double à BigDecimal
        public BigDecimal getTotalMontant() { return totalMontant; }

        public double getValidPercentage() {
            return totalEncaissements > 0 ? (validEncaissements * 100.0 / totalEncaissements) : 0.0;
        }
    }
}