package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des contraventions - MISE À JOUR POUR COMPATIBILITÉ
 * Compatible avec le modèle Contravention harmonisé
 */
public class ContraventionDAO extends AbstractSQLiteDAO<Contravention, Long> {

    private static final Logger logger = LoggerFactory.getLogger(ContraventionDAO.class);

    @Override
    protected String getTableName() {
        return "contraventions";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO contraventions (code, libelle, description, actif) 
            VALUES (?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE contraventions 
            SET code = ?, libelle = ?, description = ?, actif = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code, libelle, description, actif, created_at 
            FROM contraventions 
            ORDER BY libelle ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code, libelle, description, actif, created_at 
            FROM contraventions 
            WHERE id = ?
        """;
    }

    @Override
    protected Contravention mapResultSetToEntity(ResultSet rs) throws SQLException {
        Contravention contravention = new Contravention();

        contravention.setId(rs.getLong("id"));
        contravention.setCode(rs.getString("code"));
        contravention.setLibelle(rs.getString("libelle"));
        contravention.setDescription(rs.getString("description"));

        // Gestion du boolean actif
        try {
            contravention.setActif(rs.getBoolean("actif"));
        } catch (SQLException e) {
            contravention.setActif(true); // Valeur par défaut
        }

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                contravention.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour la contravention {}", contravention.getId());
            contravention.setCreatedAt(LocalDateTime.now());
        }

        return contravention;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Contravention contravention) throws SQLException {
        stmt.setString(1, contravention.getCode());
        stmt.setString(2, contravention.getLibelle());
        stmt.setString(3, contravention.getDescription());
        stmt.setBoolean(4, contravention.isActif());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Contravention contravention) throws SQLException {
        setInsertParameters(stmt, contravention);
        stmt.setLong(5, contravention.getId());
    }

    @Override
    protected Long getEntityId(Contravention contravention) {
        return contravention.getId();
    }

    @Override
    protected void setEntityId(Contravention contravention, Long id) {
        contravention.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX CONTRAVENTIONS ==========

    /**
     * Trouve une contravention par son code
     */
    public Optional<Contravention> findByCode(String code) {
        String sql = """
            SELECT id, code, libelle, description, actif, created_at 
            FROM contraventions 
            WHERE code = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, code);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code contravention: " + code, e);
        }

        return Optional.empty();
    }

    /**
     * Recherche de contraventions avec critères multiples - POUR ReferentielController
     */
    public List<Contravention> searchContraventions(String libelleOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code, libelle, description, actif, created_at ");
        sql.append("FROM contraventions WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (libelleOuCode != null && !libelleOuCode.trim().isEmpty()) {
            sql.append("AND (libelle LIKE ? OR code LIKE ?) ");
            String searchPattern = "%" + libelleOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND actif = ? ");
            parameters.add(actifOnly);
        }

        sql.append("ORDER BY libelle ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Contravention> contraventions = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                contraventions.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de contraventions", e);
        }

        return contraventions;
    }

    /**
     * Version simplifiée pour compatibilité
     */
    public List<Contravention> searchContraventions(String libelleOuCode, int offset, int limit) {
        return searchContraventions(libelleOuCode, null, offset, limit);
    }

    /**
     * Compte les contraventions correspondant aux critères
     */
    public long countSearchContraventions(String libelleOuCode, Boolean actifOnly) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM contraventions WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (libelleOuCode != null && !libelleOuCode.trim().isEmpty()) {
            sql.append("AND (libelle LIKE ? OR code LIKE ?) ");
            String searchPattern = "%" + libelleOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND actif = ? ");
            parameters.add(actifOnly);
        }

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des contraventions", e);
        }

        return 0;
    }

    /**
     * Version simplifiée pour compatibilité
     */
    public long countSearchContraventions(String libelleOuCode) {
        return countSearchContraventions(libelleOuCode, null);
    }

    /**
     * Trouve toutes les contraventions actives - POUR LES COMBOBOX
     */
    public List<Contravention> findAllActive() {
        String sql = """
            SELECT id, code, libelle, description, actif, created_at 
            FROM contraventions 
            WHERE actif = 1
            ORDER BY libelle ASC
        """;

        List<Contravention> contraventions = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                contraventions.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des contraventions actives", e);
        }

        return contraventions;
    }

    /**
     * Génère le prochain code contravention selon le format CVNNN
     */
    public String generateNextCodeContravention() {
        String prefix = "CV";

        String sql = """
            SELECT code FROM contraventions 
            WHERE code LIKE ? 
            ORDER BY code DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("code");
                return generateNextCodeFromLast(lastCode, prefix);
            } else {
                return prefix + "001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code contravention", e);
            return prefix + "001";
        }
    }

    private String generateNextCodeFromLast(String lastCode, String prefix) {
        try {
            if (lastCode != null && lastCode.startsWith(prefix) && lastCode.length() == 5) {
                String numericPart = lastCode.substring(2);
                int lastNumber = Integer.parseInt(numericPart);
                return prefix + String.format("%03d", lastNumber + 1);
            }
            return prefix + "001";
        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier code contravention: {}", lastCode, e);
            return prefix + "001";
        }
    }

    /**
     * Vérifie si un code contravention existe déjà
     */
    public boolean existsByCode(String code) {
        String sql = "SELECT 1 FROM contraventions WHERE code = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, code);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code contravention", e);
            return false;
        }
    }
}