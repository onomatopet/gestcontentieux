package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.service.*;
import com.regulation.contentieux.util.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

/**
 * Contrôleur pour le dialogue unifié de création d'affaire avec encaissement obligatoire
 * Implémente le workflow complet en une seule interface
 */
public class AffaireEncaissementController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AffaireEncaissementController.class);

    // === SECTION AFFAIRE ===
    @FXML private TextField numeroAffaireField;
    @FXML private DatePicker dateCreationPicker;
    @FXML private Label mandatActifLabel;

    // Contrevenant
    @FXML private ComboBox<Contrevenant> contrevenantCombo;
    @FXML private Button newContrevenantBtn;
    @FXML private TextField nomContrevenantField;
    @FXML private TextField adresseField;
    @FXML private TextField telephoneField;

    // Infraction
    @FXML private ComboBox<Contravention> contraventionCombo;
    @FXML private TextField contraventionLibreField;
    @FXML private TextField montantAmendeField;
    @FXML private ComboBox<Centre> centreCombo;
    @FXML private ComboBox<Service> serviceCombo;
    @FXML private ComboBox<Bureau> bureauCombo;

    // Acteurs
    @FXML private ListView<Agent> agentsList;
    @FXML private ListView<Agent> selectedAgentsList;
    @FXML private ListView<Agent> chefsList;
    @FXML private ListView<Agent> selectedChefsList;
    @FXML private CheckBox indicateurCheck;
    @FXML private TextField nomIndicateurField;
    @FXML private ComboBox<Agent> indicateurAgentCombo;

    // === SECTION ENCAISSEMENT ===
    @FXML private TextField numeroEncaissementField;
    @FXML private DatePicker dateEncaissementPicker;
    @FXML private TextField montantEncaisseField;
    @FXML private Label soldeRestantLabel;
    @FXML private ProgressBar paiementProgress;

    // Mode de règlement
    @FXML private ComboBox<ModeReglement> modeReglementCombo;
    @FXML private VBox chequeSection;
    @FXML private ComboBox<Banque> banqueCombo;
    @FXML private TextField numeroChequeField;

    // Boutons
    @FXML private Button validerBtn;
    @FXML private Button annulerBtn;
    @FXML private Label statusLabel;

    // Services
    private final AffaireService affaireService = new AffaireService();
    private final EncaissementService encaissementService = new EncaissementService();
    private final ContrevenantService contrevenantService = new ContrevenantService();
    private final AgentService agentService = new AgentService();
    private final ContraventionService contraventionService = new ContraventionService();
    private final CentreService centreService = new CentreService();
    private final ServiceService serviceService = new ServiceService();
    private final BureauService bureauService = new BureauService();
    private final BanqueService banqueService = new BanqueService();
    private final MandatService mandatService = MandatService.getInstance();

    // Listes observables
    private final ObservableList<Agent> availableAgents = FXCollections.observableArrayList();
    private final ObservableList<Agent> selectedAgents = FXCollections.observableArrayList();
    private final ObservableList<Agent> availableChefs = FXCollections.observableArrayList();
    private final ObservableList<Agent> selectedChefs = FXCollections.observableArrayList();

    // État
    private Affaire createdAffaire;
    private boolean isCreating = false;
    private Stage dialogStage;

    /**
     * Initialise et affiche le dialogue
     */
    public void showDialog(Stage owner) {
        try {
            dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.setTitle("Nouvelle Affaire Contentieuse");
            dialogStage.setResizable(false);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/affaire-encaissement-dialog.fxml"));
            loader.setController(this);

            BorderPane root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            dialogStage.setScene(scene);
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Erreur lors du chargement du formulaire", e);
            AlertUtil.showError("Erreur", "Impossible de charger le formulaire");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisation du dialogue unifié affaire/encaissement");

        setupValidation();
        setupComboBoxes();
        setupAgentsLists();
        setupEventHandlers();
        loadInitialData();

        // Valeurs par défaut
        dateCreationPicker.setValue(LocalDate.now());
        dateEncaissementPicker.setValue(LocalDate.now());

        // Afficher le mandat actif
        Mandat mandatActif = mandatService.getMandatActif();
        if (mandatActif != null) {
            mandatActifLabel.setText(mandatActif.getNumeroMandat());
        } else {
            mandatActifLabel.setText("AUCUN MANDAT ACTIF");
            mandatActifLabel.setStyle("-fx-text-fill: red;");
            validerBtn.setDisable(true);

            AlertUtil.showError("Erreur", "Aucun mandat actif",
                    "Vous devez activer un mandat avant de créer des affaires.");
        }
    }

    /**
     * Configuration de la validation temps réel
     */
    private void setupValidation() {
        // Validation numérique pour les montants
        montantAmendeField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) {
                montantAmendeField.setText(old);
            }
            updateSoldeRestant();
        });

        montantEncaisseField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) {
                montantEncaisseField.setText(old);
            }
            updateSoldeRestant();
            validateEncaissement();
        });

        // Mode de règlement
        modeReglementCombo.valueProperty().addListener((obs, old, val) -> {
            chequeSection.setVisible(val == ModeReglement.CHEQUE);
            chequeSection.setManaged(val == ModeReglement.CHEQUE);
        });

        // Indicateur
        indicateurCheck.selectedProperty().addListener((obs, old, val) -> {
            nomIndicateurField.setDisable(!val);
            indicateurAgentCombo.setDisable(!val);
        });
    }

    /**
     * Configuration des ComboBox
     */
    private void setupComboBoxes() {
        // Contrevenant
        contrevenantCombo.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant c) {
                return c != null ? c.getNomComplet() : "";
            }
            @Override
            public Contrevenant fromString(String s) { return null; }
        });

        // Contravention
        contraventionCombo.setConverter(new StringConverter<Contravention>() {
            @Override
            public String toString(Contravention c) {
                return c != null ? c.getLibelle() : "";
            }
            @Override
            public Contravention fromString(String s) { return null; }
        });

        // Centre
        centreCombo.setConverter(new StringConverter<Centre>() {
            @Override
            public String toString(Centre c) {
                return c != null ? c.getCode() + " - " + c.getLibelle() : "";
            }
            @Override
            public Centre fromString(String s) { return null; }
        });

        // Mode de règlement
        modeReglementCombo.setItems(FXCollections.observableArrayList(ModeReglement.values()));
        modeReglementCombo.setValue(ModeReglement.ESPECES);
    }

    /**
     * Configuration des listes d'agents
     */
    private void setupAgentsLists() {
        // Configuration des listes de sélection multiple
        agentsList.setItems(availableAgents);
        selectedAgentsList.setItems(selectedAgents);
        chefsList.setItems(availableChefs);
        selectedChefsList.setItems(selectedChefs);

        // Mode de sélection multiple
        agentsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        chefsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Affichage personnalisé
        setCellFactory(agentsList);
        setCellFactory(selectedAgentsList);
        setCellFactory(chefsList);
        setCellFactory(selectedChefsList);
    }

    private void setCellFactory(ListView<Agent> listView) {
        listView.setCellFactory(lv -> new ListCell<Agent>() {
            @Override
            protected void updateItem(Agent agent, boolean empty) {
                super.updateItem(agent, empty);
                setText(empty || agent == null ? null :
                        agent.getCode() + " - " + agent.getNomComplet());
            }
        });
    }

    /**
     * Configuration des gestionnaires d'événements
     */
    private void setupEventHandlers() {
        // Sélection contrevenant
        contrevenantCombo.setOnAction(e -> {
            Contrevenant selected = contrevenantCombo.getValue();
            if (selected != null) {
                nomContrevenantField.setText(selected.getNomComplet());
                adresseField.setText(selected.getAdresse());
                telephoneField.setText(selected.getTelephone());
            }
        });

        // Centre -> Services
        centreCombo.setOnAction(e -> {
            Centre centre = centreCombo.getValue();
            if (centre != null) {
                loadServices(centre.getId());
            }
        });

        // Service -> Bureaux
        serviceCombo.setOnAction(e -> {
            Service service = serviceCombo.getValue();
            if (service != null) {
                loadBureaux(service.getId());
            }
        });

        // Boutons de sélection d'agents
        setupAgentSelectionButtons();

        // Boutons principaux
        validerBtn.setOnAction(e -> validerCreation());
        annulerBtn.setOnAction(e -> closeDialog());
        newContrevenantBtn.setOnAction(e -> createNewContrevenant());
    }

    /**
     * Ferme le dialogue
     */
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Mise à jour du solde restant et de la barre de progression
     */
    private void updateSoldeRestant() {
        try {
            BigDecimal amende = new BigDecimal(montantAmendeField.getText().trim());
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText().trim());
            BigDecimal solde = amende.subtract(encaisse);

            soldeRestantLabel.setText(CurrencyFormatter.format(solde));

            // Barre de progression
            double progress = encaisse.doubleValue() / amende.doubleValue();
            paiementProgress.setProgress(Math.min(progress, 1.0));

            // Style selon le statut
            if (progress >= 1.0) {
                paiementProgress.setStyle("-fx-accent: green;");
                soldeRestantLabel.setStyle("-fx-text-fill: green;");
            } else if (progress >= 0.5) {
                paiementProgress.setStyle("-fx-accent: orange;");
                soldeRestantLabel.setStyle("-fx-text-fill: orange;");
            } else {
                paiementProgress.setStyle("-fx-accent: red;");
                soldeRestantLabel.setStyle("-fx-text-fill: red;");
            }

        } catch (NumberFormatException e) {
            soldeRestantLabel.setText("0 FCFA");
            paiementProgress.setProgress(0);
        }
    }

    /**
     * Validation du montant encaissé
     */
    private void validateEncaissement() {
        try {
            BigDecimal amende = new BigDecimal(montantAmendeField.getText().trim());
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText().trim());

            if (encaisse.compareTo(amende) > 0) {
                montantEncaisseField.setStyle("-fx-border-color: red;");
                statusLabel.setText("⚠️ Le montant encaissé ne peut pas dépasser l'amende");
                statusLabel.setStyle("-fx-text-fill: red;");
            } else {
                montantEncaisseField.setStyle("");
                statusLabel.setText("");
            }

        } catch (NumberFormatException e) {
            // Ignorer
        }
    }

    /**
     * Chargement des données initiales
     */
    private void loadInitialData() {
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Charger toutes les données nécessaires
                List<Contrevenant> contrevenants = contrevenantService.getAllContrevenants();
                List<Contravention> contraventions = contraventionService.getAllContraventions();
                List<Centre> centres = centreService.getAllCentres();
                List<Banque> banques = banqueService.getAllBanques();
                List<Agent> agents = agentService.getAllAgents();

                Platform.runLater(() -> {
                    contrevenantCombo.setItems(FXCollections.observableArrayList(contrevenants));
                    contraventionCombo.setItems(FXCollections.observableArrayList(contraventions));
                    centreCombo.setItems(FXCollections.observableArrayList(centres));
                    banqueCombo.setItems(FXCollections.observableArrayList(banques));

                    // Séparer agents et chefs
                    availableAgents.setAll(agents.stream()
                            .filter(a -> !a.isChef())
                            .toList());

                    availableChefs.setAll(agents.stream()
                            .filter(Agent::isChef)
                            .toList());

                    indicateurAgentCombo.setItems(FXCollections.observableArrayList(agents));
                });

                // Générer les numéros
                String numeroAffaire = affaireService.genererNumeroAffaire();
                String numeroEncaissement = encaissementService.generateNextNumeroEncaissement();

                Platform.runLater(() -> {
                    numeroAffaireField.setText(numeroAffaire);
                    numeroEncaissementField.setText(numeroEncaissement);
                });

                return null;
            }
        };

        loadTask.setOnFailed(e -> {
            logger.error("Erreur lors du chargement des données", loadTask.getException());
            AlertUtil.showError("Erreur", "Impossible de charger les données");
        });

        new Thread(loadTask).start();
    }

    /**
     * Validation et création de l'affaire avec encaissement
     */
    private void validerCreation() {
        if (isCreating) return;

        // Validation complète
        List<String> errors = validateForm();
        if (!errors.isEmpty()) {
            AlertUtil.showError("Erreurs de validation", String.join("\n", errors));
            return;
        }

        isCreating = true;
        validerBtn.setDisable(true);
        statusLabel.setText("Création en cours...");

        Task<Affaire> createTask = new Task<>() {
            @Override
            protected Affaire call() throws Exception {
                // Créer l'affaire
                Affaire affaire = buildAffaire();

                // Créer l'encaissement
                Encaissement encaissement = buildEncaissement();

                // Créer les acteurs
                List<AffaireActeur> acteurs = buildActeurs();

                // Appeler le service pour créer le tout
                return affaireService.createAffaireAvecEncaissement(affaire, encaissement, acteurs);
            }
        };

        createTask.setOnSucceeded(e -> {
            createdAffaire = createTask.getValue();
            Platform.runLater(() -> {
                AlertUtil.showSuccess("Succès",
                        String.format("Affaire %s créée avec succès\nEncaissement de %s FCFA enregistré",
                                createdAffaire.getNumeroAffaire(),
                                montantEncaisseField.getText()));
                closeDialog();
            });
        });

        createTask.setOnFailed(e -> {
            isCreating = false;
            validerBtn.setDisable(false);
            statusLabel.setText("");

            Throwable error = createTask.getException();
            logger.error("Erreur lors de la création", error);
            AlertUtil.showError("Erreur", "Impossible de créer l'affaire", error.getMessage());
        });

        new Thread(createTask).start();
    }

    /**
     * Validation du formulaire
     */
    private List<String> validateForm() {
        List<String> errors = new ArrayList<>();

        // Contrevenant
        if (contrevenantCombo.getValue() == null && nomContrevenantField.getText().isEmpty()) {
            errors.add("- Sélectionnez ou créez un contrevenant");
        }

        // Infraction
        if (contraventionCombo.getValue() == null && contraventionLibreField.getText().isEmpty()) {
            errors.add("- Sélectionnez ou saisissez une contravention");
        }

        // Montants
        try {
            BigDecimal amende = new BigDecimal(montantAmendeField.getText());
            if (amende.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("- Le montant de l'amende doit être positif");
            }
        } catch (Exception e) {
            errors.add("- Montant de l'amende invalide");
        }

        try {
            BigDecimal encaisse = new BigDecimal(montantEncaisseField.getText());
            if (encaisse.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("- Le montant encaissé doit être positif");
            }
        } catch (Exception e) {
            errors.add("- Montant encaissé invalide");
        }

        // Centre
        if (centreCombo.getValue() == null) {
            errors.add("- Sélectionnez un centre");
        }

        // Acteurs
        if (selectedAgents.isEmpty()) {
            errors.add("- Sélectionnez au moins un agent saisissant");
        }

        // Mode chèque
        if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
            if (banqueCombo.getValue() == null) {
                errors.add("- Sélectionnez une banque pour le chèque");
            }
            if (numeroChequeField.getText().isEmpty()) {
                errors.add("- Saisissez le numéro de chèque");
            }
        }

        return errors;
    }

    // Méthodes de construction des objets...
    // [buildAffaire(), buildEncaissement(), buildActeurs()]

    // Méthodes utilitaires...
    // [loadServices(), loadBureaux(), createNewContrevenant(), etc.]

    /**
     * Retourne l'affaire créée
     */
    public Affaire getCreatedAffaire() {
        return createdAffaire;
    }
}