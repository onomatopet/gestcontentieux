package com.regulation.contentieux.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service d'export des données vers différents formats
 * Gère l'export PDF et Excel des rapports et données
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

    /**
     * Exporte du HTML en PDF
     */
    public boolean exportToPdf(String htmlContent, String outputPath) {
        try {
            // Ajouter les styles CSS pour le PDF
            String styledHtml = wrapHtmlWithStyles(htmlContent);

            // Convertir en PDF
            HtmlConverter.convertToPdf(
                    new ByteArrayInputStream(styledHtml.getBytes(StandardCharsets.UTF_8)),
                    new FileOutputStream(outputPath)
            );

            logger.info("PDF exporté avec succès: {}", outputPath);
            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export PDF", e);
            return false;
        }
    }

    /**
     * Enveloppe le HTML avec les styles nécessaires
     */
    private String wrapHtmlWithStyles(String htmlContent) {
        if (htmlContent.contains("<html>")) {
            return htmlContent; // Déjà complet
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    @page { size: A4; margin: 2cm; }
                    body { 
                        font-family: Arial, sans-serif; 
                        font-size: 10pt;
                        line-height: 1.5;
                    }
                    table { 
                        width: 100%; 
                        border-collapse: collapse; 
                        margin: 10px 0;
                    }
                    th, td { 
                        border: 1px solid #ddd; 
                        padding: 8px; 
                        text-align: left;
                    }
                    th { 
                        background-color: #f5f5f5; 
                        font-weight: bold;
                    }
                    .header { 
                        text-align: center; 
                        margin-bottom: 30px;
                    }
                    .footer { 
                        margin-top: 30px; 
                        text-align: center;
                        font-size: 9pt;
                    }
                    .montant { text-align: right; }
                    .total { font-weight: bold; }
                </style>
            </head>
            <body>
            """ + htmlContent + """
            </body>
            </html>
            """;
    }

    /**
     * Exporte le rapport de répartition en Excel
     */
    public boolean exportRepartitionToExcel(RapportService.RapportRepartitionDTO rapport, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Répartition Rétrocession");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // En-tête du rapport
            rowNum = createReportHeader(sheet, "ÉTAT DE RÉPARTITION DES RÉTROCESSIONS",
                    rapport.getDateDebut(), rapport.getDateFin(), rowNum);

            // En-têtes de colonnes
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"N° Affaire", "Contrevenant", "Montant Total",
                    "Montant Encaissé", "Part État (60%)", "Part Collectivité (40%)"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Données
            for (RapportService.AffaireRepartitionDTO affaire : rapport.getAffaires()) {
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
    public boolean exportSituationToExcel(RapportService.SituationGeneraleDTO situation, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Situation Générale");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // En-tête
            rowNum = createReportHeader(sheet, "SITUATION GÉNÉRALE DES AFFAIRES",
                    situation.getDateDebut(), situation.getDateFin(), rowNum);

            // Statistiques globales
            rowNum = createSection(sheet, "STATISTIQUES GLOBALES", rowNum);

            createStatRow(sheet, rowNum++, "Nombre total d'affaires",
                    String.valueOf(situation.getNombreAffairesTotal()));
            createStatRow(sheet, rowNum++, "Affaires ouvertes",
                    String.valueOf(situation.getNombreAffairesOuvertes()));
            createStatRow(sheet, rowNum++, "Affaires clôturées",
                    String.valueOf(situation.getNombreAffairesCloses()));
            createStatRow(sheet, rowNum++, "Montant total des amendes",
                    CurrencyFormatter.format(situation.getMontantTotalAmendes()));
            createStatRow(sheet, rowNum++, "Montant total encaissé",
                    CurrencyFormatter.format(situation.getMontantTotalEncaisse()));
            createStatRow(sheet, rowNum++, "Taux de recouvrement",
                    String.format("%.2f%%", situation.getTauxRecouvrement()));

            rowNum += 2; // Espace

            // Répartition par statut
            rowNum = createSection(sheet, "RÉPARTITION PAR STATUT", rowNum);

            Row headerRow = sheet.createRow(rowNum++);
            createHeaderCell(headerRow, 0, "Statut", headerStyle);
            createHeaderCell(headerRow, 1, "Nombre", headerStyle);
            createHeaderCell(headerRow, 2, "Pourcentage", headerStyle);

            for (var entry : situation.getRepartitionStatut().entrySet()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());

                double percentage = (entry.getValue() * 100.0) / situation.getNombreAffairesTotal();
                row.createCell(2).setCellValue(String.format("%.2f%%", percentage));
            }

            // Auto-dimensionner
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel", e);
            return false;
        }
    }

    /**
     * Exporte le tableau des amendes par service
     */
    public boolean exportTableauAmendesToExcel(RapportService.TableauAmendesParServicesDTO tableau,
                                               String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Amendes par Service");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            int rowNum = 0;

            // En-tête
            rowNum = createReportHeader(sheet, "TABLEAU DES AMENDES PAR SERVICE",
                    tableau.getDateDebut(), tableau.getDateFin(), rowNum);

            // En-têtes de colonnes
            Row headerRow = sheet.createRow(rowNum++);
            createHeaderCell(headerRow, 0, "Service", headerStyle);
            createHeaderCell(headerRow, 1, "Nombre d'affaires", headerStyle);
            createHeaderCell(headerRow, 2, "Montant total", headerStyle);
            createHeaderCell(headerRow, 3, "Observations", headerStyle);

            // Données
            for (RapportService.ServiceAmendeDTO service : tableau.getServices()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(service.getNomService());
                row.createCell(1).setCellValue(service.getNombreAffaires());
                createMontantCell(row, 2, service.getMontantTotal(), montantStyle);
                row.createCell(3).setCellValue(service.getObservations() != null ? service.getObservations() : "");
            }

            // Totaux
            rowNum++; // Ligne vide
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("TOTAL GÉNÉRAL");
            totalRow.getCell(0).setCellStyle(totalStyle);
            totalRow.createCell(1).setCellValue(tableau.getTotalAffaires());
            totalRow.getCell(1).setCellStyle(totalStyle);
            createMontantCell(totalRow, 2, tableau.getTotalMontant(), totalStyle);

            // Auto-dimensionner
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel", e);
            return false;
        }
    }

    /**
     * Export générique pour les autres types de données
     */
    public boolean exportGenericToExcel(Object data, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {

            if (data instanceof List) {
                exportListToExcel(workbook, (List<?>) data);
            } else {
                // Export simple d'un objet
                Sheet sheet = workbook.createSheet("Export");
                createObjectSheet(sheet, data);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel", e);
            return false;
        }
    }

    /**
     * Exporte une liste d'affaires en Excel
     */
    public boolean exportAffairesToExcel(List<Affaire> affaires, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Affaires");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // En-tête
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {
                    "N° Affaire", "Date création", "Date constatation", "Contrevenant",
                    "Lieu", "Agent", "Montant total", "Montant encaissé", "Solde", "Statut"
            };

            for (int i = 0; i < headers.length; i++) {
                createHeaderCell(headerRow, i, headers[i], headerStyle);
            }

            // Données
            for (Affaire affaire : affaires) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(affaire.getNumeroAffaire());
                createDateCell(row, 1, affaire.getDateCreation(), dateStyle);
                createDateCell(row, 2, affaire.getDateConstatation(), dateStyle);
                row.createCell(3).setCellValue(affaire.getContrevenantDisplayName());
                row.createCell(4).setCellValue(affaire.getLieuConstatation());
                row.createCell(5).setCellValue(
                        affaire.getAgentVerbalisateur() != null ?
                                affaire.getAgentVerbalisateur().getDisplayName() : ""
                );
                createMontantCell(row, 6, affaire.getMontantTotal(), montantStyle);
                createMontantCell(row, 7, affaire.getMontantEncaisse(), montantStyle);
                createMontantCell(row, 8, affaire.getSoldeRestant(), montantStyle);
                row.createCell(9).setCellValue(affaire.getStatut().getLibelle());
            }

            // Auto-dimensionner
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel des affaires", e);
            return false;
        }
    }

    /**
     * Exporte une liste d'encaissements en Excel
     */
    public boolean exportEncaissementsToExcel(List<Encaissement> encaissements, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Encaissements");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle montantStyle = createMontantStyle(workbook);

            int rowNum = 0;

            // En-tête
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {
                    "Référence", "Date", "N° Affaire", "Contrevenant",
                    "Mode règlement", "N° Pièce", "Banque", "Montant", "Statut"
            };

            for (int i = 0; i < headers.length; i++) {
                createHeaderCell(headerRow, i, headers[i], headerStyle);
            }

            // Données
            for (Encaissement enc : encaissements) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(enc.getReference());
                createDateCell(row, 1, enc.getDateEncaissement(), dateStyle);
                row.createCell(2).setCellValue(
                        enc.getAffaire() != null ? enc.getAffaire().getNumeroAffaire() : ""
                );
                row.createCell(3).setCellValue(
                        enc.getAffaire() != null ? enc.getAffaire().getContrevenantDisplayName() : ""
                );
                row.createCell(4).setCellValue(enc.getModeReglement().getLibelle());
                row.createCell(5).setCellValue(enc.getNumeroPiece() != null ? enc.getNumeroPiece() : "");
                row.createCell(6).setCellValue(enc.getBanque() != null ? enc.getBanque() : "");
                createMontantCell(row, 7, enc.getMontantEncaisse(), montantStyle);
                row.createCell(8).setCellValue(enc.getStatut().getLibelle());
            }

            // Auto-dimensionner
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Écrire le fichier
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
            }

            return true;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel des encaissements", e);
            return false;
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Crée l'en-tête du rapport
     */
    private int createReportHeader(Sheet sheet, String title, LocalDate dateDebut,
                                   LocalDate dateFin, int startRow) {
        int rowNum = startRow;

        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);

        CellStyle titleStyle = sheet.getWorkbook().createCellStyle();
        Font titleFont = sheet.getWorkbook().createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleCell.setCellStyle(titleStyle);

        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 5));

        // Période
        Row periodRow = sheet.createRow(rowNum++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue("Période du " + DateFormatter.format(dateDebut) +
                " au " + DateFormatter.format(dateFin));

        CellStyle periodStyle = sheet.getWorkbook().createCellStyle();
        periodStyle.setAlignment(HorizontalAlignment.CENTER);
        periodCell.setCellStyle(periodStyle);

        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 5));

        // Date de génération
        Row dateRow = sheet.createRow(rowNum++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Généré le " + DateFormatter.formatDateTime(LocalDateTime.now()));
        dateCell.setCellStyle(periodStyle);

        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 5));

        rowNum++; // Ligne vide
        return rowNum;
    }

    /**
     * Crée une section
     */
    private int createSection(Sheet sheet, String title, int startRow) {
        Row row = sheet.createRow(startRow);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);

        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);
        cell.setCellStyle(style);

        return startRow + 2; // Section + ligne vide
    }

    /**
     * Crée une ligne de statistique
     */
    private void createStatRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    /**
     * Crée une cellule d'en-tête
     */
    private void createHeaderCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /**
     * Crée une cellule de montant
     */
    private void createMontantCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(style);
        }
    }

    /**
     * Crée une cellule de date
     */
    private void createDateCell(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(DateFormatter.format(value));
            cell.setCellStyle(style);
        }
    }

    /**
     * Définit la valeur d'une cellule selon le type
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            if (value instanceof Integer) {
                cell.setCellValue(((Integer) value).doubleValue());
            } else if (value instanceof Long) {
                cell.setCellValue(((Long) value).doubleValue());
            } else if (value instanceof Double) {
                cell.setCellValue((Double) value);
            } else if (value instanceof Float) {
                cell.setCellValue(((Float) value).doubleValue());
            } else if (value instanceof BigDecimal) {
                cell.setCellValue(((BigDecimal) value).doubleValue());
            } else {
                cell.setCellValue(value.toString());
            }
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof LocalDate) {
            cell.setCellValue(DateFormatter.format((LocalDate) value));
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(DateFormatter.formatDateTime((LocalDateTime) value));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Exporte une liste vers Excel
     */
    private void exportListToExcel(Workbook workbook, List<?> list) {
        if (list.isEmpty()) {
            return;
        }

        Object firstItem = list.get(0);
        String sheetName = firstItem.getClass().getSimpleName();
        Sheet sheet = workbook.createSheet(sheetName);

        // Créer l'en-tête et les données selon le type
        if (firstItem instanceof Affaire) {
            createAffaireSheet(sheet, (List<Affaire>) list);
        } else if (firstItem instanceof Encaissement) {
            createEncaissementSheet(sheet, (List<Encaissement>) list);
        } else if (firstItem instanceof Agent) {
            createAgentSheet(sheet, (List<Agent>) list);
        } else if (firstItem instanceof Contrevenant) {
            createContrevenantSheet(sheet, (List<Contrevenant>) list);
        } else {
            // Export générique
            createGenericSheet(sheet, list);
        }
    }

    /**
     * Crée une feuille pour un objet
     */
    private void createObjectSheet(Sheet sheet, Object obj) {
        // Implementation basique - à améliorer selon les besoins
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("Type");
        row.createCell(1).setCellValue(obj.getClass().getSimpleName());

        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("Données");
        dataRow.createCell(1).setCellValue(obj.toString());
    }

    /**
     * Crée une feuille d'affaires
     */
    private void createAffaireSheet(Sheet sheet, List<Affaire> affaires) {
        // Utiliser la méthode existante
        exportAffairesToExcel(affaires, "temp.xlsx");
    }

    /**
     * Crée une feuille d'encaissements
     */
    private void createEncaissementSheet(Sheet sheet, List<Encaissement> encaissements) {
        // Utiliser la méthode existante
        exportEncaissementsToExcel(encaissements, "temp.xlsx");
    }

    /**
     * Crée une feuille d'agents
     */
    private void createAgentSheet(Sheet sheet, List<Agent> agents) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);

        String[] headers = {"Code", "Nom", "Prénom", "Grade", "Service", "Bureau", "Email", "Téléphone", "Actif"};
        for (int i = 0; i < headers.length; i++) {
            createHeaderCell(headerRow, i, headers[i], headerStyle);
        }

        for (Agent agent : agents) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(agent.getCodeAgent());
            row.createCell(1).setCellValue(agent.getNom());
            row.createCell(2).setCellValue(agent.getPrenom());
            row.createCell(3).setCellValue(agent.getGrade() != null ? agent.getGrade() : "");
            row.createCell(4).setCellValue(agent.getService() != null ? agent.getService().getNomService() : "");
            row.createCell(5).setCellValue(agent.getBureau() != null ? agent.getBureau().getNomBureau() : "");
            row.createCell(6).setCellValue(agent.getEmail() != null ? agent.getEmail() : "");
            row.createCell(7).setCellValue(agent.getTelephone() != null ? agent.getTelephone() : "");
            row.createCell(8).setCellValue(agent.isActif() ? "Oui" : "Non");
        }

        // Auto-dimensionner
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Crée une feuille de contrevenants
     */
    private void createContrevenantSheet(Sheet sheet, List<Contrevenant> contrevenants) {
        CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());

        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);

        String[] headers = {"Type", "Nom/Raison sociale", "Prénom", "CIN/RC", "Adresse", "Ville", "Téléphone", "Email", "Actif"};
        for (int i = 0; i < headers.length; i++) {
            createHeaderCell(headerRow, i, headers[i], headerStyle);
        }

        for (Contrevenant cont : contrevenants) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(cont.getType().getLibelle());

            if (cont.getType().isPersonnePhysique()) {
                row.createCell(1).setCellValue(cont.getNom() != null ? cont.getNom() : "");
                row.createCell(2).setCellValue(cont.getPrenom() != null ? cont.getPrenom() : "");
                row.createCell(3).setCellValue(cont.getCin() != null ? cont.getCin() : "");
            } else {
                row.createCell(1).setCellValue(cont.getRaisonSociale() != null ? cont.getRaisonSociale() : "");
                row.createCell(2).setCellValue("");
                row.createCell(3).setCellValue(cont.getNumeroRegistreCommerce() != null ? cont.getNumeroRegistreCommerce() : "");
            }

            row.createCell(4).setCellValue(cont.getAdresse() != null ? cont.getAdresse() : "");
            row.createCell(5).setCellValue(cont.getVille() != null ? cont.getVille() : "");
            row.createCell(6).setCellValue(cont.getTelephone() != null ? cont.getTelephone() : "");
            row.createCell(7).setCellValue(cont.getEmail() != null ? cont.getEmail() : "");
            row.createCell(8).setCellValue(cont.isActif() ? "Oui" : "Non");
        }

        // Auto-dimensionner
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Crée une feuille générique
     */
    private void createGenericSheet(Sheet sheet, List<?> list) {
        if (list.isEmpty()) return;

        int rowNum = 0;
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Index");
        headerRow.createCell(1).setCellValue("Valeur");

        int index = 1;
        for (Object item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(index++);
            row.createCell(1).setCellValue(item.toString());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    // ==================== STYLES ====================

    /**
     * Crée le style pour les en-têtes
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Crée le style pour les montants
     */
    private CellStyle createMontantStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    /**
     * Crée le style pour les dates
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        return style;
    }

    /**
     * Crée le style pour les totaux
     */
    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = createMontantStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderTop(BorderStyle.DOUBLE);
        return style;
    }
}