package com.regulation.contentieux.service;

import com.regulation.contentieux.service.RapportService.RapportRepartitionDTO;
import com.regulation.contentieux.service.RapportService.AffaireRepartitionDTO;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.RoundingMode;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Service d'export des rapports vers différents formats
 * Gère l'export Excel et la préparation pour PDF
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

    private static final String EXPORT_DIRECTORY = System.getProperty("user.home") + "/Documents/Rapports_Contentieux";

    /**
     * Exporte un rapport de rétrocession vers Excel
     */
    public byte[] exporterRapportVersExcel(RapportRepartitionDTO rapport) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            logger.info("Début de l'export Excel du rapport pour la période: {}", rapport.getPeriodeLibelle());

            // Création de la feuille principale
            Sheet sheet = workbook.createSheet("Rapport de Rétrocession");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);

            int rowNum = 0;

            // === EN-TÊTE DU RAPPORT ===
            rowNum = createReportHeader(sheet, rapport, titleStyle, rowNum);

            // === RÉSUMÉ EXÉCUTIF ===
            rowNum = createExecutiveSummary(sheet, rapport, headerStyle, currencyStyle, rowNum);

            // === DÉTAIL DES AFFAIRES ===
            rowNum = createAffairesDetail(sheet, rapport, headerStyle, currencyStyle, dateStyle, rowNum);

            // === STATISTIQUES PAR BUREAU ===
            rowNum = createBureauStats(sheet, rapport, headerStyle, currencyStyle, rowNum);

            // === STATISTIQUES PAR AGENT ===
            rowNum = createAgentStats(sheet, rapport, headerStyle, currencyStyle, rowNum);

            // Auto-ajustement des colonnes
            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);

            logger.info("Export Excel terminé avec succès");
            return outputStream.toByteArray();

        } catch (Exception e) {
            logger.error("Erreur lors de l'export Excel", e);
            throw new IOException("Impossible d'exporter le rapport vers Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Sauvegarde un rapport Excel sur le disque
     */
    public String sauvegarderRapportExcel(RapportRepartitionDTO rapport) throws IOException {
        byte[] excelData = exporterRapportVersExcel(rapport);

        // Création du nom de fichier
        String filename = String.format("Rapport_Retrocession_%s_%s.xlsx",
                rapport.getDateDebut().format(DateTimeFormatter.ofPattern("yyyyMM")),
                System.currentTimeMillis());

        Path filepath = Paths.get(EXPORT_DIRECTORY, filename);

        // Création du répertoire si nécessaire
        filepath.getParent().toFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(filepath.toFile())) {
            fos.write(excelData);
        }

        logger.info("Rapport Excel sauvegardé: {}", filepath);
        return filepath.toString();
    }

    /**
     * Génère les données CSV pour un rapport
     */
    public String exporterRapportVersCSV(RapportRepartitionDTO rapport) {
        StringBuilder csv = new StringBuilder();

        // En-tête CSV
        csv.append("\"Rapport de Rétrocession - ").append(rapport.getPeriodeLibelle()).append("\"\n");
        csv.append("\"Généré le: ").append(DateFormatter.formatDate(rapport.getDateGeneration())).append("\"\n\n");

        // Résumé
        csv.append("\"RÉSUMÉ EXÉCUTIF\"\n");
        csv.append("\"Nombre d'affaires\",").append(rapport.getNombreAffaires()).append("\n");
        csv.append("\"Total encaissé\",").append(formatCurrencyForCSV(rapport.getTotalEncaisse())).append("\n");
        csv.append("\"Part État (60%)\",").append(formatCurrencyForCSV(rapport.getTotalEtat())).append("\n");
        csv.append("\"Part Collectivité (40%)\",").append(formatCurrencyForCSV(rapport.getTotalCollectivite())).append("\n\n");

        // Détail des affaires
        csv.append("\"DÉTAIL DES AFFAIRES\"\n");
        csv.append("\"N° Affaire\",\"Date\",\"Contrevenant\",\"Type\",\"Montant Amende\",\"Montant Encaissé\",\"Part État\",\"Part Collectivité\",\"Chef Dossier\",\"Bureau\"\n");

        for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
            csv.append("\"").append(affaire.getNumeroAffaire()).append("\",");
            csv.append("\"").append(DateFormatter.formatDate(affaire.getDateCreation())).append("\",");
            csv.append("\"").append(escapeCSV(affaire.getContrevenantNom())).append("\",");
            csv.append("\"").append(escapeCSV(affaire.getContraventionType())).append("\",");
            csv.append(formatCurrencyForCSV(affaire.getMontantAmende())).append(",");
            csv.append(formatCurrencyForCSV(affaire.getMontantEncaisse())).append(",");
            csv.append(formatCurrencyForCSV(affaire.getPartEtat())).append(",");
            csv.append(formatCurrencyForCSV(affaire.getPartCollectivite())).append(",");
            csv.append("\"").append(escapeCSV(affaire.getChefDossier())).append("\",");
            csv.append("\"").append(escapeCSV(affaire.getBureau())).append("\"\n");
        }

        return csv.toString();
    }

    /**
     * Crée l'en-tête du rapport Excel
     */
    private int createReportHeader(Sheet sheet, RapportRepartitionDTO rapport, CellStyle titleStyle, int rowNum) {
        // Titre principal
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("RAPPORT DE RÉTROCESSION DES AMENDES");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        // Période
        Row periodRow = sheet.createRow(rowNum++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue("Période: " + rapport.getPeriodeLibelle());
        periodCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        // Date de génération
        Row dateRow = sheet.createRow(rowNum++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Généré le: " + DateFormatter.formatDate(rapport.getDateGeneration()));
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        rowNum++; // Ligne vide
        return rowNum;
    }

    /**
     * Crée le résumé exécutif
     */
    private int createExecutiveSummary(Sheet sheet, RapportRepartitionDTO rapport,
                                       CellStyle headerStyle, CellStyle currencyStyle, int rowNum) {

        // Titre de section
        Row sectionRow = sheet.createRow(rowNum++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("RÉSUMÉ EXÉCUTIF");
        sectionCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 6));

        rowNum++; // Ligne vide

        // Données du résumé
        String[][] summaryData = {
                {"Nombre d'affaires traitées:", String.valueOf(rapport.getNombreAffaires())},
                {"Nombre d'encaissements:", String.valueOf(rapport.getNombreEncaissements())},
                {"Total encaissé:", CurrencyFormatter.format(rapport.getTotalEncaisse())},
                {"Part de l'État (60%):", CurrencyFormatter.format(rapport.getTotalEtat())},
                {"Part des Collectivités (40%):", CurrencyFormatter.format(rapport.getTotalCollectivite())}
        };

        for (String[] data : summaryData) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(data[0]);
            Cell valueCell = row.createCell(1);
            valueCell.setCellValue(data[1]);
            if (data[1].contains("FCFA")) {
                valueCell.setCellStyle(currencyStyle);
            }
        }

        rowNum += 2; // Lignes vides
        return rowNum;
    }

    /**
     * Crée le détail des affaires
     */
    private int createAffairesDetail(Sheet sheet, RapportRepartitionDTO rapport,
                                     CellStyle headerStyle, CellStyle currencyStyle,
                                     CellStyle dateStyle, int rowNum) {

        // Titre de section
        Row sectionRow = sheet.createRow(rowNum++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("DÉTAIL DES AFFAIRES");
        sectionCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 9));

        // En-têtes des colonnes
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {
                "N° Affaire", "Date", "Contrevenant", "Type Contravention",
                "Montant Amende", "Montant Encaissé", "Part État", "Part Collectivité",
                "Chef Dossier", "Bureau"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Données des affaires
        for (AffaireRepartitionDTO affaire : rapport.getAffaires()) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(affaire.getNumeroAffaire());

            Cell dateCell = row.createCell(1);
            dateCell.setCellValue(DateFormatter.formatDate(affaire.getDateCreation()));
            dateCell.setCellStyle(dateStyle);

            row.createCell(2).setCellValue(affaire.getContrevenantNom());
            row.createCell(3).setCellValue(affaire.getContraventionType());

            Cell amendeCell = row.createCell(4);
            amendeCell.setCellValue(affaire.getMontantAmende().doubleValue());
            amendeCell.setCellStyle(currencyStyle);

            Cell encaisseCell = row.createCell(5);
            encaisseCell.setCellValue(affaire.getMontantEncaisse().doubleValue());
            encaisseCell.setCellStyle(currencyStyle);

            Cell etatCell = row.createCell(6);
            etatCell.setCellValue(affaire.getPartEtat().doubleValue());
            etatCell.setCellStyle(currencyStyle);

            Cell collectiviteCell = row.createCell(7);
            collectiviteCell.setCellValue(affaire.getPartCollectivite().doubleValue());
            collectiviteCell.setCellStyle(currencyStyle);

            row.createCell(8).setCellValue(affaire.getChefDossier());
            row.createCell(9).setCellValue(affaire.getBureau());
        }

        // Ligne de total
        Row totalRow = sheet.createRow(rowNum++);
        Cell totalLabelCell = totalRow.createCell(3);
        totalLabelCell.setCellValue("TOTAL:");
        totalLabelCell.setCellStyle(headerStyle);

        Cell totalAmendeCell = totalRow.createCell(4);
        BigDecimal totalAmende = rapport.getAffaires().stream()
                .map(AffaireRepartitionDTO::getMontantAmende)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmendeCell.setCellValue(totalAmende.doubleValue());
        totalAmendeCell.setCellStyle(currencyStyle);

        Cell totalEncaisseCell = totalRow.createCell(5);
        totalEncaisseCell.setCellValue(rapport.getTotalEncaisse().doubleValue());
        totalEncaisseCell.setCellStyle(currencyStyle);

        Cell totalEtatCell = totalRow.createCell(6);
        totalEtatCell.setCellValue(rapport.getTotalEtat().doubleValue());
        totalEtatCell.setCellStyle(currencyStyle);

        Cell totalCollectiviteCell = totalRow.createCell(7);
        totalCollectiviteCell.setCellValue(rapport.getTotalCollectivite().doubleValue());
        totalCollectiviteCell.setCellStyle(currencyStyle);

        rowNum += 2; // Lignes vides
        return rowNum;
    }

    /**
     * Crée les statistiques par bureau
     */
    private int createBureauStats(Sheet sheet, RapportRepartitionDTO rapport,
                                  CellStyle headerStyle, CellStyle currencyStyle, int rowNum) {

        if (rapport.getRepartitionParBureau().isEmpty()) {
            return rowNum;
        }

        // Titre de section
        Row sectionRow = sheet.createRow(rowNum++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("RÉPARTITION PAR BUREAU");
        sectionCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 3));

        // En-têtes
        Row headerRow = sheet.createRow(rowNum++);
        Cell bureauHeader = headerRow.createCell(0);
        bureauHeader.setCellValue("Bureau");
        bureauHeader.setCellStyle(headerStyle);

        Cell montantHeader = headerRow.createCell(1);
        montantHeader.setCellValue("Montant Encaissé");
        montantHeader.setCellStyle(headerStyle);

        Cell pourcentageHeader = headerRow.createCell(2);
        pourcentageHeader.setCellValue("Pourcentage");
        pourcentageHeader.setCellStyle(headerStyle);

        // Données
        for (Map.Entry<String, BigDecimal> entry : rapport.getRepartitionParBureau().entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());

            Cell montantCell = row.createCell(1);
            montantCell.setCellValue(entry.getValue().doubleValue());
            montantCell.setCellStyle(currencyStyle);

            // Calcul du pourcentage
            BigDecimal pourcentage = entry.getValue()
                    .divide(rapport.getTotalEncaisse(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
            row.createCell(2).setCellValue(pourcentage.doubleValue() + "%");
        }

        rowNum += 2; // Lignes vides
        return rowNum;
    }

    /**
     * Crée les statistiques par agent
     */
    private int createAgentStats(Sheet sheet, RapportRepartitionDTO rapport,
                                 CellStyle headerStyle, CellStyle currencyStyle, int rowNum) {

        if (rapport.getRepartitionParAgent().isEmpty()) {
            return rowNum;
        }

        // Titre de section
        Row sectionRow = sheet.createRow(rowNum++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("RÉPARTITION PAR AGENT");
        sectionCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum-1, rowNum-1, 0, 3));

        // En-têtes
        Row headerRow = sheet.createRow(rowNum++);
        Cell agentHeader = headerRow.createCell(0);
        agentHeader.setCellValue("Agent");
        agentHeader.setCellStyle(headerStyle);

        Cell montantHeader = headerRow.createCell(1);
        montantHeader.setCellValue("Montant Encaissé");
        montantHeader.setCellStyle(headerStyle);

        Cell pourcentageHeader = headerRow.createCell(2);
        pourcentageHeader.setCellValue("Pourcentage");
        pourcentageHeader.setCellStyle(headerStyle);

        // Données triées par montant décroissant
        rapport.getRepartitionParAgent().entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> {
                    Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                    row.createCell(0).setCellValue(entry.getKey());

                    Cell montantCell = row.createCell(1);
                    montantCell.setCellValue(entry.getValue().doubleValue());
                    montantCell.setCellStyle(currencyStyle);

                    BigDecimal pourcentage = entry.getValue()
                            .divide(rapport.getTotalEncaisse(), 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(new BigDecimal("100"));
                    row.createCell(2).setCellValue(pourcentage.doubleValue() + "%");
                });

        return sheet.getLastRowNum() + 3;
    }

    /**
     * Styles Excel
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0 \"FCFA\""));
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        return style;
    }

    /**
     * Utilitaires pour CSV
     */
    private String formatCurrencyForCSV(BigDecimal amount) {
        if (amount == null) return "0";
        return "\"" + CurrencyFormatter.format(amount) + "\"";
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    /**
     * Génère un nom de fichier unique pour l'export
     */
    public String genererNomFichier(String type, LocalDate dateDebut, LocalDate dateFin) {
        String periode = dateDebut.format(DateTimeFormatter.ofPattern("yyyyMM"));
        if (!dateDebut.getMonth().equals(dateFin.getMonth()) ||
                !dateDebut.getYear().equals(dateFin.getYear())) {
            periode = dateDebut.format(DateTimeFormatter.ofPattern("yyyyMM")) +
                    "_" + dateFin.format(DateTimeFormatter.ofPattern("yyyyMM"));
        }

        return String.format("Rapport_%s_%s_%d.%s",
                type, periode, System.currentTimeMillis(),
                type.toLowerCase().equals("excel") ? "xlsx" : "csv");
    }

    /**
     * Crée le répertoire d'export s'il n'existe pas
     */
    public void creerRepertoireExport() {
        Path directory = Paths.get(EXPORT_DIRECTORY);
        if (!directory.toFile().exists()) {
            directory.toFile().mkdirs();
            logger.info("Répertoire d'export créé: {}", EXPORT_DIRECTORY);
        }
    }

    /**
     * Retourne le répertoire d'export
     */
    public String getRepertoireExport() {
        return EXPORT_DIRECTORY;
    }
}