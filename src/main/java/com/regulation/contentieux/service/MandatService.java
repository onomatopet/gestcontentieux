package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.exception.BusinessException;
import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.model.enums.StatutMandat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Date;

import java.math.BigDecimal;  // AJOUT : Import manquant pour BigDecimal
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;  // AJOUT : Import manquant pour LocalDateTime
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service pour la gestion des mandats selon le cahier des charges
 * Format: YYMMM0001 (ex: 2506M0001)
 * Un seul mandat actif à la fois
 * ENRICHISSEMENT COMPLET du service
 */
public class MandatService {

    private static final Logger logger = LoggerFactory.getLogger(MandatService.class);

    // ENRICHISSEMENT : Instance unique pour gérer le mandat actif
    private static MandatService instance;
    private Mandat mandatActif;

    // Format standard du cahier des charges (AVEC le 'M' obligatoire)
    private static final String FORMAT_PATTERN = "yyMM'M'";  // yyMM + M littéral
    private static final int NUMERO_LENGTH = 4; // 0001 à 9999

    private MandatService() {
        // ENRICHISSEMENT : Charger le mandat actif au démarrage
        chargerMandatActif();
    }

    public static synchronized MandatService getInstance() {
        if (instance == null) {
            instance = new MandatService();
        }
        return instance;
    }

    /**
     * Crée un nouveau mandat avec des dates personnalisées
     * Réservé aux SUPER_ADMIN pour créer des mandats antérieurs
     *
     * @param description Description du mandat
     * @param dateDebut Date de début du mandat
     * @param dateFin Date de fin du mandat
     * @return Le mandat créé
     * @throws BusinessException Si validation échoue
     */
    public Mandat creerMandatAvecDates(String description, LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🆕 === CRÉATION MANDAT AVEC DATES PERSONNALISÉES ===");
        logger.info("📅 Période demandée : {} au {}", dateDebut, dateFin);

        // Validation des dates
        if (dateDebut == null || dateFin == null) {
            throw new BusinessException("Les dates de début et fin sont obligatoires");
        }

        if (dateFin.isBefore(dateDebut)) {
            throw new BusinessException("La date de fin doit être après la date de début");
        }

        // Vérification des droits pour les dates antérieures
        LocalDate aujourdhui = LocalDate.now();
        if (dateDebut.isBefore(aujourdhui.withDayOfMonth(1))) {
            // Date antérieure au mois en cours
            var utilisateur = AuthenticationService.getInstance().getCurrentUser();
            if (utilisateur == null || utilisateur.getRole() != RoleUtilisateur.SUPER_ADMIN) {
                throw new BusinessException(
                        "Seul un SUPER_ADMIN peut créer un mandat pour une période antérieure.\n" +
                                "Utilisateur actuel : " + (utilisateur != null ? utilisateur.getRole().getLibelle() : "Non connecté")
                );
            }
            logger.warn("⚠️ Création d'un mandat antérieur par SUPER_ADMIN : {}", utilisateur.getLogin());
        }

        // Vérifier qu'aucun mandat actif n'existe (sauf si SUPER_ADMIN et mandat antérieur)
        boolean estMandatAnterieur = dateFin.isBefore(aujourdhui.withDayOfMonth(1));
        if (!estMandatAnterieur) {
            // Pour les mandats actuels ou futurs, appliquer la règle standard
            Mandat mandatActifActuel = getMandatActif();
            if (mandatActifActuel != null) {
                throw new BusinessException(
                        "ERREUR : Un mandat est déjà actif (" + mandatActifActuel.getNumeroMandat() +
                                "). Vous devez le clôturer avant de créer un nouveau mandat."
                );
            }
        }

        // Vérifier les chevauchements
        if (existeMandatPourPeriode(dateDebut, dateFin)) {
            throw new BusinessException(
                    "Un mandat existe déjà pour cette période ou une partie de cette période.\n" +
                            "Veuillez choisir une autre période."
            );
        }

        // Générer le numéro basé sur la date de début
        String numeroMandat = genererNumeroMandatPourDate(dateDebut);

        // Créer le mandat
        Mandat nouveauMandat = new Mandat();
        nouveauMandat.setNumeroMandat(numeroMandat);
        nouveauMandat.setDescription(description != null ? description :
                "Mandat du " + dateDebut.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        nouveauMandat.setDateDebut(dateDebut);
        nouveauMandat.setDateFin(dateFin);

        // Statut selon la période
        if (estMandatAnterieur) {
            nouveauMandat.setStatut(StatutMandat.CLOTURE);
            nouveauMandat.setActif(false);
            nouveauMandat.setDateCloture(LocalDateTime.now());
            logger.info("📌 Mandat antérieur créé avec statut CLÔTURÉ");
        } else {
            nouveauMandat.setStatut(StatutMandat.BROUILLON);
            nouveauMandat.setActif(false);
        }

        nouveauMandat.setCreatedAt(LocalDateTime.now());
        nouveauMandat.setCreatedBy(AuthenticationService.getInstance().getCurrentUser().getLogin());

        // Sauvegarder en base
        sauvegarderMandat(nouveauMandat);

        logger.info("✅ Mandat créé avec dates personnalisées : {}", numeroMandat);
        return nouveauMandat;
    }

    /**
     * Vérifie si un mandat existe déjà pour une période donnée
     */
    private boolean existeMandatPourPeriode(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT COUNT(*) FROM mandats 
        WHERE (
            (date_debut <= ? AND date_fin >= ?) OR  -- Chevauche le début
            (date_debut <= ? AND date_fin >= ?) OR  -- Chevauche la fin
            (date_debut >= ? AND date_fin <= ?)     -- Contenu dans la période
        )
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));
            stmt.setDate(4, Date.valueOf(dateFin));
            stmt.setDate(5, Date.valueOf(dateDebut));
            stmt.setDate(6, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                logger.debug("Nombre de mandats trouvés pour la période : {}", count);
                return count > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des chevauchements", e);
            return true; // Par sécurité, on considère qu'il y a chevauchement
        }

