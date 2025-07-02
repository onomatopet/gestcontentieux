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
 * CORRIGÉ : DD et DG font partie du pool des chefs selon le cahier des charges
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
 *    - Part chefs = 15% × Produit net ayants droits (divisé entre chefs + DD + DG)
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

    private final AgentDAO agentDAO;
    private final RepartitionDAO repartitionDAO;

    public RepartitionService() {
        this.agentDAO = new AgentDAO();
        this.repartitionDAO = new RepartitionDAO();
    }

    /**
     * Calcule la répartition pour un encaissement
     */
    public RepartitionResultat calculerRepartition(Encaissement encaissement, Affaire affaire) {
        logger.info("🧮 === CALCUL DE RÉPARTITION ===");
        logger.info("🧮 Encaissement: {} - Montant: {}",
                encaissement.getReference(), encaissement.getMontantEncaisse());

        RepartitionResultat resultat = new RepartitionResultat();
        resultat.setEncaissement(encaissement);

        BigDecimal montantEncaisse = encaissement.getMontantEncaisse();
        resultat.setProduitDisponible(montantEncaisse);

        // 1. Calcul de la part indicateur (si existe)
        BigDecimal partIndicateur = BigDecimal.ZERO;
        if (hasIndicateur(affaire)) {
            partIndicateur = montantEncaisse.multiply(TAUX_INDICATEUR)
                    .setScale(0, RoundingMode.HALF_UP);
            resultat.setPartIndicateur(partIndicateur);
            logger.info("💰 Part indicateur (10%): {} FCFA", partIndicateur);
        }

        // 2. Calcul du produit net
        BigDecimal produitNet = montantEncaisse.subtract(partIndicateur);
        resultat.setProduitNet(produitNet);
        logger.info("💰 Produit net: {} FCFA", produitNet);

        // 3. Répartition niveau 1
        BigDecimal partFLCF = produitNet.multiply(TAUX_FLCF)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partTresor = produitNet.multiply(TAUX_TRESOR)
                .setScale(0, RoundingMode.HALF_UP);

        resultat.setPartFLCF(partFLCF);
        resultat.setPartTresor(partTresor);

        logger.info("💰 Part FLCF (10%): {} FCFA", partFLCF);
        logger.info("💰 Part Trésor (15%): {} FCFA", partTresor);

        // 4. Produit net ayants droits
        BigDecimal produitNetAyantsDroits = produitNet.subtract(partFLCF).subtract(partTresor);
        resultat.setProduitNetAyantsDroits(produitNetAyantsDroits);
        logger.info("💰 Produit net ayants droits: {} FCFA", produitNetAyantsDroits);

        // 5. Répartition niveau 2
        BigDecimal partChefs = produitNetAyantsDroits.multiply(TAUX_CHEFS)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partSaisissants = produitNetAyantsDroits.multiply(TAUX_SAISISSANTS)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partMutuelle = produitNetAyantsDroits.multiply(TAUX_MUTUELLE)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partMasseCommune = produitNetAyantsDroits.multiply(TAUX_MASSE_COMMUNE)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partInteressement = produitNetAyantsDroits.multiply(TAUX_INTERESSEMENT)
                .setScale(0, RoundingMode.HALF_UP);

        resultat.setPartChefs(partChefs);
        resultat.setPartSaisissants(partSaisissants);
        resultat.setPartMutuelle(partMutuelle);
        resultat.setPartMasseCommune(partMasseCommune);
        resultat.setPartInteressement(partInteressement);

        logger.info("💰 Part chefs (15%): {} FCFA", partChefs);
        logger.info("💰 Part saisissants (35%): {} FCFA", partSaisissants);
        logger.info("💰 Part mutuelle (5%): {} FCFA", partMutuelle);
        logger.info("💰 Part masse commune (30%): {} FCFA", partMasseCommune);
        logger.info("💰 Part intéressement (15%): {} FCFA", partInteressement);

        // 6. Calcul des parts individuelles
        calculerPartsIndividuelles(resultat, affaire);

        // 7. Vérification de la cohérence
        verifierCoherence(resultat);

        return resultat;
    }

    /**
     * Calcule les parts individuelles des acteurs
     * CORRIGÉ : DD et DG font partie du pool des chefs
     */
    private void calculerPartsIndividuelles(RepartitionResultat resultat, Affaire affaire) {
        logger.info("👥 === CALCUL DES PARTS INDIVIDUELLES ===");

        List<Agent> chefs = getChefs(affaire);
        List<Agent> saisissants = getSaisissants(affaire);

        // CORRECTION : DD et DG font partie du pool des chefs
        // Récupérer DD et DG
        Agent dd = getAgentDD();
        Agent dg = getAgentDG();

        // Créer une liste combinée pour les chefs + DD + DG
        List<Agent> beneficiairesChefs = new ArrayList<>(chefs);

        // Ajouter DD s'il existe et n'est pas déjà dans la liste
        if (dd != null && !beneficiairesChefs.stream().anyMatch(a -> a.getId().equals(dd.getId()))) {
            beneficiairesChefs.add(dd);
        }

        // Ajouter DG s'il existe et n'est pas déjà dans la liste
        if (dg != null && !beneficiairesChefs.stream().anyMatch(a -> a.getId().equals(dg.getId()))) {
            beneficiairesChefs.add(dg);
        }

        // Parts des chefs (incluant DD et DG)
        if (!beneficiairesChefs.isEmpty()) {
            BigDecimal partParBeneficiaire = resultat.getPartChefs()
                    .divide(new BigDecimal(beneficiairesChefs.size()), 0, RoundingMode.HALF_UP);

            for (Agent beneficiaire : beneficiairesChefs) {
                String role = "CHEF";

                // Identifier le rôle spécifique
                if (dd != null && beneficiaire.getId().equals(dd.getId())) {
                    role = "DD";
                } else if (dg != null && beneficiaire.getId().equals(dg.getId())) {
                    role = "DG";
                }

                resultat.addPartIndividuelle(beneficiaire, partParBeneficiaire, role);
                logger.info("👤 {} {} - {} : {} FCFA",
                        role, beneficiaire.getCodeAgent(), beneficiaire.getNomComplet(), partParBeneficiaire);
            }
        }

        // Parts des saisissants
        if (!saisissants.isEmpty()) {
            BigDecimal partParSaisissant = resultat.getPartSaisissants()
                    .divide(new BigDecimal(saisissants.size()), 0, RoundingMode.HALF_UP);

            for (Agent saisissant : saisissants) {
                resultat.addPartIndividuelle(saisissant, partParSaisissant, "SAISISSANT");
                logger.info("👤 Saisissant {} - {} : {} FCFA",
                        saisissant.getCodeAgent(), saisissant.getNomComplet(), partParSaisissant);
            }
        }
    }

    /**
     * Récupère l'agent DD
     */
    private Agent getAgentDD() {
        try {
            Optional<Agent> dd = agentDAO.findByRoleSpecial("DD");
            return dd.orElse(null);
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération du DD", e);
            return null;
        }
    }

    /**
     * Récupère l'agent DG
     */
    private Agent getAgentDG() {
        try {
            Optional<Agent> dg = agentDAO.findByRoleSpecial("DG");
            return dg.orElse(null);
        } catch (Exception e) {
            logger.error("Erreur lors de la récupération du DG", e);
            return null;
        }
    }

    /**
     * Vérifie s'il y a un indicateur pour l'affaire
     */
    private boolean hasIndicateur(Affaire affaire) {
        String sql = """
            SELECT COUNT(*) 
            FROM affaire_acteurs 
            WHERE affaire_id = ? 
            AND role_sur_affaire = 'INDICATEUR'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaire.getId());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification de l'indicateur", e);
        }

        return false;
    }

    /**
     * Récupère les chefs de l'affaire
     */
    private List<Agent> getChefs(Affaire affaire) {
        return getAgentsByRole(affaire, "CHEF");
    }

    /**
     * Récupère les saisissants de l'affaire
     */
    private List<Agent> getSaisissants(Affaire affaire) {
        return getAgentsByRole(affaire, "SAISISSANT");
    }

    /**
     * Récupère les agents par rôle pour une affaire
     */
    private List<Agent> getAgentsByRole(Affaire affaire, String role) {
        List<Agent> agents = new ArrayList<>();

        String sql = """
            SELECT a.* 
            FROM agents a
            INNER JOIN affaire_acteurs aa ON a.id = aa.agent_id
            WHERE aa.affaire_id = ? 
            AND aa.role_sur_affaire = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaire.getId());
            stmt.setString(2, role);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Agent agent = new Agent();
                agent.setId(rs.getLong("id"));
                agent.setCodeAgent(rs.getString("code_agent"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                agent.setGrade(rs.getString("grade"));
                agent.setServiceId(rs.getLong("service_id"));
                agent.setActif(rs.getBoolean("actif"));
                agents.add(agent);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des agents par rôle", e);
        }

        return agents;
    }

    /**
     * Vérifie la cohérence du calcul
     */
    private void verifierCoherence(RepartitionResultat resultat) {
        // Vérifier que la somme des parts niveau 2 = produit net ayants droits
        BigDecimal sommeNiveau2 = resultat.getPartChefs()
                .add(resultat.getPartSaisissants())
                .add(resultat.getPartMutuelle())
                .add(resultat.getPartMasseCommune())
                .add(resultat.getPartInteressement());

        BigDecimal difference = resultat.getProduitNetAyantsDroits().subtract(sommeNiveau2).abs();

        if (difference.compareTo(new BigDecimal("5")) > 0) {
            logger.warn("⚠️ Écart de cohérence détecté: {} FCFA", difference);
            /**
             * Enregistre la répartition en base de données
             */
            public void enregistrerRepartition(RepartitionResultat resultat) {
                logger.info("💾 Enregistrement de la répartition...");

                try {
                    // Enregistrer la répartition principale
                    RepartitionResultat saved = repartitionDAO.save(resultat);

                    // Enregistrer les parts individuelles
                    if (resultat.getPartsIndividuelles() != null && !resultat.getPartsIndividuelles().isEmpty()) {
                        for (RepartitionResultat.PartIndividuelle part : resultat.getPartsIndividuelles()) {
                            repartitionDAO.savePartIndividuelle(saved.getId(), part);
                        }
                    }

                    logger.info("✅ Répartition enregistrée avec succès - ID: {}", saved.getId());

                } catch (Exception e) {
                    logger.error("❌ Erreur lors de l'enregistrement de la répartition", e);
                    throw new RuntimeException("Impossible d'enregistrer la répartition", e);
                }
            }