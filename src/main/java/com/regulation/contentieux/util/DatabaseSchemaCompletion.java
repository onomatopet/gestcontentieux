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
 * Utilitaire pour compléter le schéma de la base existante
 * SANS ÉCRASER LES DONNÉES - Juste ajouter ce qui manque
 */
public class DatabaseSchemaCompletion {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaCompletion.class);

    /**
     * Méthode principale pour compléter le schéma existant
     */
    public static void completeExistingSchema() {
        logger.info("=== COMPLETION DU SCHÉMA EXISTANT ===");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Ajouter les colonnes manquantes aux tables existantes
                addMissingColumnsToExistingTables(conn);

                // 2. Créer les tables de référence manquantes
                createMissingReferenceTables(conn);

                // 3. Créer les tables de liaison manquantes
                createMissingJunctionTables(conn);

                // 4. Insérer les données de référence de base
                insertBasicReferenceData(conn);

                conn.commit();
                logger.info("✅ Schéma complété avec succès");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de la completion du schéma", e);
            throw new RuntimeException("Impossible de compléter le schéma", e);
        }
    }

    /**
     * Ajoute les colonnes manquantes aux tables existantes
     */
    private static void addMissingColumnsToExistingTables(Connection conn) throws SQLException {
        logger.info("Ajout des colonnes manquantes...");

        try (Statement stmt = conn.createStatement()) {
            // AFFAIRES - colonnes de traçabilité
            addColumnIfNotExists(stmt, "affaires", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "affaires", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "affaires", "created_by", "TEXT");
            addColumnIfNotExists(stmt, "affaires", "updated_by", "TEXT");

            // CONTREVENANTS - colonnes manquantes
            addColumnIfNotExists(stmt, "contrevenants", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "contrevenants", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "contrevenants", "type_personne", "TEXT CHECK(type_personne IN ('PHYSIQUE', 'MORALE'))");
            addColumnIfNotExists(stmt, "contrevenants", "adresse", "TEXT");
            addColumnIfNotExists(stmt, "contrevenants", "telephone", "TEXT");
            addColumnIfNotExists(stmt, "contrevenants", "email", "TEXT");

            // AGENTS - colonnes manquantes
            addColumnIfNotExists(stmt, "agents", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "agents", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");

            // UTILISATEURS - colonnes manquantes
            addColumnIfNotExists(stmt, "utilisateurs", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "utilisateurs", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(stmt, "utilisateurs", "last_login_at", "DATETIME");

            // Mettre à jour les timestamps par défaut pour les enregistrements existants
            updateDefaultTimestamps(stmt);
        }
    }

    /**
     * Crée les tables de référence manquantes
     */
    private static void createMissingReferenceTables(Connection conn) throws SQLException {
        logger.info("Création des tables de référence manquantes...");

        try (Statement stmt = conn.createStatement()) {
            // Table services
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS services (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_service TEXT NOT NULL UNIQUE,
                    nom_service TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Table bureaux
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bureaux (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_bureau TEXT NOT NULL UNIQUE,
                    nom_bureau TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Table centres
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS centres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_centre TEXT NOT NULL UNIQUE,
                    nom_centre TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Table banques
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS banques (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_banque TEXT NOT NULL UNIQUE,
                    nom_banque TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Table contraventions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contraventions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    libelle TEXT NOT NULL,
                    description TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            logger.info("Tables de référence créées");
        }
    }

    /**
     * Crée les tables de liaison et encaissements manquantes
     */
    private static void createMissingJunctionTables(Connection conn) throws SQLException {
        logger.info("Création des tables de liaison manquantes...");

        try (Statement stmt = conn.createStatement()) {
            // Table encaissements
            stmt.execute("""
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

            // Table affaire_acteurs (liaison affaire-agent)
            stmt.execute("""
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

            // Table repartition_resultats
            stmt.execute("""
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

            // Table roles_speciaux
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS roles_speciaux (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    agent_id INTEGER NOT NULL,
                    type_role TEXT NOT NULL,
                    date_debut DATE NOT NULL,
                    date_fin DATE,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (agent_id) REFERENCES agents (id)
                )
            """);

            logger.info("Tables de liaison créées");
        }
    }

    /**
     * Insère les données de référence de base
     */
    private static void insertBasicReferenceData(Connection conn) throws SQLException {
        logger.info("Insertion des données de référence de base...");

        try (Statement stmt = conn.createStatement()) {
            // Services (si la table est vide)
            if (isTableEmpty(conn, "services")) {
                stmt.execute("""
                    INSERT INTO services (code_service, nom_service) VALUES
                    ('SRV01', 'Service Fiscal'),
                    ('SRV02', 'Service Douanier'),
                    ('SRV03', 'Service Commercial'),
                    ('SRV04', 'Service Administratif')
                """);
            }

            // Bureaux (si la table est vide)
            if (isTableEmpty(conn, "bureaux")) {
                stmt.execute("""
                    INSERT INTO bureaux (code_bureau, nom_bureau) VALUES
                    ('BUR01', 'Bureau Central'),
                    ('BUR02', 'Bureau Nord'),
                    ('BUR03', 'Bureau Sud'),
                    ('BUR04', 'Bureau Est')
                """);
            }

            // Centres (si la table est vide)
            if (isTableEmpty(conn, "centres")) {
                stmt.execute("""
                    INSERT INTO centres (code_centre, nom_centre) VALUES
                    ('CTR01', 'Centre Principal'),
                    ('CTR02', 'Centre Secondaire'),
                    ('CTR03', 'Centre Régional')
                """);
            }

            // Banques (si la table est vide)
            if (isTableEmpty(conn, "banques")) {
                stmt.execute("""
                    INSERT INTO banques (code_banque, nom_banque) VALUES
                    ('BQ001', 'Banque Centrale du Gabon'),
                    ('BQ002', 'BGFI Bank'),
                    ('BQ003', 'Banque Populaire du Gabon'),
                    ('BQ004', 'Orabank Gabon')
                """);
            }

            // Contraventions (si la table est vide)
            if (isTableEmpty(conn, "contraventions")) {
                stmt.execute("""
                    INSERT INTO contraventions (code, libelle, description) VALUES
                    ('CV001', 'Défaut de déclaration fiscale', 'Non déclaration dans les délais légaux'),
                    ('CV002', 'Contrebande', 'Importation illégale de marchandises'),
                    ('CV003', 'Défaut de licence commerciale', 'Exercice d''activité sans autorisation'),
                    ('CV004', 'Non-conformité réglementaire', 'Non respect des normes en vigueur'),
                    ('CV005', 'Fraude documentaire', 'Falsification de documents officiels')
                """);
            }

            logger.info("Données de référence insérées");
        }
    }

    // Méthodes utilitaires

    private static void addColumnIfNotExists(Statement stmt, String tableName, String columnName, String columnDef) {
        try {
            String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, columnDef);
            stmt.execute(sql);
            logger.debug("✅ Colonne {} ajoutée à {}", columnName, tableName);
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate column")) {
                logger.debug("ℹ️  Colonne {} existe déjà dans {}", columnName, tableName);
            } else {
                logger.warn("⚠️  Erreur pour {}. {}: {}", tableName, columnName, e.getMessage());
            }
        }
    }

    private static void updateDefaultTimestamps(Statement stmt) throws SQLException {
        logger.info("Mise à jour des timestamps par défaut...");

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
                int count = stmt.executeUpdate(update);
                if (count > 0) {
                    logger.debug("Mise à jour de {} enregistrements", count);
                }
            } catch (SQLException e) {
                // Ignorer les erreurs pour les colonnes qui n'existent pas encore
                logger.debug("Mise à jour ignorée: {}", e.getMessage());
            }
        }
    }

    private static boolean isTableEmpty(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            // Table n'existe probablement pas encore
            return true;
        }
        return true;
    }

    /**
     * Méthode à appeler depuis DatabaseConfig.initializeSQLite()
     */
    public static void executeSchemaCompletion() {
        try {
            completeExistingSchema();
            logger.info("Completion du schéma terminée avec succès");
        } catch (Exception e) {
            logger.error("Erreur lors de la completion du schéma", e);
            // Ne pas faire échouer l'application, juste logger l'erreur
        }
    }
}