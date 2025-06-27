package com.regulation.contentieux.dao;

import com.regulation.contentieux.dao.impl.AbstractSQLiteDAO;
import com.regulation.contentieux.model.AffaireCentre;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la gestion de la relation Affaire-Centre
 */
public class AffaireCentreDAO extends AbstractSQLiteDAO<AffaireCentre, Long> {

    private static final Logger logger = LoggerFactory.getLogger(AffaireCentreDAO.class);

    // AJOUTER CES DÉCLARATIONS
    private AffaireDAO affaireDAO = new AffaireDAO();
    private CentreDAO centreDAO = new CentreDAO();

    // CONSTRUCTEUR
    public AffaireCentreDAO() {
        this.affaireDAO = new AffaireDAO();
        this.centreDAO = new CentreDAO();
    }

    @Override
    protected String getTableName() {
        return "affaires_centres";
    }

    @Override
    protected String getIdColumnName() {
        return "id";
    }

    @Override
    protected String getInsertQuery() {
        return """
            INSERT INTO affaires_centres 
            (affaire_id, centre_id, montant_base, montant_indicateur, source)
            VALUES (?, ?, ?, ?, ?)
        """;
    }

    @Override
    protected String getUpdateQuery() {
        return """
            UPDATE affaires_centres 
            SET affaire_id = ?, centre_id = ?, montant_base = ?, 
                montant_indicateur = ?, source = ?
            WHERE id = ?
        """;
    }

    @Override
    protected String getSelectAllQuery() {
        return """
            SELECT ac.*, 
                   a.numero_affaire, 
                   c.code_centre, c.nom_centre
            FROM affaires_centres ac
            LEFT JOIN affaires a ON ac.affaire_id = a.id
            LEFT JOIN centres c ON ac.centre_id = c.id
            ORDER BY ac.id DESC
        """;
    }

    @Override
    protected String getSelectByIdQuery() {
        return getSelectAllQuery() + " WHERE ac.id = ?";
    }

    @Override
    protected AffaireCentre mapResultSetToEntity(ResultSet rs) throws SQLException {
        AffaireCentre ac = new AffaireCentre();
        ac.setId(rs.getLong("id"));
        ac.setAffaireId(rs.getLong("affaire_id"));
        ac.setCentreId(rs.getLong("centre_id"));
        ac.setMontantBase(rs.getBigDecimal("montant_base"));
        ac.setMontantIndicateur(rs.getBigDecimal("montant_indicateur"));
        ac.setSource(rs.getString("source"));

        // Charger les relations si les colonnes existent
        try {
            if (rs.getString("numero_affaire") != null) {
                Affaire affaire = new Affaire();
                affaire.setId(ac.getAffaireId());
                affaire.setNumeroAffaire(rs.getString("numero_affaire"));
                ac.setAffaire(affaire);
            }

            if (rs.getString("code_centre") != null) {
                Centre centre = new Centre();
                centre.setId(ac.getCentreId());
                centre.setCodeCentre(rs.getString("code_centre"));
                centre.setNomCentre(rs.getString("nom_centre"));
                ac.setCentre(centre);
            }
        } catch (SQLException e) {
            // Les colonnes de jointure n'existent pas dans cette requête
        }

        return ac;
    }

    @Override
    protected void setInsertParameters(PreparedStatement stmt, AffaireCentre entity) throws SQLException {
        stmt.setLong(1, entity.getAffaireId());
        stmt.setLong(2, entity.getCentreId());
        stmt.setBigDecimal(3, entity.getMontantBase());
        stmt.setBigDecimal(4, entity.getMontantIndicateur());
        stmt.setString(5, entity.getSource());
    }

    @Override
    protected void setUpdateParameters(PreparedStatement stmt, AffaireCentre entity) throws SQLException {
        setInsertParameters(stmt, entity);
        stmt.setLong(6, entity.getId());
    }

    @Override
    protected Long getEntityId(AffaireCentre entity) {
        return entity.getId();
    }

    @Override
    protected void setEntityId(AffaireCentre entity, Long id) {
        entity.setId(id);
    }

