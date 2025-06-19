package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Bureau;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des bureaux - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la structure des autres DAOs
 */
public class BureauDAO extends AbstractSQLiteDAO<Bureau, Long> {

    private static final Logger logger = LoggerFactory.getLogger(BureauDAO.class);

    @Override
    protected String getTableName() {
        return "bureaux";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO bureaux (code_bureau, nom_bureau) 
            VALUES (?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE bureaux 
            SET code_bureau = ?, nom_bureau = ? 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_bureau, nom_bureau, created_at 
            FROM bureaux 
            ORDER BY nom_bureau ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_bureau, nom_bureau, created_at 
            FROM bureaux 
            WHERE id = ?
        """;
    }

    @Override
    protected Bureau mapResultSetToEntity(ResultSet rs) throws SQLException {
        Bureau bureau = new Bureau();

        bureau.setId(rs.getLong("id"));
        bureau.setCodeBureau(rs.getString("code_bureau"));
        bureau.setNomBureau(rs.getString("nom_bureau"));

        // Gestion des timestamps - COMME DANS LES AUTRES DAOs
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                bureau.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour le bureau {}", bureau.getId());
            bureau.setCreatedAt(LocalDateTime.now());
        }

        return bureau;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Bureau bureau) throws SQLException {
        stmt.setString(1, bureau.getCodeBureau());
        stmt.setString(2, bureau.getNomBureau());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Bureau bureau) throws SQLException {
        stmt.setString(1, bureau.getCodeBureau());
        stmt.setString(2, bureau.getNomBureau());
        stmt.setLong(3, bureau.getId());
    }

    @Override
    protected Long getEntityId(Bureau bureau) {
        return bureau.getId();
    }

    @Override
    protected void setEntityId(Bureau bureau, Long id) {
        bureau.setId(id);
    }

    // Méthodes spécifiques aux bureaux

    /**
     * Trouve un bureau par son code
     */
    public Optional<Bureau> findByCodeBureau(String codeBureau) {
        String sql = """
            SELECT id, code_bureau, nom_bureau, created_at 
            FROM bureaux 
            WHERE code_bureau = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeBureau);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code bureau: " + codeBureau, e);
        }

        return Optional.empty();
    }

    /**
     * Recherche de bureaux avec critères multiples
     */
    public List<Bureau> searchBureaux(String nomOuCode, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_bureau, nom_bureau, created_at ");
        sql.append("FROM bureaux WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_bureau LIKE ? OR code_bureau LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        sql.append("ORDER BY nom_bureau ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Bureau> bureaux = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                bureaux.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de bureaux", e);
        }

        return bureaux;
    }

    /**
     * Compte les bureaux correspondant aux critères
     */
    public long countSearchBureaux(String nomOuCode) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM bureaux WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_bureau LIKE ? OR code_bureau LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
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
            logger.error("Erreur lors du comptage des bureaux", e);
        }

        return 0;
    }

    /**
     * Génère le prochain code bureau selon le format BURNNN
     */
    public String generateNextCodeBureau() {
        String prefix = "BUR";

        String sql = """
            SELECT code_bureau FROM bureaux 
            WHERE code_bureau LIKE ? 
            ORDER BY code_bureau DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("code_bureau");
                return generateNextCodeFromLast(lastCode, prefix);
            } else {
                return prefix + "01";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code bureau", e);
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
            logger.warn("Erreur lors du parsing du dernier code bureau: {}", lastCode, e);
            return prefix + "01";
        }
    }

    /**
     * Vérifie si un code bureau existe déjà
     */
    public boolean existsByCodeBureau(String codeBureau) {
        String sql = "SELECT 1 FROM bureaux WHERE code_bureau = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeBureau);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code bureau", e);
            return false;
        }
    }
}