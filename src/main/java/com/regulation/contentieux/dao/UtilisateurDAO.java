package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Utilisateur;
import com.regulation.contentieux.model.enums.RoleUtilisateur;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des utilisateurs
 * Hérite d'AbstractSQLiteDAO pour bénéficier des méthodes CRUD de base
 */
public class UtilisateurDAO extends AbstractSQLiteDAO<Utilisateur, Long> {

    private static final Logger logger = LoggerFactory.getLogger(UtilisateurDAO.class);

    @Override
    protected String getTableName() {
        return "utilisateurs";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO utilisateurs (login, mot_de_passe, nom, prenom, nom_complet, 
                                    email, role, actif, date_creation, created_by, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE utilisateurs 
            SET login = ?, mot_de_passe = ?, nom = ?, prenom = ?, nom_complet = ?,
                email = ?, role = ?, actif = ?, derniere_connexion = ?,
                updated_by = ?, updated_at = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, login, mot_de_passe, nom, prenom, nom_complet, email, 
                   role, actif, date_creation, derniere_connexion,
                   created_by, created_at, updated_by, updated_at
            FROM utilisateurs
            ORDER BY nom, prenom
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, login, mot_de_passe, nom, prenom, nom_complet, email, 
                   role, actif, date_creation, derniere_connexion,
                   created_by, created_at, updated_by, updated_at
            FROM utilisateurs
            WHERE id = ?
        """;
    }

    @Override
    protected Utilisateur mapResultSetToEntity(ResultSet rs) throws SQLException {
        Utilisateur utilisateur = new Utilisateur();

        utilisateur.setId(rs.getLong("id"));
        utilisateur.setLogin(rs.getString("login"));
        utilisateur.setMotDePasse(rs.getString("mot_de_passe"));
        utilisateur.setNom(rs.getString("nom"));
        utilisateur.setPrenom(rs.getString("prenom"));
        utilisateur.setNomComplet(rs.getString("nom_complet"));
        utilisateur.setEmail(rs.getString("email"));

        // Conversion du rôle
        String roleStr = rs.getString("role");
        if (roleStr != null) {
            try {
                utilisateur.setRole(RoleUtilisateur.valueOf(roleStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Rôle invalide dans la base: {}", roleStr);
                utilisateur.setRole(RoleUtilisateur.AGENT_CONSULTATION);
            }
        }

        utilisateur.setActif(rs.getBoolean("actif"));

        // Gestion des timestamps
        Timestamp dateCreation = rs.getTimestamp("date_creation");
        if (dateCreation != null) {
            utilisateur.setDateCreation(dateCreation.toLocalDateTime());
        }

        Timestamp derniereConnexion = rs.getTimestamp("derniere_connexion");
        if (derniereConnexion != null) {
            utilisateur.setDerniereConnexion(derniereConnexion.toLocalDateTime());
        }

        // Métadonnées
        utilisateur.setCreatedBy(rs.getString("created_by"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            utilisateur.setCreatedAt(createdAt.toLocalDateTime());
        }

        utilisateur.setUpdatedBy(rs.getString("updated_by"));

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            utilisateur.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return utilisateur;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Utilisateur utilisateur) throws SQLException {
        stmt.setString(1, utilisateur.getLogin());
        stmt.setString(2, utilisateur.getMotDePasse());
        stmt.setString(3, utilisateur.getNom());
        stmt.setString(4, utilisateur.getPrenom());
        stmt.setString(5, utilisateur.getNomComplet());
        stmt.setString(6, utilisateur.getEmail());
        stmt.setString(7, utilisateur.getRole() != null ? utilisateur.getRole().name() : null);
        stmt.setBoolean(8, utilisateur.isActif());
        stmt.setTimestamp(9, Timestamp.valueOf(utilisateur.getDateCreation()));
        stmt.setString(10, utilisateur.getCreatedBy());
        stmt.setTimestamp(11, Timestamp.valueOf(utilisateur.getCreatedAt()));
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Utilisateur utilisateur) throws SQLException {
        stmt.setString(1, utilisateur.getLogin());
        stmt.setString(2, utilisateur.getMotDePasse());
        stmt.setString(3, utilisateur.getNom());
        stmt.setString(4, utilisateur.getPrenom());
        stmt.setString(5, utilisateur.getNomComplet());
        stmt.setString(6, utilisateur.getEmail());
        stmt.setString(7, utilisateur.getRole() != null ? utilisateur.getRole().name() : null);
        stmt.setBoolean(8, utilisateur.isActif());

        if (utilisateur.getDerniereConnexion() != null) {
            stmt.setTimestamp(9, Timestamp.valueOf(utilisateur.getDerniereConnexion()));
        } else {
            stmt.setNull(9, Types.TIMESTAMP);
        }

        stmt.setString(10, utilisateur.getUpdatedBy());

        if (utilisateur.getUpdatedAt() != null) {
            stmt.setTimestamp(11, Timestamp.valueOf(utilisateur.getUpdatedAt()));
        } else {
            stmt.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
        }

        stmt.setLong(12, utilisateur.getId());
    }

    @Override
    protected Long getEntityId(Utilisateur utilisateur) {
        return utilisateur.getId();
    }

    @Override
    protected void setEntityId(Utilisateur utilisateur, Long id) {
        utilisateur.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX UTILISATEURS ==========

    /**
     * Trouve un utilisateur par son login
     */
    public Optional<Utilisateur> findByLogin(String login) {
        String sql = getSelectAllQuery().replace("ORDER BY nom, prenom", "WHERE login = ?");

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, login);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par login: " + login, e);
        }

        return Optional.empty();
    }

    /**
     * Vérifie si un login existe déjà
     */
    public boolean existsByLogin(String login) {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE login = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, login);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification d'existence du login", e);
        }

        return false;
    }

    /**
     * Trouve tous les utilisateurs par rôle
     */
    public List<Utilisateur> findByRole(RoleUtilisateur role) {
        String sql = getSelectAllQuery().replace("ORDER BY nom, prenom",
                "WHERE role = ? ORDER BY nom, prenom");
        List<Utilisateur> utilisateurs = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, role.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                utilisateurs.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par rôle", e);
        }

        return utilisateurs;
    }

    /**
     * Trouve tous les utilisateurs actifs
     */
    public List<Utilisateur> findAllActifs() {
        String sql = getSelectAllQuery().replace("ORDER BY nom, prenom",
                "WHERE actif = true ORDER BY nom, prenom");
        List<Utilisateur> utilisateurs = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                utilisateurs.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des utilisateurs actifs", e);
        }

        return utilisateurs;
    }

    /**
     * Recherche d'utilisateurs avec critères
     */
    public List<Utilisateur> searchUtilisateurs(String searchTerm, RoleUtilisateur role,
                                                Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM utilisateurs WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append("AND (login LIKE ? OR nom LIKE ? OR prenom LIKE ? OR email LIKE ?) ");
            String searchPattern = "%" + searchTerm.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (role != null) {
            sql.append("AND role = ? ");
            parameters.add(role.name());
        }

        if (actifOnly != null && actifOnly) {
            sql.append("AND actif = true ");
        }

        sql.append("ORDER BY nom, prenom LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Utilisateur> utilisateurs = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                utilisateurs.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'utilisateurs", e);
        }

        return utilisateurs;
    }

    /**
     * Compte les utilisateurs selon les critères
     */
    public long countSearchUtilisateurs(String searchTerm, RoleUtilisateur role, Boolean actifOnly) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM utilisateurs WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append("AND (login LIKE ? OR nom LIKE ? OR prenom LIKE ? OR email LIKE ?) ");
            String searchPattern = "%" + searchTerm.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (role != null) {
            sql.append("AND role = ? ");
            parameters.add(role.name());
        }

        if (actifOnly != null && actifOnly) {
            sql.append("AND actif = true ");
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des utilisateurs", e);
        }

        return 0;
    }
}