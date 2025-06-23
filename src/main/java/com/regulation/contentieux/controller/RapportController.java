package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.enums.TypeRapport;
import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.service.ExportService;
import com.regulation.contentieux.service.PrintService;
import com.regulation.contentieux.service.SituationGeneraleDTO;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la génération et l'affichage des rapports
 * ENRICHI : Gère les 8 types de rapports conformes aux templates
 */
public class RapportController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportController.class);

    // Sélection du type de rapport
    @FXML private ComboBox<TypeRapport> typeRapportComboBox;
    @FXML private Label descriptionLabel;

    // Paramètres de période
    @FXML private ComboBox<String> periodeTypeComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private ComboBox<Month> moisComboBox;
    @FXML private ComboBox<Integer> anneeComboBox;
    @FXML private VBox periodePersonnaliseeBox;
    @FXML private VBox periodeMensuelleBox;

    // Filtres additionnels
    @FXML private VBox filtresAdditionnelsBox;
    @FXML private ComboBox<String> bureauFilterComboBox;
    @FXML private ComboBox<String> serviceFilterComboBox;
    @FXML private CheckBox includeDetailsCheckBox;

    // Actions
    @FXML private Button genererButton;
    @FXML private Button previewButton;
    @FXML private Button imprimerButton;
    @FXML private Button exportPdfButton;
    @FXML private Button exportExcelButton;

    // Aperçu
    @FXML private WebView previewWebView;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;

    @FXML
    private void handleShowStatistics() {
        // Suppression de la référence à RapportStatistiquesController
        // Remplacé par une simple boîte de dialogue d'information

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Statistiques des rapports");
        alert.setHeaderText("Fonctionnalité en développement");
        alert.setContentText("Les statistiques détaillées des rapports seront bientôt disponibles.\n\n" +
                "Cette fonctionnalité permettra de visualiser :\n" +
                "- Le nombre de rapports générés par type\n" +
                "- Les tendances mensuelles\n" +
                "- Les comparaisons entre périodes\n" +
                "- Les exports les plus fréquents");
        alert.showAndWait();
    }

    // Services
    private final RapportService rapportService = Pnew RapportService();
    private final ExportService exportService = new ExportService();
    private final PrintService printService = new PrintService();

    // État
    private WebEngine webEngine;
    private String dernierRapportGenere;
    private Object dernierRapportData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        webEngine = previewWebView.getEngine();

        initializeTypeRapport();
        initializePeriode();
        initializeFiltres();

        // État initial
        progressIndicator.setVisible(false);
        updateButtonStates(false);
    }

    private void initializeTypeRapport() {
        typeRapportComboBox.getItems().addAll(TypeRapport.values());

        typeRapportComboBox.setConverter(new StringConverter<TypeRapport>() {
            @Override
            public String toString(TypeRapport type) {
                return type != null ? type.getLibelle() : "";
            }

            @Override
            public TypeRapport fromString(String string) {
                return null;
            }
        });

        // Listener pour afficher la description
        typeRapportComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                descriptionLabel.setText(newVal.getDescription());
                configurerFiltresSelonType(newVal);
            }
        });

        // Sélection par défaut
        typeRapportComboBox.getSelectionModel().selectFirst();
    }

    // Variables pour conserver le dernier contenu HTML généré
    private String dernierHtmlContent;

    /**
     * ENRICHISSEMENT : Affichage dans WebView avec sauvegarde du contenu
     */
    private void afficherRapportDansWebView(String htmlContent) {
        if (webView != null && htmlContent != null) {
            dernierHtmlContent = htmlContent;

            // Ajouter le CSS intégré pour un meilleur rendu
            String htmlAvecStyle = ajouterStylesCSS(htmlContent);

            webView.getEngine().loadContent(htmlAvecStyle);

            // Activer le zoom
            webView.setZoom(1.0);

            logger.debug("Rapport affiché dans WebView ({} caractères)", htmlContent.length());
        }
    }

    /**
     * ENRICHISSEMENT : Ajout de styles CSS intégrés
     */
    private String ajouterStylesCSS(String htmlContent) {
        String css = """
        <style>
            body { 
                font-family: Arial, sans-serif; 
                font-size: 12px; 
                margin: 20px; 
                line-height: 1.4;
            }
            .rapport-table { 
                width: 100%; 
                border-collapse: collapse; 
                margin: 20px 0; 
            }
            .rapport-table th, .rapport-table td { 
                border: 1px solid #333; 
                padding: 6px; 
                text-align: left; 
            }
            .rapport-table th { 
                background-color: #f0f0f0; 
                font-weight: bold; 
                text-align: center;
            }
            .montant { 
                text-align: right; 
                font-family: monospace;
            }
            .total-row { 
                background-color: #f8f8f8; 
                font-weight: bold; 
            }
            .section-header { 
                background-color: #e8f4fd; 
                font-weight: bold; 
            }
            .subtotal-row { 
                background-color: #f5f5f5; 
                font-style: italic; 
            }
            .info-box { 
                background-color: #d1ecf1; 
                border: 1px solid #bee5eb; 
                padding: 20px; 
                margin: 20px 0; 
                border-radius: 5px;
            }
            .error-box { 
                background-color: #f8d7da; 
                border: 1px solid #f5c6cb; 
                padding: 20px; 
                margin: 20px 0; 
                border-radius: 5px;
            }
            @media print {
                body { margin: 0; font-size: 10px; }
                .rapport-table { font-size: 9px; }
            }
        </style>
    """;

        // Insérer le CSS dans le HTML
        if (htmlContent.contains("<head>")) {
            return htmlContent.replace("</head>", css + "</head>");
        } else if (htmlContent.contains("<html>")) {
            return htmlContent.replace("<html>", "<html><head>" + css + "</head>");
        } else {
            return "<html><head>" + css + "</head><body>" + htmlContent + "</body></html>";
        }
    }

    private void initializePeriode() {
        // Types de période
        periodeTypeComboBox.getItems().addAll(
                "Personnalisée",
                "Mois en cours",
                "Mois précédent",
                "Trimestre en cours",
                "Année en cours"
        );

        // Années disponibles
        int anneeActuelle = LocalDate.now().getYear();
        for (int i = anneeActuelle; i >= anneeActuelle - 5; i--) {
            anneeComboBox.getItems().add(i);
        }

        // Mois
        moisComboBox.getItems().addAll(Month.values());

        // Listener pour le type de période
        periodeTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            configurerPeriodeSelonType(newVal);
        });

        // Sélection par défaut
        periodeTypeComboBox.getSelectionModel().selectFirst();
    }

    private void initializeFiltres() {
        // TODO: Charger les bureaux et services depuis la BDD
        bureauFilterComboBox.getItems().add("Tous");
        serviceFilterComboBox.getItems().add("Tous");

        bureauFilterComboBox.getSelectionModel().selectFirst();
        serviceFilterComboBox.getSelectionModel().selectFirst();
    }

    private void configurerFiltresSelonType(TypeRapport type) {
        // Afficher/masquer les filtres selon le type
        boolean showBureauFilter = type == TypeRapport.REPARTITION_RETROCESSION ||
                type == TypeRapport.ENCAISSEMENTS_PERIODE;

        bureauFilterComboBox.setVisible(showBureauFilter);
        bureauFilterComboBox.setManaged(showBureauFilter);

        boolean showServiceFilter = type == TypeRapport.TABLEAU_AMENDES_SERVICE ||
                type == TypeRapport.MANDATEMENT_AGENTS;

        serviceFilterComboBox.setVisible(showServiceFilter);
        serviceFilterComboBox.setManaged(showServiceFilter);
    }

    private void configurerPeriodeSelonType(String typePeriode) {
        boolean isPersonnalisee = "Personnalisée".equals(typePeriode);

        periodePersonnaliseeBox.setVisible(isPersonnalisee);
        periodePersonnaliseeBox.setManaged(isPersonnalisee);

        if (!isPersonnalisee) {
            // Calculer automatiquement les dates
            LocalDate[] dates = calculerPeriode(typePeriode);
            if (dates != null) {
                dateDebutPicker.setValue(dates[0]);
                dateFinPicker.setValue(dates[1]);
            }
        }
    }

    private LocalDate[] calculerPeriode(String typePeriode) {
        LocalDate debut, fin;
        LocalDate aujourd = LocalDate.now();

        switch (typePeriode) {
            case "Mois en cours":
                debut = aujourd.withDayOfMonth(1);
                fin = aujourd.withDayOfMonth(aujourd.lengthOfMonth());
                break;

            case "Mois précédent":
                LocalDate moisPrecedent = aujourd.minusMonths(1);
                debut = moisPrecedent.withDayOfMonth(1);
                fin = moisPrecedent.withDayOfMonth(moisPrecedent.lengthOfMonth());
                break;

            case "Trimestre en cours":
                int trimestre = (aujourd.getMonthValue() - 1) / 3;
                debut = LocalDate.of(aujourd.getYear(), trimestre * 3 + 1, 1);
                fin = debut.plusMonths(2).withDayOfMonth(
                        debut.plusMonths(2).lengthOfMonth()
                );
                break;

            case "Année en cours":
                debut = LocalDate.of(aujourd.getYear(), 1, 1);
                fin = LocalDate.of(aujourd.getYear(), 12, 31);
                break;

            default:
                return null;
        }

        return new LocalDate[]{debut, fin};
    }

    /**
     * ENRICHISSEMENT : Génération avec support de tous les templates
     */
    @FXML
    private void handleGenererRapport() {
        TypeRapport type = typeRapportComboBox.getValue();
        if (type == null) {
            AlertUtil.showWarningAlert("Type requis", "Sélection manquante",
                    "Veuillez sélectionner un type de rapport.");
            return;
        }

        // Validation de la période
        LocalDate debut = getDateDebut();
        LocalDate fin = getDateFin();

        if (debut == null || fin == null || debut.isAfter(fin)) {
            AlertUtil.showWarningAlert("Période invalide", "Dates incorrectes",
                    "Veuillez sélectionner une période valide.");
            return;
        }

        // Afficher l'indicateur de progression
        showProgressIndicator(true, "Génération du rapport en cours...");
        genererButton.setDisable(true);

        // Génération asynchrone
        Task<Object> generationTask = new Task<>() {
            @Override
            protected Object call() throws Exception {
                return genererRapportParType(type, debut, fin);
            }
        };

        generationTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                Object rapportData = generationTask.getValue();

                if (rapportData != null) {
                    dernierRapportData = rapportData;
                    dernierTypeRapport = type;

                    // Générer et afficher le HTML
                    String htmlContent = genererHtmlParType(type, debut, fin, rapportData);
                    afficherRapportDansWebView(htmlContent);

                    // Activer les boutons d'export
                    activerBoutonsExport(true);

                    AlertUtil.showSuccess("Rapport généré",
                            "Le rapport a été généré avec succès.");

                } else {
                    AlertUtil.showWarningAlert("Génération échouée",
                            "Aucune donnée",
                            "Aucune donnée trouvée pour la période sélectionnée.");
                }

                showProgressIndicator(false, "");
                genererButton.setDisable(false);
            });
        });

        generationTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable exception = generationTask.getException();
                logger.error("Erreur lors de la génération du rapport", exception);

                AlertUtil.showErrorAlert("Erreur de génération",
                        "Impossible de générer le rapport",
                        exception.getMessage());

                showProgressIndicator(false, "");
                genererButton.setDisable(false);
            });
        });

        // Lancer la tâche
        Thread generationThread = new Thread(generationTask);
        generationThread.setDaemon(true);
        generationThread.start();
    }

    // Méthode alternative si besoin d'afficher des statistiques simples
    private void afficherStatistiquesSimples() {
        StringBuilder stats = new StringBuilder();
        stats.append("Statistiques du jour\n");
        stats.append("===================\n\n");

        if (dernierRapportData != null) {
            stats.append("Dernier rapport généré : ").append(typeRapportComboBox.getValue().getLibelle()).append("\n");
            stats.append("Période : ").append(dateDebutPicker.getValue()).append(" au ").append(dateFinPicker.getValue()).append("\n");

            if (dernierRapportData instanceof RapportService.RapportRepartitionDTO rapport) {
                stats.append("Nombre d'affaires : ").append(rapport.getNombreAffaires()).append("\n");
                stats.append("Total encaissé : ").append(rapport.getTotalEncaisse()).append(" FCFA\n");
            } else if (dernierRapportData instanceof SituationGeneraleDTO situation) {
                stats.append("Total des affaires : ").append(situation.getTotalAffaires()).append("\n");
                stats.append("Taux de recouvrement : ").append(situation.getTauxRecouvrement()).append("%\n");
            }
        } else {
            stats.append("Aucun rapport généré dans cette session.");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Statistiques");
        alert.setHeaderText("Statistiques de la session");
        alert.setContentText(stats.toString());
        alert.showAndWait();
    }

    /**
     * ENRICHISSEMENT : Génération HTML selon le type de rapport
     */
    private String genererHtmlParType(TypeRapport type, LocalDate debut, LocalDate fin, Object rapportData) {
        try {
            switch (type) {
                // Templates avec HTML direct
                case ETAT_REPARTITION_AFFAIRES:
                    return rapportService.genererEtatRepartitionAffaires(debut, fin);

                case ETAT_MANDATEMENT:
                    return rapportService.genererEtatMandatement(debut, fin);

                case CENTRE_REPARTITION:
                    return rapportService.genererEtatCentreRepartition(debut, fin);

                case INDICATEURS_REELS:
                    return rapportService.genererEtatIndicateursReels(debut, fin);

                case REPARTITION_PRODUIT:
                    return rapportService.genererEtatRepartitionProduit(debut, fin);

                case ETAT_CUMULE_AGENT:
                    return rapportService.genererEtatCumuleParAgent(debut, fin);

                case MANDATEMENT_AGENTS:
                    return rapportService.genererEtatMandatementAgents(debut, fin);

                case TABLEAU_AMENDES_SERVICE:
                    return rapportService.genererTableauAmendesParServices(debut, fin);

                // Templates avec conversion de données
                case REPARTITION_RETROCESSION:
                    return convertirRepartitionVersHTML((RapportService.RapportRepartitionDTO) rapportData, debut, fin);

                case SITUATION_GENERALE:
                    return convertirSituationVersHTML((SituationGeneraleDTO) rapportData, debut, fin);

                default:
                    return genererHtmlGenerique(type, rapportData, debut, fin);
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la génération HTML pour {}", type, e);
            return genererHtmlErreur(type, e);
        }
    }

    /**
     * ENRICHISSEMENT : HTML d'erreur
     */
    private String genererHtmlErreur(TypeRapport type, Exception erreur) {
        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteHTML("Erreur - " + type.getLibelle(), LocalDate.now(), LocalDate.now()));

        html.append("<div class='error-box'>");
        html.append("<h3>Erreur lors de la génération du rapport</h3>");
        html.append("<p><strong>Type :</strong> ").append(type.getLibelle()).append("</p>");
        html.append("<p><strong>Erreur :</strong> ").append(erreur.getMessage()).append("</p>");
        html.append("<p>Veuillez vérifier les paramètres et réessayer.</p>");
        html.append("</div>");

        html.append(genererPiedHTML());

        return html.toString();
    }


    /**
     * ENRICHISSEMENT : HTML générique en cas de type non supporté
     */
    private String genererHtmlGenerique(TypeRapport type, Object rapportData, LocalDate debut, LocalDate fin) {
        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteHTML(type.getLibelle(), debut, fin));

        html.append("<div class='info-box'>");
        html.append("<h3>Rapport en cours de développement</h3>");
        html.append("<p>Le template HTML pour ce type de rapport sera disponible prochainement.</p>");
        html.append("<p><strong>Type :</strong> ").append(type.getLibelle()).append("</p>");
        html.append("<p><strong>Période :</strong> ").append(debut).append(" au ").append(fin).append("</p>");

        if (rapportData != null) {
            html.append("<p><strong>Données générées :</strong> ").append(rapportData.getClass().getSimpleName()).append("</p>");
        }

        html.append("<p><em>Vous pouvez exporter les données en Excel pour consultation.</em></p>");
        html.append("</div>");

        html.append(genererPiedHTML());

        return html.toString();
    }

    /**
     * ENRICHISSEMENT : Conversion HTML générique pour les nouveaux types
     */
    private String convertirRepartitionVersHTML(RapportService.RapportRepartitionDTO rapport, LocalDate debut, LocalDate fin) {
        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteHTML("ÉTAT DE RÉPARTITION ET DE RÉTROCESSION", debut, fin));

        html.append("""
        <table class="rapport-table">
            <thead>
                <tr>
                    <th>N° Affaire</th>
                    <th>Contrevenant</th>
                    <th>Montant Total</th>
                    <th>Part État (60%)</th>
                    <th>Part Collectivité (40%)</th>
                </tr>
            </thead>
            <tbody>
    """);

        for (RapportService.AffaireRepartitionDTO affaire : rapport.getAffaires()) {
            html.append("<tr>");
            html.append("<td>").append(affaire.getNumeroAffaire()).append("</td>");
            html.append("<td>").append(affaire.getContrevenant()).append("</td>");
            html.append("<td class='montant'>").append(formatMontant(affaire.getMontantTotal())).append("</td>");
            html.append("<td class='montant'>").append(formatMontant(affaire.getPartEtat())).append("</td>");
            html.append("<td class='montant'>").append(formatMontant(affaire.getPartCollectivite())).append("</td>");
            html.append("</tr>");
        }

        // Totaux
        html.append("""
        <tr class="total-row">
            <td colspan="2"><strong>TOTAUX</strong></td>
            <td class="montant"><strong>""").append(formatMontant(rapport.getTotalMontant())).append("""
            </strong></td>
            <td class="montant"><strong>""").append(formatMontant(rapport.getTotalPartEtat())).append("""
            </strong></td>
            <td class="montant"><strong>""").append(formatMontant(rapport.getTotalPartCollectivite())).append("""
            </strong></td>
        </tr>
    """);

        html.append("</tbody></table>");
        html.append(genererPiedHTML());

        return html.toString();
    }

    // ==================== GÉNÉRATION PAR TYPE DE RAPPORT ====================

    /**
     * ENRICHISSEMENT : Génération des données selon le type de rapport
     */
    private Object genererRapportParType(TypeRapport type, LocalDate debut, LocalDate fin) {
        logger.info("Génération rapport type: {} pour période {} - {}", type, debut, fin);

        try {
            switch (type) {
                // Templates existants
                case ETAT_REPARTITION_AFFAIRES:
                    return rapportService.genererDonneesEtatRepartitionAffaires(debut, fin);

                case REPARTITION_RETROCESSION:
                    return rapportService.genererRapportRepartition(debut, fin);

                case TABLEAU_AMENDES_SERVICE:
                    return rapportService.genererDonneesTableauAmendesParServices(debut, fin);

                // NOUVEAUX TEMPLATES - Phase 3
                case ETAT_MANDATEMENT: // Template 2
                    return rapportService.genererDonneesEtatMandatement(debut, fin);

                case CENTRE_REPARTITION: // Template 3
                    return rapportService.genererDonneesCentreRepartition(debut, fin);

                case INDICATEURS_REELS: // Template 4
                    return rapportService.genererDonneesIndicateursReels(debut, fin);

                case REPARTITION_PRODUIT: // Template 5
                    return rapportService.genererDonneesRepartitionProduit(debut, fin);

                case ETAT_CUMULE_AGENT: // Template 6
                    return rapportService.genererDonneesEtatCumuleParAgent(debut, fin);

                case MANDATEMENT_AGENTS: // Template 8
                    return rapportService.genererDonneesMandatementAgents(debut, fin);

                // Autres rapports
                case SITUATION_GENERALE:
                    return rapportService.genererSituationGenerale(debut, fin);

                case ENCAISSEMENTS_PERIODE:
                    return rapportService.genererRapportEncaissements(debut, fin);

                case AFFAIRES_NON_SOLDEES:
                    return rapportService.genererRapportAffairesNonSoldees(debut, fin);

                default:
                    logger.warn("Type de rapport non supporté: {}", type);
                    return null;
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la génération des données pour {}", type, e);
            throw new RuntimeException("Erreur de génération: " + e.getMessage(), e);
        }
    }


    /**
     * Génère le HTML pour la situation générale
     */
    private String genererHtmlSituationGenerale(SituationGeneraleDTO situation) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Situation Générale</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        html.append("h1 { text-align: center; }");
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
        html.append("th, td { border: 1px solid black; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f0f0f0; font-weight: bold; }");
        html.append(".montant { text-align: right; }");
        html.append("</style>");
        html.append("</head><body>");

        html.append("<h1>Situation Générale des Affaires</h1>");
        html.append("<p>Période : ").append(DateFormatter.format(situation.getDateDebut()));
        html.append(" au ").append(DateFormatter.format(situation.getDateFin())).append("</p>");

        html.append("<h2>Statistiques</h2>");
        html.append("<table>");
        html.append("<tr><th>Indicateur</th><th>Valeur</th></tr>");
        html.append("<tr><td>Total des affaires</td><td>").append(situation.getTotalAffaires()).append("</td></tr>");
        html.append("<tr><td>Affaires ouvertes</td><td>").append(situation.getAffairesOuvertes()).append("</td></tr>");
        html.append("<tr><td>Affaires en cours</td><td>").append(situation.getAffairesEnCours()).append("</td></tr>");
        html.append("<tr><td>Affaires soldées</td><td>").append(situation.getAffairesSoldees()).append("</td></tr>");
        html.append("</table>");

        if (situation.getTotalAmendes() != null) {
            html.append("<h2>Montants</h2>");
            html.append("<table>");
            html.append("<tr><th>Type</th><th>Montant</th></tr>");
            html.append("<tr><td>Total des amendes</td><td class='montant'>")
                    .append(situation.getTotalAmendes()).append(" FCFA</td></tr>");
            html.append("<tr><td>Total encaissé</td><td class='montant'>")
                    .append(situation.getTotalEncaisse()).append(" FCFA</td></tr>");
            html.append("<tr><td>Total restant</td><td class='montant'>")
                    .append(situation.getTotalRestant()).append(" FCFA</td></tr>");
            if (situation.getTauxEncaissement() != null) {
                html.append("<tr><td>Taux d'encaissement</td><td>")
                        .append(String.format("%.2f%%", situation.getTauxEncaissement())).append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("</body></html>");

        return html.toString();
    }

    @FXML
    private void handlePreview() {
        if (dernierRapportGenere != null && !dernierRapportGenere.isEmpty()) {
            webEngine.loadContent(dernierRapportGenere);
        }
    }

    @FXML
    private void handleImprimer() {
        if (dernierRapportGenere == null || dernierRapportGenere.isEmpty()) {
            AlertUtil.showWarningAlert("Aucun rapport",
                    "Impression impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        printService.printHtml(dernierRapportGenere);
    }

    @FXML
    private void handleExportPdf() {
        if (dernierRapportGenere == null || dernierRapportGenere.isEmpty()) {
            AlertUtil.showWarningAlert("Aucun rapport",
                    "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en PDF");
        fileChooser.setInitialFileName("rapport_" +
                LocalDate.now() + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(exportPdfButton.getScene().getWindow());
        if (file != null) {
            // TODO: Implémenter l'export PDF
            AlertUtil.showInfoAlert("Export PDF",
                    "Fonctionnalité en développement",
                    "L'export PDF sera bientôt disponible.");
        }
    }

    /**
     * ENRICHISSEMENT : Export Excel avec support de tous les types
     */
    @FXML
    private void handleExportExcel() {
        if (dernierRapportData == null || dernierTypeRapport == null) {
            AlertUtil.showWarningAlert("Aucun rapport", "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en Excel");
        fileChooser.setInitialFileName(genererNomFichier(dernierTypeRapport, "xlsx"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(exportExcelButton.getScene().getWindow());
        if (file != null) {

            // Indicateur de progression
            showProgressIndicator(true, "Export Excel en cours...");
            exportExcelButton.setDisable(true);

            Task<Boolean> exportTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    return exporterSelonType(dernierTypeRapport, dernierRapportData, file.getAbsolutePath(), "excel");
                }
            };

            exportTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    boolean success = exportTask.getValue();

                    if (success) {
                        AlertUtil.showSuccess("Export réussi",
                                "Le rapport a été exporté en Excel avec succès.");

                        // Proposer d'ouvrir le fichier
                        if (AlertUtil.showConfirmation("Ouvrir le fichier",
                                "Voulez-vous ouvrir le fichier Excel ?")) {
                            ouvrirFichier(file);
                        }
                    } else {
                        AlertUtil.showErrorAlert("Export échoué", "Erreur d'export",
                                "Impossible d'exporter le rapport en Excel.");
                    }

                    showProgressIndicator(false, "");
                    exportExcelButton.setDisable(false);
                });
            });

            exportTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    Throwable exception = exportTask.getException();
                    logger.error("Erreur lors de l'export Excel", exception);

                    AlertUtil.showErrorAlert("Erreur d'export",
                            "Impossible d'exporter en Excel",
                            exception.getMessage());

                    showProgressIndicator(false, "");
                    exportExcelButton.setDisable(false);
                });
            });

            Thread exportThread = new Thread(exportTask);
            exportThread.setDaemon(true);
            exportThread.start();
        }
    }

    /**
     * ENRICHISSEMENT : Export selon le type de rapport
     */
    private boolean exporterSelonType(TypeRapport type, Object rapportData, String outputPath, String format) {
        try {
            if ("excel".equalsIgnoreCase(format)) {
                return exportService.exportGenericToExcel(rapportData, outputPath);
            } else if ("pdf".equalsIgnoreCase(format)) {
                String htmlContent = genererHtmlParType(type, getDateDebut(), getDateFin(), rapportData);
                return exportService.exportReportToPDF(htmlContent, outputPath, type.getLibelle());
            }

            return false;

        } catch (Exception e) {
            logger.error("Erreur lors de l'export {} pour type {}", format, type, e);
            return false;
        }
    }

    /**
     * ENRICHISSEMENT : Génération de nom de fichier intelligent
     */
    private String genererNomFichier(TypeRapport type, String extension) {
        String typeNom = type.name().toLowerCase().replace("_", "-");
        String dateSuffix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String periodeSuffix = "";

        // Ajouter la période si disponible
        if (getDateDebut() != null && getDateFin() != null) {
            periodeSuffix = "_" + getDateDebut().format(DateTimeFormatter.ofPattern("yyyy-MM")) +
                    "_" + getDateFin().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }

        return String.format("rapport-%s%s_%s.%s", typeNom, periodeSuffix, dateSuffix, extension);
    }

    /**
     * ENRICHISSEMENT : Export PDF avec support de tous les types
     */
    @FXML
    private void handleExportPDF() {
        if (dernierRapportData == null || dernierTypeRapport == null) {
            AlertUtil.showWarningAlert("Aucun rapport", "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en PDF");
        fileChooser.setInitialFileName(genererNomFichier(dernierTypeRapport, "pdf"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(exportPDFButton.getScene().getWindow());
        if (file != null) {

            showProgressIndicator(true, "Export PDF en cours...");
            exportPDFButton.setDisable(true);

            Task<Boolean> exportTask = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    // Obtenir le contenu HTML actuel
                    String htmlContent = dernierHtmlContent != null ?
                            dernierHtmlContent :
                            genererHtmlParType(dernierTypeRapport, getDateDebut(), getDateFin(), dernierRapportData);

                    return exportService.exportReportToPDF(htmlContent, file.getAbsolutePath(),
                            dernierTypeRapport.getLibelle());
                }
            };

            exportTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    boolean success = exportTask.getValue();

                    if (success) {
                        AlertUtil.showSuccess("Export réussi",
                                "Le rapport a été exporté en PDF avec succès.");

                        if (AlertUtil.showConfirmation("Ouvrir le fichier",
                                "Voulez-vous ouvrir le fichier PDF ?")) {
                            ouvrirFichier(file);
                        }
                    } else {
                        AlertUtil.showErrorAlert("Export échoué", "Erreur d'export",
                                "Impossible d'exporter le rapport en PDF.");
                    }

                    showProgressIndicator(false, "");
                    exportPDFButton.setDisable(false);
                });
            });

            exportTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    Throwable exception = exportTask.getException();
                    logger.error("Erreur lors de l'export PDF", exception);

                    AlertUtil.showErrorAlert("Erreur d'export",
                            "Impossible d'exporter en PDF",
                            exception.getMessage());

                    showProgressIndicator(false, "");
                    exportPDFButton.setDisable(false);
                });
            });

            Thread exportThread = new Thread(exportTask);
            exportThread.setDaemon(true);
            exportThread.start();
        }
    }


    @FXML
    private void handleOpenRapportAvance() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/rapport-avance.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Rapports Avancés");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture des rapports avancés", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir",
                    "Erreur lors de l'ouverture des rapports avancés.");
        }
    }

    /**
     * Affiche la fenêtre de comparaison de périodes
     */
    @FXML
    private void handleComparerPeriodes() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/rapport-comparaison.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Comparaison de Périodes");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture de la comparaison", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir",
                    "Erreur lors de l'ouverture de la comparaison de périodes.");
        }
    }

    /**
     * Ouvre la fenêtre de programmation de rapports
     */
    @FXML
    private void handleProgrammerRapports() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/rapport-programmation.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Programmation de Rapports");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture de la programmation", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir",
                    "Erreur lors de l'ouverture de la programmation de rapports.");
        }
    }

    /**
     * Affiche les statistiques détaillées
     */
    @FXML
    private void handleStatistiquesDetaillees() {
        if (dernierRapportData == null) {
            AlertUtil.showWarningAlert("Aucune donnée",
                    "Statistiques indisponibles",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/rapport-statistiques.fxml")
            );
            Parent root = loader.load();

            // Passer les données au contrôleur
            RapportStatistiquesController controller = loader.getController();
            controller.setRapportData(dernierRapportData);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Statistiques Détaillées");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture des statistiques", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir",
                    "Erreur lors de l'ouverture des statistiques détaillées.");
        }
    }

    /**
     * Méthode utilitaire pour formater les périodes
     */
    private String formatPeriode(LocalDate debut, LocalDate fin) {
        if (debut.getYear() == fin.getYear()) {
            if (debut.getMonth() == fin.getMonth()) {
                return String.format("%s %d",
                        debut.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                                java.util.Locale.FRENCH),
                        debut.getYear());
            } else {
                return String.format("%s à %s %d",
                        debut.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                                java.util.Locale.FRENCH),
                        fin.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                                java.util.Locale.FRENCH),
                        debut.getYear());
            }
        } else {
            return String.format("%s %d à %s %d",
                    debut.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                            java.util.Locale.FRENCH),
                    debut.getYear(),
                    fin.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,
                            java.util.Locale.FRENCH),
                    fin.getYear());
        }
    }

    /**
     * Met à jour l'état des boutons selon la disponibilité d'un rapport
     */
    private void updateButtonStates(boolean enabled) {
        previewButton.setDisable(!enabled);
        imprimerButton.setDisable(!enabled);
        exportPdfButton.setDisable(!enabled);
        exportExcelButton.setDisable(!enabled);
    }

    /**
     * Réinitialise l'interface
     */
    @FXML
    private void handleReinitialiser() {
        // Réinitialiser les sélections
        typeRapportComboBox.getSelectionModel().selectFirst();
        periodeTypeComboBox.getSelectionModel().selectFirst();
        dateDebutPicker.setValue(null);
        dateFinPicker.setValue(null);

        // Réinitialiser les filtres
        bureauFilterComboBox.getSelectionModel().selectFirst();
        serviceFilterComboBox.getSelectionModel().selectFirst();
        includeDetailsCheckBox.setSelected(false);

        // Effacer l'aperçu
        webEngine.loadContent("");
        dernierRapportGenere = null;
        dernierRapportData = null;

        // Réinitialiser l'état
        statusLabel.setText("Prêt");
        updateButtonStates(false);
    }

    /**
     * Affiche l'aide contextuelle
     */
    @FXML
    private void handleAide() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Aide - Génération de Rapports");
        alert.setHeaderText("Comment utiliser le générateur de rapports");
        alert.setContentText(
                "1. Sélectionnez le type de rapport souhaité\n" +
                        "2. Choisissez la période (personnalisée ou prédéfinie)\n" +
                        "3. Appliquez des filtres si nécessaire\n" +
                        "4. Cliquez sur 'Générer' pour créer le rapport\n" +
                        "5. Utilisez les boutons d'export pour sauvegarder\n\n" +
                        "Pour plus d'options, utilisez les rapports avancés."
        );
        alert.showAndWait();
    }
}