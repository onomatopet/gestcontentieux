package com.regulation.contentieux.service;

import com.regulation.contentieux.service.RapportService.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service d'export pour les rapports et documents
 * Version temporaire sans dépendance iTextPDF
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

    /**
     * Exporte un contenu HTML en fichier PDF
     * Version temporaire : génère un HTML au lieu de PDF
     */
    public boolean exportToPdf(String htmlContent, String outputPath) {
        try {
            // Pour l'instant, on génère un HTML au lieu d'un PDF
            String htmlPath = outputPath.replace(".pdf", ".html");

            // Ajouter un wrapper HTML complet si nécessaire
            if (!htmlContent.contains("<html>")) {
                htmlContent = wrapInHtmlDocument(htmlContent);
            }

            try (FileWriter writer = new FileWriter(htmlPath)) {
                writer.write(htmlContent);
                logger.info("Export HTML temporaire créé: {}", htmlPath);
                return true;
            }
        } catch (IOException e) {
            logger.error("Erreur lors de l'export", e);
            return false;
        }
    }

    /**
     * Enveloppe le contenu dans un document HTML complet
     */
    private String wrapInHtmlDocument(String content) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Rapport</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; }
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

            // Période
            Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue("Période: " + rapport.getPeriodeLibelle());

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
                row.createCell(1).setCellValue(affaire.getContrevenant());

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

            createMontantCell(totalRow, 2, rapport.getTotalMontant(), totalStyle);
            createMontantCell(totalRow, 3, rapport.getTotalEncaisse(), totalStyle);
            createMontantCell(totalRow, 4, rapport.getTotalPartEtat(), totalStyle);
            createMontantCell(totalRow, 5, rapport.getTotalPartCollectivite(), totalStyle);

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
            titleCell.setCellValue("Situation Générale des Affaires Contentieuses");
            titleCell.setCellStyle(headerStyle);

            // Période
            Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue("Période: " + situation.getPeriodeLibelle());

            rowNum++; // Ligne vide

            // Statistiques globales
            sheet.createRow(rowNum++).createCell(0).setCellValue("STATISTIQUES GLOBALES");

            Row statsRow1 = sheet.createRow(rowNum++);
            statsRow1.createCell(0).setCellValue("Total des affaires:");
            statsRow1.createCell(1).setCellValue(situation.getTotalAffaires());

            Row statsRow2 = sheet.createRow(rowNum++);
            statsRow2.createCell(0).setCellValue("Affaires ouvertes:");
            statsRow2.createCell(1).setCellValue(situation.getAffairesOuvertes());

            Row statsRow3 = sheet.createRow(rowNum++);
            statsRow3.createCell(0).setCellValue("Affaires soldées:");
            statsRow3.createCell(1).setCellValue(situation.getAffairesSoldees());

            rowNum++; // Ligne vide

            // Montants
            sheet.createRow(rowNum++).createCell(0).setCellValue("MONTANTS");

            Row montantRow1 = sheet.createRow(rowNum++);
            montantRow1.createCell(0).setCellValue("Montant total des amendes:");
            createMontantCell(montantRow1, 1, situation.getMontantTotalAmendes(), montantStyle);

            Row montantRow2 = sheet.createRow(rowNum++);
            montantRow2.createCell(0).setCellValue("Montant encaissé:");
            createMontantCell(montantRow2, 1, situation.getMontantTotalEncaisse(), montantStyle);

            Row montantRow3 = sheet.createRow(rowNum++);
            montantRow3.createCell(0).setCellValue("Montant restant dû:");
            createMontantCell(montantRow3, 1, situation.getMontantRestantDu(), montantStyle);

            // Auto-dimensionner les colonnes
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

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
     * Exporte le tableau des amendes en Excel
     */
    public boolean exportTableauAmendesToExcel(TableauAmendesParServicesDTO tableau, String outputPath) {
        // TODO: Implémenter l'export du tableau des amendes
        logger.warn("Export du tableau des amendes non encore implémenté");
        return false;
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
}