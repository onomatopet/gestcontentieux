package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import java.sql.*;
import java.math.BigDecimal;

/**
 * Outil de diagnostic pour identifier les problèmes de montants
 */
public class DatabaseDiagnosticTool {

    public static void main(String[] args) {
        System.out.println("=== DIAGNOSTIC BASE DE DONNÉES ===");
        System.out.println();

        try (Connection conn = DatabaseConfig.getSQLiteConnection()) {
            // 1. Vérifier la structure de la table affaires
            checkTableStructure(conn);

            // 2. Vérifier quelques enregistrements
            checkSampleData(conn);

            // 3. Vérifier les valeurs des montants
            checkMontantValues(conn);

            // 4. Tester la requête complète du DAO
            testDAOQuery(conn);

        } catch (SQLException e) {
            System.err.println("Erreur de connexion : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkTableStructure(Connection conn) throws SQLException {
        System.out.println("1. STRUCTURE DE LA TABLE AFFAIRES :");
        System.out.println("-----------------------------------");

        DatabaseMetaData metaData = conn.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, "affaires", null);

        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            System.out.printf("   - %-25s : %s%n", columnName, dataType);
        }
        System.out.println();
    }

    private static void checkSampleData(Connection conn) throws SQLException {
        System.out.println("2. ÉCHANTILLON DE DONNÉES :");
        System.out.println("---------------------------");

        String sql = "SELECT id, numero_affaire, montant_amende_total, statut FROM affaires LIMIT 5";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                System.out.printf("   ID: %d, Numéro: %s, Montant: %.2f, Statut: %s%n",
                        rs.getLong("id"),
                        rs.getString("numero_affaire"),
                        rs.getDouble("montant_amende_total"),
                        rs.getString("statut")
                );
            }
        }
        System.out.println();
    }

    private static void checkMontantValues(Connection conn) throws SQLException {
        System.out.println("3. ANALYSE DES MONTANTS :");
        System.out.println("-------------------------");

        // Compter les affaires avec montant = 0
        String sql1 = "SELECT COUNT(*) FROM affaires WHERE montant_amende_total = 0 OR montant_amende_total IS NULL";
        try (PreparedStatement stmt = conn.prepareStatement(sql1);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.println("   Affaires avec montant = 0 ou NULL : " + rs.getInt(1));
            }
        }

        // Compter les affaires avec montant > 0
        String sql2 = "SELECT COUNT(*) FROM affaires WHERE montant_amende_total > 0";
        try (PreparedStatement stmt = conn.prepareStatement(sql2);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.println("   Affaires avec montant > 0 : " + rs.getInt(1));
            }
        }

        // Montant moyen
        String sql3 = "SELECT AVG(montant_amende_total) FROM affaires WHERE montant_amende_total > 0";
        try (PreparedStatement stmt = conn.prepareStatement(sql3);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                System.out.printf("   Montant moyen (sans les 0) : %.2f%n", rs.getDouble(1));
            }
        }
        System.out.println();
    }

    private static void testDAOQuery(Connection conn) throws SQLException {
        System.out.println("4. TEST REQUÊTE DAO :");
        System.out.println("--------------------");

        String sql = """
            SELECT a.id, a.numero_affaire, a.date_creation, a.montant_amende_total, 
                   a.statut, a.contrevenant_id, a.contravention_id, a.bureau_id, 
                   a.service_id, a.created_at, a.updated_at,
                   c.nom_complet as contrevenant_nom_complet,
                   cv.libelle as contravention_libelle,
                   b.nom_bureau as bureau_nom,
                   s.nom_service as service_nom
            FROM affaires a
            LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
            LEFT JOIN contraventions cv ON a.contravention_id = cv.id
            LEFT JOIN bureaux b ON a.bureau_id = b.id
            LEFT JOIN services s ON a.service_id = s.id
            ORDER BY a.date_creation DESC
            LIMIT 3
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                count++;
                BigDecimal montant = rs.getBigDecimal("montant_amende_total");
                System.out.printf("   Affaire %d : %s, Montant DB: %s%n",
                        count,
                        rs.getString("numero_affaire"),
                        montant != null ? montant.toString() : "NULL"
                );
            }

            if (count == 0) {
                System.out.println("   AUCUNE AFFAIRE TROUVÉE !");
            }
        }
    }
}