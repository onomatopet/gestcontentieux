package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des centres - HARMONISÉ
 * Compatible avec le modèle Centre harmonisé
 */
public class CentreDAO extends AbstractSQLiteDAO<Centre, Long> {

    private static final Logger logger = LoggerFactory.getLogger(CentreDAO.class);

    @Override
    protected String getTableName() {
        return "centres";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO centres (code_centre, nom_centre, description, actif) 
            VALUES (?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE centres 
            SET code_centre = ?, nom_centre = ?, description = ?, actif = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_centre, nom_centre, description, actif, created_at 
            FROM centres 
            ORDER BY nom_centre ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_centre, nom_centre, description, actif, created_at 
            FROM centres 
            WHERE id = ?
        """;
    }

    @Override
    protected Centre mapResultSetToEntity(ResultSet rs) throws SQLException {
        Centre centre = new Centre();

        centre.setId(rs.getLong("id"));
        centre.setCodeCentre(rs.getString("code_centre"));
        centre.setNomCentre(rs.getString("nom_centre"));
        centre.setDescription(rs.getString("description"));

        // Gestion du boolean actif
        try {
            centre.setActif(rs.getBoolean("actif"));
        } catch (SQLException e) {
            centre.setActif(true); // Valeur par défaut
        }

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                centre.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour le centre {}", centre.getId());
            centre.setCreatedAt(LocalDateTime.now());
        }

        return centre;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Centre centre) throws SQLException {
        stmt.setString(1, centre.getCodeCentre());
        stmt.setString(2, centre.getNomCentre());
        stmt.setString(3, centre.getDescription());
        stmt.setBoolean(4, centre.isActif());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Centre centre) throws SQLException {
        setInsertParameters(stmt, centre);
        stmt.setLong(5, centre.getId());
    }

    @Override
    protected Long getEntityId(Centre centre) {
        return centre.getId();
    }

    @Override
    protected void setEntityId(Centre centre, Long id) {
        centre.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX CENTRES ==========

    /**
     * Trouve un centre par son code - COMPATIBLE AVEC ReferentielController
     */
    public Optional<Centre> findByCode(String code) {
        return findByCodeCentre(code);
    }

    /**
     * Trouve un centre par son code centre
     */
    public Optional<Centre> findByCodeCentre(String codeCentre) {
        String sql = """
            SELECT id, code_centre, nom_centre, description, actif, created_at 
            FROM centres 
            WHERE code_centre = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeCentre);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code centre: " + codeCentre, e);
        }

        return Optional.empty();
    }

    /**
     * Recherche de centres avec critères multiples - POUR ReferentielController
     */
    public List<Centre> searchCentres(String nomOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_centre, nom_centre, description, actif, created_at ");
        sql.append("FROM centres WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_centre LIKE ? OR code_centre LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND actif = ? ");
            parameters.add(actifOnly);
        }

        sql.append("ORDER BY nom_centre ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Centre> centres = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                centres.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de centres", e);
        }

        return centres;
    }

    /**
     * Trouve tous les centres actifs - POUR LES COMBOBOX
     */
    public List<Centre> findAllActive() {
        String sql = """
            SELECT id, code_centre, nom_centre, description, actif, created_at 
            FROM centres 
            WHERE actif = 1
            ORDER BY nom_centre ASC
        """;

        List<Centre> centres = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                centres.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des centres actifs", e);
        }

        return centres;
    }

    /**
     * Génère le prochain code centre selon le format CTRNNN
     */
    public String generateNextCodeCentre() {
        String prefix = "CTR";

        String sql = """
            SELECT code_centre FROM centres 
            WHERE code_centre LIKE ? 
            ORDER BY code_centre DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("code_centre");
                return generateNextCodeFromLast(lastCode, prefix);
            } else {
                return prefix + "01";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code centre", e);
            return prefix + "01";
        }
    }

    private String generateNextCodeFromLast(String lastCode, String prefix) {
        try {
            if (lastCode != null && lastCode.startsWith(prefix) && lastCode.length() == 5) {
                String numericPart = lastCode.substring(3);
                int lastNumber = Integer.parseInt(numericPart);
                return prefix + String.format("%02d", lastNumber + 1);
            }
            return prefix + "01";
        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier code centre: {}", lastCode, e);
            return prefix + "01";
        }
    }

    /**
     * Vérifie si un code centre existe déjà
     */
    public boolean existsByCodeCentre(String codeCentre) {
        String sql = "SELECT 1 FROM centres WHERE code_centre = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeCentre);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code centre", e);
            return false;
        }
    }
}