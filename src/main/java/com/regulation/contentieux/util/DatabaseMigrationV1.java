package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Script de migration automatique de la base de données
 * Version 1.0 - Ajout des colonnes manquantes
 *
 * ATTENTION : Ce script modifie la structure de la base de données
 * Un backup est automatiquement créé avant toute modification
 */
public class DatabaseMigrationV1 {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationV1.class);
    private static final String MIGRATION_VERSION = "1.0";

    private Connection connection;
    private List<String> migrationLog = new ArrayList<>();
    private String backupPath;
    private int successCount = 0;
    private int errorCount = 0;

    /**
     * Point d'entrée principal pour exécuter la migration
     */
    public static void main(String[] args) {
        DatabaseMigrationV1 migration = new DatabaseMigrationV1();
        migration.execute();
    }

    /**
     * Exécute la migration complète
     */
    public void execute() {
        logger.info("🚀 === DÉBUT DE LA MIGRATION V{} ===", MIGRATION_VERSION);
        addLog("DÉBUT DE LA MIGRATION V" + MIGRATION_VERSION);

        try {
            // 1. Backup de la base
            if (!backupDatabase()) {
                logger.error("❌ Impossible de créer le backup. Migration annulée.");
                return;
            }

            // 2. Connexion à la base
            connection = DatabaseConfig.getSQLiteConnection();
            connection.setAutoCommit(false); // Transaction globale

            // 3. Exécution des migrations
            logger.info("📋 Exécution des migrations...");

            // Migration table AFFAIRES
            migrateAffairesTable();

            // Migration table ENCAISSEMENTS
            migrateEncaissementsTable();

            // Migration table AGENTS
            migrateAgentsTable();

            // Migration table CONTREVENANTS
            migrateContrevenantsTable();

            // Migration contraintes AFFAIRE_ACTEURS
            migrateAffaireActeursConstraints();

            // 4. Validation
            if (validateMigration()) {
                connection.commit();
                logger.info("✅ Migration réussie ! {} modifications appliquées.", successCount);
                addLog("MIGRATION RÉUSSIE - " + successCount + " modifications appliquées");
            } else {
                connection.rollback();
                logger.error("❌ Migration échouée. Rollback effectué.");
                addLog("MIGRATION ÉCHOUÉE - Rollback effectué");
            }

        } catch (Exception e) {
            logger.error("❌ Erreur fatale pendant la migration", e);
            addLog("ERREUR FATALE : " + e.getMessage());
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) {
                logger.error("Erreur lors du rollback", ex);
            }
        } finally {
            // 5. Nettoyage et rapport
            closeConnection();
            generateReport();
        }
    }

    /**
     * Crée un backup de la base de données
     */
    private boolean backupDatabase() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String dbPath = "gestion_contentieux.db";
            backupPath = "backup/gestion_contentieux_" + timestamp + ".db";

            // Créer le dossier backup s'il n'existe pas
            Files.createDirectories(Paths.get("backup"));

            // Copier la base
            Path source = Paths.get(dbPath);
            Path target = Paths.get(backupPath);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            logger.info("✅ Backup créé : {}", backupPath);
            addLog("Backup créé : " + backupPath);
            return true;

        } catch (IOException e) {
            logger.error("❌ Erreur lors de la création du backup", e);
            addLog("ERREUR : Impossible de créer le backup - " + e.getMessage());
            return false;
        }
    }

    /**
     * Migration de la table AFFAIRES
     */
    private void migrateAffairesTable() {
        logger.info("📊 Migration table AFFAIRES...");
        addLog("\n=== MIGRATION TABLE AFFAIRES ===");

        // Colonnes à ajouter
        addColumnIfNotExists("affaires", "date_constatation", "DATE");
        addColumnIfNotExists("affaires", "lieu_constatation", "TEXT");
        addColumnIfNotExists("affaires", "description", "TEXT");
        addColumnIfNotExists("affaires", "observations", "TEXT");
        addColumnIfNotExists("affaires", "agent_verbalisateur_id", "INTEGER REFERENCES agents(id)");
        addColumnIfNotExists("affaires", "montant_total", "REAL");
        addColumnIfNotExists("affaires", "montant_encaisse", "REAL DEFAULT 0");
        addColumnIfNotExists("affaires", "deleted", "INTEGER DEFAULT 0");
        addColumnIfNotExists("affaires", "deleted_by", "TEXT");
        addColumnIfNotExists("affaires", "deleted_at", "DATETIME");
        addColumnIfNotExists("affaires", "indicateur_existe", "INTEGER DEFAULT 0");

        // Migrer les données existantes
        migrateAffairesData();
    }

    /**
     * Migration de la table ENCAISSEMENTS
     */
    private void migrateEncaissementsTable() {
        logger.info("📊 Migration table ENCAISSEMENTS...");
        addLog("\n=== MIGRATION TABLE ENCAISSEMENTS ===");

        addColumnIfNotExists("encaissements", "statut", "TEXT DEFAULT 'VALIDE'");
        addColumnIfNotExists("encaissements", "observations", "TEXT");
        addColumnIfNotExists("encaissements", "created_by", "TEXT");
        addColumnIfNotExists("encaissements", "updated_by", "TEXT");
    }

    /**
     * Migration de la table AGENTS
     */
    private void migrateAgentsTable() {
        logger.info("📊 Migration table AGENTS...");
        addLog("\n=== MIGRATION TABLE AGENTS ===");

        addColumnIfNotExists("agents", "email", "TEXT");
        addColumnIfNotExists("agents", "telephone", "TEXT");
        addColumnIfNotExists("agents", "role_special", "TEXT CHECK(role_special IN ('DG', 'DD', NULL))");
    }

    /**
     * Migration de la table CONTREVENANTS
     */
    private void migrateContrevenantsTable() {
        logger.info("📊 Migration table CONTREVENANTS...");
        addLog("\n=== MIGRATION TABLE CONTREVENANTS ===");

        addColumnIfNotExists("contrevenants", "nom", "TEXT");
        addColumnIfNotExists("contrevenants", "prenom", "TEXT");
        addColumnIfNotExists("contrevenants", "raison_sociale", "TEXT");
        addColumnIfNotExists("contrevenants", "ville", "TEXT");
        addColumnIfNotExists("contrevenants", "code_postal", "TEXT");
        addColumnIfNotExists("contrevenants", "cin", "TEXT");
        addColumnIfNotExists("contrevenants", "numero_registre_commerce", "TEXT");
        addColumnIfNotExists("contrevenants", "numero_identification_fiscale", "TEXT");
    }

    /**
     * Migration des contraintes de AFFAIRE_ACTEURS
     */
    private void migrateAffaireActeursConstraints() {
        logger.info("📊 Migration contraintes AFFAIRE_ACTEURS...");
        addLog("\n=== MIGRATION CONTRAINTES AFFAIRE_ACTEURS ===");

        try {
            // Cette migration est plus complexe car elle nécessite de recréer la table
            // Pour l'instant, on log juste l'information
            addLog("INFO : La contrainte CHECK sur role_sur_affaire devrait inclure 'VERIFICATEUR'");
            addLog("      Migration manuelle recommandée pour cette contrainte");

        } catch (Exception e) {
            logger.error("Erreur lors de la migration des contraintes", e);
            addLog("ERREUR : " + e.getMessage());
        }
    }

    /**
     * Ajoute une colonne si elle n'existe pas déjà
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnDef) {
        if (!columnExists(tableName, columnName)) {
            try {
                String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef;
                Statement stmt = connection.createStatement();
                stmt.execute(sql);
                successCount++;
                logger.info("✅ Colonne ajoutée : {}.{}", tableName, columnName);
                addLog("✅ Ajouté : " + tableName + "." + columnName + " (" + columnDef + ")");
            } catch (SQLException e) {
                errorCount++;
                logger.error("❌ Erreur ajout colonne {}.{}", tableName, columnName, e);
                addLog("❌ Erreur : " + tableName + "." + columnName + " - " + e.getMessage());
            }
        } else {
            logger.info("ℹ️ Colonne existe déjà : {}.{}", tableName, columnName);
            addLog("ℹ️ Existe déjà : " + tableName + "." + columnName);
        }
    }

    /**
     * Vérifie si une colonne existe
     */
    private boolean columnExists(String tableName, String columnName) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet rs = meta.getColumns(null, null, tableName, columnName);
            return rs.next();
        } catch (SQLException e) {
            logger.error("Erreur vérification colonne", e);
            return false;
        }
    }

    /**
     * Migre les données existantes de la table affaires
     */
    private void migrateAffairesData() {
        try {
            // Copier montant_amende_total vers montant_total si vide
            String sql = "UPDATE affaires SET montant_total = montant_amende_total WHERE montant_total IS NULL";
            Statement stmt = connection.createStatement();
            int updated = stmt.executeUpdate(sql);
            if (updated > 0) {
                addLog("📝 Migré " + updated + " valeurs montant_total");
            }
        } catch (SQLException e) {
            logger.warn("Avertissement migration données affaires", e);
        }
    }

    /**
     * Valide que la migration s'est bien passée
     */
    private boolean validateMigration() {
        logger.info("🔍 Validation de la migration...");

        if (errorCount > 0) {
            logger.error("❌ {} erreurs détectées pendant la migration", errorCount);
            return false;
        }

        // Vérifications supplémentaires
        try {
            // Test de requête sur les nouvelles colonnes
            Statement stmt = connection.createStatement();
            stmt.executeQuery("SELECT date_constatation, lieu_constatation FROM affaires LIMIT 1");
            stmt.executeQuery("SELECT statut FROM encaissements LIMIT 1");

            logger.info("✅ Validation réussie");
            return true;

        } catch (SQLException e) {
            logger.error("❌ Échec de validation", e);
            return false;
        }
    }

    /**
     * Génère un rapport de migration
     */
    private void generateReport() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String reportPath = "migration_report_" + timestamp.replace(":", "-") + ".txt";

        try (FileWriter writer = new FileWriter(reportPath)) {
            writer.write("=== RAPPORT DE MIGRATION V" + MIGRATION_VERSION + " ===\n");
            writer.write("Date : " + timestamp + "\n");
            writer.write("Backup : " + backupPath + "\n");
            writer.write("Succès : " + successCount + "\n");
            writer.write("Erreurs : " + errorCount + "\n");
            writer.write("\n=== DÉTAILS ===\n");

            for (String log : migrationLog) {
                writer.write(log + "\n");
            }

            logger.info("📄 Rapport généré : {}", reportPath);

        } catch (IOException e) {
            logger.error("Erreur génération rapport", e);
        }
    }

    /**
     * Ajoute une entrée au log
     */
    private void addLog(String message) {
        migrationLog.add(message);
    }

    /**
     * Ferme la connexion
     */
    private void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.error("Erreur fermeture connexion", e);
        }
    }
}