package com.regulation.contentieux.util;

import com.regulation.contentieux.config.DatabaseConfig;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * UTILITAIRE TEMPORAIRE pour diagnostiquer les problèmes de mots de passe
 * À supprimer après résolution du problème
 */
public class PasswordDebugUtil {

    public static void main(String[] args) {
        System.out.println("=== DIAGNOSTIC ET CORRECTION ===\n");

        try {
            // 1. Diagnostic
            showExistingUsers();
            generateTestHashes();

            // 2. CORRECTION IMMÉDIATE
            System.out.println("\n=== CORRECTION EN COURS ===");
            updateAdminPassword("admin");  // Remet le mot de passe à "admin" avec le bon hash

            System.out.println("\n✅ Vous pouvez maintenant vous connecter avec:");
            System.out.println("Username: admin");
            System.out.println("Password: admin");

        } catch (Exception e) {
            System.err.println("Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void showExistingUsers() {
        System.out.println("1. UTILISATEURS EXISTANTS DANS LA BASE:");
        System.out.println("----------------------------------------");

        String sql = "SELECT username, password_hash, nom_complet, role, actif FROM utilisateurs WHERE actif = 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                System.out.println("Username: " + rs.getString("username"));
                System.out.println("Hash stocké: " + rs.getString("password_hash"));
                System.out.println("Nom: " + rs.getString("nom_complet"));
                System.out.println("Rôle: " + rs.getString("role"));
                System.out.println("Actif: " + rs.getInt("actif"));
                System.out.println("---");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la lecture de la base: " + e.getMessage());
        }
    }

    private static void generateTestHashes() {
        System.out.println("\n2. HASH GÉNÉRÉS POUR DIFFÉRENTS MOTS DE PASSE:");
        System.out.println("-----------------------------------------------");

        String[] passwords = {"admin", "password", "123456", "test", "user"};

        for (String pwd : passwords) {
            String hash = hashPassword(pwd);
            System.out.println("Mot de passe: '" + pwd + "'");
            System.out.println("Hash SHA-256: " + hash);
            System.out.println();
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithme SHA-256 non disponible", e);
        }
    }

    /**
     * Méthode pour mettre à jour directement le mot de passe dans la base
     */
    public static void updateAdminPassword(String newPassword) {
        String hash = hashPassword(newPassword);
        String sql = "UPDATE utilisateurs SET password_hash = ? WHERE username = 'admin'";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, hash);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                System.out.println("✅ Mot de passe mis à jour avec succès !");
                System.out.println("Nouveau mot de passe: " + newPassword);
                System.out.println("Hash: " + hash);
            } else {
                System.out.println("❌ Aucun utilisateur 'admin' trouvé pour mise à jour");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la mise à jour: " + e.getMessage());
        }
    }
}