package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service de synchronisation entre SQLite (local) et MySQL (distant)
 * Selon le cahier des charges :
 * - Mode Sauvegarde : SQLite → MySQL (déclenchée manuellement ou à la fermeture)
 * - Mode Restauration : MySQL → SQLite (récupération des données distantes)
 * - Gestion des conflits avec horodatage
 */
public class SynchronizationService {

    private static final Logger logger = LoggerFactory.getLogger(SynchronizationService.class);

    // Tables à synchroniser dans l'ordre (respecter les dépendances)
    private static final String[] TABLES_ORDER = {
            "centres", "services", "bureaux", "banques",
            "contraventions", "contrevenants", "agents",
            "mandats", "affaires", "encaissements",
            "affaire_acteurs", "affaire_contraventions",
            "repartition_resultats", "repartition_details",
            "utilisateurs", "parametres", "logs_activites"
    };

    // Tables avec colonnes d'horodatage pour la gestion des conflits
    private static final Map<String, String> TIMESTAMP_COLUMNS = Map.of(
            "created_at", "created_at",
            "updated_at", "updated_at"
    );

    private boolean synchronizationInProgress = false;
    private LocalDateTime lastSyncTime = null;

    /**
     * Synchronise SQLite vers MySQL (Sauvegarde)
     */
    public CompletableFuture<SyncResult> sauvegarderVersMySQL() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("🔄 === DÉBUT SYNCHRONISATION SQLite → MySQL ===");

            if (synchronizationInProgress) {
                logger.warn("⚠️ Synchronisation déjà en cours");
                return new SyncResult(false, "Synchronisation déjà en cours");
            }

            synchronizationInProgress = true;
            SyncResult result = new SyncResult();

            try {
                // Vérifier la disponibilité de MySQL
                if (!DatabaseConfig.isMySQLAvailable()) {
                    throw new SQLException("MySQL non disponible");
                }

                try (Connection sqliteConn = DatabaseConfig.getSQLiteConnection();
                     Connection mysqlConn = DatabaseConfig.getMySQLConnection()) {

                    // Désactiver l'autocommit
                    mysqlConn.setAutoCommit(false);

                    // Synchroniser chaque table
                    for (String table : TABLES_ORDER) {
                        syncTableToMySQL(sqliteConn, mysqlConn, table, result);
                    }

                    // Commit si tout est OK
                    mysqlConn.commit();

                    // Enregistrer le timestamp
                    lastSyncTime = LocalDateTime.now();
                    enregistrerSynchronisation(sqliteConn, "SAUVEGARDE", result);

                    result.setSuccess(true);
                    result.setMessage("Synchronisation réussie");
                    logger.info("✅ Synchronisation terminée avec succès");

                } catch (Exception e) {
                    logger.error("❌ Erreur lors de la synchronisation", e);
                    result.setSuccess(false);
                    result.setMessage("Erreur: " + e.getMessage());
                }

            } finally {
                synchronizationInProgress = false;
            }

