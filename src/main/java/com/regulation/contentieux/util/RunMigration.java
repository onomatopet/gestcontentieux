package com.regulation.contentieux.util;

import java.util.Scanner;

/**
 * Script simple pour exécuter la migration de base de données
 *
 * INSTRUCTIONS :
 * 1. Assurez-vous que l'application n'est pas en cours d'exécution
 * 2. Exécutez ce script
 * 3. Suivez les instructions à l'écran
 */
public class RunMigration {

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("   MIGRATION BASE DE DONNÉES V1.0");
        System.out.println("=========================================");
        System.out.println();
        System.out.println("Cette migration va :");
        System.out.println("✓ Créer un backup de votre base de données");
        System.out.println("✓ Ajouter les colonnes manquantes");
        System.out.println("✓ Corriger les incohérences");
        System.out.println("✓ Générer un rapport détaillé");
        System.out.println();
        System.out.println("⚠️  IMPORTANT :");
        System.out.println("- Fermez l'application avant de continuer");
        System.out.println("- La migration prend environ 5-10 minutes");
        System.out.println("- Un backup automatique sera créé");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        System.out.print("Voulez-vous continuer ? (oui/non) : ");
        String response = scanner.nextLine();

        if (response.equalsIgnoreCase("oui")) {
            System.out.println("\n🚀 Démarrage de la migration...\n");

            try {
                // Exécuter la migration
                DatabaseMigrationV1 migration = new DatabaseMigrationV1();
                migration.execute();

                System.out.println("\n✅ Migration terminée !");
                System.out.println("Consultez le rapport de migration pour les détails.");

            } catch (Exception e) {
                System.err.println("\n❌ Erreur pendant la migration : " + e.getMessage());
                System.err.println("En cas de problème, utilisez DatabaseRollbackV1 pour restaurer.");
            }

        } else {
            System.out.println("\nMigration annulée.");
        }

        System.out.println("\nAppuyez sur Entrée pour fermer...");
        scanner.nextLine();
    }
}