package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.enums.StatutAffaire;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.util.AlertUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

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
                        if (empty || montant == null) {
                            setText(null);
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
    private void loadData() {
        logger.debug("Chargement des données depuis la base...");

        try {
            affaires.clear();

            // CHARGEMENT DES VRAIES DONNÉES depuis votre base
            List<Affaire> affairesFromDb = affaireDAO.findAll(
                    (currentPage - 1) * pageSize, // offset
                    pageSize // limit
            );

            logger.info("Chargées {} affaires depuis la base (page {}, taille {})",
                    affairesFromDb.size(), currentPage, pageSize);

            // Conversion en ViewModels
            for (Affaire affaire : affairesFromDb) {
                AffaireViewModel viewModel = new AffaireViewModel();

                // Mapping des vraies données
                viewModel.setNumeroAffaire(affaire.getNumeroAffaire());
                viewModel.setDateCreation(affaire.getDateCreation());
                viewModel.setMontantAmendeTotal(affaire.getMontantTotal() != null ?
                        affaire.getMontantTotal().doubleValue() : 0.0);

                // Données liées (avec vérification null)
                if (affaire.getContrevenant() != null) {
                    viewModel.setContrevenantNom(affaire.getContrevenant().getNomComplet());
                } else {
                    viewModel.setContrevenantNom("N/A");
                }

                // CORRIGÉ : utilise getContraventions() au lieu de getContravention()
                if (affaire.getContraventions() != null && !affaire.getContraventions().isEmpty()) {
                    viewModel.setContraventionLibelle(affaire.getContraventions().get(0).getLibelle());
                } else {
                    viewModel.setContraventionLibelle("N/A");
                }

                // CORRIGÉ : getStatut() retourne déjà StatutAffaire, pas besoin de valueOf
                viewModel.setStatut(affaire.getStatut() != null ? affaire.getStatut() : StatutAffaire.OUVERTE);

                // Bureau et Service (selon votre modèle)
                if (affaire.getBureau() != null) {
                    viewModel.setBureauNom(affaire.getBureau().getNomBureau());
                } else {
                    viewModel.setBureauNom("N/A");
                }

                if (affaire.getService() != null) {
                    viewModel.setServiceNom(affaire.getService().getNomService());
                } else {
                    viewModel.setServiceNom("N/A");
                }

                affaires.add(viewModel);
            }

            // Calcul du total pour la pagination
            totalElements = affaireDAO.count(); // Méthode à implémenter dans AffaireDAO

            updateTotalCountLabel();
            updatePaginationInfo();

            logger.info("Données chargées avec succès : {} affaires sur {} total",
                    affaires.size(), totalElements);

        } catch (Exception e) {
            logger.error("Erreur lors du chargement des données", e);
        }
    }

    // ===== MÉTHODES D'ACTION =====

    private void onNewAffaire() {
        logger.info("Action: Nouvelle Affaire");
        AlertUtil.showInfoAlert("Info", "Nouvelle Affaire", "Fonctionnalité en cours de développement");
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
    private void loadDataWithCriteria(String searchTerm, StatutAffaire statut,
                                      LocalDate dateDebut, LocalDate dateFin) {
        logger.debug("Chargement avec critères: terme='{}', statut={}, dates={} à {}",
                searchTerm, statut, dateDebut, dateFin);

        try {
            affaires.clear();

            // Utiliser une méthode de recherche avec critères dans le DAO
            List<Affaire> affairesFromDb = affaireDAO.searchAffaires(
                    searchTerm, statut, dateDebut, dateFin,
                    null, // bureauId (pas utilisé pour l'instant)
                    (currentPage - 1) * pageSize, // offset
                    pageSize // limit
            );

            logger.info("Trouvées {} affaires avec les critères (page {}, taille {})",
                    affairesFromDb.size(), currentPage, pageSize);

            // Conversion en ViewModels (même logique que loadData())
            for (Affaire affaire : affairesFromDb) {
                AffaireViewModel viewModel = convertToViewModel(affaire);
                affaires.add(viewModel);
            }

            // Compter le total avec les mêmes critères
            totalElements = affaireDAO.count(); // Utilise la méthode count() simple pour l'instant

            updateTotalCountLabel();
            updatePaginationInfo();

            logger.info("Recherche terminée : {} résultats sur {} total",
                    affaires.size(), totalElements);

        } catch (Exception e) {
            logger.error("Erreur lors de la recherche", e);
            AlertUtil.showErrorAlert("Erreur", "Erreur de recherche",
                    "Impossible d'effectuer la recherche : " + e.getMessage());
        }
    }

    /**
     * Convertit une Affaire en AffaireViewModel
     */
    private AffaireViewModel convertToViewModel(Affaire affaire) {
        AffaireViewModel viewModel = new AffaireViewModel();

        viewModel.setNumeroAffaire(affaire.getNumeroAffaire());
        viewModel.setDateCreation(affaire.getDateCreation());
        viewModel.setMontantAmendeTotal(affaire.getMontantTotal() != null ?
                affaire.getMontantTotal().doubleValue() : 0.0);

        // Données liées avec vérification null
        viewModel.setContrevenantNom(affaire.getContrevenant() != null ?
                affaire.getContrevenant().getNomComplet() : "N/A");

        // CORRIGÉ : utilise getContraventions() au lieu de getContravention()
        if (affaire.getContraventions() != null && !affaire.getContraventions().isEmpty()) {
            viewModel.setContraventionLibelle(affaire.getContraventions().get(0).getLibelle());
        } else {
            viewModel.setContraventionLibelle("N/A");
        }

        // CORRIGÉ : getStatut() retourne déjà StatutAffaire, pas besoin de valueOf
        viewModel.setStatut(affaire.getStatut() != null ? affaire.getStatut() : StatutAffaire.OUVERTE);

        // Bureau et Service
        viewModel.setBureauNom(affaire.getBureau() != null ?
                affaire.getBureau().getNomBureau() : "N/A");

        viewModel.setServiceNom(affaire.getService() != null ?
                affaire.getService().getNomService() : "N/A");

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

    private void onSelectAll() {
        boolean selectAll = selectAllCheckBox.isSelected();
        affaires.forEach(affaire -> affaire.setSelected(selectAll));
        updateActionButtons();
    }

    // ===== MÉTHODES DE PAGINATION =====

    private void goToFirstPage() {
        currentPage = 1;
        loadData();
    }

    private void goToPreviousPage() {
        if (currentPage > 1) {
            currentPage--;
            loadData();
        }
    }

    private void goToNextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadData();
        }
    }

    private void goToLastPage() {
        currentPage = totalPages;
        loadData();
    }

    private void goToPage() {
        try {
            int page = Integer.parseInt(gotoPageField.getText());
            if (page >= 1 && page <= totalPages) {
                currentPage = page;
                loadData();
            }
        } catch (NumberFormatException e) {
            // Ignore
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

    private void updateTotalCountLabel() {
        if (totalCountLabel != null) {
            totalCountLabel.setText(totalElements + " affaire(s)");
        }
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