            return result;
        });
    }

    /**
     * Synchronise MySQL vers SQLite (Restauration)
     */
    public CompletableFuture<SyncResult> restaurerDepuisMySQL() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("🔄 === DÉBUT RESTAURATION MySQL → SQLite ===");

            if (synchronizationInProgress) {
                logger.warn("⚠️ Synchronisation déjà en cours");
                return new SyncResult(false, "Synchronisation déjà en cours");
            }

            synchronizationInProgress = true;
            SyncResult result = new SyncResult();

            try {
                // Vérifier la disponibilité de MySQL
                if (!DatabaseConfig.isMySQLAvailable()) {
                    throw new SQLException("MySQL non disponible");
                }

                // Créer une sauvegarde avant restauration
                creerSauvegardeLocale();

                try (Connection sqliteConn = DatabaseConfig.getSQLiteConnection();
                     Connection mysqlConn = DatabaseConfig.getMySQLConnection()) {

                    // Désactiver l'autocommit et les contraintes temporairement
                    sqliteConn.setAutoCommit(false);
                    try (Statement stmt = sqliteConn.createStatement()) {
                        stmt.execute("PRAGMA foreign_keys = OFF");
                    }

                    // Synchroniser chaque table (ordre inverse pour suppression)
                    List<String> reversedTables = new ArrayList<>(Arrays.asList(TABLES_ORDER));
                    Collections.reverse(reversedTables);

                    // D'abord vider les tables locales
                    for (String table : reversedTables) {
                        truncateTable(sqliteConn, table);
                    }

                    // Puis importer depuis MySQL
                    for (String table : TABLES_ORDER) {
                        syncTableFromMySQL(mysqlConn, sqliteConn, table, result);
                    }

                    // Réactiver les contraintes
                    try (Statement stmt = sqliteConn.createStatement()) {
                        stmt.execute("PRAGMA foreign_keys = ON");
                    }

                    // Commit
                    sqliteConn.commit();

                    // Enregistrer le timestamp
                    lastSyncTime = LocalDateTime.now();
                    enregistrerSynchronisation(sqliteConn, "RESTAURATION", result);

                    result.setSuccess(true);
                    result.setMessage("Restauration réussie");
                    logger.info("✅ Restauration terminée avec succès");

                } catch (Exception e) {
                    logger.error("❌ Erreur lors de la restauration", e);
                    result.setSuccess(false);
                    result.setMessage("Erreur: " + e.getMessage());

                    // Tenter de restaurer la sauvegarde
                    restaurerSauvegardeLocale();
                }

            } finally {
                synchronizationInProgress = false;
            }

            return result;
        });
    }

    /**
     * Synchronise une table de SQLite vers MySQL
     */
    private void syncTableToMySQL(Connection source, Connection target,
                                  String tableName, SyncResult result) throws SQLException {
        logger.debug("📤 Synchronisation de {} vers MySQL...", tableName);

        // Récupérer les colonnes de la table
        List<String> columns = getTableColumns(source, tableName);

        // Préparer les requêtes
        String selectSql = "SELECT * FROM " + tableName;
        String insertSql = buildUpsertQuery(tableName, columns, true); // true = MySQL syntax

        int count = 0;
        try (Statement selectStmt = source.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSql);
             PreparedStatement insertStmt = target.prepareStatement(insertSql)) {

            while (rs.next()) {
                // Copier les valeurs
                for (int i = 1; i <= columns.size(); i++) {
                    insertStmt.setObject(i, rs.getObject(i));
                }

                insertStmt.addBatch();
                count++;

                // Exécuter par batch
                if (count % 1000 == 0) {
                    insertStmt.executeBatch();
                    logger.debug("   {} enregistrements traités...", count);
                }
            }

            // Exécuter le dernier batch
            if (count % 1000 != 0) {
                insertStmt.executeBatch();
            }

            result.addTableSync(tableName, count);
            logger.debug("✅ {} : {} enregistrements synchronisés", tableName, count);

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de la sync de {}: {}", tableName, e.getMessage());
            throw e;
        }
    }

    /**
     * Synchronise une table de MySQL vers SQLite
     */
    private void syncTableFromMySQL(Connection source, Connection target,
                                    String tableName, SyncResult result) throws SQLException {
        logger.debug("📥 Importation de {} depuis MySQL...", tableName);

        // Récupérer les colonnes
        List<String> columns = getTableColumns(target, tableName);

        // Préparer les requêtes
        String selectSql = "SELECT * FROM " + tableName;
        String insertSql = buildInsertQuery(tableName, columns);

        int count = 0;
        try (Statement selectStmt = source.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSql);
             PreparedStatement insertStmt = target.prepareStatement(insertSql)) {

            while (rs.next()) {
                // Copier les valeurs
                for (int i = 1; i <= columns.size(); i++) {
                    insertStmt.setObject(i, rs.getObject(columns.get(i-1)));
                }

                insertStmt.addBatch();
                count++;

                // Exécuter par batch
                if (count % 1000 == 0) {
                    insertStmt.executeBatch();
                    logger.debug("   {} enregistrements importés...", count);
                }
            }

            // Exécuter le dernier batch
            if (count % 1000 != 0) {
                insertStmt.executeBatch();
            }

            result.addTableSync(tableName, count);
            logger.debug("✅ {} : {} enregistrements importés", tableName, count);

        } catch (SQLException e) {
            logger.error("❌ Erreur lors de l'import de {}: {}", tableName, e.getMessage());
            throw e;
        }
    }

    /**
     * Récupère les colonnes d'une table
     */
    private List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();

        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }

        return columns;
    }

    /**
     * Construit une requête INSERT
     */
    private String buildInsertQuery(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");

        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(columns.get(i));
            values.append("?");
        }

        sql.append(")").append(values).append(")");
        return sql.toString();
    }

    /**
     * Construit une requête UPSERT (INSERT ou UPDATE)
     */
    private String buildUpsertQuery(String tableName, List<String> columns, boolean mysqlSyntax) {
        if (mysqlSyntax) {
            // MySQL: INSERT ... ON DUPLICATE KEY UPDATE
            StringBuilder sql = new StringBuilder(buildInsertQuery(tableName, columns));
            sql.append(" ON DUPLICATE KEY UPDATE ");

            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns.get(i)).append(" = VALUES(").append(columns.get(i)).append(")");
            }

            return sql.toString();
        } else {
            // SQLite: INSERT OR REPLACE
            return buildInsertQuery(tableName, columns).replace("INSERT", "INSERT OR REPLACE");
        }
    }

    /**
     * Vide une table
     */
    private void truncateTable(Connection conn, String tableName) throws SQLException {
        String sql = "DELETE FROM " + tableName;
        try (Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                logger.debug("🗑️ Table {} vidée: {} enregistrements supprimés", tableName, deleted);
            }
        }
    }

    /**
     * Crée une sauvegarde locale avant restauration
     */
    private void creerSauvegardeLocale() {
        logger.info("💾 Création d'une sauvegarde locale...");
        // TODO: Implémenter la sauvegarde physique du fichier SQLite
    }

    /**
     * Restaure la sauvegarde locale en cas d'erreur
     */
    private void restaurerSauvegardeLocale() {
        logger.warn("🔄 Restauration de la sauvegarde locale...");
        // TODO: Implémenter la restauration du fichier SQLite
    }

    /**
     * Enregistre l'opération de synchronisation
     */
    private void enregistrerSynchronisation(Connection conn, String type, SyncResult result) {
        String sql = """
            INSERT INTO logs_activites 
            (action, entite, nouvelles_valeurs, created_at)
            VALUES (?, 'SYNCHRONISATION', ?, CURRENT_TIMESTAMP)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "SYNC_" + type);
            stmt.setString(2, result.toJson());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Erreur lors de l'enregistrement de la sync", e);
        }
    }

    /**
     * Classe pour encapsuler les résultats de synchronisation
     */
    public static class SyncResult {
        private boolean success;
        private String message;
        private Map<String, Integer> tableSyncs = new HashMap<>();
        private LocalDateTime timestamp = LocalDateTime.now();

        public SyncResult() {}

        public SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public void addTableSync(String table, int count) {
            tableSyncs.put(table, count);
        }

        public String toJson() {
            // Simple JSON pour l'exemple
            return String.format(
                    "{\"success\":%s,\"message\":\"%s\",\"tables\":%d,\"timestamp\":\"%s\"}",
                    success, message, tableSyncs.size(), timestamp
            );
        }

        // Getters et setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Map<String, Integer> getTableSyncs() { return tableSyncs; }
    }

    /**
     * Vérifie si une synchronisation est nécessaire
     */
    public boolean isSyncNeeded() {
        if (lastSyncTime == null) return true;

        // Synchroniser si plus de 24h depuis la dernière sync
        return LocalDateTime.now().minusHours(24).isAfter(lastSyncTime);
    }

    /**
     * @return Le timestamp de la dernière synchronisation
     */
    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }
}