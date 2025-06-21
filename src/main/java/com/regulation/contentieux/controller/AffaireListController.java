package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.model.Affaire;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la liste des affaires - OPTIMISÉ
 * Version corrigée avec gestion des erreurs de compilation
 */
public class AffaireListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireListController.class);

    // Recherche et filtres
    @FXML private TextField searchField;
    @FXML private ComboBox<StatutAffaire> statutComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Tableau des affaires
    @FXML private TableView<AffaireViewModel> affairesTableView;

    // Colonnes du tableau
    @FXML private TableColumn<AffaireViewModel, Boolean> selectColumn;
    @FXML private TableColumn<AffaireViewModel, String> numeroAffaireColumn;
    @FXML private TableColumn<AffaireViewModel, LocalDate> dateCreationColumn;
    @FXML private TableColumn<AffaireViewModel, String> contrevenantColumn;
    @FXML private TableColumn<AffaireViewModel, String> contraventionColumn;
    @FXML private TableColumn<AffaireViewModel, Double> montantColumn;
    @FXML private TableColumn<AffaireViewModel, StatutAffaire> statutColumn;
    @FXML private TableColumn<AffaireViewModel, String> bureauColumn;
    @FXML private TableColumn<AffaireViewModel, String> serviceColumn;
    @FXML private TableColumn<AffaireViewModel, Void> actionsColumn;

    // Actions globales
    @FXML private Button newAffaireButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button viewDetailsButton;
    @FXML private Button printButton;
    @FXML private Button exportButton;

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
                return StatutAffaire.fromLibelle(string);
            }
        });

        // Configuration des champs de recherche
        searchField.setPromptText("Rechercher par numéro, contrevenant...");
        dateDebutPicker.setPromptText("Date de début");
        dateFinPicker.setPromptText("Date de fin");

        // Configuration du tableau
        affairesTableView.setItems(affaires);
        affairesTableView.setRowFactory(tv -> {
            TableRow<AffaireViewModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    viewAffaireDetails(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Configuration des colonnes du tableau
     */
    private void setupTableColumns() {
        // Colonne de sélection
        selectColumn.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);

        // Colonnes de données
        numeroAffaireColumn.setCellValueFactory(new PropertyValueFactory<>("numeroAffaire"));
        dateCreationColumn.setCellValueFactory(new PropertyValueFactory<>("dateCreation"));
        contrevenantColumn.setCellValueFactory(new PropertyValueFactory<>("contrevenantNom"));
        contraventionColumn.setCellValueFactory(new PropertyValueFactory<>("contraventionLibelle"));
        montantColumn.setCellValueFactory(new PropertyValueFactory<>("montantAmendeTotal"));
        statutColumn.setCellValueFactory(new PropertyValueFactory<>("statut"));
        bureauColumn.setCellValueFactory(new PropertyValueFactory<>("bureauNom"));
        serviceColumn.setCellValueFactory(new PropertyValueFactory<>("serviceNom"));

        // Formatage des colonnes
        dateCreationColumn.setCellFactory(column -> new TableCell<AffaireViewModel, LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DateFormatter.format(item));
            }
        });

        montantColumn.setCellFactory(column -> new TableCell<AffaireViewModel, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyFormatter.format(item));
            }
        });

        statutColumn.setCellFactory(column -> new TableCell<AffaireViewModel, StatutAffaire>() {
            @Override
            protected void updateItem(StatutAffaire item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.getLibelle());
                    // Appliquer des styles selon le statut
                    switch (item) {
                        case OUVERTE:
                            setStyle("-fx-text-fill: blue;");
                            break;
                        case EN_COURS:
                            setStyle("-fx-text-fill: orange;");
                            break;
                        case SOLDEE:
                            setStyle("-fx-text-fill: green;");
                            break;
                        case ANNULEE:
                            setStyle("-fx-text-fill: red;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });

        // Colonne d'actions
        actionsColumn.setCellFactory(param -> new TableCell<AffaireViewModel, Void>() {
            private final Button viewButton = new Button("Voir");
            private final Button editButton = new Button("Modifier");
            private final HBox buttons = new HBox(5, viewButton, editButton);

            {
                viewButton.setOnAction(e -> {
                    AffaireViewModel affaire = getTableView().getItems().get(getIndex());
                    viewAffaireDetails(affaire);
                });

                editButton.setOnAction(e -> {
                    AffaireViewModel affaire = getTableView().getItems().get(getIndex());
                    editAffaire(affaire);
                });

                viewButton.getStyleClass().add("btn-sm");
                editButton.getStyleClass().add("btn-sm");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Boutons d'action
        searchButton.setOnAction(e -> performSearch());
        clearFiltersButton.setOnAction(e -> clearFilters());
        newAffaireButton.setOnAction(e -> createNewAffaire());
        editButton.setOnAction(e -> editSelectedAffaires());
        deleteButton.setOnAction(e -> deleteSelectedAffaires());
        viewDetailsButton.setOnAction(e -> viewSelectedAffairesDetails());
        printButton.setOnAction(e -> printSelectedAffaires());
        exportButton.setOnAction(e -> exportAffaires());

        // Recherche en temps réel
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > 2) {
                performSearch();
            }
        });

        // Gestion de la sélection
        affaires.addListener((javafx.collections.ListChangeListener<AffaireViewModel>) change -> {
            updateSelectionButtons();
        });
    }

    /**
     * Configuration de la pagination
     */
    private void setupPagination() {
        firstPageButton.setOnAction(e -> gotoFirstPage());
        previousPageButton.setOnAction(e -> gotoPreviousPage());
        nextPageButton.setOnAction(e -> gotoNextPage());
        lastPageButton.setOnAction(e -> gotoLastPage());

        gotoPageButton.setOnAction(e -> {
            try {
                int page = Integer.parseInt(gotoPageField.getText());
                gotoPage(page);
            } catch (NumberFormatException ex) {
                AlertUtil.showWarningAlert("Page invalide",
                        "Numéro de page incorrect",
                        "Veuillez saisir un numéro de page valide.");
            }
        });
    }

    /**
     * Met à jour les boutons selon la sélection
     */
    private void updateSelectionButtons() {
        long selectedCount = affaires.stream().mapToLong(a -> a.isSelected() ? 1 : 0).sum();

        editButton.setDisable(selectedCount != 1);
        deleteButton.setDisable(selectedCount == 0);
        viewDetailsButton.setDisable(selectedCount == 0);
        printButton.setDisable(selectedCount == 0);
    }

    /**
     * Charge les données - VERSION CORRIGÉE
     */
    private void loadData() {
        Platform.runLater(() -> {
            affaires.clear();
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

            // CORRECTION LIGNES 367-374: Méthode convertToViewModel corrigée
            private AffaireViewModel convertToViewModel(Affaire affaire) {
                AffaireViewModel viewModel = new AffaireViewModel(); // Déclaration en premier
                viewModel.setId(affaire.getId());
                viewModel.setNumeroAffaire(affaire.getNumeroAffaire());
                viewModel.setDateCreation(affaire.getDateCreation());

                // Traitement du montant - une seule déclaration
                BigDecimal montantAmendeTotal = affaire.getMontantAmendeTotal();
                viewModel.setMontantAmendeTotal(montantAmendeTotal != null ? montantAmendeTotal.doubleValue() : 0.0);

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

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Met à jour les informations de pagination
     */
    private void updatePaginationInfo() {
        int debut = (currentPage - 1) * pageSize + 1;
        int fin = Math.min(debut + affaires.size() - 1, (int) totalElements);

        if (totalElements == 0) {
            paginationInfoLabel.setText("Aucune affaire");
        } else {
            paginationInfoLabel.setText(String.format("Affichage de %d à %d sur %d",
                    debut, fin, totalElements));
        }
    }

    /**
     * Met à jour les boutons de pagination
     */
    private void updatePaginationButtons() {
        firstPageButton.setDisable(currentPage <= 1);
        previousPageButton.setDisable(currentPage <= 1);
        nextPageButton.setDisable(currentPage >= totalPages);
        lastPageButton.setDisable(currentPage >= totalPages);
    }

    /**
     * Met à jour les numéros de pages
     */
    private void updatePaginationNumbers() {
        pageNumbersContainer.getChildren().clear();

        if (totalPages <= 1) {
            return;
        }

        // Calculer la plage de pages à afficher
        int start = Math.max(1, currentPage - 2);
        int end = Math.min(totalPages, currentPage + 2);

        // Ajuster si on est près du début ou de la fin
        if (end - start < 4) {
            if (start == 1) {
                end = Math.min(totalPages, start + 4);
            } else if (end == totalPages) {
                start = Math.max(1, end - 4);
            }
        }

        // Ajouter les boutons de page
        for (int i = start; i <= end; i++) {
            Button pageButton = new Button(String.valueOf(i));
            pageButton.getStyleClass().add("page-button");

            if (i == currentPage) {
                pageButton.getStyleClass().add("current-page");
            }

            final int pageNum = i;
            pageButton.setOnAction(e -> gotoPage(pageNum));
            pageNumbersContainer.getChildren().add(pageButton);
        }
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

    private void createNewAffaire() {
        logger.info("Création d'une nouvelle affaire");
        AlertUtil.showInfoAlert("Nouvelle affaire",
                "Fonctionnalité en développement",
                "Le formulaire de création sera disponible prochainement.");
    }

    private void viewAffaireDetails(AffaireViewModel affaire) {
        logger.info("Affichage des détails de l'affaire: {}", affaire.getNumeroAffaire());
        AlertUtil.showInfoAlert("Détails de l'affaire",
                "Affaire: " + affaire.getNumeroAffaire() + " - " +
                        CurrencyFormatter.format(affaire.getMontantAmendeTotal()),
                "La vue de détail sera disponible prochainement.");
    }

    private void editAffaire(AffaireViewModel affaire) {
        logger.info("Modification de l'affaire: {}", affaire.getNumeroAffaire());
        AlertUtil.showInfoAlert("Modification d'affaire",
                "Affaire: " + affaire.getNumeroAffaire(),
                "Le formulaire de modification sera disponible prochainement.");
    }

    private void editSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();
        if (selected.size() == 1) {
            editAffaire(selected.get(0));
        }
    }

    private void deleteSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();
        if (selected.isEmpty()) return;

        if (AlertUtil.showConfirmAlert("Confirmation de suppression",
                "Supprimer les affaires sélectionnées",
                "Voulez-vous vraiment supprimer " + selected.size() + " affaire(s) ?")) {

            // TODO: Implémenter la suppression
            AlertUtil.showInfoAlert("Suppression",
                    "Fonctionnalité en développement",
                    "La suppression sera disponible prochainement.");
        }
    }

    private void viewSelectedAffairesDetails() {
        List<AffaireViewModel> selected = getSelectedAffaires();
        if (!selected.isEmpty()) {
            viewAffaireDetails(selected.get(0));
        }
    }

    private void printSelectedAffaires() {
        List<AffaireViewModel> selected = getSelectedAffaires();
        if (selected.isEmpty()) return;

        AlertUtil.showInfoAlert("Impression",
                "Fonctionnalité en développement",
                "L'impression sera disponible prochainement.");
    }

    private void exportAffaires() {
        AlertUtil.showInfoAlert("Export",
                "Fonctionnalité en développement",
                "L'export sera disponible prochainement.");
    }

    private List<AffaireViewModel> getSelectedAffaires() {
        return affaires.stream()
                .filter(AffaireViewModel::isSelected)
                .collect(Collectors.toList());
    }

    // Navigation dans les pages

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