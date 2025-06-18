package com.regulation.contentieux.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Configuration et gestion des connexions aux bases de données SQLite et MySQL
 */
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    // Instances des pools de connexions
    private static HikariDataSource sqliteDataSource;
    private static HikariDataSource mysqlDataSource;

    // Configuration par défaut
    private static final String DEFAULT_SQLITE_PATH = "gestion_contentieux.db"; // Utilise votre base existante
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
        // Configuration SQLite - utilise votre base existante
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
     * Initialise la base de données SQLite
     */
    public static void initializeSQLiteDatabase() {
        try {
            String sqlitePath = dbProperties.getProperty("sqlite.path", DEFAULT_SQLITE_PATH);

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

            // Création des tables si nécessaire
            createSQLiteTables();

            logger.info("Base de données SQLite initialisée : {}", sqlitePath);

        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation de la base SQLite", e);
            throw new RuntimeException("Impossible d'initialiser la base SQLite", e);
        }
    }

    /**
     * Alias pour compatibilité
     */
    public static void initializeSQLite() {
        initializeSQLiteDatabase();
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
     * Crée les tables SQLite si elles n'existent pas
     */
    private static void createSQLiteTables() {
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
                """
        };

        try (Connection conn = getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : createTableStatements) {
                stmt.execute(sql);
            }

            logger.info("Tables SQLite créées avec succès");

        } catch (SQLException e) {
            logger.error("Erreur lors de la création des tables SQLite", e);
            throw new RuntimeException("Impossible de créer les tables SQLite", e);
        }
    }

    /**
     * Crée les données initiales
     */
    public static void createInitialData() {
        try (Connection conn = getSQLiteConnection();
             Statement stmt = conn.createStatement()) {

            // Utilisateur admin par défaut (mot de passe: "admin")
            stmt.execute("""
                INSERT OR IGNORE INTO utilisateurs (username, password_hash, nom_complet, role) 
                VALUES ('admin', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f', 'Administrateur Système', 'SUPER_ADMIN')
            """);

            // Données de test pour les services
            stmt.execute("""
                INSERT OR IGNORE INTO services (code_service, nom_service) 
                VALUES 
                ('SRV001', 'Service Fiscal'),
                ('SRV002', 'Service Douanier'),
                ('SRV003', 'Service Commercial')
            """);

            // Données de test pour les bureaux
            stmt.execute("""
                INSERT OR IGNORE INTO bureaux (code_bureau, nom_bureau) 
                VALUES 
                ('BUR001', 'Bureau Central'),
                ('BUR002', 'Bureau Nord'),
                ('BUR003', 'Bureau Sud')
            """);

            // Données de test pour les contraventions
            stmt.execute("""
                INSERT OR IGNORE INTO contraventions (code, libelle, description) 
                VALUES 
                ('CV001', 'Défaut de déclaration fiscale', 'Non-respect des obligations déclaratives'),
                ('CV002', 'Contrebande', 'Importation illégale de marchandises'),
                ('CV003', 'Pratique commerciale déloyale', 'Concurrence déloyale')
            """);

            // Données de test pour les contrevenants
            stmt.execute("""
                INSERT OR IGNORE INTO contrevenants (code, nom_complet, type_personne, adresse, telephone) 
                VALUES 
                ('CTR001', 'MARTIN Jean', 'PHYSIQUE', '123 Rue de la Paix, Pointe-Noire', '+242 06 123 45 67'),
                ('CTR002', 'SOCIETE ABC SARL', 'MORALE', '456 Avenue de l''Indépendance, Pointe-Noire', '+242 05 987 65 43'),
                ('CTR003', 'DUPONT Marie', 'PHYSIQUE', '789 Boulevard des Congolais, Pointe-Noire', '+242 06 555 44 33')
            """);

            // Données de test pour les affaires (avec nouveau format YYMMNNNN)
            stmt.execute("""
                INSERT OR IGNORE INTO affaires (numero_affaire, date_creation, montant_amende_total, statut, contrevenant_id, contravention_id, bureau_id, service_id, created_by) 
                VALUES 
                ('24120001', '2024-12-01', 500000.0, 'EN_COURS', 1, 1, 1, 1, 'admin'),
                ('24120002', '2024-12-05', 1200000.0, 'OUVERTE', 2, 2, 2, 2, 'admin'),
                ('24120003', '2024-12-10', 750000.0, 'CLOTUREE', 3, 3, 1, 3, 'admin'),
                ('24120004', '2024-12-15', 300000.0, 'EN_COURS', 1, 1, 3, 1, 'admin'),
                ('24120005', '2024-12-20', 2000000.0, 'SUSPENDUE', 2, 2, 2, 2, 'admin')
            """);

            logger.info("Données initiales créées avec succès");

        } catch (SQLException e) {
            logger.error("Erreur lors de la création des données initiales", e);
        }
    }

    /**
     * Obtient une connexion à la base SQLite
     */
    public static Connection getSQLiteConnection() throws SQLException {
        if (sqliteDataSource == null) {
            initializeSQLiteDatabase();
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

    /**
     * Obtient des statistiques sur les pools de connexions
     */
    public static String getConnectionPoolStats() {
        StringBuilder stats = new StringBuilder();

        if (sqliteDataSource != null) {
            stats.append("SQLite Pool - ");
            stats.append("Active: ").append(sqliteDataSource.getHikariPoolMXBean().getActiveConnections());
            stats.append(", Idle: ").append(sqliteDataSource.getHikariPoolMXBean().getIdleConnections());
            stats.append(", Total: ").append(sqliteDataSource.getHikariPoolMXBean().getTotalConnections());
            stats.append("\n");
        }

        if (mysqlDataSource != null) {
            stats.append("MySQL Pool - ");
            stats.append("Active: ").append(mysqlDataSource.getHikariPoolMXBean().getActiveConnections());
            stats.append(", Idle: ").append(mysqlDataSource.getHikariPoolMXBean().getIdleConnections());
            stats.append(", Total: ").append(mysqlDataSource.getHikariPoolMXBean().getTotalConnections());
        }

        return stats.toString();
    }
}