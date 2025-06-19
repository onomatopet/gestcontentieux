package com.regulation.contentieux.service;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.service.RapportService.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.concurrent.Task;
import javafx.print.*;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service d'impression des rapports de rétrocession
 * Gère la génération HTML et l'impression des 8 imprimés
 */
public class PrintService {

    private static final Logger logger = LoggerFactory.getLogger(PrintService.class);

    // VARIABLES D'INSTANCE À AJOUTER EN HAUT DE LA CLASSE
    private AffaireDAO affaireDAO;
    private EncaissementDAO encaissementDAO;
    private String partIndicateur = "15.0"; // Pourcentage par défaut pour les indicateurs

    // CONSTRUCTEUR À AJOUTER OU MODIFIER
    public PrintService() {
        this.affaireDAO = new AffaireDAO();
        this.encaissementDAO = new EncaissementDAO();
    }

    // ==================== TEMPLATE HTML DE BASE ====================

    /**
     * Template HTML de base pour tous les imprimés
     */
    private static final String BASE_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>{{TITRE}}</title>
            <style>
                body { 
                    font-family: 'Arial', sans-serif; 
                    margin: 15px; 
                    font-size: 12px;
                    line-height: 1.3;
                }
                .header { 
                    text-align: center; 
                    margin-bottom: 25px; 
                    border-bottom: 2px solid #000;
                    padding-bottom: 15px;
                }
                .title { 
                    font-size: 16px; 
                    font-weight: bold; 
                    margin-bottom: 8px;
                    text-transform: uppercase;
                }
                .subtitle { 
                    font-size: 14px; 
                    margin-bottom: 5px;
                }
                .period { 
                    font-size: 12px; 
                    color: #666; 
                }
                
                table { 
                    width: 100%; 
                    border-collapse: collapse; 
                    margin-bottom: 20px;
                    font-size: 10px;
                }
                th, td { 
                    padding: 4px 6px; 
                    text-align: left; 
                    border: 1px solid #000;
                    vertical-align: middle;
                }
                th { 
                    background-color: #f0f0f0; 
                    font-weight: bold;
                    text-align: center;
                }
                .currency { 
                    text-align: right; 
                    font-family: 'Courier New', monospace;
                }
                .center { 
                    text-align: center; 
                }
                .total-row { 
                    font-weight: bold; 
                    background-color: #f9f9f9; 
                }
                .section-header {
                    background-color: #e0e0e0;
                    font-weight: bold;
                    text-align: center;
                }
                .sub-section-header {
                    background-color: #f0f0f0;
                    font-weight: bold;
                    font-style: italic;
                }
                
                .footer { 
                    margin-top: 30px; 
                    text-align: center; 
                    font-size: 10px; 
                    color: #666;
                    border-top: 1px solid #ccc;
                    padding-top: 10px;
                }
                
