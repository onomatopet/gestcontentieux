package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.service.RapportService.RapportRepartitionDTO;
import com.regulation.contentieux.service.RapportService.AffaireRepartitionDTO;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.RoundingMode;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur spécialisé pour l'affichage détaillé des rapports de rétrocession
 * Gère les tableaux, graphiques et statistiques avancées
 */
public class RapportRepartitionController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportRepartitionController.class);

    // Informations générales
    @FXML private Label titreLabel;
    @FXML private Label periodeLabel;
    @FXML private Label dateGenerationLabel;

    // Résumé exécutif
    @FXML private Label nombreAffairesLabel;
    @FXML private Label nombreEncaissementsLabel;
    @FXML private Label totalEncaisseLabel;
    @FXML private Label totalEtatLabel;
    @FXML private Label totalCollectiviteLabel;
    @FXML private Label tauxRecouvrementLabel;

    // Tableau détaillé des affaires
    @FXML private TableView<AffaireRepartitionDTO> affairesTableView;
    @FXML private TableColumn<AffaireRepartitionDTO, String> numeroColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> dateColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> contrevenantColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> typeColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> montantAmendeColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> montantEncaisseColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> partEtatColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> partCollectiviteColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> chefDossierColumn;
    @FXML private TableColumn<AffaireRepartitionDTO, String> bureauColumn;

    // Statistiques par bureau
    @FXML private TableView<BureauStatDTO> bureauStatsTableView;
    @FXML private TableColumn<BureauStatDTO, String> bureauNomColumn;
    @FXML private TableColumn<BureauStatDTO, String> bureauMontantColumn;
    @FXML private TableColumn<BureauStatDTO, String> bureauPourcentageColumn;

    // Statistiques par agent
    @FXML private TableView<AgentStatDTO> agentStatsTableView;
    @FXML private TableColumn<AgentStatDTO, String> agentNomColumn;
    @FXML private TableColumn<AgentStatDTO, String> agentMontantColumn;
    @FXML private TableColumn<AgentStatDTO, String> agentPourcentageColumn;

    // Graphiques
    @FXML private PieChart repartitionPieChart;
    @FXML private BarChart<String, Number> bureauBarChart;
    @FXML private VBox graphiquesContainer;

    // Filtres et recherche
    @FXML private TextField rechercheField;
    @FXML private ComboBox<String> filtreBureauComboBox;
    @FXML private ComboBox<String> filtreAgentComboBox;
    @FXML private CheckBox afficherSeulementEncaissesCheckBox;

    private final RapportService rapportService;
    private RapportRepartitionDTO rapportActuel;
    private ObservableList<AffaireRepartitionDTO> affairesOriginales;
    private ObservableList<AffaireRepartitionDTO> affairesFiltrees;

    /**
     * DTOs pour les statistiques
     */
    public static class BureauStatDTO {
        private final String nom;
        private final BigDecimal montant;
        private final String pourcentage;

        public BureauStatDTO(String nom, BigDecimal montant, String pourcentage) {
            this.nom = nom;
            this.montant = montant;
            this.pourcentage = pourcentage;
        }

        public String getNom() { return nom; }
        public BigDecimal getMontant() { return montant; }
        public String getMontantFormatte() { return CurrencyFormatter.format(montant); }
        public String getPourcentage() { return pourcentage; }
    }

    public static class AgentStatDTO {
        private final String nom;
        private final BigDecimal montant;
        private final String pourcentage;

        public AgentStatDTO(String nom, BigDecimal montant, String pourcentage) {
            this.nom = nom;
            this.montant = montant;
            this.pourcentage = pourcentage;
        }

        public String getNom() { return nom; }
        public BigDecimal getMontant() { return montant; }
        public String getMontantFormatte() { return CurrencyFormatter.format(montant); }
        public String getPourcentage() { return pourcentage; }
    }

    public RapportRepartitionController() {
        this.rapportService = new RapportService();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupFilters();
        setupEventHandlers();

        logger.info("RapportRepartitionController initialisé");
    }

    private void setupTableColumns() {
        // Configuration des colonnes du tableau principal
        numeroColumn.setCellValueFactory(new PropertyValueFactory<>("numeroAffaire"));

        dateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(DateFormatter.formatDate(cellData.getValue().getDateCreation())));

        contrevenantColumn.setCellValueFactory(new PropertyValueFactory<>("contrevenantNom"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("contraventionType"));

        montantAmendeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(CurrencyFormatter.format(cellData.getValue().getMontantAmende())));

        montantEncaisseColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(CurrencyFormatter.format(cellData.getValue().getMontantEncaisse())));

        partEtatColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(CurrencyFormatter.format(cellData.getValue().getPartEtat())));

        partCollectiviteColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(CurrencyFormatter.format(cellData.getValue().getPartCollectivite())));

        chefDossierColumn.setCellValueFactory(new PropertyValueFactory<>("chefDossier"));
        bureauColumn.setCellValueFactory(new PropertyValueFactory<>("bureau"));

        // Configuration des colonnes statistiques bureau
        bureauNomColumn.setCellValueFactory(new PropertyValueFactory<>("nom"));
        bureauMontantColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getMontantFormatte()));
        bureauPourcentageColumn.setCellValueFactory(new PropertyValueFactory<>("pourcentage"));

        // Configuration des colonnes statistiques agent
        agentNomColumn.setCellValueFactory(new PropertyValueFactory<>("nom"));
        agentMontantColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getMontantFormatte()));
        agentPourcentageColumn.setCellValueFactory(new PropertyValueFactory<>("pourcentage"));

        // Style des colonnes numériques
        montantAmendeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        montantEncaisseColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        partEtatColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        partCollectiviteColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        bureauMontantColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        bureauPourcentageColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        agentMontantColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        agentPourcentageColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    private void setupFilters() {
        // Configuration des ComboBox de filtres
        filtreBureauComboBox.getItems().add("Tous les bureaux");
        filtreAgentComboBox.getItems().add("Tous les agents");

        filtreBureauComboBox.setValue("Tous les bureaux");
        filtreAgentComboBox.setValue("Tous les agents");
    }

    private void setupEventHandlers() {
        // Recherche en temps réel
        rechercheField.textProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());

        // Filtres
        filtreBureauComboBox.valueProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
        filtreAgentComboBox.valueProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
        afficherSeulementEncaissesCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());

        // Sélection dans le tableau
        affairesTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        afficherDetailsAffaire(newSelection);
                    }
                }
        );
    }

    /**
     * Charge et affiche un rapport de rétrocession
     */
    public void chargerRapport(RapportRepartitionDTO rapport) {
        try {
            this.rapportActuel = rapport;

            // Mise à jour des informations générales
            mettreAJourInformationsGenerales();

            // Mise à jour du résumé
            mettreAJourResume();

            // Chargement des données dans les tableaux
            chargerDonneesTableaux();

            // Mise à jour des graphiques
            mettreAJourGraphiques();

            // Mise à jour des filtres
            mettreAJourFiltres();

            logger.info("Rapport de rétrocession chargé: {} affaires", rapport.getNombreAffaires());

        } catch (Exception e) {
            logger.error("Erreur lors du chargement du rapport", e);
            AlertUtil.showErrorAlert("Erreur", "Chargement impossible",
                    "Impossible de charger le rapport: " + e.getMessage());
        }
    }

    private void mettreAJourInformationsGenerales() {
        titreLabel.setText("Rapport de Rétrocession des Amendes");
        periodeLabel.setText("Période: " + rapportActuel.getPeriodeLibelle());
        dateGenerationLabel.setText("Généré le: " + DateFormatter.formatDate(rapportActuel.getDateGeneration()));
    }

    private void mettreAJourResume() {
        nombreAffairesLabel.setText(String.valueOf(rapportActuel.getNombreAffaires()));
        nombreEncaissementsLabel.setText(String.valueOf(rapportActuel.getNombreEncaissements()));
        totalEncaisseLabel.setText(CurrencyFormatter.format(rapportActuel.getTotalEncaisse()));
        totalEtatLabel.setText(CurrencyFormatter.format(rapportActuel.getTotalEtat()));
        totalCollectiviteLabel.setText(CurrencyFormatter.format(rapportActuel.getTotalCollectivite()));

        // Calcul du taux de recouvrement
        BigDecimal totalAmende = rapportActuel.getAffaires().stream()
                .map(AffaireRepartitionDTO::getMontantAmende)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmende.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taux = rapportActuel.getTotalEncaisse()
                    .divide(totalAmende, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
            tauxRecouvrementLabel.setText(String.format("%.2f%%", taux.doubleValue()));
        } else {
            tauxRecouvrementLabel.setText("0.00%");
        }
    }

    private void chargerDonneesTableaux() {
        // Chargement du tableau principal
        affairesOriginales = FXCollections.observableArrayList(rapportActuel.getAffaires());
        affairesFiltrees = FXCollections.observableArrayList(affairesOriginales);
        affairesTableView.setItems(affairesFiltrees);

        // Chargement des statistiques par bureau
        ObservableList<BureauStatDTO> bureauStats = FXCollections.observableArrayList();
        for (Map.Entry<String, BigDecimal> entry : rapportActuel.getRepartitionParBureau().entrySet()) {
            BigDecimal pourcentage = entry.getValue()
                    .divide(rapportActuel.getTotalEncaisse(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));

            bureauStats.add(new BureauStatDTO(
                    entry.getKey(),
                    entry.getValue(),
                    String.format("%.2f%%", pourcentage.doubleValue())
            ));
        }
        bureauStatsTableView.setItems(bureauStats);

        // Chargement des statistiques par agent
        ObservableList<AgentStatDTO> agentStats = FXCollections.observableArrayList();
        for (Map.Entry<String, BigDecimal> entry : rapportActuel.getRepartitionParAgent().entrySet()) {
            BigDecimal pourcentage = entry.getValue()
                    .divide(rapportActuel.getTotalEncaisse(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));

            agentStats.add(new AgentStatDTO(
                    entry.getKey(),
                    entry.getValue(),
                    String.format("%.2f%%", pourcentage.doubleValue())
            ));
        }
        // Tri par montant décroissant
        agentStats.sort((a1, a2) -> a2.getMontant().compareTo(a1.getMontant()));
        agentStatsTableView.setItems(agentStats);
    }

    private void mettreAJourGraphiques() {
        // Graphique en secteurs État/Collectivités
        repartitionPieChart.getData().clear();

        PieChart.Data dataEtat = new PieChart.Data("État (60%)",
                rapportActuel.getTotalEtat().doubleValue());
        PieChart.Data dataCollectivite = new PieChart.Data("Collectivités (40%)",
                rapportActuel.getTotalCollectivite().doubleValue());

        repartitionPieChart.getData().addAll(dataEtat, dataCollectivite);

        // Graphique en barres par bureau
        bureauBarChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Montant encaissé par bureau");

        for (Map.Entry<String, BigDecimal> entry : rapportActuel.getRepartitionParBureau().entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue().doubleValue()));
        }

        bureauBarChart.getData().add(series);
    }

    private void mettreAJourFiltres() {
        // Mise à jour des options de filtrage
        filtreBureauComboBox.getItems().clear();
        filtreBureauComboBox.getItems().add("Tous les bureaux");

        rapportActuel.getRepartitionParBureau().keySet().forEach(bureau ->
                filtreBureauComboBox.getItems().add(bureau));

        filtreAgentComboBox.getItems().clear();
        filtreAgentComboBox.getItems().add("Tous les agents");

        rapportActuel.getRepartitionParAgent().keySet().forEach(agent ->
                filtreAgentComboBox.getItems().add(agent));

        filtreBureauComboBox.setValue("Tous les bureaux");
        filtreAgentComboBox.setValue("Tous les agents");
    }

    private void appliquerFiltres() {
        if (affairesOriginales == null) return;

        String recherche = rechercheField.getText().toLowerCase().trim();
        String filtreBureau = filtreBureauComboBox.getValue();
        String filtreAgent = filtreAgentComboBox.getValue();
        boolean seulementEncaisses = afficherSeulementEncaissesCheckBox.isSelected();

        affairesFiltrees.clear();

        affairesOriginales.stream()
                .filter(affaire -> {
                    // Filtre de recherche
                    if (!recherche.isEmpty()) {
                        String searchText = (affaire.getNumeroAffaire() + " " +
                                affaire.getContrevenantNom() + " " +
                                affaire.getContraventionType()).toLowerCase();
                        if (!searchText.contains(recherche)) {
                            return false;
                        }
                    }

                    // Filtre bureau
                    if (!"Tous les bureaux".equals(filtreBureau)) {
                        if (!filtreBureau.equals(affaire.getBureau())) {
                            return false;
                        }
                    }

                    // Filtre agent
                    if (!"Tous les agents".equals(filtreAgent)) {
                        if (!filtreAgent.equals(affaire.getChefDossier())) {
                            return false;
                        }
                    }

                    // Filtre encaissements
                    if (seulementEncaisses) {
                        if (affaire.getMontantEncaisse().compareTo(BigDecimal.ZERO) <= 0) {
                            return false;
                        }
                    }

                    return true;
                })
                .forEach(affairesFiltrees::add);

        // Mise à jour du compteur
        mettreAJourCompteurFiltres();
    }

    private void mettreAJourCompteurFiltres() {
        if (affairesOriginales != null && affairesFiltrees != null) {
            String compteur = String.format("Affichage: %d/%d affaires",
                    affairesFiltrees.size(), affairesOriginales.size());
            // Vous pouvez ajouter un label pour afficher ce compteur
        }
    }

    private void afficherDetailsAffaire(AffaireRepartitionDTO affaire) {
        // Cette méthode peut être étendue pour afficher plus de détails
        // dans un panneau latéral ou une popup
        logger.debug("Affaire sélectionnée: {}", affaire.getNumeroAffaire());
    }

    /**
     * Méthodes d'export spécialisées
     */
    @FXML
    public void exporterTableauPrincipal() {
        // Export du tableau filtré
        try {
            // Implémenter l'export spécifique du tableau
            logger.info("Export du tableau principal demandé");
        } catch (Exception e) {
            logger.error("Erreur lors de l'export du tableau", e);
            AlertUtil.showErrorAlert("Erreur", "Export impossible",
                    "Impossible d'exporter le tableau: " + e.getMessage());
        }
    }

    @FXML
    public void exporterStatistiques() {
        // Export des statistiques
        try {
            logger.info("Export des statistiques demandé");
        } catch (Exception e) {
            logger.error("Erreur lors de l'export des statistiques", e);
            AlertUtil.showErrorAlert("Erreur", "Export impossible",
                    "Impossible d'exporter les statistiques: " + e.getMessage());
        }
    }

    /**
     * Réinitialise tous les filtres
     */
    @FXML
    public void reinitialiserFiltres() {
        rechercheField.clear();
        filtreBureauComboBox.setValue("Tous les bureaux");
        filtreAgentComboBox.setValue("Tous les agents");
        afficherSeulementEncaissesCheckBox.setSelected(false);

        if (affairesOriginales != null) {
            affairesFiltrees.setAll(affairesOriginales);
        }

        logger.info("Filtres réinitialisés");
    }

    /**
     * Actualise les données du rapport
     */
    public void actualiserRapport() {
        if (rapportActuel != null) {
            try {
                // Recharger le rapport avec les mêmes paramètres
                RapportRepartitionDTO nouveauRapport = rapportService.genererRapportRepartition(
                        rapportActuel.getDateDebut(), rapportActuel.getDateFin());

                chargerRapport(nouveauRapport);

                AlertUtil.showInfoAlert("Succès", "Actualisation terminée",
                        "Le rapport a été actualisé avec les dernières données.");

            } catch (Exception e) {
                logger.error("Erreur lors de l'actualisation du rapport", e);
                AlertUtil.showErrorAlert("Erreur", "Actualisation échouée",
                        "Impossible d'actualiser le rapport: " + e.getMessage());
            }
        }
    }

    /**
     * Affiche les statistiques avancées
     */
    @FXML
    public void afficherStatistiquesAvancees() {
        if (rapportActuel == null) {
            AlertUtil.showWarningAlert("Attention", "Aucun rapport",
                    "Aucun rapport n'est actuellement chargé.");
            return;
        }

        try {
            Map<String, Object> statsAvancees = rapportService.calculerStatistiquesAvancees(rapportActuel);

            StringBuilder message = new StringBuilder();
            message.append("STATISTIQUES AVANCÉES\n\n");

            if (statsAvancees.containsKey("moyenneParAffaire")) {
                BigDecimal moyenne = (BigDecimal) statsAvancees.get("moyenneParAffaire");
                message.append("Montant moyen par affaire: ")
                        .append(CurrencyFormatter.format(moyenne)).append("\n");
            }

            if (statsAvancees.containsKey("tauxRecouvrement")) {
                BigDecimal taux = (BigDecimal) statsAvancees.get("tauxRecouvrement");
                message.append("Taux de recouvrement: ")
                        .append(String.format("%.2f%%", taux.doubleValue())).append("\n");
            }

            if (statsAvancees.containsKey("meilleurBureau")) {
                String bureau = (String) statsAvancees.get("meilleurBureau");
                BigDecimal montant = (BigDecimal) statsAvancees.get("montantMeilleurBureau");
                message.append("Bureau le plus performant: ").append(bureau)
                        .append(" (").append(CurrencyFormatter.format(montant)).append(")\n");
            }

            // Affichage dans une alerte personnalisée
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Statistiques Avancées");
            alert.setHeaderText("Analyse approfondie du rapport");
            alert.setContentText(message.toString());

            // Agrandir la fenêtre d'alerte
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();

        } catch (Exception e) {
            logger.error("Erreur lors du calcul des statistiques avancées", e);
            AlertUtil.showErrorAlert("Erreur", "Calcul impossible",
                    "Impossible de calculer les statistiques avancées: " + e.getMessage());
        }
    }

    /**
     * Méthodes utilitaires pour l'interface
     */
    public void surlignerAffairesForteValeur() {
        // Surligne les affaires avec des montants élevés
        if (rapportActuel == null) return;

        BigDecimal seuilElevé = rapportActuel.getTotalEncaisse()
                .divide(BigDecimal.valueOf(rapportActuel.getNombreAffaires()), 2, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("2")); // 2x la moyenne

        affairesTableView.setRowFactory(tv -> {
            TableRow<AffaireRepartitionDTO> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null && newItem.getMontantEncaisse().compareTo(seuilElevé) > 0) {
                    row.setStyle("-fx-background-color: #e8f5e8;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    /**
     * Getters pour l'accès externe
     */
    public RapportRepartitionDTO getRapportActuel() {
        return rapportActuel;
    }

    public boolean hasRapportCharge() {
        return rapportActuel != null;
    }

    public ObservableList<AffaireRepartitionDTO> getAffairesFiltrees() {
        return affairesFiltrees;
    }

    public TableView<AffaireRepartitionDTO> getAffairesTableView() {
        return affairesTableView;
    }

    /**
     * Configuration personnalisée des colonnes
     */
    public void configurerColonnesTableau(boolean afficherToutesLesColonnes) {
        if (afficherToutesLesColonnes) {
            // Afficher toutes les colonnes
            numeroColumn.setVisible(true);
            dateColumn.setVisible(true);
            contrevenantColumn.setVisible(true);
            typeColumn.setVisible(true);
            montantAmendeColumn.setVisible(true);
            montantEncaisseColumn.setVisible(true);
            partEtatColumn.setVisible(true);
            partCollectiviteColumn.setVisible(true);
            chefDossierColumn.setVisible(true);
            bureauColumn.setVisible(true);
        } else {
            // Mode simplifié - colonnes essentielles seulement
            numeroColumn.setVisible(true);
            dateColumn.setVisible(true);
            contrevenantColumn.setVisible(true);
            montantEncaisseColumn.setVisible(true);
            partEtatColumn.setVisible(true);
            partCollectiviteColumn.setVisible(true);

            typeColumn.setVisible(false);
            montantAmendeColumn.setVisible(false);
            chefDossierColumn.setVisible(false);
            bureauColumn.setVisible(false);
        }
    }

    /**
     * Export vers le presse-papiers
     */
    @FXML
    public void copierVersPressPapiers() {
        if (affairesFiltrees == null || affairesFiltrees.isEmpty()) {
            AlertUtil.showWarningAlert("Attention", "Aucune donnée",
                    "Aucune donnée à copier.");
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();

            // En-têtes
            sb.append("N° Affaire\tDate\tContrevenant\tMontant Encaissé\tPart État\tPart Collectivité\n");

            // Données
            for (AffaireRepartitionDTO affaire : affairesFiltrees) {
                sb.append(affaire.getNumeroAffaire()).append("\t");
                sb.append(DateFormatter.formatDate(affaire.getDateCreation())).append("\t");
                sb.append(affaire.getContrevenantNom()).append("\t");
                sb.append(CurrencyFormatter.format(affaire.getMontantEncaisse())).append("\t");
                sb.append(CurrencyFormatter.format(affaire.getPartEtat())).append("\t");
                sb.append(CurrencyFormatter.format(affaire.getPartCollectivite())).append("\n");
            }

            // Copie vers le presse-papiers
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(sb.toString());
            clipboard.setContent(content);

            AlertUtil.showInfoAlert("Succès", "Copie terminée",
                    String.format("%d lignes copiées vers le presse-papiers.", affairesFiltrees.size()));

        } catch (Exception e) {
            logger.error("Erreur lors de la copie vers le presse-papiers", e);
            AlertUtil.showErrorAlert("Erreur", "Copie impossible",
                    "Impossible de copier les données: " + e.getMessage());
        }
    }

    /**
     * Nettoyage lors de la fermeture
     */
    public void cleanup() {
        rapportActuel = null;

        if (affairesOriginales != null) {
            affairesOriginales.clear();
        }

        if (affairesFiltrees != null) {
            affairesFiltrees.clear();
        }

        affairesTableView.getItems().clear();
        bureauStatsTableView.getItems().clear();
        agentStatsTableView.getItems().clear();

        repartitionPieChart.getData().clear();
        bureauBarChart.getData().clear();

        logger.info("Nettoyage du contrôleur de rapport de rétrocession effectué");
    }

    /**
     * Méthodes de configuration avancée
     */
    public void configurerPourImpression() {
        // Configuration optimisée pour l'impression
        configurerColonnesTableau(false); // Mode simplifié
        afficherSeulementEncaissesCheckBox.setSelected(true); // Afficher seulement les encaissements
        appliquerFiltres();

        logger.info("Configuration pour impression appliquée");
    }

    public void restaurerConfigurationNormale() {
        // Restauration de la configuration normale
        configurerColonnesTableau(true); // Toutes les colonnes
        reinitialiserFiltres();

        logger.info("Configuration normale restaurée");
    }

    /**
     * Validation des données affichées
     */
    private boolean validerDonneesRapport() {
        if (rapportActuel == null) {
            logger.warn("Aucun rapport à valider");
            return false;
        }

        if (rapportActuel.getAffaires() == null || rapportActuel.getAffaires().isEmpty()) {
            logger.warn("Rapport sans affaires");
            return false;
        }

        // Validation de la cohérence des totaux
        BigDecimal totalCalcule = rapportActuel.getAffaires().stream()
                .map(AffaireRepartitionDTO::getMontantEncaisse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!totalCalcule.equals(rapportActuel.getTotalEncaisse())) {
            logger.warn("Incohérence détectée dans les totaux du rapport");
            return false;
        }

        return true;
    }
}