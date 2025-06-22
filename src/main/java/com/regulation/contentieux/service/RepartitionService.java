package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.dao.RepartitionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Service pour le calcul des répartitions selon le cahier des charges
 *
 * FORMULES DE CALCUL :
 * 1. Si indicateur réel existe:
 *    - Part indicateur = 10% × Montant encaissé
 *    - Produit net = Montant encaissé - Part indicateur
 * 2. Sinon:
 *    - Produit net = Montant encaissé
 *
 * 3. Répartition niveau 1:
 *    - FLCF = 10% × Produit net
 *    - Trésor = 15% × Produit net
 *    - Produit net ayants droits = Produit net - FLCF - Trésor
 *
 * 4. Répartition niveau 2:
 *    - Part chefs = 15% × Produit net ayants droits
 *    - Part saisissants = 35% × Produit net ayants droits
 *    - Mutuelle nationale = 5% × Produit net ayants droits
 *    - Masse commune = 30% × Produit net ayants droits
 *    - Intéressement = 15% × Produit net ayants droits
 */
public class RepartitionService {

    private static final Logger logger = LoggerFactory.getLogger(RepartitionService.class);

    // Taux de répartition selon le cahier des charges
    private static final BigDecimal TAUX_INDICATEUR = new BigDecimal("0.10");      // 10%
    private static final BigDecimal TAUX_FLCF = new BigDecimal("0.10");           // 10%
    private static final BigDecimal TAUX_TRESOR = new BigDecimal("0.15");         // 15%
    private static final BigDecimal TAUX_CHEFS = new BigDecimal("0.15");          // 15%
    private static final BigDecimal TAUX_SAISISSANTS = new BigDecimal("0.35");    // 35%
    private static final BigDecimal TAUX_MUTUELLE = new BigDecimal("0.05");       // 5%
    private static final BigDecimal TAUX_MASSE_COMMUNE = new BigDecimal("0.30");  // 30%
    private static final BigDecimal TAUX_INTERESSEMENT = new BigDecimal("0.15");  // 15%

    private final RepartitionDAO repartitionDAO;
    private final AgentDAO agentDAO;

    public RepartitionService() {
        this.repartitionDAO = new RepartitionDAO();
        this.agentDAO = new AgentDAO();
    }

