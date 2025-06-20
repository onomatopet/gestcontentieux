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
 * DAO pour la gestion des banques - HARMONISÉ
 * Compatible avec le modèle Banque harmonisé
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
            INSERT INTO banques (code_banque, nom_banque, description, actif) 
            VALUES (?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE banques 
            SET code_banque = ?, nom_banque = ?, description = ?, actif = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_banque, nom_banque, description, actif, created_at 
            FROM banques 
            ORDER BY nom_banque ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_banque, nom_banque, description, actif, created_at 
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
        banque.setDescription(rs.getString("description"));

        // Gestion du boolean actif
        try {
            banque.setActif(rs.getBoolean("actif"));
        } catch (SQLException e) {
            banque.setActif(true); // Valeur par défaut
        }

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
        stmt.setString(3, banque.getDescription());
        stmt.setBoolean(4, banque.isActif());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Banque banque) throws SQLException {
        setInsertParameters(stmt, banque);
        stmt.setLong(5, banque.getId());
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
     * Trouve une banque par son code - COMPATIBLE AVEC ReferentielController
     */
    public Optional<Banque> findByCode(String code) {
        return findByCodeBanque(code);
    }

    /**
     * Trouve une banque par son code banque
     */
    public Optional<Banque> findByCodeBanque(String codeBanque) {
        String sql = """
            SELECT id, code_banque, nom_banque, description, actif, created_at 
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
     * Recherche de banques avec critères multiples - POUR ReferentielController
     */
    public List<Banque> searchBanques(String nomOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_banque, nom_banque, description, actif, created_at ");
        sql.append("FROM banques WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_banque LIKE ? OR code_banque LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND actif = ? ");
            parameters.add(actifOnly);
        }

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
     * Trouve toutes les banques actives - POUR LES COMBOBOX
     */
    public List<Banque> findAllActive() {
        String sql = """
            SELECT id, code_banque, nom_banque, description, actif, created_at 
            FROM banques 
            WHERE actif = 1
            ORDER BY nom_banque ASC
        """;

        List<Banque> banques = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                banques.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des banques actives", e);
        }

        return banques;
    }

    /**
     * Génère le prochain code banque selon le format BQNNN
     */
    public String generateNextCodeBanque() {
        String prefix = "BQ";

        String sql = """
            SELECT code_banque FROM banques 
            WHERE code_banque LIKE ? 
            ORDER BY code_banque DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("code_banque");
                return generateNextCodeFromLast(lastCode, prefix);
            } else {
                return prefix + "001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code banque", e);
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
            logger.warn("Erreur lors du parsing du dernier code banque: {}", lastCode, e);
            return prefix + "001";
        }
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
}