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
            INSERT INTO banques (code_banque, nom_banque, description, adresse, telephone, email) 
            VALUES (?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE banques 
            SET code_banque = ?, nom_banque = ?, description = ?, 
                adresse = ?, telephone = ?, email = ? 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_banque, nom_banque, description, adresse, 
                   telephone, email, created_at 
            FROM banques 
            ORDER BY nom_banque ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_banque, nom_banque, description, adresse, 
                   telephone, email, created_at 
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
        banque.setAdresse(rs.getString("adresse"));
        banque.setTelephone(rs.getString("telephone"));
        banque.setEmail(rs.getString("email"));

        // Gestion des timestamps - COMME DANS LES AUTRES DAOs
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
        stmt.setString(4, banque.getAdresse());
        stmt.setString(5, banque.getTelephone());
        stmt.setString(6, banque.getEmail());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Banque banque) throws SQLException {
        stmt.setString(1, banque.getCodeBanque());
        stmt.setString(2, banque.getNomBanque());
        stmt.setString(3, banque.getDescription());
        stmt.setString(4, banque.getAdresse());
        stmt.setString(5, banque.getTelephone());
        stmt.setString(6, banque.getEmail());
        stmt.setLong(7, banque.getId());
    }

    @Override
    protected Long getEntityId(Banque banque) {
        return banque.getId();
    }

    @Override
    protected void setEntityId(Banque banque, Long id) {
        banque.setId(id);
    }

    // Méthodes spécifiques aux banques

    /**
     * Trouve une banque par son code
     */
    public Optional<Banque> findByCodeBanque(String codeBanque) {
        String sql = """
            SELECT id, code_banque, nom_banque, description, adresse, 
                   telephone, email, created_at 
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
    public List<Banque> searchBanques(String nomOuCode, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_banque, nom_banque, description, adresse, ");
        sql.append("telephone, email, created_at ");
        sql.append("FROM banques WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_banque LIKE ? OR code_banque LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
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
     * Compte les banques correspondant aux critères
     */
    public long countSearchBanques(String nomOuCode) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM banques WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_banque LIKE ? OR code_banque LIKE ?) ");
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
            logger.error("Erreur lors du comptage des banques", e);
        }

        return 0;
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

    /**
     * Récupère les banques actives uniquement
     */
    public List<Banque> findActiveBanques() {
        String sql = """
            SELECT id, code_banque, nom_banque, description, adresse, 
                   telephone, email, created_at 
            FROM banques 
            ORDER BY nom_banque ASC
        """;

        List<Banque> banques = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Banque banque = mapResultSetToEntity(rs);
                if (banque.isActif()) {
                    banques.add(banque);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des banques actives", e);
        }

        return banques;
    }
}