package com.regulation.contentieux.controller;

import com.regulation.contentieux.config.DatabaseConfig;
import com.regulation.contentieux.model.Contravention;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.util.AlertUtil;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la liste des affaires - Version définitive complète
 */
public class AffaireListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireListController.class);

    // ===== FILTRES ET RECHERCHE (selon affaire-list.fxml) =====
    @FXML private TextField searchField;
    @FXML private ComboBox<StatutAffaire> statutComboBox;
    @FXML private DatePicker dateDebutPicker;
    @FXML private DatePicker dateFinPicker;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;
    @FXML private Button newAffaireButton;
    @FXML private Button exportButton;
    @FXML private Label totalCountLabel;

    // ===== TABLEAU ET COLONNES (selon affaire-list.fxml) =====
    @FXML private TableView<AffaireViewModel> affairesTableView;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private ComboBox<Integer> pageSizeComboBox;

    // Colonnes du tableau - NOMS EXACTS du FXML
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

    // ===== BOUTONS D'ACTIONS (selon affaire-list.fxml) =====
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button duplicateButton;
    @FXML private Button printButton;

    // ===== PAGINATION (composants standards) =====
    @FXML private Label paginationInfoLabel;
    @FXML private TextField gotoPageField;
    @FXML private Button gotoPageButton;
    @FXML private Button firstPageButton;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Button lastPageButton;
    @FXML private HBox pageNumbersContainer;

    // ===== SERVICES ET DONNÉES =====
    private AffaireDAO affaireDAO;
    private AuthenticationService authService;
    private ObservableList<AffaireViewModel> affaires;

    // ===== PAGINATION =====
    private int currentPage = 1;
    private int pageSize = 25;
    private long totalElements = 0;
    private int totalPages = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            logger.info("Initialisation du contrôleur de liste des affaires...");

            // Initialisation des services
            affaireDAO = new AffaireDAO();
            authService = AuthenticationService.getInstance();
            affaires = FXCollections.observableArrayList();

            // Configuration de l'interface
            setupUI();
            setupTableColumns();
            setupEventHandlers();
            setupPagination();

            // Chargement initial des données
            loadData();

            logger.info("Contrôleur de liste des affaires initialisé avec succès");

        } catch (Exception e) {
            logger.error("Erreur lors de l'initialisation du contrôleur", e);
            AlertUtil.showErrorAlert("Erreur", "Erreur d'initialisation",
                    "Impossible d'initialiser la liste des affaires : " + e.getMessage());
        }
    }

    /**
     * Configuration initiale de l'interface utilisateur
     */
    private void setupUI() {
        logger.debug("Configuration de l'interface utilisateur...");

        try {
            // Configuration du ComboBox des statuts
            if (statutComboBox != null) {
                statutComboBox.getItems().add(null); // Option "Tous les statuts"
                statutComboBox.getItems().addAll(StatutAffaire.values());
                statutComboBox.setConverter(new StringConverter<StatutAffaire>() {
                    @Override
                    public String toString(StatutAffaire statut) {
                        return statut == null ? "Tous les statuts" : statut.name();
                    }

                    @Override
                    public StatutAffaire fromString(String string) {
                        return null;
                    }
                });
            }

            // Configuration du ComboBox de taille de page
            if (pageSizeComboBox != null) {
                pageSizeComboBox.getItems().addAll(10, 25, 50, 100);
                pageSizeComboBox.setValue(pageSize);
                pageSizeComboBox.setOnAction(e -> {
                    pageSize = pageSizeComboBox.getValue();
                    currentPage = 1;
                    loadData();
                });
            }

            // Configuration initiale des boutons
            updateActionButtons();

            // Configuration du label de compteur
            if (totalCountLabel != null) {
                totalCountLabel.setText("0 affaire(s)");
            }

            logger.debug("Interface utilisateur configurée");

        } catch (Exception e) {
            logger.error("Erreur lors de la configuration de l'interface", e);
        }
    }

    // Variable pour stocker la contravention sélectionnée
    private Contravention selectedContravention;

    /**
     * Configuration des colonnes du tableau
     */
    private void setupTableColumns() {
        logger.debug("Configuration des colonnes du tableau...");

        try {
            // Configuration du tableau principal
            if (affairesTableView != null) {
                affairesTableView.setItems(affaires);
                affairesTableView.setEditable(true);

                // Gestion de la sélection
                affairesTableView.getSelectionModel().selectedItemProperty().addListener(
                        (obs, oldSelection, newSelection) -> updateActionButtons());
            }

            // Colonne de sélection
            if (selectColumn != null) {
                selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
                selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
                selectColumn.setEditable(true);
            }

            // Colonne numéro d'affaire
            if (numeroColumn != null) {
                numeroColumn.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getNumeroAffaire()));
            }

            // Colonne date de création
            if (dateCreationColumn != null) {
                dateCreationColumn.setCellValueFactory(cellData ->
                        new SimpleObjectProperty<>(cellData.getValue().getDateCreation()));

                // Formatage de la date
                dateCreationColumn.setCellFactory(col -> new TableCell<AffaireViewModel, LocalDate>() {
                    @Override
                    protected void updateItem(LocalDate date, boolean empty) {
                        super.updateItem(date, empty);
                        if (empty || date == null) {
                            setText(null);
                        } else {
                            setText(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                        }
                    }
                });
            }

            // Colonne contrevenant
            if (contrevenantColumn != null) {
                contrevenantColumn.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getContrevenantNom()));
            }

            // Colonne contravention
            if (contraventionColumn != null) {
                contraventionColumn.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getContraventionLibelle()));
            }

            // Colonne montant
            if (montantColumn != null) {
                montantColumn.setCellValueFactory(cellData ->
                        new SimpleObjectProperty<>(cellData.getValue().getMontantAmendeTotal()));

                // Formatage du montant
                montantColumn.setCellFactory(col -> new TableCell<AffaireViewModel, Double>() {
                    @Override
                    protected void updateItem(Double montant, boolean empty) {
                        super.updateItem(montant, empty);
                        if (empty || montant == null || montant == 0.0) {
                            setText("0 FCFA");
                        } else {
                            setText(String.format("%,.0f FCFA", montant));
                        }
                    }
                });
            }

            // Colonne statut
            if (statutColumn != null) {
                statutColumn.setCellValueFactory(cellData ->
                        new SimpleObjectProperty<>(cellData.getValue().getStatut()));

                // Formatage du statut
                statutColumn.setCellFactory(col -> new TableCell<AffaireViewModel, StatutAffaire>() {
                    @Override
                    protected void updateItem(StatutAffaire statut, boolean empty) {
                        super.updateItem(statut, empty);
                        if (empty || statut == null) {
                            setText(null);
                        } else {
                            setText(statut.name());
                        }
                    }
                });
            }

            // Colonne bureau
            if (bureauColumn != null) {
                bureauColumn.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getBureauNom()));
            }

            // Colonne service
            if (serviceColumn != null) {
                serviceColumn.setCellValueFactory(cellData ->
                        new SimpleStringProperty(cellData.getValue().getServiceNom()));
            }

            // Colonne actions (boutons)
            if (actionsColumn != null) {
                actionsColumn.setCellFactory(col -> new TableCell<AffaireViewModel, Void>() {
                    private final Button viewButton = new Button("Voir");
                    private final Button editButton = new Button("Modifier");

                    {
                        viewButton.setOnAction(e -> {
                            AffaireViewModel affaire = getTableView().getItems().get(getIndex());
                            onViewAffaire(affaire);
                        });

                        editButton.setOnAction(e -> {
                            AffaireViewModel affaire = getTableView().getItems().get(getIndex());
                            onEditAffaire(affaire);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            HBox buttons = new HBox(5, viewButton, editButton);
                            setGraphic(buttons);
                        }
                    }
                });
            }

            logger.debug("Colonnes du tableau configurées");

        } catch (Exception e) {
            logger.error("Erreur lors de la configuration des colonnes", e);
        }
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        logger.debug("Configuration des gestionnaires d'événements...");

        try {
            // Boutons d'action principales
            if (newAffaireButton != null) {
                newAffaireButton.setOnAction(e -> onNewAffaire());
            }

            if (editButton != null) {
                editButton.setOnAction(e -> onEditSelectedAffaire());
            }

            if (deleteButton != null) {
                deleteButton.setOnAction(e -> onDeleteSelectedAffaire());
            }

            if (duplicateButton != null) {
                duplicateButton.setOnAction(e -> onDuplicateSelectedAffaire());
            }

            if (printButton != null) {
                printButton.setOnAction(e -> onPrintAffaires());
            }

            if (exportButton != null) {
                exportButton.setOnAction(e -> onExportAffaires());
            }

            // Recherche et filtres
            if (searchButton != null) {
                searchButton.setOnAction(e -> onSearch());
            }

            if (clearFiltersButton != null) {
                clearFiltersButton.setOnAction(e -> onClearFilters());
            }

            if (searchField != null) {
                searchField.setOnAction(e -> onSearch());
            }

            // Sélection multiple
            if (selectAllCheckBox != null) {
                selectAllCheckBox.setOnAction(e -> onSelectAll());
            }

            // Pagination
            if (firstPageButton != null) {
                firstPageButton.setOnAction(e -> goToFirstPage());
            }

            if (previousPageButton != null) {
                previousPageButton.setOnAction(e -> goToPreviousPage());
            }

            if (nextPageButton != null) {
                nextPageButton.setOnAction(e -> goToNextPage());
            }

            if (lastPageButton != null) {
                lastPageButton.setOnAction(e -> goToLastPage());
            }

            if (gotoPageButton != null) {
                gotoPageButton.setOnAction(e -> goToPage());
            }

            logger.debug("Gestionnaires d'événements configurés");

        } catch (Exception e) {
            logger.error("Erreur lors de la configuration des gestionnaires d'événements", e);
        }
    }

    /**
     * Configuration de la pagination
     */
    private void setupPagination() {
        logger.debug("Configuration de la pagination...");
        // Configuration basique de la pagination
        updatePaginationInfo();
    }

    /**
     * Chargement des données RÉELLES de la base
     */
    /**
     * Chargement des données depuis la base
     */
    private void loadData() {
        logger.debug("Chargement des données depuis la base...");

        // Afficher immédiatement un indicateur de chargement
        Platform.runLater(() -> {
            affaires.clear();
            if (totalCountLabel != null) {
                totalCountLabel.setText("Chargement...");
            }
            if (paginationInfoLabel != null) {
                paginationInfoLabel.setText("Chargement en cours...");
            }
        });

        // Créer une tâche asynchrone pour le chargement
        Task<List<AffaireViewModel>> loadTask = new Task<List<AffaireViewModel>>() {
            @Override
            protected List<AffaireViewModel> call() throws Exception {
                logger.info("Début du chargement asynchrone - page {} (pageSize: {})", currentPage, pageSize);

                // Chargement des affaires depuis la base
                List<Affaire> affairesFromDb = affaireDAO.findAll(
                        (currentPage - 1) * pageSize, // offset
                        pageSize // limit
                );

                // Comptage total
                totalElements = affaireDAO.count();

                logger.info("Chargées {} affaires depuis la base (total: {})",
                        affairesFromDb.size(), totalElements);

                // Conversion en ViewModels
                List<AffaireViewModel> viewModels = new ArrayList<>();
                for (Affaire affaire : affairesFromDb) {
                    viewModels.add(convertToViewModel(affaire));
                }

                return viewModels;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    // Mise à jour de la liste
                    affaires.clear();
                    affaires.addAll(getValue());

                    // Mise à jour des compteurs
                    totalPages = (int) Math.ceil((double) totalElements / pageSize);

                    // Mise à jour des labels
                    updateTotalCountLabel();
                    updatePaginationInfo();
                    updatePaginationButtons();
                    updatePaginationNumbers();

                    logger.info("Interface mise à jour avec succès : {} affaires affichées", affaires.size());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des données", getException());

                    if (totalCountLabel != null) {
                        totalCountLabel.setText("Erreur");
                    }
                    if (paginationInfoLabel != null) {
                        paginationInfoLabel.setText("Erreur de chargement");
                    }

                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les affaires",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        // Exécuter la tâche dans un thread séparé
        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ===== MÉTHODES D'ACTION =====

    private void onNewAffaire() {
        logger.info("Action: Nouvelle Affaire");
        try {
            Stage currentStage = (Stage) affairesTableView.getScene().getWindow();
            AffaireEncaissementController controller = new AffaireEncaissementController();
            controller.showDialog(currentStage);

            if (controller.getCreatedAffaire() != null) {
                logger.info("Nouvelle affaire créée : {}", controller.getCreatedAffaire().getNumeroAffaire());
                loadData();
            }
        } catch (Exception e) {
            logger.error("Erreur lors de l'ouverture du dialogue", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible d'ouvrir le formulaire", e.getMessage());
        }
    }

    private void onEditSelectedAffaire() {
        AffaireViewModel selected = affairesTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            onEditAffaire(selected);
        }
    }

    private void onEditAffaire(AffaireViewModel affaire) {
        logger.info("Action: Éditer Affaire {}", affaire.getNumeroAffaire());
        AlertUtil.showInfoAlert("Info", "Éditer Affaire",
                "Édition de l'affaire " + affaire.getNumeroAffaire() + " (fonctionnalité en cours)");
    }

    private void onViewAffaire(AffaireViewModel affaire) {
        logger.info("Action: Voir Affaire {}", affaire.getNumeroAffaire());
        AlertUtil.showInfoAlert("Info", "Voir Affaire",
                "Affichage des détails de l'affaire " + affaire.getNumeroAffaire());
    }

    private void onDeleteSelectedAffaire() {
        logger.info("Action: Supprimer Affaires sélectionnées");
        AlertUtil.showInfoAlert("Info", "Supprimer", "Fonctionnalité en cours de développement");
    }

    private void onDuplicateSelectedAffaire() {
        logger.info("Action: Dupliquer Affaires sélectionnées");
        AlertUtil.showInfoAlert("Info", "Dupliquer", "Fonctionnalité en cours de développement");
    }

    private void onPrintAffaires() {
        logger.info("Action: Imprimer");
        AlertUtil.showInfoAlert("Info", "Imprimer", "Fonctionnalité d'impression en cours de développement");
    }

    private void onExportAffaires() {
        logger.info("Action: Exporter");
        AlertUtil.showInfoAlert("Info", "Exporter", "Fonctionnalité d'export en cours de développement");
    }

    private void onSearch() {
        logger.info("Action: Rechercher avec critères");

        // Récupération des critères de recherche
        String searchTerm = searchField != null ? searchField.getText() : null;
        StatutAffaire statut = statutComboBox != null ? statutComboBox.getValue() : null;
        LocalDate dateDebut = dateDebutPicker != null ? dateDebutPicker.getValue() : null;
        LocalDate dateFin = dateFinPicker != null ? dateFinPicker.getValue() : null;

        // Réinitialiser à la première page pour une nouvelle recherche
        currentPage = 1;

        // Recharger avec les critères
        loadDataWithCriteria(searchTerm, statut, dateDebut, dateFin);
    }

    /**
     * Chargement des données avec critères de recherche
     */
    /**
     * Chargement des données avec critères de recherche
     */
    private void loadDataWithCriteria(String searchTerm, StatutAffaire statut,
                                      LocalDate dateDebut, LocalDate dateFin) {
        logger.debug("Chargement avec critères: terme='{}', statut={}, dates={} à {}",
                searchTerm, statut, dateDebut, dateFin);

        // Afficher indicateur de chargement
        Platform.runLater(() -> {
            affaires.clear();
            if (totalCountLabel != null) {
                totalCountLabel.setText("Recherche...");
            }
            if (paginationInfoLabel != null) {
                paginationInfoLabel.setText("Recherche en cours...");
            }
        });

        // Tâche asynchrone pour la recherche
        Task<List<AffaireViewModel>> searchTask = new Task<List<AffaireViewModel>>() {
            @Override
            protected List<AffaireViewModel> call() throws Exception {
                // Utiliser la méthode de recherche avec critères
                List<Affaire> affairesFromDb = affaireDAO.searchAffaires(
                        searchTerm, statut, dateDebut, dateFin,
                        null, // bureauId (pas utilisé pour l'instant)
                        (currentPage - 1) * pageSize, // offset
                        pageSize // limit
                );

                // Pour le comptage avec critères, utiliser la même recherche sans limite
                // (Idéalement, il faudrait une méthode countSearchAffaires, mais on utilise l'existant)
                List<Affaire> allResults = affaireDAO.searchAffaires(
                        searchTerm, statut, dateDebut, dateFin,
                        null, 0, Integer.MAX_VALUE
                );
                totalElements = allResults.size();

                logger.info("Recherche terminée: {} résultats trouvés (page {}, total {})",
                        affairesFromDb.size(), currentPage, totalElements);

                // Conversion en ViewModels
                List<AffaireViewModel> viewModels = new ArrayList<>();
                for (Affaire affaire : affairesFromDb) {
                    viewModels.add(convertToViewModel(affaire));
                }

                return viewModels;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    // Mise à jour de la liste
                    affaires.clear();
                    affaires.addAll(getValue());

                    // Mise à jour des compteurs
                    totalPages = (int) Math.ceil((double) totalElements / pageSize);

                    // Mise à jour de l'interface
                    updateTotalCountLabel();
                    updatePaginationInfo();
                    updatePaginationButtons();
                    updatePaginationNumbers();

                    logger.info("Recherche terminée : {} résultats affichés sur {} total",
                            affaires.size(), totalElements);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la recherche", getException());

                    if (totalCountLabel != null) {
                        totalCountLabel.setText("Erreur");
                    }
                    if (paginationInfoLabel != null) {
                        paginationInfoLabel.setText("Erreur de recherche");
                    }

                    AlertUtil.showErrorAlert("Erreur", "Erreur de recherche",
                            "Impossible d'effectuer la recherche : " + getException().getMessage());
                });
            }
        };

        // Exécuter la recherche
        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Charge les contraventions pour une affaire depuis la table de liaison
     */
    private List<Contravention> loadContraventionsForAffaire(Long affaireId) {
        List<Contravention> contraventions = new ArrayList<>();

        String sql = """
        SELECT c.id, c.code, c.libelle, c.description, ac.montant_applique
        FROM contraventions c
        INNER JOIN affaire_contraventions ac ON c.id = ac.contravention_id
        WHERE ac.affaire_id = ?
    """;

        try (Connection conn = DatabaseConfig.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, affaireId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Contravention contravention = new Contravention();
                contravention.setId(rs.getLong("id"));
                contravention.setCode(rs.getString("code"));
                contravention.setLibelle(rs.getString("libelle"));
                contravention.setDescription(rs.getString("description"));

                // Utiliser le montant appliqué spécifique à cette affaire
                BigDecimal montantApplique = rs.getBigDecimal("montant_applique");
                if (montantApplique != null && montantApplique.compareTo(BigDecimal.ZERO) > 0) {
                    contravention.setMontant(montantApplique);
                }

                contraventions.add(contravention);
            }

        } catch (SQLException e) {
            logger.error("Erreur lors du chargement des contraventions pour l'affaire " + affaireId, e);
        }

        return contraventions;
    }

    /**
     * Convertit une Affaire en AffaireViewModel
     */
    /**
     * Convertit une Affaire en AffaireViewModel
     */
    private AffaireViewModel convertToViewModel(Affaire affaire) {
        AffaireViewModel viewModel = new AffaireViewModel();

        // Données de base
        viewModel.setNumeroAffaire(affaire.getNumeroAffaire());
        viewModel.setDateCreation(affaire.getDateCreation());

        // Montant
        BigDecimal montantTotal = affaire.getMontantAmendeTotal();
        if (montantTotal != null) {
            viewModel.setMontantAmendeTotal(montantTotal.doubleValue());
        } else {
            viewModel.setMontantAmendeTotal(0.0);
        }

        // Contrevenant
        if (affaire.getContrevenant() != null) {
            viewModel.setContrevenantNom(affaire.getContrevenant().getNomComplet());
        } else {
            viewModel.setContrevenantNom("N/A");
        }

        // Contravention - La base utilise une contravention unique
        if (affaire.getContraventions() != null && !affaire.getContraventions().isEmpty()) {
            // Prendre la première (et unique) contravention
            Contravention contravention = affaire.getContraventions().get(0);
            viewModel.setContraventionLibelle(contravention.getLibelle());
        } else {
            viewModel.setContraventionLibelle("N/A");
        }

        // Statut
        viewModel.setStatut(affaire.getStatut() != null ? affaire.getStatut() : StatutAffaire.OUVERTE);

        // Bureau
        if (affaire.getBureau() != null) {
            viewModel.setBureauNom(affaire.getBureau().getNomBureau());
        } else {
            viewModel.setBureauNom("N/A");
        }

        // Service
        if (affaire.getService() != null) {
            viewModel.setServiceNom(affaire.getService().getNomService());
        } else {
            viewModel.setServiceNom("N/A");
        }

        return viewModel;
    }

    private void onClearFilters() {
        logger.info("Action: Effacer filtres");
        if (searchField != null) searchField.clear();
        if (statutComboBox != null) statutComboBox.setValue(null);
        if (dateDebutPicker != null) dateDebutPicker.setValue(null);
        if (dateFinPicker != null) dateFinPicker.setValue(null);
        loadData();
    }

    // ===== MÉTHODES DE PAGINATION =====

    /**
     * Met à jour l'état des boutons de pagination
     */
    private void updatePaginationButtons() {
        if (firstPageButton != null) {
            firstPageButton.setDisable(currentPage <= 1);
        }

        if (previousPageButton != null) {
            previousPageButton.setDisable(currentPage <= 1);
        }

        if (nextPageButton != null) {
            nextPageButton.setDisable(currentPage >= totalPages);
        }

        if (lastPageButton != null) {
            lastPageButton.setDisable(currentPage >= totalPages);
        }

        if (gotoPageButton != null && gotoPageField != null) {
            gotoPageButton.setDisable(gotoPageField.getText().trim().isEmpty());
        }
    }

    /**
     * Met à jour les numéros de page affichés
     */
    private void updatePaginationNumbers() {
        if (pageNumbersContainer == null) {
            return;
        }

        pageNumbersContainer.getChildren().clear();

        // Calculer la plage de pages à afficher
        int startPage = Math.max(1, currentPage - 2);
        int endPage = Math.min(totalPages, currentPage + 2);

        // Ajuster pour toujours afficher 5 pages si possible
        if (endPage - startPage < 4) {
            if (startPage == 1) {
                endPage = Math.min(totalPages, 5);
            } else if (endPage == totalPages) {
                startPage = Math.max(1, totalPages - 4);
            }
        }

        // Ajouter le bouton "1..." si nécessaire
        if (startPage > 1) {
            Button firstButton = createPageButton(1);
            pageNumbersContainer.getChildren().add(firstButton);

            if (startPage > 2) {
                Label dots = new Label("...");
                dots.setPadding(new Insets(5));
                pageNumbersContainer.getChildren().add(dots);
            }
        }

        // Ajouter les boutons de page
        for (int i = startPage; i <= endPage; i++) {
            Button pageButton = createPageButton(i);
            if (i == currentPage) {
                pageButton.getStyleClass().add("page-button-active");
                pageButton.setDisable(true);
            }
            pageNumbersContainer.getChildren().add(pageButton);
        }

        // Ajouter "...dernière page" si nécessaire
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                Label dots = new Label("...");
                dots.setPadding(new Insets(5));
                pageNumbersContainer.getChildren().add(dots);
            }

            Button lastButton = createPageButton(totalPages);
            pageNumbersContainer.getChildren().add(lastButton);
        }
    }

    /**
     * Crée un bouton de page
     */
    private Button createPageButton(int pageNumber) {
        Button button = new Button(String.valueOf(pageNumber));
        button.getStyleClass().add("page-button");
        button.setOnAction(e -> goToPage(pageNumber));
        return button;
    }

    /**
     * Navigation vers une page spécifique
     */
    private void goToPage(int pageNumber) {
        if (pageNumber >= 1 && pageNumber <= totalPages && pageNumber != currentPage) {
            currentPage = pageNumber;
            loadData();
        }
    }

    /**
     * Navigation vers la première page
     */
    private void goToFirstPage() {
        if (currentPage > 1) {
            currentPage = 1;
            loadData();
        }
    }

    /**
     * Navigation vers la page précédente
     */
    private void goToPreviousPage() {
        if (currentPage > 1) {
            currentPage--;
            loadData();
        }
    }

    /**
     * Navigation vers la page suivante
     */
    private void goToNextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadData();
        }
    }

    /**
     * Navigation vers la dernière page
     */
    private void goToLastPage() {
        if (currentPage < totalPages) {
            currentPage = totalPages;
            loadData();
        }
    }

    /**
     * Navigation vers une page saisie
     */
    private void goToPage() {
        if (gotoPageField == null) {
            return;
        }

        String pageText = gotoPageField.getText().trim();
        if (!pageText.isEmpty()) {
            try {
                int pageNumber = Integer.parseInt(pageText);
                if (pageNumber >= 1 && pageNumber <= totalPages) {
                    currentPage = pageNumber;
                    loadData();
                    gotoPageField.clear();
                } else {
                    AlertUtil.showWarningAlert("Page invalide",
                            "Numéro de page incorrect",
                            "Le numéro de page doit être entre 1 et " + totalPages);
                }
            } catch (NumberFormatException e) {
                AlertUtil.showWarningAlert("Page invalide",
                        "Format incorrect",
                        "Veuillez entrer un numéro de page valide");
            }
        }
    }

    /**
     * Met à jour le label du nombre total
     */
    private void updateTotalCountLabel() {
        if (totalCountLabel != null) {
            String text = formatLargeNumber(totalElements) + " affaire(s)";
            totalCountLabel.setText(text);
        }
    }

    /**
     * Formate un grand nombre avec séparateurs
     */
    private String formatLargeNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Gestion de la sélection globale
     */
    private void onSelectAll() {
        if (selectAllCheckBox != null) {
            boolean selectAll = selectAllCheckBox.isSelected();
            for (AffaireViewModel affaire : affaires) {
                affaire.setSelected(selectAll);
            }
            updateSelectionButtons();
        }
    }

    /**
     * Met à jour l'état des boutons basés sur la sélection
     */
    private void updateSelectionButtons() {
        long selectedCount = affaires.stream()
                .filter(AffaireViewModel::isSelected)
                .count();

        boolean hasSelection = selectedCount > 0;

        if (editButton != null) {
            editButton.setDisable(!hasSelection || selectedCount > 1);
        }

        if (deleteButton != null) {
            deleteButton.setDisable(!hasSelection);
        }

        if (duplicateButton != null) {
            duplicateButton.setDisable(!hasSelection || selectedCount > 1);
        }
    }

    // ===== MÉTHODES UTILITAIRES =====

    private void updateActionButtons() {
        long selectedCount = affaires.stream().mapToLong(a -> a.isSelected() ? 1 : 0).sum();
        boolean hasSelection = selectedCount > 0;
        boolean singleSelection = selectedCount == 1;

        if (editButton != null) editButton.setDisable(!singleSelection);
        if (deleteButton != null) deleteButton.setDisable(!hasSelection);
        if (duplicateButton != null) duplicateButton.setDisable(!hasSelection);
    }

    private void updatePaginationInfo() {
        totalPages = (int) Math.ceil((double) totalElements / pageSize);
        if (paginationInfoLabel != null) {
            paginationInfoLabel.setText(String.format("Page %d sur %d", currentPage, Math.max(1, totalPages)));
        }
    }

    // ===== CLASSE VIEWMODEL =====

    public static class AffaireViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private String numeroAffaire;
        private LocalDate dateCreation;
        private String contrevenantNom;
        private String contraventionLibelle;
        private Double montantAmendeTotal;
        private StatutAffaire statut;
        private String bureauNom;
        private String serviceNom;

        // Getters et Setters
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }

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