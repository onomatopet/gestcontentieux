package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utilitaire pour corriger les problèmes de la table mandats existante
 * Format correct des mandats : YYMMM0001 (avec M séparateur)
 */
public class FixMandatsExistingTable {
    private static final Logger logger = LoggerFactory.getLogger(FixMandatsExistingTable.class);

    public static void main(String[] args) {
        logger.info("=== CORRECTION DE LA TABLE MANDATS EXISTANTE ===");
        logger.info("Format attendu : YYMMM0001 (ex: 2506M0001)");

        try {
            // 1. Vérifier le format des numéros existants
            verifyMandatNumberFormat();

            // 2. Nettoyer les données incohérentes
            cleanupMandatsData();

            // 3. Ajouter les colonnes manquantes (optionnel)
            addMissingColumns();

            // 4. Ajouter les triggers et contraintes
            addMandatsConstraints();

            // 5. Créer les index
            createMandatsIndexes();

            // 6. Vérifier et créer un mandat par défaut si nécessaire
            ensureDefaultMandat();

            // 7. Afficher l'état final
            displayMandatsStatus();

            logger.info("=== CORRECTION TERMINÉE AVEC SUCCÈS ===");

        } catch (Exception e) {
            logger.error("Erreur lors de la correction", e);
        }
    }