        return false;
    }

    /**
     * Génère un numéro de mandat pour une date spécifique
     */
    private String genererNumeroMandatPourDate(LocalDate date) {
        String prefixe = date.format(DateTimeFormatter.ofPattern(FORMAT_PATTERN)); // yyMM'M'

        String sql = """
        SELECT numero_mandat FROM mandats 
        WHERE numero_mandat LIKE ? 
        ORDER BY numero_mandat DESC 
        LIMIT 1
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefixe + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String dernierNumero = rs.getString("numero_mandat");
                return genererProchainNumero(dernierNumero, prefixe);
            } else {
                // Premier mandat du mois
                return prefixe + "0001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro", e);
            throw new BusinessException("Impossible de générer le numéro de mandat", e);
        }
    }

    /**
     * Crée un nouveau mandat pour le mois en cours
     * ENRICHISSEMENT : Validation stricte et gestion d'état
     */
    public Mandat creerNouveauMandat(String description) {
        logger.info("🆕 === CRÉATION NOUVEAU MANDAT ===");

        // VÉRIFICATION STRICTE : Un seul mandat actif autorisé
        Mandat mandatActifActuel = getMandatActif();
        if (mandatActifActuel != null) {
            throw new BusinessException(
                    "ERREUR : Un mandat est déjà actif (" + mandatActifActuel.getNumeroMandat() +
                            "). Vous devez le clôturer avant de créer un nouveau mandat.\n\n" +
                            "Actions possibles :\n" +
                            "1. Clôturer le mandat actif\n" +
                            "2. Ou désactiver le mandat actif\n" +
                            "3. Puis créer le nouveau mandat"
            );
        }

        // Vérifier également en base pour être sûr
        if (existeMandatActifEnBase()) {
            throw new BusinessException(
                    "CONTRAINTE VIOLÉE : Un mandat actif existe déjà en base de données. " +
                            "L'intégrité du système exige qu'un seul mandat soit actif à la fois."
            );
        }

        // Générer le numéro (format corrigé YYMMM0001)
        String numeroMandat = genererNouveauMandat();

        // Créer le mandat
        Mandat nouveauMandat = new Mandat();
        nouveauMandat.setNumeroMandat(numeroMandat);
        nouveauMandat.setDescription(description != null ? description :
                "Mandat du mois " + YearMonth.now().format(DateTimeFormatter.ofPattern("MM/yyyy")));
        nouveauMandat.setDateDebut(LocalDate.now().withDayOfMonth(1));
        nouveauMandat.setDateFin(LocalDate.now().withDayOfMonth(
                LocalDate.now().lengthOfMonth()));
        nouveauMandat.setStatut(StatutMandat.BROUILLON);
        nouveauMandat.setCreatedAt(LocalDateTime.now());
        nouveauMandat.setCreatedBy(AuthenticationService.getInstance().getCurrentUser().getLogin());

        // Sauvegarder en base
        sauvegarderMandat(nouveauMandat);

        logger.info("✅ Mandat créé avec format corrigé : {}", numeroMandat);
        return nouveauMandat;
    }

    /**
     * NOUVELLE MÉTHODE : Vérification stricte en base
     */
    private boolean existeMandatActifEnBase() {
        String sql = "SELECT COUNT(*) FROM mandats WHERE actif = 1 OR statut = 'ACTIF'";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                logger.debug("Nombre de mandats actifs en base : {}", count);
                return count > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des mandats actifs", e);
            // En cas d'erreur, on suppose qu'il y en a un pour la sécurité
            return true;
        }

        return false;
    }

    /**
     * Active un mandat (un seul actif à la fois)
     * ENRICHISSEMENT : Désactivation automatique des autres mandats
     */
    /**
     * Active un mandat (un seul actif à la fois)
     * ENRICHISSEMENT : Désactivation automatique des autres mandats
     * SUPER_ADMIN peut activer des mandats antérieurs
     */
    public void activerMandat(String numeroMandat) {
        logger.info("🔄 Activation du mandat : {}", numeroMandat);

        // Récupérer le mandat
        Mandat mandat = findByNumero(numeroMandat)
                .orElseThrow(() -> new BusinessException("Mandat introuvable : " + numeroMandat));

        // Vérifier qu'il n'est pas déjà actif
        if (mandat.getStatut() == StatutMandat.ACTIF) {
            logger.info("ℹ️ Le mandat {} est déjà actif", numeroMandat);
            this.mandatActif = mandat;
            return;
        }

        // Pour les mandats clôturés, vérifier les droits SUPER_ADMIN
        if (mandat.getStatut() == StatutMandat.CLOTURE) {
            var utilisateur = AuthenticationService.getInstance().getCurrentUser();
            if (utilisateur == null || utilisateur.getRole() != RoleUtilisateur.SUPER_ADMIN) {
                throw new BusinessException(
                        "Seul un SUPER_ADMIN peut réactiver un mandat clôturé.\n" +
                                "Ce mandat a été clôturé et ne peut être réactivé que par un administrateur système."
                );
            }

            // Vérifier si c'est un mandat antérieur
            LocalDate aujourdhui = LocalDate.now();
            if (mandat.getDateFin().isBefore(aujourdhui)) {
                logger.warn("⚠️ ACTIVATION D'UN MANDAT ANTÉRIEUR par SUPER_ADMIN : {} pour la période {} au {}",
                        utilisateur.getLogin(), mandat.getDateDebut(), mandat.getDateFin());
            }
        }

        String sql = "UPDATE mandats SET actif = 0, statut = 'EN_ATTENTE', updated_at = CURRENT_TIMESTAMP";
        String sqlActivate = "UPDATE mandats SET actif = 1, statut = 'ACTIF', updated_at = CURRENT_TIMESTAMP WHERE numero_mandat = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try {
                // Désactiver tous les autres mandats
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                }

                // Activer le mandat sélectionné
                try (PreparedStatement stmt = conn.prepareStatement(sqlActivate)) {
                    stmt.setString(1, numeroMandat);
                    stmt.executeUpdate();
                }

                conn.commit();

                // Mettre à jour le mandat actif en mémoire
                mandat.setActif(true);
                mandat.setStatut(StatutMandat.ACTIF);
                this.mandatActif = mandat;

                logger.info("✅ Mandat {} activé avec succès", numeroMandat);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de l'activation du mandat", e);
            throw new BusinessException("Impossible d'activer le mandat : " + e.getMessage());
        }
    }

    /**
     * Modifie les dates d'un mandat existant
     * Réservé aux SUPER_ADMIN pour les mandats non actifs
     *
     * @param numeroMandat Numéro du mandat à modifier
     * @param nouvelleDescription Nouvelle description (null pour conserver l'ancienne)
     * @param nouvelleDateDebut Nouvelle date de début
     * @param nouvelleDateFin Nouvelle date de fin
     * @return Le mandat modifié
     */
    public Mandat modifierDatesMandat(String numeroMandat, String nouvelleDescription,
                                      LocalDate nouvelleDateDebut, LocalDate nouvelleDateFin) {
        logger.info("📝 === MODIFICATION DES DATES DU MANDAT {} ===", numeroMandat);

        // Vérifier les droits SUPER_ADMIN
        var utilisateur = AuthenticationService.getInstance().getCurrentUser();
        if (utilisateur == null || utilisateur.getRole() != RoleUtilisateur.SUPER_ADMIN) {
            throw new BusinessException(
                    "Seul un SUPER_ADMIN peut modifier les dates d'un mandat.\n" +
                            "Cette opération est réservée aux administrateurs système."
            );
        }

        // Récupérer le mandat
        Mandat mandat = findByNumero(numeroMandat)
                .orElseThrow(() -> new BusinessException("Mandat introuvable : " + numeroMandat));

        // Vérifier que le mandat n'est pas le mandat actif actuel
        if (mandat.isActif() && mandat.getStatut() == StatutMandat.ACTIF) {
            // Pour un mandat actif, on peut modifier mais avec précautions
            logger.warn("⚠️ Modification d'un mandat ACTIF par SUPER_ADMIN : {}", utilisateur.getLogin());
        }

        // Validation des nouvelles dates
        if (nouvelleDateDebut == null || nouvelleDateFin == null) {
            throw new BusinessException("Les dates de début et fin sont obligatoires");
        }

        if (nouvelleDateFin.isBefore(nouvelleDateDebut)) {
            throw new BusinessException("La date de fin doit être après la date de début");
        }

        // Vérifier les chevauchements (exclure le mandat actuel)
        if (existeMandatPourPeriodeExcluant(nouvelleDateDebut, nouvelleDateFin, mandat.getId())) {
            throw new BusinessException(
                    "Un autre mandat existe déjà pour cette période.\n" +
                            "Veuillez choisir une autre période."
            );
        }

        // Générer un nouveau numéro si le mois change
        String nouveauNumero = numeroMandat;
        String ancienPrefixe = numeroMandat.substring(0, 5); // YYMMM
        String nouveauPrefixe = nouvelleDateDebut.format(DateTimeFormatter.ofPattern("yyMM")) + "M";

        if (!ancienPrefixe.equals(nouveauPrefixe)) {
            // Le mois a changé, générer un nouveau numéro
            nouveauNumero = genererNumeroMandatPourDate(nouvelleDateDebut);
            logger.info("📌 Changement de période : nouveau numéro généré {}", nouveauNumero);
        }

        // Mettre à jour en base
        String sql = """
        UPDATE mandats 
        SET numero_mandat = ?, description = ?, date_debut = ?, date_fin = ?,
            updated_at = CURRENT_TIMESTAMP, updated_by = ?
        WHERE id = ?
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nouveauNumero);
            stmt.setString(2, nouvelleDescription != null ? nouvelleDescription : mandat.getDescription());
            stmt.setDate(3, Date.valueOf(nouvelleDateDebut));
            stmt.setDate(4, Date.valueOf(nouvelleDateFin));
            stmt.setString(5, utilisateur.getLogin());
            stmt.setLong(6, mandat.getId());

            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new BusinessException("Impossible de modifier le mandat");
            }

            // Mettre à jour l'objet
            mandat.setNumeroMandat(nouveauNumero);
            if (nouvelleDescription != null) {
                mandat.setDescription(nouvelleDescription);
            }
            mandat.setDateDebut(nouvelleDateDebut);
            mandat.setDateFin(nouvelleDateFin);
            mandat.setUpdatedAt(LocalDateTime.now());
            mandat.setUpdatedBy(utilisateur.getLogin());

            logger.info("✅ Mandat modifié avec succès : {} (période {} au {})",
                    nouveauNumero, nouvelleDateDebut, nouvelleDateFin);

            return mandat;

        } catch (SQLException e) {
            logger.error("Erreur lors de la modification du mandat", e);
            throw new BusinessException("Impossible de modifier le mandat : " + e.getMessage());
        }
    }

    /**
     * Vérifie si un mandat existe pour une période en excluant un mandat spécifique
     */
    private boolean existeMandatPourPeriodeExcluant(LocalDate dateDebut, LocalDate dateFin, Long mandatIdExclu) {
        String sql = """
        SELECT COUNT(*) FROM mandats 
        WHERE id != ? AND (
            (date_debut <= ? AND date_fin >= ?) OR  -- Chevauche le début
            (date_debut <= ? AND date_fin >= ?) OR  -- Chevauche la fin
            (date_debut >= ? AND date_fin <= ?)     -- Contenu dans la période
        )
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, mandatIdExclu);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateDebut));
            stmt.setDate(4, Date.valueOf(dateFin));
            stmt.setDate(5, Date.valueOf(dateFin));
            stmt.setDate(6, Date.valueOf(dateDebut));
            stmt.setDate(7, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification des chevauchements", e);
            return true;
        }

        return false;
    }

    /**
     * Vérifie si une date est dans le mandat actif
     * Utilisé par AffaireService pour les validations
     */
    public boolean estDansMandatActif(LocalDate date) {
        if (mandatActif == null || date == null) {
            return false;
        }

        return mandatActif.contientDate(date);
    }

    /**
     * Clôture le mandat actif
     * ENRICHISSEMENT : Vérifications avant clôture
     */
    public void cloturerMandatActif() {
        if (mandatActif == null) {
            throw new BusinessException("Aucun mandat actif à clôturer");
        }

        logger.info("🔒 Clôture du mandat : {}", mandatActif.getNumeroMandat());

        // Vérifier qu'il n'y a pas d'affaires en cours
        int affairesEnCours = compterAffairesEnCours(mandatActif.getNumeroMandat());
        if (affairesEnCours > 0) {
            throw new BusinessException(
                    String.format("Impossible de clôturer le mandat : %d affaire(s) encore en cours",
                            affairesEnCours)
            );
        }

        String sql = """
            UPDATE mandats 
            SET statut = 'CLOTURE', 
                actif = 0, 
                date_cloture = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP 
            WHERE numero_mandat = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, mandatActif.getNumeroMandat());
            stmt.executeUpdate();

            mandatActif.setStatut(StatutMandat.CLOTURE);
            mandatActif.setActif(false);
            mandatActif = null;

            logger.info("✅ Mandat clôturé avec succès");

        } catch (SQLException e) {
            logger.error("Erreur lors de la clôture du mandat", e);
            throw new RuntimeException("Impossible de clôturer le mandat", e);
        }
    }

    /**
     * Liste tous les mandats avec possibilité de filtrage
     */
    public List<Mandat> listerMandats(boolean seulementActifs, StatutMandat statut) {
        List<Mandat> mandats = new ArrayList<>();

        StringBuilder sql = new StringBuilder("SELECT * FROM mandats WHERE 1=1");

        if (seulementActifs) {
            sql.append(" AND actif = 1");
        }

        if (statut != null) {
            sql.append(" AND statut = ?");
        }

        sql.append(" ORDER BY numero_mandat DESC");

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (statut != null) {
                stmt.setString(paramIndex++, statut.name());
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                mandats.add(mapResultSetToMandat(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du listage des mandats", e);
        }

        return mandats;
    }

    /**
     * Récupère les statistiques d'un mandat
     */
    public MandatStatistiques getStatistiques(String numeroMandat) {
        MandatStatistiques stats = new MandatStatistiques();
        stats.setNumeroMandat(numeroMandat);

        String sql = """
            SELECT 
                COUNT(DISTINCT a.id) as nombre_affaires,
                COUNT(DISTINCT CASE WHEN a.statut = 'SOLDEE' THEN a.id END) as affaires_soldees,
                COUNT(DISTINCT CASE WHEN a.statut = 'EN_COURS' THEN a.id END) as affaires_en_cours,
                COUNT(DISTINCT e.id) as nombre_encaissements,
                COALESCE(SUM(e.montant_encaisse), 0) as montant_total_encaisse,
                COUNT(DISTINCT aa.agent_id) as nombre_agents
            FROM affaires a
            LEFT JOIN encaissements e ON e.affaire_id = a.id
            LEFT JOIN affaire_acteurs aa ON aa.affaire_id = a.id
            WHERE EXISTS (
                SELECT 1 FROM encaissements e2 
                WHERE e2.affaire_id = a.id 
                AND e2.numero_mandat = ?
            )
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroMandat);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                stats.setNombreAffaires(rs.getInt("nombre_affaires"));
                stats.setAffairesSoldees(rs.getInt("affaires_soldees"));
                stats.setAffairesEnCours(rs.getInt("affaires_en_cours"));
                stats.setNombreEncaissements(rs.getInt("nombre_encaissements"));
                stats.setMontantTotalEncaisse(rs.getBigDecimal("montant_total_encaisse"));
                stats.setNombreAgents(rs.getInt("nombre_agents"));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul des statistiques", e);
        }

        return stats;
    }

    /**
     * Génère un nouveau numéro de mandat selon le format YYMM0001
     * ENRICHISSEMENT : Gestion robuste avec vérification d'unicité
     */
    private String genererNouveauMandat() {
        LocalDate now = LocalDate.now();
        // Format : yyMM + M + 0001
        String yearMonth = now.format(DateTimeFormatter.ofPattern("yyMM"));
        String prefixe = yearMonth + "M";  // Ajouter le M obligatoire

        logger.debug("🔍 Génération mandat pour période : {}", prefixe);

        // Rechercher le dernier mandat du mois
        String sql = """
        SELECT numero_mandat FROM mandats
        WHERE numero_mandat LIKE ?
        ORDER BY numero_mandat DESC
        LIMIT 1
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefixe + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastMandat = rs.getString("numero_mandat");
                return genererProchainNumero(lastMandat, prefixe);
            } else {
                // Premier mandat du mois
                String numero = prefixe + "0001";
                logger.info("🆕 Premier mandat du mois : {}", numero);
                return numero;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro de mandat", e);
            throw new RuntimeException("Impossible de générer le numéro de mandat", e);
        }
    }

    /**
     * Génère le prochain numéro à partir du dernier
     */
    private String genererProchainNumero(String dernierNumero, String prefixe) {
        if (dernierNumero == null || dernierNumero.length() < prefixe.length() + NUMERO_LENGTH) {
            return prefixe + "0001";
        }

        try {
            // Extraire le numéro séquentiel (4 derniers caractères)
            String sequenceStr = dernierNumero.substring(prefixe.length());
            int sequence = Integer.parseInt(sequenceStr);

            // Incrémenter
            sequence++;

            // Vérifier la limite
            if (sequence > 9999) {
                throw new BusinessException("Limite de mandats atteinte pour ce mois (9999)");
            }

            // Formater avec padding
            String nouveauNumero = prefixe + String.format("%04d", sequence);
            logger.info("📈 Prochain mandat généré : {}", nouveauNumero);

            return nouveauNumero;

        } catch (NumberFormatException e) {
            logger.warn("⚠️ Format de mandat invalide : {}, génération nouveau : {}", dernierNumero, prefixe + "0001");
            return prefixe + "0001";
        }
    }

    /**
     * Charge le mandat actif depuis la base
     */
    private void chargerMandatActif() {
        String sql = """
            SELECT * FROM mandats 
            WHERE actif = 1 AND statut = 'ACTIF' 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                this.mandatActif = mapResultSetToMandat(rs);
                logger.info("✅ Mandat actif chargé : {}", this.mandatActif.getNumeroMandat());
            } else {
                logger.warn("⚠️ Aucun mandat actif trouvé");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du chargement du mandat actif", e);
        }
    }

    /**
     * Sauvegarde un mandat en base
     */
    private void sauvegarderMandat(Mandat mandat) {
        String sql = """
            INSERT INTO mandats (numero_mandat, description, date_debut, date_fin, 
                               statut, actif, created_at, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, mandat.getNumeroMandat());
            stmt.setString(2, mandat.getDescription());
            stmt.setDate(3, Date.valueOf(mandat.getDateDebut()));
            stmt.setDate(4, Date.valueOf(mandat.getDateFin()));
            stmt.setString(5, mandat.getStatut().name());
            stmt.setBoolean(6, false); // Jamais actif à la création
            stmt.setTimestamp(7, Timestamp.valueOf(mandat.getCreatedAt()));
            stmt.setString(8, mandat.getCreatedBy());

            stmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde du mandat", e);
            throw new RuntimeException("Impossible de sauvegarder le mandat", e);
        }
    }

    /**
     * Compte les affaires en cours pour un mandat
     */
    private int compterAffairesEnCours(String numeroMandat) {
        String sql = """
            SELECT COUNT(DISTINCT a.id) 
            FROM affaires a
            WHERE a.statut = 'EN_COURS'
            AND EXISTS (
                SELECT 1 FROM encaissements e 
                WHERE e.affaire_id = a.id 
                AND e.numero_mandat = ?
            )
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroMandat);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des affaires en cours", e);
        }

        return 0;
    }

    /**
     * Recherche un mandat par son numéro
     */
    private Optional<Mandat> findByNumero(String numeroMandat) {
        String sql = "SELECT * FROM mandats WHERE numero_mandat = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroMandat);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToMandat(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche du mandat", e);
        }

        return Optional.empty();
    }

    /**
     * Mappe un ResultSet vers un objet Mandat
     */
    private Mandat mapResultSetToMandat(ResultSet rs) throws SQLException {
        Mandat mandat = new Mandat();
        mandat.setId(rs.getLong("id"));
        mandat.setNumeroMandat(rs.getString("numero_mandat"));
        mandat.setDescription(rs.getString("description"));
        mandat.setDateDebut(rs.getDate("date_debut").toLocalDate());
        mandat.setDateFin(rs.getDate("date_fin").toLocalDate());
        mandat.setStatut(StatutMandat.valueOf(rs.getString("statut")));
        mandat.setActif(rs.getBoolean("actif"));

        Timestamp dateCloture = rs.getTimestamp("date_cloture");
        if (dateCloture != null) {
            mandat.setDateCloture(dateCloture.toLocalDateTime());
        }

        mandat.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        mandat.setCreatedBy(rs.getString("created_by"));

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            mandat.setUpdatedAt(updatedAt.toLocalDateTime());
            mandat.setUpdatedBy(rs.getString("updated_by"));
        }

        return mandat;
    }

    // Getters publics

    public Mandat getMandatActif() {
        return mandatActif;
    }

    public String getNumeroMandatActif() {
        return mandatActif != null ? mandatActif.getNumeroMandat() : null;
    }

    public boolean hasMandatActif() {
        return mandatActif != null && mandatActif.getStatut() == StatutMandat.ACTIF;
    }

    /**
     * Classe interne pour les statistiques d'un mandat
     */
    public static class MandatStatistiques {
        private String numeroMandat;
        private int nombreAffaires;
        private int affairesSoldees;
        private int affairesEnCours;
        private int nombreEncaissements;
        private BigDecimal montantTotalEncaisse;
        private int nombreAgents;

        // Getters et setters
        public String getNumeroMandat() { return numeroMandat; }
        public void setNumeroMandat(String numeroMandat) { this.numeroMandat = numeroMandat; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public int getAffairesSoldees() { return affairesSoldees; }
        public void setAffairesSoldees(int affairesSoldees) { this.affairesSoldees = affairesSoldees; }

        public int getAffairesEnCours() { return affairesEnCours; }
        public void setAffairesEnCours(int affairesEnCours) { this.affairesEnCours = affairesEnCours; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }

        public BigDecimal getMontantTotalEncaisse() { return montantTotalEncaisse; }
        public void setMontantTotalEncaisse(BigDecimal montantTotalEncaisse) { this.montantTotalEncaisse = montantTotalEncaisse; }

        public int getNombreAgents() { return nombreAgents; }
        public void setNombreAgents(int nombreAgents) { this.nombreAgents = nombreAgents; }
    }
}