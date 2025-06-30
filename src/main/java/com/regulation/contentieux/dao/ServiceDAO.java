package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Service;
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
 * DAO pour la gestion des services - MISE À JOUR POUR COMPATIBILITÉ
 * Compatible avec le modèle Service harmonisé
 */
public class ServiceDAO extends AbstractSQLiteDAO<Service, Long> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDAO.class);

    @Override
    protected String getTableName() {
        return "services";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
        INSERT INTO services (code_service, nom_service, actif, centre_id) 
        VALUES (?, ?, ?, ?)
    """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
        UPDATE services 
        SET code_service = ?, nom_service = ?, actif = ?, centre_id = ?
        WHERE id = ?
    """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
        SELECT s.id, s.code_service, s.nom_service, s.actif, s.centre_id, s.created_at,
               c.code_centre, c.nom_centre
        FROM services s
        LEFT JOIN centres c ON s.centre_id = c.id
        ORDER BY s.nom_service ASC
    """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
        SELECT s.id, s.code_service, s.nom_service, s.actif, s.centre_id, s.created_at,
               c.code_centre, c.nom_centre
        FROM services s
        LEFT JOIN centres c ON s.centre_id = c.id
        WHERE s.id = ?
    """;
    }

    @Override
    protected Service mapResultSetToEntity(ResultSet rs) throws SQLException {
        Service service = new Service();

        service.setId(rs.getLong("id"));
        service.setCodeService(rs.getString("code_service"));
        service.setNomService(rs.getString("nom_service"));
        service.setDescription(rs.getString("description"));

        // Gestion du boolean actif
        try {
            service.setActif(rs.getBoolean("actif"));
        } catch (SQLException e) {
            service.setActif(true); // Valeur par défaut
        }

        // Gestion du centre parent
        try {
            Long centreId = rs.getLong("centre_id");
            if (centreId != null && centreId > 0) {
                Centre centre = new Centre();
                centre.setId(centreId);
                centre.setCodeCentre(rs.getString("code_centre"));
                centre.setNomCentre(rs.getString("nom_centre"));
                service.setCentre(centre);
            }
        } catch (SQLException e) {
            logger.debug("Pas de centre associé pour le service {}", service.getId());
        }

        // Gestion des timestamps
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                service.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour le service {}", service.getId());
            service.setCreatedAt(LocalDateTime.now());
        }

        return service;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Service service) throws SQLException {
        stmt.setString(1, service.getCodeService());
        stmt.setString(2, service.getNomService());
        stmt.setBoolean(3, service.isActif());

        if (service.getCentre() != null && service.getCentre().getId() != null) {
            stmt.setLong(4, service.getCentre().getId());
        } else {
            stmt.setNull(4, Types.BIGINT);
        }
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Service service) throws SQLException {
        setInsertParameters(stmt, service);
        stmt.setLong(5, service.getId());
    }

    @Override
    protected Long getEntityId(Service service) {
        return service.getId();
    }

    @Override
    protected void setEntityId(Service service, Long id) {
        service.setId(id);
    }

    // ========== MÉTHODES SPÉCIFIQUES AUX SERVICES ==========

    /**
     * Trouve un service par son code - COMPATIBLE AVEC ReferentielController
     */
    public Optional<Service> findByCode(String code) {
        return findByCodeService(code);
    }

    /**
     * Trouve un service par son code service
     */
    public Optional<Service> findByCodeService(String codeService) {
        String sql = """
            SELECT s.id, s.code_service, s.nom_service, s.description, s.actif, s.centre_id, s.created_at,
                   c.code_centre, c.nom_centre
            FROM services s
            LEFT JOIN centres c ON s.centre_id = c.id
            WHERE s.code_service = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeService);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code service: " + codeService, e);
        }

        return Optional.empty();
    }

    /**
     * Compte le nombre de services par centre
     * @param centreId ID du centre
     * @return Nombre de services appartenant au centre
     */
    public long countByCentreId(Long centreId) {
        if (centreId == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM services WHERE centre_id = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, centreId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des services pour le centre ID: " + centreId, e);
        }

        return 0;
    }

    /**
     * Recherche de services avec critères multiples - POUR ReferentielController
     */
    public List<Service> searchServices(String nomOuCode, Boolean actifOnly, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT s.id, s.code_service, s.nom_service, s.description, s.actif, s.centre_id, s.created_at, ");
        sql.append("c.code_centre, c.nom_centre ");
        sql.append("FROM services s ");
        sql.append("LEFT JOIN centres c ON s.centre_id = c.id ");
        sql.append("WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (s.nom_service LIKE ? OR s.code_service LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND s.actif = ? ");
            parameters.add(actifOnly);
        }

        sql.append("ORDER BY s.nom_service ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Service> services = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                services.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de services", e);
        }

        return services;
    }

    /**
     * Version simplifiée pour compatibilité
     */
    public List<Service> searchServices(String nomOuCode, int offset, int limit) {
        return searchServices(nomOuCode, null, offset, limit);
    }

    /**
     * Compte les services correspondant aux critères
     */
    public long countSearchServices(String nomOuCode, Boolean actifOnly) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM services s WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (s.nom_service LIKE ? OR s.code_service LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (actifOnly != null) {
            sql.append("AND s.actif = ? ");
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
            logger.error("Erreur lors du comptage des services", e);
        }

        return 0;
    }

    /**
     * Version simplifiée pour compatibilité
     */
    public long countSearchServices(String nomOuCode) {
        return countSearchServices(nomOuCode, null);
    }

    /**
     * Trouve tous les services actifs - POUR LES COMBOBOX
     */
    public List<Service> findAllActive() {
        String sql = """
            SELECT s.id, s.code_service, s.nom_service, s.description, s.actif, s.centre_id, s.created_at,
                   c.code_centre, c.nom_centre
            FROM services s
            LEFT JOIN centres c ON s.centre_id = c.id
            WHERE s.actif = 1
            ORDER BY s.nom_service ASC
        """;

        List<Service> services = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                services.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des services actifs", e);
        }

        return services;
    }

    /**
     * Trouve les services par centre
     */
    public List<Service> findByCentreId(Long centreId) {
        String sql = """
            SELECT s.id, s.code_service, s.nom_service, s.description, s.actif, s.centre_id, s.created_at,
                   c.code_centre, c.nom_centre
            FROM services s
            LEFT JOIN centres c ON s.centre_id = c.id
            WHERE s.centre_id = ?
            ORDER BY s.nom_service ASC
        """;

        List<Service> services = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, centreId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                services.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des services par centre: " + centreId, e);
        }

        return services;
    }

    public List<Service> findAllActifs() {
        String sql = "SELECT * FROM services WHERE actif = 1 ORDER BY nom_service";
        List<Service> services = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                services.add(mapResultSetToEntity(rs));
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des services actifs", e);
        }

        return services;
    }

    /**
     * Génère le prochain code service selon le format SRVNNN
     */
    public String generateNextCodeService() {
        String prefix = "SRV";

        String sql = """
            SELECT code_service FROM services 
            WHERE code_service LIKE ? 
            ORDER BY code_service DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("code_service");
                return generateNextCodeFromLast(lastCode, prefix);
            } else {
                return prefix + "01";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code service", e);
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
            logger.warn("Erreur lors du parsing du dernier code service: {}", lastCode, e);
            return prefix + "01";
        }
    }

    /**
     * Vérifie si un code service existe déjà
     */
    public boolean existsByCodeService(String codeService) {
        String sql = "SELECT 1 FROM services WHERE code_service = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeService);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code service", e);
            return false;
        }
    }
}