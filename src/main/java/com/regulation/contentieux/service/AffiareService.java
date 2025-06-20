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
        return affaireDAO.searchAffaires(searchTerm, statut, dateDebut, dateFin,
                contrevenantId, bureauId, offset, pageSize);
    }

    /**
     * Compte le nombre total d'affaires pour la recherche
     */
    public long countSearchAffaires(String searchTerm, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    Long contrevenantId, Long bureauId) {

        return affaireDAO.countSearchAffaires(searchTerm, statut, dateDebut, dateFin,
                contrevenantId, bureauId);
    }

    /**
     * Crée une nouvelle affaire
     */
    public Affaire createAffaire(Affaire affaire) {
        // Validation
        validateAffaire(affaire);

        // Génération du numéro si nécessaire
        if (affaire.getNumeroAffaire() == null || affaire.getNumeroAffaire().trim().isEmpty()) {
            String nextNumero = affaireDAO.generateNextNumeroAffaire();
            affaire.setNumeroAffaire(nextNumero);
        }

        // Vérification d'unicité du numéro
        if (affaireDAO.existsByNumeroAffaire(affaire.getNumeroAffaire())) {
            throw new IllegalArgumentException("Une affaire avec ce numéro existe déjà");
        }

        // Vérification du contrevenant
        Optional<Contrevenant> contrevenant = contrevenantDAO.findById(affaire.getContrevenantId());
        if (contrevenant.isEmpty()) {
            throw new IllegalArgumentException("Contrevenant non trouvé");
        }

        // Vérification de la contravention
        Optional<Contravention> contravention = contraventionDAO.findById(affaire.getContraventionId());
        if (contravention.isEmpty()) {
            throw new IllegalArgumentException("Contravention non trouvée");
        }

        // Définition du créateur
        affaire.setCreatedBy(authService.getCurrentUsername());

        // Sauvegarde
        Affaire saved = affaireDAO.save(affaire);
        logger.info("Nouvelle affaire créée: {} - Montant: {}",
                saved.getNumeroAffaire(), saved.getMontantAmendeTotal());

        return saved;
    }

    /**
     * Met à jour une affaire existante
     */
    public Affaire updateAffaire(Affaire affaire) {
        if (affaire.getId() == null) {
            throw new IllegalArgumentException("L'ID de l'affaire est requis pour la mise à jour");
        }

        // Validation
        validateAffaire(affaire);

        // Vérification que l'affaire existe
        Optional<Affaire> existing = affaireDAO.findById(affaire.getId());
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Affaire non trouvée");
        }

        Affaire existingAffaire = existing.get();

        // Vérification des droits de modification selon le statut
        if (!existingAffaire.getStatut().isModifiable()) {
            throw new IllegalStateException("Cette affaire ne peut plus être modifiée (statut: " +
                    existingAffaire.getStatut().getLibelle() + ")");
        }

        // Vérification d'unicité du numéro (si modifié)
        if (!existingAffaire.getNumeroAffaire().equals(affaire.getNumeroAffaire())) {
            if (affaireDAO.existsByNumeroAffaire(affaire.getNumeroAffaire())) {
                throw new IllegalArgumentException("Une autre affaire utilise déjà ce numéro");
            }
        }

        // Mise à jour du modificateur
        affaire.setUpdatedBy(authService.getCurrentUsername());

        // Sauvegarde
        Affaire updated = affaireDAO.update(affaire);
        logger.info("Affaire mise à jour: {} - Nouveau statut: {}",
                updated.getNumeroAffaire(), updated.getStatut());

        return updated;
    }

    /**
     * Change le statut d'une affaire
     */
    public Affaire changeStatut(Long affaireId, StatutAffaire nouveauStatut, String motif) {
        Optional<Affaire> affaireOpt = affaireDAO.findById(affaireId);
        if (affaireOpt.isEmpty()) {
            throw new IllegalArgumentException("Affaire non trouvée");
        }

        Affaire affaire = affaireOpt.get();

        // Vérifier la transition de statut
        if (!isTransitionAutorisee(affaire.getStatut(), nouveauStatut)) {
            throw new IllegalStateException("Transition de statut non autorisée: " +
                    affaire.getStatut() + " vers " + nouveauStatut);
        }

        // Appliquer le nouveau statut
        affaire.setStatut(nouveauStatut);
        affaire.setUpdatedBy(authService.getCurrentUsername());

        // TODO: Enregistrer l'historique du changement de statut avec le motif

        return affaireDAO.update(affaire);
    }

    /**
     * Supprime une affaire (soft delete)
     */
    public boolean deleteAffaire(Long affaireId) {
        Optional<Affaire> affaireOpt = affaireDAO.findById(affaireId);
        if (affaireOpt.isEmpty()) {
            throw new IllegalArgumentException("Affaire non trouvée");
        }

        Affaire affaire = affaireOpt.get();

        // Vérifier si l'affaire peut être supprimée
        if (affaire.hasEncaissements()) {
            throw new IllegalStateException("Impossible de supprimer une affaire avec des encaissements");
        }

        if (!affaire.getStatut().isModifiable()) {
            throw new IllegalStateException("Impossible de supprimer une affaire " +
                    affaire.getStatut().getLibelle());
        }

        // Suppression logique (changement de statut)
        affaire.setStatut(StatutAffaire.ANNULEE);
        affaire.setUpdatedBy(authService.getCurrentUsername());
        affaireDAO.update(affaire);

        logger.info("Affaire annulée: {}", affaire.getNumeroAffaire());
        return true;
    }

    /**
     * Récupère les affaires d'un contrevenant
     */
    public List<Affaire> getAffairesByContrevenant(Long contrevenantId) {
        return affaireDAO.findByContrevenantId(contrevenantId);
    }

    /**
     * Récupère les affaires ouvertes
     */
    public List<Affaire> getAffairesOuvertes() {
        return affaireDAO.findAll().stream()
                .filter(a -> a.getStatut() == StatutAffaire.OUVERTE ||
                        a.getStatut() == StatutAffaire.EN_COURS)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les statistiques globales
     */
    public AffaireStatistics getStatistics() {
        AffaireStatistics stats = new AffaireStatistics();

        List<Affaire> allAffaires = affaireDAO.findAll();

        stats.setTotalAffaires(allAffaires.size());
        stats.setAffairesOuvertes((int) allAffaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.OUVERTE).count());
        stats.setAffairesEnCours((int) allAffaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.EN_COURS).count());
        stats.setAffairesClosturees((int) allAffaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.CLOTUREE).count());

        stats.setMontantTotal(allAffaires.stream()
                .mapToDouble(Affaire::getMontantAmendeTotal)
                .sum());
        stats.setMontantEncaisse(allAffaires.stream()
                .mapToDouble(Affaire::getMontantEncaisseTotal)
                .sum());

        return stats;
    }

    /**
     * Valide une affaire
     */
    private void validateAffaire(Affaire affaire) {
        if (affaire == null) {
            throw new IllegalArgumentException("L'affaire ne peut pas être nulle");
        }

        // Validation du montant
        if (!validationService.isValidPositiveAmount(affaire.getMontantAmendeTotal())) {
            throw new IllegalArgumentException("Le montant de l'amende doit être positif");
        }

        // Validation de la date
        if (!validationService.isValidDate(affaire.getDateCreation())) {
            throw new IllegalArgumentException("Date de création invalide");
        }

        // Validation des références
        if (affaire.getContrevenantId() == null || affaire.getContrevenantId() <= 0) {
            throw new IllegalArgumentException("Contrevenant requis");
        }

        if (affaire.getContraventionId() == null || affaire.getContraventionId() <= 0) {
            throw new IllegalArgumentException("Contravention requise");
        }
    }

    /**
     * Vérifie si une transition de statut est autorisée
     */
    private boolean isTransitionAutorisee(StatutAffaire from, StatutAffaire to) {
        StatutAffaire[] transitionsAutorisees = from.getTransitionsAutorisees();

        for (StatutAffaire statut : transitionsAutorisees) {
            if (statut == to) {
                return true;
            }
        }

        return false;
    }

    /**
     * Classe pour les statistiques
     */
    public static class AffaireStatistics {
        private int totalAffaires;
        private int affairesOuvertes;
        private int affairesEnCours;
        private int affairesClosturees;
        private double montantTotal;
        private double montantEncaisse;

        // Getters et setters
        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }

        public int getAffairesOuvertes() { return affairesOuvertes; }
        public void setAffairesOuvertes(int affairesOuvertes) { this.affairesOuvertes = affairesOuvertes; }

        public int getAffairesEnCours() { return affairesEnCours; }
        public void setAffairesEnCours(int affairesEnCours) { this.affairesEnCours = affairesEnCours; }

        public int getAffairesClosturees() { return affairesClosturees; }
        public void setAffairesClosturees(int affairesClosturees) { this.affairesClosturees = affairesClosturees; }

        public double getMontantTotal() { return montantTotal; }
        public void setMontantTotal(double montantTotal) { this.montantTotal = montantTotal; }

        public double getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(double montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public double getMontantRestant() {
            return montantTotal - montantEncaisse;
        }

        public double getTauxRecouvrement() {
            if (montantTotal == 0) return 0;
            return (montantEncaisse / montantTotal) * 100;
        }
    }
}