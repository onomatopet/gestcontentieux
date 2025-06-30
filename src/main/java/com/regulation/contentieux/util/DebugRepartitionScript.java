package com.regulation.contentieux.util;

import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Scanner;

/**
 * Script de débogage pour analyser la répartition des parts
 * Version simplifiée qui n'utilise pas RepartitionResultat
 */
public class DebugRepartitionScript {

    private static final Logger logger = LoggerFactory.getLogger(DebugRepartitionScript.class);

    private final AffaireDAO affaireDAO;
    private final AgentDAO agentDAO;
    private final EncaissementDAO encaissementDAO;

    public DebugRepartitionScript() {
        this.affaireDAO = new AffaireDAO();
        this.agentDAO = new AgentDAO();
        this.encaissementDAO = new EncaissementDAO();
    }

    /**
     * Méthode principale pour déboguer par numéro d'affaire
     */
    public void debugParAffaire(String numeroAffaire) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 DÉBOGAGE RÉPARTITION - AFFAIRE : " + numeroAffaire);
        System.out.println("=".repeat(100));

        try {
            // 1. Récupérer l'affaire
            Affaire affaire = null;
            String sql = "SELECT * FROM affaires WHERE numero_affaire = ?";
            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, numeroAffaire);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    affaire = new Affaire();
                    affaire.setId(rs.getLong("id"));
                    affaire.setNumeroAffaire(rs.getString("numero_affaire"));
                    affaire.setDateCreation(rs.getDate("date_creation").toLocalDate());
                    affaire.setMontantAmendeTotal(rs.getBigDecimal("montant_amende_total"));
                } else {
                    System.err.println("❌ Affaire non trouvée : " + numeroAffaire);
                    return;
                }
            }

            afficherDetailsAffaire(affaire);

            // 2. Récupérer les acteurs de l'affaire
            List<ActeurInfo> acteurs = getActeursAffaire(affaire.getId());
            afficherActeursAffaire(acteurs);

            // 3. Récupérer tous les encaissements
            List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
            System.out.println("\n📊 ENCAISSEMENTS : " + encaissements.size());

            BigDecimal totalParts = BigDecimal.ZERO;

            // 4. Pour chaque encaissement, calculer et afficher la répartition
            for (Encaissement enc : encaissements) {
                if (enc.getStatut() == StatutEncaissement.VALIDE) {
                    System.out.println("\n" + "-".repeat(80));
                    System.out.println("💰 ENCAISSEMENT : " + enc.getReference());
                    System.out.println("   Date : " + enc.getDateEncaissement());
                    System.out.println("   Montant : " + formatMontant(enc.getMontantEncaisse()) + " FCFA");

                    // Afficher le détail du calcul manuel
                    afficherDetailCalculManuel(enc, acteurs);

                    totalParts = totalParts.add(enc.getMontantEncaisse());
                }
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("💎 TOTAL ENCAISSÉ : " + formatMontant(totalParts) + " FCFA");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            logger.error("Erreur lors du débogage affaire", e);
            e.printStackTrace();
        }
    }

    /**
     * Méthode pour déboguer par agent
     */
    public void debugParAgent(String codeAgent, LocalDate dateDebut, LocalDate dateFin) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 DÉBOGAGE RÉPARTITION - AGENT : " + codeAgent);
        System.out.println("   Période : " + dateDebut + " au " + dateFin);
        System.out.println("=".repeat(100));

        try {
            // 1. Récupérer l'agent
            Agent agent = null;
            String sql = "SELECT * FROM agents WHERE code_agent = ?";
            try (Connection conn = DatabaseConfig.getSQLiteConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, codeAgent);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    agent = new Agent();
                    agent.setId(rs.getLong("id"));
                    agent.setCodeAgent(rs.getString("code_agent"));
                    agent.setNom(rs.getString("nom"));
                    agent.setPrenom(rs.getString("prenom"));
                    agent.setGrade(rs.getString("grade"));
                    agent.setActif(rs.getBoolean("actif"));
                } else {
                    System.err.println("❌ Agent non trouvé : " + codeAgent);
                    return;
                }
            }

            afficherDetailsAgent(agent);

            // 2. Récupérer toutes les affaires où l'agent est impliqué
            List<AffaireRole> affairesAgent = getAffairesAgent(agent.getId(), dateDebut, dateFin);
            System.out.println("\n📊 AFFAIRES IMPLIQUÉES : " + affairesAgent.size());

            // Totaux par rôle
            BigDecimal totalChef = BigDecimal.ZERO;
            BigDecimal totalSaisissant = BigDecimal.ZERO;
            BigDecimal totalDG = BigDecimal.ZERO;
            BigDecimal totalDD = BigDecimal.ZERO;

            // 3. Pour chaque affaire, calculer la part de l'agent
            for (AffaireRole ar : affairesAgent) {
                Optional<Affaire> affaireOpt = affaireDAO.findById(ar.affaireId);
                if (affaireOpt.isPresent()) {
                    Affaire affaire = affaireOpt.get();

                    System.out.println("\n" + "-".repeat(80));
                    System.out.println("📄 AFFAIRE : " + affaire.getNumeroAffaire());
                    System.out.println("   Rôle : " + ar.role);

                    // Récupérer les encaissements de cette affaire dans la période
                    List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());

                    for (Encaissement enc : encaissements) {
                        if (enc.getStatut() == StatutEncaissement.VALIDE &&
                                !enc.getDateEncaissement().isBefore(dateDebut) &&
                                !enc.getDateEncaissement().isAfter(dateFin)) {

                            // Calculer la part de l'agent selon son rôle
                            BigDecimal partAgent = calculerPartAgentManuel(enc, ar.role, affaire.getId(), agent.getId());

                            System.out.println("   💰 Encaissement " + enc.getReference() + " : " +
                                    formatMontant(partAgent) + " FCFA");

                            // Ajouter aux totaux
                            switch (ar.role) {
                                case "Chef":
                                    totalChef = totalChef.add(partAgent);
                                    break;
                                case "Saisissant":
                                    totalSaisissant = totalSaisissant.add(partAgent);
                                    break;
                            }
                        }
                    }
                }
            }

            // 4. Vérifier si l'agent est DG ou DD
            String roleSpecial = getRoleSpecialAgent(agent.getId());
            if (roleSpecial != null) {
                System.out.println("\n🎖️ RÔLE SPÉCIAL : " + roleSpecial);

                // Calculer les parts DG/DD sur TOUTES les affaires
                BigDecimal partSpeciale = calculerPartRoleSpecial(roleSpecial, dateDebut, dateFin);

                if ("DG".equals(roleSpecial)) {
                    totalDG = partSpeciale;
                } else if ("DD".equals(roleSpecial)) {
                    totalDD = partSpeciale;
                }
            }

            // 5. Afficher le récapitulatif
            System.out.println("\n" + "=".repeat(80));
            System.out.println("📊 RÉCAPITULATIF DES PARTS");
            System.out.println("=".repeat(80));
            System.out.println("   Part en tant que Chef      : " + formatMontant(totalChef) + " FCFA");
            System.out.println("   Part en tant que Saisissant: " + formatMontant(totalSaisissant) + " FCFA");
            System.out.println("   Part en tant que DG        : " + formatMontant(totalDG) + " FCFA");
            System.out.println("   Part en tant que DD        : " + formatMontant(totalDD) + " FCFA");
            System.out.println("   " + "-".repeat(50));
            BigDecimal totalGeneral = totalChef.add(totalSaisissant).add(totalDG).add(totalDD);
            System.out.println("   💎 TOTAL GÉNÉRAL           : " + formatMontant(totalGeneral) + " FCFA");
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            logger.error("Erreur lors du débogage agent", e);
            e.printStackTrace();
        }
    }

    /**
     * Affiche le détail du calcul manuel de répartition
     * CALCULS CORRECTS selon le cahier des charges
     */
    private void afficherDetailCalculManuel(Encaissement enc, List<ActeurInfo> acteurs) {
        System.out.println("\n   📐 CALCUL DE RÉPARTITION (Cahier des charges) :");
        System.out.println("   " + "-".repeat(60));

        BigDecimal montantEncaisse = enc.getMontantEncaisse();

        // Base de calcul
        System.out.println("   Montant encaissé         : " + formatMontant(montantEncaisse) + " FCFA");

        // 1. TOUJOURS calculer la part indicateur (10%)
        BigDecimal partIndicateur = montantEncaisse.multiply(new BigDecimal("0.10"));
        BigDecimal produitNet = montantEncaisse.subtract(partIndicateur);

        System.out.println("\n   📌 ÉTAPE 1 - PART INDICATEUR (toujours calculée) :");
        System.out.println("   - Part indicateur (10%)  : " + formatMontant(partIndicateur) + " FCFA");
        System.out.println("   - Produit net (90%)      : " + formatMontant(produitNet) + " FCFA");

        // 2. Répartition niveau 1 sur le PRODUIT NET
        BigDecimal partFLCF = produitNet.multiply(new BigDecimal("0.10"));
        BigDecimal partTresor = produitNet.multiply(new BigDecimal("0.15"));
        BigDecimal produitAyantsDroits = produitNet.subtract(partFLCF).subtract(partTresor);

        System.out.println("\n   📌 ÉTAPE 2 - RÉPARTITION NIVEAU 1 (sur produit net) :");
        System.out.println("   - Part FLCF (10% PN)     : " + formatMontant(partFLCF) + " FCFA");
        System.out.println("   - Part Trésor (15% PN)   : " + formatMontant(partTresor) + " FCFA");
        System.out.println("   - Produit ayants droits  : " + formatMontant(produitAyantsDroits) + " FCFA (75% du PN)");

        // 3. Répartition niveau 2 sur le PRODUIT AYANTS DROITS
        BigDecimal partChefs = produitAyantsDroits.multiply(new BigDecimal("0.15"));
        BigDecimal partSaisissants = produitAyantsDroits.multiply(new BigDecimal("0.35"));
        BigDecimal partMutuelle = produitAyantsDroits.multiply(new BigDecimal("0.05"));
        BigDecimal partMasseCommune = produitAyantsDroits.multiply(new BigDecimal("0.30"));
        BigDecimal partInteressement = produitAyantsDroits.multiply(new BigDecimal("0.15"));

        System.out.println("\n   📌 ÉTAPE 3 - RÉPARTITION AYANTS DROITS :");

        // Compter les acteurs par rôle
        long nbChefs = acteurs.stream().filter(a -> "Chef".equals(a.role)).count();
        long nbSaisissants = acteurs.stream().filter(a -> "Saisissant".equals(a.role)).count();

        // Chercher DD et DG
        boolean hasDG = hasRoleSpecial("DG");
        boolean hasDD = hasRoleSpecial("DD");
        long totalBeneficiairesChefs = nbChefs + (hasDG ? 1 : 0) + (hasDD ? 1 : 0);

        System.out.println("   - Part chefs (15% PAD)   : " + formatMontant(partChefs) + " FCFA");
        if (totalBeneficiairesChefs > 0) {
            BigDecimal partParBeneficiaire = partChefs.divide(BigDecimal.valueOf(totalBeneficiairesChefs), 2, RoundingMode.HALF_UP);
            System.out.println("     → " + nbChefs + " chef(s) + " + (hasDD ? "DD + " : "") + (hasDG ? "DG" : ""));
            System.out.println("     → " + totalBeneficiairesChefs + " bénéficiaire(s) = " + formatMontant(partParBeneficiaire) + " FCFA chacun");
        }

        System.out.println("   - Part saisissants (35% PAD): " + formatMontant(partSaisissants) + " FCFA");
        if (nbSaisissants > 0) {
            BigDecimal partParSaisissant = partSaisissants.divide(BigDecimal.valueOf(nbSaisissants), 2, RoundingMode.HALF_UP);
            System.out.println("     → " + nbSaisissants + " saisissant(s) = " + formatMontant(partParSaisissant) + " FCFA chacun");
        }

        System.out.println("   - Part mutuelle (5% PAD) : " + formatMontant(partMutuelle) + " FCFA");
        System.out.println("   - Masse commune (30% PAD): " + formatMontant(partMasseCommune) + " FCFA");
        System.out.println("   - Intéressement (15% PAD): " + formatMontant(partInteressement) + " FCFA");

        // Vérification
        BigDecimal totalCalcule = partIndicateur
                .add(partFLCF)
                .add(partTresor)
                .add(partChefs)
                .add(partSaisissants)
                .add(partMutuelle)
                .add(partMasseCommune)
                .add(partInteressement);

        System.out.println("\n   ✅ VÉRIFICATION :");
        System.out.println("   Total calculé : " + formatMontant(totalCalcule) + " FCFA");
        System.out.println("   Montant encaissé : " + formatMontant(montantEncaisse) + " FCFA");
        BigDecimal ecart = montantEncaisse.subtract(totalCalcule);
        System.out.println("   Écart : " + formatMontant(ecart) + " FCFA " + (ecart.abs().compareTo(BigDecimal.ONE) <= 0 ? "✓" : "⚠️"));
    }

    /**
     * Calcule manuellement la part d'un agent selon son rôle
     * CALCULS CORRECTS selon le cahier des charges
     */
    private BigDecimal calculerPartAgentManuel(Encaissement enc, String role, Long affaireId, Long agentId) {
        BigDecimal montantEncaisse = enc.getMontantEncaisse();

        // 1. Part indicateur TOUJOURS 10%
        BigDecimal partIndicateur = montantEncaisse.multiply(new BigDecimal("0.10"));
        BigDecimal produitNet = montantEncaisse.subtract(partIndicateur);

        // 2. Calcul du produit ayants droits
        BigDecimal partFLCF = produitNet.multiply(new BigDecimal("0.10"));
        BigDecimal partTresor = produitNet.multiply(new BigDecimal("0.15"));
        BigDecimal produitAyantsDroits = produitNet.subtract(partFLCF).subtract(partTresor);

        switch (role) {
            case "Chef":
                // Les chefs partagent 15% AVEC DD et DG
                long nbChefs = compterActeursParRole(affaireId, "Chef");
                boolean hasDG = hasRoleSpecial("DG");
                boolean hasDD = hasRoleSpecial("DD");
                long totalBeneficiaires = nbChefs + (hasDG ? 1 : 0) + (hasDD ? 1 : 0);

                if (totalBeneficiaires > 0) {
                    BigDecimal partChefs = produitAyantsDroits.multiply(new BigDecimal("0.15"));
                    return partChefs.divide(BigDecimal.valueOf(totalBeneficiaires), 2, RoundingMode.HALF_UP);
                }
                break;

            case "Saisissant":
                long nbSaisissants = compterActeursParRole(affaireId, "Saisissant");
                if (nbSaisissants > 0) {
                    BigDecimal partSaisissants = produitAyantsDroits.multiply(new BigDecimal("0.35"));
                    return partSaisissants.divide(BigDecimal.valueOf(nbSaisissants), 2, RoundingMode.HALF_UP);
                }
                break;
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calcule la part totale pour un rôle spécial (DG/DD)
     * DD et DG sont inclus dans la part des chefs (15%)
     */
    private BigDecimal calculerPartRoleSpecial(String role, LocalDate dateDebut, LocalDate dateFin) {
        BigDecimal total = BigDecimal.ZERO;

        String sql = """
            SELECT e.montant_encaisse, a.id as affaire_id
            FROM encaissements e
            JOIN affaires a ON e.affaire_id = a.id
            WHERE e.date_encaissement BETWEEN ? AND ?
            AND e.statut = 'VALIDE'
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, java.sql.Date.valueOf(dateDebut));
            stmt.setDate(2, java.sql.Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                BigDecimal montantEncaisse = rs.getBigDecimal("montant_encaisse");
                Long affaireId = rs.getLong("affaire_id");

                // Calculs corrects
                BigDecimal partIndicateur = montantEncaisse.multiply(new BigDecimal("0.10"));
                BigDecimal produitNet = montantEncaisse.subtract(partIndicateur);
                BigDecimal partFLCF = produitNet.multiply(new BigDecimal("0.10"));
                BigDecimal partTresor = produitNet.multiply(new BigDecimal("0.15"));
                BigDecimal produitAyantsDroits = produitNet.subtract(partFLCF).subtract(partTresor);

                // Part des chefs (15% des ayants droits) à partager avec DD et DG
                BigDecimal partChefs = produitAyantsDroits.multiply(new BigDecimal("0.15"));

                // Compter les bénéficiaires de la part chefs
                long nbChefs = compterActeursParRole(affaireId, "Chef");
                boolean hasDG = hasRoleSpecial("DG");
                boolean hasDD = hasRoleSpecial("DD");
                long totalBeneficiaires = nbChefs + (hasDG ? 1 : 0) + (hasDD ? 1 : 0);

                if (totalBeneficiaires > 0 &&
                        ((role.equals("DD") && hasDD) || (role.equals("DG") && hasDG))) {
                    BigDecimal partRole = partChefs.divide(BigDecimal.valueOf(totalBeneficiaires), 2, RoundingMode.HALF_UP);
                    total = total.add(partRole);
                }
            }
        } catch (SQLException e) {
            logger.error("Erreur calcul part rôle spécial", e);
        }

        return total;
    }

    /**
     * Validation croisée entre les différents templates
     */
    public void validerCoherenceTemplates(LocalDate dateDebut, LocalDate dateFin) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🔍 VALIDATION CROISÉE DES TEMPLATES");
        System.out.println("   Période : " + dateDebut + " au " + dateFin);
        System.out.println("=".repeat(100));

        try {
            // Générer les données pour chaque template
            RapportService rapportService = new RapportService();

            // Template 1 - État de répartition
            RapportService.RapportRepartitionDTO template1 = rapportService.genererDonneesEtatRepartitionAffaires(dateDebut, dateFin);

            // Template 6 - Cumulé par agent
            RapportService.EtatCumuleAgentDTO template6 = rapportService.genererDonneesEtatCumuleParAgent(dateDebut, dateFin);

            // Comparer les totaux
            System.out.println("\n📊 COMPARAISON DES TOTAUX :");

            // Total Template 1
            BigDecimal totalT1_Chefs = template1.getAffaires().stream()
                    .map(a -> a.getPartChefs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalT1_Saisissants = template1.getAffaires().stream()
                    .map(a -> a.getPartSaisissants())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Total Template 6
            BigDecimal totalT6_Chefs = template6.getTotalChefs();
            BigDecimal totalT6_Saisissants = template6.getTotalSaisissants();

            // Comparaison
            System.out.println("\n   PARTS CHEFS :");
            System.out.println("   - Template 1 : " + formatMontant(totalT1_Chefs) + " FCFA");
            System.out.println("   - Template 6 : " + formatMontant(totalT6_Chefs) + " FCFA");
            BigDecimal ecartChefs = totalT1_Chefs.subtract(totalT6_Chefs).abs();
            if (ecartChefs.compareTo(BigDecimal.ONE) > 0) {
                System.out.println("   ❌ ÉCART : " + formatMontant(ecartChefs) + " FCFA");
            } else {
                System.out.println("   ✅ Cohérent");
            }

            System.out.println("\n   PARTS SAISISSANTS :");
            System.out.println("   - Template 1 : " + formatMontant(totalT1_Saisissants) + " FCFA");
            System.out.println("   - Template 6 : " + formatMontant(totalT6_Saisissants) + " FCFA");
            BigDecimal ecartSaisissants = totalT1_Saisissants.subtract(totalT6_Saisissants).abs();
            if (ecartSaisissants.compareTo(BigDecimal.ONE) > 0) {
                System.out.println("   ❌ ÉCART : " + formatMontant(ecartSaisissants) + " FCFA");
            } else {
                System.out.println("   ✅ Cohérent");
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la validation croisée", e);
        }
    }

    // === MÉTHODES UTILITAIRES ===

    private void afficherDetailsAffaire(Affaire affaire) {
        System.out.println("\n📄 DÉTAILS DE L'AFFAIRE :");
        System.out.println("   Numéro         : " + affaire.getNumeroAffaire());
        System.out.println("   Date création  : " + affaire.getDateCreation());
        System.out.println("   Montant amende : " + formatMontant(affaire.getMontantAmendeTotal()) + " FCFA");
        System.out.println("   Statut         : " + affaire.getStatut());
    }

    private void afficherDetailsAgent(Agent agent) {
        System.out.println("\n👤 DÉTAILS DE L'AGENT :");
        System.out.println("   Code    : " + agent.getCodeAgent());
        System.out.println("   Nom     : " + agent.getNom() + " " + (agent.getPrenom() != null ? agent.getPrenom() : ""));
        System.out.println("   Grade   : " + agent.getGrade());
        System.out.println("   Actif   : " + (agent.isActif() ? "OUI" : "NON"));
    }

    private void afficherActeursAffaire(List<ActeurInfo> acteurs) {
        System.out.println("\n👥 ACTEURS DE L'AFFAIRE :");
        for (ActeurInfo acteur : acteurs) {
            System.out.println("   - " + acteur.role + " : " + acteur.nom + " (Code: " + acteur.code + ")");
        }
    }

    private List<ActeurInfo> getActeursAffaire(Long affaireId) {
        List<ActeurInfo> acteurs = new ArrayList<>();

        String sql = """
            SELECT aa.role_sur_affaire, a.code_agent, a.nom, a.prenom
            FROM affaire_acteurs aa
            JOIN agents a ON aa.agent_id = a.id
            WHERE aa.affaire_id = ?
            ORDER BY aa.role_sur_affaire, a.nom
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ActeurInfo acteur = new ActeurInfo();
                acteur.role = rs.getString("role_sur_affaire");
                acteur.code = rs.getString("code_agent");
                acteur.nom = rs.getString("nom") + " " +
                        (rs.getString("prenom") != null ? rs.getString("prenom") : "");
                acteurs.add(acteur);
            }
        } catch (SQLException e) {
            logger.error("Erreur récupération acteurs", e);
        }

        return acteurs;
    }

    private List<AffaireRole> getAffairesAgent(Long agentId, LocalDate dateDebut, LocalDate dateFin) {
        List<AffaireRole> affaires = new ArrayList<>();

        String sql = """
            SELECT DISTINCT a.id, aa.role_sur_affaire
            FROM affaires a
            JOIN affaire_acteurs aa ON a.id = aa.affaire_id
            WHERE aa.agent_id = ? 
            AND a.date_creation BETWEEN ? AND ?
            ORDER BY a.date_creation DESC
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agentId);
            stmt.setDate(2, java.sql.Date.valueOf(dateDebut));
            stmt.setDate(3, java.sql.Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AffaireRole ar = new AffaireRole();
                ar.affaireId = rs.getLong("id");
                ar.role = rs.getString("role_sur_affaire");
                affaires.add(ar);
            }
        } catch (SQLException e) {
            logger.error("Erreur récupération affaires agent", e);
        }

        return affaires;
    }

    private long compterActeursParRole(Long affaireId, String role) {
        String sql = "SELECT COUNT(*) FROM affaire_acteurs WHERE affaire_id = ? AND role_sur_affaire = ?";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            stmt.setString(2, role);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Erreur comptage acteurs", e);
        }

        return 0;
    }

    private String getRoleSpecialAgent(Long agentId) {
        String sql = "SELECT role_nom FROM roles_speciaux WHERE agent_id = ? AND actif = 1 LIMIT 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agentId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("role_nom");
            }
        } catch (SQLException e) {
            logger.debug("Pas de rôle spécial pour l'agent {}", agentId);
        }

        return null;
    }

    private String formatMontant(BigDecimal montant) {
        if (montant == null) return "0";
        return String.format("%,.0f", montant);
    }

    /**
     * Vérifie si un rôle spécial (DD ou DG) existe
     */
    private boolean hasRoleSpecial(String role) {
        String sql = "SELECT COUNT(*) FROM roles_speciaux WHERE role_nom = ? AND actif = 1";

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, role);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.debug("Erreur vérification rôle spécial {}", role);
        }

        return false;
    }

    // Classes internes
    private static class ActeurInfo {
        String role;
        String code;
        String nom;
    }

    private static class AffaireRole {
        Long affaireId;
        String role;
    }

    // === MÉTHODE MAIN POUR TESTS ===

    public static void main(String[] args) {
        DebugRepartitionScript debug = new DebugRepartitionScript();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("🔍 SCRIPT DE DÉBOGAGE - RÉPARTITION DES PARTS");
            System.out.println("=".repeat(100));
            System.out.println("\nChoisissez une option :");
            System.out.println("1. Déboguer par numéro d'affaire");
            System.out.println("2. Déboguer par agent et période");
            System.out.println("3. Validation croisée des templates");
            System.out.println("0. Quitter");
            System.out.print("\nVotre choix : ");

            String choix = scanner.nextLine();

            switch (choix) {
                case "1":
                    // Débogage par affaire
                    System.out.print("\nEntrez le numéro d'affaire (ex: 2412000001) : ");
                    String numeroAffaire = scanner.nextLine();
                    debug.debugParAffaire(numeroAffaire);
                    break;

                case "2":
                    // Débogage par agent
                    System.out.print("\nEntrez le code agent (ex: AG001) : ");
                    String codeAgent = scanner.nextLine();

                    System.out.print("Date début (format: YYYY-MM-DD) : ");
                    String dateDebutStr = scanner.nextLine();
                    LocalDate dateDebut = LocalDate.parse(dateDebutStr);

                    System.out.print("Date fin (format: YYYY-MM-DD) : ");
                    String dateFinStr = scanner.nextLine();
                    LocalDate dateFin = LocalDate.parse(dateFinStr);

                    debug.debugParAgent(codeAgent, dateDebut, dateFin);
                    break;

                case "3":
                    // Validation croisée
                    System.out.print("\nDate début (format: YYYY-MM-DD) : ");
                    String dateDebut3Str = scanner.nextLine();
                    LocalDate dateDebut3 = LocalDate.parse(dateDebut3Str);

                    System.out.print("Date fin (format: YYYY-MM-DD) : ");
                    String dateFin3Str = scanner.nextLine();
                    LocalDate dateFin3 = LocalDate.parse(dateFin3Str);

                    debug.validerCoherenceTemplates(dateDebut3, dateFin3);
                    break;

                case "0":
                    System.out.println("\n👋 Au revoir !");
                    scanner.close();
                    return;

                default:
                    System.out.println("\n❌ Choix invalide !");
            }

            System.out.print("\n\nAppuyez sur Entrée pour continuer...");
            scanner.nextLine();
        }
    }
}