    /**
     * Calcule la répartition pour un encaissement
     * ENRICHISSEMENT : Respect strict des formules du cahier des charges
     */
    public RepartitionResultat calculerRepartition(Encaissement encaissement, Affaire affaire) {
        logger.info("🧮 === CALCUL DE RÉPARTITION ===");
        logger.info("🧮 Encaissement: {} - Montant: {}",
                encaissement.getReference(), encaissement.getMontantEncaisse());

        RepartitionResultat resultat = new RepartitionResultat();
        resultat.setEncaissement(encaissement);

        BigDecimal montantEncaisse = encaissement.getMontantEncaisse();
        resultat.setProduitDisponible(montantEncaisse);

        // 1. Calcul de la part indicateur (si indicateur réel existe)
        BigDecimal partIndicateur = BigDecimal.ZERO;
        BigDecimal produitNet;

        if (affaire.hasIndicateurReel()) {
            partIndicateur = montantEncaisse.multiply(TAUX_INDICATEUR)
                    .setScale(2, RoundingMode.HALF_UP);
            produitNet = montantEncaisse.subtract(partIndicateur);

            logger.info("✅ Indicateur réel présent");
            logger.info("   - Part indicateur (10%): {}", partIndicateur);
            logger.info("   - Produit net: {}", produitNet);
        } else {
            produitNet = montantEncaisse;
            logger.info("ℹ️ Pas d'indicateur réel");
            logger.info("   - Produit net = Montant encaissé: {}", produitNet);
        }

        resultat.setPartIndicateur(partIndicateur);
        resultat.setProduitNet(produitNet);

        // 2. Répartition niveau 1
        BigDecimal partFlcf = produitNet.multiply(TAUX_FLCF)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal partTresor = produitNet.multiply(TAUX_TRESOR)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal produitNetDroits = produitNet.subtract(partFlcf).subtract(partTresor);

        resultat.setPartFlcf(partFlcf);
        resultat.setPartTresor(partTresor);
        resultat.setProduitNetDroits(produitNetDroits);

        logger.info("📊 Répartition niveau 1:");
        logger.info("   - FLCF (10%): {}", partFlcf);
        logger.info("   - Trésor (15%): {}", partTresor);
        logger.info("   - Produit net ayants droits: {}", produitNetDroits);

        // 3. Répartition niveau 2
        BigDecimal partChefs = produitNetDroits.multiply(TAUX_CHEFS)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal partSaisissants = produitNetDroits.multiply(TAUX_SAISISSANTS)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal partMutuelle = produitNetDroits.multiply(TAUX_MUTUELLE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal partMasseCommune = produitNetDroits.multiply(TAUX_MASSE_COMMUNE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal partInteressement = produitNetDroits.multiply(TAUX_INTERESSEMENT)
                .setScale(2, RoundingMode.HALF_UP);

        resultat.setPartChefs(partChefs);
        resultat.setPartSaisissants(partSaisissants);
        resultat.setPartMutuelle(partMutuelle);
        resultat.setPartMasseCommune(partMasseCommune);
        resultat.setPartInteressement(partInteressement);

        logger.info("📊 Répartition niveau 2:");
        logger.info("   - Part chefs (15%): {}", partChefs);
        logger.info("   - Part saisissants (35%): {}", partSaisissants);
        logger.info("   - Mutuelle (5%): {}", partMutuelle);
        logger.info("   - Masse commune (30%): {}", partMasseCommune);
        logger.info("   - Intéressement (15%): {}", partInteressement);

        // 4. Calcul des parts individuelles
        Map<Agent, BigDecimal> detailsAgents = calculerPartsIndividuelles(affaire, partChefs, partSaisissants);
        resultat.setDetailsAgents(detailsAgents);

        // 5. Vérification de cohérence
        verifierCoherence(resultat);

        logger.info("✅ Calcul de répartition terminé");

        return resultat;
    }

    /**
     * Calcule les parts individuelles des agents
     * RÈGLES du cahier des charges :
     * - Chefs (15%) : Division égale entre chefs participants + DD + DG (toujours inclus)
     * - Saisissants (35%) : Division égale entre tous les saisissants
     */
    private Map<Agent, BigDecimal> calculerPartsIndividuelles(Affaire affaire,
                                                              BigDecimal partChefs,
                                                              BigDecimal partSaisissants) {
        Map<Agent, BigDecimal> details = new HashMap<>();

        // Récupérer les acteurs de l'affaire
        List<Agent> chefs = getChefsByAffaire(affaire.getId());
        List<Agent> saisissants = getSaisissantsByAffaire(affaire.getId());

        // IMPORTANT : Ajouter DD et DG même s'ils ne participent pas
        Agent dd = getAgentDD();
        Agent dg = getAgentDG();

        // Calculer le nombre total de bénéficiaires pour les chefs
        int nombreChefs = chefs.size();
        if (dd != null && !chefs.contains(dd)) {
            chefs.add(dd);
            nombreChefs++;
        }
        if (dg != null && !chefs.contains(dg)) {
            chefs.add(dg);
            nombreChefs++;
        }

        logger.info("👥 Répartition individuelle:");
        logger.info("   - Nombre de chefs (incluant DD/DG): {}", nombreChefs);
        logger.info("   - Nombre de saisissants: {}", saisissants.size());

        // Répartir la part des chefs
        if (nombreChefs > 0) {
            BigDecimal partParChef = partChefs.divide(
                    new BigDecimal(nombreChefs), 2, RoundingMode.HALF_UP);

            for (Agent chef : chefs) {
                details.put(chef, partParChef);
                logger.debug("   - Chef {} : {}", chef.getNomComplet(), partParChef);
            }
        }

        // Répartir la part des saisissants
        if (!saisissants.isEmpty()) {
            BigDecimal partParSaisissant = partSaisissants.divide(
                    new BigDecimal(saisissants.size()), 2, RoundingMode.HALF_UP);

            for (Agent saisissant : saisissants) {
                BigDecimal partExistante = details.getOrDefault(saisissant, BigDecimal.ZERO);
                details.put(saisissant, partExistante.add(partParSaisissant));
                logger.debug("   - Saisissant {} : {}", saisissant.getNomComplet(), partParSaisissant);
            }
        }

        return details;
    }

    /**
     * Récupère les chefs d'une affaire
     */
    private List<Agent> getChefsByAffaire(Long affaireId) {
        List<Agent> chefs = new ArrayList<>();
        String sql = """
            SELECT a.* FROM agents a
            JOIN affaire_acteurs aa ON a.id = aa.agent_id
            WHERE aa.affaire_id = ? AND aa.role_sur_affaire = 'CHEF'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Agent agent = new Agent();
                agent.setId(rs.getLong("id"));
                agent.setCodeAgent(rs.getString("code_agent"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                chefs.add(agent);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des chefs", e);
        }

        return chefs;
    }

    /**
     * Récupère les saisissants d'une affaire
     */
    private List<Agent> getSaisissantsByAffaire(Long affaireId) {
        List<Agent> saisissants = new ArrayList<>();
        String sql = """
            SELECT a.* FROM agents a
            JOIN affaire_acteurs aa ON a.id = aa.agent_id
            WHERE aa.affaire_id = ? AND aa.role_sur_affaire = 'SAISISSANT'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Agent agent = new Agent();
                agent.setId(rs.getLong("id"));
                agent.setCodeAgent(rs.getString("code_agent"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                saisissants.add(agent);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des saisissants", e);
        }

        return saisissants;
    }

    /**
     * Récupère l'agent DD
     */
    private Agent getAgentDD() {
        return agentDAO.findByRoleSpecial("DD").orElse(null);
    }

    /**
     * Récupère l'agent DG
     */
    private Agent getAgentDG() {
        return agentDAO.findByRoleSpecial("DG").orElse(null);
    }

    /**
     * Vérifie la cohérence du calcul
     */
    private void verifierCoherence(RepartitionResultat resultat) {
        logger.info("🔍 Vérification de cohérence...");

        // Vérifier que la somme niveau 1 = produit net
        BigDecimal sommeNiveau1 = resultat.getPartFlcf()
                .add(resultat.getPartTresor())
                .add(resultat.getProduitNetDroits());

        if (sommeNiveau1.compareTo(resultat.getProduitNet()) != 0) {
            logger.error("❌ ERREUR: Somme niveau 1 ({}) != Produit net ({})",
                    sommeNiveau1, resultat.getProduitNet());
        }

        // Vérifier que la somme niveau 2 = produit net droits
        BigDecimal sommeNiveau2 = resultat.getPartChefs()
                .add(resultat.getPartSaisissants())
                .add(resultat.getPartMutuelle())
                .add(resultat.getPartMasseCommune())
                .add(resultat.getPartInteressement());

        if (sommeNiveau2.compareTo(resultat.getProduitNetDroits()) != 0) {
            logger.error("❌ ERREUR: Somme niveau 2 ({}) != Produit net droits ({})",
                    sommeNiveau2, resultat.getProduitNetDroits());
        } else {
            logger.info("✅ Cohérence vérifiée: les totaux correspondent");
        }

        // Vérifier les pourcentages
        verifierPourcentages(resultat);
    }

    /**
     * Vérifie que les pourcentages sont corrects
     */
    private void verifierPourcentages(RepartitionResultat resultat) {
        // Vérifier FLCF = 10% du produit net
        BigDecimal flcfCalcule = resultat.getProduitNet().multiply(TAUX_FLCF)
                .setScale(2, RoundingMode.HALF_UP);
        if (flcfCalcule.compareTo(resultat.getPartFlcf()) != 0) {
            logger.warn("⚠️ Écart FLCF: calculé={}, réel={}", flcfCalcule, resultat.getPartFlcf());
        }

        // Vérifier Trésor = 15% du produit net
        BigDecimal tresorCalcule = resultat.getProduitNet().multiply(TAUX_TRESOR)
                .setScale(2, RoundingMode.HALF_UP);
        if (tresorCalcule.compareTo(resultat.getPartTresor()) != 0) {
            logger.warn("⚠️ Écart Trésor: calculé={}, réel={}", tresorCalcule, resultat.getPartTresor());
        }

        // Afficher le récapitulatif des pourcentages
        logger.info("📊 Vérification des pourcentages:");
        logger.info("   - FLCF: {}% (attendu: 10%)",
                resultat.getPartFlcf().multiply(new BigDecimal("100"))
                        .divide(resultat.getProduitNet(), 2, RoundingMode.HALF_UP));
        logger.info("   - Trésor: {}% (attendu: 15%)",
                resultat.getPartTresor().multiply(new BigDecimal("100"))
                        .divide(resultat.getProduitNet(), 2, RoundingMode.HALF_UP));
    }

    /**
     * Sauvegarde le résultat de répartition en base
     */
    public void sauvegarderRepartition(RepartitionResultat resultat) {
        try {
            // Sauvegarder le résultat principal
            repartitionDAO.create(resultat);

            // Sauvegarder les détails par agent
            sauvegarderDetailsAgents(resultat);

            logger.info("✅ Répartition sauvegardée en base");

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la sauvegarde de la répartition", e);
            throw new RuntimeException("Erreur de sauvegarde", e);
        }
    }

    /**
     * Sauvegarde les détails par agent
     */
    private void sauvegarderDetailsAgents(RepartitionResultat resultat) throws SQLException {
        String sql = """
            INSERT INTO repartition_details 
            (repartition_resultat_id, agent_id, type_part, montant)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (Map.Entry<Agent, BigDecimal> entry : resultat.getDetailsAgents().entrySet()) {
                stmt.setLong(1, resultat.getId());
                stmt.setLong(2, entry.getKey().getId());
                stmt.setString(3, determinerTypePart(entry.getKey()));
                stmt.setBigDecimal(4, entry.getValue());
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Détermine le type de part d'un agent
     */
    private String determinerTypePart(Agent agent) {
        if ("DG".equals(agent.getRoleSpecial())) return "DG";
        if ("DD".equals(agent.getRoleSpecial())) return "DD";
        // TODO: Vérifier le rôle dans l'affaire
        return "MIXTE"; // Peut être à la fois chef et saisissant
    }
}