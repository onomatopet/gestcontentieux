package com.regulation.contentieux.service;

import java.util.Objects;
import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.exception.BusinessException;
import com.regulation.contentieux.util.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service pour la gestion des affaires contentieuses
 * ENRICHI avec la règle métier : "Pas d'affaire sans paiement"
 * Une affaire ne peut être créée que si elle a au moins un encaissement
 */
public class AffaireService {

    private static final Logger logger = LoggerFactory.getLogger(AffaireService.class);

    private final AffaireDAO affaireDAO;
    private final EncaissementDAO encaissementDAO;
    private final ContrevenantDAO contrevenantDAO;
    private final AgentDAO agentDAO;
    private final RepartitionService repartitionService;
    private final MandatService mandatService;
    private final ValidationService validationService;
    private final AuthenticationService authService;

    // ENRICHISSEMENT : Gestionnaire de transactions pour garantir l'atomicité
    private final TransactionManager transactionManager;

    public AffaireService() {
        this.affaireDAO = new AffaireDAO();
        this.encaissementDAO = new EncaissementDAO();
        this.contrevenantDAO = new ContrevenantDAO();
        this.agentDAO = new AgentDAO();
        this.repartitionService = new RepartitionService();
        this.mandatService = MandatService.getInstance();
        this.validationService = new ValidationService();
        this.authService = AuthenticationService.getInstance();
        this.transactionManager = TransactionManager.getInstance();
    }

    /**
     * NOUVELLE MÉTHODE : Crée une affaire avec son premier encaissement
     * Respecte la règle métier : "Pas d'affaire sans paiement"
     *
     * @param affaireData Données de l'affaire à créer
     * @param premierEncaissement Données du premier encaissement (OBLIGATOIRE)
     * @param acteurs Liste des acteurs (chefs et saisissants)
     * @return L'affaire créée avec son encaissement
     */
    public Affaire createAffaireAvecEncaissement(
            Affaire affaireData,
            Encaissement premierEncaissement,
            List<AffaireActeur> acteurs) {

        logger.info("🆕 === CRÉATION AFFAIRE AVEC ENCAISSEMENT OBLIGATOIRE ===");

        // VALIDATION CRITIQUE : Vérifier qu'il y a bien un encaissement
        if (premierEncaissement == null) {
            throw new BusinessException(
                    "Une affaire ne peut être créée sans un premier encaissement. " +
                            "Veuillez d'abord enregistrer le paiement du contrevenant."
            );
        }

        if (premierEncaissement.getMontantEncaisse() == null ||
                premierEncaissement.getMontantEncaisse().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Le montant du premier encaissement doit être supérieur à zéro"
            );
        }

        // Validation de l'affaire
        validateAffaire(affaireData);

        // Validation spécifique : montant encaissé <= montant amende
        if (premierEncaissement.getMontantEncaisse().compareTo(affaireData.getMontantAmendeTotal()) > 0) {
            throw new BusinessException(
                    "Le montant encaissé ne peut pas dépasser le montant de l'amende"
            );
        }

        // Validation des acteurs
        validateActeurs(acteurs);

        // ENRICHISSEMENT : Vérifier le mandat actif
        String mandatActif = mandatService.getMandatActif();
        if (mandatActif == null) {
            throw new BusinessException(
                    "Aucun mandat actif. Veuillez d'abord activer un mandat."
            );
        }

        logger.info("📋 Mandat actif : {}", mandatActif);