    /**
     * Trouve toutes les relations pour une affaire
     */
    public List<AffaireCentre> findByAffaireId(Long affaireId) {
        String sql = getSelectAllQuery() + " WHERE ac.affaire_id = ?";
        List<AffaireCentre> results = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur recherche par affaire", e);
        }

        return results;
    }

    /**
     * Trouve toutes les affaires d'un centre
     */
    public List<AffaireCentre> findByCentreId(Long centreId) {
        String sql = getSelectAllQuery() + " WHERE ac.centre_id = ?";
        List<AffaireCentre> results = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, centreId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur recherche par centre", e);
        }

        return results;
    }

    /**
     * Trouve une relation spécifique affaire-centre
     */
    public Optional<AffaireCentre> findByAffaireAndCentre(Long affaireId, Long centreId) {
        String sql = "SELECT * FROM affaires_centres WHERE affaire_id = ? AND centre_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            stmt.setLong(2, centreId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEntity(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur recherche relation", e);
        }

        return Optional.empty();
    }

    /**
     * Calcule le total par centre pour une période
     */
    public List<CentreRepartitionStat> getStatsByCentrePeriode(LocalDate debut, LocalDate fin) {
        String sql = """
        SELECT 
            c.id as centre_id,
            c.code_centre,
            c.nom_centre,
            COUNT(DISTINCT ac.affaire_id) as nombre_affaires,
            COALESCE(SUM(ac.montant_base), 0) as total_base,
            COALESCE(SUM(ac.montant_indicateur), 0) as total_indicateur
        FROM centres c
        LEFT JOIN affaires_centres ac ON c.id = ac.centre_id
        LEFT JOIN affaires a ON ac.affaire_id = a.id
        LEFT JOIN encaissements e ON a.id = e.affaire_id
        WHERE e.date_encaissement BETWEEN ? AND ?
        OR (ac.affaire_id IS NOT NULL AND e.id IS NULL)
        GROUP BY c.id, c.code_centre, c.nom_centre
        ORDER BY c.nom_centre
    """;

        List<CentreRepartitionStat> stats = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(debut));
            stmt.setDate(2, Date.valueOf(fin));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CentreRepartitionStat stat = new CentreRepartitionStat();
                    stat.setCentreId(rs.getLong("centre_id"));
                    stat.setCodeCentre(rs.getString("code_centre"));
                    stat.setNomCentre(rs.getString("nom_centre"));
                    stat.setNombreAffaires(rs.getInt("nombre_affaires"));
                    stat.setMontantBase(rs.getBigDecimal("total_base"));
                    stat.setMontantIndicateur(rs.getBigDecimal("total_indicateur"));
                    stats.add(stat);
                }
            }

            logger.info("✅ {} centres trouvés avec données", stats.size());

        } catch (SQLException e) {
            logger.error("Erreur calcul stats", e);
        }

        return stats;
    }

    /**
     * Classe interne pour les statistiques
     */
    public static class CentreRepartitionStat {
        private Long centreId;
        private String codeCentre;
        private String nomCentre;
        private int nombreAffaires;
        private BigDecimal montantBase;
        private BigDecimal montantIndicateur;

        public BigDecimal getMontantTotal() {
            return montantBase.add(montantIndicateur);
        }

        // Getters et setters...
        public Long getCentreId() { return centreId; }
        public void setCentreId(Long centreId) { this.centreId = centreId; }

        public String getCodeCentre() { return codeCentre; }
        public void setCodeCentre(String codeCentre) { this.codeCentre = codeCentre; }

        public String getNomCentre() { return nomCentre; }
        public void setNomCentre(String nomCentre) { this.nomCentre = nomCentre; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantBase() { return montantBase; }
        public void setMontantBase(BigDecimal montantBase) { this.montantBase = montantBase; }

        public BigDecimal getMontantIndicateur() { return montantIndicateur; }
        public void setMontantIndicateur(BigDecimal montantIndicateur) { this.montantIndicateur = montantIndicateur; }
    }
}