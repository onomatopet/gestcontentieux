package com.regulation.contentieux.service;

import com.regulation.contentieux.service.RapportService.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service d'export des rapports en différents formats
 * Gère l'export Excel et PDF des rapports de rétrocession
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

    /**
     * Convertit du HTML en contenu avec mise en page
     */
    private String wrapHtmlContent(String content) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th, td { border: 1px solid black; padding: 8px; text-align: left; }
                    th { background-color: #f0f0f0; font-weight: bold; }
                    .text-right { text-align: right; }
                    .text-center { text-align: center; }
                    .font-weight-bold { font-weight: bold; }
                </style>
            </head>
            <body>
            """ + content + """
            </body>
            </html>
            """;
    }

    /**
     * Exporte le rapport de répartition en Excel
     */
    public boolean exportRepartitionToExcel(RapportRepartitionDTO rapport, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Répartition");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // Titre
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("État de Répartition et de Rétrocession");
            titleCell.setCellStyle(headerStyle);

            // Période - Utiliser directement les dates si getPeriodeLibelle() n'existe pas
            Row periodRow = sheet.createRow(rowNum++);
            String periode = DateFormatter.format(rapport.getDateDebut()) + " au " + DateFormatter.format(rapport.getDateFin());
            periodRow.createCell(0).setCellValue("Période: " + periode);

            rowNum++; // Ligne vide

            // En-têtes
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"N° Affaire", "Contrevenant", "Montant Total",
                    "Montant Encaissé", "Part État", "Part Collectivité"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données
            for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(affaire.getNumeroAffaire());
                row.createCell(1).setCellValue(affaire.getContrevenant() != null ? affaire.getContrevenant() : affaire.getContrevenantNom());

                createMontantCell(row, 2, affaire.getMontantTotal(), montantStyle);
                createMontantCell(row, 3, affaire.getMontantEncaisse(), montantStyle);
                createMontantCell(row, 4, affaire.getPartEtat(), montantStyle);
                createMontantCell(row, 5, affaire.getPartCollectivite(), montantStyle);
            }

            // Totaux
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(1).setCellValue("TOTAUX");
            totalRow.getCell(1).setCellStyle(totalStyle);

            // Utiliser les méthodes disponibles ou calculer les totaux
            BigDecimal totalMontant = rapport.getTotalEncaisse(); // Utiliser getTotalEncaisse() qui existe
            BigDecimal totalEncaisse = rapport.getTotalEncaisse();
            BigDecimal totalPartEtat = rapport.getTotalEtat(); // Utiliser getTotalEtat() qui existe
            BigDecimal totalPartCollectivite = rapport.getTotalCollectivite(); // Utiliser getTotalCollectivite() qui existe

            createMontantCell(totalRow, 2, totalMontant, totalStyle);
            createMontantCell(totalRow, 3, totalEncaisse, totalStyle);
            createMontantCell(totalRow, 4, totalPartEtat, totalStyle);
            createMontantCell(totalRow, 5, totalPartCollectivite, totalStyle);

            // Auto-dimensionner les colonnes
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel exporté avec succès: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel", e);
            return false;
        }
    }

    /**
     * Exporte la situation générale en Excel
     */
    public boolean exportSituationToExcel(SituationGeneraleDTO situation, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Situation Générale");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // Titre
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Situation Générale des Affaires");
            titleCell.setCellStyle(headerStyle);

            // Période
            Row periodRow = sheet.createRow(rowNum++);
            String periode = situation.getPeriodeLibelle() != null ? situation.getPeriodeLibelle() :
                    DateFormatter.format(situation.getDateDebut()) + " au " + DateFormatter.format(situation.getDateFin());
            periodRow.createCell(0).setCellValue("Période: " + periode);

            rowNum++; // Ligne vide

            // Statistiques
            Row statRow1 = sheet.createRow(rowNum++);
            statRow1.createCell(0).setCellValue("Total des affaires:");
            statRow1.createCell(1).setCellValue(situation.getTotalAffaires());

            Row statRow2 = sheet.createRow(rowNum++);
            statRow2.createCell(0).setCellValue("Affaires ouvertes:");
            statRow2.createCell(1).setCellValue(situation.getAffairesOuvertes());

            Row statRow3 = sheet.createRow(rowNum++);
            statRow3.createCell(0).setCellValue("Affaires en cours:");
            statRow3.createCell(1).setCellValue(situation.getAffairesEnCours());

            Row statRow4 = sheet.createRow(rowNum++);
            statRow4.createCell(0).setCellValue("Affaires soldées:");
            statRow4.createCell(1).setCellValue(situation.getAffairesSoldees());

            rowNum++; // Ligne vide

            // Montants
            Row montantRow1 = sheet.createRow(rowNum++);
            montantRow1.createCell(0).setCellValue("Total des amendes:");
            createMontantCell(montantRow1, 1, situation.getTotalAmendes(), montantStyle);

            Row montantRow2 = sheet.createRow(rowNum++);
            montantRow2.createCell(0).setCellValue("Total encaissé:");
            createMontantCell(montantRow2, 1, situation.getTotalEncaisse(), montantStyle);

            Row montantRow3 = sheet.createRow(rowNum++);
            montantRow3.createCell(0).setCellValue("Total restant:");
            createMontantCell(montantRow3, 1, situation.getTotalRestant(), montantStyle);

            // Taux d'encaissement
            if (situation.getTauxEncaissement() != null) {
                Row tauxRow = sheet.createRow(rowNum++);
                tauxRow.createCell(0).setCellValue("Taux d'encaissement:");
                tauxRow.createCell(1).setCellValue(String.format("%.2f%%", situation.getTauxEncaissement()));
            }

            // Auto-dimensionner
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel de situation générale exporté: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel de la situation générale", e);
            return false;
        }
    }

    /**
     * Exporte l'état cumulé par centre de répartition en Excel
     */
    public boolean exportCentreRepartitionToExcel(RapportService.CentreRepartitionDTO rapport, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("État Centre Répartition");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // Titre
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ÉTAT CUMULÉ PAR CENTRE DE RÉPARTITION");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            // Période
            Row periodeRow = sheet.createRow(rowNum++);
            Cell periodeCell = periodeRow.createCell(0);
            periodeCell.setCellValue("Période: " + rapport.getPeriodeDebut() + " au " + rapport.getPeriodeFin());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

            rowNum++; // Ligne vide

            // En-têtes principales
            Row headerRow1 = sheet.createRow(rowNum++);
            headerRow1.createCell(0).setCellValue("Centre de répartition");
            headerRow1.createCell(1).setCellValue("Part revenant au centre au titre de");
            headerRow1.createCell(3).setCellValue("Part centre");

            // Fusionner les cellules d'en-tête
            sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum, 0, 0)); // Centre
            sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 1, 2)); // Part revenant
            sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum, 3, 3)); // Part centre

            // Sous-en-têtes
            Row headerRow2 = sheet.createRow(rowNum++);
            headerRow2.createCell(1).setCellValue("Répartition de base");
            headerRow2.createCell(2).setCellValue("Répartition part indic.");

            // Appliquer le style d'en-tête
            for (Cell cell : headerRow1) {
                if (cell != null) cell.setCellStyle(headerStyle);
            }
            for (Cell cell : headerRow2) {
                if (cell != null) cell.setCellStyle(headerStyle);
            }

            // Données
            for (RapportService.CentreStatsDTO centre : rapport.getCentres()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(centre.getNomCentre());
                createMontantCell(row, 1, centre.getRepartitionBase(), montantStyle);
                createMontantCell(row, 2, centre.getRepartitionIndicateur(), montantStyle);
                createMontantCell(row, 3, centre.getPartTotalCentre(), montantStyle);
            }

            // Totaux
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("TOTAL");
            totalRow.getCell(0).setCellStyle(totalStyle);

            createMontantCell(totalRow, 1, rapport.getTotalRepartitionBase(), totalStyle);
            createMontantCell(totalRow, 2, rapport.getTotalRepartitionIndicateur(), totalStyle);
            createMontantCell(totalRow, 3, rapport.getTotalPartCentre(), totalStyle);

            // Auto-dimensionner
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel centre répartition exporté: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur export Excel centre répartition", e);
            return false;
        }
    }

    /**
    * Exporte l'état des indicateurs réels en Excel
    */
    public boolean exportIndicateursReelsToExcel(RapportService.IndicateursReelsDTO rapport, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Indicateurs Réels");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sectionStyle = createSectionStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // Titre et période
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ÉTAT DE RÉPARTITION DES PARTS DES INDICATEURS RÉELS");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            Row periodeRow = sheet.createRow(rowNum++);
            periodeRow.createCell(0).setCellValue("Période: " + rapport.getPeriodeDebut() + " au " + rapport.getPeriodeFin());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            rowNum++; // Ligne vide

            // En-têtes du tableau
            String[] headers = {
                    "N° encaissement et Date", "N° Affaire et Date", "Noms des contrevenants",
                    "Contraventions", "Montant encaissement", "Part indicateur", "Observations"
            };

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données par service
            for (RapportService.ServiceIndicateursDTO serviceData : rapport.getServicesData()) {
                // En-tête de service
                Row serviceRow = sheet.createRow(rowNum++);
                Cell serviceCell = serviceRow.createCell(0);
                serviceCell.setCellValue("Service : " + serviceData.getNomService());
                serviceCell.setCellStyle(sectionStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

                // Données du service
                for (RapportService.IndicateurItemDTO item : serviceData.getItems()) {
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(item.getNumeroEncaissement() + "\n" + item.getDateEncaissement());
                    row.createCell(1).setCellValue(item.getNumeroAffaire() + "\n" + item.getDateAffaire());
                    row.createCell(2).setCellValue(item.getNomContrevenant());
                    row.createCell(3).setCellValue(item.getContraventions());
                    createMontantCell(row, 4, item.getMontantEncaissement(), montantStyle);
                    createMontantCell(row, 5, item.getPartIndicateur(), montantStyle);
                    row.createCell(6).setCellValue(item.getObservations());
                }

                // Sous-total service
                Row subtotalRow = sheet.createRow(rowNum++);
                subtotalRow.createCell(0).setCellValue("Sous-total Service");
                createMontantCell(subtotalRow, 4, serviceData.getTotalMontant(), totalStyle);
                createMontantCell(subtotalRow, 5, serviceData.getTotalPartIndicateur(), totalStyle);
            }

            // Totaux généraux
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("TOTAL GÉNÉRAL");
            totalRow.getCell(0).setCellStyle(totalStyle);
            createMontantCell(totalRow, 4, rapport.getTotalMontant(), totalStyle);
            createMontantCell(totalRow, 5, rapport.getTotalPartIndicateur(), totalStyle);

            // Auto-dimensionner
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel indicateurs réels exporté: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur export Excel indicateurs réels", e);
            return false;
        }
    }

    public boolean exportRepartitionProduitToExcel(RapportService.RepartitionProduitDTO rapport, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Répartition Produit");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // Titre et période
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ÉTAT DE RÉPARTITION DU PRODUIT DES AFFAIRES CONTENTIEUSES");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

            Row periodeRow = sheet.createRow(rowNum++);
            periodeRow.createCell(0).setCellValue("Période: " + rapport.getPeriodeDebut() + " au " + rapport.getPeriodeFin());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 10));

            rowNum++; // Ligne vide

            // En-têtes (11 colonnes selon le gabarit)
            String[] headers = {
                    "N° encaissement et Date", "N° Affaire et Date", "Noms des contrevenants",
                    "Noms des contraventions", "Produit disponible", "Part indicateur",
                    "Part Direction contentieux", "Part indicateur", "FLCF", "Montant Trésor",
                    "Montant Global ayants droits"
            };

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données
            for (RapportService.ProduitItemDTO item : rapport.getItems()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(item.getNumeroEncaissement() + "\n" + item.getDateEncaissement());
                row.createCell(1).setCellValue(item.getNumeroAffaire() + "\n" + item.getDateAffaire());
                row.createCell(2).setCellValue(item.getNomContrevenant());
                row.createCell(3).setCellValue(item.getContraventions());
                createMontantCell(row, 4, item.getProduitDisponible(), montantStyle);
                createMontantCell(row, 5, item.getPartIndicateur(), montantStyle);
                createMontantCell(row, 6, item.getPartDirection(), montantStyle);
                createMontantCell(row, 7, item.getPartIndicateur2(), montantStyle);
                createMontantCell(row, 8, item.getFLCF(), montantStyle);
                createMontantCell(row, 9, item.getMontantTresor(), montantStyle);
                createMontantCell(row, 10, item.getMontantAyantsDroits(), montantStyle);
            }

            // Totaux
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("TOTAUX");
            totalRow.getCell(0).setCellStyle(totalStyle);

            createMontantCell(totalRow, 4, rapport.getTotalProduitDisponible(), totalStyle);
            createMontantCell(totalRow, 5, rapport.getTotalPartIndicateur(), totalStyle);
            createMontantCell(totalRow, 6, rapport.getTotalPartDirection(), totalStyle);
            createMontantCell(totalRow, 7, rapport.getTotalPartIndicateur2(), totalStyle);
            createMontantCell(totalRow, 8, rapport.getTotalFLCF(), totalStyle);
            createMontantCell(totalRow, 9, rapport.getTotalMontantTresor(), totalStyle);
            createMontantCell(totalRow, 10, rapport.getTotalMontantAyantsDroits(), totalStyle);

            // Auto-dimensionner
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel répartition produit exporté: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur export Excel répartition produit", e);
            return false;
        }
    }

    /**
     * Exporte l'état cumulé par agent en Excel
     */
    public boolean exportEtatCumuleAgentToExcel(RapportService.EtatCumuleAgentDTO rapport, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("État Cumulé Agents");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // Titre et période
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ÉTAT CUMULÉ PAR AGENT");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            Row periodeRow = sheet.createRow(rowNum++);
            periodeRow.createCell(0).setCellValue("Période: " + rapport.getPeriodeDebut() + " au " + rapport.getPeriodeFin());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

            rowNum++; // Ligne vide

            // En-têtes avec fusion selon le gabarit
            Row headerRow1 = sheet.createRow(rowNum++);
            headerRow1.createCell(0).setCellValue("Nom de l'agent");
            headerRow1.createCell(1).setCellValue("Part revenant à l'agent après répartition en tant que");
            headerRow1.createCell(5).setCellValue("Part agent");

            // Fusionner les cellules d'en-tête
            sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum, 0, 0)); // Nom agent
            sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 1, 4)); // Part revenant
            sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum, 5, 5)); // Part agent

            // Sous-en-têtes
            Row headerRow2 = sheet.createRow(rowNum++);
            headerRow2.createCell(1).setCellValue("Chef");
            headerRow2.createCell(2).setCellValue("Saisissant");
            headerRow2.createCell(3).setCellValue("DG");
            headerRow2.createCell(4).setCellValue("DD");

            // Appliquer les styles
            for (Cell cell : headerRow1) {
                if (cell != null) cell.setCellStyle(headerStyle);
            }
            for (Cell cell : headerRow2) {
                if (cell != null) cell.setCellStyle(headerStyle);
            }

            // Données
            for (RapportService.AgentCumuleDTO agent : rapport.getAgents()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(agent.getNomAgent());
                createMontantCell(row, 1, agent.getPartChef(), montantStyle);
                createMontantCell(row, 2, agent.getPartSaisissant(), montantStyle);
                createMontantCell(row, 3, agent.getPartDG(), montantStyle);
                createMontantCell(row, 4, agent.getPartDD(), montantStyle);
                createMontantCell(row, 5, agent.getPartTotale(), montantStyle);
            }

            // Totaux
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("TOTAL");
            totalRow.getCell(0).setCellStyle(totalStyle);

            createMontantCell(totalRow, 1, rapport.getTotalPartChef(), totalStyle);
            createMontantCell(totalRow, 2, rapport.getTotalPartSaisissant(), totalStyle);
            createMontantCell(totalRow, 3, rapport.getTotalPartDG(), totalStyle);
            createMontantCell(totalRow, 4, rapport.getTotalPartDD(), totalStyle);
            createMontantCell(totalRow, 5, rapport.getTotalPartAgent(), totalStyle);

            // Auto-dimensionner
            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel état cumulé agents exporté: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur export Excel état cumulé agents", e);
            return false;
        }
    }


    /**
     * Exporte le tableau des amendes en Excel
     */
    public boolean exportTableauAmendesToExcel(TableauAmendesParServicesDTO tableau, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Amendes par Service");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // En-tête
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("TABLEAU DES AMENDES PAR SERVICE");
            titleCell.setCellStyle(headerStyle);

            // Période
            Row periodRow = sheet.createRow(rowNum++);
            String periode = tableau.getPeriodeLibelle() != null ? tableau.getPeriodeLibelle() :
                    DateFormatter.format(tableau.getDateDebut()) + " au " + DateFormatter.format(tableau.getDateFin());
            periodRow.createCell(0).setCellValue("Période: " + periode);

            rowNum++; // Ligne vide

            // En-têtes de colonnes
            Row headerRow = sheet.createRow(rowNum++);
            createHeaderCell(headerRow, 0, "Service", headerStyle);
            createHeaderCell(headerRow, 1, "Nombre d'affaires", headerStyle);
            createHeaderCell(headerRow, 2, "Montant total", headerStyle);
            createHeaderCell(headerRow, 3, "Observations", headerStyle);

            // Données
            for (ServiceAmendeDTO service : tableau.getServices()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(service.getNomService());
                row.createCell(1).setCellValue(service.getNombreAffaires());
                createMontantCell(row, 2, service.getMontantTotal(), montantStyle);
                row.createCell(3).setCellValue(service.getObservations() != null ?
                        service.getObservations() : "");
            }

            // Total
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("TOTAL");
            totalRow.getCell(0).setCellStyle(totalStyle);
            totalRow.createCell(1).setCellValue(tableau.getTotalAffaires());
            totalRow.getCell(1).setCellStyle(totalStyle);
            createMontantCell(totalRow, 2, tableau.getTotalMontant(), totalStyle);

            // Auto-dimensionner les colonnes
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            logger.info("Excel du tableau des amendes exporté: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel du tableau des amendes", e);
            return false;
        }
    }

    /**
     * Export générique en Excel
     */
    public boolean exportGenericToExcel(Object data, String outputPath) {
        // TODO: Implémenter l'export générique
        logger.warn("Export générique non encore implémenté");
        return false;
    }

    // Méthodes utilitaires pour les styles Excel

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createMontantStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = createMontantStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void createMontantCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0);
        }
        cell.setCellStyle(style);
    }

    private void createHeaderCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}