    /**
     * Vérifie le format des numéros de mandat existants
     */
    private static void verifyMandatNumberFormat() throws SQLException {
        logger.info("🔍 Vérification du format des numéros de mandat...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            // Compter les mandats avec format incorrect
            String checkSQL = """
                SELECT numero_mandat, LENGTH(numero_mandat) as len
                FROM mandats 
                WHERE NOT (numero_mandat GLOB '[0-9][0-9][0-9][0-9]M[0-9][0-9][0-9][0-9]')
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSQL)) {

                boolean hasIncorrect = false;
                while (rs.next()) {
                    hasIncorrect = true;
                    logger.warn("⚠️ Format incorrect détecté : {} (longueur: {})",
                            rs.getString("numero_mandat"), rs.getInt("len"));
                }

                if (!hasIncorrect) {
                    logger.info("✅ Tous les numéros de mandat sont au bon format YYMMM0001");
                }
            }
        }
    }

    /**
     * Nettoie les données incohérentes dans la table existante
     */
    private static void cleanupMandatsData() throws SQLException {
        logger.info("🧹 Nettoyage des données mandats...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Corriger les statuts invalides
                String updateInvalidStatusSQL = """
                    UPDATE mandats 
                    SET statut = 'EN_ATTENTE' 
                    WHERE statut NOT IN ('BROUILLON', 'ACTIF', 'CLOTURE', 'EN_ATTENTE')
                    OR statut IS NULL
                """;

                try (Statement stmt = conn.createStatement()) {
                    int updated = stmt.executeUpdate(updateInvalidStatusSQL);
                    if (updated > 0) {
                        logger.info("✅ {} statuts invalides corrigés", updated);
                    }
                }

                // 2. Synchroniser statut et actif
                String syncStatusSQL = """
                    UPDATE mandats 
                    SET actif = CASE 
                        WHEN statut = 'ACTIF' THEN 1 
                        ELSE 0 
                    END
                """;

                try (Statement stmt = conn.createStatement()) {
                    int synced = stmt.executeUpdate(syncStatusSQL);
                    logger.info("✅ {} mandats synchronisés (statut/actif)", synced);
                }

                // 3. S'assurer qu'un seul mandat est actif
                // D'abord, désactiver tous les mandats
                String deactivateAllSQL = "UPDATE mandats SET actif = 0, statut = 'EN_ATTENTE'";

                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(deactivateAllSQL);
                }

                // Ensuite, activer uniquement le plus récent non clôturé
                String activateLatestSQL = """
                    UPDATE mandats 
                    SET actif = 1, statut = 'ACTIF'
                    WHERE id = (
                        SELECT id FROM mandats 
                        WHERE statut != 'CLOTURE'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                    )
                """;

                try (Statement stmt = conn.createStatement()) {
                    int activated = stmt.executeUpdate(activateLatestSQL);
                    if (activated > 0) {
                        logger.info("✅ Mandat le plus récent activé");
                    }
                }

                // 4. Corriger les dates incohérentes
                String fixDatesSQL = """
                    UPDATE mandats 
                    SET date_fin = date(date_debut, '+1 month', '-1 day')
                    WHERE date_fin < date_debut
                    OR julianday(date_fin) - julianday(date_debut) > 35
                """;

                try (Statement stmt = conn.createStatement()) {
                    int fixedDates = stmt.executeUpdate(fixDatesSQL);
                    if (fixedDates > 0) {
                        logger.info("✅ {} dates corrigées", fixedDates);
                    }
                }

                conn.commit();
                logger.info("✅ Nettoyage des données terminé");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Ajoute les colonnes manquantes si possible
     */
    private static void addMissingColumns() throws SQLException {
        logger.info("📊 Vérification des colonnes...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            String[] alterStatements = {
                    "ALTER TABLE mandats ADD COLUMN nombre_affaires INTEGER DEFAULT 0",
                    "ALTER TABLE mandats ADD COLUMN montant_total DECIMAL(15,2) DEFAULT 0",
                    "ALTER TABLE mandats ADD COLUMN observations TEXT",
                    "ALTER TABLE mandats ADD COLUMN cloture_par TEXT"
            };

            for (String sql : alterStatements) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    logger.info("✅ Colonne ajoutée");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        logger.warn("⚠️ Impossible d'ajouter la colonne: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Ajoute les contraintes et triggers
     */
    private static void addMandatsConstraints() throws SQLException {
        logger.info("🔒 Ajout des contraintes mandats...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            // Supprimer les triggers existants
            String[] dropTriggers = {
                    "DROP TRIGGER IF EXISTS ensure_single_active_mandat",
                    "DROP TRIGGER IF EXISTS update_mandats_timestamp",
                    "DROP TRIGGER IF EXISTS validate_mandat_statut"
            };

            try (Statement stmt = conn.createStatement()) {
                for (String sql : dropTriggers) {
                    stmt.execute(sql);
                }
            }

            // Trigger pour maintenir un seul mandat actif
            String triggerSQL = """
                CREATE TRIGGER ensure_single_active_mandat
                BEFORE UPDATE OF actif ON mandats
                FOR EACH ROW
                WHEN NEW.actif = 1
                BEGIN
                    UPDATE mandats 
                    SET actif = 0, statut = 'EN_ATTENTE' 
                    WHERE id != NEW.id AND actif = 1;
                END
            """;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(triggerSQL);
                logger.info("✅ Trigger unicité mandat actif créé");
            }

            // Trigger pour synchroniser statut et actif
            String syncTriggerSQL = """
                CREATE TRIGGER validate_mandat_statut
                BEFORE UPDATE ON mandats
                FOR EACH ROW
                BEGIN
                    SELECT CASE
                        WHEN NEW.statut = 'ACTIF' AND NEW.actif = 0 THEN
                            RAISE(ABORT, 'Un mandat ACTIF doit avoir actif=1')
                        WHEN NEW.statut != 'ACTIF' AND NEW.actif = 1 THEN
                            RAISE(ABORT, 'Un mandat non-ACTIF ne peut pas avoir actif=1')
                    END;
                END
            """;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(syncTriggerSQL);
                logger.info("✅ Trigger validation statut créé");
            }
        }
    }

    /**
     * Crée les index pour optimiser les performances
     */
    private static void createMandatsIndexes() throws SQLException {
        logger.info("📊 Création des index mandats...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            // Supprimer les index existants
            String[] dropIndexes = {
                    "DROP INDEX IF EXISTS idx_mandats_numero",
                    "DROP INDEX IF EXISTS idx_mandats_actif",
                    "DROP INDEX IF EXISTS idx_mandats_statut",
                    "DROP INDEX IF EXISTS idx_mandats_dates"
            };

            try (Statement stmt = conn.createStatement()) {
                for (String sql : dropIndexes) {
                    stmt.execute(sql);
                }
            }

            // Créer les nouveaux index
            String[] createIndexes = {
                    "CREATE INDEX idx_mandats_numero ON mandats(numero_mandat)",
                    "CREATE INDEX idx_mandats_actif ON mandats(actif)",
                    "CREATE INDEX idx_mandats_statut ON mandats(statut)",
                    "CREATE INDEX idx_mandats_dates ON mandats(date_debut, date_fin)"
            };

            try (Statement stmt = conn.createStatement()) {
                for (String sql : createIndexes) {
                    stmt.execute(sql);
                }
                logger.info("✅ Index créés avec succès");
            }
        }
    }

    /**
     * S'assure qu'au moins un mandat existe
     */
    private static void ensureDefaultMandat() throws SQLException {
        logger.info("📝 Vérification de l'existence d'un mandat...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            // Vérifier s'il existe des mandats
            String countSQL = "SELECT COUNT(*) FROM mandats";
            int count = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSQL)) {

                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }

            if (count == 0) {
                logger.info("⚠️ Aucun mandat trouvé - Création d'un mandat par défaut...");

                LocalDate now = LocalDate.now();
                // Format CORRECT : YYMMM0001
                String numeroMandat = now.format(DateTimeFormatter.ofPattern("yyMM")) + "M0001";
                String description = "Mandat " + now.format(DateTimeFormatter.ofPattern("MMMM yyyy"));

                String insertSQL = """
                    INSERT INTO mandats (
                        numero_mandat, description, date_debut, date_fin, 
                        statut, actif, created_by
                    )
                    VALUES (?, ?, ?, ?, 'ACTIF', 1, 'system')
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                    pstmt.setString(1, numeroMandat);
                    pstmt.setString(2, description);
                    pstmt.setDate(3, Date.valueOf(now.withDayOfMonth(1)));
                    pstmt.setDate(4, Date.valueOf(now.withDayOfMonth(now.lengthOfMonth())));

                    pstmt.executeUpdate();
                    logger.info("✅ Mandat par défaut créé: {}", numeroMandat);
                }
            } else {
                logger.info("✅ {} mandat(s) existant(s)", count);
            }
        }
    }

    /**
     * Affiche l'état final de la table mandats
     */
    private static void displayMandatsStatus() throws SQLException {
        logger.info("📊 État final de la table mandats:");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {

            // Statistiques générales
            String statsSQL = """
                SELECT 
                    COUNT(*) as total,
                    COUNT(CASE WHEN actif = 1 THEN 1 END) as actifs,
                    COUNT(CASE WHEN statut = 'CLOTURE' THEN 1 END) as clotures
                FROM mandats
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(statsSQL)) {

                if (rs.next()) {
                    logger.info("📈 Total mandats: {}", rs.getInt("total"));
                    logger.info("✅ Mandats actifs: {}", rs.getInt("actifs"));
                    logger.info("🔒 Mandats clôturés: {}", rs.getInt("clotures"));
                }
            }

            // Afficher le mandat actif
            String activeSQL = """
                SELECT numero_mandat, description, date_debut, date_fin, statut
                FROM mandats 
                WHERE actif = 1
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(activeSQL)) {

                if (rs.next()) {
                    logger.info("📌 Mandat actif:");
                    logger.info("   - Numéro: {}", rs.getString("numero_mandat"));
                    logger.info("   - Description: {}", rs.getString("description"));
                    logger.info("   - Période: {} au {}",
                            rs.getDate("date_debut").toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            rs.getDate("date_fin").toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    );
                    logger.info("   - Statut: {}", rs.getString("statut"));
                } else {
                    logger.warn("⚠️ Aucun mandat actif trouvé!");
                }
            }

            // Vérifier les formats
            String formatCheckSQL = """
                SELECT COUNT(*) as incorrect
                FROM mandats 
                WHERE NOT (numero_mandat GLOB '[0-9][0-9][0-9][0-9]M[0-9][0-9][0-9][0-9]')
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(formatCheckSQL)) {

                if (rs.next() && rs.getInt("incorrect") > 0) {
                    logger.error("❌ {} mandats avec format incorrect!", rs.getInt("incorrect"));
                } else {
                    logger.info("✅ Tous les formats sont corrects (YYMMM0001)");
                }
            }
        }
    }
}