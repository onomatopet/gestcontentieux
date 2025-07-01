package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.exception.BusinessException;
import com.regulation.contentieux.model.Mandat;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.model.enums.StatutMandat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Crée un nouveau mandat pour le mois en cours
     * ENRICHISSEMENT : Validation stricte et gestion d'état
     */
    public Mandat creerNouveauMandat(String description, LocalDate dateDebut, LocalDate dateFin) {
        logger.info("🆕 === CRÉATION NOUVEAU MANDAT ===");

        // Récupérer le rôle de l'utilisateur courant
        RoleUtilisateur roleUtilisateur = AuthenticationService.getInstance().getCurrentUser().getRole();
        boolean isSuperAdmin = (roleUtilisateur == RoleUtilisateur.SUPER_ADMIN);

        // Validation des dates selon le rôle
        LocalDate now = LocalDate.now();
        LocalDate debutMoisCourant = now.withDayOfMonth(1);
        LocalDate finMoisCourant = now.withDayOfMonth(now.lengthOfMonth());

        if (!isSuperAdmin) {
            // Pour les non SUPER_ADMIN, forcer les dates au mois courant
            if (!dateDebut.equals(debutMoisCourant) || !dateFin.equals(finMoisCourant)) {
                throw new BusinessException(
                        "ERREUR : Vous ne pouvez créer des mandats que pour le mois en cours.\n" +
                                "Pour créer un mandat d'une période différente, contactez un Super Administrateur."
                );
            }
        } else {
            // Pour SUPER_ADMIN, valider la cohérence des dates
            if (dateDebut.isAfter(dateFin)) {
                throw new BusinessException("La date de début doit être avant la date de fin");
            }

            // Vérifier que les dates sont dans le même mois
            if (dateDebut.getMonth() != dateFin.getMonth() || dateDebut.getYear() != dateFin.getYear()) {
                throw new BusinessException("Un mandat doit couvrir un seul mois calendaire");
            }

            // Vérifier que dateDebut est le premier jour du mois et dateFin le dernier
            if (dateDebut.getDayOfMonth() != 1) {
                throw new BusinessException("La date de début doit être le premier jour du mois");
            }
            if (dateFin.getDayOfMonth() != dateFin.lengthOfMonth()) {
                throw new BusinessException("La date de fin doit être le dernier jour du mois");
            }
        }

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

        // Générer le numéro avec la date fournie
        String numeroMandat = genererNouveauMandat(dateDebut);

        // Créer le mandat
        Mandat nouveauMandat = new Mandat();
        nouveauMandat.setNumeroMandat(numeroMandat);
        nouveauMandat.setDescription(description != null ? description :
                "Mandat du mois " + dateDebut.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        nouveauMandat.setDateDebut(dateDebut);
        nouveauMandat.setDateFin(dateFin);
        nouveauMandat.setStatut(StatutMandat.BROUILLON);
        nouveauMandat.setCreatedAt(LocalDateTime.now());
        nouveauMandat.setCreatedBy(AuthenticationService.getInstance().getCurrentUser().getLogin());

        // Sauvegarder en base
        sauvegarderMandat(nouveauMandat);

        logger.info("✅ Mandat créé avec format corrigé : {} pour la période {} - {}",
                numeroMandat, dateDebut, dateFin);
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

        // Vérifier qu'il n'est pas clôturé
        if (mandat.getStatut() == StatutMandat.CLOTURE) {
            throw new BusinessException("Impossible d'activer un mandat clôturé");
        }

        String sql = "UPDATE mandats SET actif = 0, statut = 'EN_ATTENTE', updated_at = CURRENT_TIMESTAMP";
        String sqlActivate = "UPDATE mandats SET actif = 1, statut = 'ACTIF', updated_at = CURRENT_TIMESTAMP WHERE numero_mandat = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try {
                // Désactiver tous les mandats
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sql);
                }

                // Activer le mandat sélectionné
                try (PreparedStatement stmt = conn.prepareStatement(sqlActivate)) {
                    stmt.setString(1, numeroMandat);
                    stmt.executeUpdate();
                }

                conn.commit();

                // Mettre à jour le cache
                mandat.setStatut(StatutMandat.ACTIF);
                mandat.setActif(true);
                this.mandatActif = mandat;

                logger.info("✅ Mandat {} activé avec succès", numeroMandat);

            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Erreur lors de l'activation du mandat", e);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de l'activation du mandat", e);
            throw new RuntimeException("Impossible d'activer le mandat", e);
        }
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
     * Vérifie si une date est dans le mandat actif
     * ENRICHISSEMENT : Validation stricte des dates
     */
    public boolean estDansMandatActif(LocalDate date) {
        if (mandatActif == null || date == null) {
            return false;
        }

        return !date.isBefore(mandatActif.getDateDebut()) &&
                !date.isAfter(mandatActif.getDateFin());
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
    private String genererNouveauMandat(LocalDate dateReference) {
        logger.info("🔢 Génération d'un nouveau numéro de mandat pour la date : {}", dateReference);

        // Format CORRECT : YYMMM0001 (avec M séparateur) basé sur la date fournie
        String prefixe = dateReference.format(DateTimeFormatter.ofPattern("yyMM")) + "M";

        // Rechercher le dernier mandat du mois
        String sql = """
        SELECT numero_mandat 
        FROM mandats 
        WHERE numero_mandat LIKE ? 
        ORDER BY numero_mandat DESC 
        LIMIT 1
    """;

        String dernierNumero = null;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefixe.substring(0, 4) + "%"); // Recherche par YYMM%
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                dernierNumero = rs.getString("numero_mandat");
                logger.debug("Dernier mandat trouvé : {}", dernierNumero);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche du dernier mandat", e);
        }

        // Si aucun mandat ce mois-ci ou format invalide
        if (dernierNumero == null || !dernierNumero.startsWith(prefixe)) {
            return prefixe + "0001";
        }

        try {
            // Extraire le numéro séquentiel (4 derniers caractères après le M)
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
            logger.info("✅ Nouveau mandat généré : {}", nouveauNumero);

            return nouveauNumero;

        } catch (NumberFormatException e) {
            logger.warn("Format invalide, génération d'un nouveau : {}", prefixe + "0001");
            return prefixe + "0001";
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