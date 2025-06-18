package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des contrevenants - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la structure de AffaireDAO
 */
public class ContrevenantDAO extends AbstractSQLiteDAO<Contrevenant, Long> {

    private static final Logger logger = LoggerFactory.getLogger(ContrevenantDAO.class);

    @Override
    protected String getTableName() {
        return "contrevenants";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO contrevenants (code, nom_complet, adresse, telephone, email, type_personne) 
            VALUES (?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE contrevenants 
            SET code = ?, nom_complet = ?, adresse = ?, telephone = ?, email = ?, 
                type_personne = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, code, nom_complet, adresse, telephone, email, type_personne, 
                   created_at, updated_at 
            FROM contrevenants 
            ORDER BY created_at DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, code, nom_complet, adresse, telephone, email, type_personne, 
                   created_at, updated_at 
            FROM contrevenants 
            WHERE id = ?
        """;
    }

    @Override
    protected Contrevenant mapResultSetToEntity(ResultSet rs) throws SQLException {
        Contrevenant contrevenant = new Contrevenant();

        contrevenant.setId(rs.getLong("id"));
        contrevenant.setCode(rs.getString("code"));
        contrevenant.setNomComplet(rs.getString("nom_complet"));
        contrevenant.setAdresse(rs.getString("adresse"));
        contrevenant.setTelephone(rs.getString("telephone"));
        contrevenant.setEmail(rs.getString("email"));
        contrevenant.setTypePersonne(rs.getString("type_personne"));

        // Gestion des timestamps - COMME DANS AffaireDAO
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                contrevenant.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour le contrevenant {}", contrevenant.getId());
            contrevenant.setCreatedAt(LocalDateTime.now());
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                contrevenant.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de updated_at pour le contrevenant {}", contrevenant.getId());
            contrevenant.setUpdatedAt(LocalDateTime.now());
        }

        return contrevenant;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Contrevenant contrevenant) throws SQLException {
        stmt.setString(1, contrevenant.getCode());
        stmt.setString(2, contrevenant.getNomComplet());
        stmt.setString(3, contrevenant.getAdresse());
        stmt.setString(4, contrevenant.getTelephone());
        stmt.setString(5, contrevenant.getEmail());
        stmt.setString(6, contrevenant.getTypePersonne());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Contrevenant contrevenant) throws SQLException {
        stmt.setString(1, contrevenant.getCode());
        stmt.setString(2, contrevenant.getNomComplet());
        stmt.setString(3, contrevenant.getAdresse());
        stmt.setString(4, contrevenant.getTelephone());
        stmt.setString(5, contrevenant.getEmail());
        stmt.setString(6, contrevenant.getTypePersonne());
        stmt.setLong(7, contrevenant.getId());
    }

    @Override
    protected Long getEntityId(Contrevenant contrevenant) {
        return contrevenant.getId();
    }

    @Override
    protected void setEntityId(Contrevenant contrevenant, Long id) {
        contrevenant.setId(id);
    }

    // Méthodes spécifiques aux contrevenants - SUIT LE PATTERN DE AffaireDAO

    /**
     * Trouve un contrevenant par son code
     */
    public Optional<Contrevenant> findByCode(String code) {
        String sql = """
            SELECT id, code, nom_complet, adresse, telephone, email, type_personne, 
                   created_at, updated_at 
            FROM contrevenants 
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
            logger.error("Erreur lors de la recherche par code: " + code, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve les contrevenants par type de personne
     */
    public List<Contrevenant> findByTypePersonne(String typePersonne) {
        String sql = """
            SELECT id, code, nom_complet, adresse, telephone, email, type_personne, 
                   created_at, updated_at 
            FROM contrevenants 
            WHERE type_personne = ? 
            ORDER BY nom_complet ASC
        """;

        List<Contrevenant> contrevenants = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, typePersonne);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                contrevenants.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par type: " + typePersonne, e);
        }

        return contrevenants;
    }

    /**
     * Recherche de contrevenants avec critères multiples - COMME AffaireDAO
     */
    public List<Contrevenant> searchContrevenants(String nomOuCode, String typePersonne,
                                                  int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, code, nom_complet, adresse, telephone, email, type_personne, ");
        sql.append("created_at, updated_at ");
        sql.append("FROM contrevenants WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_complet LIKE ? OR code LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (typePersonne != null && !typePersonne.trim().isEmpty()) {
            sql.append("AND type_personne = ? ");
            parameters.add(typePersonne);
        }

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Contrevenant> contrevenants = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                contrevenants.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de contrevenants", e);
        }

        return contrevenants;
    }

