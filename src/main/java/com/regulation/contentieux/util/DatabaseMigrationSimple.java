package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Outil de migration de la base de données - Version simplifiée
 */
public class DatabaseMigrationSimple {

    public static void main(String[] args) {
        System.out.println("=== MIGRATION DE LA BASE DE DONNÉES ===\n");

        try {
            // 1. Créer une sauvegarde
            createBackupSimple();

            // 2. Vérifier la structure actuelle
            checkCurrentSchema();

            // 3. Effectuer la migration
            performMigration();

            // 4. Vérifier la migration
            verifyMigration();

            System.out.println("✅ Migration terminée avec succès !");
            System.out.println("Votre application devrait maintenant fonctionner correctement.");

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la migration: " + e.getMessage());
            e.printStackTrace();
            System.err.println("\n⚠️  Restaurez votre sauvegarde si nécessaire !");
        }
    }

    private static void createBackupSimple() throws IOException {
        System.out.println("1. CRÉATION DE LA SAUVEGARDE:");
        System.out.println("-----------------------------");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = "gestion_contentieux_backup_" + timestamp + ".db";

        // Copier avec FileInputStream/FileOutputStream (plus simple)
        File originalFile = new File("data/gestion_contentieux.db");
        File backupFile = new File("data/" + backupName);

        if (!originalFile.exists()) {
            // Essayer aussi à la racine
            originalFile = new File("gestion_contentieux.db");
        }

        if (originalFile.exists()) {
            try (FileInputStream fis = new FileInputStream(originalFile);
                 FileOutputStream fos = new FileOutputStream(backupFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("✅ Sauvegarde créée: " + backupName);
            System.out.println("   Taille: " + (backupFile.length() / (1024 * 1024)) + " MB");
        } else {
            throw new IOException("Fichier de base de données non trouvé");
        }

        System.out.println();
    }

    private static void checkCurrentSchema() {
        System.out.println("2. VÉRIFICATION DU SCHÉMA ACTUEL:");
        System.out.println("---------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            String schemaQuery = "PRAGMA table_info(affaires)";
            try (PreparedStatement stmt = conn.prepareStatement(schemaQuery);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("Colonnes actuelles de la table affaires:");
                boolean hasCreatedAt = false;
                boolean hasUpdatedAt = false;
                boolean hasCreatedBy = false;
                boolean hasUpdatedBy = false;

                while (rs.next()) {
                    String columnName = rs.getString("name");
                    System.out.println("  - " + columnName);

                    switch (columnName) {
                        case "created_at" -> hasCreatedAt = true;
                        case "updated_at" -> hasUpdatedAt = true;
                        case "created_by" -> hasCreatedBy = true;
                        case "updated_by" -> hasUpdatedBy = true;
                    }
                }

                System.out.println("\nColonnes manquantes à ajouter:");
                if (!hasCreatedAt) System.out.println("  - created_at");
                if (!hasUpdatedAt) System.out.println("  - updated_at");
                if (!hasCreatedBy) System.out.println("  - created_by");
                if (!hasUpdatedBy) System.out.println("  - updated_by");

                if (hasCreatedAt && hasUpdatedAt && hasCreatedBy && hasUpdatedBy) {
                    System.out.println("  ✅ Aucune - le schéma est déjà à jour !");
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur: " + e.getMessage());
        }

        System.out.println();
    }

    private static void performMigration() {
        System.out.println("3. EXÉCUTION DE LA MIGRATION:");
        System.out.println("-----------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Ajouter les colonnes manquantes
                addColumnSafely(stmt, "created_at", "ALTER TABLE affaires ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "updated_at", "ALTER TABLE affaires ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP");
                addColumnSafely(stmt, "created_by", "ALTER TABLE affaires ADD COLUMN created_by TEXT");
                addColumnSafely(stmt, "updated_by", "ALTER TABLE affaires ADD COLUMN updated_by TEXT");

                // Mettre à jour les valeurs par défaut pour les enregistrements existants
                System.out.println("Mise à jour des valeurs par défaut...");
                int updatedRows = stmt.executeUpdate("""
                    UPDATE affaires 
                    SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
                        updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP),
                        created_by = COALESCE(created_by, 'migration'),
                        updated_by = COALESCE(updated_by, 'migration')
                    WHERE created_at IS NULL OR updated_at IS NULL OR created_by IS NULL OR updated_by IS NULL
                """);

                System.out.println("✅ " + updatedRows + " enregistrements mis à jour");

                conn.commit();
                System.out.println("✅ Migration commitée");
            }

        } catch (SQLException e) {
            System.err.println("❌ Erreur lors de la migration: " + e.getMessage());
            throw new RuntimeException("Migration échouée", e);
        }

        System.out.println();
    }

    private static void addColumnSafely(Statement stmt, String columnName, String sql) {
        System.out.println("Ajout de la colonne " + columnName + "...");
        try {
            stmt.execute(sql);
            System.out.println("✅ " + columnName + " ajoutée");
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate column")) {
                System.out.println("ℹ️  " + columnName + " existe déjà");
            } else {
                System.err.println("❌ Erreur pour " + columnName + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private static void verifyMigration() {
        System.out.println("4. VÉRIFICATION DE LA MIGRATION:");
        System.out.println("--------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Tester une requête comme celle de l'application
            String testQuery = """
                SELECT id, numero_affaire, date_creation, montant_amende_total, 
                       statut, contrevenant_id, contravention_id, bureau_id, 
                       service_id, created_at, updated_at, created_by, updated_by 
                FROM affaires 
                ORDER BY created_at DESC LIMIT 5
            """;

            try (PreparedStatement stmt = conn.prepareStatement(testQuery);
                 ResultSet rs = stmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    count++;
                }
                System.out.println("✅ Requête de test réussie - " + count + " enregistrements lus");
            }

            // Compter le total
            String countQuery = "SELECT COUNT(*) FROM affaires";
            try (PreparedStatement stmt = conn.prepareStatement(countQuery);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    System.out.println("✅ Total des affaires: " + rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Erreur lors de la vérification: " + e.getMessage());
        }
    }
}