package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.File;

/**
 * Outil de diagnostic pour vérifier quelle base de données est utilisée
 * VERSION CORRIGÉE - Utilise les vraies colonnes de la BDD
 */
public class DatabaseDiagnosticTool {

    public static void main(String[] args) {
        System.out.println("=== DIAGNOSTIC DE LA BASE DE DONNÉES ===\n");

        try {
            // 1. Vérifier le chemin de la base de données
            checkDatabasePath();

            // 2. Vérifier le contenu de la base
            checkDatabaseContent();

            // 3. Examiner la structure de la table affaires
            examineAffairesTable();

            // 4. Tester la requête de recherche CORRIGÉE
            testSearchQuery();

        } catch (Exception e) {
            System.err.println("Erreur lors du diagnostic: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkDatabasePath() {
        System.out.println("1. VÉRIFICATION DU CHEMIN DE LA BASE:");
        System.out.println("------------------------------------");

        // Chemin actuel de travail
        String workingDir = System.getProperty("user.dir");
        System.out.println("Répertoire de travail: " + workingDir);

        // Vérifier les fichiers .db dans le répertoire
        File currentDir = new File(workingDir);
        File[] dbFiles = currentDir.listFiles((dir, name) -> name.endsWith(".db"));

        System.out.println("Fichiers .db trouvés:");
        if (dbFiles != null) {
            for (File file : dbFiles) {
                System.out.println("  - " + file.getName() + " (" + (file.length() / (1024 * 1024)) + " MB)");
            }
        } else {
            System.out.println("  Aucun fichier .db trouvé");
        }

        // Vérifier le fichier spécifique
        File targetDb = new File("gestion_contentieux.db");
        System.out.println("gestion_contentieux.db existe: " + targetDb.exists());
        if (targetDb.exists()) {
            System.out.println("Taille: " + (targetDb.length() / (1024 * 1024)) + " MB");
        }

        System.out.println();
    }

    private static void checkDatabaseContent() {
        System.out.println("2. CONTENU DE LA BASE DE DONNÉES:");
        System.out.println("---------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Lister toutes les tables
            String tablesQuery = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name";
            try (PreparedStatement stmt = conn.prepareStatement(tablesQuery);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("Tables trouvées:");
                while (rs.next()) {
                    String tableName = rs.getString("name");
                    int count = getTableCount(conn, tableName);
                    System.out.println("  - " + tableName + ": " + count + " enregistrements");
                }
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la vérification du contenu: " + e.getMessage());
        }

        System.out.println();
    }

    private static int getTableCount(Connection conn, String tableName) {
        try {
            String countQuery = "SELECT COUNT(*) FROM " + tableName;
            try (PreparedStatement stmt = conn.prepareStatement(countQuery);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors du comptage de " + tableName + ": " + e.getMessage());
        }
        return 0;
    }

    private static void examineAffairesTable() {
        System.out.println("3. STRUCTURE DE LA TABLE AFFAIRES:");
        System.out.println("----------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Structure de la table
            String structureQuery = "PRAGMA table_info(affaires)";
            try (PreparedStatement stmt = conn.prepareStatement(structureQuery);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("Colonnes de la table affaires:");
                while (rs.next()) {
                    System.out.println("  - " + rs.getString("name") +
                            " (" + rs.getString("type") +
                            ", nullable: " + (rs.getInt("notnull") == 0) + ")");
                }
            }

            // Quelques exemples de données
            String sampleQuery = "SELECT id, numero_affaire, date_creation, statut FROM affaires LIMIT 5";
            try (PreparedStatement stmt = conn.prepareStatement(sampleQuery);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("\nPremiers enregistrements:");
                while (rs.next()) {
                    System.out.println("  ID: " + rs.getLong("id") +
                            ", Numéro: " + rs.getString("numero_affaire") +
                            ", Date: " + rs.getString("date_creation") +
                            ", Statut: " + rs.getString("statut"));
                }
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de l'examen de la table affaires: " + e.getMessage());
        }

        System.out.println();
    }

    private static void testSearchQuery() {
        System.out.println("4. TEST DE LA REQUÊTE DE RECHERCHE CORRIGÉE:");
        System.out.println("--------------------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // CORRECTION: Requête avec les VRAIES colonnes de la BDD
            String searchQuery = """
                SELECT id, numero_affaire, date_creation, montant_amende_total, 
                       statut, contrevenant_id, contravention_id, bureau_id, service_id
                FROM affaires WHERE 1=1 
                ORDER BY date_creation DESC LIMIT ? OFFSET ?
            """;

            try (PreparedStatement stmt = conn.prepareStatement(searchQuery)) {
                stmt.setInt(1, 25); // limit
                stmt.setInt(2, 0);  // offset

                try (ResultSet rs = stmt.executeQuery()) {
                    int count = 0;
                    System.out.println("✅ Résultats de la requête de recherche:");
                    while (rs.next()) {
                        count++;
                        System.out.println("  " + count + ". Affaire " + rs.getString("numero_affaire") +
                                " - Statut: " + rs.getString("statut") +
                                " - Montant: " + rs.getDouble("montant_amende_total"));

                        if (count >= 3) { // Limiter l'affichage
                            System.out.println("  ... (et " + (rs.getRow() > 0 ? "plus d'enregistrements" : "0 autres") + ")");
                            break;
                        }
                    }

                    if (count == 0) {
                        System.out.println("  Aucun résultat trouvé");
                    } else {
                        System.out.println("  Total affiché: " + count + " enregistrements");
                    }
                }

            } catch (SQLException e) {
                System.err.println("❌ Erreur lors du test de la requête: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (SQLException e) {
            System.err.println("❌ Erreur de connexion: " + e.getMessage());
        }

        System.out.println();
    }

    /**
     * NOUVEAU: Test pour vérifier les vraies colonnes disponibles
     */
    private static void listAllColumns() {
        System.out.println("5. COLONNES DISPONIBLES DANS CHAQUE TABLE:");
        System.out.println("------------------------------------------");

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // Obtenir toutes les tables
            String tablesQuery = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name";
            try (PreparedStatement stmt = conn.prepareStatement(tablesQuery);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String tableName = rs.getString("name");
                    System.out.println("\nTable: " + tableName);

                    // Lister les colonnes
                    String columnsQuery = "PRAGMA table_info(" + tableName + ")";
                    try (PreparedStatement columnStmt = conn.prepareStatement(columnsQuery);
                         ResultSet columnRs = columnStmt.executeQuery()) {

                        System.out.print("  Colonnes: ");
                        boolean first = true;
                        while (columnRs.next()) {
                            if (!first) System.out.print(", ");
                            System.out.print(columnRs.getString("name"));
                            first = false;
                        }
                        System.out.println();
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la liste des colonnes: " + e.getMessage());
        }
    }
}