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
 * CORRIGÉ pour correspondre à la vraie structure de la table utilisateurs
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
        // CORRIGÉ : utilise les vraies colonnes de la base
        return """
            INSERT INTO utilisateurs (username, password_hash, nom_complet, 
                                    role, created_at, updated_at) 
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        // CORRIGÉ : utilise les vraies colonnes de la base
        return """
            UPDATE utilisateurs 
            SET username = ?, password_hash = ?, nom_complet = ?, role = ?, 
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        // CORRIGÉ : utilise les vraies colonnes de la base
        return """
            SELECT id, username, password_hash, nom_complet, role,
                   created_at, updated_at
            FROM utilisateurs
            ORDER BY nom_complet
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        // CORRIGÉ : utilise les vraies colonnes de la base
        return """
            SELECT id, username, password_hash, nom_complet, role,
                   created_at, updated_at
            FROM utilisateurs
            WHERE id = ?
        """;
    }

    @Override
    protected Utilisateur mapResultSetToEntity(ResultSet rs) throws SQLException {
        Utilisateur utilisateur = new Utilisateur();

        utilisateur.setId(rs.getLong("id"));

        // MAPPING CORRIGÉ : username -> login dans l'objet Java
        utilisateur.setLogin(rs.getString("username"));

        // MAPPING CORRIGÉ : password_hash -> motDePasse dans l'objet Java
        utilisateur.setMotDePasse(rs.getString("password_hash"));

        // MAPPING CORRIGÉ : nom_complet -> nomComplet, et on peut séparer nom/prenom
        String nomComplet = rs.getString("nom_complet");
        utilisateur.setNomComplet(nomComplet);

        // Séparer nom_complet en nom et prenom (basique)
        if (nomComplet != null && nomComplet.contains(" ")) {
            String[] parts = nomComplet.split(" ", 2);
            utilisateur.setNom(parts[0]);
            utilisateur.setPrenom(parts[1]);
        } else {
            utilisateur.setNom(nomComplet != null ? nomComplet : "");
            utilisateur.setPrenom("");
        }

        // Email par défaut basé sur username
        utilisateur.setEmail(rs.getString("username") + "@system.local");

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

        // Valeurs par défaut
        utilisateur.setActif(true);

        // Gestion des timestamps
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            utilisateur.setDateCreation(createdAt.toLocalDateTime());
            utilisateur.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            utilisateur.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        // Métadonnées par défaut
        utilisateur.setCreatedBy("system");
        utilisateur.setUpdatedBy("system");

        return utilisateur;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Utilisateur utilisateur) throws SQLException {
        // MAPPING CORRIGÉ : login -> username en base
        stmt.setString(1, utilisateur.getLogin());

        // MAPPING CORRIGÉ : motDePasse -> password_hash en base
        stmt.setString(2, utilisateur.getMotDePasse());

        // MAPPING CORRIGÉ : nomComplet -> nom_complet en base
        String nomComplet = utilisateur.getNomComplet();
        if (nomComplet == null || nomComplet.trim().isEmpty()) {
            nomComplet = (utilisateur.getNom() + " " + utilisateur.getPrenom()).trim();
        }
        stmt.setString(3, nomComplet);

        stmt.setString(4, utilisateur.getRole() != null ? utilisateur.getRole().name() : null);
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Utilisateur utilisateur) throws SQLException {
        // MAPPING CORRIGÉ : login -> username en base
        stmt.setString(1, utilisateur.getLogin());

        // MAPPING CORRIGÉ : motDePasse -> password_hash en base
        stmt.setString(2, utilisateur.getMotDePasse());

        // MAPPING CORRIGÉ : nomComplet -> nom_complet en base
        String nomComplet = utilisateur.getNomComplet();
        if (nomComplet == null || nomComplet.trim().isEmpty()) {
            nomComplet = (utilisateur.getNom() + " " + utilisateur.getPrenom()).trim();
        }
        stmt.setString(3, nomComplet);

        stmt.setString(4, utilisateur.getRole() != null ? utilisateur.getRole().name() : null);
        stmt.setLong(5, utilisateur.getId());
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
     * Trouve un utilisateur par son login (username en base)
     */
    public Optional<Utilisateur> findByLogin(String login) {
        // CORRIGÉ : cherche par username au lieu de login
        String sql = """
            SELECT id, username, password_hash, nom_complet, role,
                   created_at, updated_at
            FROM utilisateurs 
            WHERE username = ?
        """;

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
        // CORRIGÉ : cherche par username au lieu de login
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE username = ?";

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
        String sql = """
            SELECT id, username, password_hash, nom_complet, role,
                   created_at, updated_at
            FROM utilisateurs 
            WHERE role = ? 
            ORDER BY nom_complet
        """;
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
        // Note: Si votre table n'a pas de colonne "actif", on considère tous comme actifs
        return findAll();
    }

    /**
     * Recherche d'utilisateurs avec critères
     */
    public List<Utilisateur> searchUtilisateurs(String searchTerm, RoleUtilisateur role,
                                                Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, username, password_hash, nom_complet, role,
                   created_at, updated_at
            FROM utilisateurs WHERE 1=1 
        """);

        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            // CORRIGÉ : cherche dans username et nom_complet
            sql.append("AND (username LIKE ? OR nom_complet LIKE ?) ");
            String searchPattern = "%" + searchTerm.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (role != null) {
            sql.append("AND role = ? ");
            parameters.add(role.name());
        }

        sql.append("ORDER BY nom_complet LIMIT ? OFFSET ?");
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
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM utilisateurs WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append("AND (username LIKE ? OR nom_complet LIKE ?) ");
            String searchPattern = "%" + searchTerm.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (role != null) {
            sql.append("AND role = ? ");
            parameters.add(role.name());
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