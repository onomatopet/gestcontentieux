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
 * Utilitaire pour mettre à jour le schéma de base de données
 * Ajoute les tables manquantes du cahier des charges
 */
public class DatabaseSchemaUpdate {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaUpdate.class);

    public static void main(String[] args) {
        System.out.println("=== MISE À JOUR DU SCHÉMA DE BASE DE DONNÉES ===\n");

        try {
            // 1. Vérifier l'état actuel
            checkCurrentSchema();

            // 2. Ajouter les tables manquantes
            createMissingTables();

            // 3. Ajouter quelques données de test si nécessaire
            insertTestData();

            System.out.println("\n✅ Mise à jour du schéma terminée avec succès !");

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la mise à jour du schéma: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Vérifie l'état actuel du schéma
     */
    private static void checkCurrentSchema() {
        System.out.println("1. VÉRIFICATION DU SCHÉMA ACTUEL:");
        System.out.println("---------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            String[] expectedTables = {
                    "utilisateurs", "affaires", "contrevenants", "agents",
                    "services", "bureaux", "centres", "banques", "contraventions"
            };

            for (String tableName : expectedTables) {
                if (tableExists(conn, tableName)) {
                    int count = getTableCount(conn, tableName);
                    System.out.println("✅ " + tableName + ": " + count + " enregistrements");
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
     * Crée les tables manquantes
     */
    private static void createMissingTables() {
        System.out.println("2. CRÉATION DES TABLES MANQUANTES:");
        System.out.println("----------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Table services
                createTableIfNotExists(stmt, "services", """
                    CREATE TABLE IF NOT EXISTS services (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_service TEXT NOT NULL UNIQUE,
                        nom_service TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Table bureaux
                createTableIfNotExists(stmt, "bureaux", """
                    CREATE TABLE IF NOT EXISTS bureaux (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_bureau TEXT NOT NULL UNIQUE,
                        nom_bureau TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Table centres
                createTableIfNotExists(stmt, "centres", """
                    CREATE TABLE IF NOT EXISTS centres (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_centre TEXT NOT NULL UNIQUE,
                        nom_centre TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Table banques
                createTableIfNotExists(stmt, "banques", """
                    CREATE TABLE IF NOT EXISTS banques (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code_banque TEXT NOT NULL UNIQUE,
                        nom_banque TEXT NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Table contraventions
                createTableIfNotExists(stmt, "contraventions", """
                    CREATE TABLE IF NOT EXISTS contraventions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE,
                        libelle TEXT NOT NULL,
                        description TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Table encaissements
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

                // Table affaire_acteurs (liaison affaire-agent)
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

                // Table repartition_resultats
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
                System.out.println("✅ Toutes les tables ont été créées/vérifiées");

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
     * Insère des données de test si les tables sont vides
     */
    private static void insertTestData() {
        System.out.println("3. INSERTION DES DONNÉES DE TEST:");
        System.out.println("---------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try {
                // Services
                insertTestDataIfEmpty(conn, "services", """
                    INSERT INTO services (code_service, nom_service) VALUES
                    ('SRV01', 'Service Fiscal'),
                    ('SRV02', 'Service Douanier'),
                    ('SRV03', 'Service Commercial'),
                    ('SRV04', 'Service Administratif')
                """);

                // Bureaux
                insertTestDataIfEmpty(conn, "bureaux", """
                    INSERT INTO bureaux (code_bureau, nom_bureau) VALUES
                    ('BUR01', 'Bureau Central'),
                    ('BUR02', 'Bureau Nord'),
                    ('BUR03', 'Bureau Sud'),
                    ('BUR04', 'Bureau Est')
                """);

                // Centres
                insertTestDataIfEmpty(conn, "centres", """
                    INSERT INTO centres (code_centre, nom_centre) VALUES
                    ('CTR01', 'Centre Principal'),
                    ('CTR02', 'Centre Secondaire'),
                    ('CTR03', 'Centre Régional')
                """);

                // Banques
                insertTestDataIfEmpty(conn, "banques", """
                    INSERT INTO banques (code_banque, nom_banque) VALUES
                    ('BQ001', 'Banque Centrale'),
                    ('BQ002', 'Banque Commerciale'),
                    ('BQ003', 'Banque Populaire'),
                    ('BQ004', 'Banque de Développement')
                """);

                // Contraventions
                insertTestDataIfEmpty(conn, "contraventions", """
                    INSERT INTO contraventions (code, libelle, description) VALUES
                    ('CV001', 'Défaut de déclaration fiscale', 'Non déclaration dans les délais'),
                    ('CV002', 'Contrebande', 'Importation illégale de marchandises'),
                    ('CV003', 'Défaut de licence commerciale', 'Exercice sans autorisation'),
                    ('CV004', 'Non-conformité réglementaire', 'Non respect des normes'),
                    ('CV005', 'Fraude documentaire', 'Falsification de documents')
                """);

                // Contrevenants de test
                insertTestDataIfEmpty(conn, "contrevenants", """
                    INSERT INTO contrevenants (code, nom_complet, type_personne, telephone, email) VALUES
                    ('CV00001', 'MARTIN Jean-Pierre', 'PHYSIQUE', '+241 01 23 45 67', 'martin.jp@email.com'),
                    ('CV00002', 'NGUEMA Marie-Claire', 'PHYSIQUE', '+241 02 34 56 78', 'nguema.mc@email.com'),
                    ('CV00003', 'SOCIÉTÉ GABONAISE SARL', 'MORALE', '+241 03 45 67 89', 'contact@societe-gab.ga'),
                    ('CV00004', 'ENTREPRISE COMMERCE SA', 'MORALE', '+241 04 56 78 90', 'info@entreprise.com'),
                    ('CV00005', 'OBAME Paul-Henri', 'PHYSIQUE', '+241 05 67 89 01', 'obame.ph@email.com')
                """);

                // Agents de test
                insertTestDataIfEmpty(conn, "agents", """
                    INSERT INTO agents (code_agent, nom, prenom, grade, service_id, actif) VALUES
                    ('AG00001', 'AKOMO', 'Pierre', 'Inspecteur Principal', 1, 1),
                    ('AG00002', 'BENGONE', 'Marie', 'Inspecteur', 1, 1),
                    ('AG00003', 'MENDAME', 'Joseph', 'Contrôleur Principal', 2, 1),
                    ('AG00004', 'NZOGHE', 'Anne', 'Contrôleur', 2, 1),
                    ('AG00005', 'ONDO', 'François', 'Agent Principal', 3, 1)
                """);

                // Affaires de test (plus réalistes)
                insertTestDataIfEmpty(conn, "affaires", """
                    INSERT INTO affaires (numero_affaire, date_creation, montant_amende_total, statut, 
                                        contrevenant_id, contravention_id, bureau_id, service_id, created_by) VALUES
                    ('24120001', '2024-12-01', 500000, 'OUVERTE', 1, 1, 1, 1, 'admin'),
                    ('24120002', '2024-12-05', 1200000, 'EN_COURS', 2, 2, 2, 2, 'admin'),
                    ('24120003', '2024-12-10', 2500000, 'OUVERTE', 3, 3, 1, 3, 'admin'),
                    ('24120004', '2024-12-15', 750000, 'CLOTUREE', 4, 4, 3, 1, 'admin'),
                    ('24120005', '2024-12-20', 1800000, 'EN_COURS', 5, 5, 2, 2, 'admin'),
                    ('25010001', '2025-01-05', 950000, 'OUVERTE', 1, 1, 1, 1, 'admin'),
                    ('25010002', '2025-01-10', 3200000, 'EN_COURS', 3, 2, 2, 2, 'admin'),
                    ('25010003', '2025-01-15', 1450000, 'OUVERTE', 2, 3, 3, 3, 'admin')
                """);

                // Encaissements de test
                insertTestDataIfEmpty(conn, "encaissements", """
                    INSERT INTO encaissements (affaire_id, montant_encaisse, date_encaissement, 
                                             mode_reglement, reference, banque_id, statut, created_by) VALUES
                    (4, 750000, '2024-12-18', 'VIREMENT', 'VIR-2024-001', 1, 'VALIDE', 'admin'),
                    (2, 600000, '2024-12-22', 'CHEQUE', 'CHQ-123456', 2, 'VALIDE', 'admin'),
                    (1, 250000, '2025-01-08', 'ESPECES', 'ESP-001', NULL, 'VALIDE', 'admin'),
                    (5, 900000, '2025-01-12', 'MANDAT', 'MAN-789012', 3, 'EN_ATTENTE', 'admin')
                """);

                conn.commit();
                System.out.println("✅ Données de test insérées avec succès");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de l'insertion des données de test: " + e.getMessage());
            throw new RuntimeException(e);
        }

        System.out.println();
    }

    /**
     * Vérifie si une table existe
     */
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

    /**
     * Compte les enregistrements d'une table
     */
    private static int getTableCount(Connection conn, String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            // Table probablement inexistante
        }
        return 0;
    }

    /**
     * Crée une table si elle n'existe pas
     */
    private static void createTableIfNotExists(Statement stmt, String tableName, String createSql)
            throws SQLException {
        try {
            stmt.execute(createSql);
            System.out.println("✅ Table " + tableName + " créée/vérifiée");
        } catch (SQLException e) {
            System.err.println("❌ Erreur pour la table " + tableName + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Insère des données de test seulement si la table est vide
     */
    private static void insertTestDataIfEmpty(Connection conn, String tableName, String insertSql)
            throws SQLException {
        int count = getTableCount(conn, tableName);

        if (count == 0) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(insertSql);
                int newCount = getTableCount(conn, tableName);
                System.out.println("✅ " + tableName + ": " + newCount + " enregistrements insérés");
            }
        } else {
            System.out.println("ℹ️  " + tableName + ": " + count + " enregistrements existants (pas d'insertion)");
        }
    }

    /**
     * Méthode utilitaire pour exécuter la mise à jour depuis l'application
     */
    public static void updateSchemaIfNeeded() {
        logger.info("Vérification du schéma de base de données...");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Vérifier quelques tables clés
            boolean needsUpdate = !tableExists(conn, "services") ||
                    !tableExists(conn, "bureaux") ||
                    !tableExists(conn, "contraventions");

            if (needsUpdate) {
                logger.info("Mise à jour du schéma nécessaire, exécution...");
                createMissingTables();
                insertTestData();
                logger.info("Schéma mis à jour avec succès");
            } else {
                logger.debug("Schéma de base de données à jour");
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la mise à jour du schéma", e);
            throw new RuntimeException("Impossible de mettre à jour le schéma de base de données", e);
        }
    }
}