    /**
     * Compte les contrevenants correspondant aux critères - COMME AffaireDAO
     */
    public long countSearchContrevenants(String nomOuCode, String typePersonne) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM contrevenants WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (nomOuCode != null && !nomOuCode.trim().isEmpty()) {
            sql.append("AND (nom_complet LIKE ? OR code LIKE ?) ");
            String searchPattern = "%" + nomOuCode.trim() + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        if (typePersonne != null && !typePersonne.trim().isEmpty()) {
            sql.append("AND type_personne = ? ");
            parameters.add(typePersonne);
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
            logger.error("Erreur lors du comptage des contrevenants", e);
        }

        return 0;
    }

    /**
     * Génère le prochain code contrevenant selon le format CVNNNNN
     * CV = préfixe fixe
     * NNNNN = numéro séquentiel sur 5 chiffres (00001, 00002, etc.)
     */
    public String generateNextCode() {
        String prefix = "CV";

        // Rechercher le dernier code avec ce préfixe
        String sql = """
            SELECT code FROM contrevenants 
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
                // Premier code
                return prefix + "00001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du code contrevenant", e);
            return prefix + "00001"; // Fallback
        }
    }

    /**
     * Génère le code suivant basé sur le dernier code - COMME AffaireDAO
     */
    private String generateNextCodeFromLast(String lastCode, String prefix) {
        try {
            if (lastCode != null && lastCode.startsWith(prefix) && lastCode.length() == 7) {
                String numericPart = lastCode.substring(2);
                int lastNumber = Integer.parseInt(numericPart);
                return prefix + String.format("%05d", lastNumber + 1);
            }

            // Code invalide, recommencer
            return prefix + "00001";

        } catch (Exception e) {
            logger.warn("Erreur lors du parsing du dernier code: {}", lastCode, e);
            return prefix + "00001";
        }
    }

    /**
     * Vérifie si un code contrevenant existe déjà
     */
    public boolean existsByCode(String code) {
        String sql = "SELECT 1 FROM contrevenants WHERE code = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, code);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du code contrevenant", e);
            return false;
        }
    }

    /**
     * Trouve les contrevenants récents pour tableau de bord - COMME AffaireDAO
     */
    public List<Contrevenant> getRecentContrevenants(int limit) {
        String sql = getSelectAllQuery() + " LIMIT ?";

        List<Contrevenant> contrevenants = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                contrevenants.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des contrevenants récents", e);
        }

        return contrevenants;
    }

    /**
     * Recherche rapide pour autocomplete - COMME AgentService
     */
    public List<Contrevenant> searchQuick(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        return searchContrevenants(query.trim(), null, 0, limit);
    }

    /**
     * Trouve les contrevenants pour un rapport
     */
    public List<Contrevenant> getContrevenantsForReport(String typePersonne) {
        return searchContrevenants(null, typePersonne, 0, Integer.MAX_VALUE);
    }

    /**
     * Statistiques des contrevenants par type
     */
    public long countByTypePersonne(String typePersonne) {
        String sql = "SELECT COUNT(*) FROM contrevenants WHERE type_personne = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, typePersonne);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage par type", e);
        }

        return 0;
    }
}