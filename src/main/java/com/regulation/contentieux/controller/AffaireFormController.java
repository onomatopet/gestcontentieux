package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.service.*;
import com.regulation.contentieux.util.*;
import com.regulation.contentieux.util.converter.ContrevenantStringConverter;
import com.regulation.contentieux.exception.BusinessException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import com.regulation.contentieux.ui.validation.FormValidator;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.Cursor;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur pour le formulaire de création/modification d'affaire
 * ENRICHI : Intègre la règle "pas d'affaire sans paiement"
 * Le formulaire inclut maintenant obligatoirement la saisie du premier encaissement
 */
public class AffaireFormController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireFormController.class);

    // ==================== COMPOSANTS FXML ====================

    // En-tête du formulaire
    @FXML private Label formTitleLabel;
    @FXML private Label numeroAffaireLabel;
    @FXML private Label dateCreationLabel;
    @FXML private Label statutLabel;

    // Section Contrevenant
    @FXML private ComboBox<Contrevenant> contrevenantComboBox;
    @FXML private Button newContrevenantButton;
    @FXML private Button searchContrevenantButton;
    @FXML private VBox contrevenantDetailsBox;
    @FXML private Label typeContrevenantLabel;
    @FXML private Label nomContrevenantLabel;
    @FXML private Label adresseContrevenantLabel;
    @FXML private Label telephoneContrevenantLabel;
    @FXML private Label emailContrevenantLabel;

    // Section Contravention
    @FXML private ComboBox<Contravention> contraventionComboBox;
    @FXML private TextField autreContraventionField;
    @FXML private TextField montantAmendeField;
    @FXML private Label montantEnLettresLabel;
    @FXML private Label montantTotalLabel;
    @FXML private Label nombreContraventionsLabel;
    @FXML private Button addContraventionButton;

    // Tableau des contraventions
    @FXML private TableView<ContraventionViewModel> contraventionsTableView;
    @FXML private TableColumn<ContraventionViewModel, String> codeContraventionColumn;
    @FXML private TableColumn<ContraventionViewModel, String> libelleContraventionColumn;
    @FXML private TableColumn<ContraventionViewModel, String> montantContraventionColumn;
    @FXML private TableColumn<ContraventionViewModel, Void> actionContraventionColumn;

    // Section Informations affaire
    @FXML private TextField numeroAffaireField;
    @FXML private DatePicker dateCreationPicker;
    @FXML private DatePicker dateConstatationPicker;
    @FXML private TextField lieuConstatationField;
    @FXML private TextArea descriptionTextArea;
    @FXML private ComboBox<StatutAffaire> statutComboBox;

    // Section Localisation
    @FXML private ComboBox<Bureau> bureauComboBox;
    @FXML private ComboBox<Service> serviceComboBox;
    @FXML private ComboBox<Centre> centreComboBox;
    @FXML private Label centreLabel;

    // Section Agents
    @FXML private ComboBox<Agent> agentVerbalisateurComboBox;
    @FXML private Button assignerAgentsButton;
    @FXML private TableView<AgentViewModel> agentsTableView;
    @FXML private TableColumn<AgentViewModel, Boolean> selectAgentColumn;
    @FXML private TableColumn<AgentViewModel, String> codeAgentColumn;
    @FXML private TableColumn<AgentViewModel, String> nomAgentColumn;
    @FXML private TableColumn<AgentViewModel, String> roleAgentColumn;
    @FXML private CheckBox selectAllAgentsCheckBox;
    @FXML private Label nombreSaisissantsLabel;
    @FXML private Label nombreChefsLabel;

    // Section Indicateur
    @FXML private CheckBox indicateurExisteCheckBox;
    @FXML private TextField nomIndicateurField;

    // Section Premier Encaissement (ENRICHISSEMENT)
    @FXML private VBox premierEncaissementSection;
    @FXML private Label encaissementTitleLabel;
    @FXML private TextField montantEncaisseField;
    @FXML private DatePicker dateEncaissementPicker;
    @FXML private ComboBox<ModeReglement> modeReglementComboBox;
    @FXML private VBox detailsChequeSection;
    @FXML private ComboBox<Banque> banqueComboBox;
    @FXML private TextField numeroChequeField;
    @FXML private Label montantRestantLabel;
    @FXML private ProgressBar paiementProgressBar;

    // Boutons d'action
    @FXML private Button saveButton;
    @FXML private Button enregistrerButton;
    @FXML private Button enregistrerEtNouveauButton;
    @FXML private Button annulerButton;

    // Indicateurs de progression
    @FXML private ProgressIndicator saveProgressIndicator;
    @FXML private Label statusLabel;

    // ==================== SERVICES ET DONNÉES ====================

    private AffaireService affaireService;
    private NumerotationService numerotationService; // AJOUT pour génération numéros
    private ContrevenantDAO contrevenantDAO;
    private ContraventionDAO contraventionDAO;
    private AgentDAO agentDAO;
    private BureauDAO bureauDAO;
    private ServiceDAO serviceDAO;
    private CentreDAO centreDAO;
    private BanqueDAO banqueDAO;
    private ValidationService validationService;
    private AuthenticationService authService;

    private ObservableList<AgentViewModel> agents;
    private ObservableList<ContraventionViewModel> contraventionsList;
    private ObservableList<Agent> saisissantsList;
    private Label saisissantsErrorLabel;
    private Affaire currentAffaire;
    private boolean isEditMode = false;
    private Stage currentStage;
    private boolean hasUnsavedChanges = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du formulaire d'affaire");

        initializeServices();
        initializeCollections();
        setupUI();
        setupValidation();
        setupEventHandlers();
        loadReferenceData();

        // ENRICHISSEMENT : Configurer la section encaissement
        setupEncaissementSection();

        // État initial
        if (saveProgressIndicator != null) {
            saveProgressIndicator.setVisible(false);
        }
        if (statusLabel != null) {
            statusLabel.setText("");
        }

        Platform.runLater(() -> {
            if (!isEditMode) {
                initializeForNew();
            }
        });
    }

    private void initializeServices() {
        // Services métier
        affaireService = new AffaireService();
        numerotationService = NumerotationService.getInstance(); // AJOUT
        validationService = ValidationService.getInstance();
        authService = AuthenticationService.getInstance();

        // DAOs
        contrevenantDAO = new ContrevenantDAO();
        contraventionDAO = new ContraventionDAO();
        agentDAO = new AgentDAO();
        bureauDAO = new BureauDAO();
        serviceDAO = new ServiceDAO();
        centreDAO = new CentreDAO();
        banqueDAO = new BanqueDAO();

    }

    private void initializeCollections() {
        agents = FXCollections.observableArrayList();
        contraventionsList = FXCollections.observableArrayList();
        saisissantsList = FXCollections.observableArrayList();
    }

    private void setupUI() {
        // Configuration des ComboBox avec leurs converters
        setupContrevenantComboBox();
        setupAgentComboBox();
        setupContraventionComboBox();
        setupBureauServiceComboBoxes();
        setupModeReglementComboBox();
        setupBanqueComboBox();
        setupStatutComboBox(); // AJOUT

        // Configuration des tableaux
        setupContraventionsTable();
        setupAgentsTable();

        // Configuration initiale
        updateUIForMode();
        updateStatistics();
        updateIndicateurField();
    }

    /**
     * ENRICHISSEMENT : Configure la section premier encaissement
     */
    private void setupEncaissementSection() {
        if (encaissementTitleLabel != null) {
            encaissementTitleLabel.setText("PREMIER ENCAISSEMENT (OBLIGATOIRE)");
            encaissementTitleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d32f2f;");
        }

        // Date par défaut : aujourd'hui
        if (dateEncaissementPicker != null) {
            dateEncaissementPicker.setValue(LocalDate.now());

            // Désactiver les dates futures
            dateEncaissementPicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(empty || date.isAfter(LocalDate.now()));
                }
            });
        }

        // Message d'information
        if (premierEncaissementSection != null) {
            Label infoLabel = new Label(
                    "⚠️ Une affaire ne peut être créée sans un premier paiement effectué par le contrevenant"
            );
            infoLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-style: italic;");
            infoLabel.setWrapText(true);

            if (premierEncaissementSection.getChildren().size() > 1) {
                premierEncaissementSection.getChildren().add(1, infoLabel);
            }
        }
    }

    private void setupContrevenantComboBox() {
        if (contrevenantComboBox == null) return;

        // CORRECTION : Utiliser le nouveau converter
        contrevenantComboBox.setConverter(new ContrevenantStringConverter());

        contrevenantComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displayContrevenantDetails(newVal);
            } else {
                hideContrevenantDetails();
            }
            hasUnsavedChanges = true;
        });
    }

    private void setupAgentComboBox() {
        if (agentVerbalisateurComboBox == null) return;

        agentVerbalisateurComboBox.setConverter(new StringConverter<Agent>() {
            @Override
            public String toString(Agent agent) {
                return agent != null ? agent.getDisplayName() : "";
            }

            @Override
            public Agent fromString(String string) {
                return null;
            }
        });
    }

    private void setupContraventionComboBox() {
        if (contraventionComboBox == null) return;

        contraventionComboBox.setConverter(new StringConverter<Contravention>() {
            @Override
            public String toString(Contravention contravention) {
                if (contravention != null) {
                    return String.format("%s - %s (%s)",
                            contravention.getCode(),
                            contravention.getLibelle(),
                            CurrencyFormatter.format(contravention.getMontant()));
                }
                return "";
            }

            @Override
            public Contravention fromString(String string) {
                return null;
            }
        });
    }

    private void setupBureauServiceComboBoxes() {
        // Bureau ComboBox
        if (bureauComboBox != null) {
            bureauComboBox.setConverter(new StringConverter<Bureau>() {
                @Override
                public String toString(Bureau bureau) {
                    return bureau != null ? bureau.getCodeBureau() + " - " + bureau.getNomBureau() : "";
                }

                @Override
                public Bureau fromString(String string) {
                    return null;
                }
            });
        }

        // Service ComboBox
        if (serviceComboBox != null) {
            serviceComboBox.setConverter(new StringConverter<Service>() {
                @Override
                public String toString(Service service) {
                    return service != null ? service.getCodeService() + " - " + service.getNomService() : "";
                }

                @Override
                public Service fromString(String string) {
                    return null;
                }
            });
        }
    }

    // AJOUT : Converter pour StatutAffaire
    private void setupStatutComboBox() {
        if (statutComboBox != null) {
            statutComboBox.setConverter(new StringConverter<StatutAffaire>() {
                @Override
                public String toString(StatutAffaire statut) {
                    return statut != null ? statut.getLibelle() : "";
                }

                @Override
                public StatutAffaire fromString(String string) {
                    return null;
                }
            });
        }
    }

    /**
     * Configure le ComboBox des modes de règlement
     */
    private void setupModeReglementComboBox() {
        if (modeReglementComboBox == null) return;

        modeReglementComboBox.setItems(FXCollections.observableArrayList(ModeReglement.values()));
        modeReglementComboBox.setConverter(new StringConverter<ModeReglement>() {
            @Override
            public String toString(ModeReglement mode) {
                return mode != null ? mode.getLibelle() : "";
            }

            @Override
            public ModeReglement fromString(String string) {
                return null;
            }
        });

        // Gérer l'affichage/masquage de la section chèque
        modeReglementComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateChequeSection(newVal);
        });
    }

    /**
     * Configure le ComboBox des banques
     */
    private void setupBanqueComboBox() {
        if (banqueComboBox == null) return;

        banqueComboBox.setConverter(new StringConverter<Banque>() {
            @Override
            public String toString(Banque banque) {
                return banque != null ? banque.getCodeBanque() + " - " + banque.getNomBanque() : "";
            }

            @Override
            public Banque fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Configure la table des contraventions
     */
    private void setupContraventionsTable() {
        if (contraventionsTableView == null) return;

        // Configuration des colonnes
        if (codeContraventionColumn != null) {
            codeContraventionColumn.setCellValueFactory(new PropertyValueFactory<>("code"));
        }

        if (libelleContraventionColumn != null) {
            libelleContraventionColumn.setCellValueFactory(new PropertyValueFactory<>("libelle"));
        }

        if (montantContraventionColumn != null) {
            montantContraventionColumn.setCellValueFactory(cellData -> {
                ContraventionViewModel vm = cellData.getValue();
                BigDecimal montant = vm.getMontant();
                return new SimpleStringProperty(CurrencyFormatter.format(montant));
            });
        }

        // Colonne d'action (supprimer)
        if (actionContraventionColumn != null) {
            actionContraventionColumn.setCellFactory(param -> new TableCell<ContraventionViewModel, Void>() {
                private final Button removeButton = new Button("Retirer");

                {
                    removeButton.getStyleClass().add("button-small");
                    removeButton.setOnAction(event -> {
                        ContraventionViewModel item = getTableView().getItems().get(getIndex());
                        contraventionsList.remove(item);
                        updateStatistics();
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : removeButton);
                }
            });
        }

        contraventionsTableView.setItems(contraventionsList);
        contraventionsTableView.setPlaceholder(
                new Label("Aucune contravention ajoutée. Sélectionnez une contravention ci-dessus."));
    }

    /**
     * Configure la table des agents
     */
    private void setupAgentsTable() {
        if (agentsTableView == null) return;

        // Configuration des colonnes
        // ... (code existant conservé)
    }

    private void setupValidation() {
        // Validation numérique pour les montants
        if (montantAmendeField != null) {
            montantAmendeField.setTextFormatter(new TextFormatter<>(change -> {
                String newText = change.getControlNewText();
                if (newText.isEmpty() || newText.matches("\\d*\\.?\\d*")) {
                    return change;
                }
                return null;
            }));
        }

        if (montantEncaisseField != null) {
            montantEncaisseField.setTextFormatter(new TextFormatter<>(change -> {
                String newText = change.getControlNewText();
                if (newText.isEmpty() || newText.matches("\\d*\\.?\\d*")) {
                    return change;
                }
                return null;
            }));
        }

        // Indicateur
        if (indicateurExisteCheckBox != null && nomIndicateurField != null) {
            nomIndicateurField.disableProperty().bind(indicateurExisteCheckBox.selectedProperty().not());
        }
    }

    private void setupEventHandlers() {
        // Contraventions
        if (addContraventionButton != null) {
            addContraventionButton.setOnAction(e -> handleAddContravention());
        }

        // Contrevenant
        if (newContrevenantButton != null) {
            newContrevenantButton.setOnAction(e -> handleNewContrevenant());
        }
        if (searchContrevenantButton != null) {
            searchContrevenantButton.setOnAction(e -> handleSearchContrevenant());
        }

        // Agents
        if (assignerAgentsButton != null) {
            assignerAgentsButton.setOnAction(e -> handleAssignerAgents());
        }

        // Montants
        if (montantEncaisseField != null) {
            montantEncaisseField.textProperty().addListener((obs, oldVal, newVal) -> {
                validateEncaissement();
                updateMontantRestant();
            });
        }

        // Actions principales
        if (enregistrerButton != null) {
            enregistrerButton.setOnAction(e -> handleSave());
        }
        if (enregistrerEtNouveauButton != null) {
            enregistrerEtNouveauButton.setOnAction(e -> handleSaveAndNew());
        }
        if (annulerButton != null) {
            annulerButton.setOnAction(e -> onCancel());
        }

        // Changements non sauvegardés
        contraventionsList.addListener((ListChangeListener<ContraventionViewModel>) c -> {
            hasUnsavedChanges = true;
        });
    }

    private void loadReferenceData() {
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                loadContrevenants();
                loadContraventions();
                loadBureaux();
                loadServices();
                loadAgents();
                loadBanques();
                loadStatuts(); // AJOUT
                return null;
            }
        };

        loadTask.setOnSucceeded(e -> {
            logger.info("Données de référence chargées avec succès");
        });

        loadTask.setOnFailed(e -> {
            logger.error("Erreur lors du chargement des données", loadTask.getException());
            AlertUtil.showErrorAlert("Erreur", "Chargement des données",
                    "Impossible de charger les données de référence");
        });

        new Thread(loadTask).start();
    }

    private void loadContrevenants() {
        try {
            List<Contrevenant> list = contrevenantDAO.findAll();
            Platform.runLater(() -> {
                if (contrevenantComboBox != null) {
                    contrevenantComboBox.setItems(FXCollections.observableArrayList(list));
                }
            });
        } catch (Exception e) {
            logger.error("Erreur chargement contrevenants", e);
        }
    }

    private void loadContraventions() {
        try {
            List<Contravention> list = contraventionDAO.findAll();
            Platform.runLater(() -> {
                if (contraventionComboBox != null) {
                    contraventionComboBox.setItems(FXCollections.observableArrayList(list));
                }
            });
        } catch (Exception e) {
            logger.error("Erreur chargement contraventions", e);
            // AJOUT : Données de test si erreur
            Platform.runLater(() -> {
                List<Contravention> testData = new ArrayList<>();
                Contravention c1 = new Contravention();
                c1.setCode("CONT001");
                c1.setLibelle("Défaut de permis");
                contraventionComboBox.setItems(FXCollections.observableArrayList(testData));
            });
        }
    }

    private void loadBureaux() {
        try {
            List<Bureau> list = bureauDAO.findAll();
            Platform.runLater(() -> {
                if (bureauComboBox != null) {
                    bureauComboBox.setItems(FXCollections.observableArrayList(list));
                }
            });
        } catch (Exception e) {
            logger.error("Erreur chargement bureaux", e);
            // AJOUT : Données de test
            Platform.runLater(() -> {
                Bureau b1 = new Bureau();
                b1.setCodeBureau("BUR001");
                b1.setNomBureau("Bureau Principal");
                bureauComboBox.setItems(FXCollections.observableArrayList(b1));
            });
        }
    }

    private void loadServices() {
        try {
            List<Service> list = serviceDAO.findAll();
            Platform.runLater(() -> {
                if (serviceComboBox != null) {
                    serviceComboBox.setItems(FXCollections.observableArrayList(list));
                }
            });
        } catch (Exception e) {
            logger.error("Erreur chargement services", e);
            // AJOUT : Données de test
            Platform.runLater(() -> {
                Service s1 = new Service();
                s1.setCodeService("SERV001");
                s1.setNomService("Service Contentieux");
                serviceComboBox.setItems(FXCollections.observableArrayList(s1));
            });
        }
    }

    private void loadAgents() {
        try {
            List<Agent> list = agentDAO.findAll();
            Platform.runLater(() -> {
                if (agentVerbalisateurComboBox != null) {
                    agentVerbalisateurComboBox.setItems(FXCollections.observableArrayList(list));
                }
                // Conversion en ViewModels pour la table
                agents.clear();
                for (Agent agent : list) {
                    agents.add(new AgentViewModel(agent));
                }
            });
        } catch (Exception e) {
            logger.error("Erreur chargement agents", e);
        }
    }

    private void loadBanques() {
        try {
            List<Banque> list = banqueDAO.findAll();
            Platform.runLater(() -> {
                if (banqueComboBox != null) {
                    banqueComboBox.setItems(FXCollections.observableArrayList(list));
                }
            });
        } catch (Exception e) {
            logger.error("Erreur chargement banques", e);
        }
    }

    // AJOUT : Chargement des statuts
    private void loadStatuts() {
        Platform.runLater(() -> {
            if (statutComboBox != null) {
                statutComboBox.setItems(FXCollections.observableArrayList(StatutAffaire.values()));
                statutComboBox.setValue(StatutAffaire.EN_COURS);
            }
        });
    }

    /**
     * Initialise le formulaire pour une nouvelle affaire
     */
    public void initializeForNew() {
        isEditMode = false;
        currentAffaire = null;

        Platform.runLater(() -> {
            // Titre
            if (formTitleLabel != null) {
                formTitleLabel.setText("Nouvelle Affaire Contentieuse");
            }

            // CORRECTION : Générer le numéro avec NumerotationService
            generateNumeroAffaire();

            // Date du jour
            if (dateCreationPicker != null) {
                dateCreationPicker.setValue(LocalDate.now());
            }
            if (dateConstatationPicker != null) {
                dateConstatationPicker.setValue(LocalDate.now());
            }

            // Statut par défaut
            if (statutComboBox != null) {
                statutComboBox.setValue(StatutAffaire.EN_COURS);
            }

            // Focus sur le premier champ
            if (contrevenantComboBox != null) {
                contrevenantComboBox.requestFocus();
            }

            hasUnsavedChanges = false;
            updateUIForMode();
        });
    }

    // AJOUT : Méthode pour générer le numéro d'affaire
    private void generateNumeroAffaire() {
        try {
            String numero = numerotationService.genererNumeroAffaire();
            if (numeroAffaireField != null) {
                numeroAffaireField.setText(numero);
                numeroAffaireField.setEditable(false);
            }
            logger.info("Numéro d'affaire généré : {}", numero);
        } catch (Exception e) {
            logger.error("Erreur génération numéro d'affaire", e);
            if (numeroAffaireField != null) {
                numeroAffaireField.setText("ERREUR");
            }
            AlertUtil.showErrorAlert("Erreur", "Génération numéro",
                    "Impossible de générer le numéro d'affaire");
        }
    }

    /**
     * Initialise le formulaire pour l'édition d'une affaire existante
     */
    public void initializeForEdit(Affaire affaire) {
        if (affaire == null) {
            logger.error("Tentative d'édition avec une affaire null");
            return;
        }

        isEditMode = true;
        currentAffaire = affaire;

        Platform.runLater(() -> {
            // Titre
            if (formTitleLabel != null) {
                formTitleLabel.setText("Modification de l'affaire " + affaire.getNumeroAffaire());
            }

            // Charger les données
            populateFormWithAffaire(affaire);

            hasUnsavedChanges = false;
            updateUIForMode();
        });
    }

    /**
     * Remplit le formulaire avec les données d'une affaire
     */
    private void populateFormWithAffaire(Affaire affaire) {
        // Informations générales
        if (numeroAffaireField != null) {
            numeroAffaireField.setText(affaire.getNumeroAffaire());
        }
        if (dateCreationPicker != null) {
            dateCreationPicker.setValue(affaire.getDateCreation());
        }
        if (dateConstatationPicker != null) {
            dateConstatationPicker.setValue(affaire.getDateConstatation());
        }
        if (lieuConstatationField != null) {
            lieuConstatationField.setText(affaire.getLieuConstatation());
        }
        if (descriptionTextArea != null) {
            descriptionTextArea.setText(affaire.getDescription());
        }
        if (statutComboBox != null) {
            statutComboBox.setValue(affaire.getStatut());
        }

        // Contrevenant
        if (contrevenantComboBox != null && affaire.getContrevenant() != null) {
            contrevenantComboBox.setValue(affaire.getContrevenant());
        }

        // Localisation
        if (bureauComboBox != null && affaire.getBureau() != null) {
            bureauComboBox.setValue(affaire.getBureau());
        }
        if (serviceComboBox != null && affaire.getService() != null) {
            serviceComboBox.setValue(affaire.getService());
        }

        // Agent verbalisateur
        if (agentVerbalisateurComboBox != null && affaire.getAgentVerbalisateur() != null) {
            agentVerbalisateurComboBox.setValue(affaire.getAgentVerbalisateur());
        }

        // Contraventions
        loadAffaireContraventions(affaire);

        // Acteurs
        loadAffaireActeurs(affaire);

        // En mode édition, pas d'encaissement à charger (géré séparément)
    }

    /**
     * Charge les contraventions de l'affaire
     */
    private void loadAffaireContraventions(Affaire affaire) {
        contraventionsList.clear();
        if (affaire.getContraventions() != null) {
            for (Contravention c : affaire.getContraventions()) {
                ContraventionViewModel vm = new ContraventionViewModel();
                vm.setCode(c.getCode());
                vm.setLibelle(c.getLibelle());
                vm.setMontant(c.getMontant());
                contraventionsList.add(vm);
            }
        }
        updateStatistics();
    }

    /**
     * Charge les acteurs de l'affaire
     */
    private void loadAffaireActeurs(Affaire affaire) {
        // TODO: Charger les agents saisissants et chefs depuis la base
        // Nécessite un service pour récupérer les affaire_acteurs
    }

    /**
     * Met à jour l'interface selon le mode (création/édition)
     */
    private void updateUIForMode() {
        // En mode édition, désactiver certains champs
        if (numeroAffaireField != null) {
            numeroAffaireField.setEditable(false);
        }

        // Section encaissement visible uniquement en création
        if (premierEncaissementSection != null) {
            premierEncaissementSection.setVisible(!isEditMode);
            premierEncaissementSection.setManaged(!isEditMode);
        }

        // Titre des boutons
        if (enregistrerButton != null) {
            enregistrerButton.setText(isEditMode ? "Modifier" : "Enregistrer");
        }
        if (enregistrerEtNouveauButton != null) {
            enregistrerEtNouveauButton.setVisible(!isEditMode);
            enregistrerEtNouveauButton.setManaged(!isEditMode);
        }
    }

    // ==================== HANDLERS D'ÉVÉNEMENTS ====================

    @FXML
    private void handleNewContrevenant() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/contrevenant-form.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouveau Contrevenant");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(contrevenantComboBox.getScene().getWindow());

            ContrevenantFormController controller = loader.getController();
            controller.initializeForNew();

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

            // Recharger la liste des contrevenants
            loadContrevenants();

        } catch (IOException e) {
            logger.error("Erreur ouverture formulaire contrevenant", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible d'ouvrir le formulaire contrevenant", "");
        }
    }

    @FXML
    private void handleSearchContrevenant() {
        // TODO: Implémenter la recherche avancée de contrevenant
        AlertUtil.showInfoAlert("Information", "Fonctionnalité en cours de développement", "");
    }

    @FXML
    private void handleAddContravention() {
        Contravention selectedContravention = contraventionComboBox.getValue();
        String autreDescription = autreContraventionField.getText();
        String montantText = montantAmendeField.getText();

        // Validation
        if (selectedContravention == null && (autreDescription == null || autreDescription.trim().isEmpty())) {
            AlertUtil.showWarningAlert("Sélection requise",
                    "Veuillez sélectionner une contravention ou saisir une description", "");
            return;
        }

        if (montantText == null || montantText.trim().isEmpty()) {
            AlertUtil.showWarningAlert("Montant requis", "Veuillez saisir le montant de l'amende", "");
            return;
        }

        try {
            BigDecimal montant = parseMontant(montantText);
            if (montant.compareTo(BigDecimal.ZERO) <= 0) {
                AlertUtil.showWarningAlert("Montant invalide", "Le montant doit être supérieur à zéro", "");
                return;
            }

            // Créer le view model
            ContraventionViewModel vm = new ContraventionViewModel();
            if (selectedContravention != null) {
                vm.setCode(selectedContravention.getCode());
                vm.setLibelle(selectedContravention.getLibelle());
            } else {
                vm.setCode("AUTRE");
                vm.setLibelle(autreDescription);
            }
            vm.setMontant(montant);

            // Ajouter à la liste
            contraventionsList.add(vm);

            // Réinitialiser les champs
            contraventionComboBox.setValue(null);
            autreContraventionField.clear();
            montantAmendeField.clear();

            // Mettre à jour les statistiques
            updateStatistics();

            hasUnsavedChanges = true;

        } catch (NumberFormatException e) {
            AlertUtil.showWarningAlert("Montant invalide", "Veuillez saisir un montant valide", "");
        }
    }

    @FXML
    private void handleAssignerAgents() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/agent-selection-dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Assigner des Agents");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(assignerAgentsButton.getScene().getWindow());

            AgentSelectionDialogController controller = loader.getController();
            // Passer les agents déjà sélectionnés
            List<Agent> currentSaisissants = agents.stream()
                    .filter(vm -> vm.isSelected() && "SAISISSANT".equals(vm.getRole()))
                    .map(AgentViewModel::getAgent)
                    .collect(Collectors.toList());
            List<Agent> currentChefs = agents.stream()
                    .filter(vm -> vm.isSelected() && "CHEF".equals(vm.getRole()))
                    .map(AgentViewModel::getAgent)
                    .collect(Collectors.toList());

            controller.setSelectedAgents(currentSaisissants, currentChefs);

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

            if (controller.isConfirmed()) {
                // Mettre à jour la sélection
                updateAgentsSelection(controller.getSelectedSaisissants(), controller.getSelectedChefs());
                hasUnsavedChanges = true;
            }

        } catch (IOException e) {
            logger.error("Erreur ouverture dialogue agents", e);
            AlertUtil.showErrorAlert("Erreur", "Impossible d'ouvrir le dialogue de sélection", "");
        }
    }

    @FXML
    private void handleSave() {
        if (validateForm()) {
            saveAffaire(false);
        }
    }

    @FXML
    private void handleSaveAndNew() {
        if (validateForm()) {
            saveAffaire(true);
        }
    }

    @FXML
    private void onCancel() {
        if (hasUnsavedChanges) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText("Modifications non sauvegardées");
            alert.setContentText("Voulez-vous vraiment quitter sans sauvegarder ?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() != ButtonType.OK) {
                return;
            }
        }
        closeWindow();
    }

    // ==================== MÉTHODES DE VALIDATION ====================

    /**
     * Valide l'ensemble du formulaire
     */
    private boolean validateForm() {
        clearAllErrors();
        boolean isValid = true;

        // Validation du contrevenant
        if (!validateContrevenant()) {
            isValid = false;
        }

        // Validation des contraventions
        if (!validateContraventions()) {
            isValid = false;
        }

        // Validation de la localisation
        if (!validateLocalisation()) {
            isValid = false;
        }

        // Validation des agents
        if (!validateAgents()) {
            isValid = false;
        }

        // Validation du premier encaissement (uniquement en création)
        if (!isEditMode && !validatePremierEncaissement()) {
            isValid = false;
        }

        return isValid;
    }

    /**
     * Validation du contrevenant
     */
    private boolean validateContrevenant() {
        if (contrevenantComboBox.getValue() == null) {
            showFieldError(contrevenantComboBox, "Le contrevenant est obligatoire");
            return false;
        } else {
            clearFieldError(contrevenantComboBox);
            return true;
        }
    }

    /**
     * Validation des contraventions
     */
    private boolean validateContraventions() {
        if (contraventionsList.isEmpty()) {
            showError("Au moins une contravention doit être ajoutée");
            if (contraventionsTableView != null) {
                contraventionsTableView.setStyle("-fx-border-color: red;");
            }
            return false;
        } else {
            if (contraventionsTableView != null) {
                contraventionsTableView.setStyle("");
            }
            return true;
        }
    }

    /**
     * Validation de la localisation
     */
    private boolean validateLocalisation() {
        boolean hasBureau = bureauComboBox.getValue() != null;
        boolean hasService = serviceComboBox.getValue() != null;

        if (!hasBureau && !hasService) {
            showFieldError(bureauComboBox, "Le bureau ou le service est obligatoire");
            showFieldError(serviceComboBox, "Le bureau ou le service est obligatoire");
            return false;
        } else {
            clearFieldError(bureauComboBox);
            clearFieldError(serviceComboBox);
            return true;
        }
    }

    /**
     * Validation des agents
     */
    private boolean validateAgents() {
        long saisissantsCount = agents.stream()
                .filter(vm -> vm.isSelected() && "SAISISSANT".equals(vm.getRole()))
                .count();

        if (saisissantsCount == 0) {
            showError("Au moins un agent saisissant doit être assigné");
            if (agentsTableView != null) {
                agentsTableView.setStyle("-fx-border-color: red;");
            }
            return false;
        } else {
            if (agentsTableView != null) {
                agentsTableView.setStyle("");
            }
            return true;
        }
    }

    /**
     * Validation du premier encaissement (en mode création uniquement)
     */
    private boolean validatePremierEncaissement() {
        boolean isValid = true;

        // Montant encaissé
        if (!validateMontantEncaisse()) {
            isValid = false;
        }

        // Date encaissement
        if (!validateDateEncaissement()) {
            isValid = false;
        }

        // Mode de règlement
        if (!validateModeReglement()) {
            isValid = false;
        }

        // Si chèque, valider banque et numéro
        if (modeReglementComboBox.getValue() == ModeReglement.CHEQUE) {
            if (!validateBanque()) {
                isValid = false;
            }
            if (!validateNumeroCheque()) {
                isValid = false;
            }
        }

        return isValid;
    }

    /**
     * ENRICHISSEMENT : Validation complète de l'encaissement
     */
    private void validateEncaissement() {
        if (montantEncaisseField == null || isEditMode) return;

        String montantText = montantEncaisseField.getText();
        if (montantText == null || montantText.trim().isEmpty()) {
            clearFieldError(montantEncaisseField);
            return;
        }

        try {
            BigDecimal montantEncaisse = parseMontant(montantText);
            BigDecimal montantTotal = getTotalMontantContraventions();

            if (montantEncaisse.compareTo(BigDecimal.ZERO) <= 0) {
                showFieldWarning(montantEncaisseField, "Le montant doit être supérieur à zéro");
            } else if (montantEncaisse.compareTo(montantTotal) > 0) {
                showFieldError(montantEncaisseField,
                        "Le montant encaissé ne peut pas dépasser le montant total de l'amende");
            } else {
                clearFieldError(montantEncaisseField);
                // Mettre à jour la barre de progression
                updatePaiementProgress(montantEncaisse, montantTotal);
            }

        } catch (NumberFormatException e) {
            showFieldError(montantEncaisseField, "Montant invalide");
        }
    }

    /**
     * Validation du montant encaissé
     */
    private boolean validateMontantEncaisse() {
        String montantText = montantEncaisseField.getText();

        if (montantText == null || montantText.trim().isEmpty()) {
            showFieldError(montantEncaisseField,
                    "Le montant encaissé est obligatoire (pas d'affaire sans paiement)");
            return false;
        }

        try {
            BigDecimal montantEncaisse = parseMontant(montantText);
            BigDecimal montantTotal = getTotalMontantContraventions();

            if (montantEncaisse.compareTo(BigDecimal.ZERO) <= 0) {
                showFieldError(montantEncaisseField, "Le montant doit être supérieur à zéro");
                return false;
            }

            if (montantEncaisse.compareTo(montantTotal) > 0) {
                showFieldError(montantEncaisseField,
                        "Le montant encaissé ne peut pas dépasser le montant total");
                return false;
            }

            clearFieldError(montantEncaisseField);
            return true;

        } catch (Exception e) {
            showFieldError(montantEncaisseField, "Montant invalide");
            return false;
        }
    }

    /**
     * Validation de la date d'encaissement
     */
    private boolean validateDateEncaissement() {
        LocalDate date = dateEncaissementPicker.getValue();

        if (date == null) {
            showFieldError(dateEncaissementPicker, "La date d'encaissement est obligatoire");
            return false;
        }

        if (date.isAfter(LocalDate.now())) {
            showFieldError(dateEncaissementPicker, "La date ne peut pas être dans le futur");
            return false;
        }

        // Vérifier la cohérence avec le mandat actif
        MandatService mandatService = MandatService.getInstance();
        if (!mandatService.estDansMandatActif(date)) {
            showFieldWarning(dateEncaissementPicker,
                    "Attention : Date hors du mandat actif (affaire à cheval)");
        } else {
            clearFieldError(dateEncaissementPicker);
        }

        return true;
    }

    /**
     * Validation du mode de règlement
     */
    private boolean validateModeReglement() {
        if (modeReglementComboBox.getValue() == null) {
            showFieldError(modeReglementComboBox, "Le mode de règlement est obligatoire");
            return false;
        } else {
            clearFieldError(modeReglementComboBox);
            return true;
        }
    }

    /**
     * Validation de la banque (si chèque)
     */
    private boolean validateBanque() {
        if (banqueComboBox.getValue() == null) {
            showFieldError(banqueComboBox, "La banque est obligatoire pour un paiement par chèque");
            return false;
        } else {
            clearFieldError(banqueComboBox);
            return true;
        }
    }

    /**
     * Validation du numéro de chèque
     */
    private boolean validateNumeroCheque() {
        String numero = numeroChequeField.getText();

        if (numero == null || numero.trim().isEmpty()) {
            showFieldError(numeroChequeField, "Le numéro de chèque est obligatoire");
            return false;
        } else {
            clearFieldError(numeroChequeField);
            return true;
        }
    }

    /**
     * Validation des saisissants (au moins un obligatoire)
     */
    private boolean validateSaisissants() {
        // Vérifier dans la table des agents
        long count = agents.stream()
                .filter(vm -> vm.isSelected() && "SAISISSANT".equals(vm.getRole()))
                .count();

        if (count == 0) {
            if (saisissantsErrorLabel != null) {
                saisissantsErrorLabel.setText("Au moins un saisissant est obligatoire");
                saisissantsErrorLabel.setVisible(true);
            }
            return false;
        }

        if (saisissantsErrorLabel != null) {
            saisissantsErrorLabel.setVisible(false);
        }
        return true;
    }

    // ==================== MÉTHODES DE SAUVEGARDE ====================

    /**
     * Sauvegarde l'affaire
     */
    private void saveAffaire(boolean createNew) {
        // Désactiver le formulaire pendant la sauvegarde
        setFormDisabled(true);
        if (saveProgressIndicator != null) {
            saveProgressIndicator.setVisible(true);
        }

        Task<Void> saveTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Affaire affaire = collectAffaireData();
                Encaissement encaissement = null;
                List<AffaireActeur> acteurs = collectActeurs();

                if (!isEditMode) {
                    // En création, collecter aussi l'encaissement
                    encaissement = collectEncaissementData();
                    affaireService.createAffaireWithEncaissement(affaire, encaissement, acteurs);
                } else {
                    // En édition, mise à jour simple
                    affaire.setId(currentAffaire.getId());
                    affaireService.updateAffaire(affaire);
                    // Note : Si vous avez besoin de mettre à jour les acteurs aussi,
                    // ajoutez ces lignes après :
                    // for (AffaireActeur acteur : acteurs) {
                    //     acteurService.updateActeur(acteur);
                    // }
                }

                return null;
            }
        };

        saveTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                setFormDisabled(false);
                if (saveProgressIndicator != null) {
                    saveProgressIndicator.setVisible(false);
                }

                String message = isEditMode ?
                        "L'affaire a été modifiée avec succès" :
                        "L'affaire a été créée avec succès";
                AlertUtil.showInfoAlert("Succès", message, "");

                if (createNew && !isEditMode) {
                    resetForm();
                    initializeForNew();
                } else {
                    hasUnsavedChanges = false;
                    closeWindow();
                }
            });
        });

        saveTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                setFormDisabled(false);
                if (saveProgressIndicator != null) {
                    saveProgressIndicator.setVisible(false);
                }

                Throwable error = saveTask.getException();
                logger.error("Erreur lors de la sauvegarde", error);

                String errorMessage = "Une erreur est survenue lors de l'enregistrement";
                if (error instanceof BusinessException) {
                    errorMessage = error.getMessage();
                }

                AlertUtil.showErrorAlert("Erreur", errorMessage, "");
            });
        });

        new Thread(saveTask).start();
    }

    /**
     * Collecte les données du formulaire pour créer une affaire
     */
    private Affaire collectAffaireData() {
        Affaire affaire = new Affaire();

        // Numéro et dates
        if (numeroAffaireField != null) {
            affaire.setNumeroAffaire(numeroAffaireField.getText());
        }
        if (dateCreationPicker != null) {
            affaire.setDateCreation(dateCreationPicker.getValue());
        }
        if (dateConstatationPicker != null) {
            affaire.setDateConstatation(dateConstatationPicker.getValue());
        }

        // Statut
        if (statutComboBox != null) {
            affaire.setStatut(statutComboBox.getValue());
        }

        // Contrevenant
        if (contrevenantComboBox != null) {
            affaire.setContrevenant(contrevenantComboBox.getValue());
        }

        // Localisation
        if (bureauComboBox != null) {
            affaire.setBureau(bureauComboBox.getValue());
        }
        if (serviceComboBox != null) {
            affaire.setService(serviceComboBox.getValue());
        }
        if (lieuConstatationField != null) {
            affaire.setLieuConstatation(lieuConstatationField.getText());
        }
        if (descriptionTextArea != null) {
            affaire.setDescription(descriptionTextArea.getText());
        }

        // Agent verbalisateur
        if (agentVerbalisateurComboBox != null) {
            affaire.setAgentVerbalisateur(agentVerbalisateurComboBox.getValue());
        }

        // Montant total et contraventions
        affaire.setMontantAmendeTotal(getTotalMontantContraventions());

        // Contraventions
        List<Contravention> contraventions = new ArrayList<>();
        for (ContraventionViewModel vm : contraventionsList) {
            Contravention c = new Contravention();
            c.setCode(vm.getCode());
            c.setLibelle(vm.getLibelle());
            c.setMontant(vm.getMontant());
            contraventions.add(c);
        }
        affaire.setContraventions(contraventions);

        return affaire;
    }

    /**
     * ENRICHISSEMENT : Collecte les données du premier encaissement
     */
    private Encaissement collectEncaissementData() {
        if (isEditMode) {
            return null; // Pas d'encaissement en mode édition
        }

        Encaissement encaissement = new Encaissement();

        if (montantEncaisseField != null) {
            try {
                encaissement.setMontantEncaisse(parseMontant(montantEncaisseField.getText()));
            } catch (Exception e) {
                encaissement.setMontantEncaisse(BigDecimal.ZERO);
            }
        }
        if (dateEncaissementPicker != null) {
            encaissement.setDateEncaissement(dateEncaissementPicker.getValue());
        }
        if (modeReglementComboBox != null) {
            encaissement.setModeReglement(modeReglementComboBox.getValue());
        }

        // Informations supplémentaires selon le mode
        if (modeReglementComboBox != null && modeReglementComboBox.getValue() != null) {
            if (modeReglementComboBox.getValue().isNecessiteBanque() && banqueComboBox != null) {
                if (banqueComboBox.getValue() != null) {
                    encaissement.setBanque(banqueComboBox.getValue().getNomBanque());
                }
            }
            if (modeReglementComboBox.getValue().isNecessiteReference() && numeroChequeField != null) {
                encaissement.setNumeroPiece(numeroChequeField.getText());
            }
        }

        return encaissement;
    }

    /**
     * Collecte les acteurs sélectionnés
     */
    private List<AffaireActeur> collectActeurs() {
        List<AffaireActeur> acteurs = new ArrayList<>();

        if (agents != null) {
            for (AgentViewModel vm : agents) {
                if (vm.isSelected()) {
                    AffaireActeur acteur = new AffaireActeur();
                    acteur.setAgentId(vm.getAgent().getId());
                    acteur.setRoleSurAffaire(vm.getRole());
                    acteurs.add(acteur);
                }
            }
        }

        // Si aucun acteur n'est sélectionné, ajouter l'agent verbalisateur comme saisissant
        if (acteurs.isEmpty() && agentVerbalisateurComboBox != null && agentVerbalisateurComboBox.getValue() != null) {
            AffaireActeur acteur = new AffaireActeur();
            acteur.setAgentId(agentVerbalisateurComboBox.getValue().getId());
            acteur.setRoleSurAffaire("SAISISSANT");
            acteurs.add(acteur);
        }

        return acteurs;
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Met à jour l'affichage des détails du contrevenant
     */
    private void displayContrevenantDetails(Contrevenant contrevenant) {
        if (contrevenantDetailsBox != null) {
            contrevenantDetailsBox.setVisible(true);
        }

        if (typeContrevenantLabel != null) {
            typeContrevenantLabel.setText(contrevenant.getTypePersonne() != null ?
                    contrevenant.getTypePersonne().getLibelle() : "");
        }
        if (nomContrevenantLabel != null) {
            nomContrevenantLabel.setText(contrevenant.getNom() + " " + contrevenant.getPrenom());
        }
        if (adresseContrevenantLabel != null) {
            adresseContrevenantLabel.setText(contrevenant.getAdresse() != null ?
                    contrevenant.getAdresse() : "Non renseignée");
        }
        if (telephoneContrevenantLabel != null) {
            telephoneContrevenantLabel.setText(contrevenant.getTelephone() != null ?
                    contrevenant.getTelephone() : "Non renseigné");
        }
        if (emailContrevenantLabel != null) {
            emailContrevenantLabel.setText(contrevenant.getEmail() != null ?
                    contrevenant.getEmail() : "Non renseigné");
        }
    }

    /**
     * Masque les détails du contrevenant
     */
    private void hideContrevenantDetails() {
        if (contrevenantDetailsBox != null) {
            contrevenantDetailsBox.setVisible(false);
        }
    }

    /**
     * Met à jour les statistiques (montant total, nombre de contraventions)
     */
    private void updateStatistics() {
        // Montant total
        BigDecimal total = getTotalMontantContraventions();
        if (montantTotalLabel != null) {
            montantTotalLabel.setText(CurrencyFormatter.format(total) + " FCFA");
        }

        if (montantEnLettresLabel != null) {
            montantEnLettresLabel.setText(NumberToWords.convert(total));
        }

        // Nombre de contraventions
        if (nombreContraventionsLabel != null) {
            nombreContraventionsLabel.setText(String.valueOf(contraventionsList.size()));
        }

        // Mettre à jour le montant restant si un encaissement est saisi
        updateMontantRestant();
    }

    public class ValidationUtil {
        public static void applyNumericValidation(TextField field) {
            field.setTextFormatter(new TextFormatter<>(change -> {
                String newText = change.getControlNewText();
                if (newText.matches("\\d*\\.?\\d*")) {
                    return change;
                }
                return null;
            }));
        }
    }

    /**
     * Met à jour l'affichage du montant restant
     */
    private void updateMontantRestant() {
        if (isEditMode || montantEncaisseField == null || montantRestantLabel == null) return;

        try {
            BigDecimal montantTotal = getTotalMontantContraventions();
            BigDecimal montantEncaisse = parseMontant(montantEncaisseField.getText());
            BigDecimal montantRestant = montantTotal.subtract(montantEncaisse);

            if (montantRestant.compareTo(BigDecimal.ZERO) < 0) {
                montantRestant = BigDecimal.ZERO;
            }

            montantRestantLabel.setText("Restant : " + CurrencyFormatter.format(montantRestant) + " FCFA");

            // Mettre à jour la barre de progression
            updatePaiementProgress(montantEncaisse, montantTotal);

        } catch (Exception e) {
            montantRestantLabel.setText("Restant : " + CurrencyFormatter.format(getTotalMontantContraventions()) + " FCFA");
        }
    }

    /**
     * Met à jour la barre de progression du paiement
     */
    private void updatePaiementProgress(BigDecimal montantEncaisse, BigDecimal montantTotal) {
        if (paiementProgressBar == null || montantTotal.compareTo(BigDecimal.ZERO) == 0) return;

        double progress = montantEncaisse.doubleValue() / montantTotal.doubleValue();
        paiementProgressBar.setProgress(progress);

        // Changer la couleur selon le pourcentage
        if (progress >= 1.0) {
            paiementProgressBar.setStyle("-fx-accent: green;");
        } else if (progress >= 0.5) {
            paiementProgressBar.setStyle("-fx-accent: orange;");
        } else {
            paiementProgressBar.setStyle("-fx-accent: red;");
        }
    }

    /**
     * Met à jour l'affichage de la section chèque selon le mode de règlement
     */
    private void updateChequeSection(ModeReglement mode) {
        if (detailsChequeSection == null) return;

        boolean showCheque = (mode != null && mode.isNecessiteBanque());
        detailsChequeSection.setVisible(showCheque);
        detailsChequeSection.setManaged(showCheque);

        if (!showCheque) {
            // Réinitialiser les champs chèque
            if (banqueComboBox != null) banqueComboBox.setValue(null);
            if (numeroChequeField != null) numeroChequeField.clear();
        }
    }

    /**
     * Met à jour le champ indicateur selon la checkbox
     */
    private void updateIndicateurField() {
        if (indicateurExisteCheckBox != null && nomIndicateurField != null) {
            boolean hasIndicateur = indicateurExisteCheckBox.isSelected();
            nomIndicateurField.setDisable(!hasIndicateur);
            if (!hasIndicateur) {
                nomIndicateurField.clear();
            }
        }
    }

    /**
     * Met à jour la sélection des agents
     */
    private void updateAgentsSelection(List<Agent> saisissants, List<Agent> chefs) {
        // Réinitialiser toutes les sélections
        for (AgentViewModel vm : agents) {
            vm.setSelected(false);
            vm.setRole(null);
        }

        // Marquer les saisissants
        for (Agent saisissant : saisissants) {
            agents.stream()
                    .filter(vm -> vm.getAgent().getId().equals(saisissant.getId()))
                    .findFirst()
                    .ifPresent(vm -> {
                        vm.setSelected(true);
                        vm.setRole("SAISISSANT");
                    });
        }

        // Marquer les chefs
        for (Agent chef : chefs) {
            agents.stream()
                    .filter(vm -> vm.getAgent().getId().equals(chef.getId()))
                    .findFirst()
                    .ifPresent(vm -> {
                        vm.setSelected(true);
                        vm.setRole("CHEF");
                    });
        }

        // Rafraîchir l'affichage
        if (agentsTableView != null) {
            agentsTableView.refresh();
        }

        // Mettre à jour les compteurs
        updateAgentCounters();
    }

    /**
     * Met à jour les compteurs d'agents
     */
    private void updateAgentCounters() {
        long saisissantsCount = agents.stream()
                .filter(vm -> vm.isSelected() && "SAISISSANT".equals(vm.getRole()))
                .count();
        long chefsCount = agents.stream()
                .filter(vm -> vm.isSelected() && "CHEF".equals(vm.getRole()))
                .count();

        if (nombreSaisissantsLabel != null) {
            nombreSaisissantsLabel.setText(String.valueOf(saisissantsCount));
        }
        if (nombreChefsLabel != null) {
            nombreChefsLabel.setText(String.valueOf(chefsCount));
        }
    }

    /**
     * Calcule le montant total des contraventions
     */
    private BigDecimal getTotalMontantContraventions() {
        return contraventionsList.stream()
                .map(ContraventionViewModel::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Parse un montant depuis une chaîne
     */
    private BigDecimal parseMontant(String montantText) {
        if (montantText == null || montantText.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        // Enlever les espaces et remplacer les virgules par des points
        String cleanedText = montantText.trim()
                .replaceAll("\\s", "")
                .replace(",", ".");
        return new BigDecimal(cleanedText);
    }

    /**
     * Active/désactive le formulaire
     */
    private void setFormDisabled(boolean disabled) {
        // Désactiver tous les champs principaux
        if (contrevenantComboBox != null) contrevenantComboBox.setDisable(disabled);
        if (contraventionComboBox != null) contraventionComboBox.setDisable(disabled);
        if (bureauComboBox != null) bureauComboBox.setDisable(disabled);
        if (serviceComboBox != null) serviceComboBox.setDisable(disabled);
        if (montantEncaisseField != null) montantEncaisseField.setDisable(disabled);
        if (modeReglementComboBox != null) modeReglementComboBox.setDisable(disabled);

        // Désactiver les boutons
        if (enregistrerButton != null) enregistrerButton.setDisable(disabled);
        if (enregistrerEtNouveauButton != null) enregistrerEtNouveauButton.setDisable(disabled);
        if (annulerButton != null) annulerButton.setDisable(disabled);
        if (addContraventionButton != null) addContraventionButton.setDisable(disabled);
        if (assignerAgentsButton != null) assignerAgentsButton.setDisable(disabled);
        if (newContrevenantButton != null) newContrevenantButton.setDisable(disabled);

        // Changer le curseur
        if (currentStage != null && currentStage.getScene() != null) {
            currentStage.getScene().setCursor(disabled ? Cursor.WAIT : Cursor.DEFAULT);
        }
    }

    /**
     * Réinitialise le formulaire
     */
    private void resetForm() {
        // Réinitialiser les champs
        if (contrevenantComboBox != null) contrevenantComboBox.setValue(null);
        if (contraventionComboBox != null) contraventionComboBox.setValue(null);
        if (autreContraventionField != null) autreContraventionField.clear();
        if (montantAmendeField != null) montantAmendeField.clear();
        if (bureauComboBox != null) bureauComboBox.setValue(null);
        if (serviceComboBox != null) serviceComboBox.setValue(null);
        if (centreComboBox != null) centreComboBox.setValue(null);
        if (lieuConstatationField != null) lieuConstatationField.clear();
        if (descriptionTextArea != null) descriptionTextArea.clear();
        if (agentVerbalisateurComboBox != null) agentVerbalisateurComboBox.setValue(null);
        if (montantEncaisseField != null) montantEncaisseField.clear();
        if (modeReglementComboBox != null) modeReglementComboBox.setValue(ModeReglement.ESPECES);
        if (banqueComboBox != null) banqueComboBox.setValue(null);
        if (numeroChequeField != null) numeroChequeField.clear();
        if (indicateurExisteCheckBox != null) indicateurExisteCheckBox.setSelected(false);
        if (nomIndicateurField != null) nomIndicateurField.clear();

        // Réinitialiser les listes
        contraventionsList.clear();
        for (AgentViewModel vm : agents) {
            vm.setSelected(false);
            vm.setRole(null);
        }

        // Réinitialiser les dates
        if (dateCreationPicker != null) dateCreationPicker.setValue(LocalDate.now());
        if (dateConstatationPicker != null) dateConstatationPicker.setValue(LocalDate.now());
        if (dateEncaissementPicker != null) dateEncaissementPicker.setValue(LocalDate.now());

        // Mettre à jour l'affichage
        updateStatistics();
        updateAgentCounters();
        hideContrevenantDetails();
        clearAllErrors();

        hasUnsavedChanges = false;
    }

    /**
     * Ferme la fenêtre
     */
    private void closeWindow() {
        if (currentStage == null && annulerButton != null) {
            currentStage = (Stage) annulerButton.getScene().getWindow();
        }
        if (currentStage != null) {
            currentStage.close();
        }
    }

    /**
     * Définit le stage actuel
     */
    public void setStage(Stage stage) {
        this.currentStage = stage;
    }

    // ==================== GESTION DES ERREURS ====================

    /**
     * Affiche une erreur sur un champ
     */
    private void showFieldError(Control field, String message) {
        if (field == null) return;

        field.setStyle("-fx-border-color: red;");

        // Créer ou récupérer le label d'erreur
        Label errorLabel = (Label) field.getProperties().get("errorLabel");
        if (errorLabel == null) {
            errorLabel = new Label();
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 11px;");
            field.getProperties().put("errorLabel", errorLabel);

            // Ajouter le label au parent du champ
            if (field.getParent() instanceof VBox) {
                VBox parent = (VBox) field.getParent();
                int index = parent.getChildren().indexOf(field);
                if (index >= 0 && index < parent.getChildren().size() - 1) {
                    parent.getChildren().add(index + 1, errorLabel);
                }
            }
        }

        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    /**
     * Affiche un avertissement sur un champ
     */
    private void showFieldWarning(Control field, String message) {
        if (field == null) return;

        field.setStyle("-fx-border-color: orange;");

        Label warningLabel = (Label) field.getProperties().get("warningLabel");
        if (warningLabel == null) {
            warningLabel = new Label();
            warningLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 11px;");
            field.getProperties().put("warningLabel", warningLabel);

            if (field.getParent() instanceof VBox) {
                VBox parent = (VBox) field.getParent();
                int index = parent.getChildren().indexOf(field);
                if (index >= 0 && index < parent.getChildren().size() - 1) {
                    parent.getChildren().add(index + 1, warningLabel);
                }
            }
        }

        warningLabel.setText(message);
        warningLabel.setVisible(true);
    }

    /**
     * Efface l'erreur d'un champ
     */
    private void clearFieldError(Control field) {
        if (field == null) return;

        field.setStyle("");

        Label errorLabel = (Label) field.getProperties().get("errorLabel");
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }

        Label warningLabel = (Label) field.getProperties().get("warningLabel");
        if (warningLabel != null) {
            warningLabel.setVisible(false);
        }
    }

    /**
     * Efface toutes les erreurs
     */
    private void clearAllErrors() {
        // Effacer les erreurs des champs principaux
        clearFieldError(contrevenantComboBox);
        clearFieldError(bureauComboBox);
        clearFieldError(serviceComboBox);
        clearFieldError(montantEncaisseField);
        clearFieldError(dateEncaissementPicker);
        clearFieldError(modeReglementComboBox);
        clearFieldError(banqueComboBox);
        clearFieldError(numeroChequeField);

        // Réinitialiser les styles des tableaux
        if (contraventionsTableView != null) {
            contraventionsTableView.setStyle("");
        }
        if (agentsTableView != null) {
            agentsTableView.setStyle("");
        }

        // Masquer les labels d'erreur
        if (saisissantsErrorLabel != null) {
            saisissantsErrorLabel.setVisible(false);
        }

        // Effacer le message de statut
        if (statusLabel != null) {
            statusLabel.setText("");
            statusLabel.setStyle("");
        }
    }

    /**
     * Affiche une erreur générale
     */
    private void showError(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: red;");
        } else {
            AlertUtil.showErrorAlert("Erreur", message, "");
        }
    }

    // ==================== CLASSES INTERNES ====================

    /**
     * View Model pour les contraventions dans le tableau
     */
    public static class ContraventionViewModel {
        private String code;
        private String libelle;
        private BigDecimal montant;

        // Getters et setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getLibelle() { return libelle; }
        public void setLibelle(String libelle) { this.libelle = libelle; }

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }
    }

    /**
     * View Model pour les agents dans le tableau
     */
    public static class AgentViewModel {
        private final Agent agent;
        private boolean selected;
        private String role;

        public AgentViewModel(Agent agent) {
            this.agent = agent;
            this.selected = false;
            this.role = null;
        }

        // Getters et setters
        public Agent getAgent() { return agent; }

        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        // Propriétés déléguées
        public String getCode() { return agent.getCodeAgent(); }
        public String getNom() { return agent.getNom() + " " + agent.getPrenom(); }
        public String getGrade() { return agent.getGrade(); }
        public String getService() {
            return agent.getService() != null ? agent.getService().getNomService() : "";
        }
    }

    /**
     * Contrôleur pour le dialogue de sélection des agents
     * (Interface attendue par handleAssignerAgents)
     */
    public static class AgentSelectionDialogController {
        private List<Agent> selectedSaisissants = new ArrayList<>();
        private List<Agent> selectedChefs = new ArrayList<>();
        private boolean confirmed = false;

        public void setSelectedAgents(List<Agent> saisissants, List<Agent> chefs) {
            this.selectedSaisissants = new ArrayList<>(saisissants);
            this.selectedChefs = new ArrayList<>(chefs);
        }

        public List<Agent> getSelectedSaisissants() {
            return selectedSaisissants;
        }

        public List<Agent> getSelectedChefs() {
            return selectedChefs;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }
    }
}