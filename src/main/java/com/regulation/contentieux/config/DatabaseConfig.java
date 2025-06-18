package com.regulation.contentieux.config;

import com.regulation.contentieux.util.DatabaseSchemaCompletion;
import com.regulation.contentieux.util.DatabaseSchemaUpdate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Properties;

/**
 * Configuration et gestion des connexions aux bases de données SQLite et MySQL
 * VERSION MISE À JOUR avec toutes les tables du cahier des charges
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    // Instances des pools de connexions
    private static HikariDataSource sqliteDataSource;
    private static HikariDataSource mysqlDataSource;

    // Configuration par défaut
    private static final String DEFAULT_SQLITE_PATH = "data/gestion_contentieux.db";
    private static final String CONFIG_FILE = "database.properties";

    // Properties de configuration
    private static Properties dbProperties;

    static {
        loadConfiguration();
    }

    /**
     * Charge la configuration depuis le fichier properties
     */
    private static void loadConfiguration() {
        dbProperties = new Properties();

        try (InputStream input = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (input != null) {
                dbProperties.load(input);
                logger.info("Configuration de base de données chargée depuis {}", CONFIG_FILE);
            } else {
                logger.warn("Fichier {} non trouvé, utilisation des valeurs par défaut", CONFIG_FILE);
                setDefaultProperties();
            }

        } catch (IOException e) {
            logger.error("Erreur lors du chargement de la configuration", e);
            setDefaultProperties();
        }
    }

    /**
     * Définit les propriétés par défaut
     */
    private static void setDefaultProperties() {
        // Configuration SQLite
        dbProperties.setProperty("sqlite.path", DEFAULT_SQLITE_PATH);
        dbProperties.setProperty("sqlite.poolSize", "10");

        // Configuration MySQL
        dbProperties.setProperty("mysql.host", "localhost");
        dbProperties.setProperty("mysql.port", "3306");
        dbProperties.setProperty("mysql.database", "contentieux");
        dbProperties.setProperty("mysql.username", "contentieux_user");
        dbProperties.setProperty("mysql.password", "contentieux_pass");
        dbProperties.setProperty("mysql.poolSize", "20");
        dbProperties.setProperty("mysql.connectionTimeout", "30000");
        dbProperties.setProperty("mysql.idleTimeout", "600000");
        dbProperties.setProperty("mysql.maxLifetime", "1800000");
    }

    /**
     * Initialise la base de données SQLite avec TOUTES les tables
     */
    public static void initializeSQLite() {
        try {
            String sqlitePath = dbProperties.getProperty("sqlite.path", DEFAULT_SQLITE_PATH);

            // Créer le dossier parent si nécessaire
            Path dbPath = Paths.get(sqlitePath);
            Path parentDir = dbPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("Dossier créé: {}", parentDir);
            }

            // Vérifier si la base de données existe
            boolean dbExists = Files.exists(dbPath);

            logger.info("Vérification de la base SQLite: {}", sqlitePath);
            logger.info("Base de données existante: {}", dbExists ? "OUI" : "NON");
            if (dbExists) {
                logger.info("Taille de la base: {} MB", Files.size(dbPath) / (1024 * 1024));
            }

            // Configuration du pool de connexions SQLite
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(Integer.parseInt(
                    dbProperties.getProperty("sqlite.poolSize", "10")));
            config.setPoolName("SQLitePool");

            // Optimisations SQLite
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("cache_size", "10000");
            config.addDataSourceProperty("temp_store", "MEMORY");
            config.addDataSourceProperty("foreign_keys", "ON");

            sqliteDataSource = new HikariDataSource(config);

            // Créer les tables si nécessaire et ajouter les données de test
            if (!dbExists) {
                logger.info("Base de données non trouvée, création complète...");
                createAllSQLiteTables();
                createInitialData();
                DatabaseSchemaCompletion.executeSchemaCompletion();

                logger.info("Base de données SQLite initialisée avec schéma complet : {}", sqlitePath);
                logger.info("Base de données complète créée avec succès");
            } else {
                logger.info("Base de données existante détectée");
                // Mettre à jour le schéma si nécessaire
                DatabaseSchemaUpdate.updateSchemaIfNeeded();

                // Vérifier la connexion et compter les enregistrements
                try (Connection conn = getSQLiteConnection()) {
                    logger.info("Connexion à la base existante: OK");
                    logTableCounts(conn);
                }
            }

            logger.info("Base de données SQLite initialisée : {}", sqlitePath);

        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation de la base SQLite", e);
            throw new RuntimeException("Impossible d'initialiser la base SQLite", e);
        }
    }

    /**
     * Crée TOUTES les tables SQLite selon le cahier des charges
     */
    private static void createAllSQLiteTables() {
        String[] createTableStatements = {
                // Table utilisateurs
                """
                CREATE TABLE IF NOT EXISTS utilisateurs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    nom_complet TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE')),
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    last_login_at DATETIME
                )
                """,

                // Table services
                """
                CREATE TABLE IF NOT EXISTS services (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_service TEXT NOT NULL UNIQUE,
                    nom_service TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table bureaux
                """
                CREATE TABLE IF NOT EXISTS bureaux (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_bureau TEXT NOT NULL UNIQUE,
                    nom_bureau TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table centres
                """
                CREATE TABLE IF NOT EXISTS centres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_centre TEXT NOT NULL UNIQUE,
                    nom_centre TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table banques
                """
                CREATE TABLE IF NOT EXISTS banques (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_banque TEXT NOT NULL UNIQUE,
                    nom_banque TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table contraventions
                """
                CREATE TABLE IF NOT EXISTS contraventions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    libelle TEXT NOT NULL,
                    description TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table contrevenants
                """
                CREATE TABLE IF NOT EXISTS contrevenants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    nom_complet TEXT NOT NULL,
                    adresse TEXT,
                    telephone TEXT,
                    email TEXT,
                    type_personne TEXT CHECK(type_personne IN ('PHYSIQUE', 'MORALE')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table agents
                """
                CREATE TABLE IF NOT EXISTS agents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_agent TEXT NOT NULL UNIQUE,
                    nom TEXT NOT NULL,
                    prenom TEXT NOT NULL,
                    grade TEXT,
                    service_id INTEGER,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (service_id) REFERENCES services (id)
                )
                """,

                // Table affaires
                """
                CREATE TABLE IF NOT EXISTS affaires (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    numero_affaire TEXT NOT NULL UNIQUE,
                    date_creation DATE NOT NULL,
                    montant_amende_total REAL NOT NULL,
                    statut TEXT DEFAULT 'OUVERTE',
                    contrevenant_id INTEGER NOT NULL,
                    contravention_id INTEGER NOT NULL,
                    bureau_id INTEGER,
                    service_id INTEGER,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT,
                    updated_by TEXT,
                    FOREIGN KEY (contrevenant_id) REFERENCES contrevenants (id),
                    FOREIGN KEY (contravention_id) REFERENCES contraventions (id),
                    FOREIGN KEY (bureau_id) REFERENCES bureaux(id),
                    FOREIGN KEY (service_id) REFERENCES services(id)
                )
                """,

                // Table encaissements
                """
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
                """,

                // Table affaire_acteurs (liaison affaire-agent)
                """
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
                """,

                // Table repartition_resultats
                """
                CREATE TABLE IF NOT EXISTS repartition_resultats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    encaissement_id INTEGER NOT NULL,
                    destinataire TEXT NOT NULL,
                    pourcentage REAL NOT NULL,
                    montant_calcule REAL NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (encaissement_id) REFERENCES encaissements (id)
                )
                """,

                // Table roles_speciaux
                """
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
                """
        };

        try (Connection conn = getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : createTableStatements) {
                stmt.execute(sql);
            }

            logger.info("Toutes les tables SQLite créées avec succès");

        } catch (SQLException e) {
            logger.error("Erreur lors de la création des tables SQLite", e);
            throw new RuntimeException("Impossible de créer les tables SQLite", e);
        }
    }

    /**
     * Crée les données initiales COMPLÈTES
     */
    public static void createInitialData() {
        try (Connection conn = getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            // Vérifier si des données existent déjà
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM utilisateurs");
            if (rs.next() && rs.getInt(1) > 0) {
                logger.info("Données existantes détectées, pas d'initialisation");
                return;
            }

            conn.setAutoCommit(false);

            try {
                // 1. Utilisateur admin par défaut
                stmt.execute("""
                    INSERT OR IGNORE INTO utilisateurs (username, password_hash, nom_complet, role) 
                    VALUES ('admin', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'Administrateur Système', 'SUPER_ADMIN')
                """);

                // 2. Services
                stmt.execute("""
                    INSERT INTO services (code_service, nom_service) VALUES
                    ('SRV01', 'Service Fiscal'),
                    ('SRV02', 'Service Douanier'),
                    ('SRV03', 'Service Commercial'),
                    ('SRV04', 'Service Administratif')
                """);

                // 3. Bureaux
                stmt.execute("""
                    INSERT INTO bureaux (code_bureau, nom_bureau) VALUES
                    ('BUR01', 'Bureau Central'),
                    ('BUR02', 'Bureau Nord'),
                    ('BUR03', 'Bureau Sud'),
                    ('BUR04', 'Bureau Est')
                """);

                // 4. Centres
                stmt.execute("""
                    INSERT INTO centres (code_centre, nom_centre) VALUES
                    ('CTR01', 'Centre Principal'),
                    ('CTR02', 'Centre Secondaire'),
                    ('CTR03', 'Centre Régional')
                """);

                // 5. Banques
                stmt.execute("""
                    INSERT INTO banques (code_banque, nom_banque) VALUES
                    ('BQ001', 'Banque Centrale du Gabon'),
                    ('BQ002', 'BGFI Bank'),
                    ('BQ003', 'Banque Populaire du Gabon'),
                    ('BQ004', 'Orabank Gabon')
                """);

                // 6. Contraventions
                stmt.execute("""
                    INSERT INTO contraventions (code, libelle, description) VALUES
                    ('CV001', 'Défaut de déclaration fiscale', 'Non déclaration dans les délais légaux'),
                    ('CV002', 'Contrebande', 'Importation illégale de marchandises'),
                    ('CV003', 'Défaut de licence commerciale', 'Exercice d''activité sans autorisation'),
                    ('CV004', 'Non-conformité réglementaire', 'Non respect des normes en vigueur'),
                    ('CV005', 'Fraude documentaire', 'Falsification de documents officiels')
                """);

                // 7. Contrevenants de test
                stmt.execute("""
                    INSERT INTO contrevenants (code, nom_complet, type_personne, telephone, email, adresse) VALUES
                    ('CV00001', 'MARTIN Jean-Pierre', 'PHYSIQUE', '+241 01 23 45 67', 'martin.jp@email.com', 'Quartier Batterie IV, Libreville'),
                    ('CV00002', 'NGUEMA Marie-Claire', 'PHYSIQUE', '+241 02 34 56 78', 'nguema.mc@email.com', 'Boulevard Triomphal, Port-Gentil'),
                    ('CV00003', 'SOCIÉTÉ GABONAISE SARL', 'MORALE', '+241 03 45 67 89', 'contact@societe-gab.ga', 'Zone Industrielle Oloumi'),
                    ('CV00004', 'ENTREPRISE COMMERCE SA', 'MORALE', '+241 04 56 78 90', 'info@entreprise.com', 'Centre-ville Libreville'),
                    ('CV00005', 'OBAME Paul-Henri', 'PHYSIQUE', '+241 05 67 89 01', 'obame.ph@email.com', 'Quartier Nombakele, Libreville')
                """);

                // 8. Agents de test
                stmt.execute("""
                    INSERT INTO agents (code_agent, nom, prenom, grade, service_id, actif) VALUES
                    ('AG00001', 'AKOMO', 'Pierre', 'Inspecteur Principal', 1, 1),
                    ('AG00002', 'BENGONE', 'Marie', 'Inspecteur', 1, 1),
                    ('AG00003', 'MENDAME', 'Joseph', 'Contrôleur Principal', 2, 1),
                    ('AG00004', 'NZOGHE', 'Anne', 'Contrôleur', 2, 1),
                    ('AG00005', 'ONDO', 'François', 'Agent Principal', 3, 1)
                """);

                // 9. Affaires de test (plus réalistes)
                stmt.execute("""
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

                // 10. Affaire-Acteurs (assignation des agents)
                stmt.execute("""
                    INSERT INTO affaire_acteurs (affaire_id, agent_id, role_sur_affaire, assigned_by) VALUES
                    (1, 1, 'CHEF', 'admin'),
                    (1, 2, 'SAISISSANT', 'admin'),
                    (2, 3, 'CHEF', 'admin'),
                    (3, 1, 'CHEF', 'admin'),
                    (4, 4, 'CHEF', 'admin'),
                    (5, 5, 'SAISISSANT', 'admin')
                """);

                // 11. Encaissements de test
                stmt.execute("""
                    INSERT INTO encaissements (affaire_id, montant_encaisse, date_encaissement, 
                                             mode_reglement, reference, banque_id, statut, created_by) VALUES
                    (4, 750000, '2024-12-18', 'VIREMENT', 'VIR-2024-001', 1, 'VALIDE', 'admin'),
                    (2, 600000, '2024-12-22', 'CHEQUE', 'CHQ-123456', 2, 'VALIDE', 'admin'),
                    (1, 250000, '2025-01-08', 'ESPECES', 'ESP-001', NULL, 'VALIDE', 'admin'),
                    (5, 900000, '2025-01-12', 'MANDAT', 'MAN-789012', 3, 'EN_ATTENTE', 'admin')
                """);

                // 12. Répartitions de résultats (exemples)
                stmt.execute("""
                    INSERT INTO repartition_resultats (encaissement_id, destinataire, pourcentage, montant_calcule) VALUES
                    (1, 'État', 60.0, 450000),
                    (1, 'Collectivité Locale', 25.0, 187500),
                    (1, 'Service Contrôle', 15.0, 112500),
                    (2, 'État', 60.0, 360000),
                    (2, 'Collectivité Locale', 25.0, 150000),
                    (2, 'Service Contrôle', 15.0, 90000)
                """);

                conn.commit();
                logger.info("Données initiales complètes créées avec succès");

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la création des données initiales", e);
        }
    }

    /**
     * Log du nombre d'enregistrements par table
     */
    private static void logTableCounts(Connection conn) {
        String[] tables = {
                "utilisateurs", "services", "bureaux", "centres", "banques",
                "contraventions", "contrevenants", "agents", "affaires",
                "encaissements", "affaire_acteurs", "repartition_resultats"
        };

        for (String table : tables) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    int count = rs.getInt(1);
                    logger.info("Table {}: {} enregistrements", table, count);
                }
            } catch (SQLException e) {
                logger.debug("Table {} non accessible: {}", table, e.getMessage());
            }
        }
    }

    /**
     * Initialise la connexion à la base de données MySQL
     */
    public static void initializeMySQLDatabase() {
        try {
            String host = dbProperties.getProperty("mysql.host", "localhost");
            String port = dbProperties.getProperty("mysql.port", "3306");
            String database = dbProperties.getProperty("mysql.database", "contentieux");
            String username = dbProperties.getProperty("mysql.username", "contentieux_user");
            String password = dbProperties.getProperty("mysql.password", "contentieux_pass");

            // Configuration du pool de connexions MySQL
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s", host, port, database));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setUsername(username);
            config.setPassword(password);

            // Configuration du pool
            config.setMaximumPoolSize(Integer.parseInt(
                    dbProperties.getProperty("mysql.poolSize", "20")));
            config.setMinimumIdle(5);
            config.setConnectionTimeout(Long.parseLong(
                    dbProperties.getProperty("mysql.connectionTimeout", "30000")));
            config.setIdleTimeout(Long.parseLong(
                    dbProperties.getProperty("mysql.idleTimeout", "600000")));
            config.setMaxLifetime(Long.parseLong(
                    dbProperties.getProperty("mysql.maxLifetime", "1800000")));
            config.setPoolName("MySQLPool");

            // Propriétés MySQL
            config.addDataSourceProperty("useSSL", "false");
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("useUnicode", "true");
            config.addDataSourceProperty("characterEncoding", "UTF-8");
            config.addDataSourceProperty("serverTimezone", "UTC");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            mysqlDataSource = new HikariDataSource(config);

            logger.info("Connexion MySQL initialisée : {}:{}/{}", host, port, database);

        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation de MySQL", e);
            logger.warn("L'application fonctionnera en mode SQLite uniquement");
        }
    }

    /**
     * Obtient une connexion à la base SQLite
     */
    public static Connection getSQLiteConnection() throws SQLException {
        if (sqliteDataSource == null) {
            initializeSQLite();
        }
        return sqliteDataSource.getConnection();
    }

    /**
     * Obtient une connexion à la base MySQL
     */
    public static Connection getMySQLConnection() throws SQLException {
        if (mysqlDataSource == null) {
            initializeMySQLDatabase();
        }
        if (mysqlDataSource == null) {
            throw new SQLException("MySQL non disponible");
        }
        return mysqlDataSource.getConnection();
    }

    /**
     * Teste la connexion MySQL
     */
    public static boolean testMySQLConnection() {
        try {
            if (mysqlDataSource == null) {
                initializeMySQLDatabase();
            }
            if (mysqlDataSource != null) {
                try (Connection conn = mysqlDataSource.getConnection()) {
                    return conn.isValid(5);
                }
            }
            return false;
        } catch (Exception e) {
            logger.debug("Test de connexion MySQL échoué", e);
            return false;
        }
    }

    /**
     * Ferme toutes les connexions
     */
    public static void closeAllConnections() {
        try {
            if (sqliteDataSource != null && !sqliteDataSource.isClosed()) {
                sqliteDataSource.close();
                logger.info("Pool SQLite fermé");
            }

            if (mysqlDataSource != null && !mysqlDataSource.isClosed()) {
                mysqlDataSource.close();
                logger.info("Pool MySQL fermé");
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la fermeture des connexions", e);
        }
    }

    /**
     * Vérifie si MySQL est disponible
     */
    public static boolean isMySQLAvailable() {
        return mysqlDataSource != null && !mysqlDataSource.isClosed();
    }
}