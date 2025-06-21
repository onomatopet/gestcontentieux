package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.dao.ContraventionDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.model.enums.StatutAffaire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service métier pour la gestion des affaires
 * Gère la logique métier complexe des affaires contentieuses
 */
public class AffaireService {

    private static final Logger logger = LoggerFactory.getLogger(AffaireService.class);

    private final AffaireDAO affaireDAO;
    private final ContrevenantDAO contrevenantDAO;
    private final ContraventionDAO contraventionDAO;
    private final ValidationService validationService;
    private final AuthenticationService authService;

    public AffaireService() {
        this.affaireDAO = new AffaireDAO();
        this.contrevenantDAO = new ContrevenantDAO();
        this.contraventionDAO = new ContraventionDAO();
        this.validationService = new ValidationService();
        this.authService = AuthenticationService.getInstance();
    }

    /**
     * Recherche d'affaires avec pagination
     */
    public List<Affaire> searchAffaires(String searchTerm, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Long contrevenantId, Long bureauId,
                                        int page, int pageSize) {

        int offset = (page - 1) * pageSize;
        // Conversion Long en Integer pour correspondre à la signature du DAO
        Integer bureauIdInt = bureauId != null ? bureauId.intValue() : null;
        return affaireDAO.searchAffaires(searchTerm, statut, dateDebut, dateFin,
                bureauIdInt, offset, pageSize);
    }

    /**
     * Compte le nombre total d'affaires pour la recherche
     */
    public long countSearchAffaires(String searchTerm, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    Long contrevenantId, Long bureauId) {
        // Conversion Long en Integer pour correspondre à la signature du DAO
        Integer bureauIdInt = bureauId != null ? bureauId.intValue() : null;
        return affaireDAO.countSearchAffaires(searchTerm, statut, dateDebut, dateFin, bureauIdInt);
    }

    /**
     * Récupère toutes les affaires avec pagination
     */
    public List<Affaire> getAllAffaires(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return affaireDAO.findAll(offset, pageSize);
    }

    /**
     * Compte le nombre total d'affaires
     */
    public long countAllAffaires() {
        return affaireDAO.count();
    }

    /**
     * Crée une nouvelle affaire
     */
    public Affaire createAffaire(Affaire affaire) {
        try {
            // Validation
            if (!validationService.isValidAffaire(affaire)) {
                throw new IllegalArgumentException("Données d'affaire invalides");
            }

            // Génération du numéro d'affaire
            String numeroAffaire = generateNumeroAffaire();
            affaire.setNumeroAffaire(numeroAffaire);

            // Définition des métadonnées
            affaire.setCreatedBy(authService.getCurrentUsername());
            affaire.setStatut(StatutAffaire.OUVERTE);

            // Calcul du montant total si contraventions présentes
            if (affaire.getContraventions() != null && !affaire.getContraventions().isEmpty()) {
                BigDecimal montantTotal = affaire.getContraventions().stream()
                        .map(Contravention::getMontant)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                affaire.setMontantTotal(montantTotal);
            }

            // Sauvegarde
            Affaire savedAffaire = affaireDAO.save(affaire);
            logger.info("Affaire créée avec succès: {}", savedAffaire.getNumeroAffaire());

            return savedAffaire;

        } catch (Exception e) {
            logger.error("Erreur lors de la création de l'affaire", e);
            throw new RuntimeException("Impossible de créer l'affaire: " + e.getMessage());
        }
    }

    /**
     * Met à jour une affaire existante
     */
    public Affaire updateAffaire(Affaire affaire) {
        try {
            // Validation
            if (!validationService.isValidAffaire(affaire)) {
                throw new IllegalArgumentException("Données d'affaire invalides");
            }

            // Vérification existence
            Optional<Affaire> existing = affaireDAO.findById(affaire.getId());
            if (existing.isEmpty()) {
                throw new IllegalArgumentException("Affaire introuvable");
            }

            // Mise à jour des métadonnées
            affaire.setUpdatedBy(authService.getCurrentUsername());
            affaire.setUpdatedAt(LocalDate.now());

            // Recalcul du montant total si contraventions modifiées
            if (affaire.getContraventions() != null) {
                BigDecimal montantTotal = affaire.getContraventions().stream()
                        .map(Contravention::getMontant)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                affaire.setMontantTotal(montantTotal);
            }

            // Sauvegarde
            Affaire updatedAffaire = affaireDAO.update(affaire);
            logger.info("Affaire mise à jour: {}", updatedAffaire.getNumeroAffaire());

            return updatedAffaire;

        } catch (Exception e) {
            logger.error("Erreur lors de la mise à jour de l'affaire", e);
            throw new RuntimeException("Impossible de mettre à jour l'affaire: " + e.getMessage());
        }
    }

