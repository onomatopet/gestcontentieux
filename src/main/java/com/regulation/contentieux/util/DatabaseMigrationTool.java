package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Outil de migration pour ajouter les colonnes manquantes à la base existante
 * SANS ÉCRASER LES DONNÉES EXISTANTES
 */
public class DatabaseMigrationTool {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationTool.class);

    public static void main(String[] args) {
        System.out.println("=== MIGRATION DE LA BASE DE DONNÉES EXISTANTE ===\n");

        try {
            // 1. Vérifier l'état actuel
            checkCurrentDatabase();

            // 2. Ajouter les colonnes manquantes
            addMissingColumns();

            // 3. Créer les tables manquantes (sans données)
            createMissingTables();

            // 4. Vérifier la migration
            verifyMigration();

            System.out.println("\n✅ Migration terminée avec succès !");

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Vérifie l'état actuel de la base de données
     */
    private static void checkCurrentDatabase() {
        System.out.println("1. VÉRIFICATION DE LA BASE EXISTANTE:");
        System.out.println("------------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            String[] existingTables = {"affaires", "contrevenants", "agents", "utilisateurs"};

            for (String tableName : existingTables) {
                if (tableExists(conn, tableName)) {
                    int count = getTableCount(conn, tableName);
                    System.out.println("✅ " + tableName + ": " + count + " enregistrements");

                    // Vérifier les colonnes existantes
                    checkTableColumns(conn, tableName);
                } else {
                    System.out.println("❌ " + tableName + ": TABLE MANQUANTE");
                }
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la vérification: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * Ajoute les colonnes manquantes aux tables existantes
     */
    public static void addMissingColumns() {
        System.out.println("2. AJOUT DES COLONNES MANQUANTES:");
        System.out.println("---------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {

                // AFFAIRES - Ajouter colonnes de traçabilité
                addColumnSafely(stmt, "affaires", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "affaires", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "affaires", "created_by", "TEXT");
                addColumnSafely(stmt, "affaires", "updated_by", "TEXT");

                // CONTREVENANTS - Ajouter colonnes manquantes
                addColumnSafely(stmt, "contrevenants", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "contrevenants", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "contrevenants", "type_personne", "TEXT CHECK(type_personne IN ('PHYSIQUE', 'MORALE'))");
                addColumnSafely(stmt, "contrevenants", "adresse", "TEXT");
                addColumnSafely(stmt, "contrevenants", "telephone", "TEXT");
                addColumnSafely(stmt, "contrevenants", "email", "TEXT");

                // AGENTS - Ajouter colonnes manquantes
                addColumnSafely(stmt, "agents", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "agents", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");

                // UTILISATEURS - Ajouter colonnes manquantes
                addColumnSafely(stmt, "utilisateurs", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "utilisateurs", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "utilisateurs", "last_login_at", "DATETIME");

                // Mettre à jour les valeurs par défaut pour les enregistrements existants
                updateDefaultTimestamps(stmt);

                conn.commit();
                System.out.println("✅ Toutes les colonnes ajoutées avec succès");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de l'ajout des colonnes: " + e.getMessage());
            throw new RuntimeException(e);
        }

        System.out.println();
    }

    /**
     * Crée les tables manquantes (sans données de test)
     */
    private static void createMissingTables() {
        System.out.println("3. CRÉATION DES TABLES MANQUANTES:");
        System.out.println("----------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {

                // Services
                createTableIfNotExists(stmt, "services", """
                    CREATE TABLE IF NOT EXISTS services (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_service TEXT NOT NULL UNIQUE,
                        nom_service TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Bureaux
                createTableIfNotExists(stmt, "bureaux", """
                    CREATE TABLE IF NOT EXISTS bureaux (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_bureau TEXT NOT NULL UNIQUE,
                        nom_bureau TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Centres
                createTableIfNotExists(stmt, "centres", """
                    CREATE TABLE IF NOT EXISTS centres (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_centre TEXT NOT NULL UNIQUE,
                        nom_centre TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Banques
                createTableIfNotExists(stmt, "banques", """
                    CREATE TABLE IF NOT EXISTS banques (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_banque TEXT NOT NULL UNIQUE,
                        nom_banque TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Contraventions
                createTableIfNotExists(stmt, "contraventions", """
                    CREATE TABLE IF NOT EXISTS contraventions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE,
                        libelle TEXT NOT NULL,
                        description TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Encaissements
                createTableIfNotExists(stmt, "encaissements", """
                    CREATE TABLE IF NOT EXISTS encaissements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        affaire_id INTEGER NOT NULL,
                        montant_encaisse REAL NOT NULL,
                        date_encaissement DATE NOT NULL,
                        mode_reglement TEXT NOT NULL,
                        reference TEXT,
                        banque_id INTEGER,
                        statut TEXT DEFAULT 'EN_ATTENTE',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        created_by TEXT,
                        updated_by TEXT,
                        FOREIGN KEY (affaire_id) REFERENCES affaires (id),
                        FOREIGN KEY (banque_id) REFERENCES banques (id)
                    )
                """);

                // Affaire-Acteurs
                createTableIfNotExists(stmt, "affaire_acteurs", """
                    CREATE TABLE IF NOT EXISTS affaire_acteurs (
                        affaire_id INTEGER NOT NULL,
                        agent_id INTEGER NOT NULL,
                        role_sur_affaire TEXT NOT NULL,
                        assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        assigned_by TEXT,
                        PRIMARY KEY (affaire_id, agent_id, role_sur_affaire),
                        FOREIGN KEY (affaire_id) REFERENCES affaires (id),
                        FOREIGN KEY (agent_id) REFERENCES agents (id)
                    )
                """);

                // Repartition résultats
                createTableIfNotExists(stmt, "repartition_resultats", """
                    CREATE TABLE IF NOT EXISTS repartition_resultats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        encaissement_id INTEGER NOT NULL,
                        destinataire TEXT NOT NULL,
                        pourcentage REAL NOT NULL,
                        montant_calcule REAL NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (encaissement_id) REFERENCES encaissements (id)
                    )
                """);

                conn.commit();
                System.out.println("✅ Tables manquantes créées");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la création des tables: " + e.getMessage());
            throw new RuntimeException(e);
        }

        System.out.println();
    }

    /**
     * Vérifie la migration
     */
    private static void verifyMigration() {
        System.out.println("4. VÉRIFICATION DE LA MIGRATION:");
        System.out.println("--------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Tester une requête avec les nouvelles colonnes
            String testQuery = """
                SELECT id, numero_affaire, created_at, updated_at, created_by 
                FROM affaires 
                LIMIT 3
            """;

            try (PreparedStatement stmt = conn.prepareStatement(testQuery);
                 ResultSet rs = stmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("Affaire %d: %s (créée: %s)%n",
                            rs.getLong("id"),
                            rs.getString("numero_affaire"),
                            rs.getString("created_at"));
                }
                System.out.println("✅ Requête avec nouvelles colonnes réussie - " + count + " enregistrements lus");
            }

            // Compter les enregistrements préservés
            logFinalCounts(conn);

        } catch (SQLException e) {
            System.err.println("❌ Erreur lors de la vérification: " + e.getMessage());
        }
    }

    // Méthodes utilitaires

    private static boolean tableExists(Connection conn, String tableName) {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static int getTableCount(Connection conn, String tableName) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // Table probablement inexistante
        }
        return 0;
    }

    private static void checkTableColumns(Connection conn, String tableName) {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            System.out.print("  Colonnes existantes: ");
            boolean first = true;
            while (rs.next()) {
                if (!first) System.out.print(", ");
                System.out.print(rs.getString("name"));
                first = false;
            }
            System.out.println();

        } catch (SQLException e) {
            System.out.println("  Erreur lors de la vérification des colonnes");
        }
    }

    private static void addColumnSafely(Statement stmt, String tableName, String columnName, String columnDef) {
        String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, columnDef);
        try {
            stmt.execute(sql);
            System.out.println("✅ " + tableName + "." + columnName + " ajoutée");
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate column")) {
                System.out.println("ℹ️  " + tableName + "." + columnName + " existe déjà");
            } else {
                System.err.println("❌ Erreur pour " + tableName + "." + columnName + ": " + e.getMessage());
            }
        }
    }

    private static void createTableIfNotExists(Statement stmt, String tableName, String createSql) {
        try {
            stmt.execute(createSql);
            System.out.println("✅ Table " + tableName + " créée");
        } catch (SQLException e) {
            System.err.println("❌ Erreur pour la table " + tableName + ": " + e.getMessage());
        }
    }

    private static void updateDefaultTimestamps(Statement stmt) throws SQLException {
        System.out.println("Mise à jour des timestamps par défaut...");

        // Mettre à jour les enregistrements existants qui n'ont pas de timestamps
        String[] updates = {
                "UPDATE affaires SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL",
                "UPDATE affaires SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL",
                "UPDATE affaires SET created_by = 'migration' WHERE created_by IS NULL",
                "UPDATE contrevenants SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL",
                "UPDATE contrevenants SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL",
                "UPDATE agents SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL",
                "UPDATE agents SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL",
                "UPDATE utilisateurs SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL",
                "UPDATE utilisateurs SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL"
        };

        for (String update : updates) {
            try {
                int updated = stmt.executeUpdate(update);
                if (updated > 0) {
                    System.out.println("  " + updated + " enregistrements mis à jour");
                }
            } catch (SQLException e) {
                // Ignorer les erreurs pour les colonnes qui n'existent pas encore
            }
        }
    }

    private static void logFinalCounts(Connection conn) {
        String[] tables = {"affaires", "contrevenants", "agents", "utilisateurs",
                "services", "bureaux", "centres", "banques", "contraventions"};

        System.out.println("\nCompte final des enregistrements:");
        for (String table : tables) {
            int count = getTableCount(conn, table);
            if (count > 0) {
                System.out.println("  " + table + ": " + count + " enregistrements");
            }
        }
    }

    /**
     * Méthode pour exécuter la migration depuis l'application
     */
    public static void migrateExistingDatabase() {
        logger.info("Migration de la base de données existante...");

        try {
            addMissingColumns();
            createMissingTables();
            logger.info("Migration terminée avec succès");
        } catch (Exception e) {
            logger.error("Erreur lors de la migration", e);
            throw new RuntimeException("Impossible de migrer la base de données", e);
        }
    }
}