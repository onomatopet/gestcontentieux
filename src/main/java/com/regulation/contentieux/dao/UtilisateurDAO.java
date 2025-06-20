package com.regulation.contentieux.dao;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des utilisateurs
 */
public class UtilisateurDAO {
    private static final Logger logger = LoggerFactory.getLogger(UtilisateurDAO.class);

    /**
     * Trouve un utilisateur par son nom d'utilisateur
     */
    public Optional<Utilisateur> findByUsername(String username) {
        String sql = """
            SELECT id, username, password_hash, nom_complet, role, 
                   created_at, updated_at, last_login_at, actif 
            FROM utilisateurs 
            WHERE username = ? AND actif = 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToUtilisateur(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de l'utilisateur: " + username, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve un utilisateur par son ID
     */
    public Optional<Utilisateur> findById(Long id) {
        String sql = """
            SELECT id, username, password_hash, nom_complet, role, 
                   created_at, updated_at, last_login_at, actif 
            FROM utilisateurs 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToUtilisateur(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de l'utilisateur ID: " + id, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve tous les utilisateurs actifs
     */
    findAll

    /**
     * Sauvegarde un nouvel utilisateur
     */
    public Utilisateur save(Utilisateur utilisateur) {
        String sql = """
            INSERT INTO utilisateurs (username, password_hash, nom_complet, role, actif) 
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, utilisateur.getUsername());
            pstmt.setString(2, utilisateur.getPasswordHash());
            pstmt.setString(3, utilisateur.getNomComplet());
            pstmt.setString(4, utilisateur.getRole().name());
            pstmt.setBoolean(5, utilisateur.isActif());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        utilisateur.setId(generatedKeys.getLong(1));
                    }
                }
            }

            logger.info("Utilisateur créé: {}", utilisateur.getUsername());
            return utilisateur;

        } catch (SQLException e) {
            logger.error("Erreur lors de la création de l'utilisateur: " + utilisateur.getUsername(), e);
            throw new RuntimeException("Impossible de créer l'utilisateur", e);
        }
    }

    /**
     * Met à jour un utilisateur existant
     */
    public Utilisateur update(Utilisateur utilisateur) {
        String sql = """
            UPDATE utilisateurs 
            SET password_hash = ?, nom_complet = ?, role = ?, actif = ?, 
                last_login_at = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, utilisateur.getPasswordHash());
            pstmt.setString(2, utilisateur.getNomComplet());
            pstmt.setString(3, utilisateur.getRole().name());
            pstmt.setBoolean(4, utilisateur.isActif());

            if (utilisateur.getLastLoginAt() != null) {
                pstmt.setTimestamp(5, Timestamp.valueOf(utilisateur.getLastLoginAt()));
            } else {
                pstmt.setNull(5, Types.TIMESTAMP);
            }

            pstmt.setLong(6, utilisateur.getId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Utilisateur mis à jour: {}", utilisateur.getUsername());
                return utilisateur;
            } else {
                throw new RuntimeException("Aucun utilisateur trouvé avec l'ID: " + utilisateur.getId());
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour de l'utilisateur: " + utilisateur.getUsername(), e);
            throw new RuntimeException("Impossible de mettre à jour l'utilisateur", e);
        }
    }

    /**
     * Supprime (désactive) un utilisateur
     */
    public void delete(Long id) {
        String sql = "UPDATE utilisateurs SET actif = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Utilisateur désactivé: ID {}", id);
            } else {
                throw new RuntimeException("Aucun utilisateur trouvé avec l'ID: " + id);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression de l'utilisateur ID: " + id, e);
            throw new RuntimeException("Impossible de supprimer l'utilisateur", e);
        }
    }

    /**
     * Vérifie si un nom d'utilisateur existe déjà
     */
    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE username = ? AND actif = 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification d'existence du username: " + username, e);
        }

        return false;
    }

    /**
     * Met à jour la dernière connexion d'un utilisateur
     */
    public void updateLastLogin(Long userId) {
        String sql = "UPDATE utilisateurs SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.executeUpdate();

            logger.debug("Dernière connexion mise à jour pour l'utilisateur ID: {}", userId);

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour de la dernière connexion: " + userId, e);
        }
    }

    /**
     * Mappe un ResultSet vers un objet Utilisateur
     */
    private Utilisateur mapResultSetToUtilisateur(ResultSet rs) throws SQLException {
        Utilisateur utilisateur = new Utilisateur();

        utilisateur.setId(rs.getLong("id"));
        utilisateur.setUsername(rs.getString("username"));
        utilisateur.setPasswordHash(rs.getString("password_hash"));
        utilisateur.setNomComplet(rs.getString("nom_complet"));
        utilisateur.setRole(RoleUtilisateur.valueOf(rs.getString("role")));
        utilisateur.setActif(rs.getBoolean("actif"));

        // Gestion des timestamps nullable
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            utilisateur.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            utilisateur.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        Timestamp lastLoginAt = rs.getTimestamp("last_login_at");
        if (lastLoginAt != null) {
            utilisateur.setLastLoginAt(lastLoginAt.toLocalDateTime());
        }

        return utilisateur;
    }

    /**
     * Trouve un utilisateur par son login
     */
    public Utilisateur findByLogin(String login) {
        String query = "SELECT * FROM utilisateurs WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, login);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToEntity(rs);
            }
            return null;
        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par login: {}", login, e);
            return null;
        }
    }

    /**
     * Supprime un utilisateur par son login
     */
    public boolean deleteByLogin(String login) {
        String query = "DELETE FROM utilisateurs WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, login);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Erreur lors de la suppression par login: {}", login, e);
            return false;
        }
    }

    /**
     * Trouve tous les utilisateurs
     */
    public List<Utilisateur> findAll() {
        String query = "SELECT * FROM utilisateurs ORDER BY nom, prenom";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            ResultSet rs = stmt.executeQuery();
            List<Utilisateur> utilisateurs = new ArrayList<>();

            while (rs.next()) {
                utilisateurs.add(mapResultSetToEntity(rs));
            }

            return utilisateurs;
        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération de tous les utilisateurs", e);
            return new ArrayList<>();
        }
    }
}