        // TRANSACTION ATOMIQUE : Créer l'affaire ET l'encaissement ensemble
        return transactionManager.executeInTransaction(() -> {
            try {
                // 1. Générer le numéro d'affaire
                String numeroAffaire = affaireDAO.generateNextCode();
                affaireData.setNumeroAffaire(numeroAffaire);
                affaireData.setDateCreation(LocalDate.now());
                affaireData.setStatut(StatutAffaire.EN_COURS);
                affaireData.setCreatedBy(authService.getCurrentUser().getLogin());
                affaireData.setCreatedAt(LocalDateTime.now());

                logger.info("📄 Création affaire : {}", numeroAffaire);

                // 2. Calculer le montant total si plusieurs contraventions
                if (affaireData.getContraventions() != null && !affaireData.getContraventions().isEmpty()) {
                    BigDecimal montantTotal = affaireData.getContraventions().stream()
                            .map(Contravention::getMontant)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    affaireData.setMontantAmendeTotal(montantTotal);
                }

                // 3. Sauvegarder l'affaire
                Affaire affaireSaved = affaireDAO.save(affaireData);

                // 4. Créer le premier encaissement
                String numeroEncaissement = encaissementDAO.generateNextNumeroEncaissement();
                premierEncaissement.setReference(numeroEncaissement);
                premierEncaissement.setAffaire(affaireSaved);
                premierEncaissement.setDateEncaissement(LocalDate.now());
                premierEncaissement.setStatut(StatutEncaissement.VALIDE);
                premierEncaissement.setCreatedBy(authService.getCurrentUser().getLogin());
                premierEncaissement.setCreatedAt(LocalDateTime.now());

                logger.info("💰 Création encaissement : {} - Montant : {} FCFA",
                        numeroEncaissement, premierEncaissement.getMontantEncaisse());

                Encaissement encaissementSaved = encaissementDAO.save(premierEncaissement);

                // 5. Enregistrer les acteurs de l'affaire
                for (AffaireActeur acteur : acteurs) {
                    acteur.setAffaireId(affaireSaved.getId());
                    acteur.setAssignedBy(authService.getCurrentUser().getLogin());
                    acteur.setAssignedAt(LocalDateTime.now());

                    // Utiliser une méthode alternative pour sauvegarder l'acteur
                    saveAffaireActeur(acteur);
                }

                // 6. Calculer et enregistrer les répartitions
                RepartitionResultat repartition = repartitionService.calculerRepartition(
                        encaissementSaved, affaireSaved
                );
                repartitionService.enregistrerRepartition(repartition);

                // 7. Mettre à jour le statut de l'affaire si totalement payée
                BigDecimal totalEncaisse = encaissementSaved.getMontantEncaisse();
                if (totalEncaisse.compareTo(affaireSaved.getMontantAmendeTotal()) >= 0) {
                    affaireSaved.setStatut(StatutAffaire.CLOSE);
                    affaireSaved.setUpdatedAt(LocalDateTime.now());
                    affaireSaved.setUpdatedBy(authService.getCurrentUser().getLogin());
                    affaireDAO.update(affaireSaved);
                    logger.info("✅ Affaire soldée dès le premier paiement");
                }

                // 8. ENRICHISSEMENT : Gestion des affaires à cheval
                LocalDate dateEncaissement = premierEncaissement.getDateEncaissement();
                if (!estDansMandatActif(dateEncaissement)) {
                    logger.warn("⚠️ AFFAIRE À CHEVAL : Négociation et paiement sur mois différents");
                    logger.warn("⚠️ L'affaire est comptabilisée dans le mandat du paiement : {}", mandatActif);
                }

                logger.info("✅ Affaire {} créée avec succès avec encaissement {}",
                        numeroAffaire, numeroEncaissement);

                // Recharger l'affaire complète
                return affaireDAO.findById(affaireSaved.getId())
                        .orElse(affaireSaved);

            } catch (Exception e) {
                logger.error("❌ Erreur lors de la création de l'affaire avec encaissement", e);
                throw new BusinessException("Erreur lors de la création de l'affaire : " + e.getMessage());
            }
        });
    }

    /**
     * Sauvegarde un acteur d'affaire (méthode alternative)
     */
    private void saveAffaireActeur(AffaireActeur acteur) {
        String sql = """
            INSERT INTO affaire_acteurs (affaire_id, agent_id, role_sur_affaire, assigned_at, assigned_by)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (var conn = com.regulation.contentieux.config.DatabaseConfig.getSQLiteConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, acteur.getAffaireId());
            stmt.setLong(2, acteur.getAgentId());
            stmt.setString(3, acteur.getRoleSurAffaire());
            stmt.setTimestamp(4, java.sql.Timestamp.valueOf(acteur.getAssignedAt()));
            stmt.setString(5, acteur.getAssignedBy());

            stmt.executeUpdate();
            logger.debug("Acteur ajouté à l'affaire: {} - {}", acteur.getAgentId(), acteur.getRoleSurAffaire());

        } catch (Exception e) {
            logger.error("Erreur lors de l'ajout de l'acteur", e);
            throw new RuntimeException("Impossible d'ajouter l'acteur à l'affaire", e);
        }
    }

    /**
     * MÉTHODE DÉPRÉCIÉE : Utiliser createAffaireAvecEncaissement à la place
     * Conservée temporairement pour compatibilité
     */
    @Deprecated
    public Affaire createAffaire(Affaire affaire) {
        logger.warn("⚠️ ATTENTION : Utilisation de la méthode dépréciée createAffaire()");
        logger.warn("⚠️ Cette méthode ne respecte pas la règle 'Pas d'affaire sans paiement'");
        logger.warn("⚠️ Utiliser createAffaireAvecEncaissement() à la place");

        throw new BusinessException(
                "La création d'affaire sans encaissement n'est plus autorisée. " +
                        "Utilisez createAffaireAvecEncaissement() pour créer une affaire avec son premier paiement."
        );
    }

    /**
     * Met à jour une affaire existante
     * ENRICHISSEMENT : Vérification que l'affaire a au moins un encaissement
     */
    public Affaire updateAffaire(Affaire affaire) {
        logger.info("📝 Mise à jour de l'affaire : {}", affaire.getNumeroAffaire());

        // Vérifier que l'affaire existe
        Optional<Affaire> existing = affaireDAO.findById(affaire.getId());
        if (existing.isEmpty()) {
            throw new BusinessException("Affaire introuvable : " + affaire.getId());
        }

        // ENRICHISSEMENT : Vérifier qu'elle a au moins un encaissement
        List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
        if (encaissements.isEmpty()) {
            logger.error("❌ Tentative de mise à jour d'une affaire sans encaissement");
            throw new BusinessException(
                    "Cette affaire n'a aucun encaissement. Elle ne devrait pas exister selon les règles métier."
            );
        }

        // Validation
        validateAffaire(affaire);

        // Mise à jour
        affaire.setUpdatedAt(LocalDateTime.now());
        affaire.setUpdatedBy(authService.getCurrentUser().getLogin());

        return affaireDAO.update(affaire);
    }

    /**
     * Ajoute un encaissement à une affaire existante (paiement partiel)
     */
    public Encaissement addEncaissementToAffaire(Long affaireId, Encaissement encaissement) {
        logger.info("💰 Ajout d'un encaissement à l'affaire ID: {}", affaireId);

        // Vérifier que l'affaire existe
        Affaire affaire = affaireDAO.findById(affaireId)
                .orElseThrow(() -> new BusinessException("Affaire introuvable : " + affaireId));

        // Vérifier que l'affaire n'est pas déjà soldée
        if (affaire.getStatut() == StatutAffaire.CLOSE) {
            throw new BusinessException("L'affaire est déjà soldée, aucun encaissement supplémentaire n'est possible");
        }

        // Calculer le solde restant
        BigDecimal totalEncaisse = getTotalEncaisse(affaireId);
        BigDecimal soldeRestant = affaire.getMontantAmendeTotal().subtract(totalEncaisse);

        // Vérifier que le nouveau montant ne dépasse pas le solde
        if (encaissement.getMontantEncaisse().compareTo(soldeRestant) > 0) {
            throw new BusinessException(String.format(
                    "Le montant encaissé (%s FCFA) dépasse le solde restant (%s FCFA)",
                    encaissement.getMontantEncaisse(), soldeRestant
            ));
        }

        // Transaction pour ajouter l'encaissement
        return transactionManager.executeInTransaction(() -> {
            // Générer le numéro d'encaissement
            String numeroEncaissement = encaissementDAO.generateNextNumeroEncaissement();
            encaissement.setReference(numeroEncaissement);
            encaissement.setAffaire(affaire);
            encaissement.setDateEncaissement(LocalDate.now());
            encaissement.setStatut(StatutEncaissement.VALIDE);
            encaissement.setCreatedBy(authService.getCurrentUser().getLogin());
            encaissement.setCreatedAt(LocalDateTime.now());

            // Sauvegarder l'encaissement
            Encaissement saved = encaissementDAO.save(encaissement);

            // Calculer les répartitions
            RepartitionResultat repartition = repartitionService.calculerRepartition(saved, affaire);
            repartitionService.enregistrerRepartition(repartition);

            // Vérifier si l'affaire est maintenant soldée
            BigDecimal nouveauTotal = totalEncaisse.add(encaissement.getMontantEncaisse());
            if (nouveauTotal.compareTo(affaire.getMontantAmendeTotal()) >= 0) {
                affaire.setStatut(StatutAffaire.CLOSE);
                affaire.setUpdatedAt(LocalDateTime.now());
                affaire.setUpdatedBy(authService.getCurrentUser().getLogin());
                affaireDAO.update(affaire);
                logger.info("✅ Affaire {} soldée après ce paiement", affaire.getNumeroAffaire());
            }

            return saved;
        });
    }

    /**
     * Recherche les affaires selon différents critères
     */
    public List<Affaire> searchAffaires(String searchTerm, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Integer bureauId, int page, int size) {
        int offset = page * size;
        return affaireDAO.searchAffaires(searchTerm, statut, dateDebut, dateFin, bureauId, offset, size);
    }

    /**
     * Compte les affaires selon les critères
     */
    public long countSearchAffaires(String searchTerm, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin, Integer bureauId) {
        return affaireDAO.countSearchAffaires(searchTerm, statut, dateDebut, dateFin, bureauId);
    }

    /**
     * Trouve une affaire par son ID avec tous ses détails
     */
    public Optional<Affaire> findByIdWithDetails(Long id) {
        // Pour l'instant, utiliser findById simple
        return affaireDAO.findById(id);
    }

    /**
     * Trouve une affaire par son numéro
     */
    public Optional<Affaire> findByNumero(String numeroAffaire) {
        return affaireDAO.findByNumeroAffaire(numeroAffaire);
    }

    /**
     * Obtient le montant total encaissé pour une affaire
     */
    public BigDecimal getTotalEncaisse(Long affaireId) {
        List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaireId);
        return encaissements.stream()
                .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                .map(Encaissement::getMontantEncaisse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Obtient les statistiques du tableau de bord
     */
    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();

        // Affaires du mois
        LocalDate debutMois = LocalDate.now().withDayOfMonth(1);
        LocalDate finMois = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        stats.setAffairesOuvertes(affaireDAO.countByStatut(StatutAffaire.EN_COURS));
        stats.setAffairesSoldees(affaireDAO.countByStatut(StatutAffaire.CLOSE));

        // Pour l'instant, simuler les statistiques manquantes
        stats.setNouvellesAffairesMois(countAffairesByPeriode(debutMois, finMois));
        stats.setMontantTotalAmendes(calculateTotalAmendes());
        stats.setMontantTotalEncaisse(calculateTotalEncaisse());
        stats.setMontantEncaisseMois(calculateEncaisseMois(debutMois, finMois));

        return stats;
    }

    /**
     * Méthodes temporaires pour les statistiques
     */
    private long countAffairesByPeriode(LocalDate debut, LocalDate fin) {
        // Simulation temporaire
        return affaireDAO.findAll().stream()
                .filter(a -> a.getDateCreation() != null)
                .filter(a -> !a.getDateCreation().isBefore(debut))
                .filter(a -> !a.getDateCreation().isAfter(fin))
                .count();
    }

    private BigDecimal calculateTotalAmendes() {
        // Simulation temporaire
        return affaireDAO.findAll().stream()
                .map(Affaire::getMontantAmendeTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalEncaisse() {
        // Simulation temporaire
        return encaissementDAO.findAll().stream()
                .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                .map(Encaissement::getMontantEncaisse)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateEncaisseMois(LocalDate debut, LocalDate fin) {
        // Simulation temporaire
        return encaissementDAO.findAll().stream()
                .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                .filter(e -> e.getDateEncaissement() != null)
                .filter(e -> !e.getDateEncaissement().isBefore(debut))
                .filter(e -> !e.getDateEncaissement().isAfter(fin))
                .map(Encaissement::getMontantEncaisse)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========== MÉTHODES PRIVÉES DE VALIDATION ==========

    private void validateAffaire(Affaire affaire) {
        if (affaire == null) {
            throw new IllegalArgumentException("L'affaire ne peut pas être null");
        }

        // Validation du contrevenant
        if (affaire.getContrevenant() == null && affaire.getContrevenantId() == null) {
            throw new IllegalArgumentException("Le contrevenant est obligatoire");
        }

        // Validation du montant
        if (affaire.getMontantAmendeTotal() == null ||
                affaire.getMontantAmendeTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant de l'amende doit être positif");
        }

        // Validation de la date
        if (affaire.getDateCreation() != null && affaire.getDateCreation().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de création ne peut pas être dans le futur");
        }
    }

    private void validateActeurs(List<AffaireActeur> acteurs) {
        if (acteurs == null || acteurs.isEmpty()) {
            throw new IllegalArgumentException("Au moins un acteur (chef ou saisissant) est requis");
        }

        // Vérifier qu'il y a au moins un saisissant
        boolean hasSaisissant = acteurs.stream()
                .anyMatch(a -> "SAISISSANT".equals(a.getRoleSurAffaire()));

        if (!hasSaisissant) {
            throw new IllegalArgumentException("Au moins un agent saisissant est obligatoire");
        }
    }

    /**
     * ENRICHISSEMENT : Vérifie si une date est dans le mandat actif
     */
    private boolean estDansMandatActif(LocalDate date) {
        String mandatActif = mandatService.getMandatActif();
        if (mandatActif == null || date == null) {
            return false;
        }

        // Extraire YYMM du mandat
        String yymmMandat = mandatActif.substring(0, 4);
        String yymmDate = date.format(DateTimeFormatter.ofPattern("yyMM"));

        return yymmMandat.equals(yymmDate);
    }

    /**
     * Classe interne pour les statistiques du tableau de bord
     */
    public static class DashboardStats {
        private long affairesOuvertes;
        private long affairesSoldees;
        private long nouvellesAffairesMois;
        private BigDecimal montantTotalAmendes;
        private BigDecimal montantTotalEncaisse;
        private BigDecimal montantEncaisseMois;

        // Getters et setters
        public long getAffairesOuvertes() { return affairesOuvertes; }
        public void setAffairesOuvertes(long affairesOuvertes) { this.affairesOuvertes = affairesOuvertes; }

        public long getAffairesSoldees() { return affairesSoldees; }
        public void setAffairesSoldees(long affairesSoldees) { this.affairesSoldees = affairesSoldees; }

        public long getNouvellesAffairesMois() { return nouvellesAffairesMois; }
        public void setNouvellesAffairesMois(long nouvellesAffairesMois) { this.nouvellesAffairesMois = nouvellesAffairesMois; }

        public BigDecimal getMontantTotalAmendes() { return montantTotalAmendes; }
        public void setMontantTotalAmendes(BigDecimal montantTotalAmendes) { this.montantTotalAmendes = montantTotalAmendes; }

        public BigDecimal getMontantTotalEncaisse() { return montantTotalEncaisse; }
        public void setMontantTotalEncaisse(BigDecimal montantTotalEncaisse) { this.montantTotalEncaisse = montantTotalEncaisse; }

        public BigDecimal getMontantEncaisseMois() { return montantEncaisseMois; }
        public void setMontantEncaisseMois(BigDecimal montantEncaisseMois) { this.montantEncaisseMois = montantEncaisseMois; }
    }
}