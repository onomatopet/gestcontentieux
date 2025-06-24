package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.ContraventionDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.regulation.contentieux.model.Centre;
import com.regulation.contentieux.model.enums.StatutEncaissement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de génération des rapports de rétrocession
 * Gère les calculs de répartition et la génération des documents
 */
public class RapportService {

    private static final Logger logger = LoggerFactory.getLogger(RapportService.class);

    // Pourcentages de répartition selon la réglementation
    private static final BigDecimal POURCENTAGE_ETAT = new BigDecimal("60.00");
    private static final BigDecimal POURCENTAGE_COLLECTIVITE = new BigDecimal("40.00");

    private AffaireDAO affaireDAO = new AffaireDAO();
    private EncaissementDAO encaissementDAO = new EncaissementDAO();
    private AgentDAO agentDAO = new AgentDAO();
    private ServiceDAO serviceDAO = new ServiceDAO();
    private CentreDAO centreDAO = new CentreDAO();
    private RepartitionService repartitionService = new RepartitionService();

    private ContraventionDAO contraventionDAO; // CORRECTION : Variable manquante

    // CORRECTION : Définir les constantes de rôles spéciaux correctement
    private static final String ROLE_DG = "DG";
    private static final String ROLE_DD = "DD";

    // CORRECTION LIGNE 446 : Constante pour les rôles spéciaux avec nom correct
    private static final String ROLE_SPECIAL = "ROLE_SPECIAL"; // CORRECTION : Variable manquante avec point-virgule

    public RapportService() {
        this.encaissementDAO = new EncaissementDAO();
        this.affaireDAO = new AffaireDAO();
        this.agentDAO = new AgentDAO();
        this.serviceDAO = new ServiceDAO();
        this.centreDAO = new CentreDAO();
        this.repartitionService = new RepartitionService();
        this.contraventionDAO = new ContraventionDAO();
    }

    public RapportService(ContraventionDAO contraventionDAO) {
        this();
        this.contraventionDAO = contraventionDAO;
    }

    // CORRECTION : Initialisation des DAOs manquante
    private void initializeDAOs() {
        if (this.contraventionDAO == null) {
            this.contraventionDAO = new ContraventionDAO();
        }
    }

    // ==================== MÉTHODES POUR SITUATION GÉNÉRALE ====================

