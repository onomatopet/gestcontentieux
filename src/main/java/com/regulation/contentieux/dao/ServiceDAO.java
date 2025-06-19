package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
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
 * DAO pour la gestion des services - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la structure des autres DAOs
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
            INSERT INTO services (code_service, nom_service) 
            VALUES (?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE services 
            SET code_service = ?, nom_service = ? 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_service, nom_service, created_at 
            FROM services 
            ORDER BY nom_service ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_service, nom_service, created_at 
            FROM services 
            WHERE id = ?
        """;
    }

    @Override
    protected Service mapResultSetToEntity(ResultSet rs) throws SQLException {
        Service service = new Service();

        service.setId(rs.getLong("id"));
        service.setCodeService(rs.getString("code_service"));
        service.setNomService(rs.getString("nom_service"));

        // Gestion des timestamps - COMME DANS LES AUTRES DAOs
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
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Service service) throws SQLException {
        stmt.setString(1, service.getCodeService());
        stmt.setString(2, service.getNomService());
        stmt.setLong(3, service.getId());
    }

    @Override
    protected Long getEntityId(Service service) {
        return service.getId();
    }

    @Override
    protected void setEntityId(Service service, Long id) {
        service.setId(id);
    }

    // Méthodes spécifiques aux services

    /**
     * Trouve un service par son code
     */
    public Optional<Service> findByCodeService(String codeService) {
        String sql = """
            SELECT id, code_service, nom_service, created_at 
            FROM services 
            WHERE code_service = ?
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
     * Recherche de services avec critères multiples
     */
    public List<Service> searchServices(String nomOuCode, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_service, nom_service, created_at ");
        sql.append("FROM services WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_service LIKE ? OR code_service LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        sql.append("ORDER BY nom_service ASC LIMIT ? OFFSET ?");
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
     * Compte les services correspondant aux critères
     */
    public long countSearchServices(String nomOuCode) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM services WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_service LIKE ? OR code_service LIKE ?) ");
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
            logger.error("Erreur lors du comptage des services", e);
        }

        return 0;
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