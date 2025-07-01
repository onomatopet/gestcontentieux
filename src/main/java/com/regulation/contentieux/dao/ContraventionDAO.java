package com.regulation.contentieux.dao;

import java.util.stream.Collectors;
import java.util.Objects;
import java.sql.Timestamp;
import java.math.BigDecimal;

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
            INSERT INTO contraventions (code, libelle, description) 
            VALUES (?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE contraventions 
            SET code = ?, libelle = ?, description = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code, libelle, description, created_at 
            FROM contraventions 
            ORDER BY libelle ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code, libelle, description, created_at 
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

        // La colonne actif n'existe pas dans la table, on met true par défaut
        contravention.setActif(true);

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
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Contravention contravention) throws SQLException {
        stmt.setString(1, contravention.getCode());
        stmt.setString(2, contravention.getLibelle());
        stmt.setString(3, contravention.getDescription());
        stmt.setLong(4, contravention.getId());
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
            SELECT id, code, libelle, description, created_at 
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
        sql.append("SELECT id, code, libelle, description, created_at ");
        sql.append("FROM contraventions WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (libelleOuCode != null && !libelleOuCode.trim().isEmpty()) {
            sql.append("AND (libelle LIKE ? OR code LIKE ?) ");
            String searchPattern = "%" + libelleOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        // Ignorer le paramètre actifOnly car la colonne n'existe pas

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
     * CORRECTION BUG : Méthode manquante findByAffaireId
     * Trouve toutes les contraventions associées à une affaire
     */
    public List<Contravention> findByAffaireId(Long affaireId) {
        if (affaireId == null) {
            return new ArrayList<>();
        }

        String sql = """
        SELECT c.id, c.code, c.libelle, c.description, 
               c.created_at,
               ac.montant_applique
        FROM contraventions c
        INNER JOIN affaire_contraventions ac ON c.id = ac.contravention_id
        WHERE ac.affaire_id = ?
        ORDER BY c.libelle ASC
        """;

        List<Contravention> contraventions = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Contravention contravention = mapResultSetToEntity(rs);
                // Si on veut stocker le montant appliqué quelque part
                contraventions.add(contravention);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des contraventions pour l'affaire: " + affaireId, e);
        }

        return contraventions;
    }

    /**
     * Compte les contraventions selon les critères
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
     * Comme il n'y a pas de colonne actif, on retourne toutes les contraventions
     */
    public List<Contravention> findAllActive() {
        String sql = """
            SELECT id, code, libelle, description, created_at 
            FROM contraventions 
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

    public List<Contravention> findAllActives() {
        // Comme il n'y a pas de colonne actif, on retourne toutes les contraventions
        String sql = "SELECT * FROM contraventions ORDER BY libelle";
        List<Contravention> contraventions = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                contraventions.add(mapResultSetToEntity(rs));
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des contraventions actives", e);
        }

        return contraventions;
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