    /**
     * ENRICHISSEMENT : Génère le rapport de situation générale
     * SIGNATURE CONSERVÉE - CONTENU ENRICHI
     */
    public SituationGeneraleDTO genererSituationGenerale(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération de la situation générale - {} au {}", dateDebut, dateFin);

        SituationGeneraleDTO situation = new SituationGeneraleDTO();
        situation.setDateDebut(dateDebut);
        situation.setDateFin(dateFin);
        situation.setDateGeneration(LocalDate.now());
        situation.setPeriodeLibelle(DateFormatter.format(dateDebut) + " au " + DateFormatter.format(dateFin));

        // Récupérer toutes les affaires de la période
        List<Affaire> affaires = affaireDAO.findByPeriod(dateDebut, dateFin);

        // Statistiques globales
        situation.setTotalAffaires(affaires.size());
        situation.setAffairesOuvertes((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.OUVERTE)
                .count());
        situation.setAffairesEnCours((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.EN_COURS)
                .count());
        situation.setAffairesSoldees((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.CLOSE)
                .count());
        situation.setAffairesAnnulees((int) affaires.stream()
                .filter(a -> a.getStatut() == StatutAffaire.ANNULEE)
                .count());

        // ENRICHISSEMENT : Calculs financiers avancés
        BigDecimal montantTotal = affaires.stream()
                .map(Affaire::getMontantAmendeTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        situation.setMontantTotalAmendes(montantTotal);

        BigDecimal montantEncaisse = affaires.stream()
                .flatMap(a -> a.getEncaissements().stream())
                .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                .map(Encaissement::getMontantEncaisse)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        situation.setMontantEncaisse(montantEncaisse);

        situation.setSoldeRestant(montantTotal.subtract(montantEncaisse));

        // ENRICHISSEMENT : Calcul du taux de recouvrement
        if (montantTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxRecouvrement = montantEncaisse
                    .divide(montantTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            situation.setTauxRecouvrement(tauxRecouvrement);
        } else {
            situation.setTauxRecouvrement(BigDecimal.ZERO);
        }

        logger.info("✅ Situation générale générée - {} affaires", affaires.size());
        return situation;
    }

    /**
     * CORRECTION BUG : Méthode manquante calculerPartDG()
     */
    private BigDecimal calculerPartDG(Agent agent, LocalDate dateDebut, LocalDate dateFin) {
        if (agent == null) return BigDecimal.ZERO;

        // Vérifier d'abord si l'agent a le rôle DG
        if (!hasRoleSpecial(agent, ROLE_DG)) {
            return BigDecimal.ZERO;
        }

        // Rechercher les encaissements où l'agent a bénéficié comme DG
        String sql = """
        SELECT SUM(r.part_dg) as total_dg
        FROM repartition_resultat r
        INNER JOIN encaissements e ON r.encaissement_id = e.id
        WHERE e.date_encaissement BETWEEN ? AND ?
        AND r.part_dg > 0
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal totalDG = rs.getBigDecimal("total_dg");
                return totalDG != null ? totalDG : BigDecimal.ZERO;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul part DG pour agent: {}", agent.getId(), e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * ENRICHISSEMENT : Méthode pour générer l'état cumulé par agent
     */
    public EtatCumuleAgentDTO genererDonneesEtatCumuleParAgent(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération de l'état cumulé par agent");

        EtatCumuleAgentDTO rapport = new EtatCumuleAgentDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

        // Récupérer tous les agents actifs
        List<Agent> agents = agentDAO.findAllActifs();

        for (Agent agent : agents) {
            AgentStatsDTO stats = calculerStatsAgent(agent, dateDebut, dateFin);
            if (stats.hasActivite()) {
                rapport.getAgents().add(stats);
            }
        }

        rapport.calculateTotaux();
        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante calculerPartDD()
     */
    private BigDecimal calculerPartDD(Agent agent, LocalDate dateDebut, LocalDate dateFin) {
        if (agent == null) return BigDecimal.ZERO;

        // Vérifier d'abord si l'agent a le rôle DD
        if (!hasRoleSpecial(agent, ROLE_DD)) {
            return BigDecimal.ZERO;
        }

        // Rechercher les encaissements où l'agent a bénéficié comme DD
        String sql = """
        SELECT SUM(r.part_dd) as total_dd
        FROM repartition_resultat r
        INNER JOIN encaissements e ON r.encaissement_id = e.id
        WHERE e.date_encaissement BETWEEN ? AND ?
        AND r.part_dd > 0
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal totalDD = rs.getBigDecimal("total_dd");
                return totalDD != null ? totalDD : BigDecimal.ZERO;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul part DD pour agent: {}", agent.getId(), e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * CORRECTION BUG : Méthode utilitaire pour vérifier les rôles spéciaux
     */
    private boolean hasRoleSpecial(Agent agent, String role) {
        if (agent == null || role == null) return false;

        String sql = """
        SELECT COUNT(*) 
        FROM roles_speciaux rs 
        WHERE rs.agent_id = ? AND rs.role_nom = ?
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agent.getId());
            stmt.setString(2, role);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la vérification du rôle spécial: {}", e.getMessage());
        }

        return false;
    }

    /**
     * CORRECTION BUG : Remplacer l'usage de encaissementDAO.mapResultSetToEntity
     * par une méthode locale ou utiliser EncaissementDAO.mapResultSetToEncaissement
     */
    private Encaissement mapEncaissementFromResultSet(ResultSet rs) throws SQLException {
        // Utiliser la méthode statique que nous avons créée dans EncaissementDAO
        return EncaissementDAO.mapResultSetToEncaissement(rs);
    }

    /**
     * CORRECTION BUG : Remplacer l'appel à contraventionDAO.findByAffaireId
     * Cette méthode n'existe peut-être pas, utilisons une alternative
     */
    private List<Contravention> getContraventionsByAffaire(Long affaireId) {
        if (contraventionDAO == null) {
            initializeDAOs();
        }

        // Si la méthode findByAffaireId n'existe pas, utiliser une requête SQL directe
        String sql = """
        SELECT c.* 
        FROM contraventions c
        INNER JOIN affaire_contraventions ac ON c.id = ac.contravention_id
        WHERE ac.affaire_id = ?
    """;

        List<Contravention> contraventions = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                // Mapper manuellement ou utiliser le DAO si disponible
                Contravention contravention = new Contravention();
                contravention.setId(rs.getLong("id"));
                contravention.setCodeContravention(rs.getString("code_contravention"));
                contravention.setLibelle(rs.getString("libelle"));
                contravention.setMontantAmende(rs.getBigDecimal("montant_amende"));
                contraventions.add(contravention);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des contraventions pour l'affaire: {}", affaireId, e);
        }

        return contraventions;
    }

    /**
     * CORRECTION BUG : Méthode corrigée pour utiliser les bonnes constantes
     */
    private void traiterRolesSpeciaux(AgentStatsDTO stats, Agent agent, LocalDate dateDebut, LocalDate dateFin) {
        // Utiliser les constantes correctes au lieu de RoleSpecial.DG
        if (hasRoleSpecial(agent, ROLE_DG)) {
            BigDecimal partDG = calculerPartDG(agent, dateDebut, dateFin);
            stats.setPartEnTantQueDG(partDG);
        }

        if (hasRoleSpecial(agent, ROLE_DD)) {
            BigDecimal partDD = calculerPartDD(agent, dateDebut, dateFin);
            stats.setPartEnTantQueDD(partDD);
        }

        // Recalculer la part totale
        stats.calculerPartTotale();
    }

    // ==================== RAPPORT PRINCIPAL DE RÉTROCESSION ====================

    /**
     * ENRICHISSEMENT : Génère le rapport principal de répartition/rétrocession
     * SIGNATURE CONSERVÉE - CONTENU ENRICHI
     */
    public RapportRepartitionDTO genererRapportRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📊 Génération du rapport de répartition - {} au {}", dateDebut, dateFin);

        RapportRepartitionDTO rapport = new RapportRepartitionDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());
        // Ajout de la période libellé pour corriger le bug
        rapport.setPeriodeLibelle(DateFormatter.format(dateDebut) + " au " + DateFormatter.format(dateFin));

        // Récupérer les encaissements validés de la période
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() != StatutEncaissement.VALIDE || enc.getAffaire() == null) {
                continue;
            }

            Affaire affaire = enc.getAffaire();
            RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

            // Créer le DTO pour cette affaire
            AffaireRepartitionDTO affaireDTO = new AffaireRepartitionDTO();
            affaireDTO.setNumeroAffaire(affaire.getNumeroAffaire());
            affaireDTO.setDateCreation(affaire.getDateCreation());
            affaireDTO.setContrevenantNom(affaire.getContrevenant() != null ?
                    affaire.getContrevenant().getNomComplet() : "");
            affaireDTO.setContrevenant(affaire.getContrevenant() != null ?
                    affaire.getContrevenant().getNomComplet() : "");
            affaireDTO.setContraventionType(getContraventionLibelle(affaire));
            affaireDTO.setMontantAmende(affaire.getMontantAmendeTotal());
            affaireDTO.setMontantEncaisse(enc.getMontantEncaisse());
            affaireDTO.setPartEtat(repartition.getPartTresor());
            affaireDTO.setPartCollectivite(repartition.getPartFLCF());
            affaireDTO.setChefDossier(getChefDossier(affaire));
            affaireDTO.setBureau(affaire.getBureau() != null ? affaire.getBureau().getNomBureau() : "");
            affaireDTO.setStatut(affaire.getStatut().name());

            rapport.getAffaires().add(affaireDTO);
        }

        // Calcul des totaux
        rapport.calculateTotaux();

        return rapport;
    }

    /**
     * ENRICHISSEMENT : Génère le rapport des encaissements par période
     * SIGNATURE CONSERVÉE - CONTENU ENRICHI
     */
    public RapportEncaissementsDTO genererRapportEncaissements(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("💰 Génération du rapport des encaissements - {} au {}", dateDebut, dateFin);

        RapportEncaissementsDTO rapport = new RapportEncaissementsDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());

        // Récupérer les encaissements
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        Map<Service, List<Encaissement>> encaissementsParService = new HashMap<>();

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() == StatutEncaissement.VALIDE && enc.getAffaire() != null) {
                Service service = enc.getAffaire().getService();
                if (service != null) {
                    encaissementsParService.computeIfAbsent(service, k -> new ArrayList<>()).add(enc);
                }
            }
        }

        // Créer les DTOs par service
        for (Map.Entry<Service, List<Encaissement>> entry : encaissementsParService.entrySet()) {
            ServiceEncaissementDTO serviceDTO = new ServiceEncaissementDTO();
            serviceDTO.setNomService(entry.getKey().getNomService());

            BigDecimal totalService = BigDecimal.ZERO;
            for (Encaissement enc : entry.getValue()) {
                if (enc.getAffaire() != null) {
                    RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

                    DetailEncaissementDTO detail = new DetailEncaissementDTO();
                    detail.setNumeroEncaissement(enc.getReference());
                    detail.setDateEncaissement(enc.getDateEncaissement());
                    detail.setNumeroAffaire(enc.getAffaire().getNumeroAffaire());
                    detail.setMontant(enc.getMontantEncaisse());
                    detail.setPartIndicateur(repartition.getPartIndicateur());

                    serviceDTO.getEncaissements().add(detail);
                    totalService = totalService.add(enc.getMontantEncaisse());
                }
            }

            serviceDTO.setTotalEncaisse(totalService);
            serviceDTO.setNombreEncaissements(entry.getValue().size());

            rapport.getServices().add(serviceDTO);
        }

        // Calcul du total général
        rapport.setTotalGeneral(
                rapport.getServices().stream()
                        .map(ServiceEncaissementDTO::getTotalEncaisse)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        return rapport;
    }

    /**
     * ENRICHISSEMENT : Génère le rapport des affaires non soldées
     * SIGNATURE CONSERVÉE (sans paramètres) - CONTENU ENRICHI
     */
    public List<Affaire> genererRapportAffairesNonSoldees() {
        logger.info("📋 Génération du rapport des affaires non soldées");

        // ENRICHISSEMENT : Récupération avec critères avancés
        List<Affaire> affairesNonSoldees = affaireDAO.findAffairesNonSoldees();

        // ENRICHISSEMENT : Tri par ancienneté (plus anciennes en premier)
        affairesNonSoldees.sort((a1, a2) -> {
            LocalDate date1 = a1.getDateCreation();
            LocalDate date2 = a2.getDateCreation();
            if (date1 == null && date2 == null) return 0;
            if (date1 == null) return 1;
            if (date2 == null) return -1;
            return date1.compareTo(date2);
        });

        // ENRICHISSEMENT : Enrichissement des données pour chaque affaire
        for (Affaire affaire : affairesNonSoldees) {
            // Calcul du solde restant
            BigDecimal totalEncaisse = affaire.getEncaissements().stream()
                    .filter(e -> e.getStatut() == StatutEncaissement.VALIDE)
                    .map(Encaissement::getMontantEncaisse)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal soldeRestant = affaire.getMontantAmendeTotal().subtract(totalEncaisse);
            affaire.setSoldeRestant(soldeRestant);

            // Calcul du nombre de jours depuis création
            if (affaire.getDateCreation() != null) {
                long joursDepuisCreation = java.time.temporal.ChronoUnit.DAYS.between(
                        affaire.getDateCreation(), LocalDate.now());
                affaire.setJoursDepuisCreation((int) joursDepuisCreation);
            }
        }

        logger.info("✅ Rapport des affaires non soldées généré - {} affaires trouvées", affairesNonSoldees.size());
        return affairesNonSoldees;
    }

    // ==================== NOUVELLES MÉTHODES POUR LES DONNÉES ====================

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesEtatRepartitionAffaires()
     */
    public RapportRepartitionDTO genererDonneesEtatRepartitionAffaires(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération des données d'état de répartition des affaires");

        RapportRepartitionDTO rapport = new RapportRepartitionDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());
        rapport.setPeriodeLibelle(String.format("Du %s au %s",
                DateFormatter.format(dateDebut), DateFormatter.format(dateFin)));

        // Récupérer tous les encaissements de la période
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() == StatutEncaissement.VALIDE && enc.getAffaire() != null) {
                AffaireRepartitionDTO affaireDTO = new AffaireRepartitionDTO();
                affaireDTO.setNumeroAffaire(enc.getAffaire().getNumeroAffaire());
                affaireDTO.setDateEncaissement(enc.getDateEncaissement());
                affaireDTO.setMontantEncaisse(enc.getMontantEncaisse());

                // Calculer la répartition
                RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());
                affaireDTO.setPartEtat(repartition.getPartTresor());
                affaireDTO.setPartCollectivite(repartition.getProduitNetAyantsDroits());

                rapport.getAffaires().add(affaireDTO);
            }
        }

        rapport.calculateTotaux();
        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesTableauAmendesParServices()
     */
    public TableauAmendesParServicesDTO genererDonneesTableauAmendesParServices(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération du tableau des amendes par services");

        TableauAmendesParServicesDTO rapport = new TableauAmendesParServicesDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());

        // Récupérer les données par service
        List<Service> services = serviceDAO.findAllActifs();

        for (Service service : services) {
            ServiceStatsDTO serviceStats = calculerStatsService(service, dateDebut, dateFin);
            if (serviceStats.hasActivite()) {
                // Convertir ServiceStatsDTO vers ServiceAmendeDTO
                ServiceAmendeDTO amendeDTO = new ServiceAmendeDTO();
                amendeDTO.setNomService(service.getNomService());
                amendeDTO.setNombreAffaires(serviceStats.getNombreAffaires());
                amendeDTO.setMontantTotal(serviceStats.getMontantTotal());
                amendeDTO.setObservations(serviceStats.getObservations());

                rapport.getServices().add(amendeDTO);
            }
        }

        // Calculer les totaux
        rapport.setTotalGeneral(
                rapport.getServices().stream()
                        .map(ServiceAmendeDTO::getMontantTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        rapport.setNombreTotalAffaires(
                rapport.getServices().stream()
                        .mapToInt(ServiceAmendeDTO::getNombreAffaires)
                        .sum()
        );

        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesEtatMandatement()
     */
    public EtatMandatementDTO genererDonneesEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération des données d'état de mandatement");

        EtatMandatementDTO rapport = new EtatMandatementDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());
        rapport.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));
        rapport.setTypeEtat("Mandatement Général");

        // Récupérer tous les encaissements validés de la période
        List<Encaissement> encaissements = encaissementDAO.findByPeriodAndStatut(dateDebut, dateFin, StatutEncaissement.VALIDE);

        for (Encaissement enc : encaissements) {
            if (enc.getAffaire() != null) {
                RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

                MandatementDTO mandatement = new MandatementDTO();
                mandatement.setReference(enc.getReference());
                mandatement.setDateEncaissement(enc.getDateEncaissement());
                mandatement.setProduitNet(repartition.getProduitNet());
                mandatement.setPartChefs(repartition.getPartChefs());
                mandatement.setPartSaisissants(repartition.getPartSaisissants());
                mandatement.setPartMutuelle(repartition.getPartMutuelle());
                mandatement.setPartMasseCommune(repartition.getPartMasseCommune());
                mandatement.setPartInteressement(repartition.getPartInteressement());
                mandatement.setPartDG(repartition.getPartDG());
                mandatement.setPartDD(repartition.getPartDD());

                rapport.getMandatements().add(mandatement);
            }
        }

        rapport.calculateTotaux();
        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesCentreRepartition()
     */
    public CentreRepartitionDTO genererDonneesCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération des données de répartition par centre");

        CentreRepartitionDTO rapport = new CentreRepartitionDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setDateGeneration(LocalDate.now());

        // Récupérer tous les centres actifs
        List<Centre> centres = centreDAO.findAllActifs();

        for (Centre centre : centres) {
            CentreStatsDTO centreStats = calculerStatsCentre(centre, dateDebut, dateFin);
            if (centreStats.hasActivite()) {
                rapport.getCentres().add(centreStats);
            }
        }

        rapport.calculateTotaux();
        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesIndicateursReels()
     */
    public IndicateursReelsDTO genererDonneesIndicateursReels(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération des données d'indicateurs réels");

        IndicateursReelsDTO rapport = new IndicateursReelsDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

        // Récupérer tous les encaissements avec indicateur de la période
        String sql = """
        SELECT e.*, a.numero_affaire, a.agent_indicateur_id
        FROM encaissements e
        INNER JOIN affaires a ON e.affaire_id = a.id
        WHERE e.date_encaissement BETWEEN ? AND ?
        AND a.agent_indicateur_id IS NOT NULL
        AND e.statut = 'VALIDE'
        ORDER BY e.date_encaissement ASC
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                IndicateurReelDTO indicateur = new IndicateurReelDTO();
                indicateur.setNumeroAffaire(rs.getString("numero_affaire"));
                indicateur.setDateEncaissement(rs.getDate("date_encaissement").toLocalDate());

                // Charger l'agent indicateur
                Long agentId = rs.getLong("agent_indicateur_id");
                Agent agent = agentDAO.findById(agentId).orElse(null);
                indicateur.setIndicateur(agent);

                // Calculer le montant de l'indicateur (10%)
                BigDecimal montantEncaisse = rs.getBigDecimal("montant_encaisse");
                BigDecimal montantIndicateur = montantEncaisse.multiply(new BigDecimal("0.10"));
                indicateur.setMontant(montantIndicateur);

                rapport.getIndicateurs().add(indicateur);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des indicateurs réels", e);
        }

        rapport.calculateTotaux();
        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesRepartitionProduit()
     */
    public RepartitionProduitDTO genererDonneesRepartitionProduit(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération des données de répartition de produit");

        RepartitionProduitDTO rapport = new RepartitionProduitDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

        // Récupérer tous les encaissements de la période
        List<Encaissement> encaissements = encaissementDAO.findByPeriod(dateDebut, dateFin);

        for (Encaissement enc : encaissements) {
            if (enc.getStatut() == StatutEncaissement.VALIDE && enc.getAffaire() != null) {
                RepartitionResultat repartition = repartitionService.calculerRepartition(enc, enc.getAffaire());

                LigneRepartitionDTO ligne = new LigneRepartitionDTO();
                ligne.setNumeroEncaissement(enc.getReference());
                ligne.setNumeroAffaire(enc.getAffaire().getNumeroAffaire());
                ligne.setDateEncaissement(enc.getDateEncaissement());
                ligne.setProduitDisponible(repartition.getProduitDisponible());
                ligne.setPartIndicateur(repartition.getPartIndicateur());
                ligne.setPartFLCF(repartition.getPartFLCF());
                ligne.setPartTresor(repartition.getPartTresor());
                ligne.setPartAyantsDroits(repartition.getProduitNetAyantsDroits());

                rapport.getLignes().add(ligne);
            }
        }

        rapport.calculateTotaux();
        return rapport;
    }

    /**
     * CORRECTION BUG : Méthode manquante genererDonneesMandatementAgents()
     */
    public EtatMandatementDTO genererDonneesMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération du mandatement par agents");

        EtatMandatementDTO rapport = new EtatMandatementDTO();
        rapport.setDateDebut(dateDebut);
        rapport.setDateFin(dateFin);
        rapport.setTypeEtat("Mandatement par Agents");
        rapport.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

        // Récupérer tous les agents avec activité sur la période
        List<Agent> agents = agentDAO.findAllActifs();

        for (Agent agent : agents) {
            AgentStatsDTO stats = calculerStatsAgent(agent, dateDebut, dateFin);
            if (stats.hasActivite()) {
                // Créer un mandatement fictif pour l'agent
                MandatementDTO mandatement = new MandatementDTO();
                mandatement.setAgent(agent);
                mandatement.setMontantTotal(stats.getPartTotaleAgent());
                mandatement.setObservations("Cumul des parts de l'agent sur la période");

                rapport.getMandatements().add(mandatement);
            }
        }

        rapport.calculateTotaux();
        return rapport;
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * ENRICHISSEMENT : Formate une période de dates
     */
    private String formatPeriode(LocalDate debut, LocalDate fin) {
        return String.format("Du %s au %s",
                DateFormatter.format(debut),
                DateFormatter.format(fin));
    }

    /**
     * ENRICHISSEMENT : Calcul des statistiques par centre
     */
    private CentreStatsDTO calculerStatsCentre(Centre centre, LocalDate dateDebut, LocalDate dateFin) {
        CentreStatsDTO stats = new CentreStatsDTO();
        stats.setCentre(centre);

        String sql = """
        SELECT 
            COALESCE(SUM(e.montant_encaisse), 0) as montant_total,
            COUNT(DISTINCT a.id) as nombre_affaires
        FROM affaires a
        JOIN encaissements e ON a.id = e.affaire_id
        WHERE a.centre_id = ? 
        AND e.date_encaissement BETWEEN ? AND ?
        AND e.statut = 'VALIDE'
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, centre.getId());
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                BigDecimal montantTotal = rs.getBigDecimal("montant_total");

                // Calcul des répartitions selon les règles métier
                // 60% pour la répartition de base, part indicateur variable
                stats.setRepartitionBase(montantTotal.multiply(new BigDecimal("0.60")));
                stats.setRepartitionIndicateur(montantTotal.multiply(new BigDecimal("0.10"))); // 10% indicateurs
                stats.setPartTotalCentre(stats.getRepartitionBase().add(stats.getRepartitionIndicateur()));
                stats.setNombreAffaires(rs.getInt("nombre_affaires"));
            }

        } catch (SQLException e) {
            logger.error("Erreur calcul stats centre: {}", centre.getId(), e);
        }

        return stats;
    }

    /**
     * ENRICHISSEMENT : Calcule les statistiques d'un service
     */
    private ServiceStatsDTO calculerStatsService(Service service, LocalDate dateDebut, LocalDate dateFin) {
        ServiceStatsDTO stats = new ServiceStatsDTO();
        stats.setService(service);

        String sql = """
        SELECT 
            COUNT(DISTINCT a.id) as nombre_affaires,
            COALESCE(SUM(a.montant_amende_total), 0) as montant_total,
            COUNT(DISTINCT e.id) as nombre_encaissements,
            COALESCE(SUM(e.montant_encaisse), 0) as montant_encaisse
        FROM affaires a
        LEFT JOIN encaissements e ON a.id = e.affaire_id 
            AND e.date_encaissement BETWEEN ? AND ?
        WHERE a.service_id = ?
        AND a.date_creation BETWEEN ? AND ?
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(dateDebut));
            stmt.setDate(2, Date.valueOf(dateFin));
            stmt.setLong(3, service.getId());
            stmt.setDate(4, Date.valueOf(dateDebut));
            stmt.setDate(5, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                stats.setNombreAffaires(rs.getInt("nombre_affaires"));
                stats.setMontantTotal(rs.getBigDecimal("montant_total"));
                stats.setNombreEncaissements(rs.getInt("nombre_encaissements"));
                stats.setMontantEncaisse(rs.getBigDecimal("montant_encaisse"));

                // Calculer les observations
                BigDecimal montantTotal = stats.getMontantTotal();
                BigDecimal montantEncaisse = stats.getMontantEncaisse();

                if (montantTotal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal tauxRecouvrement = montantEncaisse
                            .divide(montantTotal, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));

                    if (tauxRecouvrement.compareTo(new BigDecimal("90")) >= 0) {
                        stats.setObservations("Excellent recouvrement");
                    } else if (tauxRecouvrement.compareTo(new BigDecimal("70")) >= 0) {
                        stats.setObservations("Bon recouvrement");
                    } else if (tauxRecouvrement.compareTo(new BigDecimal("50")) >= 0) {
                        stats.setObservations("Recouvrement moyen");
                    } else {
                        stats.setObservations("Recouvrement faible");
                    }
                } else {
                    stats.setObservations("Aucune activité");
                }
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du calcul des stats service: {}", service.getId(), e);
        }

        return stats;
    }

    /**
     * ENRICHISSEMENT : Calcule les statistiques d'un agent
     */
    private AgentStatsDTO calculerStatsAgent(Agent agent, LocalDate dateDebut, LocalDate dateFin) {
        AgentStatsDTO stats = new AgentStatsDTO();
        stats.setAgent(agent);
        stats.setNombreAffaires(0);
        stats.setMontantTotal(BigDecimal.ZERO);
        stats.setObservations("");

        // Récupérer les affaires où l'agent est impliqué via la table de liaison
        List<Affaire> affaires = getAffairesByAgentAndPeriod(agent.getId(), dateDebut, dateFin);

        for (Affaire affaire : affaires) {
            stats.setNombreAffaires(stats.getNombreAffaires() + 1);

            // Calculer le montant en fonction du rôle
            List<Encaissement> encaissements = encaissementDAO.findByAffaireId(affaire.getId());
            for (Encaissement enc : encaissements) {
                if (enc.getStatut() == StatutEncaissement.VALIDE &&
                        !enc.getDateEncaissement().isBefore(dateDebut) &&
                        !enc.getDateEncaissement().isAfter(dateFin)) {

                    RepartitionResultat repartition = repartitionService.calculerRepartition(enc, affaire);

                    // Ajouter la part de l'agent selon son rôle
                    BigDecimal partAgent = getPartAgentFromRepartition(repartition, agent.getId());
                    if (partAgent != null) {
                        stats.setMontantTotal(stats.getMontantTotal().add(partAgent));
                    }
                }
            }
        }

        // Traiter les rôles spéciaux
        traiterRolesSpeciaux(stats, agent, dateDebut, dateFin);

        return stats;
    }

    /**
     * ENRICHISSEMENT : Récupère les encaissements avec indicateur par service
     */
    private List<Encaissement> getEncaissementsAvecIndicateurByService(Service service, LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
        SELECT e.* FROM encaissements e
        JOIN affaires a ON e.affaire_id = a.id
        WHERE a.service_id = ?
        AND e.date_encaissement BETWEEN ? AND ?
        AND e.statut = 'VALIDE'
        AND a.indicateur_existe = 1
        ORDER BY e.date_encaissement, e.reference
    """;

        List<Encaissement> encaissements = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, service.getId());
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Encaissement enc = encaissementDAO.mapResultSetToEntity(rs);
                encaissements.add(enc);
            }

        } catch (SQLException e) {
            logger.error("Erreur récupération encaissements indicateur service: {}", service.getId(), e);
        }

        return encaissements;
    }

    /**
     * ENRICHISSEMENT : Obtient les libellés des contraventions d'une affaire
     */
    private String getLibellesContraventions(Affaire affaire) {
        try {
            List<Contravention> contraventions = getContraventionsByAffaire(affaire.getId());
            return contraventions.stream()
                    .map(Contravention::getLibelle)
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            logger.debug("Impossible de récupérer les contraventions pour l'affaire {}", affaire.getId());
            return "Non défini";
        }
    }

    /**
     * Récupère le libellé de la contravention d'une affaire
     */
    private String getContraventionLibelle(Affaire affaire) {
        return getLibellesContraventions(affaire);
    }

    /**
     * Récupère le chef de dossier d'une affaire
     */
    private String getChefDossier(Affaire affaire) {
        try {
            // Récupérer les acteurs de type CHEF via la table de liaison
            List<Agent> chefs = getAgentsByAffaireAndRole(affaire.getId(), "CHEF");

            if (!chefs.isEmpty()) {
                return chefs.get(0).getNomComplet();
            }

            return "Non défini";
        } catch (Exception e) {
            logger.warn("Impossible de déterminer le chef de dossier pour l'affaire {}", affaire.getNumeroAffaire());
            return "Non défini";
        }
    }

    /**
     * Récupère les agents d'une affaire par rôle
     */
    private List<Agent> getAgentsByAffaireAndRole(Long affaireId, String role) {
        List<Agent> agents = new ArrayList<>();

        String sql = """
            SELECT a.* FROM agents a
            JOIN affaire_acteurs aa ON a.id = aa.agent_id
            WHERE aa.affaire_id = ? AND aa.role_sur_affaire = ?
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            stmt.setString(2, role);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Agent agent = new Agent();
                agent.setId(rs.getLong("id"));
                agent.setCodeAgent(rs.getString("code_agent"));
                agent.setNom(rs.getString("nom"));
                agent.setPrenom(rs.getString("prenom"));
                agent.setGrade(rs.getString("grade"));
                agent.setActif(rs.getBoolean("actif"));
                agents.add(agent);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des agents pour l'affaire {} avec rôle {}", affaireId, role, e);
        }

        return agents;
    }

