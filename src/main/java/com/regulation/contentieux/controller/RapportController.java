package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.enums.TypeRapport;
import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.service.ExportService;
import com.regulation.contentieux.service.PrintService;
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

    // Services
    private final RapportService rapportService = new RapportService();
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

    @FXML
    private void handleGenererRapport() {
        TypeRapport type = typeRapportComboBox.getValue();
        LocalDate dateDebut = dateDebutPicker.getValue();
        LocalDate dateFin = dateFinPicker.getValue();

        // Validation
        if (type == null) {
            AlertUtil.showWarningAlert("Type de rapport requis",
                    "Sélection manquante",
                    "Veuillez sélectionner un type de rapport.");
            return;
        }

        if (dateDebut == null || dateFin == null) {
            AlertUtil.showWarningAlert("Dates requises",
                    "Période incomplète",
                    "Veuillez sélectionner les dates de début et de fin.");
            return;
        }

        if (dateDebut.isAfter(dateFin)) {
            AlertUtil.showWarningAlert("Période invalide",
                    "Dates incorrectes",
                    "La date de début doit être antérieure à la date de fin.");
            return;
        }

        // Lancer la génération
        progressIndicator.setVisible(true);
        statusLabel.setText("Génération du rapport en cours...");
        updateButtonStates(false);

        Task<String> generateTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // Générer selon le type en utilisant les bonnes méthodes
                switch (type) {
                    case REPARTITION_RETROCESSION:
                        return rapportService.genererHtmlEtatRepartition(dateDebut, dateFin);

                    case ETAT_MANDATEMENT:
                        return rapportService.genererEtatMandatement(dateDebut, dateFin);

                    case CENTRE_REPARTITION:
                        return rapportService.genererEtatCentreRepartition(dateDebut, dateFin);

                    case INDICATEURS_REELS:
                        return rapportService.genererEtatIndicateurs(dateDebut, dateFin);

                    case REPARTITION_PRODUIT:
                        return rapportService.genererEtatRepartitionParService(dateDebut, dateFin);

                    case SITUATION_GENERALE:
                        // Générer d'abord les données puis créer le HTML
                        RapportService.SituationGeneraleDTO situationData =
                                rapportService.genererSituationGenerale(dateDebut, dateFin);
                        dernierRapportData = situationData;
                        // Créer le HTML manuellement
                        return genererHtmlSituationGenerale(situationData);

                    case TABLEAU_AMENDES_SERVICE:
                        return rapportService.genererEtatAmendesParService(dateDebut, dateFin);

                    case MANDATEMENT_AGENTS:
                        return rapportService.genererEtatMandatementAgents(dateDebut, dateFin);

                    case ENCAISSEMENTS_PERIODE:
                        // Utiliser la méthode existante
                        RapportService.RapportEncaissementsDTO encaissementsData =
                                rapportService.genererRapportEncaissements(dateDebut, dateFin);
                        dernierRapportData = encaissementsData;
                        return rapportService.genererHtmlEncaissements(encaissementsData);

                    case AFFAIRES_NON_SOLDEES:
                        // Utiliser la méthode existante
                        RapportService.RapportAffairesNonSoldeesDTO nonSoldeesData =
                                rapportService.genererRapportAffairesNonSoldees();
                        dernierRapportData = nonSoldeesData;
                        return rapportService.genererHtmlAffairesNonSoldees(nonSoldeesData);

                    default:
                        throw new UnsupportedOperationException("Type de rapport non supporté: " + type);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Rapport généré avec succès");
                    dernierRapportGenere = getValue();

                    // Afficher automatiquement l'aperçu
                    webEngine.loadContent(dernierRapportGenere);
                    updateButtonStates(true);

                    logger.info("✅ Rapport {} généré avec succès", type.getLibelle());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Erreur lors de la génération");
                    updateButtonStates(false);

                    logger.error("Erreur lors de la génération du rapport", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Génération échouée",
                            "Impossible de générer le rapport: " + getException().getMessage());
                });
            }
        };

        new Thread(generateTask).start();
    }

    /**
     * Génère le HTML pour la situation générale
     */
    private String genererHtmlSituationGenerale(RapportService.SituationGeneraleDTO situation) {
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
                LocalDate.now().toString() + ".pdf");
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

    @FXML
    private void handleExportExcel() {
        if (dernierRapportData == null) {
            AlertUtil.showWarningAlert("Aucun rapport",
                    "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en Excel");
        fileChooser.setInitialFileName("rapport_" +
                LocalDate.now().toString() + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(exportExcelButton.getScene().getWindow());
        if (file != null) {
            boolean success = false;
            TypeRapport type = typeRapportComboBox.getValue();

            // Export selon le type
            if (type == TypeRapport.REPARTITION_RETROCESSION &&
                    dernierRapportData instanceof RapportService.RapportRepartitionDTO) {
                success = exportService.exportRepartitionToExcel(
                        (RapportService.RapportRepartitionDTO) dernierRapportData,
                        file.getAbsolutePath()
                );
            } else if (type == TypeRapport.SITUATION_GENERALE &&
                    dernierRapportData instanceof RapportService.SituationGeneraleDTO) {
                success = exportService.exportSituationToExcel(
                        (RapportService.SituationGeneraleDTO) dernierRapportData,
                        file.getAbsolutePath()
                );
            } else if (type == TypeRapport.TABLEAU_AMENDES_SERVICE &&
                    dernierRapportData instanceof RapportService.TableauAmendesParServicesDTO) {
                success = exportService.exportTableauAmendesToExcel(
                        (RapportService.TableauAmendesParServicesDTO) dernierRapportData,
                        file.getAbsolutePath()
                );
            } else {
                // Export générique
                success = exportService.exportGenericToExcel(
                        dernierRapportData,
                        file.getAbsolutePath()
                );
            }

            if (success) {
                AlertUtil.showInfoAlert("Export réussi",
                        "Export Excel",
                        "Le rapport a été exporté avec succès.");
            } else {
                AlertUtil.showErrorAlert("Export échoué",
                        "Erreur",
                        "Impossible d'exporter le rapport.");
            }
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