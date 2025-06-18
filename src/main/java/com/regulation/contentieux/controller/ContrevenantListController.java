package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.Contrevenant;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.ContrevenantService;
import com.regulation.contentieux.util.AlertUtil;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la liste des contrevenants
 * Suit la même logique que AffaireListController
 */
public class ContrevenantListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ContrevenantListController.class);

    // Filtres et recherche
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typePersonneComboBox;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Actions principales
    @FXML private Button newContrevenantButton;
    @FXML private Button exportButton;

    // Tableau et sélection
    @FXML private TableView<ContrevenantViewModel> contrevenantsTableView;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private ComboBox<Integer> pageSizeComboBox;

    // Colonnes du tableau
    @FXML private TableColumn<ContrevenantViewModel, Boolean> selectColumn;
    @FXML private TableColumn<ContrevenantViewModel, String> codeColumn;
    @FXML private TableColumn<ContrevenantViewModel, String> nomCompletColumn;
    @FXML private TableColumn<ContrevenantViewModel, String> typePersonneColumn;
    @FXML private TableColumn<ContrevenantViewModel, String> telephoneColumn;
    @FXML private TableColumn<ContrevenantViewModel, String> emailColumn;
    @FXML private TableColumn<ContrevenantViewModel, LocalDateTime> dateCreationColumn;
    @FXML private TableColumn<ContrevenantViewModel, Void> actionsColumn;

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
    private ContrevenantService contrevenantService;
    private AuthenticationService authService;
    private ObservableList<ContrevenantViewModel> contrevenants;

    // Pagination
    private int currentPage = 1;
    private int pageSize = 25;
    private long totalElements = 0;
    private int totalPages = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        contrevenantService = new ContrevenantService();
        authService = AuthenticationService.getInstance();
        contrevenants = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupEventHandlers();
        setupPagination();
        loadData();

        logger.info("Contrôleur de liste des contrevenants initialisé");
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        // Configuration des ComboBox
        typePersonneComboBox.getItems().add(null); // Option "Tous les types"
        typePersonneComboBox.getItems().addAll("PHYSIQUE", "MORALE");
        typePersonneComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String type) {
                if (type == null) return "Tous les types";
                return type.equals("PHYSIQUE") ? "Personne physique" : "Personne morale";
            }

            @Override
            public String fromString(String string) {
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
        codeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCode()));

        nomCompletColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNomComplet()));

        typePersonneColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTypePersonneLibelle()));

        telephoneColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTelephone()));

        emailColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getEmail()));

        dateCreationColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getCreatedAt()));

        // Formatage des colonnes
        dateCreationColumn.setCellFactory(col -> new TableCell<ContrevenantViewModel, LocalDateTime>() {
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
        contrevenantsTableView.setItems(contrevenants);
        contrevenantsTableView.setEditable(true);
    }

    /**
     * Crée la factory pour les boutons d'action
     */
    private Callback<TableColumn<ContrevenantViewModel, Void>, TableCell<ContrevenantViewModel, Void>> createActionButtonsCellFactory() {
        return param -> new TableCell<ContrevenantViewModel, Void>() {
            private final Button viewButton = new Button("👁");
            private final Button editButton = new Button("✏");
            private final HBox buttonsBox = new HBox(2, viewButton, editButton);

            {
                viewButton.getStyleClass().add("button-icon");
                editButton.getStyleClass().add("button-icon");
                viewButton.setTooltip(new Tooltip("Voir les détails"));
                editButton.setTooltip(new Tooltip("Modifier"));

                viewButton.setOnAction(e -> {
                    ContrevenantViewModel contrevenant = getTableView().getItems().get(getIndex());
                    viewContrevenant(contrevenant);
                });

                editButton.setOnAction(e -> {
                    ContrevenantViewModel contrevenant = getTableView().getItems().get(getIndex());
                    editContrevenant(contrevenant);
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
        newContrevenantButton.setOnAction(e -> createNewContrevenant());
        exportButton.setOnAction(e -> exportData());

        // Actions sur sélection
        editButton.setOnAction(e -> editSelectedContrevenants());
        deleteButton.setOnAction(e -> deleteSelectedContrevenants());
        duplicateButton.setOnAction(e -> duplicateSelectedContrevenants());
        printButton.setOnAction(e -> printSelectedContrevenants());

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
            contrevenants.clear();
            totalCountLabel.setText("Chargement...");
            paginationInfoLabel.setText("Chargement en cours...");
        });

        Task<List<ContrevenantViewModel>> loadTask = new Task<List<ContrevenantViewModel>>() {
            @Override
            protected List<ContrevenantViewModel> call() throws Exception {
                // Récupération des critères de recherche
                String searchText = searchField.getText();
                String typePersonne = typePersonneComboBox.getValue();

                logger.info("Chargement page {} (pageSize: {})", currentPage, pageSize);

                // Chargement des contrevenants
                List<Contrevenant> contrevenantsList = contrevenantService.searchContrevenants(
                        searchText, typePersonne, currentPage, pageSize);

                // Comptage total
                totalElements = contrevenantService.countSearchContrevenants(searchText, typePersonne);

                logger.info("Chargement terminé: {} contrevenants trouvés sur {} total",
                        contrevenantsList.size(), totalElements);

                // Conversion vers le modèle d'affichage
                return contrevenantsList.stream()
                        .map(this::convertToViewModel)
                        .collect(Collectors.toList());
            }

            private ContrevenantViewModel convertToViewModel(Contrevenant contrevenant) {
                ContrevenantViewModel viewModel = new ContrevenantViewModel();
                viewModel.setId(contrevenant.getId());
                viewModel.setCode(contrevenant.getCode());
                viewModel.setNomComplet(contrevenant.getNomComplet());
                viewModel.setTypePersonne(contrevenant.getTypePersonne());
                viewModel.setTelephone(contrevenant.getTelephone());
                viewModel.setEmail(contrevenant.getEmail());
                viewModel.setAdresse(contrevenant.getAdresse());
                viewModel.setCreatedAt(contrevenant.getCreatedAt());
                return viewModel;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    contrevenants.clear();
                    contrevenants.addAll(getValue());

                    // Mise à jour de la pagination
                    totalPages = (int) Math.ceil((double) totalElements / pageSize);
                    updatePaginationInfo();
                    updatePaginationButtons();
                    updatePaginationNumbers();

                    // Mise à jour du compteur
                    String countText = formatLargeNumber(totalElements) + " contrevenant(s)";
                    totalCountLabel.setText(countText);

                    logger.debug("Interface mise à jour: {} contrevenants affichés", contrevenants.size());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des contrevenants", getException());
                    totalCountLabel.setText("Erreur");
                    paginationInfoLabel.setText("Erreur de chargement");

                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les contrevenants",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

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

    // Actions sur les contrevenants

    private void performSearch() {
        currentPage = 1;
        loadData();
    }

    private void clearFilters() {
        searchField.clear();
        typePersonneComboBox.setValue(null);
        performSearch();
    }

    private void createNewContrevenant() {
        logger.info("Création d'un nouveau contrevenant");
        AlertUtil.showInfoAlert("Nouveau contrevenant",
                "Fonctionnalité en développement",
                "Le formulaire de création sera disponible prochainement.");
    }

    private void viewContrevenant(ContrevenantViewModel contrevenant) {
        logger.info("Affichage des détails du contrevenant: {}", contrevenant.getCode());
        AlertUtil.showInfoAlert("Détails du contrevenant",
                "Contrevenant: " + contrevenant.getCode() + " - " + contrevenant.getNomComplet(),
                "La vue de détail sera disponible prochainement.");
    }

    private void editContrevenant(ContrevenantViewModel contrevenant) {
        logger.info("Modification du contrevenant: {}", contrevenant.getCode());
        AlertUtil.showInfoAlert("Modification de contrevenant",
                "Contrevenant: " + contrevenant.getCode(),
                "Le formulaire de modification sera disponible prochainement.");
    }

    private void editSelectedContrevenants() {
        List<ContrevenantViewModel> selected = getSelectedContrevenants();
        if (selected.size() == 1) {
            editContrevenant(selected.get(0));
        }
    }

    private void deleteSelectedContrevenants() {
        List<ContrevenantViewModel> selected = getSelectedContrevenants();

        if (selected.isEmpty()) {
            return;
        }

        String message = selected.size() == 1
                ? "Voulez-vous vraiment supprimer le contrevenant " + selected.get(0).getCode() + " ?"
                : "Voulez-vous vraiment supprimer les " + selected.size() + " contrevenants sélectionnés ?";

        if (AlertUtil.showConfirmAlert("Confirmation de suppression",
                "Supprimer les contrevenants", message)) {

            performDeletion(selected);
        }
    }

    private void performDeletion(List<ContrevenantViewModel> contrevenants) {
        Task<Void> deleteTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (ContrevenantViewModel contrevenant : contrevenants) {
                    contrevenantService.deleteContrevenant(contrevenant.getId());
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showInfoAlert("Suppression réussie",
                            "Contrevenants supprimés",
                            contrevenants.size() + " contrevenant(s) supprimé(s) avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la suppression", getException());
                    AlertUtil.showErrorAlert("Erreur de suppression",
                            "Impossible de supprimer les contrevenants",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread deleteThread = new Thread(deleteTask);
        deleteThread.setDaemon(true);
        deleteThread.start();
    }

    private void duplicateSelectedContrevenants() {
        List<ContrevenantViewModel> selected = getSelectedContrevenants();
        if (selected.size() == 1) {
            logger.info("Duplication du contrevenant: {}", selected.get(0).getCode());
            AlertUtil.showInfoAlert("Duplication de contrevenant",
                    "Fonctionnalité en développement",
                    "La duplication sera disponible prochainement.");
        }
    }

    private void printSelectedContrevenants() {
        List<ContrevenantViewModel> selected = getSelectedContrevenants();
        logger.info("Impression de {} contrevenant(s)", selected.size());
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
        long selectedCount = contrevenants.stream()
                .mapToLong(c -> c.isSelected() ? 1 : 0)
                .sum();

        editButton.setDisable(selectedCount != 1);
        deleteButton.setDisable(selectedCount == 0);
        duplicateButton.setDisable(selectedCount != 1);
        printButton.setDisable(selectedCount == 0);
    }

    private void toggleSelectAll() {
        boolean selectAll = selectAllCheckBox.isSelected();
        contrevenants.forEach(contrevenant -> contrevenant.setSelected(selectAll));
        updateActionButtons();
    }

    private List<ContrevenantViewModel> getSelectedContrevenants() {
        return contrevenants.stream()
                .filter(ContrevenantViewModel::isSelected)
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
     * Modèle d'affichage pour le tableau des contrevenants
     */
    public static class ContrevenantViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private Long id;
        private String code;
        private String nomComplet;
        private String typePersonne;
        private String telephone;
        private String email;
        private String adresse;
        private LocalDateTime createdAt;

        // Getters et setters
        public boolean isSelected() { return selected.get(); }
        public BooleanProperty selectedProperty() { return selected; }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getNomComplet() { return nomComplet; }
        public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

        public String getTypePersonne() { return typePersonne; }
        public void setTypePersonne(String typePersonne) { this.typePersonne = typePersonne; }

        public String getTypePersonneLibelle() {
            if (typePersonne == null) return "";
            return typePersonne.equals("PHYSIQUE") ? "Personne physique" : "Personne morale";
        }

        public String getTelephone() { return telephone; }
        public void setTelephone(String telephone) { this.telephone = telephone; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getAdresse() { return adresse; }
        public void setAdresse(String adresse) { this.adresse = adresse; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}