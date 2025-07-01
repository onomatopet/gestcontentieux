package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Banque;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des banques - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la structure des autres DAOs
 */
public class BanqueDAO extends AbstractSQLiteDAO<Banque, Long> {

    private static final Logger logger = LoggerFactory.getLogger(BanqueDAO.class);

    @Override
    protected String getTableName() {
        return "banques";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO banques (code_banque, nom_banque) 
            VALUES (?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE banques 
            SET code_banque = ?, nom_banque = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_banque, nom_banque, created_at 
            FROM banques 
            ORDER BY nom_banque ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_banque, nom_banque, created_at 
            FROM banques 
            WHERE id = ?
        """;
    }

    @Override
    protected Banque mapResultSetToEntity(ResultSet rs) throws SQLException {
        Banque banque = new Banque();

        banque.setId(rs.getLong("id"));
        banque.setCodeBanque(rs.getString("code_banque"));
        banque.setNomBanque(rs.getString("nom_banque"));

        // Les colonnes description, adresse, telephone, email n'existent pas dans la table
        banque.setDescription("");  // Valeur par défaut
        banque.setAdresse("");      // Valeur par défaut
        banque.setTelephone("");    // Valeur par défaut
        banque.setEmail("");        // Valeur par défaut
        banque.setActif(true);      // Valeur par défaut

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                banque.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour la banque {}", banque.getId());
            banque.setCreatedAt(LocalDateTime.now());
        }

        return banque;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Banque banque) throws SQLException {
        stmt.setString(1, banque.getCodeBanque());
        stmt.setString(2, banque.getNomBanque());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Banque banque) throws SQLException {
        stmt.setString(1, banque.getCodeBanque());
        stmt.setString(2, banque.getNomBanque());
        stmt.setLong(3, banque.getId());
    }

    @Override
    protected Long getEntityId(Banque banque) {
        return banque.getId();
    }

    @Override
    protected void setEntityId(Banque banque, Long id) {
        banque.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX BANQUES ==========

    /**
     * Trouve une banque par son code
     */
    public Optional<Banque> findByCodeBanque(String codeBanque) {
        String sql = """
            SELECT id, code_banque, nom_banque, created_at 
            FROM banques 
            WHERE code_banque = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeBanque);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code banque: " + codeBanque, e);
        }

        return Optional.empty();
    }

    /**
     * Recherche de banques avec critères multiples
     */
    public List<Banque> searchBanques(String nomOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_banque, nom_banque, created_at ");
        sql.append("FROM banques WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_banque LIKE ? OR code_banque LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        // Ignorer actifOnly car la colonne n'existe pas

        sql.append("ORDER BY nom_banque ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Banque> banques = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                banques.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de banques", e);
        }

        return banques;
    }

    /**
     * Trouve toutes les banques actives
     * Comme il n'y a pas de colonne actif, on retourne toutes les banques
     */
    public List<Banque> findAllActive() {
        return findAll();
    }

    /**
     * Vérifie si un code banque existe déjà
     */
    public boolean existsByCodeBanque(String codeBanque) {
        String sql = "SELECT 1 FROM banques WHERE code_banque = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeBanque);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code banque", e);
            return false;
        }
    }

    /**
     * Récupère les banques actives uniquement
     * Comme il n'y a pas de colonne actif, on retourne toutes les banques
     */
    public List<Banque> findActiveBanques() {
        return findAll();
    }
}