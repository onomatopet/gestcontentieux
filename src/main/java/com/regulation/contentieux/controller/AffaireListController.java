package com.regulation.contentieux.controller;

import com.regulation.contentieux.Main;
import com.regulation.contentieux.util.FXMLLoaderUtil;
import com.regulation.contentieux.controller.AffaireFormController;
import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.model.Contrevenant;
import javafx.stage.Stage;
import javafx.stage.Modality;
import java.util.Optional;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.dao.ContrevenantDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la liste des affaires contentieuses
 * Version complète et fonctionnelle
 */
public class AffaireListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireListController.class);

    // Filtres et recherche
    @FXML private TextField searchField;
    @FXML private ComboBox<StatutAffaire> statutComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Actions principales
    @FXML private Button newAffaireButton;
    @FXML private Button exportButton;

    // Tableau et sélection
    @FXML private TableView<AffaireViewModel> affairesTableView;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private ComboBox<Integer> pageSizeComboBox;

    // Colonnes du tableau
    @FXML private TableColumn<AffaireViewModel, Boolean> selectColumn;
    @FXML private TableColumn<AffaireViewModel, String> numeroColumn;
    @FXML private TableColumn<AffaireViewModel, LocalDate> dateCreationColumn;
    @FXML private TableColumn<AffaireViewModel, String> contrevenantColumn;
    @FXML private TableColumn<AffaireViewModel, String> contraventionColumn;
    @FXML private TableColumn<AffaireViewModel, Double> montantColumn;
    @FXML private TableColumn<AffaireViewModel, StatutAffaire> statutColumn;
    @FXML private TableColumn<AffaireViewModel, String> bureauColumn;
    @FXML private TableColumn<AffaireViewModel, String> serviceColumn;
    @FXML private TableColumn<AffaireViewModel, Void> actionsColumn;

    // Actions sur sélection
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button duplicateButton;
    @FXML private Button printButton;

    // Pagination
    @FXML private Label totalCountLabel;
    @FXML private Label paginationInfoLabel;
    @FXML private TextField gotoPageField;
    @FXML private Button gotoPageButton;
    @FXML private Button firstPageButton;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button lastPageButton;
    @FXML private HBox pageNumbersContainer;

    // Services et données
    private AffaireDAO affaireDAO;
    private AuthenticationService authService;
    private ObservableList<AffaireViewModel> affaires;

    // Pagination
    private int currentPage = 1;
    private int pageSize = 25;
    private long totalElements = 0;
    private int totalPages = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        affaireDAO = new AffaireDAO();
        authService = AuthenticationService.getInstance();
        affaires = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupEventHandlers();
        setupPagination();
        loadData();

        logger.info("Contrôleur de liste des affaires initialisé");
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        // Configuration des ComboBox
        statutComboBox.getItems().add(null); // Option "Tous les statuts"
        statutComboBox.getItems().addAll(StatutAffaire.values());
        statutComboBox.setConverter(new StringConverter<StatutAffaire>() {
            @Override
            public String toString(StatutAffaire statut) {
                return statut == null ? "Tous les statuts" : statut.getLibelle();
            }

            @Override
            public StatutAffaire fromString(String string) {
                return null;
            }
        });

        // Tailles de page
        pageSizeComboBox.getItems().addAll(10, 25, 50, 100);
        pageSizeComboBox.setValue(pageSize);

        // Configuration initiale des boutons
        updateActionButtons();
    }

    /**
     * Configuration des colonnes du tableau
     */
    private void setupTableColumns() {
        // Colonne de sélection
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);

        // Colonnes de données avec PropertyValueFactory
        numeroColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNumeroAffaire()));

        dateCreationColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getDateCreation()));

        contrevenantColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getContrevenantNom()));

        contraventionColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getContraventionLibelle()));

        montantColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getMontantAmendeTotal()));

        statutColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getStatut()));

        bureauColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getBureauNom()));

        serviceColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getServiceNom()));

        // Formatage des colonnes
        dateCreationColumn.setCellFactory(col -> new TableCell<AffaireViewModel, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(DateFormatter.formatDate(date));
                }
            }
        });

        montantColumn.setCellFactory(col -> new TableCell<AffaireViewModel, Double>() {
            @Override
            protected void updateItem(Double montant, boolean empty) {
                super.updateItem(montant, empty);
                if (empty || montant == null) {
                    setText(null);
                } else {
                    setText(CurrencyFormatter.format(montant));
                }
            }
        });

        statutColumn.setCellFactory(col -> new TableCell<AffaireViewModel, StatutAffaire>() {
            @Override
            protected void updateItem(StatutAffaire statut, boolean empty) {
                super.updateItem(statut, empty);
                if (empty || statut == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(statut.getLibelle());
                    // Coloration selon le statut
                    setStyle("-fx-text-fill: " + statut.getCouleur() + "; -fx-font-weight: bold;");
                }
            }
        });

        // Colonne d'actions
        actionsColumn.setCellFactory(createActionButtonsCellFactory());

        // Données du tableau
        affairesTableView.setItems(affaires);
        affairesTableView.setEditable(true);
    }

    /**
     * Crée la factory pour les boutons d'action
     */
    private Callback<TableColumn<AffaireViewModel, Void>, TableCell<AffaireViewModel, Void>> createActionButtonsCellFactory() {
        return param -> new TableCell<AffaireViewModel, Void>() {
            private final Button viewButton = new Button("👁");
            private final Button editButton = new Button("✏");
            private final HBox buttonsBox = new HBox(2, viewButton, editButton);

            {
                viewButton.getStyleClass().add("button-icon");
                editButton.getStyleClass().add("button-icon");
                viewButton.setTooltip(new Tooltip("Voir les détails"));
                editButton.setTooltip(new Tooltip("Modifier"));

                viewButton.setOnAction(e -> {
                    AffaireViewModel affaire = getTableView().getItems().get(getIndex());
                    viewAffaire(affaire);
                });

                editButton.setOnAction(e -> {
                    AffaireViewModel affaire = getTableView().getItems().get(getIndex());
                    editAffaire(affaire);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(buttonsBox);
                }
            }
        };
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Recherche
        searchButton.setOnAction(e -> performSearch());
        clearFiltersButton.setOnAction(e -> clearFilters());

        // Recherche en temps réel
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() >= 2 || newVal.isEmpty()) {
                performSearch();
            }
        });

        // Actions principales
        newAffaireButton.setOnAction(e -> createNewAffaire());
        exportButton.setOnAction(e -> exportData());

        // Actions sur sélection
        editButton.setOnAction(e -> editSelectedAffaires());
        deleteButton.setOnAction(e -> deleteSelectedAffaires());
        duplicateButton.setOnAction(e -> duplicateSelectedAffaires());
        printButton.setOnAction(e -> printSelectedAffaires());

        // Sélection
        selectAllCheckBox.setOnAction(e -> toggleSelectAll());

        // Pagination
        pageSizeComboBox.setOnAction(e -> {
            pageSize = pageSizeComboBox.getValue();
            currentPage = 1;
            loadData();
        });

        gotoPageButton.setOnAction(e -> gotoPage());
        firstPageButton.setOnAction(e -> gotoFirstPage());
        previousPageButton.setOnAction(e -> gotoPreviousPage());
        nextPageButton.setOnAction(e -> gotoNextPage());
        lastPageButton.setOnAction(e -> gotoLastPage());
    }

    /**
     * Configuration de la pagination
     */
    private void setupPagination() {
        updatePaginationInfo();
        updatePaginationButtons();
    }

    /**
     * Charge les données
     */
    private void loadData() {
        // Afficher immédiatement un indicateur de chargement
        Platform.runLater(() -> {
            affaires.clear();
            // Ajouter un placeholder temporaire
            totalCountLabel.setText("Chargement...");
            paginationInfoLabel.setText("Chargement en cours...");
        });

        Task<List<AffaireViewModel>> loadTask = new Task<List<AffaireViewModel>>() {
            @Override
            protected List<AffaireViewModel> call() throws Exception {
                int offset = (currentPage - 1) * pageSize;

                // Récupération des critères de recherche
                String searchText = searchField.getText();
                StatutAffaire statut = statutComboBox.getValue();
                LocalDate dateDebut = dateDebutPicker.getValue();
                LocalDate dateFin = dateFinPicker.getValue();

                logger.info("Chargement page {} (offset: {}, limit: {})", currentPage, offset, pageSize);

                // OPTIMISÉ: Chargement avec timeout pour éviter les blocages
                List<Affaire> affairesList = affaireDAO.searchAffaires(
                        searchText, statut, dateDebut, dateFin, null, offset, pageSize);

                // Comptage total (optimisé)
                totalElements = affaireDAO.countSearchAffaires(
                        searchText, statut, dateDebut, dateFin, null);

                logger.info("Chargement terminé: {} affaires trouvées sur {} total",
                        affairesList.size(), totalElements);

                // Conversion vers le modèle d'affichage
                return affairesList.stream()
                        .map(this::convertToViewModel)
                        .collect(Collectors.toList());
            }

            private AffaireViewModel convertToViewModel(Affaire affaire) {
                AffaireViewModel viewModel = new AffaireViewModel();
                viewModel.setId(affaire.getId());
                viewModel.setNumeroAffaire(affaire.getNumeroAffaire());
                viewModel.setDateCreation(affaire.getDateCreation());
                viewModel.setMontantAmendeTotal(affaire.getMontantAmendeTotal());
                viewModel.setStatut(affaire.getStatut());

                // OPTIMISÉ: Affichage simple pour éviter les jointures coûteuses
                viewModel.setContrevenantNom("Contrevenant #" + affaire.getContrevenantId());
                viewModel.setContraventionLibelle("Contravention #" + affaire.getContraventionId());
                viewModel.setBureauNom(affaire.getBureauId() != null ? "Bureau #" + affaire.getBureauId() : "");
                viewModel.setServiceNom(affaire.getServiceId() != null ? "Service #" + affaire.getServiceId() : "");

                return viewModel;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    affaires.clear();
                    affaires.addAll(getValue());

                    // Mise à jour de la pagination
                    totalPages = (int) Math.ceil((double) totalElements / pageSize);
                    updatePaginationInfo();
                    updatePaginationButtons();
                    updatePaginationNumbers();

                    // Mise à jour du compteur avec format lisible
                    String countText = formatLargeNumber(totalElements) + " affaire(s)";
                    totalCountLabel.setText(countText);

                    logger.debug("Interface mise à jour: {} affaires affichées", affaires.size());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des affaires", getException());
                    totalCountLabel.setText("Erreur");
                    paginationInfoLabel.setText("Erreur de chargement");

                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les affaires",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        // Configuration du thread avec priorité normale
        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.setPriority(Thread.NORM_PRIORITY);
        loadThread.start();
    }

    /**
     * Formate les grands nombres de manière lisible
     */
    private String formatLargeNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format("%.1fk", number / 1000.0);
        } else {
            return String.format("%.1fM", number / 1000000.0);
        }
    }

    // Actions sur les affaires

    private void performSearch() {
        currentPage = 1;
        loadData();
    }

    private void clearFilters() {
        searchField.clear();
        statutComboBox.setValue(null);
        dateDebutPicker.setValue(null);
        dateFinPicker.setValue(null);
        performSearch();
    }

    /**
     * MISE À JOUR : Crée une nouvelle affaire avec le formulaire - REMPLACE LA MÉTHODE EXISTANTE
     */
    private void createNewAffaire() {
        try {
            logger.info("Ouverture du formulaire de création d'affaire");

            // Chargement du formulaire FXML
            FXMLLoaderUtil.LoadResult<AffaireFormController> result =
                    FXMLLoaderUtil.loadWithController("view/affaire-form.fxml");

            // Création de la fenêtre popup
            Stage formStage = new Stage();
            formStage.setTitle("Nouvelle Affaire - " + Main.getAppTitle());
            formStage.setScene(new javafx.scene.Scene(result.getParent(), 900, 700));
            formStage.setResizable(true);
            formStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            // Configuration du contrôleur
            AffaireFormController controller = result.getController();

            // Génération automatique du numéro d'affaire
            try {
                String nextNumero = affaireDAO.generateNextNumeroAffaire();
                controller.setDefaultValues(nextNumero, null);
            } catch (Exception e) {
                logger.warn("Impossible de pré-générer le numéro d'affaire", e);
            }

            // Gestion de la fermeture
            formStage.setOnHidden(e -> {
                logger.info("Formulaire d'affaire fermé, actualisation de la liste");
                loadData(); // Rafraîchir la liste
            });

            // Affichage de la fenêtre
            formStage.showAndWait();

        } catch (Exception e) {
            logger.error("Erreur lors de l'ouverture du formulaire d'affaire", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir le formulaire",
                    "Une erreur technique s'est produite : " + e.getMessage());
        }
    }

    /**
     * MISE À JOUR : Affiche les détails d'une affaire - AMÉLIORE LA MÉTHODE EXISTANTE
     */
    private void viewAffaire(AffaireViewModel affaireViewModel) {
        try {
            logger.info("Affichage des détails de l'affaire: {}",
                    affaireViewModel.getNumeroAffaire());

            // Récupération de l'affaire complète depuis la base
            Optional<Affaire> affaireOpt = affaireDAO.findById(affaireViewModel.getId());
            if (affaireOpt.isEmpty()) {
                AlertUtil.showErrorAlert("Erreur",
                        "Affaire introuvable",
                        "L'affaire demandée n'existe plus en base de données.");
                return;
            }

            Affaire affaire = affaireOpt.get();

            // Construction du message de détails
            StringBuilder details = new StringBuilder();
            details.append("Numéro d'affaire : ").append(affaire.getNumeroAffaire()).append("\n");
            details.append("Date de création : ").append(DateFormatter.formatDate(affaire.getDateCreation())).append("\n");
            details.append("Montant amende : ").append(CurrencyFormatter.format(affaire.getMontantAmendeTotal())).append("\n");
            details.append("Statut : ").append(affaire.getStatut().getLibelle()).append("\n");
            details.append("Contrevenant ID : ").append(affaire.getContrevenantId()).append("\n");

            if (affaire.getCreatedBy() != null) {
                details.append("Créé par : ").append(affaire.getCreatedBy()).append("\n");
            }
            if (affaire.getCreatedAt() != null) {
                details.append("Créé le : ").append(DateFormatter.formatDateTime(affaire.getCreatedAt())).append("\n");
            }
            if (affaire.getUpdatedBy() != null) {
                details.append("Modifié par : ").append(affaire.getUpdatedBy()).append("\n");
            }
            if (affaire.getUpdatedAt() != null) {
                details.append("Modifié le : ").append(DateFormatter.formatDateTime(affaire.getUpdatedAt())).append("\n");
            }

            // Affichage dans une alerte d'information
            AlertUtil.showInfoAlert("Détails de l'affaire",
                    "Affaire : " + affaire.getNumeroAffaire(),
                    details.toString());

        } catch (Exception e) {
            logger.error("Erreur lors de l'affichage des détails", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'afficher les détails",
                    "Une erreur technique s'est produite : " + e.getMessage());
        }
    }

    /**
     * MISE À JOUR : Édite une affaire avec le formulaire - REMPLACE LA MÉTHODE EXISTANTE
     */
    private void editAffaire(AffaireViewModel affaireViewModel) {
        try {
            logger.info("Ouverture du formulaire d'édition pour l'affaire: {}",
                    affaireViewModel.getNumeroAffaire());

            // Récupération de l'affaire complète depuis la base
            Optional<Affaire> affaireOpt = affaireDAO.findById(affaireViewModel.getId());
            if (affaireOpt.isEmpty()) {
                AlertUtil.showErrorAlert("Erreur",
                        "Affaire introuvable",
                        "L'affaire demandée n'existe plus en base de données.");
                return;
            }

            Affaire affaire = affaireOpt.get();

            // Vérification des permissions d'édition
            if (!affaire.peutEtreModifiee()) {
                AlertUtil.showWarningAlert("Modification impossible",
                        "Cette affaire ne peut pas être modifiée",
                        "Statut actuel : " + affaire.getStatut().getLibelle());
                return;
            }

            // Chargement du formulaire FXML
            FXMLLoaderUtil.LoadResult<AffaireFormController> result =
                    FXMLLoaderUtil.loadWithController("view/affaire-form.fxml");

            // Création de la fenêtre popup
            Stage formStage = new Stage();
            formStage.setTitle("Modifier l'affaire " + affaire.getNumeroAffaire() +
                    " - " + Main.getAppTitle());
            formStage.setScene(new javafx.scene.Scene(result.getParent(), 900, 700));
            formStage.setResizable(true);
            formStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            // Configuration du contrôleur en mode édition
            AffaireFormController controller = result.getController();
            controller.setAffaireToEdit(affaire);

            // Gestion de la fermeture
            formStage.setOnHidden(e -> {
                logger.info("Formulaire d'édition fermé, actualisation de la liste");
                loadData(); // Rafraîchir la liste
            });

            // Affichage de la fenêtre
            formStage.showAndWait();

        } catch (Exception e) {
            logger.error("Erreur lors de l'ouverture du formulaire d'édition", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible d'ouvrir le formulaire d'édition",
                    "Une erreur technique s'est produite : " + e.getMessage());
        }
    }

    /**
     * MISE À JOUR : Actions sur les affaires sélectionnées - AMÉLIORE LES MÉTHODES EXISTANTES
     */
    private void editSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();

        if (selected.isEmpty()) {
            AlertUtil.showWarningAlert("Aucune sélection",
                    "Aucune affaire sélectionnée",
                    "Veuillez sélectionner au moins une affaire à modifier.");
            return;
        }

        if (selected.size() == 1) {
            editAffaire(selected.get(0));
        } else {
            // Édition en lot (future fonctionnalité)
            AlertUtil.showInfoAlert("Édition en lot",
                    "Fonctionnalité en développement",
                    "L'édition en lot de " + selected.size() + " affaires sera disponible prochainement.");
        }
    }


    private void deleteSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();

        if (selected.isEmpty()) {
            return;
        }

        String message = selected.size() == 1
                ? "Voulez-vous vraiment supprimer l'affaire " + selected.get(0).getNumeroAffaire() + " ?"
                : "Voulez-vous vraiment supprimer les " + selected.size() + " affaires sélectionnées ?";

        if (AlertUtil.showConfirmAlert("Confirmation de suppression",
                "Supprimer les affaires", message)) {

            performDeletion(selected);
        }
    }

    private void performDeletion(List<AffaireViewModel> affaires) {
        Task<Void> deleteTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                List<Long> ids = affaires.stream()
                        .map(AffaireViewModel::getId)
                        .collect(Collectors.toList());

                affaireDAO.deleteAllById(ids);
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showInfoAlert("Suppression réussie",
                            "Affaires supprimées",
                            affaires.size() + " affaire(s) supprimée(s) avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la suppression", getException());
                    AlertUtil.showErrorAlert("Erreur de suppression",
                            "Impossible de supprimer les affaires",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread deleteThread = new Thread(deleteTask);
        deleteThread.setDaemon(true);
        deleteThread.start();
    }

    private void duplicateSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();

        if (selected.isEmpty()) {
            AlertUtil.showWarningAlert("Aucune sélection",
                    "Aucune affaire sélectionnée",
                    "Veuillez sélectionner une affaire à dupliquer.");
            return;
        }

        if (selected.size() == 1) {
            duplicateAffaire(selected.get(0));
        } else {
            AlertUtil.showWarningAlert("Sélection multiple",
                    "Duplication multiple non supportée",
                    "Veuillez sélectionner une seule affaire à dupliquer.");
        }
    }

    /**
     * NOUVELLE MÉTHODE : Gère la duplication d'une affaire
     */
    private void duplicateAffaire(AffaireViewModel affaireViewModel) {
        try {
            logger.info("Duplication de l'affaire: {}", affaireViewModel.getNumeroAffaire());

            // Récupération de l'affaire originale
            Optional<Affaire> affaireOpt = affaireDAO.findById(affaireViewModel.getId());
            if (affaireOpt.isEmpty()) {
                AlertUtil.showErrorAlert("Erreur",
                        "Affaire introuvable",
                        "L'affaire à dupliquer n'existe plus en base de données.");
                return;
            }

            Affaire affaireOriginale = affaireOpt.get();

            // Confirmation de duplication
            if (!AlertUtil.showConfirmAlert("Confirmation de duplication",
                    "Dupliquer l'affaire " + affaireOriginale.getNumeroAffaire(),
                    "Voulez-vous vraiment créer une copie de cette affaire ?")) {
                return;
            }

            // Chargement du formulaire FXML
            FXMLLoaderUtil.LoadResult<AffaireFormController> result =
                    FXMLLoaderUtil.loadWithController("view/affaire-form.fxml");

            // Création de la fenêtre popup
            Stage formStage = new Stage();
            formStage.setTitle("Dupliquer l'affaire " + affaireOriginale.getNumeroAffaire() +
                    " - " + Main.getAppTitle());
            formStage.setScene(new javafx.scene.Scene(result.getParent(), 900, 700));
            formStage.setResizable(true);
            formStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            // Configuration du contrôleur avec les données de l'affaire originale
            AffaireFormController controller = result.getController();

            // Génération d'un nouveau numéro d'affaire
            try {
                String nextNumero = affaireDAO.generateNextNumeroAffaire();

                // Récupération du contrevenant si possible
                Contrevenant contrevenant = null;
                try {
                    ContrevenantDAO contrevenantDAO = new ContrevenantDAO();
                    Optional<Contrevenant> contrevenantOpt = contrevenantDAO.findById(affaireOriginale.getContrevenantId());
                    if (contrevenantOpt.isPresent()) {
                        contrevenant = contrevenantOpt.get();
                    }
                } catch (Exception e) {
                    logger.warn("Impossible de récupérer le contrevenant pour la duplication", e);
                }

                // Configuration des valeurs par défaut
                controller.setDefaultValues(nextNumero, contrevenant);

            } catch (Exception e) {
                logger.warn("Impossible de pré-remplir les données pour la duplication", e);
            }

            // Gestion de la fermeture
            formStage.setOnHidden(e -> {
                logger.info("Formulaire de duplication fermé, actualisation de la liste");
                loadData(); // Rafraîchir la liste
            });

            // Affichage de la fenêtre
            formStage.showAndWait();

        } catch (Exception e) {
            logger.error("Erreur lors de la duplication de l'affaire", e);
            AlertUtil.showErrorAlert("Erreur",
                    "Impossible de dupliquer l'affaire",
                    "Une erreur technique s'est produite : " + e.getMessage());
        }
    }

    private void printSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();
        logger.info("Impression de {} affaire(s)", selected.size());
        AlertUtil.showInfoAlert("Impression",
                "Fonctionnalité en développement",
                "L'impression sera disponible prochainement.");
    }

    private void exportData() {
        logger.info("Export des données");
        AlertUtil.showInfoAlert("Export",
                "Fonctionnalité en développement",
                "L'export sera disponible prochainement.");
    }

    // Utilitaires

    private void updateActionButtons() {
        long selectedCount = affaires.stream()
                .mapToLong(a -> a.isSelected() ? 1 : 0)
                .sum();

        editButton.setDisable(selectedCount != 1);
        deleteButton.setDisable(selectedCount == 0);
        duplicateButton.setDisable(selectedCount != 1);
        printButton.setDisable(selectedCount == 0);
    }

    private void toggleSelectAll() {
        boolean selectAll = selectAllCheckBox.isSelected();
        affaires.forEach(affaire -> affaire.setSelected(selectAll));
        updateActionButtons();
    }

    private List<AffaireViewModel> getSelectedAffaires() {
        return affaires.stream()
                .filter(AffaireViewModel::isSelected)
                .collect(Collectors.toList());
    }

    // Pagination

    private void updatePaginationInfo() {
        int start = Math.min((currentPage - 1) * pageSize + 1, (int) totalElements);
        int end = Math.min(currentPage * pageSize, (int) totalElements);

        paginationInfoLabel.setText(String.format("Affichage de %d à %d sur %d résultats",
                start, end, totalElements));
    }

    private void updatePaginationButtons() {
        firstPageButton.setDisable(currentPage <= 1);
        previousPageButton.setDisable(currentPage <= 1);
        nextPageButton.setDisable(currentPage >= totalPages);
        lastPageButton.setDisable(currentPage >= totalPages);
    }

    private void updatePaginationNumbers() {
        pageNumbersContainer.getChildren().clear();

        if (totalPages <= 1) {
            return;
        }

        int maxButtons = 10;
        int start = Math.max(1, currentPage - maxButtons / 2);
        int end = Math.min(totalPages, start + maxButtons - 1);

        if (end - start < maxButtons - 1) {
            start = Math.max(1, end - maxButtons + 1);
        }

        for (int i = start; i <= end; i++) {
            Button pageButton = new Button(String.valueOf(i));
            pageButton.getStyleClass().add("pagination-button");

            if (i == currentPage) {
                pageButton.getStyleClass().add("current-page");
                pageButton.setDisable(true);
            }

            final int pageNumber = i;
            pageButton.setOnAction(e -> gotoPage(pageNumber));

            pageNumbersContainer.getChildren().add(pageButton);
        }
    }

    private void gotoPage() {
        try {
            int page = Integer.parseInt(gotoPageField.getText());
            gotoPage(page);
        } catch (NumberFormatException e) {
            AlertUtil.showWarningAlert("Page invalide",
                    "Numéro de page incorrect",
                    "Veuillez saisir un numéro de page valide.");
        }
    }

    private void gotoPage(int page) {
        if (page >= 1 && page <= totalPages && page != currentPage) {
            currentPage = page;
            gotoPageField.clear();
            loadData();
        }
    }

    private void gotoFirstPage() {
        gotoPage(1);
    }

    private void gotoPreviousPage() {
        if (currentPage > 1) {
            gotoPage(currentPage - 1);
        }
    }

    private void gotoNextPage() {
        if (currentPage < totalPages) {
            gotoPage(currentPage + 1);
        }
    }

    private void gotoLastPage() {
        gotoPage(totalPages);
    }

    public void refresh() {
        loadData();
    }

    /**
     * Modèle d'affichage pour le tableau
     */
    public static class AffaireViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private Long id;
        private String numeroAffaire;
        private LocalDate dateCreation;
        private String contrevenantNom;
        private String contraventionLibelle;
        private Double montantAmendeTotal;
        private StatutAffaire statut;
        private String bureauNom;
        private String serviceNom;

        // Getters et setters
        public boolean isSelected() { return selected.get(); }
        public BooleanProperty selectedProperty() { return selected; }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getNumeroAffaire() { return numeroAffaire; }
        public void setNumeroAffaire(String numeroAffaire) { this.numeroAffaire = numeroAffaire; }

        public LocalDate getDateCreation() { return dateCreation; }
        public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

        public String getContrevenantNom() { return contrevenantNom; }
        public void setContrevenantNom(String contrevenantNom) { this.contrevenantNom = contrevenantNom; }

        public String getContraventionLibelle() { return contraventionLibelle; }
        public void setContraventionLibelle(String contraventionLibelle) { this.contraventionLibelle = contraventionLibelle; }

        public Double getMontantAmendeTotal() { return montantAmendeTotal; }
        public void setMontantAmendeTotal(Double montantAmendeTotal) { this.montantAmendeTotal = montantAmendeTotal; }

        public StatutAffaire getStatut() { return statut; }
        public void setStatut(StatutAffaire statut) { this.statut = statut; }

        public String getBureauNom() { return bureauNom; }
        public void setBureauNom(String bureauNom) { this.bureauNom = bureauNom; }

        public String getServiceNom() { return serviceNom; }
        public void setServiceNom(String serviceNom) { this.serviceNom = serviceNom; }
    }
}