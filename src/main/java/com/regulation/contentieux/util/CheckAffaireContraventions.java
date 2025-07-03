package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import java.sql.*;
import java.math.BigDecimal;

/**
 * Vérifie la table affaire_contraventions et les montants
 */
public class CheckAffaireContraventions {

    public static void main(String[] args) {
        System.out.println("=== VÉRIFICATION TABLE AFFAIRE_CONTRAVENTIONS ===");
        System.out.println();

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // 1. Vérifier si la table existe
            checkTableExists(conn);

            // 2. Vérifier les données pour les affaires récentes
            checkRecentAffaires(conn);

            // 3. Proposer une solution de réparation
            proposeFixSolution(conn);

        } catch (SQLException e) {
            System.err.println("Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkTableExists(Connection conn) throws SQLException {
        System.out.println("1. VÉRIFICATION DE L'EXISTENCE DE LA TABLE :");
        System.out.println("--------------------------------------------");

        DatabaseMetaData metaData = conn.getMetaData();
        ResultSet tables = metaData.getTables(null, null, "affaire_contraventions", null);

        if (tables.next()) {
            System.out.println("✅ La table affaire_contraventions existe");

            // Vérifier la structure
            ResultSet columns = metaData.getColumns(null, null, "affaire_contraventions", null);
            System.out.println("\nColonnes :");
            while (columns.next()) {
                System.out.printf("   - %s (%s)%n",
                        columns.getString("COLUMN_NAME"),
                        columns.getString("TYPE_NAME"));
            }
        } else {
            System.out.println("❌ La table affaire_contraventions N'EXISTE PAS !");
            System.out.println("   C'est probablement la cause du problème.");
        }
        System.out.println();
    }

    private static void checkRecentAffaires(Connection conn) throws SQLException {
        System.out.println("2. VÉRIFICATION DES AFFAIRES RÉCENTES :");
        System.out.println("---------------------------------------");

        // Vérifier les affaires 2507xxxxx
        String sql = """
            SELECT a.id, a.numero_affaire, a.montant_amende_total, a.montant_total,
                   a.contravention_id, c.libelle, c.montant
            FROM affaires a
            LEFT JOIN contraventions c ON a.contravention_id = c.id
            WHERE a.numero_affaire LIKE '2507%'
            ORDER BY a.id DESC
            LIMIT 5
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            System.out.println("Affaires récentes et leurs contraventions :");
            while (rs.next()) {
                System.out.printf("Affaire %s (ID: %d):%n",
                        rs.getString("numero_affaire"),
                        rs.getLong("id"));
                System.out.printf("  - Montant amende total : %.2f%n",
                        rs.getDouble("montant_amende_total"));
                System.out.printf("  - Montant total : %.2f%n",
                        rs.getDouble("montant_total"));
                System.out.printf("  - Contravention ID : %d%n",
                        rs.getLong("contravention_id"));
                System.out.printf("  - Contravention : %s (Montant base: %.2f)%n",
                        rs.getString("libelle"),
                        rs.getDouble("montant"));
                System.out.println();
            }
        }

        // Vérifier s'il y a des entrées dans affaire_contraventions
        try {
            String checkSql = "SELECT COUNT(*) FROM affaire_contraventions WHERE affaire_id IN (SELECT id FROM affaires WHERE numero_affaire LIKE '2507%')";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    System.out.println("Entrées dans affaire_contraventions pour les affaires 2507: " + count);
                }
            }
        } catch (SQLException e) {
            // La table n'existe probablement pas
            System.out.println("⚠️ Impossible de vérifier affaire_contraventions : " + e.getMessage());
        }
    }

    private static void proposeFixSolution(Connection conn) throws SQLException {
        System.out.println("\n3. SOLUTION PROPOSÉE :");
        System.out.println("----------------------");

        System.out.println("Le problème semble être que :");
        System.out.println("1. La table affaire_contraventions n'existe peut-être pas");
        System.out.println("2. Les montants des contraventions ne sont pas récupérés lors de la création");
        System.out.println("\nPour corriger :");
        System.out.println("1. Créer la table affaire_contraventions si elle n'existe pas");
        System.out.println("2. Mettre à jour les affaires existantes avec les montants des contraventions");

        // Générer un script SQL de correction
        System.out.println("\nScript SQL de correction :");
        System.out.println("```sql");
        System.out.println("-- Mettre à jour les montants depuis les contraventions");
        System.out.println("UPDATE affaires");
        System.out.println("SET montant_amende_total = (");
        System.out.println("    SELECT c.montant FROM contraventions c");
        System.out.println("    WHERE c.id = affaires.contravention_id");
        System.out.println("),");
        System.out.println("montant_total = (");
        System.out.println("    SELECT c.montant FROM contraventions c");
        System.out.println("    WHERE c.id = affaires.contravention_id");
        System.out.println(")");
        System.out.println("WHERE numero_affaire LIKE '2507%'");
        System.out.println("AND (montant_amende_total = 0 OR montant_amende_total IS NULL);");
        System.out.println("```");
    }
}