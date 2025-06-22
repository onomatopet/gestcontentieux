package com.regulation.contentieux.dao;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.model.RepartitionResultat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * DAO pour la gestion des répartitions
 * Gère la persistance des résultats de calcul de répartition
 */
public class RepartitionDAO {

    private static final Logger logger = LoggerFactory.getLogger(RepartitionDAO.class);

    /**
     * Sauvegarde un résultat de répartition
     */
    public RepartitionResultat save(RepartitionResultat repartition) {
        String sql = """
            INSERT INTO repartition_resultats (
                encaissement_id, produit_disponible, part_indicateur, produit_net,
                part_flcf, part_tresor, produit_net_ayants_droits,
                part_dd, part_dg, part_chefs, part_saisissants,
                part_mutuelle, part_masse_commune, part_interessement,
                calculated_at, calculated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, repartition.getEncaissement().getId());
            stmt.setBigDecimal(2, repartition.getProduitDisponible());
            stmt.setBigDecimal(3, repartition.getPartIndicateur());
            stmt.setBigDecimal(4, repartition.getProduitNet());
            stmt.setBigDecimal(5, repartition.getPartFLCF());
            stmt.setBigDecimal(6, repartition.getPartTresor());
            stmt.setBigDecimal(7, repartition.getProduitNetAyantsDroits());
            stmt.setBigDecimal(8, repartition.getPartDD());
            stmt.setBigDecimal(9, repartition.getPartDG());
            stmt.setBigDecimal(10, repartition.getPartChefs());
            stmt.setBigDecimal(11, repartition.getPartSaisissants());
            stmt.setBigDecimal(12, repartition.getPartMutuelle());
            stmt.setBigDecimal(13, repartition.getPartMasseCommune());
            stmt.setBigDecimal(14, repartition.getPartInteressement());
            stmt.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(16, repartition.getCalculatedBy());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        repartition.setId(generatedKeys.getLong(1));
                        logger.info("Répartition sauvegardée avec l'ID: {}", repartition.getId());
                    }
                }
            }

            return repartition;

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde de la répartition", e);
            throw new RuntimeException("Impossible de sauvegarder la répartition", e);
        }
    }

    /**
     * Sauvegarde une part individuelle
     */
    public void savePartIndividuelle(Long repartitionId, RepartitionResultat.PartIndividuelle part) {
        String sql = """
            INSERT INTO repartition_parts_individuelles (
                repartition_id, agent_id, montant, role, description
            ) VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, repartitionId);
            stmt.setLong(2, part.getAgent().getId());
            stmt.setBigDecimal(3, part.getMontant());
            stmt.setString(4, part.getRole());
            stmt.setString(5, part.getDescription());

            stmt.executeUpdate();
            logger.debug("Part individuelle sauvegardée pour l'agent {}", part.getAgent().getId());

        } catch (SQLException e) {
            logger.error("Erreur lors de la sauvegarde de la part individuelle", e);
            throw new RuntimeException("Impossible de sauvegarder la part individuelle", e);
        }
    }

    /**
     * Trouve une répartition par l'ID de l'encaissement
     */
    public RepartitionResultat findByEncaissementId(Long encaissementId) {
        String sql = """
            SELECT * FROM repartition_resultats
            WHERE encaissement_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, encaissementId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToEntity(rs);
            }

            return null;

        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche de la répartition", e);
            return null;
        }
    }

    /**
     * Mappe un ResultSet vers une entité RepartitionResultat
     */
    private RepartitionResultat mapResultSetToEntity(ResultSet rs) throws SQLException {
        RepartitionResultat resultat = new RepartitionResultat();

        resultat.setId(rs.getLong("id"));
        resultat.setProduitDisponible(rs.getBigDecimal("produit_disponible"));
        resultat.setPartIndicateur(rs.getBigDecimal("part_indicateur"));
        resultat.setProduitNet(rs.getBigDecimal("produit_net"));
        resultat.setPartFLCF(rs.getBigDecimal("part_flcf"));
        resultat.setPartTresor(rs.getBigDecimal("part_tresor"));
        resultat.setProduitNetAyantsDroits(rs.getBigDecimal("produit_net_ayants_droits"));
        resultat.setPartDD(rs.getBigDecimal("part_dd"));
        resultat.setPartDG(rs.getBigDecimal("part_dg"));
        resultat.setPartChefs(rs.getBigDecimal("part_chefs"));
        resultat.setPartSaisissants(rs.getBigDecimal("part_saisissants"));
        resultat.setPartMutuelle(rs.getBigDecimal("part_mutuelle"));
        resultat.setPartMasseCommune(rs.getBigDecimal("part_masse_commune"));
        resultat.setPartInteressement(rs.getBigDecimal("part_interessement"));

        Timestamp calculatedAt = rs.getTimestamp("calculated_at");
        if (calculatedAt != null) {
            resultat.setCalculatedAt(calculatedAt.toLocalDateTime());
        }

        resultat.setCalculatedBy(rs.getString("calculated_by"));

        return resultat;
    }
}