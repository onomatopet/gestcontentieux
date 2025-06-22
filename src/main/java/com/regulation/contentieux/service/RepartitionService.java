package com.regulation.contentieux.service;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.dao.AgentDAO;
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
 * ENRICHI : DD et DG sont TOUJOURS bénéficiaires même sans participation
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
 *
 * ENRICHISSEMENT : DD et DG reçoivent leur part même s'ils n'ont pas participé
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

    // ENRICHISSEMENT : Taux spécifiques pour DD et DG (à définir selon les règles métier)
    private static final BigDecimal TAUX_DD = new BigDecimal("0.02");  // 2% du produit net ayants droits
    private static final BigDecimal TAUX_DG = new BigDecimal("0.03");  // 3% du produit net ayants droits

    private final AgentDAO agentDAO;

    public RepartitionService() {
        this.agentDAO = new AgentDAO();
    }

    /**
     * Calcule la répartition pour un encaissement
     * ENRICHISSEMENT : Inclut DD et DG systématiquement
     */
    public RepartitionResultat calculerRepartition(Encaissement encaissement, Affaire affaire) {
        logger.info("🧮 === CALCUL DE RÉPARTITION ENRICHI ===");
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

        // 5. ENRICHISSEMENT : Parts DD et DG (toujours bénéficiaires)
        BigDecimal partDD = produitNetAyantsDroits.multiply(TAUX_DD)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partDG = produitNetAyantsDroits.multiply(TAUX_DG)
                .setScale(0, RoundingMode.HALF_UP);

        resultat.setPartDD(partDD);
        resultat.setPartDG(partDG);

        logger.info("💰 Part DD (2% - TOUJOURS): {} FCFA", partDD);
        logger.info("💰 Part DG (3% - TOUJOURS): {} FCFA", partDG);

        // 6. Ajuster le produit net ayants droits après DD et DG
        BigDecimal produitNetAyantsDroitsAjuste = produitNetAyantsDroits
                .subtract(partDD)
                .subtract(partDG);

        // 7. Répartition niveau 2 (sur le montant ajusté)
        BigDecimal partChefs = produitNetAyantsDroitsAjuste.multiply(TAUX_CHEFS)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partSaisissants = produitNetAyantsDroitsAjuste.multiply(TAUX_SAISISSANTS)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partMutuelle = produitNetAyantsDroitsAjuste.multiply(TAUX_MUTUELLE)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partMasseCommune = produitNetAyantsDroitsAjuste.multiply(TAUX_MASSE_COMMUNE)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal partInteressement = produitNetAyantsDroitsAjuste.multiply(TAUX_INTERESSEMENT)
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

        // 8. Calcul des parts individuelles
        calculerPartsIndividuelles(resultat, affaire);

        // 9. Vérification de la cohérence
        verifierCoherence(resultat);

        return resultat;
    }

    /**
     * Calcule les parts individuelles des acteurs
     */
    private void calculerPartsIndividuelles(RepartitionResultat resultat, Affaire affaire) {
        logger.info("👥 === CALCUL DES PARTS INDIVIDUELLES ===");

        List<Agent> chefs = getChefs(affaire);
        List<Agent> saisissants = getSaisissants(affaire);

        // Parts des chefs
        if (!chefs.isEmpty()) {
            BigDecimal partParChef = resultat.getPartChefs()
                    .divide(new BigDecimal(chefs.size()), 0, RoundingMode.HALF_UP);

            for (Agent chef : chefs) {
                resultat.addPartIndividuelle(chef, partParChef, "CHEF");
                logger.info("👤 Chef {} - {} : {} FCFA",
                        chef.getCodeAgent(), chef.getNomComplet(), partParChef);
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

        // ENRICHISSEMENT : Ajouter DD et DG même s'ils n'ont pas participé
        ajouterBeneficiairePermanent(resultat, "DD", resultat.getPartDD());
        ajouterBeneficiairePermanent(resultat, "DG", resultat.getPartDG());
    }

    /**
     * ENRICHISSEMENT : Ajoute un bénéficiaire permanent (DD ou DG)
     */
    private void ajouterBeneficiairePermanent(RepartitionResultat resultat, String role, BigDecimal montant) {
        try {
            // Rechercher l'agent avec le rôle spécial DD ou DG
            String sql = """
                SELECT a.* FROM agents a
                JOIN roles_speciaux rs ON a.id = rs.agent_id
                WHERE rs.type_role = ? AND rs.actif = 1
                LIMIT 1
            """;

            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, role);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    // Utiliser la méthode find du DAO qui est publique
                    Long agentId = rs.getLong("id");
                    Optional<Agent> agentOpt = agentDAO.findById(agentId);

                    if (agentOpt.isPresent()) {
                        Agent beneficiaire = agentOpt.get();
                        resultat.addPartIndividuelle(beneficiaire, montant, role + "_PERMANENT");
                        logger.info("👤 {} (TOUJOURS bénéficiaire) - {} : {} FCFA",
                                role, beneficiaire.getNomComplet(), montant);
                    }
                } else {
                    logger.warn("⚠️ Aucun agent avec le rôle {} trouvé", role);
                    // Créer une entrée générique pour ne pas perdre la répartition
                    resultat.addBeneficiaireGenerique(role, montant);
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur lors de la recherche du bénéficiaire permanent " + role, e);
            // En cas d'erreur, créer une entrée générique
            resultat.addBeneficiaireGenerique(role, montant);
        }
    }

    /**
     * Vérifie s'il y a un indicateur pour l'affaire
     */
    private boolean hasIndicateur(Affaire affaire) {
        // Logique pour déterminer si un indicateur existe
        // À implémenter selon les règles métier
        return false; // Pour l'instant, pas d'indicateur
    }

    /**
     * Récupère les chefs de l'affaire
     */
    private List<Agent> getChefs(Affaire affaire) {
        List<Agent> chefs = new ArrayList<>();

        String sql = """
            SELECT agent_id FROM affaire_acteurs
            WHERE affaire_id = ? AND role_sur_affaire = 'CHEF'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaire.getId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Long agentId = rs.getLong("agent_id");
                agentDAO.findById(agentId).ifPresent(chefs::add);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des chefs", e);
        }

        return chefs;
    }

    /**
     * Récupère les saisissants de l'affaire
     */
    private List<Agent> getSaisissants(Affaire affaire) {
        List<Agent> saisissants = new ArrayList<>();

        String sql = """
            SELECT agent_id FROM affaire_acteurs
            WHERE affaire_id = ? AND role_sur_affaire = 'SAISISSANT'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaire.getId());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Long agentId = rs.getLong("agent_id");
                agentDAO.findById(agentId).ifPresent(saisissants::add);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des saisissants", e);
        }

        return saisissants;
    }

    /**
     * Vérifie la cohérence de la répartition
     */
    private void verifierCoherence(RepartitionResultat resultat) {
        BigDecimal total = BigDecimal.ZERO;

        // Somme de toutes les parts
        total = total.add(resultat.getPartIndicateur())
                .add(resultat.getPartFLCF())
                .add(resultat.getPartTresor())
                .add(resultat.getPartDD())
                .add(resultat.getPartDG())
                .add(resultat.getPartChefs())
                .add(resultat.getPartSaisissants())
                .add(resultat.getPartMutuelle())
                .add(resultat.getPartMasseCommune())
                .add(resultat.getPartInteressement());

        BigDecimal ecart = resultat.getProduitDisponible().subtract(total).abs();

        if (ecart.compareTo(new BigDecimal("10")) > 0) {
            logger.warn("⚠️ Écart de répartition détecté: {} FCFA", ecart);
        } else {
            logger.info("✅ Répartition cohérente - Écart: {} FCFA", ecart);
        }
    }

    /**
     * Enregistre la répartition en base de données
     */
    public void enregistrerRepartition(RepartitionResultat resultat) {
        logger.info("💾 Enregistrement de la répartition...");

        try {
            // Pour l'instant, on simule l'enregistrement
            // Le RepartitionDAO sera créé plus tard
            resultat.setId(System.currentTimeMillis()); // ID temporaire
            resultat.setCalculatedAt(java.time.LocalDateTime.now());

            logger.info("✅ Répartition enregistrée avec succès (simulation)");

        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'enregistrement de la répartition", e);
            throw new RuntimeException("Impossible d'enregistrer la répartition", e);
        }
    }

    /**
     * Génère un rapport de répartition pour un encaissement
     */
    public String genererRapportRepartition(Long encaissementId) {
        // À implémenter : génération du rapport PDF/Excel
        return "Rapport de répartition pour encaissement " + encaissementId;
    }
}