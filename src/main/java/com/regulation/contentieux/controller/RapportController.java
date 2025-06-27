package com.regulation.contentieux.controller;

import com.regulation.contentieux.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import com.regulation.contentieux.model.Agent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Collection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import com.regulation.contentieux.service.RapportHtmlBuilder;
import javafx.scene.control.Tooltip;

import java.time.format.DateTimeFormatter;
import java.awt.Desktop;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

import com.regulation.contentieux.dao.ContraventionDAO;
import javafx.scene.layout.HBox;
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
import javafx.scene.text.TextAlignment;
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
    @FXML private HBox periodePersonnaliseeBox;
    @FXML private HBox periodeMensuelleBox;

    // === NOUVEAUX COMPOSANTS FXML ===
    @FXML private TableView<Object> resultatsTableView;
    @FXML private Label tableauTitreLabel;
    @FXML private Label nombreResultatsLabel;
    @FXML private Label derniereMajLabel;
    @FXML private Label statusFooterLabel;

    // Nouveaux boutons
    @FXML private Button reinitialiserButton;
    @FXML private Button selectionnerToutButton;
    @FXML private Button deselectionnerToutButton;
    @FXML private Button helpButton;

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

    // CORRECTION : Variable webView manquante
    @FXML private WebView webView;

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

    // CORRECTION : Variables pour la gestion des dates
    private LocalDate dateDebut;
    private LocalDate dateFin;

    // CORRECTION : Formateur de devises
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    // Services
    private RapportService rapportService;
    private final ExportService exportService = new ExportService();
    private final PrintService printService = new PrintService();

    // État
    private TypeRapport dernierTypeRapport;
    @FXML private Button exportPDFButton;
    private WebEngine webEngine;
    private String dernierRapportGenere;
    private Object dernierRapportData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // CORRECTION : Initialiser RapportService avec gestion d'erreurs
        try {
            ContraventionDAO contraventionDAO = new ContraventionDAO();
            this.rapportService = new RapportService(contraventionDAO);
        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation des services", e);
            // Fallback : utiliser un constructeur par défaut si disponible
            this.rapportService = new RapportService();
        }

        if (webView != null) {
            webEngine = webView.getEngine();
        }

        if (previewWebView != null && webEngine == null) {
            webEngine = previewWebView.getEngine();
        }

        initializeTypeRapport();     // DOIT être AVANT configureTableViewInitial
        initializePeriode();
        initializeFiltres();
        setupEventHandlers();

        // Configuration de la TableView (APRÈS initializeTypeRapport)
        configureTableViewInitial();
        setupNewEventHandlers();

        // État initial
        if (progressIndicator != null) {
            progressIndicator.setVisible(false);
        }
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
                if (string == null || string.trim().isEmpty()) {
                    return null;
                }
                try {
                    return TypeRapport.fromLibelle(string);
                } catch (IllegalArgumentException e) {
                    logger.warn("Type de rapport inconnu: {}", string);
                    return null;
                }
            }
        });

        // CORRECTION : Gestionnaire de changement avec chargement automatique ET mise à jour aperçu
        typeRapportComboBox.setOnAction(e -> {
            TypeRapport selected = typeRapportComboBox.getValue();
            if (selected != null) {
                // Mettre à jour la description
                if (descriptionLabel != null) {
                    descriptionLabel.setText(selected.getDescription());
                }

                // Configurer les colonnes
                configureTableViewForReport(selected);

                // CORRECTION : Charger automatiquement les données ET l'aperçu
                chargerDonneesAutomatiquement(selected);

                logger.debug("Type de rapport changé: {}", selected.getLibelle());
            }
        });

        // Sélection par défaut
        typeRapportComboBox.getSelectionModel().selectFirst();
    }


    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Gestionnaire pour le changement de type de rapport
        if (typeRapportComboBox != null) {
            typeRapportComboBox.setOnAction(e -> handleTypeRapportChanged());
        }

        // Gestionnaires pour les dates
        if (dateDebutPicker != null) {
            dateDebutPicker.setOnAction(e -> validateDateRange());
        }
        if (dateFinPicker != null) {
            dateFinPicker.setOnAction(e -> validateDateRange());
        }

        // Gestionnaires pour les boutons d'action
        if (genererButton != null) {
            genererButton.setOnAction(e -> handleGenererRapport());
        }
        if (previewButton != null) {
            previewButton.setOnAction(e -> handlePreviewRapport());
        }
        if (imprimerButton != null) {
            imprimerButton.setOnAction(e -> handleImprimer());
        }
        if (exportPdfButton != null) {
            exportPdfButton.setOnAction(e -> handleExportPdf());
        }
        if (exportExcelButton != null) {
            exportExcelButton.setOnAction(e -> handleExportExcel());
        }

        logger.debug("Gestionnaires d'événements configurés");
    }

    /**
     * Gestionnaire pour le changement de type de rapport
     */
    private void handleTypeRapportChanged() {
        TypeRapport typeSelectionne = typeRapportComboBox.getValue();

        if (typeSelectionne != null) {
            // Mettre à jour la description
            if (descriptionLabel != null) {
                descriptionLabel.setText(typeSelectionne.getDescription());
            }

            // Configurer la TableView selon le type
            configureTableViewForReport(typeSelectionne);

            // CORRECTION : Éviter le double chargement + meilleur état
            if (!typeSelectionne.equals(dernierTypeRapport)) {
                chargerDonneesAutomatiquement(typeSelectionne);
                dernierTypeRapport = typeSelectionne;
            }

            logger.debug("Type de rapport changé: {}", typeSelectionne.getLibelle());
        }
    }

    /**
     * CORRECTION PROBLÈME 2 : Chargement automatique des données au changement de template
     */
    private void chargerDonneesAutomatiquement(TypeRapport typeRapport) {
        if (typeRapport == null) {
            return;
        }

        // Afficher l'indicateur de chargement
        showProgressIndicator(true, "Chargement des données pour " + typeRapport.getLibelle() + "...");

        // Dates par défaut (mois courant)
        LocalDate debut = getDateDebut();
        LocalDate fin = getDateFin();

        // Si pas de dates sélectionnées, utiliser le mois courant
        if (debut == null || fin == null) {
            LocalDate now = LocalDate.now();
            debut = now.withDayOfMonth(1);
            fin = now.withDayOfMonth(now.lengthOfMonth());

            // Mettre à jour les DatePickers
            if (dateDebutPicker != null) dateDebutPicker.setValue(debut);
            if (dateFinPicker != null) dateFinPicker.setValue(fin);
        }

        // Charger les données en arrière-plan
        final LocalDate finalDebut = debut;
        final LocalDate finalFin = fin;

        Task<Object> task = new Task<Object>() {
            @Override
            protected Object call() throws Exception {
                logger.debug("🔄 Chargement automatique des données pour: {}", typeRapport.getLibelle());
                return genererRapportParType(typeRapport, finalDebut, finalFin);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    try {
                        Object donnees = getValue();
                        logger.debug("📦 Données automatiques chargées: {}",
                                donnees != null ? donnees.getClass().getSimpleName() : "NULL");

                        // Mettre à jour la TableView
                        updateTableViewData(donnees);
                        dernierRapportData = donnees;

                        // CORRECTION : Générer et mettre à jour l'aperçu HTML automatiquement
                        genererApercuHtmlAutomatique(typeRapport, finalDebut, finalFin, donnees);

                        showProgressIndicator(false, "");

                        if (statusLabel != null) {
                            statusLabel.setText("Données et aperçu chargés automatiquement");
                        }

                        // Mettre à jour l'état des boutons
                        updateButtonStates(true);

                    } catch (Exception e) {
                        logger.error("Erreur lors de la mise à jour automatique", e);
                        showProgressIndicator(false, "");
                        if (statusLabel != null) {
                            statusLabel.setText("Erreur lors du chargement automatique");
                        }
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Échec du chargement automatique", getException());
                    showProgressIndicator(false, "");
                    if (statusLabel != null) {
                        statusLabel.setText("Erreur: " + getException().getMessage());
                    }
                });
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * CORRECTION : Génération automatique de l'aperçu HTML
     */
    private void genererApercuHtmlAutomatique(TypeRapport typeRapport, LocalDate debut, LocalDate fin, Object donnees) {
        try {
            // Générer le HTML via RapportService
            String html = genererHtmlViaRapportService(typeRapport, debut, fin);

            // Mettre à jour l'aperçu
            if (webEngine != null) {
                webEngine.loadContent(html);
                dernierRapportGenere = html;
            }

            logger.debug("✅ Aperçu HTML généré automatiquement pour {}", typeRapport.getLibelle());

        } catch (Exception e) {
            logger.error("Erreur génération aperçu HTML automatique", e);

            // Afficher un message d'erreur dans l'aperçu
            String errorHtml = genererHtmlErreur(typeRapport, e);
            if (webEngine != null) {
                webEngine.loadContent(errorHtml);
            }
        }
    }


    /**
     * Génération d'aperçu HTML automatique
     */
    private void genererApercuHtml(TypeRapport typeRapport, LocalDate debut, LocalDate fin, Object donnees) {
        try {
            // Générer le HTML via RapportService
            String html = genererHtmlViaRapportService(typeRapport, debut, fin);

            // Mettre à jour l'aperçu
            if (webEngine != null) {
                webEngine.loadContent(html);
                dernierRapportGenere = html;
            }

            logger.debug("✅ Aperçu HTML généré automatiquement pour {}", typeRapport.getLibelle());

        } catch (Exception e) {
            logger.error("Erreur génération aperçu HTML automatique", e);

            // Afficher un message d'erreur dans l'aperçu
            String errorHtml = genererHtmlErreur(typeRapport, e);
            if (webEngine != null) {
                webEngine.loadContent(errorHtml);
            }
        }
    }

    /**
     * NOUVEAU : Génération HTML via les méthodes existantes du RapportService
     */
    private String genererHtmlViaRapportService(TypeRapport typeRapport, LocalDate debut, LocalDate fin) {
        try {
            switch (typeRapport) {
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
                default:
                    // Fallback : HTML basique pour les types non supportés
                    return genererHtmlBasique(typeRapport, debut, fin, null);
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la génération HTML via RapportService pour {}", typeRapport, e);
            return genererHtmlBasique(typeRapport, debut, fin, null);
        }
    }

    /**
     * CORRECTION PROBLÈME 3 : Génération HTML de base en cas d'échec du template engine
     */
    private String genererHtmlBasique(TypeRapport typeRapport, LocalDate debut, LocalDate fin, Object donnees) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<title>").append(typeRapport.getLibelle()).append("</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        html.append("h1 { text-align: center; color: #333; }");
        html.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; font-weight: bold; }");
        html.append(".info { background-color: #e7f3ff; padding: 15px; border-radius: 5px; margin: 10px 0; }");
        html.append("</style></head><body>");

        html.append("<h1>").append(typeRapport.getLibelle()).append("</h1>");
        html.append("<div class='info'>");
        html.append("<p><strong>Période :</strong> ").append(debut).append(" au ").append(fin).append("</p>");
        html.append("<p><strong>Template :</strong> ").append(typeRapport.getNumeroTemplate()).append("</p>");
        html.append("<p><strong>Données :</strong> ").append(donnees != null ? "Chargées" : "Aucune donnée").append("</p>");
        html.append("</div>");

        if (donnees != null) {
            html.append("<div class='info'>");
            html.append("<p><strong>Type de données :</strong> ").append(donnees.getClass().getSimpleName()).append("</p>");
            html.append("<p><strong>Contenu :</strong> ").append(donnees.toString()).append("</p>");
            html.append("</div>");
        } else {
            html.append("<div class='info' style='background-color: #fff3cd;'>");
            html.append("<p><strong>⚠️ Remarque :</strong> Aucune donnée disponible pour cette période.</p>");
            html.append("<p>Cela peut être normal si aucune affaire n'a été traitée pendant cette période.</p>");
            html.append("</div>");
        }

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * CORRECTION PROBLÈME 3 : HTML d'erreur informatif
     */
    private String genererHtmlErreur(TypeRapport typeRapport, Exception e) {
        return String.format("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset='UTF-8'>
            <title>Erreur - %s</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                .error { background-color: #f8d7da; color: #721c24; padding: 15px; border-radius: 5px; border: 1px solid #f5c6cb; }
                .info { background-color: #d1ecf1; color: #0c5460; padding: 15px; border-radius: 5px; border: 1px solid #bee5eb; margin-top: 10px; }
            </style>
        </head>
        <body>
            <h1>%s</h1>
            <div class='error'>
                <h3>❌ Erreur lors de la génération</h3>
                <p><strong>Erreur :</strong> %s</p>
            </div>
            <div class='info'>
                <h3>ℹ️ Informations</h3>
                <p>Cette erreur peut se produire si :</p>
                <ul>
                    <li>Le template n'est pas encore complètement implémenté</li>
                    <li>Aucune donnée n'est disponible pour la période sélectionnée</li>
                    <li>Il y a un problème de connexion à la base de données</li>
                </ul>
                <p><strong>Solution :</strong> Essayez de sélectionner un autre template ou une autre période.</p>
            </div>
        </body>
        </html>
        """, typeRapport.getLibelle(), typeRapport.getLibelle(), e.getMessage());
    }

    /**
     * Gestionnaire pour l'export PDF
     */
    @FXML
    private void handleExportPdf() {
        if (dernierRapportData == null) {
            AlertUtil.showWarningAlert("Aucun rapport", "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en PDF");
        fileChooser.setInitialFileName("rapport_" + LocalDate.now() + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        Stage stage = (Stage) exportPdfButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try {
                boolean success = exportService.exportToPdf(dernierRapportGenere, file.getAbsolutePath());
                if (success) {
                    AlertUtil.showSuccess("Export réussi", "Le rapport a été exporté en PDF avec succès.");
                } else {
                    AlertUtil.showErrorAlert("Export échoué", "Erreur d'export",
                            "Impossible d'exporter le rapport en PDF.");
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'export PDF", e);
                AlertUtil.showErrorAlert("Erreur d'export", "Impossible d'exporter en PDF", e.getMessage());
            }
        }
    }

    /**
     * Validation de la plage de dates
     */
    private void validateDateRange() {
        LocalDate debut = dateDebutPicker.getValue();
        LocalDate fin = dateFinPicker.getValue();

        if (debut != null && fin != null) {
            if (debut.isAfter(fin)) {
                AlertUtil.showWarningAlert("Dates invalides",
                        "Date de début postérieure à la date de fin",
                        "Veuillez corriger les dates sélectionnées");

                // Réinitialiser la date de fin
                dateFinPicker.setValue(debut);
            }
        }
    }

    /**
     * CORRECTION BUG : Initialisation des services avec dépendances
     */
    private void initializeServices() {
        try {
            ContraventionDAO contraventionDAO = new ContraventionDAO();
            this.rapportService = new RapportService(contraventionDAO);
        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation des services", e);
            // Fallback : utiliser un constructeur sans paramètre si disponible
            this.rapportService = new RapportService();
        }
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

    /**
     * ENRICHISSEMENT : Export PDF avec support de tous les types
     */
    @FXML
    private void handleExportPDF() {
        if (dernierRapportGenere == null || dernierRapportGenere.isEmpty()) {
            AlertUtil.showWarningAlert("Aucun rapport", "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en PDF");
        fileChooser.setInitialFileName("rapport_" + LocalDate.now() + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(exportPdfButton.getScene().getWindow());
        if (file != null) {
            try {
                // Utiliser la méthode existante avec signature (String, String, String)
                File result = exportService.exportReportToPDF(dernierRapportGenere, "Rapport", file.getName());
                if (result != null) {
                    AlertUtil.showSuccess("Export réussi", "Le rapport a été exporté en PDF avec succès.");
                } else {
                    AlertUtil.showErrorAlert("Export échoué", "Erreur d'export",
                            "Impossible d'exporter le rapport en PDF.");
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'export PDF", e);
                AlertUtil.showErrorAlert("Erreur d'export", "Impossible d'exporter en PDF", e.getMessage());
            }
        }
    }

    /**
     * Gestionnaire pour l'aperçu du rapport
     */
    @FXML
    private void handlePreviewRapport() {
        // CORRECTION : Vérifier si on a des données chargées automatiquement
        if (dernierRapportGenere == null && dernierRapportData != null) {
            // Générer l'HTML à partir des données disponibles
            TypeRapport typeSelectionne = typeRapportComboBox.getValue();
            LocalDate debut = getDateDebut();
            LocalDate fin = getDateFin();

            if (typeSelectionne != null) {
                try {
                    // Générer l'HTML à la volée
                    dernierRapportGenere = genererHtmlViaRapportService(typeSelectionne, debut, fin);
                    logger.debug("✅ HTML généré à la volée pour l'aperçu");
                } catch (Exception e) {
                    logger.error("Erreur génération HTML à la volée", e);
                    dernierRapportGenere = genererHtmlBasique(typeSelectionne, debut, fin, dernierRapportData);
                }
            }
        }

        // Vérification finale
        if (dernierRapportGenere == null) {
            // AMÉLIORATION : Message plus informatif
            AlertUtil.showWarningAlert("Aucun rapport",
                    "Aperçu impossible",
                    "Aucune donnée disponible. Sélectionnez un type de rapport et une période valide.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/rapport-preview-dialog.fxml"));
            Parent root = loader.load();

            RapportPreviewController controller = loader.getController();
            controller.setRapportContent(dernierRapportGenere);

            Stage previewStage = new Stage();
            previewStage.setTitle("Aperçu du rapport");
            previewStage.initModality(Modality.APPLICATION_MODAL);
            previewStage.setScene(new Scene(root));
            previewStage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture de l'aperçu", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible d'ouvrir l'aperçu", e.getMessage());
        }
    }

    private void diagnosticTableView() {
        if (resultatsTableView == null) {
            logger.error("❌ resultatsTableView est NULL");
            return;
        }

        logger.debug("🔍 === DIAGNOSTIC TABLEVIEW ===");
        logger.debug("- Visible: {}", resultatsTableView.isVisible());
        logger.debug("- Managed: {}", resultatsTableView.isManaged());
        logger.debug("- Width: {}", resultatsTableView.getWidth());
        logger.debug("- Height: {}", resultatsTableView.getHeight());
        logger.debug("- Colonnes: {}", resultatsTableView.getColumns().size());
        logger.debug("- Items: {}", resultatsTableView.getItems() != null ? resultatsTableView.getItems().size() : "NULL");

        if (resultatsTableView.getItems() != null && !resultatsTableView.getItems().isEmpty()) {
            logger.debug("- Premier item: {}", resultatsTableView.getItems().get(0).getClass().getSimpleName());
        }

        if (!resultatsTableView.getColumns().isEmpty()) {
            logger.debug("- Première colonne: {}", resultatsTableView.getColumns().get(0).getText());
        }

        logger.debug("🔍 === FIN DIAGNOSTIC ===");
    }

    /**
     * CORRECTION BUG : Forcer la mise à jour de la TableView
     */
    @FXML
    private void handleActualiser() {
        logger.debug("🔄 Actualisation forcée de la TableView");

        if (dernierRapportData != null) {
            logger.debug("🔧 Re-application des dernières données...");
            updateTableViewData(dernierRapportData);
            diagnosticTableView();
        } else {
            logger.debug("⚠️ Aucune donnée précédente à ré-appliquer");
            AlertUtil.showInfoAlert("Actualisation", "Aucune donnée",
                    "Aucune donnée précédente à actualiser. Générez d'abord un rapport.");
        }
    }

    /**
     * CORRECTION BUG : Test de la TableView avec données fictives
     */
    @FXML
    private void handleTestTableView() {
        logger.debug("🧪 Test de la TableView avec données fictives");

        // Créer des données de test
        ObservableList<Object> testData = FXCollections.observableArrayList();
        testData.add("Test Item 1");
        testData.add("Test Item 2");
        testData.add("Test Item 3");

        // Configurer colonnes si nécessaire
        if (resultatsTableView.getColumns().isEmpty()) {
            configureColumnsGeneric();
        }

        // Appliquer les données de test
        Platform.runLater(() -> {
            resultatsTableView.setItems(testData);
            resultatsTableView.refresh();
            updateNombreResultats(testData.size());

            logger.debug("✅ Données de test appliquées: {} éléments", testData.size());
            diagnosticTableView();
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        Platform.runLater(() -> {
            if (genererButton != null) genererButton.setDisable(!enabled);
            // BUG 7 FIX : Remplacer exporterButton par les bonnes variables
            if (exportPdfButton != null) exportPdfButton.setDisable(!enabled);
            if (exportExcelButton != null) exportExcelButton.setDisable(!enabled);
            if (imprimerButton != null) imprimerButton.setDisable(!enabled);
            if (previewButton != null) previewButton.setDisable(!enabled);
        });
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
            if (statusFooterLabel != null) {
                statusFooterLabel.setText(message);
            }
        });
    }

    private void configurerFiltresSelonType(TypeRapport type) {
        // Afficher/masquer les filtres selon le type

        boolean showServiceFilter = type == TypeRapport.TABLEAU_AMENDES_SERVICE ||
                type == TypeRapport.MANDATEMENT_AGENTS;

        serviceFilterComboBox.setVisible(showServiceFilter);
        serviceFilterComboBox.setManaged(showServiceFilter);
    }

    private void configurerPeriodeSelonType(String typePeriode) {
        boolean isPersonnalisee = "Personnalisée".equals(typePeriode);

        periodePersonnaliseeBox.setVisible(isPersonnalisee);
        periodeMensuelleBox.setManaged(isPersonnalisee);

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
     * CORRECTION BUG : Méthode manquante getDateDebut()
     */
    private LocalDate getDateDebut() {
        if (dateDebutPicker != null && dateDebutPicker.getValue() != null) {
            return dateDebutPicker.getValue();
        }
        // Fallback : début du mois courant
        return LocalDate.now().withDayOfMonth(1);
    }

    /**
     * CORRECTION BUG : Méthode manquante getDateFin()
     */
    private LocalDate getDateFin() {
        if (dateFinPicker != null && dateFinPicker.getValue() != null) {
            return dateFinPicker.getValue();
        }
        // Fallback : fin du mois courant
        LocalDate now = LocalDate.now();
        return now.withDayOfMonth(now.lengthOfMonth());
    }

    /**
     * ENRICHISSEMENT : Génération avec support de tous les templates
     */
    @FXML
    private void handleGenererRapport() {
        logger.debug("🎯 === DÉBUT GÉNÉRATION RAPPORT ===");

        // Validation des paramètres
        TypeRapport typeSelectionne = typeRapportComboBox.getValue();
        if (typeSelectionne == null) {
            AlertUtil.showWarningAlert("Paramètres manquants",
                    "Type de rapport requis",
                    "Veuillez sélectionner un type de rapport");
            return;
        }

        LocalDate dateDebut = dateDebutPicker.getValue();
        LocalDate dateFin = dateFinPicker.getValue();

        if (dateDebut == null || dateFin == null) {
            AlertUtil.showWarningAlert("Paramètres manquants",
                    "Période requise",
                    "Veuillez sélectionner une période");
            return;
        }

        logger.debug("📊 Génération rapport: {}", typeSelectionne.getLibelle()); // BUG 5 FIX
        logger.debug("📅 Période: {} - {}", dateDebut, dateFin);

        // Désactiver les boutons pendant la génération
        setButtonsEnabled(false);
        updateStatus("Génération en cours...");

        // Configuration des colonnes AVANT la génération
        configureTableViewForReport(typeSelectionne);

        // Génération asynchrone
        Task<Object> task = new Task<Object>() {
            @Override
            protected Object call() throws Exception {
                logger.debug("🔄 Début génération asynchrone...");
                Object resultData = genererRapportParType(typeSelectionne, dateDebut, dateFin);
                logger.debug("✅ Données générées: {}", resultData != null ? resultData.getClass().getSimpleName() : "NULL");
                return resultData;
            }
        };

        task.setOnSucceeded(event -> {
            logger.debug("🎉 Génération réussie");

            try {
                Object rapportData = task.getValue();
                logger.debug("📦 Données récupérées: {}", rapportData != null ? rapportData.getClass().getSimpleName() : "NULL");

                if (rapportData != null) {
                    updateTableViewData(rapportData);
                    dernierRapportData = rapportData;
                    dernierTypeRapport = typeSelectionne;
                    dernierRapportGenere = genererHtmlParType(typeSelectionne, dateDebut, dateFin, rapportData);

                    updateStatus("Rapport généré avec succès");
                    updateButtonStates(true);
                    AlertUtil.showSuccess("Rapport généré", "Le rapport a été généré avec succès.");
                } else {
                    logger.warn("⚠️ Données de rapport nulles");
                    updateStatus("Aucune donnée trouvée");
                    updateTableViewData(null);
                    AlertUtil.showWarningAlert("Données", "Aucune donnée",
                            "Aucune donnée trouvée pour la période sélectionnée");
                }

            } catch (Exception e) {
                logger.error("❌ Erreur lors du traitement des données", e);
                updateStatus("Erreur lors du traitement");
                AlertUtil.showErrorAlert("Erreur", "Erreur de traitement",
                        "Erreur lors du traitement des données: " + e.getMessage());
            } finally {
                setButtonsEnabled(true);
            }
        });

        task.setOnFailed(event -> {
            logger.error("❌ Échec génération rapport", task.getException());
            setButtonsEnabled(true);
            updateStatus("Erreur lors de la génération");

            Throwable exception = task.getException();
            AlertUtil.showErrorAlert("Erreur", "Erreur de génération",
                    "Erreur lors de la génération du rapport: " +
                            (exception != null ? exception.getMessage() : ""));
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
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

                default:
                    return genererHtmlGenerique(type, rapportData, debut, fin);
            }

        } catch (Exception e) {
            logger.error("Erreur lors de la génération HTML pour {}", type, e);
            return genererHtmlErreur(type, e);
        }
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
                // Template 1
                case ETAT_REPARTITION_AFFAIRES:
                    return rapportService.genererDonneesEtatRepartitionAffaires(debut, fin);

                // Template 2
                case ETAT_MANDATEMENT:
                    return rapportService.genererDonneesEtatMandatement(debut, fin);

                // Template 3
                case CENTRE_REPARTITION:
                    return rapportService.genererDonneesCentreRepartition(debut, fin);

                // Template 4
                case INDICATEURS_REELS:
                    return rapportService.genererDonneesIndicateursReels(debut, fin);

                // Template 5
                case REPARTITION_PRODUIT:
                    return rapportService.genererDonneesRepartitionProduit(debut, fin);

                // Template 6 - CORRECTION : Utiliser la méthode EXISTANTE
                case ETAT_CUMULE_AGENT:
                    return rapportService.genererDonneesEtatCumuleParAgent(debut, fin);

                // Template 7 - CORRECTION : Utiliser la méthode EXISTANTE
                case TABLEAU_AMENDES_SERVICE:
                    return rapportService.genererDonneesTableauAmendesParServices(debut, fin);

                // Template 8 - CORRECTION : Utiliser la méthode EXISTANTE
                case MANDATEMENT_AGENTS:
                    return rapportService.genererDonneesMandatementAgents(debut, fin);

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
            String titre = dernierTypeRapport != null ?
                    dernierTypeRapport.getLibelle() : "Rapport";
            ouvrirPopupApercu(dernierRapportGenere, titre, dernierRapportData);
        } else {
            AlertUtil.showWarningAlert("Aucun rapport", "Aperçu impossible",
                    "Veuillez d'abord générer un rapport.");
        }
    }

    /**
     * ENRICHISSEMENT : Export Excel avec support de tous les types
     */
    @FXML
    private void handleExportExcel() {
        if (dernierRapportData == null) {
            AlertUtil.showWarningAlert("Aucun rapport", "Export impossible",
                    "Veuillez d'abord générer un rapport.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en Excel");
        fileChooser.setInitialFileName("rapport_" + LocalDate.now() + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(exportExcelButton.getScene().getWindow());
        if (file != null) {
            try {
                boolean success = exportService.exportGenericToExcel(dernierRapportData, file.getAbsolutePath());
                if (success) {
                    AlertUtil.showSuccess("Export réussi", "Le rapport a été exporté en Excel avec succès.");
                } else {
                    AlertUtil.showErrorAlert("Export échoué", "Erreur d'export",
                            "Impossible d'exporter le rapport en Excel.");
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'export Excel", e);
                AlertUtil.showErrorAlert("Erreur d'export", "Impossible d'exporter en Excel", e.getMessage());
            }
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
                File result = exportService.exportReportToPDF(htmlContent, outputPath, type.getLibelle());
                return result != null;
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
     * CORRECTION BUG : Méthode manquante showProgressIndicator()
     */
    private void showProgressIndicator(boolean show, String message) {
        Platform.runLater(() -> {
            if (progressIndicator != null) {
                progressIndicator.setVisible(show);
            }
            if (statusLabel != null && message != null && !message.isEmpty()) {
                statusLabel.setText(message);
            }
        });
    }

    /**
     * CORRECTION BUG : Méthode manquante activerBoutonsExport()
     */
    private void activerBoutonsExport(boolean activer) {
        Platform.runLater(() -> {
            if (exportPdfButton != null) {
                exportPdfButton.setDisable(!activer);
            }
            if (exportPDFButton != null) {
                exportPDFButton.setDisable(!activer);
            }
            if (exportExcelButton != null) {
                exportExcelButton.setDisable(!activer);
            }
            if (imprimerButton != null) {
                imprimerButton.setDisable(!activer);
            }
        });
    }

// ==================== MÉTHODES DE FORMATAGE ====================


    /**
     * CORRECTION BUG : Méthode manquante formatMontant()
     */
    private String formatMontant(BigDecimal montant) {
        if (montant == null) {
            return ""; // Changement : chaîne vide au lieu de "0"
        }

        if (montant.compareTo(BigDecimal.ZERO) == 0) {
            return ""; // Changement : chaîne vide au lieu de "0" pour les zéros aussi
        }

        DecimalFormat formatter = new DecimalFormat("#,##0.00");
        return formatter.format(montant);
    }

// ==================== MÉTHODES DE GÉNÉRATION HTML ====================

    /**
     * CORRECTION BUG : Méthode manquante genererEnTeteHTML()
     */
    private String genererEnTeteHTML(String titreRapport, LocalDate dateDebut, LocalDate dateFin) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        return String.format("""
        <div class="rapport-header">
            <h1>%s</h1>
            <p class="periode">Période : du %s au %s</p>
            <p class="generation">Généré le : %s</p>
        </div>
        """,
                titreRapport,
                dateDebut.format(formatter),
                dateFin.format(formatter),
                LocalDate.now().format(formatter)
        );
    }

    /**
     * CORRECTION BUG : Méthode manquante genererPiedHTML()
     */
    private String genererPiedHTML() {
        return """
        <div class="rapport-footer">
            <p>Application de Gestion des Affaires Contentieuses</p>
            <p>Rapport généré automatiquement</p>
        </div>
        """;
    }

    /**
     * CORRECTION BUG : Méthode manquante convertirSituationVersHTML()
     */
    private String convertirSituationVersHTML(SituationGeneraleDTO situation, LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder html = new StringBuilder();

        html.append(genererEnTeteHTML("Situation Générale", dateDebut, dateFin));

        html.append("<div class='situation-content'>");
        html.append("<h2>Résumé de la Situation</h2>");

        if (situation != null) {
            html.append("<table class='rapport-table'>");
            html.append("<tr><th>Indicateur</th><th>Valeur</th></tr>");
            html.append("<tr><td>Total Encaissements</td><td>").append(formatMontant(situation.getTotalEncaissements())).append("</td></tr>");
            html.append("<tr><td>Nombre d'Affaires</td><td>").append(situation.getNombreAffaires()).append("</td></tr>");
            html.append("<tr><td>Affaires Soldées</td><td>").append(situation.getAffairesSoldees()).append("</td></tr>");
            html.append("</table>");
        } else {
            html.append("<p>Aucune donnée disponible pour la période sélectionnée.</p>");
        }

        html.append("</div>");
        html.append(genererPiedHTML());

        return html.toString();
    }

// ==================== MÉTHODES D'OUVERTURE DE FICHIERS ====================

    /**
     * CORRECTION BUG : Méthode manquante ouvrirFichier()
     */
    private void ouvrirFichier(File fichier) {
        if (fichier == null || !fichier.exists()) {
            AlertUtil.showError("Erreur", "Le fichier n'existe pas.");
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(fichier);
            } else {
                // Alternative pour les systèmes sans Desktop support
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", fichier.getAbsolutePath());
                pb.start();
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'ouverture du fichier: {}", fichier.getAbsolutePath(), e);
            AlertUtil.showError("Erreur", "Impossible d'ouvrir le fichier : " + e.getMessage());
        }
    }

// ==================== CORRECTIONS DE SIGNATURE DE MÉTHODES ====================

    /**
     * CORRECTION BUG : Appel avec paramètres corrects
     */
    @FXML
    private void handleGenererRapportAffairesNonSoldees() {
        try {
            showProgressIndicator(true, "Génération du rapport des affaires non soldées...");

            Task<String> task = new Task<String>() {
                @Override
                protected String call() throws Exception {
                    // CORRECTION : Appel sans paramètres selon la signature existante
                    var rapport = rapportService.genererRapportAffairesNonSoldees();
                    return convertirRapportVersHTML(rapport);
                }
            };

            task.setOnSucceeded(e -> {
                String htmlContent = task.getValue();
                if (webView != null) {
                    webView.getEngine().loadContent(htmlContent);
                }
                showProgressIndicator(false, "Rapport généré avec succès");
                activerBoutonsExport(true);
            });

            task.setOnFailed(e -> {
                showProgressIndicator(false, "Erreur lors de la génération");
                AlertUtil.showError("Erreur", "Impossible de générer le rapport : " + task.getException().getMessage());
            });

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            logger.error("Erreur lors de la génération du rapport", e);
            showProgressIndicator(false, "");
            AlertUtil.showError("Erreur", "Erreur lors de la génération : " + e.getMessage());
        }
    }

    /**
     * CORRECTION BUG : Méthode utilitaire pour convertir les rapports vers HTML
     */
    private String convertirRapportVersHTML(Object rapport) {
        if (rapport == null) {
            return "<html><body><h1>Aucune donnée disponible</h1></body></html>";
        }

        // Conversion basique - à enrichir selon le type de rapport
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Rapport</title></head><body>");
        html.append(genererEnTeteHTML("Rapport", getDateDebut(), getDateFin()));
        html.append("<div>").append(rapport.toString()).append("</div>");
        html.append(genererPiedHTML());
        html.append("</body></html>");

        return html.toString();
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
     * CORRECTION : Méthode utilitaire manquante pour extraire les valeurs des champs via réflexion
     */
    private Object getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;

        try {
            // Essayer d'abord avec getter
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (Exception e) {
            // Fallback : accès direct au champ
            try {
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ex) {
                logger.debug("Impossible d'extraire le champ {}: {}", fieldName, ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Met à jour l'état des boutons selon la disponibilité d'un rapport
     */
    private void updateButtonStates(boolean hasReport) {
        Platform.runLater(() -> {
            if (previewButton != null) {
                previewButton.setDisable(!hasReport);
            }
            if (imprimerButton != null) {
                imprimerButton.setDisable(!hasReport);
            }
            if (exportPdfButton != null) {
                exportPdfButton.setDisable(!hasReport);
            }
            if (exportExcelButton != null) {
                exportExcelButton.setDisable(!hasReport);
            }
        });
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
        if (webEngine != null) {
            webEngine.loadContent("");
        }
        dernierRapportGenere = null;
        dernierRapportData = null;

        // Réinitialiser l'état
        if (statusLabel != null) {
            statusLabel.setText("Prêt");
        }
        updateButtonStates(false);
    }

    /**
     * Configuration initiale de la TableView
     */
    private void configureTableViewInitial() {
        if (resultatsTableView != null) {
            resultatsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            resultatsTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            // Configuration par défaut sans TypeRapport
            configureColumnsGeneric();

            // Placeholder par défaut
            if (tableauTitreLabel != null) {
                tableauTitreLabel.setText("Sélectionnez un type de rapport");
            }
        }
    }

    /**
     * Configuration des nouveaux gestionnaires d'événements
     */
    private void setupNewEventHandlers() {
        if (reinitialiserButton != null) {
            reinitialiserButton.setOnAction(e -> handleReinitialiser());
        }
        if (selectionnerToutButton != null) {
            selectionnerToutButton.setOnAction(e -> handleSelectionnerTout());
        }
        if (deselectionnerToutButton != null) {
            deselectionnerToutButton.setOnAction(e -> handleDeselectionnerTout());
        }
        if (helpButton != null) {
            helpButton.setOnAction(e -> handleAideRapport());
        }
    }

    /**
     * Configure la TableView selon le type de rapport sélectionné
     */
    private void configureTableViewForReport(TypeRapport typeRapport) {
        if (resultatsTableView == null) {
            logger.debug("resultatsTableView est null, skip configuration");
            return;
        }

        if (typeRapport == null) {
            logger.debug("typeRapport est null, configuration par défaut");
            configureColumnsGeneric();
            updateTableauTitre(null);
            return;
        }

        // Effacer les colonnes existantes
        resultatsTableView.getColumns().clear();

        try {
            switch (typeRapport) {
                case ETAT_REPARTITION_AFFAIRES:
                    configureColumnsRepartitionAffaires();
                    break;
                case ETAT_MANDATEMENT:
                    configureColumnsEtatMandatement();
                    break;
                case CENTRE_REPARTITION: // AJOUT Template 3
                    configureColumnsCentreRepartition();
                    break;
                case INDICATEURS_REELS: // AJOUT Template 4
                    configureColumnsIndicateursReels();
                    break;
                case REPARTITION_PRODUIT: // AJOUT Template 5
                    configureColumnsRepartitionProduit();
                    break;
                case ETAT_CUMULE_AGENT: // AJOUT Template 6
                    configureColumnsEtatCumuleAgent();
                    break;
                case TABLEAU_AMENDES_SERVICE:
                    configureColumnsAmendesServices();
                    break;
                case MANDATEMENT_AGENTS: // AJOUT Template 8
                    configureColumnsMandatementAgents();
                    break;
                default:
                    configureColumnsGeneric();
                    break;
            }
            updateTableauTitre(typeRapport);
        } catch (Exception e) {
            logger.error("Erreur lors de la configuration des colonnes", e);
            configureColumnsGeneric();
        }
    }

    /**
     * Configure les colonnes pour le Template 2 : État par séries de mandatement
     */
    /**
     * CORRECTION BUG 2 : Configure les colonnes pour le Template 2 : État par séries de mandatement
     * FIX : Colonne "N° Affaire et Date" affiche maintenant numéro + date (au lieu de juste la date)
     */
    /**
     * CORRECTION BUG 2 : Configure les colonnes pour le Template 2 : État par séries de mandatement
     * FIX : Colonne "N° encaissement et Date" doit afficher numéro + date (pas juste la date)
     */
    private void configureColumnsEtatMandatement() {
        // 1. N° encaissement et Date - CORRIGÉ : Afficher numéro + date (pas juste la date)
        TableColumn<Object, String> numeroEncCol = new TableColumn<>("N° encaissement et Date");
        numeroEncCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            // CORRECTION BUG 2 : Essayer d'extraire reference ET numeroEncaissement
            String numero = extractValue(item, "reference");
            if (numero == null || numero.isEmpty()) {
                numero = extractValue(item, "numeroEncaissement");
            }
            String date = extractValue(item, "dateEncaissement");

            if (numero != null && date != null) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null) {
                return new SimpleStringProperty(numero);
            } else if (date != null) {
                return new SimpleStringProperty("N/A\n" + date);
            }
            return new SimpleStringProperty("");
        });
        numeroEncCol.setPrefWidth(140);

        // Configuration pour affichage multi-ligne
        numeroEncCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTextAlignment(TextAlignment.CENTER);
                }
            }
        });

        // 2. N° Affaire et Date - Déjà correct
        TableColumn<Object, String> numeroAffCol = new TableColumn<>("N° Affaire et Date");
        numeroAffCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "numeroAffaire");
            String date = extractValue(item, "dateAffaire");

            if (numero != null && date != null) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null) {
                return new SimpleStringProperty(numero);
            } else if (date != null) {
                return new SimpleStringProperty("N/A\n" + date);
            }
            return new SimpleStringProperty("");
        });
        numeroAffCol.setPrefWidth(130);
        numeroAffCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTextAlignment(TextAlignment.CENTER);
                }
            }
        });

        // 3. Produit net
        TableColumn<Object, String> produitNetCol = new TableColumn<>("Produit net");
        produitNetCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "produitNet"))));
        produitNetCol.setPrefWidth(100);
        produitNetCol.getStyleClass().add("montant-column");

        // 4. Part revenant aux Chefs
        TableColumn<Object, String> chefsCol = new TableColumn<>("Chefs");
        chefsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partChefs"))));
        chefsCol.setPrefWidth(80);
        chefsCol.getStyleClass().add("montant-column");

        // 5. Part revenant aux Saisissants
        TableColumn<Object, String> saisissantsCol = new TableColumn<>("Saisissants");
        saisissantsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partSaisissants"))));
        saisissantsCol.setPrefWidth(100);
        saisissantsCol.getStyleClass().add("montant-column");

        // 6. Mutuelle nationale
        TableColumn<Object, String> mutuelleCol = new TableColumn<>("Mutuelle nationale");
        mutuelleCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partMutuelle"))));
        mutuelleCol.setPrefWidth(130);
        mutuelleCol.getStyleClass().add("montant-column");

        // 7. Masse commune
        TableColumn<Object, String> masseCol = new TableColumn<>("Masse commune");
        masseCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partMasseCommune"))));
        masseCol.setPrefWidth(110);
        masseCol.getStyleClass().add("montant-column");

        // 8. Intéressement
        TableColumn<Object, String> interessementCol = new TableColumn<>("Intéressement");
        interessementCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partInteressement"))));
        interessementCol.setPrefWidth(110);
        interessementCol.getStyleClass().add("montant-column");

        // 9. Observations
        TableColumn<Object, String> observationsCol = new TableColumn<>("Observations");
        observationsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "observations")));
        observationsCol.setPrefWidth(150);

        resultatsTableView.getColumns().addAll(
                numeroEncCol, numeroAffCol, produitNetCol, chefsCol, saisissantsCol,
                mutuelleCol, masseCol, interessementCol, observationsCol
        );
        logger.debug("✅ Colonnes Template 2 configurées : 9 colonnes exactes");
    }

    public static TypeRapport fromLibelle(String libelle) {
        if (libelle == null) {
            return null;
        }

        for (TypeRapport type : TypeRapport.values()) {  // Corriger : TypeRapport.values()
            if (type.getLibelle().equalsIgnoreCase(libelle)) {  // Corriger : type.getLibelle()
                return type;
            }
        }

        throw new IllegalArgumentException("Type de rapport inconnu: " + libelle);
    }

    /**
     * Configure les colonnes pour le rapport de répartition des affaires
     * AMÉLIORÉ: Support natif des AffaireRepartitionDTO
     */
    /**
     * CORRECTION BUG 1 : Configure les colonnes pour le Template 1 : État de répartition des affaires
     * FIX : Colonne "N° encaissement et Date" affiche maintenant numéro + date formatée
     */
    private void configureColumnsRepartitionAffaires() {
        // 1. N° encaissement et Date - CORRIGÉ avec format numéro + date à la ligne
        TableColumn<Object, String> numeroEncCol = new TableColumn<>("N° encaissement et Date");
        numeroEncCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "numeroEncaissement");
            String date = extractValue(item, "dateEncaissement");

            if (numero != null && date != null) {
                // Format: 2505R0034 <br/> 25/10/2025 (simulé avec \n)
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null) {
                return new SimpleStringProperty(numero);
            }
            return new SimpleStringProperty("");
        });
        numeroEncCol.setPrefWidth(140);

        // Configuration pour affichage multi-ligne
        numeroEncCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTextAlignment(TextAlignment.CENTER);
                }
            }
        });

        // 2. N° Affaire et Date
        TableColumn<Object, String> numeroAffCol = new TableColumn<>("N° Affaire et Date");
        numeroAffCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "numeroAffaire");
            String date = extractValue(item, "dateCreation");

            if (numero != null && date != null) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null) {
                return new SimpleStringProperty(numero);
            }
            return new SimpleStringProperty("");
        });
        numeroAffCol.setPrefWidth(130);
        numeroAffCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTextAlignment(TextAlignment.CENTER);
                }
            }
        });

        // 3. Produit disponible
        TableColumn<Object, String> produitDispCol = new TableColumn<>("Produit disponible");
        produitDispCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "produitDisponible"))));
        produitDispCol.setPrefWidth(120);
        produitDispCol.getStyleClass().add("montant-column");

        // 4. Direction Départementale
        TableColumn<Object, String> ddCol = new TableColumn<>("Direction Départementale");
        ddCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partDD"))));
        ddCol.setPrefWidth(140);
        ddCol.getStyleClass().add("montant-column");

        // 5. Indicateur
        TableColumn<Object, String> indicateurCol = new TableColumn<>("Indicateur");
        indicateurCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partIndicateur"))));
        indicateurCol.setPrefWidth(100);
        indicateurCol.getStyleClass().add("montant-column");

        // 6. Produit net
        TableColumn<Object, String> produitNetCol = new TableColumn<>("Produit net");
        produitNetCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "produitNet"))));
        produitNetCol.setPrefWidth(100);
        produitNetCol.getStyleClass().add("montant-column");

        // 7. FLCF
        TableColumn<Object, String> flcfCol = new TableColumn<>("FLCF");
        flcfCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partFlcf"))));
        flcfCol.setPrefWidth(80);
        flcfCol.getStyleClass().add("montant-column");

        // 8. Trésor
        TableColumn<Object, String> tresorCol = new TableColumn<>("Trésor");
        tresorCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partTresor"))));
        tresorCol.setPrefWidth(80);
        tresorCol.getStyleClass().add("montant-column");

        // 9. Produit net ayants droits
        TableColumn<Object, String> ayantsDroitsCol = new TableColumn<>("Produit net ayants droits");
        ayantsDroitsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partAyantsDroits"))));
        ayantsDroitsCol.setPrefWidth(160);
        ayantsDroitsCol.getStyleClass().add("montant-column");

        // 10. Chefs
        TableColumn<Object, String> chefsCol = new TableColumn<>("Chefs");
        chefsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partChefs"))));
        chefsCol.setPrefWidth(80);
        chefsCol.getStyleClass().add("montant-column");

        // 11. Saisissants
        TableColumn<Object, String> saisissantsCol = new TableColumn<>("Saisissants");
        saisissantsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partSaisissants"))));
        saisissantsCol.setPrefWidth(100);
        saisissantsCol.getStyleClass().add("montant-column");

        // 12. Mutuelle nationale
        TableColumn<Object, String> mutuelleCol = new TableColumn<>("Mutuelle nationale");
        mutuelleCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partMutuelle"))));
        mutuelleCol.setPrefWidth(130);
        mutuelleCol.getStyleClass().add("montant-column");

        // 13. Masse commune
        TableColumn<Object, String> masseCol = new TableColumn<>("Masse commune");
        masseCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partMasseCommune"))));
        masseCol.setPrefWidth(110);
        masseCol.getStyleClass().add("montant-column");

        // 14. Intéressement
        TableColumn<Object, String> interessementCol = new TableColumn<>("Intéressement");
        interessementCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partInteressement"))));
        interessementCol.setPrefWidth(110);
        interessementCol.getStyleClass().add("montant-column");

        resultatsTableView.getColumns().addAll(
                numeroEncCol, numeroAffCol, produitDispCol, ddCol, indicateurCol,
                produitNetCol, flcfCol, tresorCol, ayantsDroitsCol,
                chefsCol, saisissantsCol, mutuelleCol, masseCol, interessementCol
        );
        logger.debug("✅ Colonnes Template 1 configurées : 14 colonnes exactes");
    }

    /**
     * Configure les colonnes pour le tableau des amendes par services
     * AMÉLIORÉ: Support natif des ServiceStatsDTO
     */
    private void configureColumnsAmendesServices() {
        // 1. Nom du service
        TableColumn<Object, String> nomServiceCol = new TableColumn<>("Nom du service");
        nomServiceCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "nomService")));
        nomServiceCol.setPrefWidth(200);

        // 2. Nombre d'affaires
        TableColumn<Object, String> nombreAffairesCol = new TableColumn<>("Nombre d'affaires");
        nombreAffairesCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "nombreAffaires")));
        nombreAffairesCol.setPrefWidth(130);

        // 3. Montant total
        TableColumn<Object, String> montantTotalCol = new TableColumn<>("Montant total");
        montantTotalCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "montantTotal"))));
        montantTotalCol.setPrefWidth(120);
        montantTotalCol.getStyleClass().add("montant-column");

        // 4. Montant moyen
        TableColumn<Object, String> montantMoyenCol = new TableColumn<>("Montant moyen");
        montantMoyenCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "montantMoyen"))));
        montantMoyenCol.setPrefWidth(120);
        montantMoyenCol.getStyleClass().add("montant-column");

        resultatsTableView.getColumns().addAll(
                nomServiceCol, nombreAffairesCol, montantTotalCol, montantMoyenCol
        );
        logger.debug("✅ Colonnes Template 7 configurées : 4 colonnes exactes");
    }

    /**
     * AJOUT Template 3 : Configuration des colonnes pour Centre de Répartition
     */
    private void configureColumnsCentreRepartition() {
        // 1. Centre de répartition
        TableColumn<Object, String> centreCol = new TableColumn<>("Centre de répartition");
        centreCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "nomCentre")));
        centreCol.setPrefWidth(180);

        // 2. Répartition de base
        TableColumn<Object, String> repartitionBaseCol = new TableColumn<>("Répartition de base");
        repartitionBaseCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "repartitionBase"))));
        repartitionBaseCol.setPrefWidth(140);
        repartitionBaseCol.getStyleClass().add("montant-column");

        // 3. Répartition part indicateur fictif
        TableColumn<Object, String> repartitionIndicCol = new TableColumn<>("Répartition part indic. fictif");
        repartitionIndicCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "repartitionIndicateur"))));
        repartitionIndicCol.setPrefWidth(160);
        repartitionIndicCol.getStyleClass().add("montant-column");

        // 4. Part centre
        TableColumn<Object, String> partCentreCol = new TableColumn<>("Part centre");
        partCentreCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partCentre"))));
        partCentreCol.setPrefWidth(120);
        partCentreCol.getStyleClass().add("montant-column");

        resultatsTableView.getColumns().addAll(centreCol, repartitionBaseCol, repartitionIndicCol, partCentreCol);
        logger.debug("✅ Colonnes Template 3 configurées : 4 colonnes exactes");
    }

    /**
     * AJOUT Template 4 : Configuration des colonnes pour Indicateurs Réels
     */
    private void configureColumnsIndicateursReels() {
        // 1. N° encaissement et Date
        TableColumn<Object, String> numeroEncCol = new TableColumn<>("N° encaissement et Date");
        numeroEncCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "numeroEncaissement")));
        numeroEncCol.setPrefWidth(140);

        // 2. N° Affaire et Date
        TableColumn<Object, String> numeroAffCol = new TableColumn<>("N° Affaire et Date");
        numeroAffCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "numeroAffaire")));
        numeroAffCol.setPrefWidth(130);

        // 3. Noms des contrevenants
        TableColumn<Object, String> contrevenantsCol = new TableColumn<>("Noms des contrevenants");
        contrevenantsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "nomContrevenant")));
        contrevenantsCol.setPrefWidth(150);

        // 4. Contraventions
        TableColumn<Object, String> contraventionsCol = new TableColumn<>("Contraventions");
        contraventionsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "typeContravention")));
        contraventionsCol.setPrefWidth(120);

        // 5. Montant encaissement
        TableColumn<Object, String> montantEncCol = new TableColumn<>("Montant encaissement");
        montantEncCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "montantEncaissement"))));
        montantEncCol.setPrefWidth(130);
        montantEncCol.getStyleClass().add("montant-column");

        // 6. Part indicateur
        TableColumn<Object, String> partIndicCol = new TableColumn<>("Part indicateur");
        partIndicCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partIndicateur"))));
        partIndicCol.setPrefWidth(110);
        partIndicCol.getStyleClass().add("montant-column");

        // 7. Observations
        TableColumn<Object, String> observationsCol = new TableColumn<>("Observations");
        observationsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "observations")));
        observationsCol.setPrefWidth(150);

        resultatsTableView.getColumns().addAll(
                numeroEncCol, numeroAffCol, contrevenantsCol, contraventionsCol,
                montantEncCol, partIndicCol, observationsCol
        );
        logger.debug("✅ Colonnes Template 4 configurées : 7 colonnes exactes");
    }

    /**
     * AJOUT Template 5 : Configuration des colonnes pour Répartition du Produit
     */
    private void configureColumnsRepartitionProduit() {
        // 1. N° encaissement et Date
        TableColumn<Object, String> numeroEncCol = new TableColumn<>("N° encaissement et Date");
        numeroEncCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "numeroEncaissement");
            String date = extractValue(item, "dateEncaissement");

            if (numero != null && !numero.isEmpty() && date != null && !date.isEmpty()) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null && !numero.isEmpty()) {
                return new SimpleStringProperty(numero);
            }
            return new SimpleStringProperty("");
        });
        numeroEncCol.setPrefWidth(120);
        numeroEncCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                }
            }
        });

        // 2. N° Affaire et Date
        TableColumn<Object, String> numeroAffCol = new TableColumn<>("N° Affaire et Date");
        numeroAffCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "numeroAffaire");
            String date = extractValue(item, "dateAffaire");

            if (numero != null && !numero.isEmpty() && date != null && !date.isEmpty()) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null && !numero.isEmpty()) {
                return new SimpleStringProperty(numero);
            }
            return new SimpleStringProperty("");
        });
        numeroAffCol.setPrefWidth(120);
        numeroAffCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                }
            }
        });

        // 3. Noms des contrevenants - CORRECTION BUG 5
        TableColumn<Object, String> contrevenantsCol = new TableColumn<>("Noms des contrevenants");
        contrevenantsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "nomContrevenant")));
        contrevenantsCol.setPrefWidth(130);

        // 4. Noms des contraventions - CORRECTION BUG 5
        TableColumn<Object, String> contraventionsCol = new TableColumn<>("Noms des contraventions");
        contraventionsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "nomContravention")));
        contraventionsCol.setPrefWidth(130);

        // 5. Produit disponible
        TableColumn<Object, String> produitDispCol = new TableColumn<>("Produit disponible");
        produitDispCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "produitDisponible"))));
        produitDispCol.setPrefWidth(110);
        produitDispCol.getStyleClass().add("montant-column");

        // 6. Part indicateur
        TableColumn<Object, String> partIndicCol = new TableColumn<>("Part indicateur");
        partIndicCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partIndicateur"))));
        partIndicCol.setPrefWidth(100);
        partIndicCol.getStyleClass().add("montant-column");

        // 7. Part Direction contentieux
        TableColumn<Object, String> partDirectionCol = new TableColumn<>("Part Direction contentieux");
        partDirectionCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partDirectionContentieux"))));
        partDirectionCol.setPrefWidth(130);
        partDirectionCol.getStyleClass().add("montant-column");

        // 8. Part indicateur (2ème colonne)
        TableColumn<Object, String> partIndic2Col = new TableColumn<>("Part indicateur");
        partIndic2Col.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partIndicateur2"))));
        partIndic2Col.setPrefWidth(100);
        partIndic2Col.getStyleClass().add("montant-column");

        // 9. FLCF
        TableColumn<Object, String> flcfCol = new TableColumn<>("FLCF");
        flcfCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partFlcf"))));
        flcfCol.setPrefWidth(80);
        flcfCol.getStyleClass().add("montant-column");

        // 10. Montant Trésor
        TableColumn<Object, String> tresorCol = new TableColumn<>("Montant Trésor");
        tresorCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "montantTresor"))));
        tresorCol.setPrefWidth(100);
        tresorCol.getStyleClass().add("montant-column");

        // 11. Montant Global ayants droits
        TableColumn<Object, String> ayantsDroitsCol = new TableColumn<>("Montant Global ayants droits");
        ayantsDroitsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "montantGlobalAyantsDroits"))));
        ayantsDroitsCol.setPrefWidth(140);
        ayantsDroitsCol.getStyleClass().add("montant-column");

        // Ajouter toutes les colonnes dans l'ordre du Template 5
        resultatsTableView.getColumns().addAll(
                numeroEncCol, numeroAffCol, contrevenantsCol, contraventionsCol,
                produitDispCol, partIndicCol, partDirectionCol, partIndic2Col,
                flcfCol, tresorCol, ayantsDroitsCol
        );

        logger.debug("✅ Colonnes Template 5 configurées : 11 colonnes exactes");
    }

    /**
     * AJOUT Template 6 : Configuration des colonnes pour État Cumulé par Agent
     */
    /**
     * CORRECTION Template 6 : Configuration des colonnes SANS "Rôle", "Nb Affaires" et "Part mutuelle"
     * FIX : Supprimer les colonnes indésirables selon les spécifications
     */
    private void configureColumnsEtatCumuleAgent() {
        // 1. Nom de l'agent - CORRECTION : Utiliser getNom() + getPrenom() (pas nomComplet)
        TableColumn<Object, String> nomAgentCol = new TableColumn<>("Nom de l'agent");
        nomAgentCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            Agent agent = (Agent) getFieldValue(item, "agent");
            if (agent != null) {
                String nom = agent.getNom() != null ? agent.getNom() : "";
                String prenom = agent.getPrenom() != null ? agent.getPrenom() : "";
                return new SimpleStringProperty(nom + " " + prenom);
            }
            return new SimpleStringProperty("N/A");
        });
        nomAgentCol.setPrefWidth(150);

        // 2. Part en tant que Chef
        TableColumn<Object, String> partChefCol = new TableColumn<>("Part Chef");
        partChefCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partEnTantQueChef"))));
        partChefCol.setPrefWidth(100);
        partChefCol.getStyleClass().add("montant-column");

        // 3. Part en tant que Saisissant
        TableColumn<Object, String> partSaisissantCol = new TableColumn<>("Part Saisissant");
        partSaisissantCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partEnTantQueSaisissant"))));
        partSaisissantCol.setPrefWidth(120);
        partSaisissantCol.getStyleClass().add("montant-column");

        // 4. Part en tant que DG
        TableColumn<Object, String> partDGCol = new TableColumn<>("Part DG");
        partDGCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partEnTantQueDG"))));
        partDGCol.setPrefWidth(80);
        partDGCol.getStyleClass().add("montant-column");

        // 5. Part en tant que DD
        TableColumn<Object, String> partDDCol = new TableColumn<>("Part DD");
        partDDCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partEnTantQueDD"))));
        partDDCol.setPrefWidth(80);
        partDDCol.getStyleClass().add("montant-column");

        // 6. Total agent
        TableColumn<Object, String> totalAgentCol = new TableColumn<>("Total agent");
        totalAgentCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partTotaleAgent"))));
        totalAgentCol.setPrefWidth(100);
        totalAgentCol.getStyleClass().add("montant-column");

        // 7. Observations
        TableColumn<Object, String> observationsCol = new TableColumn<>("Observations");
        observationsCol.setCellValueFactory(data ->
                new SimpleStringProperty(extractValue(data.getValue(), "observations")));
        observationsCol.setPrefWidth(150);

        resultatsTableView.getColumns().addAll(
                nomAgentCol, partChefCol, partSaisissantCol,
                partDGCol, partDDCol, totalAgentCol, observationsCol
        );
        logger.debug("✅ Colonnes Template 6 configurées : 7 colonnes (colonnes indésirables supprimées)");
    }

    /**
     * AJOUT Template 8 : Configuration des colonnes pour État par Séries de Mandatements Agents
     */
    /**
     * CORRECTION Template 8 : Configuration des colonnes selon les spécifications exactes
     * FIX : Ordre et libellés corrects selon le cahier des charges
     */
    private void configureColumnsMandatementAgents() {
        // 1. N° encaissement et Date
        TableColumn<Object, String> numeroEncCol = new TableColumn<>("N° encaissement et Date");
        numeroEncCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "reference");
            if (numero == null || numero.isEmpty()) {
                numero = extractValue(item, "numeroEncaissement");
            }
            String date = extractValue(item, "dateEncaissement");

            if (numero != null && date != null) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null) {
                return new SimpleStringProperty(numero);
            }
            return new SimpleStringProperty("");
        });
        numeroEncCol.setPrefWidth(140);
        numeroEncCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTextAlignment(TextAlignment.CENTER);
                }
            }
        });

        // 2. N° Affaire et Date
        TableColumn<Object, String> numeroAffCol = new TableColumn<>("N° Affaire et Date");
        numeroAffCol.setCellValueFactory(data -> {
            Object item = data.getValue();
            String numero = extractValue(item, "numeroAffaire");
            String date = extractValue(item, "dateAffaire");

            if (numero != null && date != null) {
                return new SimpleStringProperty(numero + "\n" + date);
            } else if (numero != null) {
                return new SimpleStringProperty(numero);
            }
            return new SimpleStringProperty("");
        });
        numeroAffCol.setPrefWidth(130);
        numeroAffCol.setCellFactory(col -> new TableCell<Object, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                    setTextAlignment(TextAlignment.CENTER);
                }
            }
        });

        // 3. Chefs
        TableColumn<Object, String> chefsCol = new TableColumn<>("Chefs");
        chefsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partChefs"))));
        chefsCol.setPrefWidth(80);
        chefsCol.getStyleClass().add("montant-column");

        // 4. Saisissants
        TableColumn<Object, String> saisissantsCol = new TableColumn<>("Saisissants");
        saisissantsCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partSaisissants"))));
        saisissantsCol.setPrefWidth(100);
        saisissantsCol.getStyleClass().add("montant-column");

        // 5. Mutuelle nationale
        TableColumn<Object, String> mutuelleCol = new TableColumn<>("Mutuelle nationale");
        mutuelleCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partMutuelle"))));
        mutuelleCol.setPrefWidth(120);
        mutuelleCol.getStyleClass().add("montant-column");

        // 6. D.G
        TableColumn<Object, String> dgCol = new TableColumn<>("D.G");
        dgCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partDG"))));
        dgCol.setPrefWidth(80);
        dgCol.getStyleClass().add("montant-column");

        // 7. D.D
        TableColumn<Object, String> ddCol = new TableColumn<>("D.D");
        ddCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "partDD"))));
        ddCol.setPrefWidth(80);
        ddCol.getStyleClass().add("montant-column");

        // 8. Part agent
        TableColumn<Object, String> partAgentCol = new TableColumn<>("Part agent");
        partAgentCol.setCellValueFactory(data ->
                new SimpleStringProperty(formatMontant(extractBigDecimal(data.getValue(), "montantTotal"))));
        partAgentCol.setPrefWidth(100);
        partAgentCol.getStyleClass().add("montant-column");

        resultatsTableView.getColumns().addAll(
                numeroEncCol, numeroAffCol, chefsCol, saisissantsCol, mutuelleCol,
                dgCol, ddCol, partAgentCol
        );
        logger.debug("✅ Colonnes Template 8 configurées : 8 colonnes exactes selon spécifications");
    }

    private void configurerColonnesTemplate3() {
        logger.debug("✅ Configuration colonnes Template 3");

        resultatsTableView.getColumns().clear();

        TableColumn<Object, String> col1 = new TableColumn<>("Centre de répartition");
        col1.setCellValueFactory(data -> {
            if (data.getValue() instanceof RapportService.CentreStatsDTO) {
                RapportService.CentreStatsDTO centre = (RapportService.CentreStatsDTO) data.getValue();
                return new SimpleStringProperty(centre.getCentre() != null ?
                        centre.getCentre().getNomCentre() : "");
            }
            return new SimpleStringProperty("");
        });
        col1.setPrefWidth(250);

        TableColumn<Object, String> col2 = new TableColumn<>("Répartition de base");
        col2.setCellValueFactory(data -> {
            if (data.getValue() instanceof RapportService.CentreStatsDTO) {
                RapportService.CentreStatsDTO centre = (RapportService.CentreStatsDTO) data.getValue();
                return new SimpleStringProperty(
                        CurrencyFormatter.format(centre.getRepartitionBase())
                );
            }
            return new SimpleStringProperty("");
        });
        col2.setPrefWidth(150);

        TableColumn<Object, String> col3 = new TableColumn<>("Répartition part indic. fictif");
        col3.setCellValueFactory(data -> {
            if (data.getValue() instanceof RapportService.CentreStatsDTO) {
                // TOUJOURS ZÉRO
                return new SimpleStringProperty("0");
            }
            return new SimpleStringProperty("");
        });
        col3.setPrefWidth(180);

        TableColumn<Object, String> col4 = new TableColumn<>("Part centre");
        col4.setCellValueFactory(data -> {
            if (data.getValue() instanceof RapportService.CentreStatsDTO) {
                RapportService.CentreStatsDTO centre = (RapportService.CentreStatsDTO) data.getValue();
                // Part centre = Répartition base - 0 = Répartition base
                return new SimpleStringProperty(
                        CurrencyFormatter.format(centre.getPartTotalCentre())
                );
            }
            return new SimpleStringProperty("");
        });
        col4.setPrefWidth(150);

        resultatsTableView.getColumns().addAll(col1, col2, col3, col4);
        logger.debug("✅ Colonnes Template 3 configurées : {} colonnes", resultatsTableView.getColumns().size());
    }

    /**
     * Configure les colonnes génériques
     */
    private void configureColumnsGeneric() {
        logger.debug("🔧 Configuration colonnes génériques");

        // Vider les colonnes existantes
        resultatsTableView.getColumns().clear();

        // Colonne principale flexible
        TableColumn<Object, String> col1 = new TableColumn<>("Informations");
        col1.setCellValueFactory(data -> {
            Object item = data.getValue();
            if (item == null) {
                return new SimpleStringProperty("N/A");
            }

            // Affichage intelligent selon le type
            String display = formatObjectForDisplay(item);
            return new SimpleStringProperty(display);
        });
        col1.setPrefWidth(400);
        col1.setMinWidth(200);

        // Colonne type pour debug
        TableColumn<Object, String> col2 = new TableColumn<>("Type");
        col2.setCellValueFactory(data -> {
            Object item = data.getValue();
            return new SimpleStringProperty(item != null ? item.getClass().getSimpleName() : "NULL");
        });
        col2.setPrefWidth(150);

        resultatsTableView.getColumns().addAll(col1, col2);

        logger.debug("✅ Colonnes génériques configurées: {}", resultatsTableView.getColumns().size());
    }

    private String formatObjectForDisplay(Object item) {
        if (item == null) return "NULL";

        try {
            // Si c'est un DTO avec des propriétés, essayer d'extraire les infos principales
            String className = item.getClass().getSimpleName();

            if (className.contains("Encaissement")) {
                return String.format("Encaissement - Référence: %s, Montant: %s",
                        extractValue(item, "reference", "N/A"),
                        formatMontant(extractBigDecimal(item, "montantEncaisse")));

            } else if (className.contains("Affaire")) {
                return String.format("Affaire - N°: %s, Montant: %s",
                        extractValue(item, "numeroAffaire", "N/A"),
                        formatMontant(extractBigDecimal(item, "montantTotal")));

            } else if (className.contains("Service")) {
                return String.format("Service - Nom: %s, Nb Affaires: %s",
                        extractValue(item, "nomService", "N/A"),
                        extractValue(item, "nombreAffaires", "N/A"));

            } else {
                // Fallback : toString ou informations de base
                String toString = item.toString();
                return toString.length() > 100 ? toString.substring(0, 100) + "..." : toString;
            }

        } catch (Exception e) {
            logger.debug("Erreur formatage objet: {}", e.getMessage());
            return item.toString();
        }
    }

    /**
     * Met à jour les données de la TableView avec intelligence de type
     * AMÉLIORÉ: Support des DTOs spécifiques du RapportService
     */
    private void updateTableViewData(Object rapportData) {
        logger.debug("=== DÉBUT updateTableViewData ===");
        logger.debug("Paramètres reçus:");
        logger.debug("- resultatsTableView: {}", resultatsTableView != null ? "EXISTS" : "NULL");
        logger.debug("- rapportData: {}", rapportData != null ? rapportData.getClass().getSimpleName() : "NULL");

        if (resultatsTableView == null) {
            logger.error("❌ resultatsTableView est null");
            return;
        }

        List<Object> itemsToDisplay = new ArrayList<>();

        try {
            if (rapportData != null) {
                logger.debug("🔍 Type de données reçues: {}", rapportData.getClass().getName());

                if (rapportData instanceof RapportService.RapportRepartitionDTO) {
                    // Template 1
                    RapportService.RapportRepartitionDTO rapport = (RapportService.RapportRepartitionDTO) rapportData;
                    if (rapport.getAffaires() != null && !rapport.getAffaires().isEmpty()) {
                        itemsToDisplay.addAll(rapport.getAffaires());
                        logger.debug("✅ Ajouté {} affaires de répartition", rapport.getAffaires().size());
                    }

                } else if (rapportData instanceof RapportService.EtatMandatementDTO) {
                    // Template 2
                    RapportService.EtatMandatementDTO mandatement = (RapportService.EtatMandatementDTO) rapportData;
                    if (mandatement.getMandatements() != null && !mandatement.getMandatements().isEmpty()) {
                        itemsToDisplay.addAll(mandatement.getMandatements());
                        logger.debug("✅ Ajouté {} éléments de mandatement", mandatement.getMandatements().size());
                    } else {
                        logger.warn("⚠️ Aucun mandatement, création de données simulées");
                        itemsToDisplay.addAll(creerDonneesSimuleesTemplate2());
                    }

                } else if (rapportData instanceof RapportService.CentreRepartitionDTO) {
                    // Template 3
                    RapportService.CentreRepartitionDTO centres = (RapportService.CentreRepartitionDTO) rapportData;
                    if (centres.getCentres() != null && !centres.getCentres().isEmpty()) {
                        itemsToDisplay.addAll(centres.getCentres());
                        logger.debug("✅ Ajouté {} centres", centres.getCentres().size());
                    } else {
                        logger.warn("⚠️ Aucun centre, création de données simulées");
                        itemsToDisplay.addAll(creerDonneesSimuleesTemplate3());
                    }

                } else if (rapportData instanceof RapportService.RepartitionProduitDTO) {
                    // Template 5
                    RapportService.RepartitionProduitDTO produit = (RapportService.RepartitionProduitDTO) rapportData;
                    if (produit.getLignes() != null && !produit.getLignes().isEmpty()) {
                        itemsToDisplay.addAll(produit.getLignes());
                        logger.debug("✅ Ajouté {} lignes de répartition", produit.getLignes().size());
                    } else {
                        logger.warn("⚠️ Aucune ligne, création de données simulées");
                        itemsToDisplay.addAll(creerDonneesSimuleesTemplate5());
                    }

                } else {
                    // Autres templates - extraction générique
                    logger.debug("📋 Traitement DTO générique: {}", rapportData.getClass().getSimpleName());
                    List<Object> extracted = extraireDonneesGeneriques(rapportData);
                    if (!extracted.isEmpty()) {
                        itemsToDisplay.addAll(extracted);
                        logger.debug("✅ Ajouté {} éléments génériques", extracted.size());
                    } else {
                        // Fallback : ajouter l'objet lui-même
                        itemsToDisplay.add(rapportData);
                        logger.debug("✅ Ajouté l'objet rapport lui-même");
                    }
                }
            }

            // Mise à jour de la TableView
            logger.debug("📊 Mise à jour TableView avec {} éléments", itemsToDisplay.size());
            resultatsTableView.getItems().clear();
            resultatsTableView.getItems().addAll(itemsToDisplay);

            // Mise à jour du compteur
            updateNombreResultats(itemsToDisplay.size());

            logger.debug("✅ TableView mise à jour avec {} éléments", itemsToDisplay.size());

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la mise à jour des données", e);
            resultatsTableView.getItems().clear();
            updateNombreResultats(0);
        }
    }

    /**
     * NOUVEAU : Création de données simulées pour Template 2
     */
    private List<Object> creerDonneesSimuleesTemplate2() {
        List<Object> donneesSimulees = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            Map<String, Object> mandatement = new HashMap<>();
            mandatement.put("numeroEncaissement", "2025R000" + i);
            mandatement.put("dateEncaissement", LocalDate.now().minusDays(i));
            mandatement.put("numeroAffaire", "AF2025-00" + i);
            mandatement.put("dateAffaire", LocalDate.now().minusDays(i + 5));
            mandatement.put("produitNet", new BigDecimal(100000 * i));
            mandatement.put("partChefs", new BigDecimal(20000 * i));
            mandatement.put("partSaisissants", new BigDecimal(15000 * i));
            mandatement.put("partMutuelle", new BigDecimal(5000 * i));
            mandatement.put("partMasseCommune", new BigDecimal(3000 * i));
            mandatement.put("partInteressement", new BigDecimal(2000 * i));
            mandatement.put("observations", "Données de test - Template 2");

            donneesSimulees.add(mandatement);
        }

        return donneesSimulees;
    }

    private List<Object> creerDonneesSimuleesTemplate3() {
        List<Object> donneesSimulees = new ArrayList<>();

        String[] centres = {"Centre Nord", "Centre Sud", "Centre Est"};
        for (int i = 0; i < centres.length; i++) {
            Map<String, Object> centre = new HashMap<>();
            centre.put("nomCentre", centres[i]);
            centre.put("repartitionBase", new BigDecimal(500000 + (i * 100000)));
            centre.put("repartitionIndicateur", new BigDecimal(200000 + (i * 50000)));
            centre.put("partCentre", new BigDecimal(700000 + (i * 150000)));
            centre.put("nombreAffaires", 10 + (i * 5));

            donneesSimulees.add(centre);
        }

        return donneesSimulees;
    }

    /**
     * CORRECTION : Crée des données simulées pour Template 5
     */
    private List<Object> creerDonneesSimuleesTemplate5() {
        List<Object> donneesSimulees = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            Map<String, Object> ligne = new HashMap<>();
            ligne.put("numeroEncaissement", "2025R000" + i);
            ligne.put("dateEncaissement", LocalDate.now().minusDays(i));
            ligne.put("numeroAffaire", "AF2025-00" + i);
            ligne.put("dateAffaire", LocalDate.now().minusDays(i + 5));
            ligne.put("nomContrevenant", "Contrevenant Test " + i);
            ligne.put("nomContravention", "Contravention Type " + i);
            ligne.put("produitDisponible", new BigDecimal(150000 * i));
            ligne.put("partIndicateur", new BigDecimal(30000 * i));
            ligne.put("partDirectionContentieux", new BigDecimal(25000 * i));
            ligne.put("partFlcf", new BigDecimal(20000 * i));
            ligne.put("montantTresor", new BigDecimal(35000 * i));
            ligne.put("montantGlobalAyantsDroits", new BigDecimal(40000 * i));

            donneesSimulees.add(ligne);
        }

        return donneesSimulees;
    }

    /**
     * Extrait les données d'un DTO générique par réflexion
     */
    private List<Object> extraireDonneesGeneriques(Object rapportData) {
        List<Object> donnees = new ArrayList<>();

        try {
            // Essayer de trouver des collections dans le DTO via les champs
            Field[] fields = rapportData.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(rapportData);

                if (value instanceof Collection) {
                    Collection<?> collection = (Collection<?>) value;
                    donnees.addAll(collection);
                    logger.debug("✅ Collection trouvée via champ: {} ({} éléments)",
                            field.getName(), collection.size());
                    break; // Prendre la première collection trouvée
                }
            }

            // Si aucune collection trouvée, essayer les getters
            if (donnees.isEmpty()) {
                Method[] methods = rapportData.getClass().getMethods();
                for (Method method : methods) {
                    if (method.getName().startsWith("get") &&
                            method.getParameterCount() == 0 &&
                            Collection.class.isAssignableFrom(method.getReturnType())) {

                        Object value = method.invoke(rapportData);
                        if (value instanceof Collection) {
                            Collection<?> collection = (Collection<?>) value;
                            donnees.addAll(collection);
                            logger.debug("✅ Collection trouvée via getter: {} ({} éléments)",
                                    method.getName(), collection.size());
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Extraction générique impossible: {}", e.getMessage());
        }

        return donnees;
    }

    /**
     * CORRECTION : Vérifie si un objet DTO a des données
     */
    private boolean hasDonnees(Object dto) {
        if (dto == null) return false;

        try {
            // Vérifier via réflexion s'il y a des collections non vides
            Field[] fields = dto.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(dto);

                if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                    return true;
                }
            }

            // Vérifier via les getters
            Method[] methods = dto.getClass().getMethods();
            for (Method method : methods) {
                if (method.getName().startsWith("get") &&
                        method.getParameterCount() == 0 &&
                        Collection.class.isAssignableFrom(method.getReturnType())) {

                    Object value = method.invoke(dto);
                    if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Erreur vérification données: {}", e.getMessage());
        }

        return false;
    }

    /**
     * NOUVEAU : Création de données simulées selon le template
     */
    private List<Object> creerDonneesSimuleesSelon(TypeRapport type) {
        List<Object> donneesSimulees = new ArrayList<>();

        switch (type) {
            case CENTRE_REPARTITION:
                for (int i = 1; i <= 3; i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("nomCentre", "Centre " + i);
                    item.put("repartitionBase", new BigDecimal(100000 * i));
                    item.put("repartitionIndicateur", new BigDecimal(50000 * i));
                    item.put("partCentre", new BigDecimal(150000 * i));
                    donneesSimulees.add(item);
                }
                break;

            case INDICATEURS_REELS:
                for (int i = 1; i <= 4; i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("numeroEncaissement", "2505R000" + i);
                    item.put("dateEncaissement", "25/05/2025");
                    item.put("numeroAffaire", "25050000" + i);
                    item.put("dateAffaire", "20/05/2025");
                    item.put("nomContrevenant", "Contrevenant " + i);
                    item.put("typeContravention", "Infraction " + i);
                    item.put("montantEncaissement", new BigDecimal(200000 * i));
                    item.put("partIndicateur", new BigDecimal(30000 * i));
                    item.put("observations", "Observation " + i);
                    donneesSimulees.add(item);
                }
                break;

            case REPARTITION_PRODUIT:
                for (int i = 1; i <= 3; i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("numeroEncaissement", "2505R000" + i);
                    item.put("dateEncaissement", "25/05/2025");
                    item.put("numeroAffaire", "25050000" + i);
                    item.put("dateAffaire", "20/05/2025");
                    item.put("nomContrevenant", "Contrevenant " + i);
                    item.put("nomContravention", "Contravention " + i);
                    item.put("produitDisponible", new BigDecimal(300000 * i));
                    item.put("partIndicateur", new BigDecimal(45000 * i));
                    item.put("partDirectionContentieux", new BigDecimal(60000 * i));
                    item.put("partIndicateur2", new BigDecimal(35000 * i));
                    item.put("partFLCF", new BigDecimal(20000 * i));
                    item.put("montantTresor", new BigDecimal(80000 * i));
                    item.put("montantGlobalAyantsDroits", new BigDecimal(140000 * i));
                    donneesSimulees.add(item);
                }
                break;

            case ETAT_CUMULE_AGENT:
                for (int i = 1; i <= 4; i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("nomAgent", "Agent " + i);
                    item.put("partChef", new BigDecimal(25000 * i));
                    item.put("partSaisissant", new BigDecimal(35000 * i));
                    item.put("partDG", new BigDecimal(15000 * i));
                    item.put("partDD", new BigDecimal(10000 * i));
                    item.put("partTotaleAgent", new BigDecimal(85000 * i));
                    donneesSimulees.add(item);
                }
                break;

            default:
                // Données génériques
                Map<String, Object> item = new HashMap<>();
                item.put("information", "Template " + type.getNumeroTemplate());
                item.put("description", type.getDescription());
                donneesSimulees.add(item);
                break;
        }

        return donneesSimulees;
    }

    /**
     * Ouvre le popup d'aperçu pour une ligne spécifique
     */
    private void handlePreviewRow(Object item) {
        if (item == null) return;

        try {
            String htmlContent = genererHtmlPourItem(item);
            String titre = "Aperçu - " + extractValue(item, "numeroAffaire");
            ouvrirPopupApercu(htmlContent, titre, item);
        } catch (Exception e) {
            logger.error("Erreur lors de l'aperçu de la ligne", e);
            AlertUtil.showErrorAlert("Erreur", "Aperçu impossible", e.getMessage());
        }
    }

    /**
     * Imprime directement une ligne
     */
    private void handlePrintRow(Object item) {
        if (item == null) return;

        try {
            String htmlContent = genererHtmlPourItem(item);
            boolean success = printService.printHtml(htmlContent);

            if (success) {
                AlertUtil.showSuccess("Impression", "Document envoyé à l'imprimante");
            } else {
                AlertUtil.showWarningAlert("Impression", "Impression annulée", "L'impression a été annulée");
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'impression de la ligne", e);
            AlertUtil.showErrorAlert("Erreur", "Impression impossible", e.getMessage());
        }
    }

    /**
     * Affiche le détail d'un service
     */
    private void handleDetailService(Object item) {
        if (item == null) return;

        try {
            String nomService = extractValue(item, "nomService");
            String details = String.format("Détail du service: %s\n" +
                            "Nombre d'affaires: %s\n" +
                            "Montant total: %s\n" +
                            "Observations: %s",
                    nomService,
                    extractValue(item, "nombreAffaires"),
                    formatMontant(extractBigDecimal(item, "montantTotal")),
                    extractValue(item, "observations"));

            AlertUtil.showInfoAlert("Détail Service", "Informations du service", details);

        } catch (Exception e) {
            logger.error("Erreur lors de l'affichage du détail service", e);
            AlertUtil.showErrorAlert("Erreur", "Détail impossible", e.getMessage());
        }
    }

    /**
     * Ouvre le popup d'aperçu
     */
    private void ouvrirPopupApercu(String htmlContent, String titre, Object rapportData) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/rapport-preview-dialog.fxml"));
            Parent root = loader.load();

            RapportPreviewController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Aperçu du rapport");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(true);

            controller.setDialogStage(dialogStage);
            controller.loadContent(htmlContent, titre, rapportData);

            dialogStage.show();

        } catch (IOException e) {
            logger.error("Erreur lors de l'ouverture du popup d'aperçu", e);
            AlertUtil.showErrorAlert("Erreur", "Aperçu impossible",
                    "Impossible d'ouvrir la fenêtre d'aperçu");
        }
    }

    /**
     * Gestion de la sélection dans le tableau
     */
    @FXML
    private void handleSelectionnerTout() {
        if (resultatsTableView != null) {
            resultatsTableView.getSelectionModel().selectAll();
        }
    }

    @FXML
    private void handleDeselectionnerTout() {
        if (resultatsTableView != null) {
            resultatsTableView.getSelectionModel().clearSelection();
        }
    }

    /**
     * Affiche l'aide
     */
    @FXML
    private void handleAideRapport() {
        AlertUtil.showInfoAlert("Aide - Rapports",
                "Guide d'utilisation",
                "1. Sélectionnez le type de rapport\n" +
                        "2. Choisissez la période\n" +
                        "3. Appliquez les filtres\n" +
                        "4. Cliquez sur 'Générer'\n" +
                        "5. Utilisez les actions sur chaque ligne ou globalement");
    }

    private String extractValue(Object item, String property, String defaultValue) {
        try {
            String value = extractValue(item, property);
            return value != null && !value.isEmpty() ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Extraction générique par réflexion
     */
    private String extractValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null) {
            return "";
        }

        try {
            // Gestion spéciale pour les colonnes composées N° + Date
            if (fieldName.equals("numeroEncaissementEtDate")) {
                String numero = extractValue(obj, "numeroEncaissement");
                String date = extractValue(obj, "dateEncaissement");

                if (numero != null && !numero.isEmpty() && date != null && !date.isEmpty()) {
                    return numero + "\n" + date;
                } else if (numero != null && !numero.isEmpty()) {
                    return numero;
                } else if (date != null && !date.isEmpty()) {
                    return date;
                }
                return "";
            }

            if (fieldName.equals("numeroAffaireEtDate")) {
                String numero = extractValue(obj, "numeroAffaire");
                String date = extractValue(obj, "dateAffaire");

                if (numero != null && !numero.isEmpty() && date != null && !date.isEmpty()) {
                    return numero + "\n" + date;
                } else if (numero != null && !numero.isEmpty()) {
                    return numero;
                } else if (date != null && !date.isEmpty()) {
                    return date;
                }
                return "";
            }

            // Essayer getter d'abord
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            java.lang.reflect.Method getter = obj.getClass().getMethod(getterName);
            Object value = getter.invoke(obj);

            if (value == null) {
                return "";
            } else if (value instanceof LocalDate) {
                return DateFormatter.format((LocalDate) value);
            } else if (value instanceof BigDecimal) {
                return value.toString();
            } else {
                return value.toString();
            }
        } catch (Exception e) {
            try {
                // Essayer champ direct
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                return value != null ? value.toString() : "";
            } catch (Exception e2) {
                logger.debug("Extraction impossible: {}", fieldName);
                return "";
            }
        }
    }

    /**
     * Extrait une valeur BigDecimal
     */
    private BigDecimal extractBigDecimal(Object obj, String fieldName) {
        try {
            if (obj == null) return null; // Changement : retourne null au lieu de ZERO

            String value = extractValue(obj, fieldName);
            if (value == null || value.trim().isEmpty() || "null".equals(value)) {
                return null; // Changement : retourne null au lieu de ZERO
            }

            // Nettoyer la valeur (supprimer espaces et caractères de formatage)
            value = value.trim().replaceAll("[^0-9.,\\-]", "");
            if (value.isEmpty()) return null;

            return new BigDecimal(value);
        } catch (Exception e) {
            logger.debug("Erreur extraction BigDecimal pour {}.{}: {}",
                    obj.getClass().getSimpleName(), fieldName, e.getMessage());
            return null; // Changement : retourne null au lieu de ZERO
        }
    }

    /**
     * Met à jour le titre du tableau
     */
    private void updateTableauTitre(TypeRapport typeRapport) {
        if (tableauTitreLabel != null) {
            String titre = typeRapport != null ? typeRapport.getLibelle() : "Sélectionnez un type de rapport";
            tableauTitreLabel.setText(titre);
        }
    }

    /**
     * CORRECTION BUG : Met à jour le label du nombre de résultats
     */
    private void updateNombreResultats(int nombre) {
        Platform.runLater(() -> {
            if (nombreResultatsLabel != null) {
                nombreResultatsLabel.setText(nombre + " résultat(s)");
                logger.debug("📊 Nombre de résultats affiché: {}", nombre);
            }

            if (tableauTitreLabel != null && nombre > 0) {
                TypeRapport typeSelectionne = typeRapportComboBox.getValue();
                if (typeSelectionne != null) {
                    tableauTitreLabel.setText(typeSelectionne.getLibelle() + " (" + nombre + " éléments)");
                }
            }
        });
    }

    /**
     * Génère du HTML pour un item spécifique
     */
    private String genererHtmlPourItem(Object item) {
        StringBuilder html = new StringBuilder();
        html.append(genererEnTeteHTML("Détail", getDateDebut(), getDateFin()));
        html.append("<div class='item-detail'>");
        html.append("<h2>Informations détaillées</h2>");
        html.append("<p>").append(item.toString()).append("</p>");
        html.append("</div>");
        html.append(genererPiedHTML());
        return html.toString();
    }

    /**
     * Met à jour la dernière mise à jour
     */
    private void updateDerniereMaj() {
        if (derniereMajLabel != null) {
            derniereMajLabel.setText("Dernière mise à jour: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
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

    @FXML
    private void handleImprimer() {
        if (dernierRapportGenere != null && !dernierRapportGenere.isEmpty()) {
            try {
                boolean success = printService.printHtml(dernierRapportGenere);
                if (success) {
                    AlertUtil.showSuccess("Impression", "Le rapport a été envoyé à l'imprimante.");
                } else {
                    AlertUtil.showWarningAlert("Impression impossible", "Aucune imprimante disponible", "Aucune imprimante disponible ou impression annulée.");
                }
            } catch (Exception e) {
                logger.error("Erreur lors de l'impression", e);
                AlertUtil.showErrorAlert("Erreur d'impression", "Impossible d'imprimer", e.getMessage());
            }
        } else {
            AlertUtil.showWarningAlert("Aucun rapport", "Impression impossible",
                    "Veuillez d'abord générer un rapport.");
        }
    }
}