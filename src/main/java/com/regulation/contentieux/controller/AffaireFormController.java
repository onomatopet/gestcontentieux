package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.model.*;
import com.regulation.contentieux.model.enums.*;
import com.regulation.contentieux.service.*;
import com.regulation.contentieux.util.*;
import com.regulation.contentieux.exception.BusinessException;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
    @FXML private TableColumn<ContraventionViewModel, Void> actionsContraventionColumn;

    // Section Détails
    @FXML private DatePicker dateConstatationPicker;
    @FXML private TextField lieuConstatationField;
    @FXML private ComboBox<Agent> agentVerbalisateurComboBox;
    @FXML private TextArea descriptionTextArea;
    @FXML private TextArea observationsTextArea;

    // Section Bureau/Service
    @FXML private ComboBox<Bureau> bureauComboBox;
    @FXML private ComboBox<Service> serviceComboBox;
    @FXML private ComboBox<Centre> centreComboBox;

    // Section Acteurs
    @FXML private TableView<AgentViewModel> agentsTableView;
    @FXML private TableColumn<AgentViewModel, Boolean> selectColumn;
    @FXML private TableColumn<AgentViewModel, String> matriculeColumn;
    @FXML private TableColumn<AgentViewModel, String> nomColumn;
    @FXML private TableColumn<AgentViewModel, String> roleColumn;
    @FXML private TextField searchAgentField;
    @FXML private Button searchAgentButton;
    @FXML private CheckBox indicateurExisteCheckBox;
    @FXML private TextField nomIndicateurField;

    // ENRICHISSEMENT : Section Premier Encaissement (OBLIGATOIRE)
    @FXML private VBox premierEncaissementSection;
    @FXML private Label encaissementTitleLabel;
    @FXML private TextField montantEncaisseField;
    @FXML private Label soldeRestantLabel;
    @FXML private DatePicker dateEncaissementPicker;
    @FXML private ComboBox<ModeReglement> modeReglementComboBox;
    @FXML private VBox infosReglementBox;
    @FXML private ComboBox<Banque> banqueComboBox;
    @FXML private TextField numeroChequeField;
    @FXML private Label montantEncaisseEnLettresLabel;

    // Section Observations
    @FXML private TextArea observationsArea;

    // Boutons d'action
    @FXML private Button cancelButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;
    @FXML private Button enregistrerButton;
    @FXML private Button enregistrerEtNouveauButton;
    @FXML private Button annulerButton;

    // Indicateurs de progression
    @FXML private ProgressIndicator saveProgressIndicator;
    @FXML private Label statusLabel;

    // ==================== SERVICES ET DONNÉES ====================

    private AffaireService affaireService;
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
        this.affaireService = new AffaireService();
        this.contrevenantDAO = new ContrevenantDAO();
        this.contraventionDAO = new ContraventionDAO();
        this.agentDAO = new AgentDAO();
        this.bureauDAO = new BureauDAO();
        this.serviceDAO = new ServiceDAO();
        this.centreDAO = new CentreDAO();
        this.banqueDAO = new BanqueDAO();
        this.validationService = new ValidationService();
        this.authService = AuthenticationService.getInstance();
    }

    private void initializeCollections() {
        this.agents = FXCollections.observableArrayList();
        this.contraventionsList = FXCollections.observableArrayList();
    }

    private void setupUI() {
        // Configuration des ComboBox
        setupContrevenantComboBox();
        setupContraventionComboBox();
        setupBureauServiceComboBoxes();
        setupAgentComboBox();
        setupAgentsTable();
        setupContraventionsTable();

        // ENRICHISSEMENT : Configuration du mode de règlement
        setupModeReglementComboBox();

        // Formatage des montants
        if (montantAmendeField != null) {
            CurrencyFormatter.setupCurrencyField(montantAmendeField);
            montantAmendeField.textProperty().addListener((obs, old, newVal) -> {
                updateMontantEnLettres(newVal, montantEnLettresLabel);
                updateSoldeRestant();
            });
        }

        if (montantEncaisseField != null) {
            CurrencyFormatter.setupCurrencyField(montantEncaisseField);
            montantEncaisseField.textProperty().addListener((obs, old, newVal) -> {
                updateMontantEnLettres(newVal, montantEncaisseEnLettresLabel);
                updateSoldeRestant();
            });
        }

        // Section indicateur
        if (nomIndicateurField != null && indicateurExisteCheckBox != null) {
            nomIndicateurField.disableProperty().bind(indicateurExisteCheckBox.selectedProperty().not());
        }
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

        contrevenantComboBox.setConverter(new StringConverter<Contrevenant>() {
            @Override
            public String toString(Contrevenant contrevenant) {
                return contrevenant != null ? contrevenant.getDisplayName() : "";
            }

            @Override
            public Contrevenant fromString(String string) {
                return null;
            }
        });

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

    private void setupAgentsTable() {
        // Configuration du tableau des agents si nécessaire
    }

    private void setupContraventionsTable() {
        if (contraventionsTableView == null) return;

        // Configuration des colonnes
        if (codeContraventionColumn != null) {
            codeContraventionColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getContravention().getCode()));
        }

        if (libelleContraventionColumn != null) {
            libelleContraventionColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getContravention().getLibelle()));
        }

        if (montantContraventionColumn != null) {
            montantContraventionColumn.setCellValueFactory(cellData ->
                    new SimpleStringProperty(
                            CurrencyFormatter.format(cellData.getValue().getContravention().getMontant())));
        }

        // Colonne d'actions
        if (actionsContraventionColumn != null) {
            actionsContraventionColumn.setCellFactory(param -> new TableCell<ContraventionViewModel, Void>() {
                private final Button removeButton = new Button("Retirer");

                {
                    removeButton.getStyleClass().add("button-danger-small");
                    removeButton.setOnAction(event -> {
                        ContraventionViewModel item = getTableView().getItems().get(getIndex());
                        removeContravention(item);
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

        // Gérer l'affichage des champs supplémentaires selon le mode
        modeReglementComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateModeReglementFields(newVal);
        });

        // Mode par défaut : Espèces
        modeReglementComboBox.setValue(ModeReglement.ESPECES);
    }

    /**
     * Met à jour les champs selon le mode de règlement
     */
    private void updateModeReglementFields(ModeReglement mode) {
        if (infosReglementBox == null) return;

        if (mode == null) {
            infosReglementBox.setVisible(false);
            infosReglementBox.setManaged(false);
            return;
        }

        boolean needsBanque = mode.isNecessiteBanque();
        boolean needsReference = mode.isNecessiteReference();

        infosReglementBox.setVisible(needsBanque || needsReference);
        infosReglementBox.setManaged(needsBanque || needsReference);

        if (banqueComboBox != null) {
            banqueComboBox.setVisible(needsBanque);
            banqueComboBox.setManaged(needsBanque);
        }

        if (numeroChequeField != null) {
            numeroChequeField.setVisible(needsReference);
            numeroChequeField.setManaged(needsReference);

            // Adapter le label selon le mode
            if (mode == ModeReglement.CHEQUE) {
                numeroChequeField.setPromptText("Numéro de chèque");
            } else if (mode == ModeReglement.VIREMENT) {
                numeroChequeField.setPromptText("Référence du virement");
            }
        }
    }

    /**
     * Met à jour le solde restant
     */
    private void updateSoldeRestant() {
        if (soldeRestantLabel == null || montantAmendeField == null || montantEncaisseField == null) return;

        try {
            BigDecimal montantAmende = CurrencyFormatter.parse(montantAmendeField.getText());
            BigDecimal montantEncaisse = CurrencyFormatter.parse(montantEncaisseField.getText());

            BigDecimal solde = montantAmende.subtract(montantEncaisse);

            if (solde.compareTo(BigDecimal.ZERO) < 0) {
                soldeRestantLabel.setText("Montant encaissé supérieur à l'amende !");
                soldeRestantLabel.setStyle("-fx-text-fill: red;");
                if (saveButton != null) saveButton.setDisable(true);
            } else {
                soldeRestantLabel.setText("Solde restant : " + CurrencyFormatter.format(solde) + " FCFA");
                soldeRestantLabel.setStyle("-fx-text-fill: #1976d2;");
                if (saveButton != null) saveButton.setDisable(false);
            }
        } catch (Exception e) {
            soldeRestantLabel.setText("Solde restant : 0 FCFA");
        }
    }

    private void setupValidation() {
        // Validation en temps réel
        if (contrevenantComboBox != null) {
            validationService.setupRequiredField(contrevenantComboBox, "Contrevenant obligatoire");
        }
        if (contraventionComboBox != null) {
            validationService.setupRequiredField(contraventionComboBox, "Type de contravention obligatoire");
        }
        if (montantAmendeField != null) {
            validationService.setupRequiredField(montantAmendeField, "Montant de l'amende obligatoire");
        }

        // ENRICHISSEMENT : Validation du premier encaissement
        if (montantEncaisseField != null) {
            validationService.setupRequiredField(montantEncaisseField, "Montant encaissé obligatoire");
        }
        if (dateEncaissementPicker != null) {
            validationService.setupRequiredField(dateEncaissementPicker, "Date d'encaissement obligatoire");
        }
        if (modeReglementComboBox != null) {
            validationService.setupRequiredField(modeReglementComboBox, "Mode de règlement obligatoire");
        }

        // Validation du montant encaissé
        if (montantEncaisseField != null) {
            montantEncaisseField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    validateMontantEncaisse();
                }
            });
        }
    }

    /**
     * Configure la validation temps réel sur tous les champs
     */
    private void setupRealTimeValidation() {
        logger.debug("Configuration de la validation temps réel");

        // Validation du contrevenant
        if (contrevenantComboBox != null) {
            contrevenantComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                validateContrevenant();
            });
        }

        // Validation du montant amende
        if (montantAmendeField != null) {
            montantAmendeField.textProperty().addListener((obs, oldVal, newVal) -> {
                validateMontantAmende();
                updateMontantEnLettres();
            });

            // Formater automatiquement le montant
            montantAmendeField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) {
                    formatMontantField(montantAmendeField);
                }
            });
        }

        // Validation du montant encaissé
        if (montantEncaisseField != null) {
            montantEncaisseField.textProperty().addListener((obs, oldVal, newVal) -> {
                validateMontantEncaisse();
            });

            montantEncaisseField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) {
                    formatMontantField(montantEncaisseField);
                }
            });
        }

        // Validation de la date d'encaissement
        if (dateEncaissementPicker != null) {
            dateEncaissementPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                validateDateEncaissement();
            });

            // Désactiver les dates futures
            dateEncaissementPicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    setDisable(date.isAfter(LocalDate.now()));
                }
            });
        }

        // Validation du mode de règlement
        if (modeReglementCombo != null) {
            modeReglementCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                validateModeReglement();
                updateBanqueVisibility();
            });
        }

        // Validation des informations bancaires
        if (banqueCombo != null) {
            banqueCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
                    validateBanque();
                }
            });
        }

        if (numeroChequeField != null) {
            numeroChequeField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
                    validateNumeroCheque();
                }
            });
        }

        // Validation des acteurs
        setupActeursValidation();
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
     * Validation du montant de l'amende
     */
    private boolean validateMontantAmende() {
        String montantText = montantAmendeField.getText();

        if (montantText == null || montantText.trim().isEmpty()) {
            showFieldError(montantAmendeField, "Le montant de l'amende est obligatoire");
            return false;
        }

        try {
            BigDecimal montant = CurrencyFormatter.parse(montantText);
            if (montant.compareTo(BigDecimal.ZERO) <= 0) {
                showFieldError(montantAmendeField, "Le montant doit être supérieur à zéro");
                return false;
            }

            clearFieldError(montantAmendeField);
            return true;

        } catch (Exception e) {
            showFieldError(montantAmendeField, "Montant invalide");
            return false;
        }
    }

    /**
     * Validation du montant encaissé
     * Règle métier : montant encaissé ≤ montant amende
     */
    private boolean validateMontantEncaisse() {
        String montantText = montantEncaisseField.getText();

        if (montantText == null || montantText.trim().isEmpty()) {
            showFieldError(montantEncaisseField, "Le montant encaissé est obligatoire");
            return false;
        }

        try {
            BigDecimal montantEncaisse = CurrencyFormatter.parse(montantText);

            if (montantEncaisse.compareTo(BigDecimal.ZERO) <= 0) {
                showFieldError(montantEncaisseField, "Le montant doit être supérieur à zéro");
                return false;
            }

            // Vérifier que le montant encaissé ne dépasse pas le montant de l'amende
            BigDecimal montantAmende = CurrencyFormatter.parse(montantAmendeField.getText());
            if (montantEncaisse.compareTo(montantAmende) > 0) {
                showFieldError(montantEncaisseField,
                        "Le montant encaissé ne peut pas dépasser le montant de l'amende");
                return false;
            }

            clearFieldError(montantEncaisseField);

            // Mettre à jour le solde restant
            updateSoldeRestant();

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
        if (modeReglementCombo.getValue() == null) {
            showFieldError(modeReglementCombo, "Le mode de règlement est obligatoire");
            return false;
        } else {
            clearFieldError(modeReglementCombo);
            return true;
        }
    }

    /**
     * Validation de la banque (si chèque)
     */
    private boolean validateBanque() {
        if (banqueCombo.getValue() == null) {
            showFieldError(banqueCombo, "La banque est obligatoire pour un paiement par chèque");
            return false;
        } else {
            clearFieldError(banqueCombo);
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
     * Configuration de la validation des acteurs
     */
    private void setupActeursValidation() {
        // Vérifier qu'il y a au moins un saisissant lors de l'ajout/suppression
        if (saisissantsList != null) {
            saisissantsList.addListener((ListChangeListener<Agent>) change -> {
                validateSaisissants();
            });
        }
    }

    /**
     * Validation des saisissants (au moins un obligatoire)
     */
    private boolean validateSaisissants() {
        if (saisissantsList == null || saisissantsList.isEmpty()) {
            if (saisissantsErrorLabel != null) {
                saisissantsErrorLabel.setText("Au moins un saisissant est obligatoire");
                saisissantsErrorLabel.setVisible(true);
            }
            return false;
        } else {
            if (saisissantsErrorLabel != null) {
                saisissantsErrorLabel.setVisible(false);
            }
            return true;
        }
    }

    /**
     * Affiche une erreur sur un champ
     */
    private void showFieldError(Control field, String message) {
        field.getStyleClass().add("field-error");

        // Chercher ou créer le label d'erreur
        Label errorLabel = findOrCreateErrorLabel(field);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.getStyleClass().clear();
        errorLabel.getStyleClass().addAll("error-label");
    }

    /**
     * Affiche un warning sur un champ
     */
    private void showFieldWarning(Control field, String message) {
        field.getStyleClass().add("field-warning");

        Label warningLabel = findOrCreateErrorLabel(field);
        warningLabel.setText(message);
        warningLabel.setVisible(true);
        warningLabel.getStyleClass().clear();
        warningLabel.getStyleClass().addAll("warning-label");
    }

    /**
     * Efface l'erreur d'un champ
     */
    private void clearFieldError(Control field) {
        field.getStyleClass().removeAll("field-error", "field-warning");

        Label errorLabel = findErrorLabel(field);
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }
    }

    /**
     * Trouve ou crée un label d'erreur pour un champ
     */
    private Label findOrCreateErrorLabel(Control field) {
        // Chercher un label d'erreur existant
        Label errorLabel = findErrorLabel(field);

        if (errorLabel == null) {
            // Créer un nouveau label d'erreur
            errorLabel = new Label();
            errorLabel.getStyleClass().add("error-label");
            errorLabel.setVisible(false);
            errorLabel.setWrapText(true);

            // L'ajouter après le champ dans son parent
            if (field.getParent() instanceof VBox) {
                VBox parent = (VBox) field.getParent();
                int index = parent.getChildren().indexOf(field);
                parent.getChildren().add(index + 1, errorLabel);
            } else if (field.getParent() instanceof GridPane) {
                GridPane parent = (GridPane) field.getParent();
                Integer row = GridPane.getRowIndex(field);
                Integer col = GridPane.getColumnIndex(field);
                if (row != null && col != null) {
                    GridPane.setRowIndex(errorLabel, row + 1);
                    GridPane.setColumnIndex(errorLabel, col);
                    GridPane.setColumnSpan(errorLabel, 2);
                    parent.getChildren().add(errorLabel);
                }
            }

            // Associer le label au champ
            field.getProperties().put("errorLabel", errorLabel);
        }

        return errorLabel;
    }

    /**
     * Trouve le label d'erreur associé à un champ
     */
    private Label findErrorLabel(Control field) {
        return (Label) field.getProperties().get("errorLabel");
    }

    /**
     * Formate un champ de montant
     */
    private void formatMontantField(TextField field) {
        try {
            BigDecimal montant = CurrencyFormatter.parse(field.getText());
            field.setText(CurrencyFormatter.format(montant));
        } catch (Exception e) {
            // Ignorer si le format est invalide
        }
    }

    /**
     * Met à jour l'affichage du montant en lettres
     */
    private void updateMontantEnLettres() {
        try {
            BigDecimal montant = CurrencyFormatter.parse(montantAmendeField.getText());
            String enLettres = NumberToWords.convert(montant);
            montantEnLettresLabel.setText(enLettres);
        } catch (Exception e) {
            montantEnLettresLabel.setText("");
        }
    }

    /**
     * Met à jour l'affichage du solde restant
     */
    private void updateSoldeRestant() {
        try {
            BigDecimal montantAmende = CurrencyFormatter.parse(montantAmendeField.getText());
            BigDecimal montantEncaisse = CurrencyFormatter.parse(montantEncaisseField.getText());
            BigDecimal solde = montantAmende.subtract(montantEncaisse);

            if (soldeRestantLabel != null) {
                soldeRestantLabel.setText("Solde restant : " + CurrencyFormatter.format(solde));

                if (solde.compareTo(BigDecimal.ZERO) == 0) {
                    soldeRestantLabel.getStyleClass().add("label-success");
                } else {
                    soldeRestantLabel.getStyleClass().remove("label-success");
                }
            }
        } catch (Exception e) {
            // Ignorer si les montants sont invalides
        }
    }

    /**
     * Valide le formulaire complet avant sauvegarde
     */
    private boolean validateForm() {
        boolean valid = true;

        // Valider tous les champs
        valid &= validateContrevenant();
        valid &= validateMontantAmende();
        valid &= validateMontantEncaisse();
        valid &= validateDateEncaissement();
        valid &= validateModeReglement();

        if (modeReglementCombo.getValue() == ModeReglement.CHEQUE) {
            valid &= validateBanque();
            valid &= validateNumeroCheque();
        }

        valid &= validateSaisissants();

        // Vérifier qu'il y a au moins une contravention
        if (contraventionsList == null || contraventionsList.isEmpty()) {
            AlertUtil.showWarning("Validation", "Au moins une contravention est obligatoire");
            valid = false;
        }

        return valid;
    }

    /**
     * Valide le montant encaissé
     */
    private void validateMontantEncaisse() {
        if (montantAmendeField == null || montantEncaisseField == null) return;

        try {
            BigDecimal montantAmende = CurrencyFormatter.parse(montantAmendeField.getText());
            BigDecimal montantEncaisse = CurrencyFormatter.parse(montantEncaisseField.getText());

            if (montantEncaisse.compareTo(BigDecimal.ZERO) <= 0) {
                AlertUtil.showWarningAlert("Validation",
                        "Montant invalide",
                        "Le montant encaissé doit être supérieur à zéro");
                montantEncaisseField.requestFocus();
            } else if (montantEncaisse.compareTo(montantAmende) > 0) {
                AlertUtil.showWarningAlert("Validation",
                        "Montant trop élevé",
                        "Le montant encaissé ne peut pas dépasser le montant de l'amende");
                montantEncaisseField.requestFocus();
            }
        } catch (Exception e) {
            // Ignorer si les montants ne sont pas valides
        }
    }

    private void setupEventHandlers() {
        // Boutons
        if (cancelButton != null) cancelButton.setOnAction(e -> onCancel());
        if (resetButton != null) resetButton.setOnAction(e -> onReset());
        if (saveButton != null) saveButton.setOnAction(e -> onSave());
        if (enregistrerButton != null) enregistrerButton.setOnAction(e -> onSave());
        if (enregistrerEtNouveauButton != null) enregistrerEtNouveauButton.setOnAction(e -> onSaveAndNew());
        if (annulerButton != null) annulerButton.setOnAction(e -> onCancel());

        // Contrevenant
        if (newContrevenantButton != null) newContrevenantButton.setOnAction(e -> onNewContrevenant());
        if (searchContrevenantButton != null) searchContrevenantButton.setOnAction(e -> onSearchContrevenant());

        // Contraventions
        if (addContraventionButton != null) addContraventionButton.setOnAction(e -> onAddContravention());

        // Agents
        if (searchAgentButton != null) searchAgentButton.setOnAction(e -> onSearchAgent());
        if (searchAgentField != null) searchAgentField.setOnAction(e -> onSearchAgent());

        // Détection des changements
        setupChangeDetection();
    }

    private void setupChangeDetection() {
        // Ajouter des listeners pour détecter les changements
        if (contrevenantComboBox != null) {
            contrevenantComboBox.valueProperty().addListener((obs, old, val) -> hasUnsavedChanges = true);
        }
        if (montantAmendeField != null) {
            montantAmendeField.textProperty().addListener((obs, old, val) -> hasUnsavedChanges = true);
        }
        if (montantEncaisseField != null) {
            montantEncaisseField.textProperty().addListener((obs, old, val) -> hasUnsavedChanges = true);
        }
        if (observationsArea != null) {
            observationsArea.textProperty().addListener((obs, old, val) -> hasUnsavedChanges = true);
        }
    }

    private void loadReferenceData() {
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Charger les contrevenants
                List<Contrevenant> contrevenants = contrevenantDAO.findAll();
                Platform.runLater(() -> {
                    if (contrevenantComboBox != null) {
                        contrevenantComboBox.setItems(FXCollections.observableArrayList(contrevenants));
                    }
                });

                // Charger les agents
                List<Agent> agentsList = agentDAO.findAll();
                Platform.runLater(() -> {
                    if (agentVerbalisateurComboBox != null) {
                        agentVerbalisateurComboBox.setItems(FXCollections.observableArrayList(agentsList));
                    }
                });

                // Charger les contraventions
                List<Contravention> contraventions = contraventionDAO.findAll();
                Platform.runLater(() -> {
                    if (contraventionComboBox != null) {
                        contraventionComboBox.setItems(FXCollections.observableArrayList(contraventions));
                    }
                });

                // Charger les bureaux
                List<Bureau> bureaux = bureauDAO.findAll();
                Platform.runLater(() -> {
                    if (bureauComboBox != null) {
                        bureauComboBox.setItems(FXCollections.observableArrayList(bureaux));
                    }
                });

                // Charger les services
                List<Service> services = serviceDAO.findAll();
                Platform.runLater(() -> {
                    if (serviceComboBox != null) {
                        serviceComboBox.setItems(FXCollections.observableArrayList(services));
                    }
                });

                // Charger les banques
                List<Banque> banques = banqueDAO.findAll();
                Platform.runLater(() -> {
                    if (banqueComboBox != null) {
                        banqueComboBox.setItems(FXCollections.observableArrayList(banques));
                    }
                });

                return null;
            }
        };

        new Thread(loadTask).start();
    }

    /**
     * Initialise le formulaire pour une nouvelle affaire
     */
    public void initializeForNew() {
        logger.info("Initialisation pour nouvelle affaire");

        isEditMode = false;
        if (formTitleLabel != null) formTitleLabel.setText("Nouvelle affaire contentieuse");

        // Le numéro sera généré automatiquement
        if (numeroAffaireLabel != null) numeroAffaireLabel.setText("Auto-généré");
        if (dateCreationLabel != null) dateCreationLabel.setText(DateFormatter.format(LocalDate.now()));
        if (statutLabel != null) {
            statutLabel.setText("EN COURS");
            statutLabel.getStyleClass().add("status-open");
        }

        setupRealTimeValidation();

        // ENRICHISSEMENT : Message sur l'encaissement obligatoire
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Information importante");
        info.setHeaderText("Règle métier : Pas d'affaire sans paiement");
        info.setContentText(
                "Une affaire ne peut être créée que si le contrevenant effectue un premier paiement.\n\n" +
                        "Veuillez saisir les informations de l'affaire ET le premier encaissement."
        );
        info.showAndWait();

        hasUnsavedChanges = false;
    }

    /**
     * Sauvegarde l'affaire avec son premier encaissement
     */
    @FXML
    private void onSave() {
        saveAffaire(false);
    }

    @FXML
    private void onSaveAndNew() {
        saveAffaire(true);
    }

    private void saveAffaire(boolean createNew) {
        logger.info("Sauvegarde de l'affaire avec encaissement obligatoire");

        // Validation complète
        if (!validateForm()) {
            return;
        }

        // Collecte des données
        Affaire affaire = collectAffaireData();
        Encaissement encaissement = collectEncaissementData();
        List<AffaireActeur> acteurs = collectActeurs();

        // Vérification critique
        if (!isEditMode && (encaissement == null || encaissement.getMontantEncaisse() == null)) {
            AlertUtil.showErrorAlert("Erreur",
                    "Encaissement obligatoire",
                    "Un premier encaissement est obligatoire pour créer une affaire");
            return;
        }

        // Désactiver le formulaire pendant la sauvegarde
        setFormDisabled(true);
        if (saveButton != null) saveButton.setText("Enregistrement...");

        Task<Affaire> saveTask = new Task<>() {
            @Override
            protected Affaire call() throws Exception {
                if (isEditMode) {
                    // Mode édition : mise à jour simple
                    return affaireService.updateAffaire(affaire);
                } else {
                    // Mode création : NOUVELLE MÉTHODE avec encaissement obligatoire
                    return affaireService.createAffaireAvecEncaissement(affaire, encaissement, acteurs);
                }
            }
        };

        saveTask.setOnSucceeded(e -> {
            Affaire saved = saveTask.getValue();
            logger.info("✅ Affaire {} sauvegardée avec succès", saved.getNumeroAffaire());

            // Message de succès personnalisé
            String message = isEditMode ?
                    "L'affaire a été mise à jour avec succès." :
                    String.format(
                            "L'affaire %s a été créée avec succès.\n" +
                                    "Premier encaissement de %s FCFA enregistré.",
                            saved.getNumeroAffaire(),
                            CurrencyFormatter.format(encaissement.getMontantEncaisse())
                    );

            AlertUtil.showInfoAlert("Succès", "Enregistrement réussi", message);

            hasUnsavedChanges = false;

            if (createNew) {
                initializeForNew();
                setFormDisabled(false);
            } else {
                closeForm();
            }
        });

        saveTask.setOnFailed(e -> {
            Throwable error = saveTask.getException();
            logger.error("Erreur lors de la sauvegarde", error);

            String errorMessage = "Une erreur est survenue lors de l'enregistrement.";
            if (error instanceof BusinessException) {
                errorMessage = error.getMessage();
            }

            AlertUtil.showErrorAlert("Erreur", "Échec de l'enregistrement", errorMessage);

            setFormDisabled(false);
            if (saveButton != null) saveButton.setText("Enregistrer");
        });

        new Thread(saveTask).start();
    }

    /**
     * Collecte les données du formulaire pour l'affaire
     */
    private Affaire collectAffaireData() {
        Affaire affaire = isEditMode ? currentAffaire : new Affaire();

        if (contrevenantComboBox != null) affaire.setContrevenant(contrevenantComboBox.getValue());
        if (montantAmendeField != null) affaire.setMontantAmendeTotal(CurrencyFormatter.parse(montantAmendeField.getText()));
        if (bureauComboBox != null) affaire.setBureau(bureauComboBox.getValue());
        if (serviceComboBox != null) affaire.setService(serviceComboBox.getValue());
        if (observationsArea != null) affaire.setObservations(observationsArea.getText());
        if (dateConstatationPicker != null) affaire.setDateConstatation(dateConstatationPicker.getValue());
        if (lieuConstatationField != null) affaire.setLieuConstatation(lieuConstatationField.getText());
        if (agentVerbalisateurComboBox != null) affaire.setAgentVerbalisateur(agentVerbalisateurComboBox.getValue());
        if (descriptionTextArea != null) affaire.setDescription(descriptionTextArea.getText());

        // Gestion des contraventions
        if (contraventionsList != null && !contraventionsList.isEmpty()) {
            List<Contravention> contraventions = contraventionsList.stream()
                    .map(ContraventionViewModel::getContravention)
                    .collect(Collectors.toList());
            affaire.setContraventions(contraventions);
        }

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
            encaissement.setMontantEncaisse(CurrencyFormatter.parse(montantEncaisseField.getText()));
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
                encaissement.setBanque(banqueComboBox.getValue());
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

    /**
     * Valide le formulaire complet
     */
    private boolean validateForm() {
        List<String> errors = new ArrayList<>();

        // Validation de l'affaire
        if (contrevenantComboBox != null && contrevenantComboBox.getValue() == null) {
            errors.add("Le contrevenant est obligatoire");
        }

        if (montantAmendeField != null && montantAmendeField.getText().trim().isEmpty()) {
            errors.add("Le montant de l'amende est obligatoire");
        }

        // ENRICHISSEMENT : Validation du premier encaissement
        if (!isEditMode) {
            if (montantEncaisseField != null && montantEncaisseField.getText().trim().isEmpty()) {
                errors.add("Le montant encaissé est obligatoire (règle : pas d'affaire sans paiement)");
            }

            if (dateEncaissementPicker != null && dateEncaissementPicker.getValue() == null) {
                errors.add("La date d'encaissement est obligatoire");
            }

            if (modeReglementComboBox != null && modeReglementComboBox.getValue() == null) {
                errors.add("Le mode de règlement est obligatoire");
            }

            // Validation spécifique selon le mode
            if (modeReglementComboBox != null && modeReglementComboBox.getValue() != null) {
                ModeReglement mode = modeReglementComboBox.getValue();
                if (mode.isNecessiteBanque() && banqueComboBox != null && banqueComboBox.getValue() == null) {
                    errors.add("La banque est obligatoire pour ce mode de règlement");
                }
                if (mode.isNecessiteReference() && numeroChequeField != null && numeroChequeField.getText().trim().isEmpty()) {
                    errors.add("La référence est obligatoire pour ce mode de règlement");
                }
            }
        }

        // Validation des contraventions
        if (contraventionsList != null && contraventionsList.isEmpty()) {
            errors.add("Au moins une contravention doit être ajoutée");
        }

        // Affichage des erreurs
        if (!errors.isEmpty()) {
            AlertUtil.showErrorAlert("Validation",
                    "Veuillez corriger les erreurs suivantes :",
                    String.join("\n", errors));
            return false;
        }

        return true;
    }

    // Méthodes d'action

    @FXML
    private void onCancel() {
        if (hasUnsavedChanges) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText("Modifications non sauvegardées");
            alert.setContentText("Voulez-vous vraiment quitter sans sauvegarder ?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                closeForm();
            }
        } else {
            closeForm();
        }
    }

    @FXML
    private void onReset() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Réinitialiser le formulaire");
        alert.setContentText("Voulez-vous vraiment effacer toutes les données saisies ?");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            clearForm();
        }
    }

    @FXML
    private void onNewContrevenant() {
        // TODO: Ouvrir le formulaire de création de contrevenant
        AlertUtil.showInfoAlert("Information", "Fonctionnalité en développement",
                "La création de contrevenant sera disponible prochainement.");
    }

    @FXML
    private void onSearchContrevenant() {
        // TODO: Ouvrir la recherche de contrevenant
        AlertUtil.showInfoAlert("Information", "Fonctionnalité en développement",
                "La recherche de contrevenant sera disponible prochainement.");
    }

    @FXML
    private void onSearchAgent() {
        // TODO: Rechercher des agents
        String searchTerm = searchAgentField != null ? searchAgentField.getText() : "";
        logger.info("Recherche d'agents : {}", searchTerm);
    }

    @FXML
    private void onAddContravention() {
        if (contraventionComboBox == null || contraventionComboBox.getValue() == null) {
            return;
        }

        Contravention selected = contraventionComboBox.getValue();

        // Vérifier si la contravention n'est pas déjà ajoutée
        boolean alreadyAdded = contraventionsList.stream()
                .anyMatch(vm -> vm.getContravention().getId().equals(selected.getId()));

        if (alreadyAdded) {
            AlertUtil.showWarningAlert("Attention",
                    "Contravention déjà ajoutée",
                    "Cette contravention est déjà dans la liste.");
            return;
        }

        // Ajouter la contravention
        contraventionsList.add(new ContraventionViewModel(selected));
        updateContraventionsInfo();
        contraventionComboBox.setValue(null);
        hasUnsavedChanges = true;
    }

    private void removeContravention(ContraventionViewModel contravention) {
        contraventionsList.remove(contravention);
        updateContraventionsInfo();
        hasUnsavedChanges = true;
    }

    private void updateContraventionsInfo() {
        if (montantTotalLabel != null && contraventionsList != null) {
            // Calculer le montant total
            BigDecimal total = contraventionsList.stream()
                    .map(vm -> vm.getContravention().getMontant())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            montantTotalLabel.setText(CurrencyFormatter.format(total));

            // Mettre à jour le montant de l'amende
            if (montantAmendeField != null) {
                montantAmendeField.setText(CurrencyFormatter.format(total));
            }
        }

        if (nombreContraventionsLabel != null && contraventionsList != null) {
            int count = contraventionsList.size();
            nombreContraventionsLabel.setText(count + " contravention" + (count > 1 ? "s" : ""));
        }
    }

    // Méthodes utilitaires

    private void displayContrevenantDetails(Contrevenant contrevenant) {
        if (contrevenantDetailsBox != null) {
            contrevenantDetailsBox.setVisible(true);
            contrevenantDetailsBox.setManaged(true);
        }

        if (typeContrevenantLabel != null) {
            typeContrevenantLabel.setText(contrevenant.getTypePersonne());
        }
        if (nomContrevenantLabel != null) {
            nomContrevenantLabel.setText(contrevenant.getNomComplet());
        }
        if (adresseContrevenantLabel != null) {
            adresseContrevenantLabel.setText(contrevenant.getAdresse() != null ? contrevenant.getAdresse() : "Non renseignée");
        }
        if (telephoneContrevenantLabel != null) {
            telephoneContrevenantLabel.setText(contrevenant.getTelephone() != null ? contrevenant.getTelephone() : "Non renseigné");
        }
        if (emailContrevenantLabel != null) {
            emailContrevenantLabel.setText(contrevenant.getEmail() != null ? contrevenant.getEmail() : "Non renseigné");
        }
    }

    private void hideContrevenantDetails() {
        if (contrevenantDetailsBox != null) {
            contrevenantDetailsBox.setVisible(false);
            contrevenantDetailsBox.setManaged(false);
        }
    }

    private void clearForm() {
        if (contrevenantComboBox != null) contrevenantComboBox.setValue(null);
        if (montantAmendeField != null) montantAmendeField.clear();
        if (montantEncaisseField != null) montantEncaisseField.clear();
        if (observationsArea != null) observationsArea.clear();
        if (dateConstatationPicker != null) dateConstatationPicker.setValue(LocalDate.now());
        if (lieuConstatationField != null) lieuConstatationField.clear();
        if (agentVerbalisateurComboBox != null) agentVerbalisateurComboBox.setValue(null);
        if (descriptionTextArea != null) descriptionTextArea.clear();
        if (contraventionsList != null) contraventionsList.clear();
        if (dateEncaissementPicker != null) dateEncaissementPicker.setValue(LocalDate.now());
        if (modeReglementComboBox != null) modeReglementComboBox.setValue(ModeReglement.ESPECES);

        hasUnsavedChanges = false;
    }

    private void setFormDisabled(boolean disabled) {
        if (contrevenantComboBox != null) contrevenantComboBox.setDisable(disabled);
        if (contraventionComboBox != null) contraventionComboBox.setDisable(disabled);
        if (montantAmendeField != null) montantAmendeField.setDisable(disabled);
        if (montantEncaisseField != null) montantEncaisseField.setDisable(disabled);
        if (dateEncaissementPicker != null) dateEncaissementPicker.setDisable(disabled);
        if (modeReglementComboBox != null) modeReglementComboBox.setDisable(disabled);
        if (saveButton != null) saveButton.setDisable(disabled);
        if (enregistrerButton != null) enregistrerButton.setDisable(disabled);
        if (enregistrerEtNouveauButton != null) enregistrerEtNouveauButton.setDisable(disabled);
    }

    private void closeForm() {
        if (currentStage != null) {
            currentStage.close();
        }
    }

    private void updateMontantEnLettres(String montant, Label label) {
        if (label == null) return;

        try {
            BigDecimal value = CurrencyFormatter.parse(montant);
            label.setText(NumberToWords.convert(value.longValue()) + " francs CFA");
        } catch (Exception e) {
            label.setText("");
        }
    }

    // Classes internes

    /**
     * ViewModel pour les agents dans le tableau
     */
    public static class AgentViewModel {
        private final Agent agent;
        private boolean selected;
        private String role;

        public AgentViewModel(Agent agent) {
            this.agent = agent;
            this.selected = false;
            this.role = "SAISISSANT";
        }

        public Agent getAgent() { return agent; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getMatricule() { return agent.getCodeAgent(); }
        public String getNomComplet() { return agent.getNomComplet(); }
    }

    /**
     * ViewModel pour les contraventions dans le tableau
     */
    public static class ContraventionViewModel {
        private final Contravention contravention;

        public ContraventionViewModel(Contravention contravention) {
            this.contravention = contravention;
        }

        public Contravention getContravention() {
            return contravention;
        }
    }
}