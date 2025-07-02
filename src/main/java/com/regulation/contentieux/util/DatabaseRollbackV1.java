package com.regulation.contentieux.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;

/**
 * Script de rollback pour annuler une migration
 * Restaure la base de données à partir du dernier backup
 */
public class DatabaseRollbackV1 {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseRollbackV1.class);

    public static void main(String[] args) {
        DatabaseRollbackV1 rollback = new DatabaseRollbackV1();
        rollback.execute();
    }

    /**
     * Exécute le rollback
     */
    public void execute() {
        logger.info("🔄 === DÉBUT DU ROLLBACK ===");

        try {
            // 1. Lister les backups disponibles
            File[] backups = listBackups();
            if (backups.length == 0) {
                logger.error("❌ Aucun backup trouvé dans le dossier backup/");
                return;
            }

            // 2. Afficher les backups et demander confirmation
            logger.info("📋 Backups disponibles :");
            for (int i = 0; i < backups.length; i++) {
                logger.info("  {}. {} ({})", i + 1, backups[i].getName(),
                        formatFileSize(backups[i].length()));
            }

            // 3. Sélection du backup
            Scanner scanner = new Scanner(System.in);
            System.out.print("\nSélectionnez le numéro du backup à restaurer (0 pour annuler) : ");
            int choice = scanner.nextInt();

            if (choice == 0) {
                logger.info("Rollback annulé.");
                return;
            }

            if (choice < 1 || choice > backups.length) {
                logger.error("❌ Choix invalide.");
                return;
            }

            File selectedBackup = backups[choice - 1];

            // 4. Confirmation finale
            System.out.print("\n⚠️ ATTENTION : Cette opération va remplacer la base actuelle.\n" +
                    "Voulez-vous vraiment restaurer " + selectedBackup.getName() + " ? (oui/non) : ");
            String confirm = scanner.next();

            if (!confirm.equalsIgnoreCase("oui")) {
                logger.info("Rollback annulé.");
                return;
            }

            // 5. Sauvegarde de la base actuelle avant rollback
            backupCurrentDatabase();

            // 6. Restauration
            restoreBackup(selectedBackup);

            logger.info("✅ Rollback terminé avec succès !");

        } catch (Exception e) {
            logger.error("❌ Erreur pendant le rollback", e);
        }
    }

    /**
     * Liste les backups disponibles
     */
    private File[] listBackups() {
        File backupDir = new File("backup");
        if (!backupDir.exists()) {
            backupDir.mkdir();
            return new File[0];
        }

        File[] files = backupDir.listFiles((dir, name) ->
                name.startsWith("gestion_contentieux_") && name.endsWith(".db"));

        // Trier par date (plus récent en premier)
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::lastModified).reversed());
        }

        return files != null ? files : new File[0];
    }

    /**
     * Sauvegarde la base actuelle avant rollback
     */
    private void backupCurrentDatabase() throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = "backup/gestion_contentieux_before_rollback_" + timestamp + ".db";

        Path source = Paths.get("gestion_contentieux.db");
        Path target = Paths.get(backupName);

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("✅ Sauvegarde de sécurité créée : {}", backupName);
    }

    /**
     * Restaure un backup
     */
    private void restoreBackup(File backup) throws Exception {
        Path source = backup.toPath();
        Path target = Paths.get("gestion_contentieux.db");

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("✅ Base restaurée depuis : {}", backup.getName());
    }

    /**
     * Formate la taille d'un fichier
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }
}