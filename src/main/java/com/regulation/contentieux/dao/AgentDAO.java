package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Agent;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des agents
 * Suit la même logique que les autres DAOs
 */
public class AgentDAO extends AbstractSQLiteDAO<Agent, Long> {

    private static final Logger logger = LoggerFactory.getLogger(AgentDAO.class);

    @Override
    protected String getTableName() {
        return "agents";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO agents (code_agent, nom, prenom, grade, service_id, actif) 
            VALUES (?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE agents 
            SET code_agent = ?, nom = ?, prenom = ?, grade = ?, 
                service_id = ?, actif = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code_agent, nom, prenom, grade, service_id, 
                   actif, created_at, updated_at 
            FROM agents 
            ORDER BY nom ASC, prenom ASC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code_agent, nom, prenom, grade, service_id, 
                   actif, created_at, updated_at 
            FROM agents 
            WHERE id = ?
        """;
    }

    @Override
    protected Agent mapResultSetToEntity(ResultSet rs) throws SQLException {
        Agent agent = new Agent();

        agent.setId(rs.getLong("id"));
        agent.setCodeAgent(rs.getString("code_agent"));
        agent.setNom(rs.getString("nom"));
        agent.setPrenom(rs.getString("prenom"));
        agent.setGrade(rs.getString("grade"));

        // Gestion du service_id nullable
        long serviceId = rs.getLong("service_id");
        if (!rs.wasNull()) {
            agent.setServiceId(serviceId);
        }

        agent.setActif(rs.getBoolean("actif"));

        // Gestion des timestamps avec fallback
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                agent.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour l'agent {}", agent.getId());
            agent.setCreatedAt(LocalDateTime.now());
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                agent.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de updated_at pour l'agent {}", agent.getId());
            agent.setUpdatedAt(LocalDateTime.now());
        }

        return agent;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Agent agent) throws SQLException {
        stmt.setString(1, agent.getCodeAgent());
        stmt.setString(2, agent.getNom());
        stmt.setString(3, agent.getPrenom());
        stmt.setString(4, agent.getGrade());

        if (agent.getServiceId() != null) {
            stmt.setLong(5, agent.getServiceId());
        } else {
            stmt.setNull(5, Types.BIGINT);
        }

        stmt.setBoolean(6, agent.getActif() != null ? agent.getActif() : true);
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Agent agent) throws SQLException {
        stmt.setString(1, agent.getCodeAgent());
        stmt.setString(2, agent.getNom());
        stmt.setString(3, agent.getPrenom());
        stmt.setString(4, agent.getGrade());

        if (agent.getServiceId() != null) {
            stmt.setLong(5, agent.getServiceId());
        } else {
            stmt.setNull(5, Types.BIGINT);
        }

        stmt.setBoolean(6, agent.getActif() != null ? agent.getActif() : true);
        stmt.setLong(7, agent.getId());
    }

    @Override
    protected Long getEntityId(Agent agent) {
        return agent.getId();
    }

    @Override
    protected void setEntityId(Agent agent, Long id) {
        agent.setId(id);
    }

    // Méthodes spécifiques aux agents

    /**
     * Trouve un agent par son code
     */
    public Optional<Agent> findByCodeAgent(String codeAgent) {
        String sql = """
            SELECT id, code_agent, nom, prenom, grade, service_id, 
                   actif, created_at, updated_at 
            FROM agents 
            WHERE code_agent = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeAgent);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par code agent: " + codeAgent, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve les agents actifs
     */
    public List<Agent> findActiveAgents() {
        String sql = """
            SELECT id, code_agent, nom, prenom, grade, service_id, 
                   actif, created_at, updated_at 
            FROM agents 
            WHERE actif = true 
            ORDER BY nom ASC, prenom ASC
        """;

        List<Agent> agents = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                agents.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des agents actifs", e);
        }

        return agents;
    }

