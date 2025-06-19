package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service de sauvegarde et restauration des données
 * Gère les sauvegardes automatiques et manuelles de la base SQLite
 */
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

    private final ExecutorService executorService;
    private static final String BACKUP_DIRECTORY = System.getProperty("user.home") + "/Documents/Sauvegardes_Contentieux";
    private static final String BACKUP_EXTENSION = ".backup.zip";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public BackupService() {
        this.executorService = Executors.newFixedThreadPool(2);
        creerRepertoireSauvegarde();
    }

    /**
     * Résultat d'une opération de sauvegarde/restauration
     */
    public static class BackupResult {
        private boolean success;
        private String message;
        private String filePath;
        private long fileSize;
        private LocalDateTime timestamp;
        private Exception error;

        public BackupResult() {
            this.timestamp = LocalDateTime.now();
        }

        // Getters et setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public Exception getError() { return error; }
        public void setError(Exception error) { this.error = error; }
    }

    /**
     * Informations sur un fichier de sauvegarde
     */
    public static class BackupInfo {
        private String fileName;
        private String fullPath;
        private LocalDateTime creationDate;
        private long fileSize;
        private String description;

        public BackupInfo(String fileName, String fullPath, LocalDateTime creationDate, long fileSize) {
            this.fileName = fileName;
            this.fullPath = fullPath;
            this.creationDate = creationDate;
            this.fileSize = fileSize;
        }

        // Getters et setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFullPath() { return fullPath; }
        public void setFullPath(String fullPath) { this.fullPath = fullPath; }

        public LocalDateTime getCreationDate() { return creationDate; }
        public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getFormattedSize() {
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /**
     * Crée une sauvegarde manuelle
     */
    public CompletableFuture<BackupResult> creerSauvegardeManuelle(String description) {
        return CompletableFuture.supplyAsync(() -> {
            BackupResult result = new BackupResult();

            try {
                logger.info("Début de la sauvegarde manuelle: {}", description);

                String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
                String fileName = String.format("Backup_Manuel_%s%s", timestamp, BACKUP_EXTENSION);
                String fullPath = Paths.get(BACKUP_DIRECTORY, fileName).toString();

                // Création de la sauvegarde
                creerSauvegarde(fullPath, description, true);

                File backupFile = new File(fullPath);
                if (backupFile.exists()) {
                    result.setSuccess(true);
                    result.setMessage("Sauvegarde manuelle créée avec succès");
                    result.setFilePath(fullPath);
                    result.setFileSize(backupFile.length());

                    logger.info("Sauvegarde manuelle créée: {} ({})", fileName, formatFileSize(backupFile.length()));
                } else {
                    result.setSuccess(false);
                    result.setMessage("Fichier de sauvegarde introuvable après création");
                }

            } catch (Exception e) {
                logger.error("Erreur lors de la sauvegarde manuelle", e);
                result.setSuccess(false);
                result.setMessage("Erreur: " + e.getMessage());
                result.setError(e);
            }

            return result;
        }, executorService);
    }

    /**
     * Crée une sauvegarde automatique
     */
    public CompletableFuture<BackupResult> creerSauvegardeAutomatique() {
        return CompletableFuture.supplyAsync(() -> {
            BackupResult result = new BackupResult();

            try {
                logger.info("Début de la sauvegarde automatique");

                String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
                String fileName = String.format("Backup_Auto_%s%s", timestamp, BACKUP_EXTENSION);
                String fullPath = Paths.get(BACKUP_DIRECTORY, fileName).toString();

                // Création de la sauvegarde
                creerSauvegarde(fullPath, "Sauvegarde automatique", false);

                File backupFile = new File(fullPath);
                if (backupFile.exists()) {
                    result.setSuccess(true);
                    result.setMessage("Sauvegarde automatique créée");
                    result.setFilePath(fullPath);
                    result.setFileSize(backupFile.length());

                    // Nettoyage des anciennes sauvegardes automatiques
                    nettoyerAnciennesSauvegardes();

                    logger.info("Sauvegarde automatique créée: {}", fileName);
                } else {
                    result.setSuccess(false);
                    result.setMessage("Fichier de sauvegarde introuvable");
                }

            } catch (Exception e) {
                logger.error("Erreur lors de la sauvegarde automatique", e);
                result.setSuccess(false);
                result.setMessage("Erreur: " + e.getMessage());
                result.setError(e);
            }

            return result;
        }, executorService);
    }

    /**
     * Crée effectivement la sauvegarde
     */
    private void creerSauvegarde(String backupPath, String description, boolean includeAll) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupPath))) {

            // Sauvegarde de la base de données SQLite
            sauvegarderBaseDeDonnees(zos);

            // Sauvegarde des fichiers de configuration
            sauvegarderFichiersConfiguration(zos);

            // Métadonnées de la sauvegarde
            ajouterMetadonnees(zos, description, includeAll);

            // Sauvegarde des logs si demandé
            if (includeAll) {
                sauvegarderLogs(zos);
            }
        }
    }

    /**
     * Sauvegarde la base de données SQLite
     */
    private void sauvegarderBaseDeDonnees(ZipOutputStream zos) throws Exception {
        // Export SQL de la base
        String sqlDump = exporterBaseSQLite();

        ZipEntry entry = new ZipEntry("database_export.sql");
        zos.putNextEntry(entry);
        zos.write(sqlDump.getBytes("UTF-8"));
        zos.closeEntry();

        // Copie du fichier de base de données
        String dbPath = "database.db"; // Ajuster selon la configuration
        File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            ZipEntry dbEntry = new ZipEntry("database.db");
            zos.putNextEntry(dbEntry);

            try (FileInputStream fis = new FileInputStream(dbFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
            }
            zos.closeEntry();
        }
    }

    /**
     * Exporte la base SQLite vers SQL
     */
    private String exporterBaseSQLite() throws SQLException {
        StringBuilder sqlDump = new StringBuilder();
        sqlDump.append("-- Sauvegarde de la base de données SQLite\n");
        sqlDump.append("-- Générée le: ").append(LocalDateTime.now()).append("\n\n");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Obtenir toutes les tables
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                // Structure de la table
                sqlDump.append("-- Structure de la table ").append(tableName).append("\n");
                sqlDump.append(obtenirStructureTable(conn, tableName));
                sqlDump.append("\n");

                // Données de la table
                sqlDump.append("-- Données de la table ").append(tableName).append("\n");
                sqlDump.append(obtenirDonneesTable(conn, tableName));
                sqlDump.append("\n\n");
            }

            tables.close();
        }

        return sqlDump.toString();
    }

    /**
     * Obtient la structure d'une table
     */
    private String obtenirStructureTable(Connection conn, String tableName) throws SQLException {
        StringBuilder structure = new StringBuilder();

        try (PreparedStatement stmt = conn.prepareStatement("SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    structure.append(rs.getString("sql")).append(";\n");
                }
            }
        }

        return structure.toString();
    }

    /**
     * Obtient les données d'une table
     */
    private String obtenirDonneesTable(Connection conn, String tableName) throws SQLException {
        StringBuilder donnees = new StringBuilder();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                donnees.append("INSERT INTO ").append(tableName).append(" VALUES (");

                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) donnees.append(", ");

                    Object value = rs.getObject(i);
                    if (value == null) {
                        donnees.append("NULL");
                    } else if (value instanceof String) {
                        donnees.append("'").append(value.toString().replace("'", "''")).append("'");
                    } else {
                        donnees.append(value.toString());
                    }
                }

                donnees.append(");\n");
            }
        }

        return donnees.toString();
    }

    /**
     * Sauvegarde les fichiers de configuration
     */
    private void sauvegarderFichiersConfiguration(ZipOutputStream zos) throws IOException {
        String[] configFiles = {
                "application.properties",
                "database.properties",
                "logback.xml"
        };

        for (String configFile : configFiles) {
            File file = new File("src/main/resources/" + configFile);
            if (file.exists()) {
                ZipEntry entry = new ZipEntry("config/" + configFile);
                zos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * Ajoute les métadonnées de la sauvegarde
     */
    private void ajouterMetadonnees(ZipOutputStream zos, String description, boolean includeAll) throws IOException {
        Properties metadata = new Properties();
        metadata.setProperty("backup.date", LocalDateTime.now().toString());
        metadata.setProperty("backup.description", description);
        metadata.setProperty("backup.type", includeAll ? "COMPLETE" : "AUTOMATIC");
        metadata.setProperty("backup.version", "1.0");
        metadata.setProperty("application.version", "1.0.0");

        ZipEntry entry = new ZipEntry("backup_metadata.properties");
        zos.putNextEntry(entry);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        metadata.store(baos, "Métadonnées de la sauvegarde");
        zos.write(baos.toByteArray());
        zos.closeEntry();
    }

    /**
     * Sauvegarde les fichiers de logs
     */
    private void sauvegarderLogs(ZipOutputStream zos) throws IOException {
        File logsDir = new File("logs");
        if (logsDir.exists() && logsDir.isDirectory()) {
            File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));

            if (logFiles != null) {
                for (File logFile : logFiles) {
                    ZipEntry entry = new ZipEntry("logs/" + logFile.getName());
                    zos.putNextEntry(entry);

                    try (FileInputStream fis = new FileInputStream(logFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * Restaure une sauvegarde
     */
    public CompletableFuture<BackupResult> restaurerSauvegarde(String backupPath) {
        return CompletableFuture.supplyAsync(() -> {
            BackupResult result = new BackupResult();

            try {
                logger.info("Début de la restauration depuis: {}", backupPath);

                File backupFile = new File(backupPath);
                if (!backupFile.exists()) {
                    result.setSuccess(false);
                    result.setMessage("Fichier de sauvegarde introuvable");
                    return result;
                }

                // Validation de la sauvegarde
                if (!validerSauvegarde(backupPath)) {
                    result.setSuccess(false);
                    result.setMessage("Fichier de sauvegarde corrompu ou invalide");
                    return result;
                }

                // Sauvegarde de sécurité avant restauration
                creerSauvegardeSecurite();

                // Restauration
                restaurerDepuisZip(backupPath);

                result.setSuccess(true);
                result.setMessage("Restauration réussie");
                result.setFilePath(backupPath);

                logger.info("Restauration terminée avec succès");

            } catch (Exception e) {
                logger.error("Erreur lors de la restauration", e);
                result.setSuccess(false);
                result.setMessage("Erreur: " + e.getMessage());
                result.setError(e);
            }

            return result;
        }, executorService);
    }

    /**
     * Valide un fichier de sauvegarde
     */
    private boolean validerSauvegarde(String backupPath) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupPath))) {
            ZipEntry entry;
            boolean hasDatabase = false;
            boolean hasMetadata = false;

            while ((entry = zis.getNextEntry()) != null) {
                if ("database_export.sql".equals(entry.getName()) || "database.db".equals(entry.getName())) {
                    hasDatabase = true;
                }
                if ("backup_metadata.properties".equals(entry.getName())) {
                    hasMetadata = true;
                }
            }

            return hasDatabase && hasMetadata;

        } catch (Exception e) {
            logger.error("Erreur lors de la validation de la sauvegarde", e);
            return false;
        }
    }

    /**
     * Crée une sauvegarde de sécurité avant restauration
     */
    private void creerSauvegardeSecurite() throws Exception {
        String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
        String fileName = String.format("Backup_Securite_%s%s", timestamp, BACKUP_EXTENSION);
        String fullPath = Paths.get(BACKUP_DIRECTORY, fileName).toString();

        creerSauvegarde(fullPath, "Sauvegarde de sécurité avant restauration", false);
        logger.info("Sauvegarde de sécurité créée: {}", fileName);
    }

    /**
     * Restaure depuis un fichier ZIP
     */
    private void restaurerDepuisZip(String backupPath) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupPath))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if ("database_export.sql".equals(entry.getName())) {
                    // Restauration de la base de données
                    restaurerBaseDeDonnees(zis);
                } else if (entry.getName().startsWith("config/")) {
                    // Restauration des fichiers de configuration
                    restaurerFichierConfiguration(zis, entry);
                }
            }
        }
    }

    /**
     * Restaure la base de données
     */
    private void restaurerBaseDeDonnees(ZipInputStream zis) throws Exception {
        // Lecture du contenu SQL
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, length);
        }

        String sqlContent = baos.toString("UTF-8");

        // Exécution du SQL
        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Désactiver les contraintes temporairement
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF");

                // Exécuter le script SQL
                String[] sqlStatements = sqlContent.split(";");
                for (String sqlStatement : sqlStatements) {
                    sqlStatement = sqlStatement.trim();
                    if (!sqlStatement.isEmpty() && !sqlStatement.startsWith("--")) {
                        stmt.execute(sqlStatement);
                    }
                }

                // Réactiver les contraintes
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    /**
     * Restaure un fichier de configuration
     */
    private void restaurerFichierConfiguration(ZipInputStream zis, ZipEntry entry) throws IOException {
        String fileName = entry.getName().substring("config/".length());
        String targetPath = "src/main/resources/" + fileName;

        // Créer le répertoire si nécessaire
        File targetFile = new File(targetPath);
        targetFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }

        logger.debug("Fichier de configuration restauré: {}", fileName);
    }

    /**
     * Liste les sauvegardes disponibles
     */
    public List<BackupInfo> listerSauvegardes() {
        List<BackupInfo> sauvegardes = new ArrayList<>();

        File backupDir = new File(BACKUP_DIRECTORY);
        if (backupDir.exists() && backupDir.isDirectory()) {
            File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(BACKUP_EXTENSION));

            if (backupFiles != null) {
                for (File backupFile : backupFiles) {
                    try {
                        LocalDateTime creationDate = obtenirDateCreationSauvegarde(backupFile);
                        BackupInfo info = new BackupInfo(
                                backupFile.getName(),
                                backupFile.getAbsolutePath(),
                                creationDate,
                                backupFile.length()
                        );

                        // Déterminer le type de sauvegarde
                        if (backupFile.getName().contains("Manuel")) {
                            info.setDescription("Sauvegarde manuelle");
                        } else if (backupFile.getName().contains("Auto")) {
                            info.setDescription("Sauvegarde automatique");
                        } else if (backupFile.getName().contains("Securite")) {
                            info.setDescription("Sauvegarde de sécurité");
                        }

                        sauvegardes.add(info);

                    } catch (Exception e) {
                        logger.warn("Erreur lors de l'analyse du fichier de sauvegarde: {}", backupFile.getName(), e);
                    }
                }
            }
        }

        // Trier par date de création (plus récent en premier)
        sauvegardes.sort((a, b) -> b.getCreationDate().compareTo(a.getCreationDate()));

        return sauvegardes;
    }

    /**
     * Obtient la date de création d'une sauvegarde
     */
    private LocalDateTime obtenirDateCreationSauvegarde(File backupFile) {
        try {
            // Essayer d'extraire la date du nom du fichier
            String fileName = backupFile.getName();
            String[] parts = fileName.split("_");

            if (parts.length >= 3) {
                String datePart = parts[2];
                String timePart = parts[3].replace(BACKUP_EXTENSION, "");
                String dateTimeStr = datePart + "_" + timePart;

                return LocalDateTime.parse(dateTimeStr, BACKUP_DATE_FORMAT);
            }

        } catch (Exception e) {
            logger.debug("Impossible d'extraire la date du nom de fichier: {}", backupFile.getName());
        }

        // Fallback sur la date de modification du fichier
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(backupFile.lastModified()),
                java.time.ZoneId.systemDefault()
        );
    }

    /**
     * Supprime une sauvegarde
     */
    public boolean supprimerSauvegarde(String backupPath) {
        try {
            File backupFile = new File(backupPath);
            if (backupFile.exists()) {
                boolean deleted = backupFile.delete();
                if (deleted) {
                    logger.info("Sauvegarde supprimée: {}", backupFile.getName());
                }
                return deleted;
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la suppression de la sauvegarde", e);
        }
        return false;
    }

    /**
     * Nettoie les anciennes sauvegardes automatiques
     */
    private void nettoyerAnciennesSauvegardes() {
        try {
            List<BackupInfo> sauvegardes = listerSauvegardes();

            // Garder seulement les 10 dernières sauvegardes automatiques
            List<BackupInfo> sauvegardesAuto = sauvegardes.stream()
                    .filter(backup -> backup.getFileName().contains("Auto"))
                    .sorted((a, b) -> b.getCreationDate().compareTo(a.getCreationDate()))
                    .toList();

            if (sauvegardesAuto.size() > 10) {
                for (int i = 10; i < sauvegardesAuto.size(); i++) {
                    BackupInfo oldBackup = sauvegardesAuto.get(i);
                    supprimerSauvegarde(oldBackup.getFullPath());
                }
            }

        } catch (Exception e) {
            logger.error("Erreur lors du nettoyage des anciennes sauvegardes", e);
        }
    }

    /**
     * Planifie les sauvegardes automatiques
     */
    public void planifierSauvegardesAutomatiques(int intervalHeures) {
        Timer timer = new Timer("BackupAutomatique", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                logger.info("Démarrage de la sauvegarde automatique planifiée");
                creerSauvegardeAutomatique()
                        .thenAccept(result -> {
                            if (result.isSuccess()) {
                                logger.info("Sauvegarde automatique planifiée réussie");
                            } else {
                                logger.warn("Sauvegarde automatique planifiée échouée: {}", result.getMessage());
                            }
                        })
                        .exceptionally(throwable -> {
                            logger.error("Erreur lors de la sauvegarde automatique planifiée", throwable);
                            return null;
                        });
            }
        }, intervalHeures * 60 * 60 * 1000L, intervalHeures * 60 * 60 * 1000L);

        logger.info("Sauvegardes automatiques planifiées toutes les {} heures", intervalHeures);
    }

    /**
     * Crée le répertoire de sauvegarde
     */
    private void creerRepertoireSauvegarde() {
        File backupDir = new File(BACKUP_DIRECTORY);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
            logger.info("Répertoire de sauvegarde créé: {}", BACKUP_DIRECTORY);
        }
    }

    /**
     * Formate la taille d'un fichier
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    /**
     * Obtient des statistiques sur les sauvegardes
     */
    public Map<String, Object> obtenirStatistiquesSauvegardes() {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<BackupInfo> sauvegardes = listerSauvegardes();

            stats.put("nombreTotal", sauvegardes.size());
            stats.put("nombreManuelles", sauvegardes.stream()
                    .mapToInt(b -> b.getFileName().contains("Manuel") ? 1 : 0).sum());
            stats.put("nombreAutomatiques", sauvegardes.stream()
                    .mapToInt(b -> b.getFileName().contains("Auto") ? 1 : 0).sum());

            long tailleTotal = sauvegardes.stream().mapToLong(BackupInfo::getFileSize).sum();
            stats.put("tailleTotal", tailleTotal);
            stats.put("tailleTotalFormattee", formatFileSize(tailleTotal));

            if (!sauvegardes.isEmpty()) {
                stats.put("derniereSauvegarde", sauvegardes.get(0).getCreationDate());
            }

            stats.put("repertoireSauvegarde", BACKUP_DIRECTORY);

        } catch (Exception e) {
            logger.error("Erreur lors de l'obtention des statistiques de sauvegarde", e);
            stats.put("erreur", e.getMessage());
        }

        return stats;
    }

    /**
     * Nettoyage des ressources
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("Service de sauvegarde arrêté");
        }
    }
}