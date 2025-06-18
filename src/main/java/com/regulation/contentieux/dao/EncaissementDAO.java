package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion des encaissements - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la structure de AffaireDAO et ContrevenantDAO
 */
public class EncaissementDAO extends AbstractSQLiteDAO<Encaissement, Long> {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementDAO.class);

    @Override
    protected String getTableName() {
        return "encaissements";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO encaissements (affaire_id, montant_encaisse, date_encaissement, 
                                     mode_reglement, reference, banque_id, statut, created_by) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE encaissements 
            SET affaire_id = ?, montant_encaisse = ?, date_encaissement = ?, 
                mode_reglement = ?, reference = ?, banque_id = ?, statut = ?, 
                updated_by = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, created_at, updated_at, created_by, updated_by 
            FROM encaissements 
            ORDER BY created_at DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, created_at, updated_at, created_by, updated_by 
            FROM encaissements 
            WHERE id = ?
        """;
    }

    @Override
    protected Encaissement mapResultSetToEntity(ResultSet rs) throws SQLException {
        Encaissement encaissement = new Encaissement();

        encaissement.setId(rs.getLong("id"));
        encaissement.setAffaireId(rs.getLong("affaire_id"));
        encaissement.setMontantEncaisse(rs.getDouble("montant_encaisse"));

        // Gestion de la date d'encaissement - COMME DANS AffaireDAO
        try {
            Date sqlDate = rs.getDate("date_encaissement");
            if (sqlDate != null) {
                encaissement.setDateEncaissement(sqlDate.toLocalDate());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de date_encaissement pour l'encaissement {}", rs.getLong("id"));
            encaissement.setDateEncaissement(LocalDate.now());
        }

        // Conversion du mode de règlement
        String modeReglementStr = rs.getString("mode_reglement");
        if (modeReglementStr != null) {
            try {
                encaissement.setModeReglement(ModeReglement.valueOf(modeReglementStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Mode de règlement inconnu pour l'encaissement {}: {}",
                        encaissement.getId(), modeReglementStr);
                encaissement.setModeReglement(ModeReglement.ESPECES); // Valeur par défaut
            }
        }

        encaissement.setReference(rs.getString("reference"));

        // Gestion de la banque (nullable)
        long banqueId = rs.getLong("banque_id");
        if (!rs.wasNull()) {
            encaissement.setBanqueId(banqueId);
        }

        // Conversion du statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                encaissement.setStatut(StatutEncaissement.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                logger.warn("Statut inconnu pour l'encaissement {}: {}",
                        encaissement.getId(), statutStr);
                encaissement.setStatut(StatutEncaissement.EN_ATTENTE); // Valeur par défaut
            }
        }

        // Gestion des timestamps - COMME DANS AffaireDAO
        try {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                encaissement.setCreatedAt(createdAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de created_at pour l'encaissement {}", encaissement.getId());
            encaissement.setCreatedAt(LocalDateTime.now());
        }

        try {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                encaissement.setUpdatedAt(updatedAt.toLocalDateTime());
            }
        } catch (SQLException e) {
            logger.debug("Échec du parsing de updated_at pour l'encaissement {}", encaissement.getId());
            encaissement.setUpdatedAt(LocalDateTime.now());
        }

        encaissement.setCreatedBy(rs.getString("created_by"));
        encaissement.setUpdatedBy(rs.getString("updated_by"));

        return encaissement;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        stmt.setLong(1, encaissement.getAffaireId());
        stmt.setDouble(2, encaissement.getMontantEncaisse());
        stmt.setDate(3, Date.valueOf(encaissement.getDateEncaissement()));
        stmt.setString(4, encaissement.getModeReglement().name());
        stmt.setString(5, encaissement.getReference());

        if (encaissement.getBanqueId() != null) {
            stmt.setLong(6, encaissement.getBanqueId());
        } else {
            stmt.setNull(6, Types.BIGINT);
        }

        stmt.setString(7, encaissement.getStatut().name());
        stmt.setString(8, encaissement.getCreatedBy());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Encaissement encaissement) throws SQLException {
        stmt.setLong(1, encaissement.getAffaireId());
        stmt.setDouble(2, encaissement.getMontantEncaisse());
        stmt.setDate(3, Date.valueOf(encaissement.getDateEncaissement()));
        stmt.setString(4, encaissement.getModeReglement().name());
        stmt.setString(5, encaissement.getReference());

        if (encaissement.getBanqueId() != null) {
            stmt.setLong(6, encaissement.getBanqueId());
        } else {
            stmt.setNull(6, Types.BIGINT);
        }

        stmt.setString(7, encaissement.getStatut().name());
        stmt.setString(8, encaissement.getUpdatedBy());
        stmt.setLong(9, encaissement.getId());
    }

    @Override
    protected Long getEntityId(Encaissement encaissement) {
        return encaissement.getId();
    }

    @Override
    protected void setEntityId(Encaissement encaissement, Long id) {
        encaissement.setId(id);
    }

    // Méthodes spécifiques aux encaissements - SUIT LE PATTERN ÉTABLI

    /**
     * Trouve les encaissements par affaire
     */
    public List<Encaissement> findByAffaireId(Long affaireId) {
        String sql = """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, created_at, updated_at, created_by, updated_by 
            FROM encaissements 
            WHERE affaire_id = ? 
            ORDER BY date_encaissement DESC
        """;

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par affaire: " + affaireId, e);
        }

        return encaissements;
    }

    /**
     * Trouve les encaissements par statut
     */
    public List<Encaissement> findByStatut(StatutEncaissement statut) {
        String sql = """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, created_at, updated_at, created_by, updated_by 
            FROM encaissements 
            WHERE statut = ? 
            ORDER BY date_encaissement DESC
        """;

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statut.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par statut: " + statut, e);
        }

        return encaissements;
    }

    /**
     * Trouve un encaissement par sa référence
     */
    public Optional<Encaissement> findByReference(String reference) {
        String sql = """
            SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, 
                   reference, banque_id, statut, created_at, updated_at, created_by, updated_by 
            FROM encaissements 
            WHERE reference = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par référence: " + reference, e);
        }

        return Optional.empty();
    }

    /**
     * Recherche d'encaissements avec critères multiples - COMME AffaireDAO
     */
    public List<Encaissement> searchEncaissements(String reference, StatutEncaissement statut,
                                                  ModeReglement modeReglement, LocalDate dateDebut,
                                                  LocalDate dateFin, Long affaireId,
                                                  int offset, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, affaire_id, montant_encaisse, date_encaissement, mode_reglement, ");
        sql.append("reference, banque_id, statut, created_at, updated_at, created_by, updated_by ");
        sql.append("FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND reference LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (modeReglement != null) {
            sql.append("AND mode_reglement = ? ");
            parameters.add(modeReglement.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (affaireId != null) {
            sql.append("AND affaire_id = ? ");
            parameters.add(affaireId);
        }

        sql.append("ORDER BY date_encaissement DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'encaissements", e);
        }

        return encaissements;
    }

    /**
     * Compte les encaissements correspondant aux critères - COMME AffaireDAO
     */
    public long countSearchEncaissements(String reference, StatutEncaissement statut,
                                         ModeReglement modeReglement, LocalDate dateDebut,
                                         LocalDate dateFin, Long affaireId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (reference != null && !reference.trim().isEmpty()) {
            sql.append("AND reference LIKE ? ");
            parameters.add("%" + reference.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        if (modeReglement != null) {
            sql.append("AND mode_reglement = ? ");
            parameters.add(modeReglement.name());
        }

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (affaireId != null) {
            sql.append("AND affaire_id = ? ");
            parameters.add(affaireId);
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
            logger.error("Erreur lors du comptage des encaissements", e);
        }

        return 0;
    }

    /**
     * Calcule le montant total encaissé pour une affaire
     */
    public Double getTotalEncaisseByAffaire(Long affaireId) {
        String sql = """
            SELECT SUM(montant_encaisse) as total 
            FROM encaissements 
            WHERE affaire_id = ? AND statut = 'VALIDE'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble("total");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du total encaissé", e);
        }

        return 0.0;
    }

    /**
     * Calcule le montant total des encaissements par période
     */
    public Double getTotalEncaissementsByPeriod(LocalDate dateDebut, LocalDate dateFin,
                                                StatutEncaissement statut) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT SUM(montant_encaisse) as total FROM encaissements WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        if (dateDebut != null) {
            sql.append("AND date_encaissement >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND date_encaissement <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (statut != null) {
            sql.append("AND statut = ? ");
            parameters.add(statut.name());
        }

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total");
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul du total par période", e);
        }

        return 0.0;
    }

    /**
     * Met à jour le statut d'un encaissement
     */
    public boolean updateStatut(Long encaissementId, StatutEncaissement nouveauStatut, String updatedBy) {
        String sql = """
            UPDATE encaissements 
            SET statut = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nouveauStatut.name());
            stmt.setString(2, updatedBy);
            stmt.setLong(3, encaissementId);

            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Statut de l'encaissement {} mis à jour vers {} par {}",
                        encaissementId, nouveauStatut, updatedBy);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour du statut", e);
        }

        return false;
    }

    /**
     * Vérifie si une référence existe déjà
     */
    public boolean existsByReference(String reference) {
        String sql = "SELECT 1 FROM encaissements WHERE reference = ? LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification de la référence", e);
            return false;
        }
    }

    /**
     * Encaissements récents pour tableau de bord - COMME AffaireDAO
     */
    public List<Encaissement> getRecentEncaissements(int limit) {
        String sql = getSelectAllQuery() + " LIMIT ?";

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                encaissements.add(mapResultSetToEntity(rs));
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des encaissements récents", e);
        }

        return encaissements;
    }

    /**
     * Génère le prochain numéro d'encaissement selon le format YYMMRNNNN
     * YY = année sur 2 chiffres
     * MM = mois sur 2 chiffres
     * R = lettre fixe "R" pour "recette"
     * NNNN = incrémentation du mois (0001, 0002, etc.)
     *
     * Exemples:
     * - Premier encaissement de juin 2025: 2506R0001
     * - Deuxième encaissement de juin 2025: 2506R0002
     */
    public String generateNextNumeroEncaissement() {
        LocalDate today = LocalDate.now();
        String yearMonth = String.format("%02d%02d", today.getYear() % 100, today.getMonthValue());
        String prefix = yearMonth + "R";

        // Rechercher le dernier numéro du mois en cours
        String sql = """
            SELECT reference FROM encaissements 
            WHERE reference LIKE ? 
            ORDER BY reference DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastReference = rs.getString("reference");
                return generateNextNumeroFromLast(lastReference, prefix);
            } else {
                // Premier numéro du mois
                return prefix + "0001";
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro d'encaissement", e);
            return prefix + "0001"; // Fallback
        }
    }

    /**
     * Génère le numéro suivant basé sur le dernier numéro du mois
     */
    private String generateNextNumeroFromLast(String lastReference, String currentPrefix) {
        try {
            // Vérifier que la dernière référence correspond bien au mois en cours
            if (lastReference != null && lastReference.length() == 9) { // YYMMRNNNN = 9 caractères
                String lastPrefix = lastReference.substring(0, 5); // YYMMR

                if (lastPrefix.equals(currentPrefix)) {
                    // Même mois, incrémenter le compteur
                    String compteurStr = lastReference.substring(5); // NNNN
                    int compteur = Integer.parseInt(compteurStr);
                    return currentPrefix + String.format("%04d", compteur + 1);
                }
            }

            // Nouveau mois ou format invalide
            return currentPrefix + "0001";

        } catch (Exception e) {
            logger.warn("Erreur lors du parsing de la dernière référence: {}", lastReference, e);
            return currentPrefix + "0001";
        }
    }

    /**
     * Statistiques des encaissements par mode de règlement
     */
    public long countByModeReglement(ModeReglement modeReglement) {
        String sql = "SELECT COUNT(*) FROM encaissements WHERE mode_reglement = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, modeReglement.name());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage par mode de règlement", e);
        }

        return 0;
    }
}