    /**
     * Récupère les affaires d'un agent pour une période
     */
    private List<Affaire> getAffairesByAgentAndPeriod(Long agentId, LocalDate dateDebut, LocalDate dateFin) {
        List<Affaire> affaires = new ArrayList<>();

        String sql = """
            SELECT DISTINCT a.* FROM affaires a
            JOIN affaire_acteurs aa ON a.id = aa.affaire_id
            WHERE aa.agent_id = ? 
            AND a.date_creation BETWEEN ? AND ?
            AND a.deleted = false
        """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, agentId);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                // Utiliser la méthode findById du DAO pour charger l'affaire complète
                Long affaireId = rs.getLong("id");
                affaireDAO.findById(affaireId).ifPresent(affaires::add);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors de la récupération des affaires pour l'agent {} sur la période", agentId, e);
        }

        return affaires;
    }

    /**
     * Récupère la part d'un agent depuis une répartition
     */
    private BigDecimal getPartAgentFromRepartition(RepartitionResultat repartition, Long agentId) {
        // Parcourir les parts individuelles
        for (RepartitionResultat.PartIndividuelle part : repartition.getPartsIndividuelles()) {
            if (part.getAgent() != null && part.getAgent().getId().equals(agentId)) {
                return part.getMontant();
            }
        }

        // Vérifier aussi les bénéficiaires génériques (DD/DG)
        // TODO: Implémenter si nécessaire

        return BigDecimal.ZERO;
    }

    // ==================== CLASSES DTO CORRECTEMENT DÉFINIES ====================

    /**
     * DTO pour le tableau des amendes par services
     * CLASSE CORRIGÉE avec toutes les méthodes
     */
    public static class TableauAmendesParServicesDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<ServiceAmendeDTO> services = new ArrayList<>();
        private BigDecimal totalGeneral = BigDecimal.ZERO;
        private int nombreTotalAffaires = 0;

        // Getters et setters
        public void setMontantEncaisse(BigDecimal montantEncaisse) {
            this.montantTotalEncaisse = montantEncaisse;
        }
        public void setSoldeRestant(BigDecimal soldeRestant) {
            this.montantRestantDu = soldeRestant;
        }
        public BigDecimal getTotalEncaissements() {
            return montantTotalEncaisse;
        }
        public int getNombreAffaires() {
            return totalAffaires;
        }
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public List<ServiceAmendeDTO> getServices() { return services; }
        public void setServices(List<ServiceAmendeDTO> services) { this.services = services; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }

        public int getNombreTotalAffaires() { return nombreTotalAffaires; }
        public void setNombreTotalAffaires(int nombreTotalAffaires) { this.nombreTotalAffaires = nombreTotalAffaires; }

        /**
         * Alias pour getNombreTotalAffaires()
         */
        public int getTotalAffaires() {
            return nombreTotalAffaires;
        }

        /**
         * Alias pour getTotalGeneral()
         */
        public BigDecimal getTotalMontant() {
            return totalGeneral;
        }
    }

    /**
     * DTO pour les amendes par service
     * CLASSE CORRIGÉE avec validation
     */
    public static class ServiceAmendeDTO {
        private String nomService;
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private String observations;

        public ServiceAmendeDTO() {
            this.montantTotal = BigDecimal.ZERO;
            this.observations = "";
        }

        public boolean hasActivite() {
            return nombreAffaires > 0 || montantTotal.compareTo(BigDecimal.ZERO) > 0;
        }

        // Getters et setters
        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    /**
     * DTO pour les statistiques d'un service
     * CLASSE CORRIGÉE avec méthode hasActivite()
     */
    public static class ServiceStatsDTO {
        private Service service;
        private int nombreAffaires;
        private BigDecimal montantTotal = BigDecimal.ZERO;
        private int nombreEncaissements;
        private BigDecimal montantEncaisse = BigDecimal.ZERO;
        private String observations;

        public boolean hasActivite() {
            return nombreAffaires > 0 || montantTotal.compareTo(BigDecimal.ZERO) > 0;
        }

        // Getters et Setters complets
        public Service getService() { return service; }
        public void setService(Service service) { this.service = service; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    /**
     * CORRECTION : Classes DTO manquantes ajoutées
     */
    public static class CentreStatsDTO {
        private Centre centre;
        private int nombreAffaires;
        private BigDecimal montantTotal = BigDecimal.ZERO;
        private BigDecimal repartitionBase = BigDecimal.ZERO;
        private BigDecimal repartitionIndicateur = BigDecimal.ZERO;
        private BigDecimal partTotalCentre = BigDecimal.ZERO;

        public boolean hasActivite() {
            return nombreAffaires > 0 || montantTotal.compareTo(BigDecimal.ZERO) > 0;
        }

        // Getters et setters
        public Centre getCentre() { return centre; }
        public void setCentre(Centre centre) { this.centre = centre; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public BigDecimal getRepartitionBase() { return repartitionBase; }
        public void setRepartitionBase(BigDecimal repartitionBase) { this.repartitionBase = repartitionBase; }

        public BigDecimal getRepartitionIndicateur() { return repartitionIndicateur; }
        public void setRepartitionIndicateur(BigDecimal repartitionIndicateur) { this.repartitionIndicateur = repartitionIndicateur; }

        public BigDecimal getPartTotalCentre() { return partTotalCentre; }
        public void setPartTotalCentre(BigDecimal partTotalCentre) { this.partTotalCentre = partTotalCentre; }
    }

    public static class AgentStatsDTO {
        private Agent agent;
        private int nombreAffaires;
        private BigDecimal montantTotal;
        private String observations;

        // ENRICHISSEMENT : Parts par rôle pour les rapports détaillés
        private BigDecimal partEnTantQueChef = BigDecimal.ZERO;
        private BigDecimal partEnTantQueSaisissant = BigDecimal.ZERO;
        private BigDecimal partEnTantQueDG = BigDecimal.ZERO;
        private BigDecimal partEnTantQueDD = BigDecimal.ZERO;
        private BigDecimal partTotaleAgent = BigDecimal.ZERO;

        // Constructeur par défaut
        public AgentStatsDTO() {
            this.nombreAffaires = 0;
            this.montantTotal = BigDecimal.ZERO;
            this.observations = "";
        }

        // Constructeur avec agent
        public AgentStatsDTO(Agent agent) {
            this();
            this.agent = agent;
        }

        /**
         * Vérifie si l'agent a une activité
         */
        public boolean hasActivite() {
            return partTotaleAgent.compareTo(BigDecimal.ZERO) > 0 || nombreAffaires > 0;
        }

        /**
         * Calcule la part totale de l'agent
         */
        public void calculerPartTotale() {
            partTotaleAgent = partEnTantQueChef
                    .add(partEnTantQueSaisissant)
                    .add(partEnTantQueDG)
                    .add(partEnTantQueDD);
        }

        // ========== GETTERS ET SETTERS COMPLETS ==========

        public Agent getAgent() { return agent; }
        public void setAgent(Agent agent) { this.agent = agent; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }

        public BigDecimal getPartEnTantQueChef() { return partEnTantQueChef; }
        public void setPartEnTantQueChef(BigDecimal partEnTantQueChef) { this.partEnTantQueChef = partEnTantQueChef; }

        public BigDecimal getPartEnTantQueSaisissant() { return partEnTantQueSaisissant; }
        public void setPartEnTantQueSaisissant(BigDecimal partEnTantQueSaisissant) { this.partEnTantQueSaisissant = partEnTantQueSaisissant; }

        public BigDecimal getPartEnTantQueDG() { return partEnTantQueDG; }
        public void setPartEnTantQueDG(BigDecimal partEnTantQueDG) { this.partEnTantQueDG = partEnTantQueDG; }

        public BigDecimal getPartEnTantQueDD() { return partEnTantQueDD; }
        public void setPartEnTantQueDD(BigDecimal partEnTantQueDD) { this.partEnTantQueDD = partEnTantQueDD; }

        public BigDecimal getPartTotaleAgent() { return partTotaleAgent; }
        public void setPartTotaleAgent(BigDecimal partTotaleAgent) { this.partTotaleAgent = partTotaleAgent; }
    }

    // ==================== AUTRES CLASSES DTO EXISTANTES ====================

    /**
     * DTO pour l'état de répartition du produit
     */
    public static class RepartitionProduitDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<LigneRepartitionDTO> lignes = new ArrayList<>();
        private BigDecimal totalProduitDisponible = BigDecimal.ZERO;
        private BigDecimal totalIndicateur = BigDecimal.ZERO;
        private BigDecimal totalFLCF = BigDecimal.ZERO;
        private BigDecimal totalTresor = BigDecimal.ZERO;
        private BigDecimal totalAyantsDroits = BigDecimal.ZERO;

        // Constructeur
        public RepartitionProduitDTO() {
            this.dateGeneration = LocalDate.now();
        }

        public void calculateTotaux() {
            totalProduitDisponible = lignes.stream()
                    .map(LigneRepartitionDTO::getProduitDisponible)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalIndicateur = lignes.stream()
                    .map(LigneRepartitionDTO::getPartIndicateur)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalFLCF = lignes.stream()
                    .map(LigneRepartitionDTO::getPartFLCF)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalTresor = lignes.stream()
                    .map(LigneRepartitionDTO::getPartTresor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalAyantsDroits = lignes.stream()
                    .map(LigneRepartitionDTO::getPartAyantsDroits)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public List<LigneRepartitionDTO> getLignes() { return lignes; }
        public void setLignes(List<LigneRepartitionDTO> lignes) { this.lignes = lignes; }

        public BigDecimal getTotalProduitDisponible() { return totalProduitDisponible; }
        public void setTotalProduitDisponible(BigDecimal totalProduitDisponible) { this.totalProduitDisponible = totalProduitDisponible; }

        public BigDecimal getTotalIndicateur() { return totalIndicateur; }
        public void setTotalIndicateur(BigDecimal totalIndicateur) { this.totalIndicateur = totalIndicateur; }

        public BigDecimal getTotalFLCF() { return totalFLCF; }
        public void setTotalFLCF(BigDecimal totalFLCF) { this.totalFLCF = totalFLCF; }

        public BigDecimal getTotalTresor() { return totalTresor; }
        public void setTotalTresor(BigDecimal totalTresor) { this.totalTresor = totalTresor; }

        public BigDecimal getTotalAyantsDroits() { return totalAyantsDroits; }
        public void setTotalAyantsDroits(BigDecimal totalAyantsDroits) { this.totalAyantsDroits = totalAyantsDroits; }
    }

    public static class LigneRepartitionDTO {
        private String numeroEncaissement;
        private String numeroAffaire;
        private LocalDate dateEncaissement;
        private BigDecimal montantEncaisse;
        private BigDecimal produitDisponible;
        private BigDecimal partIndicateur;
        private BigDecimal partFLCF;
        private BigDecimal partTresor;
        private BigDecimal partAyantsDroits;
        private String nomContrevenant;
        private String contraventions;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getProduitDisponible() { return produitDisponible; }
        public void setProduitDisponible(BigDecimal produitDisponible) { this.produitDisponible = produitDisponible; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }

        public BigDecimal getPartFLCF() { return partFLCF; }
        public void setPartFLCF(BigDecimal partFLCF) { this.partFLCF = partFLCF; }

        public BigDecimal getPartTresor() { return partTresor; }
        public void setPartTresor(BigDecimal partTresor) { this.partTresor = partTresor; }

        public BigDecimal getPartAyantsDroits() { return partAyantsDroits; }
        public void setPartAyantsDroits(BigDecimal partAyantsDroits) { this.partAyantsDroits = partAyantsDroits; }

        public String getNomContrevenant() { return nomContrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.nomContrevenant = nomContrevenant; }

        public String getContraventions() { return contraventions; }
        public void setContraventions(String contraventions) { this.contraventions = contraventions; }
    }

    public static class EtatCumuleAgentDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<AgentStatsDTO> agents = new ArrayList<>();
        private BigDecimal totalGeneral = BigDecimal.ZERO;
        private int nombreAgents = 0;

        // Constructeur
        public EtatCumuleAgentDTO() {
            this.dateGeneration = LocalDate.now();
        }

        public void calculateTotaux() {
            totalGeneral = agents.stream()
                    .map(AgentStatsDTO::getPartTotaleAgent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            nombreAgents = agents.size();
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<AgentStatsDTO> getAgents() { return agents; }
        public void setAgents(List<AgentStatsDTO> agents) { this.agents = agents; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }

        public int getNombreAgents() { return nombreAgents; }
        public void setNombreAgents(int nombreAgents) { this.nombreAgents = nombreAgents; }
    }

    /**
     * DTO pour un indicateur réel individuel
     */
    public static class IndicateurReelDTO {
        private String numeroEncaissement;
        private String numeroAffaire;
        private LocalDate dateEncaissement;
        private BigDecimal montantEncaisse;
        private BigDecimal partIndicateur;
        private String nomContrevenant;
        private String contraventions;
        private Agent indicateur;
        private BigDecimal montant;
        private String observations;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }

        public String getNomContrevenant() { return nomContrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.nomContrevenant = nomContrevenant; }

        public String getContraventions() { return contraventions; }
        public void setContraventions(String contraventions) { this.contraventions = contraventions; }

        public Agent getIndicateur() { return indicateur; }
        public void setIndicateur(Agent indicateur) { this.indicateur = indicateur; }

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    public static class IndicateursReelsDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<IndicateurReelDTO> indicateurs = new ArrayList<>();
        private BigDecimal totalEncaissement = BigDecimal.ZERO;
        private BigDecimal totalPartIndicateur = BigDecimal.ZERO;
        private int nombreIndicateurs = 0;

        // Constructeur
        public IndicateursReelsDTO() {
            this.dateGeneration = LocalDate.now();
        }

        public void calculateTotaux() {
            totalEncaissement = indicateurs.stream()
                    .map(IndicateurReelDTO::getMontantEncaisse)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalPartIndicateur = indicateurs.stream()
                    .map(IndicateurReelDTO::getPartIndicateur)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            nombreIndicateurs = indicateurs.size();
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<IndicateurReelDTO> getIndicateurs() { return indicateurs; }
        public void setIndicateurs(List<IndicateurReelDTO> indicateurs) { this.indicateurs = indicateurs; }

        public BigDecimal getTotalEncaissement() { return totalEncaissement; }
        public void setTotalEncaissement(BigDecimal totalEncaissement) { this.totalEncaissement = totalEncaissement; }

        public BigDecimal getTotalPartIndicateur() { return totalPartIndicateur; }
        public void setTotalPartIndicateur(BigDecimal totalPartIndicateur) { this.totalPartIndicateur = totalPartIndicateur; }

        public int getNombreIndicateurs() { return nombreIndicateurs; }
        public void setNombreIndicateurs(int nombreIndicateurs) { this.nombreIndicateurs = nombreIndicateurs; }
    }

    /**
     * DTO pour l'état de mandatement (Templates 2 et 8)
     */
    public static class EtatMandatementDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private String typeEtat;
        private List<MandatementDTO> mandatements;
        private List<LigneMandatementDTO> lignes;
        private BigDecimal totalMontantEncaisse = BigDecimal.ZERO;
        private BigDecimal totalMontantMandatement = BigDecimal.ZERO;
        private BigDecimal totalProduitNet = BigDecimal.ZERO;
        private BigDecimal totalChefs = BigDecimal.ZERO;
        private BigDecimal totalSaisissants = BigDecimal.ZERO;
        private BigDecimal totalMutuelleNationale = BigDecimal.ZERO;
        private BigDecimal totalMasseCommune = BigDecimal.ZERO;
        private BigDecimal totalInteressement = BigDecimal.ZERO;
        private BigDecimal totalDG = BigDecimal.ZERO;
        private BigDecimal totalDD = BigDecimal.ZERO;
        private int nombreLignes = 0;

        public EtatMandatementDTO() {
            this.mandatements = new ArrayList<>();
            this.lignes = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
        }

        public void calculateTotaux() {
            if (mandatements != null) {
                totalProduitNet = mandatements.stream()
                        .map(MandatementDTO::getProduitNet)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalChefs = mandatements.stream()
                        .map(MandatementDTO::getPartChefs)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalSaisissants = mandatements.stream()
                        .map(MandatementDTO::getPartSaisissants)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalMutuelleNationale = mandatements.stream()
                        .map(MandatementDTO::getPartMutuelle)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalMasseCommune = mandatements.stream()
                        .map(MandatementDTO::getPartMasseCommune)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalInteressement = mandatements.stream()
                        .map(MandatementDTO::getPartInteressement)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalDG = mandatements.stream()
                        .map(MandatementDTO::getPartDG)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalDD = mandatements.stream()
                        .map(MandatementDTO::getPartDD)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            if (lignes != null) {
                totalMontantEncaisse = lignes.stream()
                        .map(LigneMandatementDTO::getMontantEncaisse)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalMontantMandatement = lignes.stream()
                        .map(LigneMandatementDTO::getMontantMandatement)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                nombreLignes = lignes.size();
            }
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public String getTypeEtat() { return typeEtat; }
        public void setTypeEtat(String typeEtat) { this.typeEtat = typeEtat; }

        public List<MandatementDTO> getMandatements() { return mandatements; }
        public void setMandatements(List<MandatementDTO> mandatements) { this.mandatements = mandatements; }

        public List<LigneMandatementDTO> getLignes() { return lignes; }
        public void setLignes(List<LigneMandatementDTO> lignes) { this.lignes = lignes; }

        public BigDecimal getTotalMontantEncaisse() { return totalMontantEncaisse; }
        public void setTotalMontantEncaisse(BigDecimal totalMontantEncaisse) { this.totalMontantEncaisse = totalMontantEncaisse; }

        public BigDecimal getTotalMontantMandatement() { return totalMontantMandatement; }
        public void setTotalMontantMandatement(BigDecimal totalMontantMandatement) { this.totalMontantMandatement = totalMontantMandatement; }

        public BigDecimal getTotalProduitNet() { return totalProduitNet; }
        public void setTotalProduitNet(BigDecimal totalProduitNet) { this.totalProduitNet = totalProduitNet; }

        public BigDecimal getTotalChefs() { return totalChefs; }
        public void setTotalChefs(BigDecimal totalChefs) { this.totalChefs = totalChefs; }

        public BigDecimal getTotalSaisissants() { return totalSaisissants; }
        public void setTotalSaisissants(BigDecimal totalSaisissants) { this.totalSaisissants = totalSaisissants; }

        public BigDecimal getTotalMutuelleNationale() { return totalMutuelleNationale; }
        public void setTotalMutuelleNationale(BigDecimal totalMutuelleNationale) { this.totalMutuelleNationale = totalMutuelleNationale; }

        public BigDecimal getTotalMasseCommune() { return totalMasseCommune; }
        public void setTotalMasseCommune(BigDecimal totalMasseCommune) { this.totalMasseCommune = totalMasseCommune; }

        public BigDecimal getTotalInteressement() { return totalInteressement; }
        public void setTotalInteressement(BigDecimal totalInteressement) { this.totalInteressement = totalInteressement; }

        public BigDecimal getTotalDG() { return totalDG; }
        public void setTotalDG(BigDecimal totalDG) { this.totalDG = totalDG; }

        public BigDecimal getTotalDD() { return totalDD; }
        public void setTotalDD(BigDecimal totalDD) { this.totalDD = totalDD; }

        public int getNombreLignes() { return nombreLignes; }
        public void setNombreLignes(int nombreLignes) { this.nombreLignes = nombreLignes; }
    }

    /**
     * DTO pour une ligne de mandatement
     */
    public static class LigneMandatementDTO {
        private String numeroEncaissement;
        private String numeroAffaire;
        private LocalDate dateEncaissement;
        private BigDecimal montantEncaisse;
        private BigDecimal montantMandatement;
        private String nomContrevenant;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getMontantMandatement() { return montantMandatement; }
        public void setMontantMandatement(BigDecimal montantMandatement) { this.montantMandatement = montantMandatement; }

        public String getNomContrevenant() { return nomContrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.nomContrevenant = nomContrevenant; }
    }

    /**
     * DTO pour une ligne de mandatement par agent
     */
    public static class LigneMandatementAgentDTO {
        private String numeroEncaissement;
        private String numeroAffaire;
        private LocalDate dateEncaissement;
        private BigDecimal montantEncaisse;
        private BigDecimal montantMandatement;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getMontantMandatement() { return montantMandatement; }
        public void setMontantMandatement(BigDecimal montantMandatement) { this.montantMandatement = montantMandatement; }
    }

    /**
     * DTO pour le mandatement d'un agent
     */
    public static class AgentMandatementDTO {
        private Agent agent;
        private List<LigneMandatementAgentDTO> lignes = new ArrayList<>();
        private BigDecimal montantTotalEncaisse = BigDecimal.ZERO;
        private BigDecimal montantTotalMandatement = BigDecimal.ZERO;
        private int nombreEncaissements = 0;

        public boolean hasEncaissements() {
            return nombreEncaissements > 0 || montantTotalMandatement.compareTo(BigDecimal.ZERO) > 0;
        }

        // Getters et setters
        public Agent getAgent() { return agent; }
        public void setAgent(Agent agent) { this.agent = agent; }

        public List<LigneMandatementAgentDTO> getLignes() { return lignes; }
        public void setLignes(List<LigneMandatementAgentDTO> lignes) { this.lignes = lignes; }

        public BigDecimal getMontantTotalEncaisse() { return montantTotalEncaisse; }
        public void setMontantTotalEncaisse(BigDecimal montantTotalEncaisse) { this.montantTotalEncaisse = montantTotalEncaisse; }

        public BigDecimal getMontantTotalMandatement() { return montantTotalMandatement; }
        public void setMontantTotalMandatement(BigDecimal montantTotalMandatement) { this.montantTotalMandatement = montantTotalMandatement; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatRepartitionAffaires()
     * Génère le HTML pour l'état de répartition des affaires
     */
    public String genererEtatRepartitionAffaires(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État de répartition des affaires");

        // Utiliser la méthode de données existante
        RapportRepartitionDTO donnees = genererDonneesEtatRepartitionAffaires(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT DE RÉPARTITION DES AFFAIRES CONTENTIEUSES</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>N° Affaire</th><th>Date</th><th>Montant Encaissé</th><th>Part État</th><th>Part Collectivité</th></tr>");

        for (AffaireRepartitionDTO affaire : donnees.getAffaires()) {
            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(DateFormatter.format(affaire.getDateEncaissement())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(affaire.getMontantEncaisse())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(affaire.getPartEtat())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(affaire.getPartCollectivite())).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");

        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatMandatement()
     * Génère le HTML pour l'état de mandatement
     */
    public String genererEtatMandatement(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État de mandatement");

        // Utiliser la méthode de données existante
        EtatMandatementDTO donnees = genererDonneesEtatMandatement(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT DE MANDATEMENT</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>Référence</th><th>Date</th><th>Produit Net</th><th>Part Chefs</th><th>Part Saisissants</th><th>Part Mutuelle</th><th>Part DG</th><th>Part DD</th></tr>");

        for (MandatementDTO mandatement : donnees.getMandatements()) {
            html.append("<tr>");
            html.append("<td>").append(mandatement.getReference()).append("</td>");
            html.append("<td>").append(DateFormatter.format(mandatement.getDateEncaissement())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getProduitNet())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getPartChefs())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getPartSaisissants())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getPartMutuelle())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getPartDG())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getPartDD())).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");

        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatCentreRepartition()
     */
    public String genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État centre répartition");

        CentreRepartitionDTO donnees = genererDonneesCentreRepartition(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT CUMULÉ PAR CENTRE DE RÉPARTITION</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>Centre</th><th>Nb Affaires</th><th>Répartition Base</th><th>Répartition Indicateur</th><th>Total Centre</th></tr>");

        for (CentreStatsDTO centre : donnees.getCentres()) {
            html.append("<tr>");
            html.append("<td>").append(centre.getCentre().getNomCentre()).append("</td>");
            html.append("<td>").append(centre.getNombreAffaires()).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(centre.getRepartitionBase())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(centre.getRepartitionIndicateur())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(centre.getPartTotalCentre())).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatIndicateursReels()
     */
    public String genererEtatIndicateursReels(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État indicateurs réels");

        IndicateursReelsDTO donnees = genererDonneesIndicateursReels(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT DE RÉPARTITION DES PARTS INDICATEURS RÉELS</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>N° Affaire</th><th>Date</th><th>Indicateur</th><th>Montant</th></tr>");

        for (IndicateurReelDTO indicateur : donnees.getIndicateurs()) {
            html.append("<tr>");
            html.append("<td>").append(indicateur.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(DateFormatter.format(indicateur.getDateEncaissement())).append("</td>");
            html.append("<td>").append(indicateur.getIndicateur() != null ? indicateur.getIndicateur().getNomComplet() : "").append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(indicateur.getMontant())).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatRepartitionProduit()
     */
    public String genererEtatRepartitionProduit(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État répartition produit");

        RepartitionProduitDTO donnees = genererDonneesRepartitionProduit(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>N° Encaissement</th><th>N° Affaire</th><th>Produit Disponible</th><th>Part FLCF</th><th>Part Trésor</th><th>Part Ayants Droits</th></tr>");

        for (LigneRepartitionDTO ligne : donnees.getLignes()) {
            html.append("<tr>");
            html.append("<td>").append(ligne.getNumeroEncaissement()).append("</td>");
            html.append("<td>").append(ligne.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(ligne.getProduitDisponible())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(ligne.getPartFLCF())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(ligne.getPartTresor())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(ligne.getPartAyantsDroits())).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatCumuleParAgent()
     */
    public String genererEtatCumuleParAgent(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État cumulé par agent");

        EtatCumuleAgentDTO donnees = genererDonneesEtatCumuleParAgent(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT CUMULÉ PAR AGENT</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>Agent</th><th>Nb Affaires</th><th>Part Chef</th><th>Part Saisissant</th><th>Part DG</th><th>Part DD</th><th>Total</th></tr>");

        for (AgentStatsDTO agent : donnees.getAgents()) {
            html.append("<tr>");
            html.append("<td>").append(agent.getAgent().getNomComplet()).append("</td>");
            html.append("<td>").append(agent.getNombreAffaires()).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(agent.getPartEnTantQueChef())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(agent.getPartEnTantQueSaisissant())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(agent.getPartEnTantQueDG())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(agent.getPartEnTantQueDD())).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(agent.getPartTotaleAgent())).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererEtatMandatementAgents()
     */
    public String genererEtatMandatementAgents(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - État mandatement agents");

        EtatMandatementDTO donnees = genererDonneesMandatementAgents(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>ÉTAT DE MANDATEMENT PAR AGENTS</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>Agent</th><th>Montant Total</th><th>Observations</th></tr>");

        for (MandatementDTO mandatement : donnees.getMandatements()) {
            html.append("<tr>");
            html.append("<td>").append(mandatement.getAgent() != null ? mandatement.getAgent().getNomComplet() : "").append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(mandatement.getMontantTotal())).append("</td>");
            html.append("<td>").append(mandatement.getObservations()).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * CORRECTION BUG : Méthode manquante genererTableauAmendesParServices()
     */
    public String genererTableauAmendesParServices(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération HTML - Tableau amendes par services");

        TableauAmendesParServicesDTO donnees = genererDonneesTableauAmendesParServices(dateDebut, dateFin);

        StringBuilder html = new StringBuilder();
        html.append("<h1>TABLEAU DES AMENDES PAR SERVICES</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(dateDebut));
        html.append(" au ").append(DateFormatter.format(dateFin)).append("</p>");

        html.append("<table border='1'>");
        html.append("<tr><th>Service</th><th>Nb Affaires</th><th>Montant Total</th><th>Observations</th></tr>");

        for (ServiceAmendeDTO service : donnees.getServices()) {
            html.append("<tr>");
            html.append("<td>").append(service.getNomService()).append("</td>");
            html.append("<td>").append(service.getNombreAffaires()).append("</td>");
            html.append("<td>").append(CurrencyFormatter.format(service.getMontantTotal())).append("</td>");
            html.append("<td>").append(service.getObservations()).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * DTO pour l'état des mandatements par agents
     */
    public static class EtatMandatementAgentsDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<AgentMandatementDTO> agents = new ArrayList<>();
        private BigDecimal totalMontantMandatement = BigDecimal.ZERO;
        private int totalNombreEncaissements = 0;
        private int nombreAgents = 0;

        public EtatMandatementAgentsDTO() {
            this.dateGeneration = LocalDate.now();
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<AgentMandatementDTO> getAgents() { return agents; }
        public void setAgents(List<AgentMandatementDTO> agents) { this.agents = agents; }

        public BigDecimal getTotalMontantMandatement() { return totalMontantMandatement; }
        public void setTotalMontantMandatement(BigDecimal totalMontantMandatement) { this.totalMontantMandatement = totalMontantMandatement; }

        public int getTotalNombreEncaissements() { return totalNombreEncaissements; }
        public void setTotalNombreEncaissements(int totalNombreEncaissements) { this.totalNombreEncaissements = totalNombreEncaissements; }

        public int getNombreAgents() { return nombreAgents; }
        public void setNombreAgents(int nombreAgents) { this.nombreAgents = nombreAgents; }
    }

    /**
     * DTO pour une ligne de mandatement - CORRIGÉ avec toutes les méthodes
     */
    public static class MandatementDTO {
        private String reference;
        private LocalDate dateEncaissement;
        private Agent agent;
        private BigDecimal produitNet = BigDecimal.ZERO;
        private BigDecimal partChefs = BigDecimal.ZERO;
        private BigDecimal partSaisissants = BigDecimal.ZERO;
        private BigDecimal partMutuelle = BigDecimal.ZERO;
        private BigDecimal partMasseCommune = BigDecimal.ZERO;
        private BigDecimal partInteressement = BigDecimal.ZERO;
        private BigDecimal partDG = BigDecimal.ZERO;
        private BigDecimal partDD = BigDecimal.ZERO;
        private BigDecimal montantTotal = BigDecimal.ZERO;
        private String observations;

        public void calculateTotal() {
            montantTotal = partChefs.add(partSaisissants).add(partMutuelle)
                    .add(partMasseCommune).add(partInteressement)
                    .add(partDG).add(partDD);
        }

        // Getters et setters
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public Agent getAgent() { return agent; }
        public void setAgent(Agent agent) { this.agent = agent; }

        public BigDecimal getProduitNet() { return produitNet; }
        public void setProduitNet(BigDecimal produitNet) { this.produitNet = produitNet; }

        public BigDecimal getPartChefs() { return partChefs; }
        public void setPartChefs(BigDecimal partChefs) { this.partChefs = partChefs; }

        public BigDecimal getPartSaisissants() { return partSaisissants; }
        public void setPartSaisissants(BigDecimal partSaisissants) { this.partSaisissants = partSaisissants; }

        public BigDecimal getPartMutuelle() { return partMutuelle; }
        public void setPartMutuelle(BigDecimal partMutuelle) { this.partMutuelle = partMutuelle; }

        public BigDecimal getPartMasseCommune() { return partMasseCommune; }
        public void setPartMasseCommune(BigDecimal partMasseCommune) { this.partMasseCommune = partMasseCommune; }

        public BigDecimal getPartInteressement() { return partInteressement; }
        public void setPartInteressement(BigDecimal partInteressement) { this.partInteressement = partInteressement; }

        public BigDecimal getPartDG() { return partDG; }
        public void setPartDG(BigDecimal partDG) { this.partDG = partDG; }

        public BigDecimal getPartDD() { return partDD; }
        public void setPartDD(BigDecimal partDD) { this.partDD = partDD; }

        public BigDecimal getMontantTotal() { return montantTotal; }
        public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }
    }

    /**
     * DTO pour l'état par centre de répartition
     */
    public static class CentreRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<CentreStatsDTO> centres = new ArrayList<>();
        private BigDecimal totalRepartitionBase = BigDecimal.ZERO;
        private BigDecimal totalRepartitionIndicateur = BigDecimal.ZERO;
        private BigDecimal totalPartCentre = BigDecimal.ZERO;
        private BigDecimal totalGeneral = BigDecimal.ZERO;
        private int nombreCentres = 0;

        public CentreRepartitionDTO() {
            this.dateGeneration = LocalDate.now();
        }

        public void calculateTotaux() {
            totalRepartitionBase = centres.stream()
                    .map(CentreStatsDTO::getRepartitionBase)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalRepartitionIndicateur = centres.stream()
                    .map(CentreStatsDTO::getRepartitionIndicateur)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalPartCentre = centres.stream()
                    .map(CentreStatsDTO::getPartTotalCentre)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalGeneral = centres.stream()
                    .map(CentreStatsDTO::getMontantTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            nombreCentres = centres.size();
        }

        public boolean hasActivite() {
            return !centres.isEmpty();
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<CentreStatsDTO> getCentres() { return centres; }
        public void setCentres(List<CentreStatsDTO> centres) { this.centres = centres; }

        public BigDecimal getTotalRepartitionBase() { return totalRepartitionBase; }
        public void setTotalRepartitionBase(BigDecimal totalRepartitionBase) { this.totalRepartitionBase = totalRepartitionBase; }

        public BigDecimal getTotalRepartitionIndicateur() { return totalRepartitionIndicateur; }
        public void setTotalRepartitionIndicateur(BigDecimal totalRepartitionIndicateur) { this.totalRepartitionIndicateur = totalRepartitionIndicateur; }

        public BigDecimal getTotalPartCentre() { return totalPartCentre; }
        public void setTotalPartCentre(BigDecimal totalPartCentre) { this.totalPartCentre = totalPartCentre; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }

        public int getNombreCentres() { return nombreCentres; }
        public void setNombreCentres(int nombreCentres) { this.nombreCentres = nombreCentres; }
    }

    public List<Affaire> genererRapportAffairesNonSoldees(LocalDate dateDebut, LocalDate dateFin) {
        logger.info("📋 Génération du rapport des affaires non soldées - {} au {}", dateDebut, dateFin);

        // Récupérer toutes les affaires non soldées
        List<Affaire> affairesNonSoldees = genererRapportAffairesNonSoldees();

        // Filtrer par période si les dates sont fournies
        if (dateDebut != null && dateFin != null) {
            affairesNonSoldees = affairesNonSoldees.stream()
                    .filter(affaire -> {
                        LocalDate dateCreation = affaire.getDateCreation();
                        return dateCreation != null &&
                                !dateCreation.isBefore(dateDebut) &&
                                !dateCreation.isAfter(dateFin);
                    })
                    .collect(Collectors.toList());
        }

        logger.info("✅ Rapport des affaires non soldées filtré - {} affaires trouvées", affairesNonSoldees.size());
        return affairesNonSoldees;
    }

    // ==================== CLASSES DTO DE BASE CORRIGÉES ====================

    /**
     * DTO pour le rapport de répartition principal
     * ENRICHI avec les méthodes manquantes
     */
    public static class RapportRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<AffaireRepartitionDTO> affaires = new ArrayList<>();
        private List<LigneRepartitionDTO> lignes = new ArrayList<>();
        private BigDecimal totalEncaisse = BigDecimal.ZERO;
        private BigDecimal totalEtat = BigDecimal.ZERO;
        private BigDecimal totalCollectivite = BigDecimal.ZERO;
        private BigDecimal totalProduitDisponible = BigDecimal.ZERO;
        private BigDecimal totalPartIndicateur = BigDecimal.ZERO;
        private BigDecimal totalPartFLCF = BigDecimal.ZERO;
        private BigDecimal totalPartTresor = BigDecimal.ZERO;
        private BigDecimal totalPartAyantsDroits = BigDecimal.ZERO;
        private int nombreAffaires = 0;

        public void calculateTotaux() {
            if (affaires != null && !affaires.isEmpty()) {
                totalEncaisse = affaires.stream()
                        .map(AffaireRepartitionDTO::getMontantEncaisse)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalEtat = affaires.stream()
                        .map(AffaireRepartitionDTO::getPartEtat)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalCollectivite = affaires.stream()
                        .map(AffaireRepartitionDTO::getPartCollectivite)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                nombreAffaires = affaires.size();
            }

            if (lignes != null && !lignes.isEmpty()) {
                totalProduitDisponible = lignes.stream()
                        .map(LigneRepartitionDTO::getProduitDisponible)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalPartIndicateur = lignes.stream()
                        .map(LigneRepartitionDTO::getPartIndicateur)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalPartFLCF = lignes.stream()
                        .map(LigneRepartitionDTO::getPartFLCF)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalPartTresor = lignes.stream()
                        .map(LigneRepartitionDTO::getPartTresor)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalPartAyantsDroits = lignes.stream()
                        .map(LigneRepartitionDTO::getPartAyantsDroits)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }

        // Getters et setters existants
        public BigDecimal getTotalMontant() {
            return totalEncaisse;
        }
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<AffaireRepartitionDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireRepartitionDTO> affaires) { this.affaires = affaires; }

        public List<LigneRepartitionDTO> getLignes() { return lignes; }
        public void setLignes(List<LigneRepartitionDTO> lignes) { this.lignes = lignes; }

        public BigDecimal getTotalEncaisse() { return totalEncaisse; }
        public void setTotalEncaisse(BigDecimal totalEncaisse) { this.totalEncaisse = totalEncaisse; }

        public BigDecimal getTotalEtat() { return totalEtat; }
        public void setTotalEtat(BigDecimal totalEtat) { this.totalEtat = totalEtat; }

        public BigDecimal getTotalCollectivite() { return totalCollectivite; }
        public void setTotalCollectivite(BigDecimal totalCollectivite) { this.totalCollectivite = totalCollectivite; }

        public BigDecimal getTotalProduitDisponible() { return totalProduitDisponible; }
        public void setTotalProduitDisponible(BigDecimal totalProduitDisponible) { this.totalProduitDisponible = totalProduitDisponible; }

        public BigDecimal getTotalPartIndicateur() { return totalPartIndicateur; }
        public void setTotalPartIndicateur(BigDecimal totalPartIndicateur) { this.totalPartIndicateur = totalPartIndicateur; }

        public BigDecimal getTotalPartFLCF() { return totalPartFLCF; }
        public void setTotalPartFLCF(BigDecimal totalPartFLCF) { this.totalPartFLCF = totalPartFLCF; }

        public BigDecimal getTotalPartTresor() { return totalPartTresor; }
        public void setTotalPartTresor(BigDecimal totalPartTresor) { this.totalPartTresor = totalPartTresor; }

        public BigDecimal getTotalPartAyantsDroits() { return totalPartAyantsDroits; }
        public void setTotalPartAyantsDroits(BigDecimal totalPartAyantsDroits) { this.totalPartAyantsDroits = totalPartAyantsDroits; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }

        // Méthodes aliases pour la compatibilité
        public BigDecimal getTotalPartEtat() { return totalEtat; }
        public BigDecimal getTotalPartCollectivite() { return totalCollectivite; }
        public BigDecimal getTotalMontantAmendes() { return totalEncaisse; }
        public BigDecimal getTotalMontantEncaisse() { return totalEncaisse; }
    }

    /**
     * DTO pour les détails d'une affaire dans le rapport
     */
    public static class AffaireRepartitionDTO {
        private String numeroAffaire;
        private LocalDate dateCreation;
        private LocalDate dateEncaissement;
        private String contrevenantNom;
        private String contrevenant;
        private String contraventionType;
        private BigDecimal montantAmende;
        private BigDecimal montantEncaisse;
        private BigDecimal partEtat;
        private BigDecimal partCollectivite;
        private String chefDossier;
        private String bureau;
        private String statut;
        private String nomService;
        private String adresseContrevenant;
        private String observations;

        // Getters et setters
        public BigDecimal getMontantTotal() {
            return montantEncaisse;
        }
        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateCreation() { return dateCreation; }
        public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getContrevenantNom() { return contrevenantNom; }
        public void setContrevenantNom(String contrevenantNom) { this.contrevenantNom = contrevenantNom; }

        public String getContrevenant() { return contrevenant; }
        public void setContrevenant(String contrevenant) { this.contrevenant = contrevenant; }

        public String getContraventionType() { return contraventionType; }
        public void setContraventionType(String contraventionType) { this.contraventionType = contraventionType; }

        public BigDecimal getMontantAmende() { return montantAmende; }
        public void setMontantAmende(BigDecimal montantAmende) { this.montantAmende = montantAmende; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getPartEtat() { return partEtat; }
        public void setPartEtat(BigDecimal partEtat) { this.partEtat = partEtat; }

        public BigDecimal getPartCollectivite() { return partCollectivite; }
        public void setPartCollectivite(BigDecimal partCollectivite) { this.partCollectivite = partCollectivite; }

        public String getChefDossier() { return chefDossier; }
        public void setChefDossier(String chefDossier) { this.chefDossier = chefDossier; }

        public String getBureau() { return bureau; }
        public void setBureau(String bureau) { this.bureau = bureau; }

        public String getStatut() { return statut; }
        public void setStatut(String statut) { this.statut = statut; }

        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public String getAdresseContrevenant() { return adresseContrevenant; }
        public void setAdresseContrevenant(String adresseContrevenant) { this.adresseContrevenant = adresseContrevenant; }

        public String getObservations() { return observations; }
        public void setObservations(String observations) { this.observations = observations; }

        // Alias pour compatibilité
        public String getNomContrevenant() { return contrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.contrevenant = nomContrevenant; }
    }

    /**
     * DTO pour le rapport des encaissements
     */
    public static class RapportEncaissementsDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private List<ServiceEncaissementDTO> services = new ArrayList<>();
        private BigDecimal totalGeneral = BigDecimal.ZERO;

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public List<ServiceEncaissementDTO> getServices() { return services; }
        public void setServices(List<ServiceEncaissementDTO> services) { this.services = services; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }
    }

    /**
     * DTO pour les encaissements par service
     */
    public static class ServiceEncaissementDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String nomService;
        private List<DetailEncaissementDTO> encaissements = new ArrayList<>();
        private List<ServiceStatsDTO> services = new ArrayList<>();
        private BigDecimal totalEncaisse = BigDecimal.ZERO;
        private BigDecimal totalGeneral = BigDecimal.ZERO;
        private int nombreEncaissements = 0;

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getNomService() { return nomService; }
        public void setNomService(String nomService) { this.nomService = nomService; }

        public List<DetailEncaissementDTO> getEncaissements() { return encaissements; }
        public void setEncaissements(List<DetailEncaissementDTO> encaissements) { this.encaissements = encaissements; }

        public List<ServiceStatsDTO> getServices() { return services; }
        public void setServices(List<ServiceStatsDTO> services) { this.services = services; }

        public BigDecimal getTotalEncaisse() { return totalEncaisse; }
        public void setTotalEncaisse(BigDecimal totalEncaisse) { this.totalEncaisse = totalEncaisse; }

        public BigDecimal getTotalGeneral() { return totalGeneral; }
        public void setTotalGeneral(BigDecimal totalGeneral) { this.totalGeneral = totalGeneral; }

        public int getNombreEncaissements() { return nombreEncaissements; }
        public void setNombreEncaissements(int nombreEncaissements) { this.nombreEncaissements = nombreEncaissements; }
    }

    /**
     * DTO pour le détail d'un encaissement
     */
    public static class DetailEncaissementDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private BigDecimal montant;
        private BigDecimal partIndicateur;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }

        public BigDecimal getPartIndicateur() { return partIndicateur; }
        public void setPartIndicateur(BigDecimal partIndicateur) { this.partIndicateur = partIndicateur; }
    }

    // ==================== AUTRES CLASSES DTO UTILITAIRES ====================

    public static class TableauAmendesServiceDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<ServiceStatsDTO> services = new ArrayList<>();
        private int totalAffaires = 0;
        private BigDecimal totalMontant = BigDecimal.ZERO;
        private int nombreServices = 0;

        public TableauAmendesServiceDTO() {
            this.dateGeneration = LocalDate.now();
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<ServiceStatsDTO> getServices() { return services; }
        public void setServices(List<ServiceStatsDTO> services) { this.services = services; }

        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }

        public BigDecimal getTotalMontant() { return totalMontant; }
        public void setTotalMontant(BigDecimal totalMontant) { this.totalMontant = totalMontant; }

        public int getNombreServices() { return nombreServices; }
        public void setNombreServices(int nombreServices) { this.nombreServices = nombreServices; }
    }

    public static class EtatRepartitionAffairesDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private String titreRapport;
        private List<AffaireRepartitionDTO> affaires = new ArrayList<>();
        private BigDecimal totalMontantAmendes = BigDecimal.ZERO;
        private BigDecimal totalMontantEncaisse = BigDecimal.ZERO;
        private BigDecimal totalPartEtat = BigDecimal.ZERO;
        private BigDecimal totalPartCollectivite = BigDecimal.ZERO;
        private int nombreAffaires = 0;

        public EtatRepartitionAffairesDTO() {
            this.dateGeneration = LocalDate.now();
        }

        // Getters et setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

        public LocalDate getDateGeneration() { return dateGeneration; }
        public void setDateGeneration(LocalDate dateGeneration) { this.dateGeneration = dateGeneration; }

        public String getPeriodeLibelle() { return periodeLibelle; }
        public void setPeriodeLibelle(String periodeLibelle) { this.periodeLibelle = periodeLibelle; }

        public String getTitreRapport() { return titreRapport; }
        public void setTitreRapport(String titreRapport) { this.titreRapport = titreRapport; }

        public List<AffaireRepartitionDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireRepartitionDTO> affaires) { this.affaires = affaires; }

        public BigDecimal getTotalMontantAmendes() { return totalMontantAmendes; }
        public void setTotalMontantAmendes(BigDecimal totalMontantAmendes) { this.totalMontantAmendes = totalMontantAmendes; }

        public BigDecimal getTotalMontantEncaisse() { return totalMontantEncaisse; }
        public void setTotalMontantEncaisse(BigDecimal totalMontantEncaisse) { this.totalMontantEncaisse = totalMontantEncaisse; }

        public BigDecimal getTotalPartEtat() { return totalPartEtat; }
        public void setTotalPartEtat(BigDecimal totalPartEtat) { this.totalPartEtat = totalPartEtat; }

        public BigDecimal getTotalPartCollectivite() { return totalPartCollectivite; }
        public void setTotalPartCollectivite(BigDecimal totalPartCollectivite) { this.totalPartCollectivite = totalPartCollectivite; }

        public int getNombreAffaires() { return nombreAffaires; }
        public void setNombreAffaires(int nombreAffaires) { this.nombreAffaires = nombreAffaires; }
    }

    // CORRECTION : Classes DTO manquantes avec validation `hasActivite()`
    public static class ProduitRepartitionDTO {
        private String numeroEncaissement;
        private String numeroAffaire;
        private LocalDate dateEncaissement;
        private BigDecimal montantEncaisse;
        private BigDecimal produitDisponible;
        private BigDecimal partTresor;
        private BigDecimal partFLCF;
        private BigDecimal partAyantsDroits;
        private String nomContrevenant;

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public BigDecimal getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(BigDecimal montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public BigDecimal getProduitDisponible() { return produitDisponible; }
        public void setProduitDisponible(BigDecimal produitDisponible) { this.produitDisponible = produitDisponible; }

        public BigDecimal getPartTresor() { return partTresor; }
        public void setPartTresor(BigDecimal partTresor) { this.partTresor = partTresor; }

        public BigDecimal getPartFLCF() { return partFLCF; }
        public void setPartFLCF(BigDecimal partFLCF) { this.partFLCF = partFLCF; }

        public BigDecimal getPartAyantsDroits() { return partAyantsDroits; }
        public void setPartAyantsDroits(BigDecimal partAyantsDroits) { this.partAyantsDroits = partAyantsDroits; }

        public String getNomContrevenant() { return nomContrevenant; }
        public void setNomContrevenant(String nomContrevenant) { this.nomContrevenant = nomContrevenant; }
    }

}