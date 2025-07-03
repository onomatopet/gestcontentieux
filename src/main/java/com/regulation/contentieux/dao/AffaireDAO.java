package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.model.Contravention;
import com.regulation.contentieux.model.Bureau;
import com.regulation.contentieux.model.Service;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AffaireDAO extends AbstractSQLiteDAO<Affaire, Long> {

    private static final Logger logger = LoggerFactory.getLogger(AffaireDAO.class);

    @Override
    protected String getTableName() {
        return "affaires";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO affaires (numero_affaire, date_creation, montant_amende_total, 
                                 statut, contrevenant_id, contravention_id, bureau_id, service_id,
                                 created_at, updated_at, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE affaires 
            SET numero_affaire = ?, date_creation = ?, montant_amende_total = ?,
                statut = ?, contrevenant_id = ?, contravention_id = ?, 
                bureau_id = ?, service_id = ?, updated_at = ?, updated_by = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        // CORRECTION: Ajouter les LEFT JOIN pour charger toutes les relations
        return """
            SELECT a.*,
                   c.code as contrevenant_code, c.nom_complet as contrevenant_nom_complet,
                   c.type_personne as contrevenant_type_personne, c.adresse as contrevenant_adresse,
                   c.telephone as contrevenant_telephone, c.email as contrevenant_email,
                   ct.code as contravention_code, ct.libelle as contravention_libelle,
                   ct.description as contravention_description,
                   b.code_bureau, b.nom_bureau,
                   s.code_service, s.nom_service
            FROM affaires a
            LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
            LEFT JOIN contraventions ct ON a.contravention_id = ct.id
            LEFT JOIN bureaux b ON a.bureau_id = b.id
            LEFT JOIN services s ON a.service_id = s.id
            WHERE a.deleted = 0
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return getSelectAllQuery() + " AND a.id = ?";
    }

    @Override
    protected Affaire mapResultSetToEntity(ResultSet rs) throws SQLException {
        Affaire affaire = new Affaire();

        // Colonnes de base de la table affaires
        affaire.setId(rs.getLong("id"));
        affaire.setNumeroAffaire(rs.getString("numero_affaire"));

        // Date création
        Date dateCreation = rs.getDate("date_creation");
        if (dateCreation != null) {
            affaire.setDateCreation(dateCreation.toLocalDate());
        }

        // Montant
        BigDecimal montantAmendeTotal = rs.getBigDecimal("montant_amende_total");
        if (montantAmendeTotal != null) {
            affaire.setMontantAmendeTotal(montantAmendeTotal);
            affaire.setMontantTotal(montantAmendeTotal);
        } else {
            affaire.setMontantAmendeTotal(BigDecimal.ZERO);
            affaire.setMontantTotal(BigDecimal.ZERO);
        }

        // Statut
        String statutStr = rs.getString("statut");
        if (statutStr != null) {
            try {
                affaire.setStatut(StatutAffaire.valueOf(statutStr));
            } catch (IllegalArgumentException e) {
                affaire.setStatut(StatutAffaire.OUVERTE);
            }
        } else {
            affaire.setStatut(StatutAffaire.OUVERTE);
        }

        // IDs des relations
        affaire.setContrevenantId(rs.getLong("contrevenant_id"));
        affaire.setContraventionId(rs.getLong("contravention_id"));
        affaire.setBureauId(rs.getLong("bureau_id"));
        affaire.setServiceId(rs.getLong("service_id"));

        // CORRECTION: Charger les objets liés depuis les colonnes du LEFT JOIN

        // Contrevenant
        String contrevenantCode = rs.getString("contrevenant_code");
        if (contrevenantCode != null) {
            Contrevenant contrevenant = new Contrevenant();
            contrevenant.setId(affaire.getContrevenantId());
            contrevenant.setCode(contrevenantCode);
            contrevenant.setNomComplet(rs.getString("contrevenant_nom_complet"));
            contrevenant.setTypePersonne(rs.getString("contrevenant_type_personne"));
            contrevenant.setAdresse(rs.getString("contrevenant_adresse"));
            contrevenant.setTelephone(rs.getString("contrevenant_telephone"));
            contrevenant.setEmail(rs.getString("contrevenant_email"));
            affaire.setContrevenant(contrevenant);
        }

        // Contravention unique
        String contraventionCode = rs.getString("contravention_code");
        if (contraventionCode != null) {
            Contravention contravention = new Contravention();
            contravention.setId(affaire.getContraventionId());
            contravention.setCode(contraventionCode);
            contravention.setLibelle(rs.getString("contravention_libelle"));
            contravention.setDescription(rs.getString("contravention_description"));
            contravention.setMontant(montantAmendeTotal); // Utiliser le montant de l'affaire

            // Ajouter à la liste pour compatibilité
            List<Contravention> contraventions = new ArrayList<>();
            contraventions.add(contravention);
            affaire.setContraventions(contraventions);
        }

        // Bureau
        String codeBureau = rs.getString("code_bureau");
        if (codeBureau != null) {
            Bureau bureau = new Bureau();
            bureau.setId(affaire.getBureauId());
            bureau.setCodeBureau(codeBureau);
            bureau.setNomBureau(rs.getString("nom_bureau"));
            affaire.setBureau(bureau);
        }

        // Service
        String codeService = rs.getString("code_service");
        if (codeService != null) {
            Service service = new Service();
            service.setId(affaire.getServiceId());
            service.setCodeService(codeService);
            service.setNomService(rs.getString("nom_service"));
            affaire.setService(service);
        }

        // Timestamps
        affaire.setCreatedAt(rs.getTimestamp("created_at") != null ?
                rs.getTimestamp("created_at").toLocalDateTime() : null);
        affaire.setUpdatedAt(rs.getTimestamp("updated_at") != null ?
                rs.getTimestamp("updated_at").toLocalDateTime() : null);
        affaire.setCreatedBy(rs.getString("created_by"));
        affaire.setUpdatedBy(rs.getString("updated_by"));

        return affaire;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, Affaire entity) throws SQLException {
        stmt.setString(1, entity.getNumeroAffaire());
        stmt.setDate(2, entity.getDateCreation() != null ?
                Date.valueOf(entity.getDateCreation()) : Date.valueOf(LocalDate.now()));

        // CORRECTION IMPORTANTE : S'assurer que le montant est correctement défini
        BigDecimal montant = entity.getMontantAmendeTotal();
        if (montant == null || montant.compareTo(BigDecimal.ZERO) == 0) {
            montant = entity.getMontantTotal();
        }
        if (montant == null) {
            montant = BigDecimal.ZERO;
        }
        stmt.setBigDecimal(3, montant);

        logger.debug("Insertion affaire {} avec montant: {}", entity.getNumeroAffaire(), montant);

        stmt.setString(4, entity.getStatut() != null ? entity.getStatut().name() : "OUVERTE");
        stmt.setLong(5, entity.getContrevenantId());
        stmt.setLong(6, entity.getContraventionId());
        stmt.setObject(7, entity.getBureauId());
        stmt.setObject(8, entity.getServiceId());
        stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setString(11, entity.getCreatedBy());
        stmt.setString(12, entity.getUpdatedBy());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, Affaire entity) throws SQLException {
        stmt.setString(1, entity.getNumeroAffaire());
        stmt.setDate(2, entity.getDateCreation() != null ?
                Date.valueOf(entity.getDateCreation()) : null);
        stmt.setBigDecimal(3, entity.getMontantAmendeTotal());
        stmt.setString(4, entity.getStatut() != null ? entity.getStatut().name() : "OUVERTE");
        stmt.setLong(5, entity.getContrevenantId());
        stmt.setLong(6, entity.getContraventionId());
        stmt.setLong(7, entity.getBureauId() != null ? entity.getBureauId() : 0);
        stmt.setLong(8, entity.getServiceId() != null ? entity.getServiceId() : 0);
        stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setString(10, entity.getUpdatedBy());
        stmt.setLong(11, entity.getId());
    }

    @Override
    protected Long getEntityId(Affaire entity) {
        return entity.getId();
    }

    @Override
    protected void setEntityId(Affaire entity, Long id) {
        entity.setId(id);
    }

    /**
     * NOUVELLE MÉTHODE: Trouve toutes les affaires avec pagination
     */
    public List<Affaire> findAll(int offset, int limit) {
        String sql = getSelectAllQuery() + " ORDER BY a.date_creation DESC, a.numero_affaire DESC LIMIT ? OFFSET ?";
        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

            logger.info("Chargées {} affaires (offset={}, limit={})", affaires.size(), offset, limit);

        } catch (SQLException e) {
            logger.error("Erreur lors du chargement des affaires avec pagination", e);
            throw new RuntimeException("Erreur lors du chargement des affaires", e);
        }

        return affaires;
    }

    /**
     * Compte le nombre total d'affaires non supprimées
     */
    public long count() {
        String sql = "SELECT COUNT(*) FROM affaires WHERE deleted = 0";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage des affaires", e);
        }

        return 0;
    }

    /**
     * Recherche des affaires avec critères
     */
    public List<Affaire> searchAffaires(String searchTerm, StatutAffaire statut,
                                        LocalDate dateDebut, LocalDate dateFin,
                                        Long bureauId, int offset, int limit) {

        StringBuilder sql = new StringBuilder(getSelectAllQuery());
        List<Object> parameters = new ArrayList<>();

        // Construction dynamique de la requête
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append(" AND (a.numero_affaire LIKE ? OR c.nom_complet LIKE ?)");
            parameters.add("%" + searchTerm.trim() + "%");
            parameters.add("%" + searchTerm.trim() + "%");
        }

        if (statut != null) {
            sql.append(" AND a.statut = ?");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append(" AND a.date_creation >= ?");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append(" AND a.date_creation <= ?");
            parameters.add(Date.valueOf(dateFin));
        }

        if (bureauId != null && bureauId > 0) {
            sql.append(" AND a.bureau_id = ?");
            parameters.add(bureauId);
        }

        sql.append(" ORDER BY a.date_creation DESC, a.numero_affaire DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

            logger.info("Recherche terminée: {} affaires trouvées", affaires.size());

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche d'affaires", e);
            throw new RuntimeException("Erreur lors de la recherche", e);
        }

        return affaires;
    }

    /**
     * Trouve une affaire par son numéro
     */
    public Optional<Affaire> findByNumero(String numeroAffaire) {
        String sql = getSelectAllQuery() + " AND a.numero_affaire = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, numeroAffaire);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par numéro: {}", numeroAffaire, e);
        }

        return Optional.empty();
    }

    /**
     * Trouve une affaire par son numéro (alias pour compatibilité)
     */
    public Optional<Affaire> findByNumeroAffaire(String numeroAffaire) {
        return findByNumero(numeroAffaire);
    }

    /**
     * Génère le prochain numéro d'affaire
     */
    public String generateNextCode() {
        String sql = "SELECT numero_affaire FROM affaires WHERE numero_affaire LIKE ? ORDER BY numero_affaire DESC LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Format: AAMM00001 (année + mois + séquence)
            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
            stmt.setString(1, yearMonth + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String lastNumero = rs.getString("numero_affaire");
                    // Extraire le numéro de séquence et l'incrémenter
                    String sequenceStr = lastNumero.substring(4);
                    int sequence = Integer.parseInt(sequenceStr) + 1;
                    return yearMonth + String.format("%05d", sequence);
                } else {
                    // Premier numéro du mois
                    return yearMonth + "00001";
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la génération du numéro d'affaire", e);
            // Fallback : utiliser timestamp
            return "TMP" + System.currentTimeMillis();
        }
    }

    /**
     * Compte les affaires par statut
     */
    public long countByStatut(StatutAffaire statut) {
        String sql = "SELECT COUNT(*) FROM affaires WHERE statut = ? AND deleted = 0";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statut.name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage par statut: {}", statut, e);
        }

        return 0;
    }

    /**
     * Compte les affaires selon les critères de recherche
     */
    public long countSearchAffaires(String searchTerm, StatutAffaire statut,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    Long bureauId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT a.id) FROM affaires a ");
        sql.append("LEFT JOIN contrevenants c ON a.contrevenant_id = c.id ");
        sql.append("WHERE a.deleted = 0 ");

        List<Object> parameters = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append("AND (a.numero_affaire LIKE ? OR c.nom_complet LIKE ?) ");
            parameters.add("%" + searchTerm.trim() + "%");
            parameters.add("%" + searchTerm.trim() + "%");
        }

        if (statut != null) {
            sql.append("AND a.statut = ? ");
            parameters.add(statut.name());
        }

        if (dateDebut != null) {
            sql.append("AND a.date_creation >= ? ");
            parameters.add(Date.valueOf(dateDebut));
        }

        if (dateFin != null) {
            sql.append("AND a.date_creation <= ? ");
            parameters.add(Date.valueOf(dateFin));
        }

        if (bureauId != null && bureauId > 0) {
            sql.append("AND a.bureau_id = ? ");
            parameters.add(bureauId);
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du comptage avec critères", e);
        }

        return 0;
    }

    /**
     * Trouve toutes les affaires créées dans une période donnée
     */
    public List<Affaire> findByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = getSelectAllQuery() +
                " AND a.date_creation >= ? AND a.date_creation <= ? " +
                " ORDER BY a.date_creation ASC, a.numero_affaire ASC";

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

            logger.info("Trouvées {} affaires pour la période {} à {}",
                    affaires.size(), dateDebut, dateFin);

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par période", e);
            throw new RuntimeException("Erreur lors de la recherche par période", e);
        }

        return affaires;
    }

    /**
     * Trouve toutes les affaires par statut
     */
    public List<Affaire> findByStatut(StatutAffaire statut) {
        String sql = getSelectAllQuery() +
                " AND a.statut = ? " +
                " ORDER BY a.date_creation DESC, a.numero_affaire DESC";

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statut.name());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

            logger.info("Trouvées {} affaires avec le statut {}",
                    affaires.size(), statut);

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par statut", e);
            throw new RuntimeException("Erreur lors de la recherche par statut", e);
        }

        return affaires;
    }

    /**
     * Trouve toutes les affaires d'un service pour une période donnée
     */
    public List<Affaire> findByServiceAndPeriod(Long serviceId, LocalDate dateDebut, LocalDate dateFin) {
        String sql = getSelectAllQuery() +
                " AND a.service_id = ? " +
                " AND a.date_creation >= ? " +
                " AND a.date_creation <= ? " +
                " ORDER BY a.date_creation ASC, a.numero_affaire ASC";

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, serviceId);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

            logger.info("Trouvées {} affaires pour le service {} période {} à {}",
                    affaires.size(), serviceId, dateDebut, dateFin);

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche par service et période", e);
            throw new RuntimeException("Erreur lors de la recherche par service et période", e);
        }

        return affaires;
    }

    /**
     * Trouve les affaires avec encaissements validés pour une période
     */
    public List<Affaire> findAffairesWithEncaissementsByPeriod(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
            SELECT DISTINCT a.*,
                   c.code as contrevenant_code, c.nom_complet as contrevenant_nom_complet,
                   c.type_personne as contrevenant_type_personne, c.adresse as contrevenant_adresse,
                   c.telephone as contrevenant_telephone, c.email as contrevenant_email,
                   ct.code as contravention_code, ct.libelle as contravention_libelle,
                   ct.description as contravention_description,
                   b.code_bureau, b.nom_bureau,
                   s.code_service, s.nom_service
            FROM affaires a
            INNER JOIN encaissements e ON a.id = e.affaire_id
            LEFT JOIN contrevenants c ON a.contrevenant_id = c.id
            LEFT JOIN contraventions ct ON a.contravention_id = ct.id
            LEFT JOIN bureaux b ON a.bureau_id = b.id
            LEFT JOIN services s ON a.service_id = s.id
            WHERE e.date_encaissement BETWEEN ? AND ?
              AND e.statut = 'VALIDE'
              AND a.deleted = 0
            ORDER BY a.numero_affaire
        """;

        List<Affaire> affaires = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    affaires.add(mapResultSetToEntity(rs));
                }
            }

            logger.info("Trouvées {} affaires avec encaissements validés pour la période {} à {}",
                    affaires.size(), dateDebut, dateFin);

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche des affaires avec encaissements", e);
            throw new RuntimeException("Erreur lors de la recherche", e);
        }

        return affaires;
    }

    /**
     * Met à jour le montant encaissé d'une affaire
     */
    public void updateMontantEncaisse(Long affaireId, BigDecimal montantEncaisse) {
        String sql = """
            UPDATE affaires 
            SET montant_encaisse = ?, updated_at = ?
            WHERE id = ?
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBigDecimal(1, montantEncaisse);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, affaireId);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Montant encaissé mis à jour pour l'affaire {}: {}",
                        affaireId, montantEncaisse);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la mise à jour du montant encaissé", e);
            throw new RuntimeException("Erreur lors de la mise à jour", e);
        }
    }
}