    /**
     * Trouve les agents par service
     */
    public List<Agent> findByServiceId(Long serviceId) {
        String sql = """
            SELECT id, code_agent, nom, prenom, grade, service_id, 
                   actif, created_at, updated_at 
            FROM agents 
            WHERE service_id = ? AND actif = true 
            ORDER BY nom ASC, prenom ASC
        """;

        List<Agent> agents = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, serviceId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                agents.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par service: " + serviceId, e);
        }

        return agents;
    }

    /**
     * Recherche d'agents avec critères multiples
     */
    public List<Agent> searchAgents(String nomOuPrenom, String grade, Long serviceId,
                                    Boolean actif, int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code_agent, nom, prenom, grade, service_id, ");
        sql.append("actif, created_at, updated_at ");
        sql.append("FROM agents WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuPrenom != null && !nomOuPrenom.trim().isEmpty()) {
            sql.append("AND (nom LIKE ? OR prenom LIKE ? OR code_agent LIKE ?) ");
            String pattern = "%" + nomOuPrenom.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }

        if (grade != null && !grade.trim().isEmpty()) {
            sql.append("AND grade = ? ");
            parameters.add(grade);
        }

        if (serviceId != null) {
            sql.append("AND service_id = ? ");
            parameters.add(serviceId);
        }

        if (actif != null) {
            sql.append("AND actif = ? ");
            parameters.add(actif);
        }

        sql.append("ORDER BY nom ASC, prenom ASC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Agent> agents = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                agents.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'agents", e);
        }

        return agents;
    }

    /**
     * Compte les agents correspondant aux critères de recherche
     */
    public long countSearchAgents(String nomOuPrenom, String grade, Long serviceId, Boolean actif) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM agents WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuPrenom != null && !nomOuPrenom.trim().isEmpty()) {
            sql.append("AND (nom LIKE ? OR prenom LIKE ? OR code_agent LIKE ?) ");
            String pattern = "%" + nomOuPrenom.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }

        if (grade != null && !grade.trim().isEmpty()) {
            sql.append("AND grade = ? ");
            parameters.add(grade);
        }

        if (serviceId != null) {
            sql.append("AND service_id = ? ");
            parameters.add(serviceId);
        }

        if (actif != null) {
            sql.append("AND actif = ? ");
            parameters.add(actif);
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
            logger.error("Erreur lors du comptage des agents", e);
        }

        return 0;
    }

    /**
     * Génère le prochain code agent selon le format AGNNNNN
     */
    public String generateNextCodeAgent() {
        String prefix = "AG";

        String sql = """
            SELECT code_agent FROM agents 
            WHERE code_agent LIKE ? 
            ORDER BY code_agent DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastCode = rs.getString("code_agent");
                return generateNextCodeFromLast(lastCode, prefix);
            } else {
                return prefix + "00001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code agent", e);
            return prefix + "00001"; // Fallback
        }
    }

    /**
     * Génère le code suivant basé sur le dernier code
     */
    private String generateNextCodeFromLast(String lastCode, String prefix) {
        try {
            if (lastCode != null && lastCode.startsWith(prefix)) {
                String numberPart = lastCode.substring(prefix.length());
                int number = Integer.parseInt(numberPart);
                return prefix + String.format("%05d", number + 1);
            }

            return prefix + "00001";

        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier code: {}", lastCode, e);
            return prefix + "00001";
        }
    }

    /**
     * Vérifie si un code agent existe déjà
     */
    public boolean existsByCodeAgent(String codeAgent) {
        String sql = "SELECT 1 FROM agents WHERE code_agent = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codeAgent);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code agent", e);
            return false;
        }
    }

    /**
     * Désactive un agent (soft delete)
     */
    public boolean deactivateAgent(Long agentId) {
        String sql = "UPDATE agents SET actif = false, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agentId);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Agent {} désactivé", agentId);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la désactivation de l'agent", e);
        }

        return false;
    }

    /**
     * Réactive un agent
     */
    public boolean reactivateAgent(Long agentId) {
        String sql = "UPDATE agents SET actif = true, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agentId);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Agent {} réactivé", agentId);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la réactivation de l'agent", e);
        }

        return false;
    }
}