    /**
     * Change le statut d'une affaire
     */
    public void changerStatutAffaire(Long affaireId, StatutAffaire nouveauStatut) {
        try {
            Optional<Affaire> optAffaire = affaireDAO.findById(affaireId);
            if (optAffaire.isEmpty()) {
                throw new IllegalArgumentException("Affaire introuvable");
            }

            Affaire affaire = optAffaire.get();
            affaire.setStatut(nouveauStatut);
            affaire.setUpdatedBy(authService.getCurrentUsername());
            affaire.setUpdatedAt(LocalDate.now());

            affaireDAO.update(affaire);
            logger.info("Statut de l'affaire {} changé en {}", affaire.getNumeroAffaire(), nouveauStatut);

        } catch (Exception e) {
            logger.error("Erreur lors du changement de statut", e);
            throw new RuntimeException("Impossible de changer le statut: " + e.getMessage());
        }
    }

    /**
     * Supprime une affaire
     */
    public void deleteAffaire(Long affaireId) {
        try {
            Optional<Affaire> optAffaire = affaireDAO.findById(affaireId);
            if (optAffaire.isEmpty()) {
                throw new IllegalArgumentException("Affaire introuvable");
            }

            Affaire affaire = optAffaire.get();

            // Vérification si l'affaire a des encaissements
            if (affaire.hasEncaissements()) {
                throw new IllegalStateException("Impossible de supprimer une affaire avec des encaissements");
            }

            // Suppression logique
            affaire.setDeleted(true);
            affaire.setDeletedBy(authService.getCurrentUsername());
            affaire.setDeletedAt(LocalDate.now());

            affaireDAO.update(affaire);
            logger.info("Affaire {} supprimée (logiquement)", affaire.getNumeroAffaire());

        } catch (Exception e) {
            logger.error("Erreur lors de la suppression de l'affaire", e);
            throw new RuntimeException("Impossible de supprimer l'affaire: " + e.getMessage());
        }
    }

    /**
     * Récupère une affaire par son ID
     */
    public Optional<Affaire> getAffaireById(Long id) {
        return affaireDAO.findById(id);
    }

    /**
     * Récupère une affaire par son numéro
     */
    public Optional<Affaire> getAffaireByNumero(String numeroAffaire) {
        return affaireDAO.findByNumeroAffaire(numeroAffaire);
    }

    /**
     * Récupère les affaires d'un contrevenant
     */
    public List<Affaire> getAffairesByContrevenant(Long contrevenantId) {
        return affaireDAO.findByContrevenantId(contrevenantId);
    }

    /**
     * Calcule les statistiques des affaires
     */
    public AffaireStatistiques calculateStatistiques() {
        AffaireStatistiques stats = new AffaireStatistiques();

        // Total des affaires par statut
        stats.setNombreOuvertes(affaireDAO.countByStatut(StatutAffaire.OUVERTE));
        stats.setNombreEnCours(affaireDAO.countByStatut(StatutAffaire.EN_COURS));
        stats.setNombreCloses(affaireDAO.countByStatut(StatutAffaire.CLOSE));
        stats.setNombreAnnulees(affaireDAO.countByStatut(StatutAffaire.ANNULEE));

        // Montants
        List<Affaire> allAffaires = affaireDAO.findAll();
        BigDecimal montantTotal = allAffaires.stream()
                .map(Affaire::getMontantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal montantEncaisse = allAffaires.stream()
                .map(Affaire::getMontantEncaisseTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.setMontantTotal(montantTotal);
        stats.setMontantEncaisse(montantEncaisse);
        stats.setMontantRestant(montantTotal.subtract(montantEncaisse));

        return stats;
    }

    /**
     * Génère un numéro d'affaire unique
     */
    private String generateNumeroAffaire() {
        // Format: AFF-YYYY-XXXXX
        int year = LocalDate.now().getYear();
        long count = affaireDAO.countByYear(year) + 1;
        return String.format("AFF-%d-%05d", year, count);
    }

    /**
     * Classe interne pour les statistiques
     */
    public static class AffaireStatistiques {
        private long nombreOuvertes;
        private long nombreEnCours;
        private long nombreCloses;
        private long nombreAnnulees;
        private BigDecimal montantTotal;
        private BigDecimal montantEncaisse;
        private BigDecimal montantRestant;

        // Getters et setters
        public long getNombreOuvertes() { return nombreOuvertes; }
        public void setNombreOuvertes(long nombreOuvertes) { this.nombreOuvertes = nombreOuvertes; }

        public long getNombreEnCours() { return nombreEnCours; }
        public void setNombreEnCours(long nombreEnCours) { this.nombreEnCours = nombreEnCours; }

        public long getNombreCloses() { return nombreCloses; }
        public void setNombreCloses(long nombreCloses) { this.nombreCloses = nombreCloses; }

        public long getNombreAnnulees() { return nombreAnnulees; }
        public void setNombreAnnulees(long nombreAnnulees) { this.nombreAnnulees = nombreAnnulees; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getMontantRestant() { return montantRestant; }
        public void setMontantRestant(BigDecimal montantRestant) { this.montantRestant = montantRestant; }
    }
}