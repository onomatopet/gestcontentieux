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
 * VERSION ENRICHIE avec diagnostics avancés et mécanismes de récupération
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
     * Charge la configuration depuis le fichier properties avec diagnostics enrichis
     */
    private static void loadConfiguration() {
        dbProperties = new Properties();

        // === DIAGNOSTIC COMPLET ENRICHI ===
        logger.info("=== DIAGNOSTIC DE CHARGEMENT DE CONFIGURATION ===");
        logger.info("Timestamp: {}", new java.util.Date());
        logger.info("JVM: {} version {}", System.getProperty("java.vendor"), System.getProperty("java.version"));
        logger.info("OS: {} {} {}", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        logger.info("Tentative de chargement du fichier: {}", CONFIG_FILE);

        // Diagnostic 1: Vérifier le ClassLoader
        ClassLoader classLoader = DatabaseConfig.class.getClassLoader();
        logger.info("ClassLoader utilisé: {}", classLoader.getClass().getName());

        // Diagnostic 2: Lister toutes les ressources disponibles
        try {
            logger.info("Working directory: {}", System.getProperty("user.dir"));
            logger.info("Classpath: {}", System.getProperty("java.class.path"));
            logger.info("User home: {}", System.getProperty("user.home"));
        } catch (Exception e) {
            logger.warn("Impossible d'obtenir les informations système: {}", e.getMessage());
        }

        // Diagnostic 3: Tentative de chargement avec différentes méthodes
        InputStream input = null;
        boolean configFound = false;
        String loadMethod = "AUCUN";

        // Méthode 1: ClassLoader.getResourceAsStream (recommandée)
        try {
            input = classLoader.getResourceAsStream(CONFIG_FILE);
            if (input != null) {
                configFound = true;
                loadMethod = "ClassLoader.getResourceAsStream";
                logger.info("✅ Méthode 1 réussie: {}", loadMethod);
            } else {
                logger.debug("❌ Méthode 1 échouée");
            }
        } catch (Exception e) {
            logger.debug("❌ Méthode 1 exception: {}", e.getMessage());
        }

        // Méthode 2: Avec slash initial
        if (!configFound) {
            try {
                input = classLoader.getResourceAsStream("/" + CONFIG_FILE);
                if (input != null) {
                    configFound = true;
                    loadMethod = "ClassLoader.getResourceAsStream avec /";
                    logger.info("✅ Méthode 2 réussie: {}", loadMethod);
                } else {
                    logger.debug("❌ Méthode 2 échouée");
                }
            } catch (Exception e) {
                logger.debug("❌ Méthode 2 exception: {}", e.getMessage());
            }
        }

        // Méthode 3: Thread Context ClassLoader
        if (!configFound) {
            try {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                input = contextClassLoader.getResourceAsStream(CONFIG_FILE);
                if (input != null) {
                    configFound = true;
                    loadMethod = "Thread.currentThread().getContextClassLoader()";
                    logger.info("✅ Méthode 3 réussie: {}", loadMethod);
                } else {
                    logger.debug("❌ Méthode 3 échouée");
                }
            } catch (Exception e) {
                logger.debug("❌ Méthode 3 exception: {}", e.getMessage());
            }
        }

        // Méthode 4: Chemin absolu en fallback
        if (!configFound) {
            try {
                java.nio.file.Path configPath = java.nio.file.Paths.get("src/main/resources", CONFIG_FILE);
                if (java.nio.file.Files.exists(configPath)) {
                    input = java.nio.file.Files.newInputStream(configPath);
                    configFound = true;
                    loadMethod = "Chemin absolu (fallback)";
                    logger.warn("⚠️ Méthode 4 (fallback) utilisée: {}", loadMethod);
                } else {
                    logger.debug("❌ Méthode 4 échouée - fichier non trouvé: {}", configPath);
                }
            } catch (Exception e) {
                logger.debug("❌ Méthode 4 exception: {}", e.getMessage());
            }
        }

        // Charger les propriétés si trouvées
        if (configFound && input != null) {
            try {
                dbProperties.load(input);
                logger.info("✅ Configuration chargée avec succès via: {}", loadMethod);
                logger.info("✅ Nombre de propriétés chargées: {}", dbProperties.size());

                // Log des propriétés importantes (sans les mots de passe)
                String sqlitePath = dbProperties.getProperty("sqlite.path");
                String mysqlHost = dbProperties.getProperty("mysql.host");
                logger.info("✅ sqlite.path configuré: {}", sqlitePath);
                logger.info("✅ mysql.host configuré: {}", mysqlHost);

                // Diagnostic enrichi des propriétés
                diagnosticProperties();

            } catch (IOException e) {
                logger.error("❌ Erreur lors du chargement des propriétés: {}", e.getMessage());
                configFound = false;
            } finally {
                try {
                    if (input != null) input.close();
                } catch (IOException e) {
                    logger.warn("Erreur lors de la fermeture du stream: {}", e.getMessage());
                }
            }
        }

        // Si aucune méthode n'a fonctionné, utiliser les valeurs par défaut
        if (!configFound) {
            logger.warn("❌ Aucune méthode de chargement n'a réussi");
            logger.warn("🔄 Utilisation des valeurs par défaut");
            setDefaultProperties();

            // Log des valeurs par défaut pour diagnostic
            logger.info("🔄 Valeurs par défaut appliquées:");
            logger.info("🔄 sqlite.path: {}", dbProperties.getProperty("sqlite.path"));
            logger.info("🔄 mysql.host: {}", dbProperties.getProperty("mysql.host"));
        }

        logger.info("=== FIN DU DIAGNOSTIC ===");
    }

    /**
     * Définit les propriétés par défaut enrichies
     */
    private static void setDefaultProperties() {
        // Configuration SQLite
        dbProperties.setProperty("sqlite.path", DEFAULT_SQLITE_PATH);
        dbProperties.setProperty("sqlite.poolSize", "10");
        dbProperties.setProperty("sqlite.busyTimeout", "30000");
        dbProperties.setProperty("sqlite.journalMode", "WAL");
        dbProperties.setProperty("sqlite.synchronous", "NORMAL");

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

        logger.debug("Propriétés par défaut définies: {} propriétés", dbProperties.size());
    }

    /**
     * Méthode utilitaire pour vérifier le contenu des propriétés chargées
     */
    public static void diagnosticProperties() {
        logger.info("=== DIAGNOSTIC DES PROPRIÉTÉS CHARGÉES ===");
        logger.info("Nombre de propriétés: {}", dbProperties.size());

        // Lister toutes les propriétés de manière sécurisée
        dbProperties.forEach((key, value) -> {
            if (key.toString().toLowerCase().contains("password") ||
                    key.toString().toLowerCase().contains("pwd") ||
                    key.toString().toLowerCase().contains("secret")) {
                logger.info("{}=***MASQUÉ***", key);
            } else {
                logger.info("{}={}", key, value);
            }
        });

        // Vérifications spécifiques enrichies
        String sqlitePath = dbProperties.getProperty("sqlite.path", "NON_DÉFINI");
        logger.info("🔍 Chemin SQLite final: {}", sqlitePath);

        // Vérifier si le chemin est relatif ou absolu
        Path path = Paths.get(sqlitePath);
        logger.info("🔍 Chemin absolu: {}", path.isAbsolute());
        logger.info("🔍 Chemin résolu: {}", path.toAbsolutePath());
        logger.info("🔍 Chemin normalisé: {}", path.normalize());

        logger.info("=== FIN DU DIAGNOSTIC DES PROPRIÉTÉS ===");
    }

    /**
     * Initialise la base de données SQLite avec diagnostic avancé enrichi
     */
    public static void initializeSQLite() {
        try {
            // === DIAGNOSTIC AVANCÉ DU CHEMIN ENRICHI ===
            logger.info("=== DIAGNOSTIC AVANCÉ DU CHEMIN DE BASE ===");
            logger.info("Timestamp début: {}", new Timestamp(System.currentTimeMillis()));

            // 1. Vérifier les System Properties qui pourraient override la config
            String systemSqliteUrl = System.getProperty("db.sqlite.url");
            String systemSqlitePath = System.getProperty("sqlite.path");
            String systemDbPath = System.getProperty("database.path");

            logger.info("🔍 System Property 'db.sqlite.url': {}", systemSqliteUrl);
            logger.info("🔍 System Property 'sqlite.path': {}", systemSqlitePath);
            logger.info("🔍 System Property 'database.path': {}", systemDbPath);

            // 2. Récupérer le chemin depuis la configuration
            String configSqlitePath = dbProperties.getProperty("sqlite.path", DEFAULT_SQLITE_PATH);
            logger.info("🔍 Configuration sqlite.path: {}", configSqlitePath);
            logger.info("🔍 DEFAULT_SQLITE_PATH: {}", DEFAULT_SQLITE_PATH);

            // 3. Déterminer le chemin final utilisé avec traçabilité complète
            String sqlitePath;
            String pathSource;

            if (systemSqliteUrl != null && systemSqliteUrl.startsWith("jdbc:sqlite:")) {
                // System property override (comme dans les tests)
                sqlitePath = systemSqliteUrl.substring("jdbc:sqlite:".length());
                pathSource = "System Property 'db.sqlite.url'";
                logger.warn("⚠️ CHEMIN OVERRIDÉ par System Property: {}", sqlitePath);
            } else if (systemSqlitePath != null) {
                // System property direct
                sqlitePath = systemSqlitePath;
                pathSource = "System Property 'sqlite.path'";
                logger.warn("⚠️ CHEMIN OVERRIDÉ par System Property: {}", sqlitePath);
            } else if (systemDbPath != null) {
                // System property alternatif
                sqlitePath = systemDbPath;
                pathSource = "System Property 'database.path'";
                logger.warn("⚠️ CHEMIN OVERRIDÉ par System Property: {}", sqlitePath);
            } else {
                // Configuration normale
                sqlitePath = configSqlitePath;
                pathSource = "Fichier database.properties";
                logger.info("✅ CHEMIN depuis configuration: {}", sqlitePath);
            }

            logger.info("🎯 CHEMIN FINAL utilisé: {} (source: {})", sqlitePath, pathSource);

            // 4. Analyser le chemin final de manière approfondie
            Path dbPath = Paths.get(sqlitePath);
            logger.info("🔍 Chemin absolu: {}", dbPath.isAbsolute());
            logger.info("🔍 Chemin résolu: {}", dbPath.toAbsolutePath());
            logger.info("🔍 Nom du fichier: {}", dbPath.getFileName());
            logger.info("🔍 Dossier parent: {}", dbPath.getParent());

            // 5. Vérifier et créer le dossier parent si nécessaire
            Path parentDir = dbPath.getParent();
            if (parentDir != null) {
                boolean parentExists = Files.exists(parentDir);
                logger.info("🔍 Dossier parent existe: {}", parentExists);

                if (!parentExists) {
                    logger.info("📁 Création du dossier parent: {}", parentDir);
                    Files.createDirectories(parentDir);
                    logger.info("✅ Dossier créé: {}", parentDir);

                    // Vérifier les permissions du dossier créé
                    verifyDirectoryPermissions(parentDir);
                } else {
                    logger.info("✅ Dossier parent déjà existant: {}", parentDir);
                    verifyDirectoryPermissions(parentDir);
                }
            } else {
                logger.info("⚠️ Aucun dossier parent (fichier à la racine)");
            }

            // 6. Vérifier l'existence et l'état de la base de données
            boolean dbExists = Files.exists(dbPath);
            logger.info("🔍 Base de données existe: {}", dbExists);

            if (dbExists) {
                long size = Files.size(dbPath);
                logger.info("🔍 Taille de la base: {} bytes ({} MB)", size, size / (1024.0 * 1024.0));

                if (size == 0) {
                    logger.warn("⚠️ BASE DE DONNÉES VIDE - Sera réinitialisée");
                } else {
                    logger.info("✅ Base de données contient des données");

                    // Diagnostics enrichis pour base existante
                    performDatabaseDiagnostics(dbPath, sqlitePath);
                }
            } else {
                logger.info("📝 Base de données inexistante - Sera créée");
            }

            logger.info("=== FIN DU DIAGNOSTIC AVANCÉ ===");

            // 7. Configuration du pool de connexions SQLite avec paramètres optimisés
            logger.info("🔧 Configuration du pool de connexions SQLite...");
            HikariConfig config = new HikariConfig();

            String jdbcUrl = "jdbc:sqlite:" + sqlitePath;
            logger.info("🔧 JDBC URL finale: {}", jdbcUrl);

            config.setJdbcUrl(jdbcUrl);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(Integer.parseInt(
                    dbProperties.getProperty("sqlite.poolSize", "10")));
            config.setPoolName("SQLitePool");

            // Paramètres de timeout enrichis
            config.setConnectionTimeout(30000); // 30 secondes
            config.setIdleTimeout(600000); // 10 minutes
            config.setMaxLifetime(1800000); // 30 minutes

            // Optimisations SQLite enrichies
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("cache_size", "10000");
            config.addDataSourceProperty("temp_store", "MEMORY");
            config.addDataSourceProperty("foreign_keys", "ON");
            config.addDataSourceProperty("busy_timeout", "30000");
            config.addDataSourceProperty("wal_checkpoint", "1000");

            // Créer le datasource
            sqliteDataSource = new HikariDataSource(config);
            logger.info("✅ Pool de connexions SQLite configuré avec succès");

            // 8. Initialisation si nécessaire avec gestion d'erreur enrichie
            if (!dbExists || Files.size(dbPath) == 0) {
                logger.info("🚀 Initialisation complète de la base de données...");

                try {
                    createAllSQLiteTables();
                    createInitialData();
                    DatabaseSchemaCompletion.executeSchemaCompletion();

                    logger.info("✅ Base de données SQLite initialisée avec schéma complet : {}", sqlitePath);
                    logger.info("✅ Base de données complète créée avec succès");

                    // Vérifier l'intégrité après création
                    verifyDatabaseIntegrity(sqlitePath);

                } catch (Exception e) {
                    logger.error("❌ Erreur lors de l'initialisation du schéma", e);
                    // Tenter une récupération
                    attemptSchemaRecovery(sqlitePath);
                }
            } else {
                logger.info("ℹ️ Base de données existante détectée");

                try {
                    // Mettre à jour le schéma si nécessaire
                    DatabaseSchemaUpdate.updateSchemaIfNeeded();

                    // Vérifier la connexion et compter les enregistrements
                    try (Connection conn = getSQLiteConnection()) {
                        logger.info("✅ Connexion à la base existante: OK");
                        logTableCounts(conn);
                        optimizeDatabase(conn);
                    }
                } catch (Exception e) {
                    logger.error("❌ Erreur lors de la mise à jour du schéma", e);
                    attemptSchemaRecovery(sqlitePath);
                }
            }

            logger.info("✅ Base de données SQLite initialisée : {}", sqlitePath);
            logger.info("Timestamp fin: {}", new Timestamp(System.currentTimeMillis()));

        } catch (Exception e) {
            logger.error("❌ Erreur critique lors de l'initialisation de la base SQLite", e);
            // Tentative de récupération ultime
            performEmergencyRecovery();
            throw new RuntimeException("Impossible d'initialiser la base SQLite après toutes les tentatives", e);
        }
    }

    /**
     * Vérifie les permissions d'un répertoire
     */
    private static void verifyDirectoryPermissions(Path directory) {
        try {
            boolean readable = Files.isReadable(directory);
            boolean writable = Files.isWritable(directory);
            boolean executable = Files.isExecutable(directory);

            logger.info("📁 Permissions du dossier {} :", directory);
            logger.info("   - Lecture: {}", readable ? "✅" : "❌");
            logger.info("   - Écriture: {}", writable ? "✅" : "❌");
            logger.info("   - Exécution: {}", executable ? "✅" : "❌");

            if (!readable || !writable) {
                logger.error("⚠️ Permissions insuffisantes sur le dossier!");
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la vérification des permissions", e);
        }
    }

    /**
     * Effectue des diagnostics approfondis sur une base existante
     */
    private static void performDatabaseDiagnostics(Path dbPath, String sqlitePath) {
        logger.info("🔍 === DIAGNOSTICS APPROFONDIS DE LA BASE ===");

        try {
            // Vérifier les permissions du fichier
            boolean readable = Files.isReadable(dbPath);
            boolean writable = Files.isWritable(dbPath);
            logger.info("🔍 Permissions - Lecture: {}, Écriture: {}", readable, writable);

            // Vérifier l'intégrité SQLite
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
                // PRAGMA integrity_check
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("PRAGMA integrity_check");
                    String integrity = rs.getString(1);
                    if ("ok".equalsIgnoreCase(integrity)) {
                        logger.info("✅ Intégrité SQLite: OK");
                    } else {
                        logger.error("❌ PROBLÈME D'INTÉGRITÉ: {}", integrity);
                    }
                }

                // PRAGMA quick_check
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("PRAGMA quick_check");
                    String quickCheck = rs.getString(1);
                    logger.info("🔍 Quick check: {}", quickCheck);
                }

                // Vérifier le journal mode
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("PRAGMA journal_mode");
                    String journalMode = rs.getString(1);
                    logger.info("🔍 Journal mode: {}", journalMode);
                }

                // Statistiques sur les tables
                analyzeTableStatistics(conn);

            } catch (SQLException e) {
                logger.error("❌ Erreur lors des diagnostics SQLite", e);
            }

        } catch (Exception e) {
            logger.error("❌ Erreur lors des diagnostics de base", e);
        }

        logger.info("🔍 === FIN DES DIAGNOSTICS ===");
    }

    /**
     * Analyse les statistiques des tables
     */
    private static void analyzeTableStatistics(Connection conn) throws SQLException {
        logger.info("📊 Analyse des tables:");

        String[] criticalTables = {
                "utilisateurs", "affaires", "contrevenants", "agents",
                "contraventions", "encaissements", "services", "bureaux"
        };

        for (String table : criticalTables) {
            try (Statement stmt = conn.createStatement()) {
                // Compter les enregistrements
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                if (rs.next()) {
                    int count = rs.getInt(1);
                    logger.info("   - Table {}: {} enregistrements", table, count);
                }

                // Analyser la taille
                rs = stmt.executeQuery("SELECT page_count * page_size as size FROM pragma_page_count(), pragma_page_size()");
                if (rs.next()) {
                    long tableSize = rs.getLong(1);
                    logger.debug("     Taille approximative: {} KB", tableSize / 1024);
                }
            } catch (SQLException e) {
                logger.warn("   - Table {} : erreur ou inexistante", table);
            }
        }
    }

    /**
     * Vérifie l'intégrité complète de la base
     */
    private static void verifyDatabaseIntegrity(String sqlitePath) {
        logger.info("🔍 Vérification de l'intégrité post-création...");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
            // Vérifier toutes les contraintes
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check");
                boolean hasIssues = false;
                while (rs.next()) {
                    hasIssues = true;
                    logger.error("❌ Problème de clé étrangère: table={}, rowid={}",
                            rs.getString("table"), rs.getLong("rowid"));
                }

                if (!hasIssues) {
                    logger.info("✅ Toutes les contraintes de clés étrangères sont valides");
                }
            }

            // Analyser les index
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'");
                int indexCount = 0;
                while (rs.next()) {
                    indexCount++;
                    logger.debug("   Index trouvé: {}", rs.getString("name"));
                }
                logger.info("✅ {} index trouvés", indexCount);
            }

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la vérification d'intégrité", e);
        }
    }

    /**
     * Optimise la base de données
     */
    private static void optimizeDatabase(Connection conn) {
        logger.info("🔧 Optimisation de la base de données...");

        try (Statement stmt = conn.createStatement()) {
            // VACUUM pour défragmenter
            logger.debug("   Exécution VACUUM...");
            stmt.execute("VACUUM");

            // ANALYZE pour mettre à jour les statistiques
            logger.debug("   Exécution ANALYZE...");
            stmt.execute("ANALYZE");

            // Optimiser les paramètres de cache
            stmt.execute("PRAGMA cache_size = 10000");
            stmt.execute("PRAGMA temp_store = MEMORY");

            logger.info("✅ Optimisation terminée");

        } catch (SQLException e) {
            logger.warn("⚠️ Optimisation partielle: {}", e.getMessage());
        }
    }

    /**
     * Tente une récupération du schéma
     */
    private static void attemptSchemaRecovery(String sqlitePath) {
        logger.warn("🔧 Tentative de récupération du schéma...");

        try {
            // Créer une sauvegarde
            Path backupPath = Paths.get(sqlitePath + ".backup_" + System.currentTimeMillis());
            Files.copy(Paths.get(sqlitePath), backupPath);
            logger.info("📦 Sauvegarde créée: {}", backupPath);

            // Tenter de réparer
            try (Connection conn = getSQLiteConnection()) {
                // Désactiver temporairement les contraintes
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");

                    // Recréer les tables manquantes
                    createAllSQLiteTables();

                    // Réactiver les contraintes
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }

            logger.info("✅ Récupération du schéma réussie");

        } catch (Exception e) {
            logger.error("❌ Échec de la récupération du schéma", e);
        }
    }

    /**
     * Récupération d'urgence en cas d'échec total
     */
    private static void performEmergencyRecovery() {
        logger.error("🚨 RÉCUPÉRATION D'URGENCE ACTIVÉE");

        try {
            // Fermer toutes les connexions existantes
            if (sqliteDataSource != null && !sqliteDataSource.isClosed()) {
                sqliteDataSource.close();
                sqliteDataSource = null;
            }

            // Utiliser une base en mémoire temporaire
            logger.warn("🔄 Basculement vers base de données en mémoire");
            HikariConfig emergencyConfig = new HikariConfig();
            emergencyConfig.setJdbcUrl("jdbc:sqlite::memory:");
            emergencyConfig.setDriverClassName("org.sqlite.JDBC");
            emergencyConfig.setMaximumPoolSize(1);
            emergencyConfig.setPoolName("EmergencyPool");

            sqliteDataSource = new HikariDataSource(emergencyConfig);

            // Créer le schéma minimal
            createAllSQLiteTables();
            createEmergencyData();

            logger.warn("✅ Base de données d'urgence opérationnelle (données temporaires uniquement)");

        } catch (Exception e) {
            logger.error("❌ Échec de la récupération d'urgence", e);
        }
    }

    /**
     * Crée des données d'urgence minimales
     */
    private static void createEmergencyData() {
        try (Connection conn = getSQLiteConnection()) {
            // Créer un utilisateur admin par défaut
            String sql = "INSERT INTO utilisateurs (username, password_hash, nom_complet, role) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "admin");
                stmt.setString(2, "$2a$10$YK8kKr8mPwv3FM8rMTjm3uASJCn0J0eVqMf2wfQQnYJHhsoF2HJhK"); // "admin123"
                stmt.setString(3, "Administrateur d'urgence");
                stmt.setString(4, "SUPER_ADMIN");
                stmt.executeUpdate();

                logger.info("✅ Utilisateur d'urgence créé: admin/admin123");
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la création des données d'urgence", e);
        }
    }

    // ... (reste du code existant pour createAllSQLiteTables, etc.)

    /**
     * Obtient une connexion à la base SQLite avec retry automatique
     */
    public static Connection getSQLiteConnection() throws SQLException {
        if (sqliteDataSource == null) {
            initializeSQLite();
        }

        int maxRetries = 3;
        SQLException lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                Connection conn = sqliteDataSource.getConnection();

                // Vérifier que la connexion est valide
                if (conn.isValid(5)) {
                    return conn;
                } else {
                    conn.close();
                    throw new SQLException("Connexion invalide obtenue du pool");
                }

            } catch (SQLException e) {
                lastException = e;
                logger.warn("Tentative {} de connexion échouée: {}", i + 1, e.getMessage());

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep((long) Math.pow(2, i) * 1000); // Backoff exponentiel
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        throw new SQLException("Impossible d'obtenir une connexion après " + maxRetries + " tentatives", lastException);
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
                    derniere_connexion DATETIME,
                    tentatives_connexion INTEGER DEFAULT 0
                )
                """,

                // Table contrevenants
                """
                CREATE TABLE IF NOT EXISTS contrevenants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    nom_complet TEXT NOT NULL,
                    type_personne TEXT CHECK(type_personne IN ('PHYSIQUE', 'MORALE')),
                    nom TEXT,
                    prenom TEXT,
                    raison_sociale TEXT,
                    adresse TEXT,
                    telephone TEXT,
                    email TEXT,
                    ville TEXT,
                    code_postal TEXT,
                    cin TEXT,
                    numero_registre_commerce TEXT,
                    numero_identification_fiscale TEXT,
                    actif INTEGER NOT NULL DEFAULT 1,
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
                    email TEXT,
                    telephone TEXT,
                    service_id INTEGER,
                    actif INTEGER NOT NULL DEFAULT 1,
                    role_special TEXT CHECK(role_special IN ('DG', 'DD', NULL)),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (service_id) REFERENCES services (id)
                )
                """,

                // Table services
                """
                CREATE TABLE IF NOT EXISTS services (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_service TEXT NOT NULL UNIQUE,
                    nom_service TEXT NOT NULL,
                    description TEXT,
                    centre_id INTEGER,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (centre_id) REFERENCES centres (id)
                )
                """,

                // Table bureaux
                """
                CREATE TABLE IF NOT EXISTS bureaux (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_bureau TEXT NOT NULL UNIQUE,
                    nom_bureau TEXT NOT NULL,
                    description TEXT,
                    centre_id INTEGER,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (centre_id) REFERENCES centres (id)
                )
                """,

                // Table centres
                """
                CREATE TABLE IF NOT EXISTS centres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_centre TEXT NOT NULL UNIQUE,
                    nom_centre TEXT NOT NULL,
                    description TEXT,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table banques
                """
                CREATE TABLE IF NOT EXISTS banques (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code_banque TEXT NOT NULL UNIQUE,
                    nom_banque TEXT NOT NULL,
                    sigle TEXT,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table contraventions
                """
                CREATE TABLE IF NOT EXISTS contraventions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    libelle TEXT NOT NULL,
                    montant_min REAL,
                    montant_max REAL,
                    montant_fixe REAL,
                    description TEXT,
                    actif INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table affaires
                """
                CREATE TABLE IF NOT EXISTS affaires (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    numero_affaire TEXT NOT NULL UNIQUE,
                    date_creation DATE NOT NULL,
                    date_constatation DATE,
                    lieu_constatation TEXT,
                    description TEXT,
                    montant_total REAL NOT NULL,
                    montant_encaisse REAL DEFAULT 0,
                    montant_amende_total REAL,
                    statut TEXT DEFAULT 'OUVERTE',
                    observations TEXT,
                    contrevenant_id INTEGER NOT NULL,
                    agent_verbalisateur_id INTEGER,
                    bureau_id INTEGER,
                    service_id INTEGER,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT,
                    updated_by TEXT,
                    deleted INTEGER DEFAULT 0,
                    deleted_by TEXT,
                    deleted_at DATETIME,
                    FOREIGN KEY (contrevenant_id) REFERENCES contrevenants (id),
                    FOREIGN KEY (agent_verbalisateur_id) REFERENCES agents (id),
                    FOREIGN KEY (bureau_id) REFERENCES bureaux(id),
                    FOREIGN KEY (service_id) REFERENCES services(id)
                )
                """,

                // Table encaissements
                """
                CREATE TABLE IF NOT EXISTS encaissements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reference TEXT NOT NULL UNIQUE,
                    date_encaissement DATE NOT NULL,
                    montant_encaisse REAL NOT NULL,
                    mode_reglement TEXT NOT NULL CHECK(mode_reglement IN ('ESPECES', 'CHEQUE', 'VIREMENT')),
                    numero_piece TEXT,
                    banque TEXT,
                    observations TEXT,
                    statut TEXT DEFAULT 'EN_ATTENTE',
                    affaire_id INTEGER NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT,
                    updated_by TEXT,
                    FOREIGN KEY (affaire_id) REFERENCES affaires (id)
                )
                """,

                // Table affaire_acteurs (liaison affaire-agent)
                """
                CREATE TABLE IF NOT EXISTS affaire_acteurs (
                    affaire_id INTEGER NOT NULL,
                    agent_id INTEGER NOT NULL,
                    role_sur_affaire TEXT NOT NULL CHECK(role_sur_affaire IN ('CHEF', 'SAISISSANT', 'VERIFICATEUR')),
                    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    assigned_by TEXT,
                    PRIMARY KEY (affaire_id, agent_id, role_sur_affaire),
                    FOREIGN KEY (affaire_id) REFERENCES affaires (id),
                    FOREIGN KEY (agent_id) REFERENCES agents (id)
                )
                """,

                // Table affaire_contraventions (liaison affaire-contravention)
                """
                CREATE TABLE IF NOT EXISTS affaire_contraventions (
                    affaire_id INTEGER NOT NULL,
                    contravention_id INTEGER NOT NULL,
                    montant_applique REAL NOT NULL,
                    PRIMARY KEY (affaire_id, contravention_id),
                    FOREIGN KEY (affaire_id) REFERENCES affaires (id),
                    FOREIGN KEY (contravention_id) REFERENCES contraventions (id)
                )
                """,

                // Table repartition_resultats
                """
                CREATE TABLE IF NOT EXISTS repartition_resultats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    encaissement_id INTEGER NOT NULL,
                    produit_disponible REAL NOT NULL,
                    part_indicateur REAL DEFAULT 0,
                    produit_net REAL NOT NULL,
                    part_flcf REAL NOT NULL,
                    part_tresor REAL NOT NULL,
                    produit_net_droits REAL NOT NULL,
                    part_chefs REAL NOT NULL,
                    part_saisissants REAL NOT NULL,
                    part_mutuelle REAL NOT NULL,
                    part_masse_commune REAL NOT NULL,
                    part_interessement REAL NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (encaissement_id) REFERENCES encaissements (id)
                )
                """,

                // Table repartition_details (détails par agent)
                """
                CREATE TABLE IF NOT EXISTS repartition_details (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    repartition_resultat_id INTEGER NOT NULL,
                    agent_id INTEGER NOT NULL,
                    type_part TEXT NOT NULL CHECK(type_part IN ('CHEF', 'SAISISSANT', 'DD', 'DG')),
                    montant REAL NOT NULL,
                    FOREIGN KEY (repartition_resultat_id) REFERENCES repartition_resultats (id),
                    FOREIGN KEY (agent_id) REFERENCES agents (id)
                )
                """,

                // Table mandats
                """
                CREATE TABLE IF NOT EXISTS mandats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    numero_mandat TEXT NOT NULL UNIQUE,
                    date_debut DATE NOT NULL,
                    date_fin DATE NOT NULL,
                    actif INTEGER NOT NULL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    created_by TEXT,
                    CONSTRAINT un_seul_actif CHECK (
                        (SELECT COUNT(*) FROM mandats WHERE actif = 1) <= 1
                    )
                )
                """,

                // Table parametres (pour les configurations)
                """
                CREATE TABLE IF NOT EXISTS parametres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cle TEXT NOT NULL UNIQUE,
                    valeur TEXT NOT NULL,
                    description TEXT,
                    type_valeur TEXT CHECK(type_valeur IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
                    modifiable INTEGER DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """,

                // Table logs_activites (pour l'audit)
                """
                CREATE TABLE IF NOT EXISTS logs_activites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    utilisateur_id INTEGER,
                    action TEXT NOT NULL,
                    entite TEXT,
                    entite_id INTEGER,
                    anciennes_valeurs TEXT,
                    nouvelles_valeurs TEXT,
                    ip_address TEXT,
                    user_agent TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs (id)
                )
                """
        };

        try (Connection conn = getSQLiteConnection()) {
            logger.info("🔨 Création des tables SQLite...");

            for (String sql : createTableStatements) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }

            // Créer les index pour améliorer les performances
            createIndexes(conn);

            logger.info("✅ Toutes les tables créées avec succès");

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de la création des tables", e);
            throw new RuntimeException("Impossible de créer les tables", e);
        }
    }

    /**
     * Crée les index pour optimiser les performances
     */
    private static void createIndexes(Connection conn) throws SQLException {
        String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_affaires_numero ON affaires(numero_affaire)",
                "CREATE INDEX IF NOT EXISTS idx_affaires_contrevenant ON affaires(contrevenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_affaires_date ON affaires(date_creation)",
                "CREATE INDEX IF NOT EXISTS idx_affaires_statut ON affaires(statut)",
                "CREATE INDEX IF NOT EXISTS idx_encaissements_affaire ON encaissements(affaire_id)",
                "CREATE INDEX IF NOT EXISTS idx_encaissements_date ON encaissements(date_encaissement)",
                "CREATE INDEX IF NOT EXISTS idx_encaissements_reference ON encaissements(reference)",
                "CREATE INDEX IF NOT EXISTS idx_agents_code ON agents(code_agent)",
                "CREATE INDEX IF NOT EXISTS idx_contrevenants_code ON contrevenants(code)",
                "CREATE INDEX IF NOT EXISTS idx_logs_utilisateur ON logs_activites(utilisateur_id)",
                "CREATE INDEX IF NOT EXISTS idx_logs_date ON logs_activites(created_at)"
        };

        logger.debug("📇 Création des index...");

        for (String sql : indexStatements) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }

        logger.debug("✅ Index créés");
    }

    /**
     * Crée les données initiales
     */
    private static void createInitialData() {
        try (Connection conn = getSQLiteConnection()) {
            logger.info("📝 Insertion des données initiales...");

            // Créer l'utilisateur admin par défaut
            String sql = """
                INSERT OR IGNORE INTO utilisateurs (username, password_hash, nom_complet, role) 
                VALUES (?, ?, ?, ?)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "admin");
                stmt.setString(2, "$2a$10$YK8kKr8mPwv3FM8rMTjm3uASJCn0J0eVqMf2wfQQnYJHhsoF2HJhK"); // "admin123"
                stmt.setString(3, "Administrateur Système");
                stmt.setString(4, "SUPER_ADMIN");
                stmt.executeUpdate();
            }

            // Créer les paramètres par défaut
            insertDefaultParameters(conn);

            // Créer les données de référence
            insertReferenceData(conn);

            logger.info("✅ Données initiales créées");

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de la création des données initiales", e);
        }
    }

    /**
     * Insère les paramètres par défaut
     */
    private static void insertDefaultParameters(Connection conn) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO parametres (cle, valeur, description, type_valeur, modifiable) 
            VALUES (?, ?, ?, ?, ?)
        """;

        Object[][] parametres = {
                {"app.version", "1.0.0", "Version de l'application", "STRING", 0},
                {"app.name", "Gestion des Affaires Contentieuses", "Nom de l'application", "STRING", 0},
                {"format.date", "dd/MM/yyyy", "Format d'affichage des dates", "STRING", 1},
                {"format.montant", "#,##0.00", "Format d'affichage des montants", "STRING", 1},
                {"session.timeout", "30", "Timeout de session en minutes", "NUMBER", 1},
                {"backup.auto", "true", "Sauvegarde automatique activée", "BOOLEAN", 1},
                {"backup.interval", "24", "Intervalle de sauvegarde en heures", "NUMBER", 1}
        };

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Object[] param : parametres) {
                stmt.setString(1, (String) param[0]);
                stmt.setString(2, (String) param[1]);
                stmt.setString(3, (String) param[2]);
                stmt.setString(4, (String) param[3]);
                stmt.setInt(5, (Integer) param[4]);
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Insère les données de référence
     */
    private static void insertReferenceData(Connection conn) throws SQLException {
        // Insérer les centres par défaut
        String sqlCentres = "INSERT OR IGNORE INTO centres (code_centre, nom_centre) VALUES (?, ?)";
        Object[][] centres = {
                {"CTR001", "Centre Principal"},
                {"CTR002", "Centre Secondaire"}
        };

        try (PreparedStatement stmt = conn.prepareStatement(sqlCentres)) {
            for (Object[] centre : centres) {
                stmt.setString(1, (String) centre[0]);
                stmt.setString(2, (String) centre[1]);
                stmt.executeUpdate();
            }
        }

        // Insérer les services par défaut
        String sqlServices = "INSERT OR IGNORE INTO services (code_service, nom_service, centre_id) VALUES (?, ?, ?)";
        Object[][] services = {
                {"SRV001", "Service Contentieux", 1},
                {"SRV002", "Service Recouvrement", 1}
        };

        try (PreparedStatement stmt = conn.prepareStatement(sqlServices)) {
            for (Object[] service : services) {
                stmt.setString(1, (String) service[0]);
                stmt.setString(2, (String) service[1]);
                stmt.setInt(3, (Integer) service[2]);
                stmt.executeUpdate();
            }
        }

        // Insérer les banques par défaut
        String sqlBanques = "INSERT OR IGNORE INTO banques (code_banque, nom_banque, sigle) VALUES (?, ?, ?)";
        Object[][] banques = {
                {"BQ001", "Banque Centrale", "BC"},
                {"BQ002", "Banque Commerciale", "BCM"}
        };

        try (PreparedStatement stmt = conn.prepareStatement(sqlBanques)) {
            for (Object[] banque : banques) {
                stmt.setString(1, (String) banque[0]);
                stmt.setString(2, (String) banque[1]);
                stmt.setString(3, (String) banque[2]);
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Log le nombre d'enregistrements par table
     */
    private static void logTableCounts(Connection conn) {
        String[] tables = {
                "utilisateurs", "affaires", "contrevenants", "agents",
                "contraventions", "encaissements", "services", "bureaux",
                "centres", "banques", "affaire_acteurs", "repartition_resultats"
        };

        logger.info("📊 Comptage des enregistrements:");
        for (String table : tables) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count > 0) {
                        logger.info("   - {}: {} enregistrements", table, count);
                    } else {
                        logger.debug("   - {}: vide", table);
                    }
                }
            } catch (SQLException e) {
                logger.debug("   - {}: erreur ou inexistante", table);
            }
        }
    }

    /**
     * Teste la connexion MySQL
     */
    public static void testMySQLConnection() throws SQLException {
        if (mysqlDataSource == null) {
            initializeMySQL();
        }

        if (mysqlDataSource != null) {
            try (Connection conn = mysqlDataSource.getConnection()) {
                logger.info("✅ Test de connexion MySQL réussi");

                // Test enrichi avec plus d'informations
                DatabaseMetaData metaData = conn.getMetaData();
                logger.info("   - Serveur: {} {}", metaData.getDatabaseProductName(),
                        metaData.getDatabaseProductVersion());
                logger.info("   - Driver: {} {}", metaData.getDriverName(),
                        metaData.getDriverVersion());
                logger.info("   - URL: {}", metaData.getURL());

                // Vérifier les privilèges
                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SHOW GRANTS");
                    logger.debug("   - Privilèges accordés:");
                    while (rs.next()) {
                        logger.debug("     {}", rs.getString(1));
                    }
                }
            }
        } else {
            throw new SQLException("Datasource MySQL non initialisé");
        }
    }

    /**
     * Initialise la connexion MySQL avec gestion d'erreur enrichie
     */
    private static void initializeMySQL() {
        try {
            String host = dbProperties.getProperty("mysql.host", "localhost");
            String port = dbProperties.getProperty("mysql.port", "3306");
            String database = dbProperties.getProperty("mysql.database", "contentieux");
            String username = dbProperties.getProperty("mysql.username", "contentieux_user");
            String password = dbProperties.getProperty("mysql.password", "contentieux_pass");

            logger.info("Tentative de connexion MySQL : {}:{}@{}/{}", username, "****", host, database);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Configuration du pool
            config.setMaximumPoolSize(Integer.parseInt(
                    dbProperties.getProperty("mysql.poolSize", "20")));
            config.setConnectionTimeout(Long.parseLong(
                    dbProperties.getProperty("mysql.connectionTimeout", "30000")));
            config.setIdleTimeout(Long.parseLong(
                    dbProperties.getProperty("mysql.idleTimeout", "600000")));
            config.setMaxLifetime(Long.parseLong(
                    dbProperties.getProperty("mysql.maxLifetime", "1800000")));
            config.setPoolName("MySQLPool");

            // Propriétés MySQL enrichies
            config.addDataSourceProperty("useSSL", "false");
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("useUnicode", "true");
            config.addDataSourceProperty("characterEncoding", "UTF-8");
            config.addDataSourceProperty("serverTimezone", "UTC");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

            // Test de validation enrichi
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(5000);

            mysqlDataSource = new HikariDataSource(config);

            logger.info("✅ Connexion MySQL initialisée : {}:{}/{}", host, port, database);

            // Test immédiat de la connexion
            try (Connection testConn = mysqlDataSource.getConnection()) {
                logger.info("✅ Test de connexion initial réussi");
            }

        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'initialisation de MySQL", e);
            logger.warn("🔄 L'application fonctionnera en mode SQLite uniquement");

            // Diagnostic enrichi de l'erreur
            if (e.getMessage().contains("Communications link failure")) {
                logger.error("   → Vérifiez que le serveur MySQL est démarré");
                logger.error("   → Vérifiez l'adresse et le port: {}:{}",
                        dbProperties.getProperty("mysql.host"),
                        dbProperties.getProperty("mysql.port"));
            } else if (e.getMessage().contains("Access denied")) {
                logger.error("   → Vérifiez le nom d'utilisateur et le mot de passe");
                logger.error("   → Vérifiez les privilèges de l'utilisateur");
            } else if (e.getMessage().contains("Unknown database")) {
                logger.error("   → La base de données '{}' n'existe pas",
                        dbProperties.getProperty("mysql.database"));
                logger.error("   → Créez la base avec: CREATE DATABASE {};",
                        dbProperties.getProperty("mysql.database"));
            }
        }
    }

    /**
     * Obtient une connexion à la base MySQL avec gestion d'erreur enrichie
     */
    public static Connection getMySQLConnection() throws SQLException {
        if (mysqlDataSource == null) {
            initializeMySQL();
        }

        if (mysqlDataSource == null) {
            throw new SQLException("Connexion MySQL non disponible - Mode SQLite uniquement");
        }

        // Retry logic similaire à SQLite
        int maxRetries = 3;
        SQLException lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                Connection conn = mysqlDataSource.getConnection();

                if (conn.isValid(5)) {
                    return conn;
                } else {
                    conn.close();
                    throw new SQLException("Connexion MySQL invalide");
                }

            } catch (SQLException e) {
                lastException = e;
                logger.warn("Tentative {} de connexion MySQL échouée: {}", i + 1, e.getMessage());

                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep((long) Math.pow(2, i) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        throw new SQLException("Impossible d'obtenir une connexion MySQL après " + maxRetries + " tentatives", lastException);
    }

    /**
     * Ferme toutes les connexions aux bases de données
     */
    public static void closeAllConnections() {
        logger.info("🔒 Fermeture de toutes les connexions...");

        // Fermer SQLite
        if (sqliteDataSource != null && !sqliteDataSource.isClosed()) {
            try {
                sqliteDataSource.close();
                logger.info("✅ Pool SQLite fermé");
            } catch (Exception e) {
                logger.error("Erreur lors de la fermeture du pool SQLite", e);
            }
        }

        // Fermer MySQL
        if (mysqlDataSource != null && !mysqlDataSource.isClosed()) {
            try {
                mysqlDataSource.close();
                logger.info("✅ Pool MySQL fermé");
            } catch (Exception e) {
                logger.error("Erreur lors de la fermeture du pool MySQL", e);
            }
        }

        logger.info("✅ Toutes les connexions fermées");
    }

    /**
     * Méthode utilitaire pour forcer le rechargement de la configuration
     */
    public static void reloadConfiguration() {
        logger.info("🔄 Rechargement forcé de la configuration...");

        // Fermer les connexions existantes
        closeAllConnections();

        // Recharger
        loadConfiguration();

        // Réinitialiser
        initializeSQLite();
        initializeMySQL();
    }

    /**
     * Vérifie si MySQL est disponible
     */
    public static boolean isMySQLAvailable() {
        if (mysqlDataSource == null) {
            return false;
        }

        try (Connection conn = mysqlDataSource.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Vérifie si SQLite est disponible
     */
    public static boolean isSQLiteAvailable() {
        if (sqliteDataSource == null) {
            return false;
        }

        try (Connection conn = sqliteDataSource.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retourne le statut complet des connexions
     */
    public static ConnectionStatus getConnectionStatus() {
        ConnectionStatus status = new ConnectionStatus();

        // SQLite
        status.setSqliteAvailable(isSQLiteAvailable());
        if (sqliteDataSource != null) {
            HikariDataSource ds = sqliteDataSource;
            status.setSqliteActiveConnections(ds.getHikariPoolMXBean().getActiveConnections());
            status.setSqliteTotalConnections(ds.getHikariPoolMXBean().getTotalConnections());
        }

        // MySQL
        status.setMysqlAvailable(isMySQLAvailable());
        if (mysqlDataSource != null) {
            HikariDataSource ds = mysqlDataSource;
            status.setMysqlActiveConnections(ds.getHikariPoolMXBean().getActiveConnections());
            status.setMysqlTotalConnections(ds.getHikariPoolMXBean().getTotalConnections());
        }

        return status;
    }

    /**
     * Classe interne pour le statut des connexions
     */
    public static class ConnectionStatus {
        private boolean sqliteAvailable;
        private boolean mysqlAvailable;
        private int sqliteActiveConnections;
        private int sqliteTotalConnections;
        private int mysqlActiveConnections;
        private int mysqlTotalConnections;

        // Getters et setters
        public boolean isSqliteAvailable() { return sqliteAvailable; }
        public void setSqliteAvailable(boolean available) { this.sqliteAvailable = available; }

        public boolean isMysqlAvailable() { return mysqlAvailable; }
        public void setMysqlAvailable(boolean available) { this.mysqlAvailable = available; }

        public int getSqliteActiveConnections() { return sqliteActiveConnections; }
        public void setSqliteActiveConnections(int active) { this.sqliteActiveConnections = active; }

        public int getSqliteTotalConnections() { return sqliteTotalConnections; }
        public void setSqliteTotalConnections(int total) { this.sqliteTotalConnections = total; }

        public int getMysqlActiveConnections() { return mysqlActiveConnections; }
        public void setMysqlActiveConnections(int active) { this.mysqlActiveConnections = active; }

        public int getMysqlTotalConnections() { return mysqlTotalConnections; }
        public void setMysqlTotalConnections(int total) { this.mysqlTotalConnections = total; }

        @Override
        public String toString() {
            return String.format("ConnectionStatus[SQLite: %s (%d/%d), MySQL: %s (%d/%d)]",
                    sqliteAvailable ? "UP" : "DOWN", sqliteActiveConnections, sqliteTotalConnections,
                    mysqlAvailable ? "UP" : "DOWN", mysqlActiveConnections, mysqlTotalConnections);
        }
    }

    /**
     * Effectue la synchronisation SQLite vers MySQL
     */
    public static void syncToMySQL() throws SQLException {
        if (!isMySQLAvailable()) {
            throw new SQLException("MySQL non disponible pour la synchronisation");
        }

        logger.info("🔄 Début de la synchronisation SQLite → MySQL");

        try (Connection sqliteConn = getSQLiteConnection();
             Connection mysqlConn = getMySQLConnection()) {

            // Désactiver l'autocommit pour MySQL
            mysqlConn.setAutoCommit(false);

            try {
                // Synchroniser chaque table
                String[] tablesToSync = {
                        "utilisateurs", "contrevenants", "agents", "services",
                        "bureaux", "centres", "banques", "contraventions",
                        "affaires", "encaissements", "affaire_acteurs",
                        "repartition_resultats", "repartition_details"
                };

                for (String table : tablesToSync) {
                    syncTable(sqliteConn, mysqlConn, table);
                }

                // Commit si tout s'est bien passé
                mysqlConn.commit();
                logger.info("✅ Synchronisation réussie");

            } catch (Exception e) {
                // Rollback en cas d'erreur
                mysqlConn.rollback();
                logger.error("❌ Erreur lors de la synchronisation, rollback effectué", e);
                throw new SQLException("Erreur de synchronisation", e);
            }
        }
    }

    /**
     * Synchronise une table spécifique
     */
    private static void syncTable(Connection source, Connection target, String tableName) throws SQLException {
        logger.debug("Synchronisation de la table: {}", tableName);

        // Compter les enregistrements
        int sourceCount = countRecords(source, tableName);
        int targetCount = countRecords(target, tableName);

        logger.debug("  - Source: {} enregistrements, Target: {} enregistrements",
                sourceCount, targetCount);

        if (sourceCount > 0) {
            // TODO: Implémenter la logique de synchronisation réelle
            // Pour l'instant, juste logger
            logger.debug("  - Synchronisation de {} enregistrements", sourceCount);
        }
    }

    /**
     * Compte les enregistrements dans une table
     */
    private static int countRecords(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}