                @media print {
                    body { margin: 0; font-size: 11px; }
                    .page-break { page-break-before: always; }
                    table { font-size: 9px; }
                    th, td { padding: 3px 5px; }
                }
            </style>
        </head>
        <body>
            {{HEADER}}
            {{CONTENT}}
            {{FOOTER}}
        </body>
        </html>
        """;

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Génère l'en-tête standard pour tous les imprimés
     */
    private String genererEnteteStandard(String titre, String periode, LocalDateTime dateGeneration) {
        return String.format("""
            <div class="header">
                <div class="title">%s</div>
                <div class="subtitle">Période: %s</div>
                <div class="period">Généré le: %s</div>
            </div>
            """,
                titre,
                periode,
                DateFormatter.formatDateTime(dateGeneration)
        );
    }

    /**
     * Génère le pied de page standard
     */
    private String genererPiedPageStandard() {
        return String.format("""
            <div class="footer">
                Document généré automatiquement le %s par l'application de gestion des affaires contentieuses
            </div>
            """,
                DateFormatter.formatDateTime(LocalDateTime.now())
        );
    }

    /**
     * Échapper les caractères HTML
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    // ==================== DTO 1: ETAT DE REPARTITION DES AFFAIRES CONTENTIEUSES ====================

    /**
     * DTO pour l'imprimé 1: "ETAT DE REPARTITION DES AFFAIRES CONTENTIEUSE"
     * Le plus complexe avec toutes les colonnes de répartition
     */
    public static class EtatRepartitionAffairesDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<AffaireRepartitionCompleteDTO> affaires;
        private TotauxRepartitionDTO totaux;
        private int totalAffaires;

        public EtatRepartitionAffairesDTO() {
            this.affaires = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totaux = new TotauxRepartitionDTO();
            this.totalAffaires = 0;
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

        public List<AffaireRepartitionCompleteDTO> getAffaires() { return affaires; }
        public void setAffaires(List<AffaireRepartitionCompleteDTO> affaires) { this.affaires = affaires; }

        public TotauxRepartitionDTO getTotaux() { return totaux; }
        public void setTotaux(TotauxRepartitionDTO totaux) { this.totaux = totaux; }

        public int getTotalAffaires() { return totalAffaires; }
        public void setTotalAffaires(int totalAffaires) { this.totalAffaires = totalAffaires; }
    }

    /**
     * DTO pour une ligne complète de répartition d'affaire
     */
    public static class AffaireRepartitionCompleteDTO {
        private String numeroEncaissement;
        private LocalDate dateEncaissement;
        private String numeroAffaire;
        private LocalDate dateAffaire;
        private BigDecimal produitDisponible;
        private String directionDepartementale;
        private BigDecimal indicateur;
        private BigDecimal produitNet;
        private BigDecimal flcf;
        private BigDecimal tresor;
        private BigDecimal produitNetAyantsDroits;
        private BigDecimal chefs;
        private BigDecimal saisissants;
        private BigDecimal mutuelleNationale;
        private BigDecimal masseCommune;
        private BigDecimal interessement;

        public AffaireRepartitionCompleteDTO() {}

        // Getters et setters
        public String getNumeroEncaissement() { return numeroEncaissement; }
        public void setNumeroEncaissement(String numeroEncaissement) { this.numeroEncaissement = numeroEncaissement; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateAffaire() { return dateAffaire; }
        public void setDateAffaire(LocalDate dateAffaire) { this.dateAffaire = dateAffaire; }

        public BigDecimal getProduitDisponible() { return produitDisponible; }
        public void setProduitDisponible(BigDecimal produitDisponible) { this.produitDisponible = produitDisponible; }

        public String getDirectionDepartementale() { return directionDepartementale; }
        public void setDirectionDepartementale(String directionDepartementale) { this.directionDepartementale = directionDepartementale; }

        public BigDecimal getIndicateur() { return indicateur; }
        public void setIndicateur(BigDecimal indicateur) { this.indicateur = indicateur; }

        public BigDecimal getProduitNet() { return produitNet; }
        public void setProduitNet(BigDecimal produitNet) { this.produitNet = produitNet; }

        public BigDecimal getFlcf() { return flcf; }
        public void setFlcf(BigDecimal flcf) { this.flcf = flcf; }

        public BigDecimal getTresor() { return tresor; }
        public void setTresor(BigDecimal tresor) { this.tresor = tresor; }

        public BigDecimal getProduitNetAyantsDroits() { return produitNetAyantsDroits; }
        public void setProduitNetAyantsDroits(BigDecimal produitNetAyantsDroits) { this.produitNetAyantsDroits = produitNetAyantsDroits; }

        public BigDecimal getChefs() { return chefs; }
        public void setChefs(BigDecimal chefs) { this.chefs = chefs; }

        public BigDecimal getSaisissants() { return saisissants; }
        public void setSaisissants(BigDecimal saisissants) { this.saisissants = saisissants; }

        public BigDecimal getMutuelleNationale() { return mutuelleNationale; }
        public void setMutuelleNationale(BigDecimal mutuelleNationale) { this.mutuelleNationale = mutuelleNationale; }

        public BigDecimal getMasseCommune() { return masseCommune; }
        public void setMasseCommune(BigDecimal masseCommune) { this.masseCommune = masseCommune; }

        public BigDecimal getInteressement() { return interessement; }
        public void setInteressement(BigDecimal interessement) { this.interessement = interessement; }
    }

    /**
     * DTO pour les totaux de répartition
     */
    public static class TotauxRepartitionDTO {
        private BigDecimal totalProduitDisponible;
        private BigDecimal totalIndicateur;
        private BigDecimal totalProduitNet;
        private BigDecimal totalFlcf;
        private BigDecimal totalTresor;
        private BigDecimal totalProduitNetAyantsDroits;
        private BigDecimal totalChefs;
        private BigDecimal totalSaisissants;
        private BigDecimal totalMutuelleNationale;
        private BigDecimal totalMasseCommune;
        private BigDecimal totalInteressement;

        public TotauxRepartitionDTO() {
            this.totalProduitDisponible = BigDecimal.ZERO;
            this.totalIndicateur = BigDecimal.ZERO;
            this.totalProduitNet = BigDecimal.ZERO;
            this.totalFlcf = BigDecimal.ZERO;
            this.totalTresor = BigDecimal.ZERO;
            this.totalProduitNetAyantsDroits = BigDecimal.ZERO;
            this.totalChefs = BigDecimal.ZERO;
            this.totalSaisissants = BigDecimal.ZERO;
            this.totalMutuelleNationale = BigDecimal.ZERO;
            this.totalMasseCommune = BigDecimal.ZERO;
            this.totalInteressement = BigDecimal.ZERO;
        }

        // Getters et setters
        public BigDecimal getTotalProduitDisponible() { return totalProduitDisponible; }
        public void setTotalProduitDisponible(BigDecimal totalProduitDisponible) { this.totalProduitDisponible = totalProduitDisponible; }

        public BigDecimal getTotalIndicateur() { return totalIndicateur; }
        public void setTotalIndicateur(BigDecimal totalIndicateur) { this.totalIndicateur = totalIndicateur; }

        public BigDecimal getTotalProduitNet() { return totalProduitNet; }
        public void setTotalProduitNet(BigDecimal totalProduitNet) { this.totalProduitNet = totalProduitNet; }

        public BigDecimal getTotalFlcf() { return totalFlcf; }
        public void setTotalFlcf(BigDecimal totalFlcf) { this.totalFlcf = totalFlcf; }

        public BigDecimal getTotalTresor() { return totalTresor; }
        public void setTotalTresor(BigDecimal totalTresor) { this.totalTresor = totalTresor; }

        public BigDecimal getTotalProduitNetAyantsDroits() { return totalProduitNetAyantsDroits; }
        public void setTotalProduitNetAyantsDroits(BigDecimal totalProduitNetAyantsDroits) { this.totalProduitNetAyantsDroits = totalProduitNetAyantsDroits; }

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
    }

    /**
     * Génère l'état de répartition des affaires contentieuses (Imprimé 1)
     */
    public EtatRepartitionAffairesDTO genererEtatRepartitionAffaires(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état de répartition des affaires contentieuses du {} au {}", dateDebut, dateFin);

            EtatRepartitionAffairesDTO etat = new EtatRepartitionAffairesDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // Récupération des affaires avec encaissements
            List<Affaire> affaires = affaireDAO.findAffairesWithEncaissementsByPeriod(dateDebut, dateFin);

            List<AffaireRepartitionCompleteDTO> affairesDTO = new ArrayList<>();
            TotauxRepartitionDTO totaux = new TotauxRepartitionDTO();

            // Traitement de chaque affaire
            for (Affaire affaire : affaires) {
                List<Encaissement> encaissements = encaissementDAO.findByAffaireAndPeriod(
                        affaire.getId(), dateDebut, dateFin);

                for (Encaissement encaissement : encaissements) {
                    if (encaissement.getStatut() == StatutEncaissement.VALIDE) {
                        AffaireRepartitionCompleteDTO affaireDTO = new AffaireRepartitionCompleteDTO();

                        // Données de base
                        affaireDTO.setNumeroEncaissement(encaissement.getReferenceMandat());
                        affaireDTO.setDateEncaissement(encaissement.getDateEncaissement());
                        affaireDTO.setNumeroAffaire(affaire.getNumeroAffaire());
                        affaireDTO.setDateAffaire(affaire.getDateCreation());

                        // Direction départementale (indicatif pour l'instant)
                        affaireDTO.setDirectionDepartementale(determinerDirectionDepartementale(affaire));

                        // Calculs de répartition selon la hiérarchie
                        BigDecimal produitDisponible = encaissement.getMontant();
                        BigDecimal indicateur = CalculsRepartition.calculerIndicateur(produitDisponible);
                        BigDecimal produitNet = CalculsRepartition.calculerProduitNet(produitDisponible);
                        BigDecimal flcf = CalculsRepartition.calculerFLCF(produitNet);
                        BigDecimal tresor = CalculsRepartition.calculerTresor(produitNet);
                        BigDecimal produitNetAyantsDroits = CalculsRepartition.calculerProduitNetAyantsDroits(produitNet);

                        BigDecimal chefs = CalculsRepartition.calculerPartChefs(produitNetAyantsDroits);
                        BigDecimal saisissants = CalculsRepartition.calculerPartSaisissants(produitNetAyantsDroits);
                        BigDecimal mutuelleNationale = CalculsRepartition.calculerPartMutuelleNationale(produitNetAyantsDroits);
                        BigDecimal masseCommune = CalculsRepartition.calculerPartMasseCommune(produitNetAyantsDroits);
                        BigDecimal interessement = CalculsRepartition.calculerPartInteressement(produitNetAyantsDroits);

                        // Remplissage du DTO
                        affaireDTO.setProduitDisponible(produitDisponible);
                        affaireDTO.setIndicateur(indicateur);
                        affaireDTO.setProduitNet(produitNet);
                        affaireDTO.setFlcf(flcf);
                        affaireDTO.setTresor(tresor);
                        affaireDTO.setProduitNetAyantsDroits(produitNetAyantsDroits);
                        affaireDTO.setChefs(chefs);
                        affaireDTO.setSaisissants(saisissants);
                        affaireDTO.setMutuelleNationale(mutuelleNationale);
                        affaireDTO.setMasseCommune(masseCommune);
                        affaireDTO.setInteressement(interessement);

                        affairesDTO.add(affaireDTO);

                        // Accumulation des totaux
                        totaux.setTotalProduitDisponible(totaux.getTotalProduitDisponible().add(produitDisponible));
                        totaux.setTotalPartIndicateur(totaux.getTotalPartIndicateur().add(partIndicateur));
                        totaux.setTotalPartDirectionContentieux(totaux.getTotalPartDirectionContentieux().add(affaireDTO.getPartDirectionContentieux()));
                        totaux.setTotalPartIndicateur2(totaux.getTotalPartIndicateur2().add(partIndicateur));
                        totaux.setTotalFlcf(totaux.getTotalFlcf().add(flcf));
                        totaux.setTotalMontantTresor(totaux.getTotalMontantTresor().add(tresor));
                        totaux.setTotalMontantGlobalAyantsDroits(totaux.getTotalMontantGlobalAyantsDroits().add(produitNetAyantsDroits));
                    }
                }
            }

            // Tri par date d'encaissement
            affairesDTO.sort((a1, a2) -> a1.getDateEncaissement().compareTo(a2.getDateEncaissement()));

            etat.setAffaires(affairesDTO);
            etat.setTotaux(totaux);
            etat.setTotalAffaires(affaires.size());

            logger.info("État de répartition du produit généré: {} lignes, {} FCFA produit disponible",
                    affairesDTO.size(), CurrencyFormatter.format(totaux.getTotalProduitDisponible()));

            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état de répartition du produit", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }


    // ==================== IMPRIMÉ 2: ÉTAT PAR SÉRIES DE MANDATEMENT ====================

    /**
     * Génère le HTML pour l'imprimé 2
     */
    public String genererHTML_EtatMandatement(EtatMandatementDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("""
            <table>
                <thead>
                    <tr>
                        <th rowspan="2">N° encaissement et Date</th>
                        <th rowspan="2">N° Affaire et Date</th>
                        <th rowspan="2">Produit net</th>
                        <th colspan="5">Part revenant aux</th>
                        <th rowspan="2">Observations</th>
                    </tr>
                    <tr>
                        <th>Chefs</th>
                        <th>Saisissants</th>
                        <th>Mutuelle nationale</th>
                        <th>Masse commune</th>
                        <th>Intéressement</th>
                    </tr>
                </thead>
                <tbody>
            """);

        // Lignes de données
        for (MandatementDTO mandatement : etat.getMandatements()) {
            content.append(String.format("""
                <tr>
                    <td class="center">%s<br/>%s</td>
                    <td class="center">%s<br/>%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td>%s</td>
                </tr>
                """,
                    escapeHtml(mandatement.getNumeroEncaissement()),
                    DateFormatter.formatDate(mandatement.getDateEncaissement()),
                    escapeHtml(mandatement.getNumeroAffaire()),
                    DateFormatter.formatDate(mandatement.getDateAffaire()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getProduitNet()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartChefs()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartSaisissants()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartMutuelleNationale()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartMasseCommune()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartInteressement()),
                    escapeHtml(mandatement.getObservations())
            ));
        }

        // Ligne de total
        content.append(String.format("""
                <tr class="total-row">
                    <td colspan="2"><strong>TOTAUX</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td></td>
                </tr>
            """,
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalProduitNet()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalChefs()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalSaisissants()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalMutuelleNationale()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalMasseCommune()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalInteressement())
        ));

        content.append("""
                </tbody>
            </table>
            """);

        return assemblerHTML("État par Séries de Mandatement",
                "ÉTAT PAR SÉRIES DE MANDATEMENT",
                etat.getPeriodeLibelle(), etat.getDateGeneration(), content.toString());
    }

    // ==================== DTO 3: ETAT CUMULE PAR CENTRE DE REPARTITION ====================

    /**
     * DTO pour l'imprimé 3: "ETAT CUMULE PAR CENTRE DE REPARTITION"
     * (À garder pour la fin comme convenu)
     */
    public static class EtatCentreRepartitionDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalDate dateGeneration;
        private String periodeLibelle;
        private List<CentreRepartitionDTO> centres;
        private BigDecimal totalRepartitionBase;
        private BigDecimal totalRepartitionIndicateurFictif;
        private BigDecimal totalPartCentre;

        public EtatCentreRepartitionDTO() {
            this.centres = new ArrayList<>();
            this.dateGeneration = LocalDate.now();
            this.totalRepartitionBase = BigDecimal.ZERO;
            this.totalRepartitionIndicateurFictif = BigDecimal.ZERO;
            this.totalPartCentre = BigDecimal.ZERO;
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

        public List<CentreRepartitionDTO> getCentres() { return centres; }
        public void setCentres(List<CentreRepartitionDTO> centres) { this.centres = centres; }

        public BigDecimal getTotalRepartitionBase() { return totalRepartitionBase; }
        public void setTotalRepartitionBase(BigDecimal totalRepartitionBase) { this.totalRepartitionBase = totalRepartitionBase; }

        public BigDecimal getTotalRepartitionIndicateurFictif() { return totalRepartitionIndicateurFictif; }
        public void setTotalRepartitionIndicateurFictif(BigDecimal totalRepartitionIndicateurFictif) { this.totalRepartitionIndicateurFictif = totalRepartitionIndicateurFictif; }

        public BigDecimal getTotalPartCentre() { return totalPartCentre; }
        public void setTotalPartCentre(BigDecimal totalPartCentre) { this.totalPartCentre = totalPartCentre; }
    }

    /**
     * DTO pour un centre de répartition
     */
    public static class CentreRepartitionDTO {
        private String nomCentre;
        private BigDecimal partRepartitionBase;
        private BigDecimal partRepartitionIndicateurFictif;
        private BigDecimal partTotaleCentre;

        public CentreRepartitionDTO() {}

        public CentreRepartitionDTO(String nomCentre) {
            this.nomCentre = nomCentre;
            this.partRepartitionBase = BigDecimal.ZERO;
            this.partRepartitionIndicateurFictif = BigDecimal.ZERO;
            this.partTotaleCentre = BigDecimal.ZERO;
        }

        // Getters et setters
        public String getNomCentre() { return nomCentre; }
        public void setNomCentre(String nomCentre) { this.nomCentre = nomCentre; }

        public BigDecimal getPartRepartitionBase() { return partRepartitionBase; }
        public void setPartRepartitionBase(BigDecimal partRepartitionBase) { this.partRepartitionBase = partRepartitionBase; }

        public BigDecimal getPartRepartitionIndicateurFictif() { return partRepartitionIndicateurFictif; }
        public void setPartRepartitionIndicateurFictif(BigDecimal partRepartitionIndicateurFictif) { this.partRepartitionIndicateurFictif = partRepartitionIndicateurFictif; }

        public BigDecimal getPartTotaleCentre() { return partTotaleCentre; }
        public void setPartTotaleCentre(BigDecimal partTotaleCentre) { this.partTotaleCentre = partTotaleCentre; }

        /**
         * Calcule le total du centre
         */
        public void calculerTotal() {
            this.partTotaleCentre = this.partRepartitionBase.add(this.partRepartitionIndicateurFictif);
        }
    }

    /**
     * Génère l'état cumulé par centre de répartition (Imprimé 3)
     * Méthode placeholder - à implémenter plus tard
     */
    public EtatCentreRepartitionDTO genererEtatCentreRepartition(LocalDate dateDebut, LocalDate dateFin) {
        try {
            logger.info("Génération de l'état cumulé par centre de répartition du {} au {}", dateDebut, dateFin);

            EtatCentreRepartitionDTO etat = new EtatCentreRepartitionDTO();
            etat.setDateDebut(dateDebut);
            etat.setDateFin(dateFin);
            etat.setPeriodeLibelle(formatPeriode(dateDebut, dateFin));

            // TODO: Implémenter la logique des centres de répartition
            // Cette méthode est gardée pour la fin comme convenu

            List<CentreRepartitionDTO> centres = new ArrayList<>();
            // Logique à implémenter...

            etat.setCentres(centres);

            logger.info("État cumulé par centre de répartition généré (placeholder)");
            return etat;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'état par centre de répartition", e);
            throw new RuntimeException("Impossible de générer l'état: " + e.getMessage(), e);
        }
    }

    // ==================== IMPRIMÉ 4: ÉTAT DE RÉPARTITION DES INDICATEURS RÉELS ====================

    /**
     * Génère le HTML pour l'imprimé 4
     */
    public String genererHTML_EtatRepartitionIndicateursReels(EtatRepartitionIndicateursReelsDTO etat) {
        StringBuilder content = new StringBuilder();

        for (ServiceIndicateurDTO service : etat.getServices()) {
            content.append(String.format("""
                <table>
                    <thead>
                        <tr>
                            <th>N° encaissement et Date</th>
                            <th>N° Affaire et Date</th>
                            <th>Noms des contrevenants</th>
                            <th>Contraventions</th>
                            <th>Montant encaissement</th>
                            <th>Part indicateur</th>
                            <th>Observations</th>
                        </tr>
                        <tr class="section-header">
                            <th colspan="7">Service : %s</th>
                        </tr>
                    </thead>
                    <tbody>
                """, escapeHtml(service.getNomService())));

            for (SectionIndicateurDTO section : service.getSections()) {
                content.append(String.format("""
                    <tr class="sub-section-header">
                        <td colspan="7">Section : %s</td>
                    </tr>
                    """, escapeHtml(section.getNomSection())));

                for (AffaireIndicateurDTO affaire : section.getAffaires()) {
                    content.append(String.format("""
                        <tr>
                            <td class="center">%s<br/>%s</td>
                            <td class="center">%s<br/>%s</td>
                            <td>%s</td>
                            <td>%s</td>
                            <td class="currency">%s</td>
                            <td class="currency">%s</td>
                            <td>%s</td>
                        </tr>
                        """,
                            escapeHtml(affaire.getNumeroEncaissement()),
                            DateFormatter.formatDate(affaire.getDateEncaissement()),
                            escapeHtml(affaire.getNumeroAffaire()),
                            DateFormatter.formatDate(affaire.getDateAffaire()),
                            escapeHtml(affaire.getNomContrevenant()),
                            escapeHtml(affaire.getNomContravention()),
                            CurrencyFormatter.formatWithoutSymbol(affaire.getMontantEncaissement()),
                            CurrencyFormatter.formatWithoutSymbol(affaire.getPartIndicateur()),
                            escapeHtml(affaire.getObservations())
                    ));
                }

                content.append(String.format("""
                    <tr class="total-row">
                        <td colspan="4"><strong>Sous-total Section %s</strong></td>
                        <td class="currency"><strong>%s</strong></td>
                        <td class="currency"><strong>%s</strong></td>
                        <td></td>
                    </tr>
                    """,
                        escapeHtml(section.getNomSection()),
                        CurrencyFormatter.formatWithoutSymbol(section.getTotalMontantSection()),
                        CurrencyFormatter.formatWithoutSymbol(section.getTotalPartIndicateurSection())
                ));
            }

            content.append(String.format("""
                <tr class="total-row">
                    <td colspan="4"><strong>TOTAL SERVICE %s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td></td>
                </tr>
                """,
                    escapeHtml(service.getNomService()),
                    CurrencyFormatter.formatWithoutSymbol(service.getTotalMontantService()),
                    CurrencyFormatter.formatWithoutSymbol(service.getTotalPartIndicateurService())
            ));

            content.append("</tbody></table><div class='page-break'></div>");
        }

        content.append(String.format("""
            <table>
                <tr class="total-row">
                    <td colspan="4"><strong>TOTAL GÉNÉRAL</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td></td>
                </tr>
            </table>
            """,
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalMontantEncaissement()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalPartIndicateur())
        ));

        return assemblerHTML("État de Répartition des Indicateurs Réels",
                "ÉTAT DE RÉPARTITION DES PART DES INDICATEURS RÉELS",
                etat.getPeriodeLibelle(), etat.getDateGeneration(), content.toString());
    }

    // ==================== IMPRIMÉ 6: ÉTAT CUMULÉ PAR AGENT ====================

    /**
     * Génère le HTML pour l'imprimé 6
     */
    public String genererHTML_EtatCumuleParAgent(EtatCumuleParAgentDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("""
            <table>
                <thead>
                    <tr>
                        <th rowspan="2">Nom de l'agent</th>
                        <th colspan="4">Part revenant à l'agent après répartition en tant que</th>
                        <th rowspan="2">Part agent</th>
                    </tr>
                    <tr>
                        <th>Chef</th>
                        <th>Saisissant</th>
                        <th>DG</th>
                        <th>DD</th>
                    </tr>
                </thead>
                <tbody>
            """);

        for (AgentCumuleDTO agent : etat.getAgents()) {
            content.append(String.format("""
                <tr>
                    <td>%s (%s)</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                </tr>
                """,
                    escapeHtml(agent.getNomAgent()),
                    escapeHtml(agent.getCodeAgent()),
                    CurrencyFormatter.formatWithoutSymbol(agent.getPartChef()),
                    CurrencyFormatter.formatWithoutSymbol(agent.getPartSaisissant()),
                    CurrencyFormatter.formatWithoutSymbol(agent.getPartDG()),
                    CurrencyFormatter.formatWithoutSymbol(agent.getPartDD()),
                    CurrencyFormatter.formatWithoutSymbol(agent.getPartTotaleAgent())
            ));
        }

        BigDecimal totalChefs = etat.getAgents().stream()
                .map(AgentCumuleDTO::getPartChef)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDG = etat.getAgents().stream()
                .map(AgentCumuleDTO::getPartDG)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDD = etat.getAgents().stream()
                .map(AgentCumuleDTO::getPartDD)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        content.append(String.format("""
                <tr class="total-row">
                    <td><strong>TOTAL GÉNÉRAL</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                </tr>
            """,
                CurrencyFormatter.formatWithoutSymbol(totalChefs),
                CurrencyFormatter.formatWithoutSymbol(totalSaisissants),
                CurrencyFormatter.formatWithoutSymbol(totalDG),
                CurrencyFormatter.formatWithoutSymbol(totalDD),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalGeneral())
        ));

        content.append("""
                </tbody>
            </table>
            """);

        return assemblerHTML("État Cumulé par Agent",
                "ÉTAT CUMULÉ PAR AGENT",
                etat.getPeriodeLibelle(), etat.getDateGeneration(), content.toString());
    }

    // ==================== IMPRIMÉ 7: TABLEAU DES AMENDES PAR SERVICES ====================

    /**
     * Génère le HTML pour l'imprimé 7
     */
    public String genererHTML_TableauAmendesParServices(TableauAmendesParServicesDTO tableau) {
        StringBuilder content = new StringBuilder();

        content.append("""
            <table>
                <thead>
                    <tr>
                        <th>Services</th>
                        <th>Nombre d'affaires</th>
                        <th>Montant</th>
                        <th>Observations</th>
                    </tr>
                </thead>
                <tbody>
            """);

        for (ServiceAmendeDTO service : tableau.getServices()) {
            content.append(String.format("""
                <tr>
                    <td>%s</td>
                    <td class="center">%d</td>
                    <td class="currency">%s</td>
                    <td>%s</td>
                </tr>
                """,
                    escapeHtml(service.getNomService()),
                    service.getNombreAffaires(),
                    CurrencyFormatter.formatWithoutSymbol(service.getMontantTotal()),
                    escapeHtml(service.getObservations())
            ));
        }

        content.append(String.format("""
                <tr class="total-row">
                    <td><strong>TOTAL GÉNÉRAL</strong></td>
                    <td class="center"><strong>%d</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td></td>
                </tr>
            """,
                tableau.getTotalAffaires(),
                CurrencyFormatter.formatWithoutSymbol(tableau.getTotalMontant())
        ));

        content.append("""
                </tbody>
            </table>
            """);

        return assemblerHTML("Tableau des Amendes par Services",
                "TABLEAU DES AMENDES PAR SERVICES",
                tableau.getPeriodeLibelle(), tableau.getDateGeneration(), content.toString());
    }

    // ==================== IMPRIMÉ 8: ÉTAT PAR SÉRIES DE MANDATEMENTS (AGENTS) ====================

    /**
     * Génère le HTML pour l'imprimé 8
     */
    public String genererHTML_EtatMandatementAgents(EtatMandatementDTO etat) {
        StringBuilder content = new StringBuilder();

        content.append("""
            <table>
                <thead>
                    <tr>
                        <th rowspan="2">N° encaissement et Date</th>
                        <th rowspan="2">N° Affaire et Date</th>
                        <th colspan="5">Part revenant à l'agent après répartition en tant que</th>
                        <th rowspan="2">Part agent</th>
                    </tr>
                    <tr>
                        <th>Chefs</th>
                        <th>Saisissants</th>
                        <th>Mutuelle nationale</th>
                        <th>D.G</th>
                        <th>D.D</th>
                    </tr>
                </thead>
                <tbody>
            """);

        for (MandatementDTO mandatement : etat.getMandatements()) {
            BigDecimal partAgent = mandatement.getPartChefs()
                    .add(mandatement.getPartSaisissants())
                    .add(mandatement.getPartMutuelleNationale())
                    .add(mandatement.getPartDG() != null ? mandatement.getPartDG() : BigDecimal.ZERO)
                    .add(mandatement.getPartDD() != null ? mandatement.getPartDD() : BigDecimal.ZERO);

            content.append(String.format("""
                <tr>
                    <td class="center">%s<br/>%s</td>
                    <td class="center">%s<br/>%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                    <td class="currency">%s</td>
                </tr>
                """,
                    escapeHtml(mandatement.getNumeroEncaissement()),
                    DateFormatter.formatDate(mandatement.getDateEncaissement()),
                    escapeHtml(mandatement.getNumeroAffaire()),
                    DateFormatter.formatDate(mandatement.getDateAffaire()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartChefs()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartSaisissants()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartMutuelleNationale()),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartDG() != null ? mandatement.getPartDG() : BigDecimal.ZERO),
                    CurrencyFormatter.formatWithoutSymbol(mandatement.getPartDD() != null ? mandatement.getPartDD() : BigDecimal.ZERO),
                    CurrencyFormatter.formatWithoutSymbol(partAgent)
            ));
        }

        BigDecimal totalPartAgent = etat.getTotalChefs()
                .add(etat.getTotalSaisissants())
                .add(etat.getTotalMutuelleNationale())
                .add(etat.getTotalDG())
                .add(etat.getTotalDD());

        content.append(String.format("""
                <tr class="total-row">
                    <td colspan="2"><strong>TOTAUX</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                    <td class="currency"><strong>%s</strong></td>
                </tr>
            """,
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalChefs()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalSaisissants()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalMutuelleNationale()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalDG()),
                CurrencyFormatter.formatWithoutSymbol(etat.getTotalDD()),
                CurrencyFormatter.formatWithoutSymbol(totalPartAgent)
        ));

        content.append("""
                </tbody>
            </table>
            """);

        return assemblerHTML("État par Séries de Mandatements (Agents)",
                "ÉTAT PAR SÉRIES DE MANDATEMENTS",
                etat.getPeriodeLibelle(), etat.getDateGeneration(), content.toString());
    }

    // ==================== MÉTHODES UTILITAIRES POUR LES 8 IMPRIMÉS ====================

    /**
     * Retourne la liste des types d'imprimés disponibles
     */
    public static List<String> getTypesImprimesDisponibles() {
        return Arrays.asList(
                "ETAT_REPARTITION_AFFAIRES",           // Imprimé 1
                "ETAT_MANDATEMENT",                    // Imprimé 2
                "ETAT_CENTRE_REPARTITION",             // Imprimé 3
                "ETAT_INDICATEURS_REELS",              // Imprimé 4
                "ETAT_REPARTITION_PRODUIT",            // Imprimé 5
                "ETAT_CUMULE_AGENT",                   // Imprimé 6
                "TABLEAU_AMENDES_SERVICES",            // Imprimé 7
                "ETAT_MANDATEMENT_AGENTS"              // Imprimé 8
        );
    }

    /**
     * Génère un imprimé selon son type
     */
    public Object genererImprimeParType(String typeImprime, LocalDate dateDebut, LocalDate dateFin) {
        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                return genererEtatRepartitionAffaires(dateDebut, dateFin);
            case "ETAT_MANDATEMENT":
                return genererEtatMandatement(dateDebut, dateFin);
            case "ETAT_CENTRE_REPARTITION":
                return genererEtatCentreRepartition(dateDebut, dateFin);
            case "ETAT_INDICATEURS_REELS":
                return genererEtatRepartitionIndicateursReels(dateDebut, dateFin);
            case "ETAT_REPARTITION_PRODUIT":
                return genererEtatRepartitionProduit(dateDebut, dateFin);
            case "ETAT_CUMULE_AGENT":
                return genererEtatCumuleParAgent(dateDebut, dateFin);
            case "TABLEAU_AMENDES_SERVICES":
                return genererTableauAmendesParServices(dateDebut, dateFin);
            case "ETAT_MANDATEMENT_AGENTS":
                return genererEtatMandatementAgents(dateDebut, dateFin);
            default:
                throw new IllegalArgumentException("Type d'imprimé non reconnu: " + typeImprime);
        }
    }

    /**
     * Retourne le libellé d'un type d'imprimé
     */
    public static String getLibelleTypeImprime(String typeImprime) {
        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                return "État de Répartition des Affaires Contentieuses";
            case "ETAT_MANDATEMENT":
                return "État par Séries de Mandatement";
            case "ETAT_CENTRE_REPARTITION":
                return "État Cumulé par Centre de Répartition";
            case "ETAT_INDICATEURS_REELS":
                return "État de Répartition des Part des Indicateurs Réels";
            case "ETAT_REPARTITION_PRODUIT":
                return "État de Répartition du Produit des Affaires Contentieuses";
            case "ETAT_CUMULE_AGENT":
                return "État Cumulé par Agent";
            case "TABLEAU_AMENDES_SERVICES":
                return "Tableau des Amendes par Services";
            case "ETAT_MANDATEMENT_AGENTS":
                return "État par Séries de Mandatements (Agents)";
            default:
                return "Type d'imprimé inconnu";
        }
    }

    /**
     * Valide qu'un imprimé peut être généré
     */
    public boolean validerParametresImprime(String typeImprime, LocalDate dateDebut, LocalDate dateFin) {
        // Validation générale
        if (!validerParametresRapport(dateDebut, dateFin)) {
            return false;
        }

        // Validations spécifiques par type
        switch (typeImprime) {
            case "ETAT_CENTRE_REPARTITION":
                // Ce type nécessite des données spéciales
                logger.warn("L'imprimé {} nécessite une configuration avancée", typeImprime);
                return false; // Temporairement désactivé
            default:
                return true;
        }
    }

    /**
     * Calcule les statistiques générales d'un ensemble d'imprimés
     */
    public Map<String, Object> calculerStatistiquesImprimes(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Génération de quelques imprimés clés pour les statistiques
            TableauAmendesParServicesDTO tableau = genererTableauAmendesParServices(dateDebut, dateFin);
            EtatCumuleParAgentDTO etatAgents = genererEtatCumuleParAgent(dateDebut, dateFin);

            stats.put("nombreServices", tableau.getServices().size());
            stats.put("totalMontantServices", tableau.getTotalMontant());
            stats.put("nombreAgents", etatAgents.getAgents().size());
            stats.put("totalMontantAgents", etatAgents.getTotalGeneral());
            stats.put("affairesTraitees", tableau.getTotalAffaires());

        } catch (Exception e) {
            logger.warn("Erreur lors du calcul des statistiques d'imprimés", e);
            stats.put("erreur", "Impossible de calculer les statistiques");
        }

        return stats;
    }

    // ==================== MÉTHODES GÉNÉRIQUES ====================

    /**
     * Assemble le HTML final avec le template de base
     */
    private String assemblerHTML(String titre, String titreHeader, String periode,
                                 LocalDateTime dateGeneration, String contenu) {
        String header = genererEnteteStandard(titreHeader, periode, dateGeneration);

        return BASE_TEMPLATE
                .replace("{{TITRE}}", titre)
                .replace("{{HEADER}}", header)
                .replace("{{CONTENT}}", contenu)
                .replace("{{FOOTER}}", genererPiedPageStandard());
    }

    /**
     * Génère le HTML d'un imprimé selon son type
     */
    public String genererHTMLImprimeParType(String typeImprime, Object donneesImprime) {
        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                return genererHTML_EtatRepartitionAffaires((EtatRepartitionAffairesDTO) donneesImprime);
            case "ETAT_MANDATEMENT":
                return genererHTML_EtatMandatement((EtatMandatementDTO) donneesImprime);
            case "ETAT_CENTRE_REPARTITION":
                return genererHTML_EtatCentreRepartition((EtatCentreRepartitionDTO) donneesImprime);
            case "ETAT_INDICATEURS_REELS":
                return genererHTML_EtatRepartitionIndicateursReels((EtatRepartitionIndicateursReelsDTO) donneesImprime);
            case "ETAT_REPARTITION_PRODUIT":
                return genererHTML_EtatRepartitionProduit((EtatRepartitionProduitDTO) donneesImprime);
            case "ETAT_CUMULE_AGENT":
                return genererHTML_EtatCumuleParAgent((EtatCumuleParAgentDTO) donneesImprime);
            case "TABLEAU_AMENDES_SERVICES":
                return genererHTML_TableauAmendesParServices((TableauAmendesParServicesDTO) donneesImprime);
            case "ETAT_MANDATEMENT_AGENTS":
                return genererHTML_EtatMandatementAgents((EtatMandatementDTO) donneesImprime);
            default:
                throw new IllegalArgumentException("Type d'imprimé non reconnu: " + typeImprime);
        }
    }

    // ==================== MÉTHODES D'IMPRESSION ====================

    /**
     * Imprime un imprimé selon son type
     */
    public CompletableFuture<Boolean> imprimerImprimeParType(String typeImprime, Object donneesImprime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Début de l'impression de l'imprimé: {}", typeImprime);

                String htmlContent = genererHTMLImprimeParType(typeImprime, donneesImprime);

                WebView webView = new WebView();
                webView.getEngine().loadContent(htmlContent);

                webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        javafx.application.Platform.runLater(() -> {
                            try {
                                executerImpression(webView);
                            } catch (Exception e) {
                                logger.error("Erreur lors de l'exécution de l'impression", e);
                            }
                        });
                    }
                });

                return true;

            } catch (Exception e) {
                logger.error("Erreur lors de l'impression de l'imprimé {}", typeImprime, e);
                return false;
            }
        });
    }

    /**
     * Génère l'aperçu d'un imprimé
     */
    public WebView genererApercuImprimeParType(String typeImprime, Object donneesImprime) {
        try {
            String htmlContent = genererHTMLImprimeParType(typeImprime, donneesImprime);

            WebView webView = new WebView();
            webView.getEngine().loadContent(htmlContent);

            logger.info("Aperçu généré pour l'imprimé: {}", typeImprime);
            return webView;

        } catch (Exception e) {
            logger.error("Erreur lors de la génération de l'aperçu de l'imprimé {}", typeImprime, e);
            throw new RuntimeException("Impossible de générer l'aperçu: " + e.getMessage(), e);
        }
    }

    /**
     * Exécute l'impression avec les paramètres par défaut
     */
    private void executerImpression(WebView webView) {
        Printer defaultPrinter = Printer.getDefaultPrinter();
        if (defaultPrinter == null) {
            logger.warn("Aucune imprimante par défaut trouvée");
            return;
        }

        PrinterJob printerJob = PrinterJob.createPrinterJob();
        if (printerJob == null) {
            logger.error("Impossible de créer le job d'impression");
            return;
        }

        PageLayout pageLayout = defaultPrinter.createPageLayout(
                Paper.A4,
                PageOrientation.PORTRAIT,
                Printer.MarginType.DEFAULT
        );

        printerJob.getJobSettings().setPageLayout(pageLayout);

        boolean proceed = printerJob.showPrintDialog(webView.getScene().getWindow());

        if (proceed) {
            boolean success = printerJob.printPage(webView);
            if (success) {
                printerJob.endJob();
                logger.info("Impression terminée avec succès");
            } else {
                logger.error("Échec de l'impression");
            }
        } else {
            logger.info("Impression annulée par l'utilisateur");
        }
    }

    /**
     * Crée une tâche d'impression pour un imprimé spécifique
     */
    public Task<Boolean> creerTacheImpressionImprime(String typeImprime, Object donneesImprime) {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                updateMessage("Génération de l'imprimé " + RapportService.getLibelleTypeImprime(typeImprime) + "...");
                updateProgress(0, 100);

                String htmlContent = genererHTMLImprimeParType(typeImprime, donneesImprime);
                updateProgress(50, 100);

                updateMessage("Envoi vers l'imprimante...");

                WebView webView = new WebView();
                webView.getEngine().loadContent(htmlContent);

                final boolean[] success = {false};

                webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        javafx.application.Platform.runLater(() -> {
                            try {
                                executerImpression(webView);
                                success[0] = true;
                                updateProgress(100, 100);
                                updateMessage("Impression terminée");
                            } catch (Exception e) {
                                logger.error("Erreur dans la tâche d'impression de l'imprimé", e);
                                updateMessage("Erreur d'impression: " + e.getMessage());
                            }
                        });
                    }
                });

                Thread.sleep(3000);
                return success[0];
            }
        };
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Vérifie la disponibilité des imprimantes
     */
    public boolean isImprimanteDisponible() {
        return Printer.getDefaultPrinter() != null;
    }

    /**
     * Obtient la liste des imprimantes disponibles
     */
    public java.util.List<String> getImprimantesDisponibles() {
        return Printer.getAllPrinters().stream()
                .map(Printer::getName)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Valide qu'un imprimé peut être imprimé
     */
    public boolean validerImprimePourImpression(String typeImprime, Object donneesImprime) {
        if (donneesImprime == null) {
            logger.warn("Données null pour l'imprimé {}", typeImprime);
            return false;
        }

        switch (typeImprime) {
            case "ETAT_REPARTITION_AFFAIRES":
                EtatRepartitionAffairesDTO etatAffaires = (EtatRepartitionAffairesDTO) donneesImprime;
                return etatAffaires.getAffaires() != null && !etatAffaires.getAffaires().isEmpty();

            case "TABLEAU_AMENDES_SERVICES":
                TableauAmendesParServicesDTO tableau = (TableauAmendesParServicesDTO) donneesImprime;
                return tableau.getServices() != null && !tableau.getServices().isEmpty();

            case "ETAT_CUMULE_AGENT":
                EtatCumuleParAgentDTO etatAgents = (EtatCumuleParAgentDTO) donneesImprime;
                return etatAgents.getAgents() != null && !etatAgents.getAgents().isEmpty();

            default:
                return true;
        }
    }

    /**
     * Génère les paramètres d'impression recommandés
     */
    public PageLayout getParametresImpressionRecommandes() {
        Printer defaultPrinter = Printer.getDefaultPrinter();
        if (defaultPrinter == null) {
            return null;
        }

        return defaultPrinter.createPageLayout(
                Paper.A4,
                PageOrientation.PORTRAIT,
                Printer.MarginType.DEFAULT
        );
    }
}