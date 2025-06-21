package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.EncaissementService;
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

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la liste des encaissements
 * Suit la même logique que AffaireListController et ContrevenantListController
 */
public class EncaissementListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementListController.class);

    // Filtres et recherche
    @FXML private TextField searchField;
    @FXML private ComboBox<StatutEncaissement> statutComboBox;
    @FXML private ComboBox<ModeReglement> modeReglementComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Actions principales
    @FXML private Button newEncaissementButton;
    @FXML private Button exportButton;

    // Tableau et sélection
    @FXML private TableView<EncaissementViewModel> encaissementsTableView;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private ComboBox<Integer> pageSizeComboBox;

    // Colonnes du tableau
    @FXML private TableColumn<EncaissementViewModel, Boolean> selectColumn;
    @FXML private TableColumn<EncaissementViewModel, String> referenceColumn;
    @FXML private TableColumn<EncaissementViewModel, String> affaireColumn;
    @FXML private TableColumn<EncaissementViewModel, Double> montantColumn;
    @FXML private TableColumn<EncaissementViewModel, LocalDate> dateEncaissementColumn;
    @FXML private TableColumn<EncaissementViewModel, ModeReglement> modeReglementColumn;
    @FXML private TableColumn<EncaissementViewModel, StatutEncaissement> statutColumn;
    @FXML private TableColumn<EncaissementViewModel, String> banqueColumn;
    @FXML private TableColumn<EncaissementViewModel, LocalDateTime> dateCreationColumn;
    @FXML private TableColumn<EncaissementViewModel, Void> actionsColumn;

    // Actions sur sélection
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button validateButton;
    @FXML private Button rejectButton;
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
    private EncaissementService encaissementService;
    private AuthenticationService authService;
    private ObservableList<EncaissementViewModel> encaissements;

    // Pagination
    private int currentPage = 1;
    private int pageSize = 25;
    private long totalElements = 0;
    private int totalPages = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        encaissementService = new EncaissementService();
        authService = AuthenticationService.getInstance();
        encaissements = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupEventHandlers();
        setupPagination();
        loadData();

        logger.info("Contrôleur de liste des encaissements initialisé");
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        // Configuration des ComboBox
        statutComboBox.getItems().add(null); // Option "Tous les statuts"
        statutComboBox.getItems().addAll(StatutEncaissement.values());
        statutComboBox.setConverter(new StringConverter<StatutEncaissement>() {
            @Override
            public String toString(StatutEncaissement statut) {
                return statut == null ? "Tous les statuts" : statut.getLibelle();
            }

            @Override
            public StatutEncaissement fromString(String string) {
                return null;
            }
        });

        modeReglementComboBox.getItems().add(null); // Option "Tous les modes"
        modeReglementComboBox.getItems().addAll(ModeReglement.values());
        modeReglementComboBox.setConverter(new StringConverter<ModeReglement>() {
            @Override
            public String toString(ModeReglement mode) {
                return mode == null ? "Tous les modes" : mode.getLibelle();
            }

            @Override
            public ModeReglement fromString(String string) {
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

        // Colonnes de données
        referenceColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getReference()));

        affaireColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getAffaireNumero()));

        montantColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getMontantEncaisse()));

        dateEncaissementColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getDateEncaissement()));

        modeReglementColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getModeReglement()));

        statutColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getStatut()));

        banqueColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getBanqueNom()));

        dateCreationColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getCreatedAt()));

        // Formatage des colonnes
        montantColumn.setCellFactory(col -> new TableCell<EncaissementViewModel, Double>() {
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

        dateEncaissementColumn.setCellFactory(col -> new TableCell<EncaissementViewModel, LocalDate>() {
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

        modeReglementColumn.setCellFactory(col -> new TableCell<EncaissementViewModel, ModeReglement>() {
            @Override
            protected void updateItem(ModeReglement mode, boolean empty) {
                super.updateItem(mode, empty);
                if (empty || mode == null) {
                    setText(null);
                } else {
                    setText(mode.getLibelle());
                }
            }
        });

        statutColumn.setCellFactory(col -> new TableCell<EncaissementViewModel, StatutEncaissement>() {
            @Override
            protected void updateItem(StatutEncaissement statut, boolean empty) {
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

        dateCreationColumn.setCellFactory(col -> new TableCell<EncaissementViewModel, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime dateTime, boolean empty) {
                super.updateItem(dateTime, empty);
                if (empty || dateTime == null) {
                    setText(null);
                } else {
                    setText(DateFormatter.formatDateTimeShort(dateTime));
                }
            }
        });

        // Colonne d'actions
        actionsColumn.setCellFactory(createActionButtonsCellFactory());

        // Données du tableau
        encaissementsTableView.setItems(encaissements);
        encaissementsTableView.setEditable(true);
    }

    /**
     * Crée la factory pour les boutons d'action
     */
    private Callback<TableColumn<EncaissementViewModel, Void>, TableCell<EncaissementViewModel, Void>> createActionButtonsCellFactory() {
        return param -> new TableCell<EncaissementViewModel, Void>() {
            private final Button viewButton = new Button("👁");
            private final Button editButton = new Button("✏");
            private final Button validateButton = new Button("✅");
            private final Button rejectButton = new Button("❌");
            private final HBox buttonsBox = new HBox(2, viewButton, editButton, validateButton, rejectButton);

            {
                viewButton.getStyleClass().add("button-icon");
                editButton.getStyleClass().add("button-icon");
                validateButton.getStyleClass().add("button-icon");
                rejectButton.getStyleClass().add("button-icon");

                viewButton.setTooltip(new Tooltip("Voir les détails"));
                editButton.setTooltip(new Tooltip("Modifier"));
                validateButton.setTooltip(new Tooltip("Valider"));
                rejectButton.setTooltip(new Tooltip("Rejeter"));

                viewButton.setOnAction(e -> {
                    EncaissementViewModel encaissement = getTableView().getItems().get(getIndex());
                    viewEncaissement(encaissement);
                });

                editButton.setOnAction(e -> {
                    EncaissementViewModel encaissement = getTableView().getItems().get(getIndex());
                    editEncaissement(encaissement);
                });

                validateButton.setOnAction(e -> {
                    EncaissementViewModel encaissement = getTableView().getItems().get(getIndex());
                    validateEncaissement(encaissement);
                });

                rejectButton.setOnAction(e -> {
                    EncaissementViewModel encaissement = getTableView().getItems().get(getIndex());
                    rejectEncaissement(encaissement);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    EncaissementViewModel encaissement = getTableView().getItems().get(getIndex());

                    // Afficher/masquer les boutons selon le statut
                    validateButton.setVisible(encaissement.getStatut() == StatutEncaissement.EN_ATTENTE);
                    rejectButton.setVisible(encaissement.getStatut() == StatutEncaissement.EN_ATTENTE);
                    editButton.setDisable(encaissement.getStatut() == StatutEncaissement.VALIDE);

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
        newEncaissementButton.setOnAction(e -> createNewEncaissement());
        exportButton.setOnAction(e -> exportData());

        // Actions sur sélection
        editButton.setOnAction(e -> editSelectedEncaissements());
        deleteButton.setOnAction(e -> deleteSelectedEncaissements());
        validateButton.setOnAction(e -> validateSelectedEncaissements());
        rejectButton.setOnAction(e -> rejectSelectedEncaissements());
        printButton.setOnAction(e -> printSelectedEncaissements());

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
        Platform.runLater(() -> {
            encaissements.clear();
            totalCountLabel.setText("Chargement...");
            paginationInfoLabel.setText("Chargement en cours...");
        });

        Task<List<EncaissementViewModel>> loadTask = new Task<List<EncaissementViewModel>>() {
            @Override
            protected List<EncaissementViewModel> call() throws Exception {
                // Récupération des critères de recherche
                String searchText = searchField.getText();
                StatutEncaissement statut = statutComboBox.getValue();
                ModeReglement modeReglement = modeReglementComboBox.getValue();
                LocalDate dateDebut = dateDebutPicker.getValue();
                LocalDate dateFin = dateFinPicker.getValue();

                logger.info("Chargement page {} (pageSize: {})", currentPage, pageSize);

                // Chargement des encaissements
                List<Encaissement> encaissementsList = encaissementService.searchEncaissements(
                        searchText, statut, modeReglement, dateDebut, dateFin, null, currentPage, pageSize);

                // Comptage total
                totalElements = encaissementService.countSearchEncaissements(
                        searchText, statut, modeReglement, dateDebut, dateFin, null);

                logger.info("Chargement terminé: {} encaissements trouvés sur {} total",
                        encaissementsList.size(), totalElements);

                // Conversion vers le modèle d'affichage
                return encaissementsList.stream()
                        .map(this::convertToViewModel)
                        .collect(Collectors.toList());
            }

            private EncaissementViewModel convertToViewModel(Encaissement encaissement) {
                EncaissementViewModel viewModel = new EncaissementViewModel();
                viewModel.setId(encaissement.getId());
                viewModel.setReference(encaissement.getReference());
                viewModel.setAffaireId(encaissement.getAffaireId());
                viewModel.setAffaireNumero("Affaire #" + encaissement.getAffaireId()); // Simplifié
                BigDecimal montantEncaisse = encaissement.getMontantEncaisse();
                viewModel.setMontantEncaisse(montantEncaisse != null ? montantEncaisse.doubleValue() : 0.0);
                viewModel.setDateEncaissement(encaissement.getDateEncaissement());
                viewModel.setModeReglement(encaissement.getModeReglement());
                viewModel.setStatut(encaissement.getStatut());
                viewModel.setBanqueId(encaissement.getBanqueId());
                viewModel.setBanqueNom(encaissement.getBanqueId() != null ? "Banque #" + encaissement.getBanqueId() : "");
                viewModel.setCreatedAt(encaissement.getCreatedAt());
                viewModel.setCreatedBy(encaissement.getCreatedBy());
                return viewModel;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    encaissements.clear();
                    encaissements.addAll(getValue());

                    // Mise à jour de la pagination
                    totalPages = (int) Math.ceil((double) totalElements / pageSize);
                    updatePaginationInfo();
                    updatePaginationButtons();
                    updatePaginationNumbers();

                    // Mise à jour du compteur
                    String countText = formatLargeNumber(totalElements) + " encaissement(s)";
                    totalCountLabel.setText(countText);

                    logger.debug("Interface mise à jour: {} encaissements affichés", encaissements.size());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des encaissements", getException());
                    totalCountLabel.setText("Erreur");
                    paginationInfoLabel.setText("Erreur de chargement");

                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les encaissements",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
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

    // Actions sur les encaissements

    private void performSearch() {
        currentPage = 1;
        loadData();
    }

    private void clearFilters() {
        searchField.clear();
        statutComboBox.setValue(null);
        modeReglementComboBox.setValue(null);
        dateDebutPicker.setValue(null);
        dateFinPicker.setValue(null);
        performSearch();
    }

    private void createNewEncaissement() {
        logger.info("Création d'un nouvel encaissement");
        AlertUtil.showInfoAlert("Nouvel encaissement",
                "Fonctionnalité en développement",
                "Le formulaire de création sera disponible prochainement.");
    }

    private void viewEncaissement(EncaissementViewModel encaissement) {
        logger.info("Affichage des détails de l'encaissement: {}", encaissement.getReference());
        AlertUtil.showInfoAlert("Détails de l'encaissement",
                "Encaissement: " + encaissement.getReference() + " - " +
                        CurrencyFormatter.format(encaissement.getMontantEncaisse()),
                "La vue de détail sera disponible prochainement.");
    }

    private void editEncaissement(EncaissementViewModel encaissement) {
        logger.info("Modification de l'encaissement: {}", encaissement.getReference());
        AlertUtil.showInfoAlert("Modification d'encaissement",
                "Encaissement: " + encaissement.getReference(),
                "Le formulaire de modification sera disponible prochainement.");
    }

    private void validateEncaissement(EncaissementViewModel encaissement) {
        if (encaissement.getStatut() != StatutEncaissement.EN_ATTENTE) {
            AlertUtil.showWarningAlert("Validation impossible",
                    "Statut incorrect",
                    "Seuls les encaissements en attente peuvent être validés.");
            return;
        }

        if (AlertUtil.showConfirmAlert("Confirmation de validation",
                "Valider l'encaissement",
                "Voulez-vous vraiment valider l'encaissement " + encaissement.getReference() + " ?")) {

            performValidation(encaissement);
        }
    }

    private void rejectEncaissement(EncaissementViewModel encaissement) {
        if (encaissement.getStatut() != StatutEncaissement.EN_ATTENTE) {
            AlertUtil.showWarningAlert("Rejet impossible",
                    "Statut incorrect",
                    "Seuls les encaissements en attente peuvent être rejetés.");
            return;
        }

        if (AlertUtil.showConfirmAlert("Confirmation de rejet",
                "Rejeter l'encaissement",
                "Voulez-vous vraiment rejeter l'encaissement " + encaissement.getReference() + " ?")) {

            performRejection(encaissement);
        }
    }

    private void performValidation(EncaissementViewModel encaissement) {
        Task<Void> validateTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String currentUser = authService.getCurrentUser().getUsername();
                encaissementService.validerEncaissement(encaissement.getId(), currentUser);
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showSuccessAlert("Validation réussie",
                            "Encaissement validé",
                            "L'encaissement " + encaissement.getReference() + " a été validé avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la validation", getException());
                    AlertUtil.showErrorAlert("Erreur de validation",
                            "Impossible de valider l'encaissement",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread validateThread = new Thread(validateTask);
        validateThread.setDaemon(true);
        validateThread.start();
    }

    private void performRejection(EncaissementViewModel encaissement) {
        Task<Void> rejectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String currentUser = authService.getCurrentUser().getUsername();
                encaissementService.rejeterEncaissement(encaissement.getId(), currentUser);
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showSuccessAlert("Rejet réussi",
                            "Encaissement rejeté",
                            "L'encaissement " + encaissement.getReference() + " a été rejeté avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du rejet", getException());
                    AlertUtil.showErrorAlert("Erreur de rejet",
                            "Impossible de rejeter l'encaissement",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread rejectThread = new Thread(rejectTask);
        rejectThread.setDaemon(true);
        rejectThread.start();
    }

    private void editSelectedEncaissements() {
        List<EncaissementViewModel> selected = getSelectedEncaissements();
        if (selected.size() == 1) {
            editEncaissement(selected.get(0));
        }
    }

    private void deleteSelectedEncaissements() {
        List<EncaissementViewModel> selected = getSelectedEncaissements();

        if (selected.isEmpty()) {
            return;
        }

        String message = selected.size() == 1
                ? "Voulez-vous vraiment supprimer l'encaissement " + selected.get(0).getReference() + " ?"
                : "Voulez-vous vraiment supprimer les " + selected.size() + " encaissements sélectionnés ?";

        if (AlertUtil.showConfirmAlert("Confirmation de suppression",
                "Supprimer les encaissements", message)) {

            performDeletion(selected);
        }
    }

    private void performDeletion(List<EncaissementViewModel> encaissements) {
        Task<Void> deleteTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (EncaissementViewModel encaissement : encaissements) {
                    encaissementService.deleteEncaissement(encaissement.getId());
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showInfoAlert("Suppression réussie",
                            "Encaissements supprimés",
                            encaissements.size() + " encaissement(s) supprimé(s) avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la suppression", getException());
                    AlertUtil.showErrorAlert("Erreur de suppression",
                            "Impossible de supprimer les encaissements",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread deleteThread = new Thread(deleteTask);
        deleteThread.setDaemon(true);
        deleteThread.start();
    }

    private void validateSelectedEncaissements() {
        List<EncaissementViewModel> selected = getSelectedEncaissements().stream()
                .filter(e -> e.getStatut() == StatutEncaissement.EN_ATTENTE)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            AlertUtil.showWarningAlert("Validation impossible",
                    "Aucun encaissement éligible",
                    "Aucun encaissement en attente n'est sélectionné.");
            return;
        }

        if (AlertUtil.showConfirmAlert("Confirmation de validation",
                "Valider les encaissements",
                "Voulez-vous vraiment valider les " + selected.size() + " encaissements sélectionnés ?")) {

            performBatchValidation(selected);
        }
    }

    private void rejectSelectedEncaissements() {
        List<EncaissementViewModel> selected = getSelectedEncaissements().stream()
                .filter(e -> e.getStatut() == StatutEncaissement.EN_ATTENTE)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            AlertUtil.showWarningAlert("Rejet impossible",
                    "Aucun encaissement éligible",
                    "Aucun encaissement en attente n'est sélectionné.");
            return;
        }

        if (AlertUtil.showConfirmAlert("Confirmation de rejet",
                "Rejeter les encaissements",
                "Voulez-vous vraiment rejeter les " + selected.size() + " encaissements sélectionnés ?")) {

            performBatchRejection(selected);
        }
    }

    private void performBatchValidation(List<EncaissementViewModel> encaissements) {
        Task<Void> batchTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String currentUser = authService.getCurrentUser().getUsername(); // AJOUTÉ
                for (EncaissementViewModel encaissement : encaissements) {
                    encaissementService.validerEncaissement(encaissement.getId(), currentUser); // CORRIGÉ
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showSuccessAlert("Validation réussie",
                            "Encaissements validés",
                            encaissements.size() + " encaissement(s) validé(s) avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la validation en lot", getException());
                    AlertUtil.showErrorAlert("Erreur de validation",
                            "Impossible de valider tous les encaissements",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread batchThread = new Thread(batchTask);
        batchThread.setDaemon(true);
        batchThread.start();
    }


    private void performBatchRejection(List<EncaissementViewModel> encaissements) {
        Task<Void> batchTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String currentUser = authService.getCurrentUser().getUsername(); // AJOUTÉ
                for (EncaissementViewModel encaissement : encaissements) {
                    encaissementService.rejeterEncaissement(encaissement.getId(), currentUser); // CORRIGÉ
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showSuccessAlert("Rejet réussi",
                            "Encaissements rejetés",
                            encaissements.size() + " encaissement(s) rejeté(s) avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du rejet en lot", getException());
                    AlertUtil.showErrorAlert("Erreur de rejet",
                            "Impossible de rejeter tous les encaissements",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread batchThread = new Thread(batchTask);
        batchThread.setDaemon(true);
        batchThread.start();
    }

    private void printSelectedEncaissements() {
        List<EncaissementViewModel> selected = getSelectedEncaissements();
        logger.info("Impression de {} encaissement(s)", selected.size());
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
        long selectedCount = encaissements.stream()
                .mapToLong(e -> e.isSelected() ? 1 : 0)
                .sum();

        long selectedPendingCount = encaissements.stream()
                .filter(EncaissementViewModel::isSelected)
                .mapToLong(e -> e.getStatut() == StatutEncaissement.EN_ATTENTE ? 1 : 0)
                .sum();

        editButton.setDisable(selectedCount != 1);
        deleteButton.setDisable(selectedCount == 0);
        validateButton.setDisable(selectedPendingCount == 0);
        rejectButton.setDisable(selectedPendingCount == 0);
        printButton.setDisable(selectedCount == 0);
    }

    private void toggleSelectAll() {
        boolean selectAll = selectAllCheckBox.isSelected();
        encaissements.forEach(encaissement -> encaissement.setSelected(selectAll));
        updateActionButtons();
    }

    private List<EncaissementViewModel> getSelectedEncaissements() {
        return encaissements.stream()
                .filter(EncaissementViewModel::isSelected)
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
     * Modèle d'affichage pour le tableau des encaissements
     */
    public static class EncaissementViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private Long id;
        private String reference;
        private Long affaireId;
        private String affaireNumero;
        private Double montantEncaisse;
        private LocalDate dateEncaissement;
        private ModeReglement modeReglement;
        private StatutEncaissement statut;
        private Long banqueId;
        private String banqueNom;
        private LocalDateTime createdAt;
        private String createdBy;

        // Getters et setters
        public boolean isSelected() { return selected.get(); }
        public BooleanProperty selectedProperty() { return selected; }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }

        public Long getAffaireId() { return affaireId; }
        public void setAffaireId(Long affaireId) { this.affaireId = affaireId; }

        public String getAffaireNumero() { return affaireNumero; }
        public void setAffaireNumero(String affaireNumero) { this.affaireNumero = affaireNumero; }

        public Double getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(Double montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public ModeReglement getModeReglement() { return modeReglement; }
        public void setModeReglement(ModeReglement modeReglement) { this.modeReglement = modeReglement; }

        public StatutEncaissement getStatut() { return statut; }
        public void setStatut(StatutEncaissement statut) { this.statut = statut; }

        public Long getBanqueId() { return banqueId; }
        public void setBanqueId(Long banqueId) { this.banqueId = banqueId; }

        public String getBanqueNom() { return banqueNom; }
        public void setBanqueNom(String banqueNom) { this.banqueNom = banqueNom; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }
}