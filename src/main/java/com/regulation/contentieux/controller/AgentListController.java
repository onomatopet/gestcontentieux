package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.AgentDAO;
import com.regulation.contentieux.model.Agent;
import com.regulation.contentieux.service.AgentService;
import com.regulation.contentieux.service.AuthenticationService;
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
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la liste des agents
 * Suit la même logique que les autres contrôleurs de liste
 */
public class AgentListController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AgentListController.class);

    // Filtres et recherche
    @FXML private TextField searchField;
    @FXML private ComboBox<String> gradeComboBox;
    @FXML private ComboBox<Boolean> statutComboBox;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;

    // Actions principales
    @FXML private Button newAgentButton;
    @FXML private Button exportButton;

    // Tableau et sélection
    @FXML private TableView<AgentViewModel> agentsTableView;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private ComboBox<Integer> pageSizeComboBox;

    // Colonnes du tableau
    @FXML private TableColumn<AgentViewModel, Boolean> selectColumn;
    @FXML private TableColumn<AgentViewModel, String> codeColumn;
    @FXML private TableColumn<AgentViewModel, String> nomColumn;
    @FXML private TableColumn<AgentViewModel, String> prenomColumn;
    @FXML private TableColumn<AgentViewModel, String> gradeColumn;
    @FXML private TableColumn<AgentViewModel, String> serviceColumn;
    @FXML private TableColumn<AgentViewModel, Boolean> actifColumn;
    @FXML private TableColumn<AgentViewModel, LocalDateTime> dateCreationColumn;
    @FXML private TableColumn<AgentViewModel, Void> actionsColumn;

    // Actions sur sélection
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button activateButton;
    @FXML private Button deactivateButton;
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
    private AgentService agentService;
    private AuthenticationService authService;
    private ObservableList<AgentViewModel> agents;

    // Pagination
    private int currentPage = 1;
    private int pageSize = 25;
    private long totalElements = 0;
    private int totalPages = 0;

    @FXML
    private void showRolesSpeciauxDialog() {
        if (!authService.getCurrentUser().isAdmin()) {
            AlertUtil.showWarningAlert("Accès refusé",
                    "Droits insuffisants",
                    "Seuls les administrateurs peuvent attribuer les rôles DD/DG.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Attribution des rôles DD/DG");
        dialog.setHeaderText("Attribuez les rôles de Directeur Départemental et Directeur Général");
        dialog.setResizable(true);

        // Créer le contenu du dialogue
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ComboBox pour DD
        ComboBox<Agent> ddComboBox = new ComboBox<>();
        ddComboBox.setPromptText("Sélectionner un agent");
        ddComboBox.setPrefWidth(300);

        // ComboBox pour DG
        ComboBox<Agent> dgComboBox = new ComboBox<>();
        dgComboBox.setPromptText("Sélectionner un agent");
        dgComboBox.setPrefWidth(300);

        // Charger les agents
        Task<List<Agent>> loadTask = new Task<List<Agent>>() {
            @Override
            protected List<Agent> call() throws Exception {
                // CORRECTION: Utiliser la méthode existante findActiveAgents()
                return agentService.findActiveAgents();
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<Agent> agents = loadTask.getValue();
            ObservableList<Agent> agentsList = FXCollections.observableArrayList(agents);

            // Configuration des ComboBox
            ddComboBox.setItems(agentsList);
            dgComboBox.setItems(agentsList);

            // Convertisseur pour afficher le nom des agents
            StringConverter<Agent> converter = new StringConverter<Agent>() {
                @Override
                public String toString(Agent agent) {
                    return agent != null ? agent.getCodeAgent() + " - " + agent.getNom() + " " + agent.getPrenom() : "";
                }

                @Override
                public Agent fromString(String string) {
                    return null;
                }
            };

            ddComboBox.setConverter(converter);
            dgComboBox.setConverter(converter);

            // CORRECTION: Créer une instance locale de AgentDAO
            AgentDAO agentDAO = new AgentDAO();

            // Présélectionner les agents actuels si existants
            try {
                agentDAO.findByRoleSpecial("DD").ifPresent(ddComboBox::setValue);
                agentDAO.findByRoleSpecial("DG").ifPresent(dgComboBox::setValue);
            } catch (Exception ex) {
                logger.error("Erreur lors du chargement des rôles existants", ex);
            }
        });

        loadTask.setOnFailed(e -> {
            logger.error("Erreur lors du chargement des agents", loadTask.getException());
            AlertUtil.showErrorAlert("Erreur",
                    "Chargement impossible",
                    "Impossible de charger la liste des agents.");
        });

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();

        // Ajouter les éléments au grid
        grid.add(new Label("Directeur Départemental (DD):"), 0, 0);
        grid.add(ddComboBox, 1, 0);
        grid.add(new Label("Directeur Général (DG):"), 0, 1);
        grid.add(dgComboBox, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Boutons
        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Validation et enregistrement
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                // Validation
                if (ddComboBox.getValue() == null && dgComboBox.getValue() == null) {
                    AlertUtil.showWarningAlert("Attention",
                            "Aucun agent sélectionné",
                            "Veuillez sélectionner au moins un agent pour un rôle.");
                    return null;
                }

                // Vérifier que DD et DG ne sont pas le même agent
                if (ddComboBox.getValue() != null && dgComboBox.getValue() != null &&
                        ddComboBox.getValue().getId().equals(dgComboBox.getValue().getId())) {
                    AlertUtil.showWarningAlert("Attention",
                            "Sélection invalide",
                            "Le même agent ne peut pas être DD et DG simultanément.");
                    return null;
                }

                // Sauvegarder les attributions
                try {
                    // CORRECTION: Créer une instance locale de AgentDAO
                    AgentDAO agentDAO = new AgentDAO();

                    // Attribuer DD
                    if (ddComboBox.getValue() != null) {
                        agentDAO.assignRoleSpecial(ddComboBox.getValue().getId(), "DD");
                        logger.info("Rôle DD attribué à: {}", ddComboBox.getValue().getNomComplet());
                    }

                    // Attribuer DG
                    if (dgComboBox.getValue() != null) {
                        agentDAO.assignRoleSpecial(dgComboBox.getValue().getId(), "DG");
                        logger.info("Rôle DG attribué à: {}", dgComboBox.getValue().getNomComplet());
                    }

                    AlertUtil.showSuccessAlert("Succès",
                            "Rôles attribués",
                            "Les rôles DD et DG ont été attribués avec succès.");

                    // Rafraîchir la liste des agents
                    loadData();

                } catch (Exception ex) {
                    logger.error("Erreur lors de l'attribution des rôles", ex);
                    AlertUtil.showErrorAlert("Erreur",
                            "Attribution échouée",
                            "Impossible d'attribuer les rôles: " + ex.getMessage());
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    /**
     * Ajouter ce bouton dans la toolbar de AgentListController
     * Dans le fichier FXML agent-list.fxml, ajouter :
     * <Button fx:id="rolesSpeciauxButton" text="Rôles DD/DG" onAction="#showRolesSpeciauxDialog"/>
     */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        agentService = new AgentService();
        authService = AuthenticationService.getInstance();
        agents = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupEventHandlers();
        setupPagination();
        loadData();

        logger.info("Contrôleur de liste des agents initialisé");
    }

    /**
     * Configuration initiale de l'interface
     */
    private void setupUI() {
        // Configuration des ComboBox
        gradeComboBox.getItems().add(null); // Option "Tous les grades"
        gradeComboBox.getItems().addAll(
                "Inspecteur Principal", "Inspecteur", "Contrôleur Principal",
                "Contrôleur", "Agent Principal", "Agent"
        );
        gradeComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String grade) {
                return grade == null ? "Tous les grades" : grade;
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });

        // Statut actif/inactif
        statutComboBox.getItems().add(null); // Option "Tous"
        statutComboBox.getItems().addAll(true, false);
        statutComboBox.setConverter(new StringConverter<Boolean>() {
            @Override
            public String toString(Boolean actif) {
                if (actif == null) return "Tous les statuts";
                return actif ? "Actif" : "Inactif";
            }

            @Override
            public Boolean fromString(String string) {
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
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCodeAgent()));

        nomColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getNom()));

        prenomColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getPrenom()));

        gradeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getGrade()));

        serviceColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getServiceNom()));

        actifColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getActif()));

        dateCreationColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getCreatedAt()));

        // Formatage des colonnes
        actifColumn.setCellFactory(col -> new TableCell<AgentViewModel, Boolean>() {
            @Override
            protected void updateItem(Boolean actif, boolean empty) {
                super.updateItem(actif, empty);
                if (empty || actif == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(actif ? "Actif" : "Inactif");
                    setStyle(actif ? "-fx-text-fill: green; -fx-font-weight: bold;" :
                            "-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }
        });

        dateCreationColumn.setCellFactory(col -> new TableCell<AgentViewModel, LocalDateTime>() {
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
        agentsTableView.setItems(agents);
        agentsTableView.setEditable(true);
    }

    /**
     * Crée la factory pour les boutons d'action
     */
    private Callback<TableColumn<AgentViewModel, Void>, TableCell<AgentViewModel, Void>> createActionButtonsCellFactory() {
        return param -> new TableCell<AgentViewModel, Void>() {
            private final Button viewButton = new Button("👁");
            private final Button editButton = new Button("✏");
            private final Button toggleButton = new Button();
            private final HBox buttonsBox = new HBox(2, viewButton, editButton, toggleButton);

            {
                viewButton.getStyleClass().add("button-icon");
                editButton.getStyleClass().add("button-icon");
                toggleButton.getStyleClass().add("button-icon");

                viewButton.setTooltip(new Tooltip("Voir les détails"));
                editButton.setTooltip(new Tooltip("Modifier"));

                viewButton.setOnAction(e -> {
                    AgentViewModel agent = getTableView().getItems().get(getIndex());
                    viewAgent(agent);
                });

                editButton.setOnAction(e -> {
                    AgentViewModel agent = getTableView().getItems().get(getIndex());
                    editAgent(agent);
                });

                toggleButton.setOnAction(e -> {
                    AgentViewModel agent = getTableView().getItems().get(getIndex());
                    toggleAgentStatus(agent);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    AgentViewModel agent = getTableView().getItems().get(getIndex());
                    if (agent.getActif()) {
                        toggleButton.setText("🚫");
                        toggleButton.setTooltip(new Tooltip("Désactiver"));
                        toggleButton.getStyleClass().removeAll("button-success");
                        toggleButton.getStyleClass().add("button-warning");
                    } else {
                        toggleButton.setText("✅");
                        toggleButton.setTooltip(new Tooltip("Activer"));
                        toggleButton.getStyleClass().removeAll("button-warning");
                        toggleButton.getStyleClass().add("button-success");
                    }
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
        newAgentButton.setOnAction(e -> createNewAgent());
        exportButton.setOnAction(e -> exportData());

        // Actions sur sélection
        editButton.setOnAction(e -> editSelectedAgents());
        deleteButton.setOnAction(e -> deleteSelectedAgents());
        activateButton.setOnAction(e -> activateSelectedAgents());
        deactivateButton.setOnAction(e -> deactivateSelectedAgents());
        printButton.setOnAction(e -> printSelectedAgents());

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
            agents.clear();
            totalCountLabel.setText("Chargement...");
            paginationInfoLabel.setText("Chargement en cours...");
        });

        Task<List<AgentViewModel>> loadTask = new Task<List<AgentViewModel>>() {
            @Override
            protected List<AgentViewModel> call() throws Exception {
                String searchText = searchField.getText();
                String grade = gradeComboBox.getValue();
                Boolean actif = statutComboBox.getValue();

                logger.info("Chargement page {} (pageSize: {})", currentPage, pageSize);

                List<Agent> agentsList = agentService.searchAgents(
                        searchText, grade, null, actif, currentPage, pageSize);

                totalElements = agentService.countSearchAgents(searchText, grade, null, actif);

                logger.info("Chargement terminé: {} agents trouvés sur {} total",
                        agentsList.size(), totalElements);

                return agentsList.stream()
                        .map(this::convertToViewModel)
                        .collect(Collectors.toList());
            }

            private AgentViewModel convertToViewModel(Agent agent) {
                AgentViewModel viewModel = new AgentViewModel();
                viewModel.setId(agent.getId());
                viewModel.setCodeAgent(agent.getCodeAgent());
                viewModel.setNom(agent.getNom());
                viewModel.setPrenom(agent.getPrenom());
                viewModel.setGrade(agent.getGrade());
                viewModel.setServiceId(agent.getServiceId());
                viewModel.setServiceNom("Service #" + agent.getServiceId()); // Simplifié
                viewModel.setActif(agent.getActif());
                viewModel.setCreatedAt(agent.getCreatedAt());
                return viewModel;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    agents.clear();
                    agents.addAll(getValue());

                    totalPages = (int) Math.ceil((double) totalElements / pageSize);
                    updatePaginationInfo();
                    updatePaginationButtons();
                    updatePaginationNumbers();

                    String countText = formatLargeNumber(totalElements) + " agent(s)";
                    totalCountLabel.setText(countText);

                    logger.debug("Interface mise à jour: {} agents affichés", agents.size());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des agents", getException());
                    totalCountLabel.setText("Erreur");
                    paginationInfoLabel.setText("Erreur de chargement");

                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les agents",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private String formatLargeNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format("%.1fk", number / 1000.0);
        } else {
            return String.format("%.1fM", number / 1000000.0);
        }
    }

    // Actions

    private void performSearch() {
        currentPage = 1;
        loadData();
    }

    private void clearFilters() {
        searchField.clear();
        gradeComboBox.setValue(null);
        statutComboBox.setValue(null);
        performSearch();
    }

    private void createNewAgent() {
        logger.info("Création d'un nouvel agent");
        AlertUtil.showInfoAlert("Nouvel agent",
                "Fonctionnalité en développement",
                "Le formulaire de création sera disponible prochainement.");
    }

    private void viewAgent(AgentViewModel agent) {
        logger.info("Affichage des détails de l'agent: {}", agent.getCodeAgent());
        AlertUtil.showInfoAlert("Détails de l'agent",
                "Agent: " + agent.getCodeAgent() + " - " + agent.getNom() + " " + agent.getPrenom(),
                "La vue de détail sera disponible prochainement.");
    }

    private void editAgent(AgentViewModel agent) {
        logger.info("Modification de l'agent: {}", agent.getCodeAgent());
        AlertUtil.showInfoAlert("Modification d'agent",
                "Agent: " + agent.getCodeAgent(),
                "Le formulaire de modification sera disponible prochainement.");
    }

    private void toggleAgentStatus(AgentViewModel agent) {
        Task<Void> toggleTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (agent.getActif()) {
                    agentService.deactivateAgent(agent.getId());
                } else {
                    agentService.reactivateAgent(agent.getId());
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    String action = agent.getActif() ? "désactivé" : "activé";
                    AlertUtil.showInfoAlert("Statut modifié",
                            "Agent " + action,
                            "L'agent " + agent.getCodeAgent() + " a été " + action + " avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du changement de statut", getException());
                    AlertUtil.showErrorAlert("Erreur",
                            "Impossible de modifier le statut",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread toggleThread = new Thread(toggleTask);
        toggleThread.setDaemon(true);
        toggleThread.start();
    }

    private void editSelectedAgents() {
        List<AgentViewModel> selected = getSelectedAgents();
        if (selected.size() == 1) {
            editAgent(selected.get(0));
        }
    }

    private void deleteSelectedAgents() {
        List<AgentViewModel> selected = getSelectedAgents();
        if (selected.isEmpty()) return;

        String message = selected.size() == 1
                ? "Voulez-vous vraiment supprimer l'agent " + selected.get(0).getCodeAgent() + " ?"
                : "Voulez-vous vraiment supprimer les " + selected.size() + " agents sélectionnés ?";

        if (AlertUtil.showConfirmAlert("Confirmation de suppression",
                "Supprimer les agents", message)) {
            performDeletion(selected);
        }
    }

    private void performDeletion(List<AgentViewModel> agents) {
        Task<Void> deleteTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                for (AgentViewModel agent : agents) {
                    agentService.deleteAgent(agent.getId());
                }
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    AlertUtil.showInfoAlert("Suppression réussie",
                            "Agents supprimés",
                            agents.size() + " agent(s) supprimé(s) avec succès.");
                    loadData();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la suppression", getException());
                    AlertUtil.showErrorAlert("Erreur de suppression",
                            "Impossible de supprimer les agents",
                            "Une erreur technique s'est produite.");
                });
            }
        };

        Thread deleteThread = new Thread(deleteTask);
        deleteThread.setDaemon(true);
        deleteThread.start();
    }

    private void activateSelectedAgents() {
        performStatusChange(getSelectedAgents(), true);
    }

    private void deactivateSelectedAgents() {
        performStatusChange(getSelectedAgents(), false);
    }

    private void performStatusChange(List<AgentViewModel> agents, boolean activate) {
        if (agents.isEmpty()) return;

        String action = activate ? "activer" : "désactiver";
        String message = "Voulez-vous vraiment " + action + " les " + agents.size() + " agents sélectionnés ?";

        if (AlertUtil.showConfirmAlert("Confirmation",
                "Modifier le statut", message)) {

            Task<Void> statusTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    for (AgentViewModel agent : agents) {
                        if (activate) {
                            agentService.reactivateAgent(agent.getId());
                        } else {
                            agentService.deactivateAgent(agent.getId());
                        }
                    }
                    return null;
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        String pastAction = activate ? "activés" : "désactivés";
                        AlertUtil.showInfoAlert("Statut modifié",
                                "Agents " + pastAction,
                                agents.size() + " agent(s) " + pastAction + " avec succès.");
                        loadData();
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        logger.error("Erreur lors du changement de statut", getException());
                        AlertUtil.showErrorAlert("Erreur",
                                "Impossible de modifier le statut",
                                "Une erreur technique s'est produite.");
                    });
                }
            };

            Thread statusThread = new Thread(statusTask);
            statusThread.setDaemon(true);
            statusThread.start();
        }
    }

    private void printSelectedAgents() {
        List<AgentViewModel> selected = getSelectedAgents();
        logger.info("Impression de {} agent(s)", selected.size());
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
        long selectedCount = agents.stream()
                .mapToLong(a -> a.isSelected() ? 1 : 0)
                .sum();

        long selectedActiveCount = agents.stream()
                .filter(AgentViewModel::isSelected)
                .mapToLong(a -> a.getActif() ? 1 : 0)
                .sum();

        long selectedInactiveCount = selectedCount - selectedActiveCount;

        editButton.setDisable(selectedCount != 1);
        deleteButton.setDisable(selectedCount == 0);
        activateButton.setDisable(selectedInactiveCount == 0);
        deactivateButton.setDisable(selectedActiveCount == 0);
        printButton.setDisable(selectedCount == 0);
    }

    private void toggleSelectAll() {
        boolean selectAll = selectAllCheckBox.isSelected();
        agents.forEach(agent -> agent.setSelected(selectAll));
        updateActionButtons();
    }

    private List<AgentViewModel> getSelectedAgents() {
        return agents.stream()
                .filter(AgentViewModel::isSelected)
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
     * Modèle d'affichage pour le tableau des agents
     */
    public static class AgentViewModel {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private Long id;
        private String codeAgent;
        private String nom;
        private String prenom;
        private String grade;
        private Long serviceId;
        private String serviceNom;
        private Boolean actif;
        private LocalDateTime createdAt;

        // Getters et setters
        public boolean isSelected() { return selected.get(); }
        public BooleanProperty selectedProperty() { return selected; }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCodeAgent() { return codeAgent; }
        public void setCodeAgent(String codeAgent) { this.codeAgent = codeAgent; }

        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }

        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }

        public String getNomComplet() {
            return (prenom != null ? prenom + " " : "") + (nom != null ? nom : "");
        }

        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }

        public Long getServiceId() { return serviceId; }
        public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

        public String getServiceNom() { return serviceNom; }
        public void setServiceNom(String serviceNom) { this.serviceNom = serviceNom; }

        public Boolean getActif() { return actif; }
        public void setActif(Boolean actif) { this.actif = actif; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}