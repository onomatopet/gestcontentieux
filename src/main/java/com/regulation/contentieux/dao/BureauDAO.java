package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Bureau;
import com.regulation.contentieux.model.Service;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des bureaux - MISE À JOUR POUR COMPATIBILITÉ
 * Compatible avec le modèle Bureau harmonisé
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
            INSERT INTO bureaux (code_bureau, nom_bureau, description, actif, service_id) 
            VALUES (?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE bureaux 
            SET code_bureau = ?, nom_bureau = ?, description = ?, actif = ?, service_id = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT b.id, b.code_bureau, b.nom_bureau, b.description, b.actif, b.service_id, b.created_at,
                   s.code_service, s.nom_service
            FROM bureaux b
            LEFT JOIN services s ON b.service_id = s.id
            ORDER BY b.nom_bureau ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT b.id, b.code_bureau, b.nom_bureau, b.description, b.actif, b.service_id, b.created_at,
                   s.code_service, s.nom_service
            FROM bureaux b
            LEFT JOIN services s ON b.service_id = s.id
            WHERE b.id = ?
        """;
    }

    @Override
    protected Bureau mapResultSetToEntity(ResultSet rs) throws SQLException {
        Bureau bureau = new Bureau();

        bureau.setId(rs.getLong("id"));
        bureau.setCodeBureau(rs.getString("code_bureau"));
        bureau.setNomBureau(rs.getString("nom_bureau"));
        bureau.setDescription(rs.getString("description"));

        // Gestion du boolean actif
        try {
            bureau.setActif(rs.getBoolean("actif"));
        } catch (SQLException e) {
            bureau.setActif(true); // Valeur par défaut
        }

        // Gestion du service parent
        try {
            Long serviceId = rs.getLong("service_id");
            if (serviceId != null && serviceId > 0) {
                Service service = new Service();
                service.setId(serviceId);
                service.setCodeService(rs.getString("code_service"));
                service.setNomService(rs.getString("nom_service"));
                bureau.setService(service);
            }
        } catch (SQLException e) {
            logger.debug("Pas de service associé pour le bureau {}", bureau.getId());
        }

        // Gestion des timestamps
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
        stmt.setString(3, bureau.getDescription());
        stmt.setBoolean(4, bureau.isActif());

        // Service parent
        if (bureau.getService() != null && bureau.getService().getId() != null) {
            stmt.setLong(5, bureau.getService().getId());
        } else {
            stmt.setNull(5, Types.BIGINT);
        }
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Bureau bureau) throws SQLException {
        setInsertParameters(stmt, bureau);
        stmt.setLong(6, bureau.getId());
    }

    @Override
    protected Long getEntityId(Bureau bureau) {
        return bureau.getId();
    }

    @Override
    protected void setEntityId(Bureau bureau, Long id) {
        bureau.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX BUREAUX ==========

    /**
     * Trouve un bureau par son code - COMPATIBLE AVEC ReferentielController
     */
    public Optional<Bureau> findByCode(String code) {
        return findByCodeBureau(code);
    }

    /**
     * Trouve un bureau par son code bureau
     */
    public Optional<Bureau> findByCodeBureau(String codeBureau) {
        String sql = """
            SELECT b.id, b.code_bureau, b.nom_bureau, b.description, b.actif, b.service_id, b.created_at,
                   s.code_service, s.nom_service
            FROM bureaux b
            LEFT JOIN services s ON b.service_id = s.id
            WHERE b.code_bureau = ?
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
     * Recherche de bureaux avec critères multiples - POUR ReferentielController
     */
    public List<Bureau> searchBureaux(String nomOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.id, b.code_bureau, b.nom_bureau, b.description, b.actif, b.service_id, b.created_at, ");
        sql.append("s.code_service, s.nom_service ");
        sql.append("FROM bureaux b ");
        sql.append("LEFT JOIN services s ON b.service_id = s.id ");
        sql.append("WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (b.nom_bureau LIKE ? OR b.code_bureau LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND b.actif = ? ");
            parameters.add(actifOnly);
        }

        sql.append("ORDER BY b.nom_bureau ASC LIMIT ? OFFSET ?");
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
     * Trouve tous les bureaux actifs - POUR LES COMBOBOX
     */
    public List<Bureau> findAllActive() {
        String sql = """
            SELECT b.id, b.code_bureau, b.nom_bureau, b.description, b.actif, b.service_id, b.created_at,
                   s.code_service, s.nom_service
            FROM bureaux b
            LEFT JOIN services s ON b.service_id = s.id
            WHERE b.actif = 1
            ORDER BY b.nom_bureau ASC
        """;

        List<Bureau> bureaux = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                bureaux.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des bureaux actifs", e);
        }

        return bureaux;
    }

    /**
     * Trouve les bureaux par service
     */
    public List<Bureau> findByServiceId(Long serviceId) {
        String sql = """
            SELECT b.id, b.code_bureau, b.nom_bureau, b.description, b.actif, b.service_id, b.created_at,
                   s.code_service, s.nom_service
            FROM bureaux b
            LEFT JOIN services s ON b.service_id = s.id
            WHERE b.service_id = ?
            ORDER BY b.nom_bureau ASC
        """;

        List<Bureau> bureaux = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, serviceId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                bureaux.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des bureaux par service: " + serviceId, e);
        }

        return bureaux;
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