package com.regulation.contentieux.controller;

import com.regulation.contentieux.dao.EncaissementDAO;
import com.regulation.contentieux.dao.AffaireDAO;
import com.regulation.contentieux.model.Encaissement;
import com.regulation.contentieux.model.Affaire;
import com.regulation.contentieux.model.enums.ModeReglement;
import com.regulation.contentieux.model.enums.StatutEncaissement;
import com.regulation.contentieux.service.AuthenticationService;
import com.regulation.contentieux.service.EncaissementService;
import com.regulation.contentieux.util.AlertUtil;
import com.regulation.contentieux.util.CurrencyFormatter;
import com.regulation.contentieux.util.DateFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour le formulaire d'encaissement - SUIT LE PATTERN ÉTABLI
 * Respecte exactement la logique des autres contrôleurs de formulaire
 */
public class EncaissementFormController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(EncaissementFormController.class);

    // FXML - En-tête
    @FXML private Label formTitleLabel;
    @FXML private Label modeLabel;

    // FXML - Informations générales
    @FXML private TextField referenceField;
    @FXML private Button generateReferenceButton;
    @FXML private DatePicker dateEncaissementPicker;
    @FXML private TextField montantEncaisseField;
    @FXML private ComboBox<StatutEncaissement> statutComboBox;

    // FXML - Affaire liée
    @FXML private ComboBox<Affaire> affaireComboBox;
    @FXML private Button searchAffaireButton;
    @FXML private VBox affaireDetailsBox;
    @FXML private Label contrevenantLabel;
    @FXML private Label montantTotalLabel;
    @FXML private Label montantEncaisseAnterieurLabel;
    @FXML private Label soldeRestantLabel;

    // FXML - Mode de règlement
    @FXML private ComboBox<ModeReglement> modeReglementComboBox;
    @FXML private ComboBox<Object> banqueComboBox; // TODO: Banque class
    @FXML private VBox modeReglementInfoBox;
    @FXML private Label modeReglementInfoLabel;
    @FXML private GridPane modeReglementFieldsGrid;
    @FXML private Label referenceExterneLabel;
    @FXML private TextField referenceExterneField;
    @FXML private Label numeroChequeLabel;
    @FXML private TextField numeroChequeField;

    // FXML - Observations
    @FXML private TextArea observationsField;

    // FXML - Historique
    @FXML private VBox encaissementsHistoryBox;
    @FXML private Label encaissementsCountLabel;
    @FXML private TableView<EncaissementHistoryViewModel> encaissementsTableView;
    @FXML private TableColumn<EncaissementHistoryViewModel, String> histReferenceColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, LocalDate> histDateColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, Double> histMontantColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, ModeReglement> histModeColumn;
    @FXML private TableColumn<EncaissementHistoryViewModel, StatutEncaissement> histStatutColumn;

    // FXML - Actions
    @FXML private Button cancelButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;

    // Services et données - SUIT LE PATTERN ÉTABLI
    private EncaissementService encaissementService;
    private EncaissementDAO encaissementDAO;
    private AffaireDAO affaireDAO;
    private AuthenticationService authService;

    // État du formulaire
    private boolean isEditMode = false;
    private Encaissement currentEncaissement;
    private ObservableList<Affaire> affaires;
    private ObservableList<EncaissementHistoryViewModel> encaissementsHistory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialisation des services - SUIT LE PATTERN ÉTABLI
        encaissementService = new EncaissementService();
        encaissementDAO = new EncaissementDAO();
        affaireDAO = new AffaireDAO();
        authService = AuthenticationService.getInstance();

        // Initialisation des listes
        affaires = FXCollections.observableArrayList();
        encaissementsHistory = FXCollections.observableArrayList();

        setupUI();
        setupEventHandlers();
        setupTableColumns();
        loadFormData();

        logger.info("Contrôleur de formulaire d'encaissement initialisé");
    }

    /**
     * Configuration initiale de l'interface - SUIT LE PATTERN ÉTABLI
     */
    private void setupUI() {
        // Configuration des ComboBox
        setupStatutComboBox();
        setupModeReglementComboBox();
        setupAffaireComboBox();

        // Configuration des champs
        dateEncaissementPicker.setValue(LocalDate.now());
        montantEncaisseField.setPromptText("Montant en FCFA");
        observationsField.setWrapText(true);

        // Configuration initiale
        updateUIMode();
    }

    /**
     * Configuration des gestionnaires d'événements - SUIT LE PATTERN ÉTABLI
     */
    private void setupEventHandlers() {
        // Génération de la référence
        generateReferenceButton.setOnAction(e -> generateReference());

        // Validation en temps réel du montant
        montantEncaisseField.textProperty().addListener((obs, oldVal, newVal) -> validateMontant(newVal));

        // Sélection de l'affaire
        affaireComboBox.setOnAction(e -> handleAffaireSelection());

        // Changement de mode de règlement
        modeReglementComboBox.setOnAction(e -> handleModeReglementChange());

        // Boutons d'action
        cancelButton.setOnAction(e -> handleCancel());
        resetButton.setOnAction(e -> handleReset());
        saveButton.setOnAction(e -> handleSave());

        // Recherche d'affaire
        searchAffaireButton.setOnAction(e -> handleSearchAffaire());
    }

    /**
     * Configuration des colonnes du tableau - SUIT LE PATTERN ÉTABLI
     */
    private void setupTableColumns() {
        histReferenceColumn.setCellValueFactory(new PropertyValueFactory<>("reference"));

        histDateColumn.setCellValueFactory(new PropertyValueFactory<>("dateEncaissement"));
        histDateColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, LocalDate>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : DateFormatter.formatDate(date));
            }
        });

        histMontantColumn.setCellValueFactory(new PropertyValueFactory<>("montantEncaisse"));
        histMontantColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, Double>() {
            @Override
            protected void updateItem(Double montant, boolean empty) {
                super.updateItem(montant, empty);
                setText(empty || montant == null ? null : CurrencyFormatter.format(montant));
            }
        });

        histModeColumn.setCellValueFactory(new PropertyValueFactory<>("modeReglement"));
        histModeColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, ModeReglement>() {
            @Override
            protected void updateItem(ModeReglement mode, boolean empty) {
                super.updateItem(mode, empty);
                setText(empty || mode == null ? null : mode.getLibelle());
            }
        });

        histStatutColumn.setCellValueFactory(new PropertyValueFactory<>("statut"));
        histStatutColumn.setCellFactory(col -> new TableCell<EncaissementHistoryViewModel, StatutEncaissement>() {
            @Override
            protected void updateItem(StatutEncaissement statut, boolean empty) {
                super.updateItem(statut, empty);
                if (empty || statut == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(statut.getLibelle());
                    setStyle("-fx-text-fill: " + statut.getCouleur() + "; -fx-font-weight: bold;");
                }
            }
        });

        encaissementsTableView.setItems(encaissementsHistory);
    }

    /**
     * Configuration du ComboBox des statuts - SUIT LE PATTERN ÉTABLI
     */
    private void setupStatutComboBox() {
        statutComboBox.getItems().addAll(StatutEncaissement.values());
        statutComboBox.setValue(StatutEncaissement.EN_ATTENTE);
        statutComboBox.setConverter(new StringConverter<StatutEncaissement>() {
            @Override
            public String toString(StatutEncaissement statut) {
                return statut != null ? statut.getLibelle() : "";
            }

            @Override
            public StatutEncaissement fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Configuration du ComboBox des modes de règlement - SUIT LE PATTERN ÉTABLI
     */
    private void setupModeReglementComboBox() {
        modeReglementComboBox.getItems().addAll(ModeReglement.values());
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
    }

    /**
     * Configuration du ComboBox des affaires - SUIT LE PATTERN ÉTABLI
     */
    private void setupAffaireComboBox() {
        affaireComboBox.setItems(affaires);
        affaireComboBox.setConverter(new StringConverter<Affaire>() {
            @Override
            public String toString(Affaire affaire) {
                return affaire != null ?
                        affaire.getNumeroAffaire() + " - " + CurrencyFormatter.format(affaire.getMontantAmendeTotal()) : "";
            }

            @Override
            public Affaire fromString(String string) {
                return null;
            }
        });
    }

    /**
     * Charge les données du formulaire - SUIT LE PATTERN ÉTABLI
     */
    private void loadFormData() {
        Task<Void> loadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Chargement des affaires qui peuvent recevoir des encaissements
                List<Affaire> affairesList = affaireDAO.findAll().stream()
                        .filter(Affaire::peutRecevoirEncaissement)
                        .toList();

                Platform.runLater(() -> {
                    affaires.clear();
                    affaires.addAll(affairesList);

                    logger.info("Données du formulaire chargées: {} affaires disponibles", affairesList.size());
                });

                return null;
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement des données", getException());
                    AlertUtil.showErrorAlert("Erreur de chargement",
                            "Impossible de charger les données",
                            "Vérifiez la connexion à la base de données.");
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // Actions du formulaire - SUIT LE PATTERN ÉTABLI

    private void generateReference() {
        try {
            String nextReference = encaissementService.generateNextNumeroEncaissement();
            referenceField.setText(nextReference);
            logger.info("Référence d'encaissement générée: {}", nextReference);
        } catch (Exception e) {
            logger.error("Erreur lors de la génération de la référence", e);
            AlertUtil.showErrorAlert("Erreur de génération",
                    "Impossible de générer la référence d'encaissement",
                    "Vérifiez la connexion à la base de données.");
        }
    }

    private void validateMontant(String newValue) {
        if (newValue != null && !newValue.trim().isEmpty()) {
            try {
                double montant = Double.parseDouble(newValue.replace(",", "."));
                if (montant <= 0) {
                    montantEncaisseField.setStyle("-fx-border-color: red;");
                } else {
                    montantEncaisseField.setStyle("");
                    // Vérifier si le montant dépasse le solde restant
                    checkMontantVsSolde(montant);
                }
            } catch (NumberFormatException e) {
                montantEncaisseField.setStyle("-fx-border-color: red;");
            }
        } else {
            montantEncaisseField.setStyle("");
        }
    }

    private void checkMontantVsSolde(double montant) {
        if (affaireComboBox.getValue() != null) {
            Affaire affaire = affaireComboBox.getValue();
            BigDecimal totalEncaisse = encaissementService.getTotalEncaisseByAffaire(affaire.getId());
            BigDecimal totalEncaisseNonNull = totalEncaisse != null ? totalEncaisse : BigDecimal.ZERO;
            BigDecimal montantTotal = affaire.getMontantAmendeTotal();
            BigDecimal soldeRestant = montantTotal.subtract(totalEncaisse != null ? totalEncaisse : BigDecimal.ZERO);

            if (BigDecimal.valueOf(montant).compareTo(soldeRestant) > 0) {
                montantEncaisseField.setStyle("-fx-border-color: orange;");
                // Note: Orange pour avertissement, pas erreur bloquante
            }
        }
    }

    private void handleAffaireSelection() {
        Affaire selected = affaireComboBox.getValue();
        if (selected != null) {
            updateAffaireDetails(selected);
            loadEncaissementsHistory(selected.getId());
        } else {
            hideAffaireDetails();
        }
    }

    private void updateAffaireDetails(Affaire affaire) {
        // TODO: Récupérer le nom du contrevenant via ContrevenantDAO
        contrevenantLabel.setText("Contrevenant #" + affaire.getContrevenantId());

        BigDecimal montantTotal = affaire.getMontantAmendeTotal();
        montantTotalLabel.setText(CurrencyFormatter.format(montantTotal.doubleValue()));

        // Calculer le montant déjà encaissé
        BigDecimal totalEncaisse = encaissementService.getTotalEncaisseByAffaire(affaire.getId());
        BigDecimal totalEncaisseNonNull = totalEncaisse != null ? totalEncaisse : BigDecimal.ZERO;
        montantEncaisseAnterieurLabel.setText(CurrencyFormatter.format(totalEncaisseNonNull.doubleValue()));

        // Calculer le solde restant
        BigDecimal soldeRestant = montantTotal.subtract(totalEncaisseNonNull);
        soldeRestantLabel.setText(CurrencyFormatter.format(soldeRestant.doubleValue()));

        // Afficher la section
        affaireDetailsBox.setVisible(true);
        affaireDetailsBox.setManaged(true);
    }

    private void hideAffaireDetails() {
        affaireDetailsBox.setVisible(false);
        affaireDetailsBox.setManaged(false);

        encaissementsHistoryBox.setVisible(false);
        encaissementsHistoryBox.setManaged(false);
    }

    private void handleModeReglementChange() {
        ModeReglement selected = modeReglementComboBox.getValue();

        if (selected != null) {
            updateModeReglementInfo(selected);
        } else {
            hideModeReglementInfo();
        }
    }

    private void updateModeReglementInfo(ModeReglement mode) {
        // Mise à jour du texte d'information
        StringBuilder info = new StringBuilder();
        info.append("Mode: ").append(mode.getLibelle()).append(". ");

        if (mode.isNecessiteBanque()) {
            info.append("Banque obligatoire. ");
        }
        if (mode.isNecessiteReference()) {
            info.append("Référence externe obligatoire. ");
        }
        if (mode.getDelaiEncaissement() > 0) {
            info.append("Délai d'encaissement: ").append(mode.getDelaiEncaissement()).append(" jour(s).");
        }

        modeReglementInfoLabel.setText(info.toString());

        // Affichage conditionnel des champs
        updateConditionalFields(mode);

        // Afficher la section
        modeReglementInfoBox.setVisible(true);
        modeReglementInfoBox.setManaged(true);
    }

    private void updateConditionalFields(ModeReglement mode) {
        // Champ référence externe
        boolean needsReference = mode.isNecessiteReference();
        referenceExterneLabel.setVisible(needsReference);
        referenceExterneLabel.setManaged(needsReference);
        referenceExterneField.setVisible(needsReference);
        referenceExterneField.setManaged(needsReference);

        // Mise à jour du prompt selon le mode
        if (needsReference) {
            switch (mode) {
                case CHEQUE -> referenceExterneField.setPromptText("Numéro du chèque");
                case VIREMENT -> referenceExterneField.setPromptText("Référence du virement");
                case MANDAT -> referenceExterneField.setPromptText("Numéro du mandat");
                default -> referenceExterneField.setPromptText("Référence externe");
            }
        }

        // ComboBox banque
        banqueComboBox.setDisable(!mode.isNecessiteBanque());
        if (!mode.isNecessiteBanque()) {
            banqueComboBox.setValue(null);
        }
    }

    private void hideModeReglementInfo() {
        modeReglementInfoBox.setVisible(false);
        modeReglementInfoBox.setManaged(false);
    }

    private void loadEncaissementsHistory(Long affaireId) {
        Task<List<Encaissement>> loadTask = new Task<List<Encaissement>>() {
            @Override
            protected List<Encaissement> call() throws Exception {
                return encaissementService.findByAffaireId(affaireId);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<Encaissement> encaissements = getValue();

                    // Filtrer l'encaissement actuel si on est en mode édition
                    if (isEditMode && currentEncaissement != null) {
                        encaissements = encaissements.stream()
                                .filter(e -> !e.getId().equals(currentEncaissement.getId()))
                                .toList();
                    }

                    // Conversion vers ViewModel
                    encaissementsHistory.clear();
                    encaissements.forEach(e -> {
                        EncaissementHistoryViewModel vm = new EncaissementHistoryViewModel();
                        vm.setReference(e.getReference());
                        vm.setDateEncaissement(e.getDateEncaissement());
                        vm.setMontantEncaisse(e.getMontantEncaisse());
                        vm.setModeReglement(e.getModeReglement());
                        vm.setStatut(e.getStatut());
                        encaissementsHistory.add(vm);
                    });

                    // Mise à jour du compteur
                    encaissementsCountLabel.setText("(" + encaissements.size() + " encaissement(s))");

                    // Afficher la section si on a des données
                    if (!encaissements.isEmpty()) {
                        encaissementsHistoryBox.setVisible(true);
                        encaissementsHistoryBox.setManaged(true);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors du chargement de l'historique", getException());
                });
            }
        };

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void handleSearchAffaire() {
        logger.info("Recherche d'affaire demandée");
        AlertUtil.showInfoAlert("Recherche d'affaire",
                "Fonctionnalité en développement",
                "La recherche avancée d'affaires sera disponible prochainement.");
    }

    private void handleSave() {
        if (validateForm()) {
            saveEncaissement();
        }
    }

    private void handleReset() {
        if (AlertUtil.showConfirmAlert("Confirmation",
                "Réinitialiser le formulaire",
                "Voulez-vous vraiment réinitialiser tous les champs ?")) {
            resetForm();
        }
    }

    private void handleCancel() {
        if (hasUnsavedChanges()) {
            if (AlertUtil.showConfirmAlert("Confirmation",
                    "Annuler les modifications",
                    "Vous avez des modifications non sauvegardées. Voulez-vous vraiment annuler ?")) {
                closeForm();
            }
        } else {
            closeForm();
        }
    }

    /**
     * Validation du formulaire - SUIT LE PATTERN ÉTABLI
     */
    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        // Validation de la référence
        if (referenceField.getText() == null || referenceField.getText().trim().isEmpty()) {
            errors.append("• La référence d'encaissement est obligatoire\n");
        }

        // Validation de la date
        if (dateEncaissementPicker.getValue() == null) {
            errors.append("• La date d'encaissement est obligatoire\n");
        }

        // Validation du montant
        if (montantEncaisseField.getText() == null || montantEncaisseField.getText().trim().isEmpty()) {
            errors.append("• Le montant encaissé est obligatoire\n");
        } else {
            try {
                double montant = Double.parseDouble(montantEncaisseField.getText().replace(",", "."));
                if (montant <= 0) {
                    errors.append("• Le montant encaissé doit être positif\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Le montant encaissé doit être un nombre valide\n");
            }
        }

        // Validation de l'affaire
        if (affaireComboBox.getValue() == null) {
            errors.append("• L'affaire liée est obligatoire\n");
        }

        // Validation du mode de règlement
        if (modeReglementComboBox.getValue() == null) {
            errors.append("• Le mode de règlement est obligatoire\n");
        } else {
            ModeReglement mode = modeReglementComboBox.getValue();

            // Validation banque si nécessaire
            if (mode.isNecessiteBanque() && banqueComboBox.getValue() == null) {
                errors.append("• Une banque est obligatoire pour le mode de règlement " + mode.getLibelle() + "\n");
            }

            // Validation référence externe si nécessaire
            if (mode.isNecessiteReference() &&
                    (referenceExterneField.getText() == null || referenceExterneField.getText().trim().isEmpty())) {
                errors.append("• Une référence externe est obligatoire pour le mode de règlement " + mode.getLibelle() + "\n");
            }
        }

        // Validation du statut
        if (statutComboBox.getValue() == null) {
            errors.append("• Le statut est obligatoire\n");
        }

        if (errors.length() > 0) {
            AlertUtil.showErrorAlert("Erreurs de validation",
                    "Veuillez corriger les erreurs suivantes :",
                    errors.toString());
            return false;
        }

        return true;
    }

    /**
     * Sauvegarde de l'encaissement - SUIT LE PATTERN ÉTABLI
     */
    private void saveEncaissement() {
        Task<Encaissement> saveTask = new Task<Encaissement>() {
            @Override
            protected Encaissement call() throws Exception {
                Encaissement encaissement = isEditMode ? currentEncaissement : new Encaissement();

                // Remplissage des données
                encaissement.setReference(referenceField.getText().trim());
                encaissement.setDateEncaissement(dateEncaissementPicker.getValue());
                BigDecimal montant = new BigDecimal(montantEncaisseField.getText().replace(",", "."));
                encaissement.setMontantEncaisse(montant);
                encaissement.setStatut(statutComboBox.getValue());
                encaissement.setAffaireId(affaireComboBox.getValue().getId());
                encaissement.setModeReglement(modeReglementComboBox.getValue());

                // Référence externe si applicable
                if (referenceExterneField.isVisible() && !referenceExterneField.getText().trim().isEmpty()) {
                    // Note: On stocke la référence externe dans le champ reference principal
                    // ou on peut l'ajouter comme attribut supplémentaire selon le modèle
                }

                // Banque si applicable
                if (banqueComboBox.getValue() != null) {
                    // TODO: Récupérer l'ID de la banque sélectionnée
                    // encaissement.setBanqueId(banqueComboBox.getValue().getId());
                }

                // Métadonnées
                String currentUser = authService.getCurrentUser().getUsername();
                if (isEditMode) {
                    encaissement.setUpdatedBy(currentUser);
                    return encaissementService.updateEncaissement(encaissement);
                } else {
                    encaissement.setCreatedBy(currentUser);
                    return encaissementService.saveEncaissement(encaissement);
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    Encaissement savedEncaissement = getValue();
                    String message = isEditMode ? "Encaissement modifié avec succès" : "Encaissement créé avec succès";

                    AlertUtil.showSuccessAlert("Sauvegarde réussie", message,
                            "Référence: " + savedEncaissement.getReference());

                    // Mise à jour de l'état
                    currentEncaissement = savedEncaissement;
                    isEditMode = true;
                    updateUIMode();

                    // Recharger l'historique
                    if (affaireComboBox.getValue() != null) {
                        loadEncaissementsHistory(affaireComboBox.getValue().getId());
                    }

                    logger.info("Encaissement sauvegardé: {}", savedEncaissement.getReference());
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    logger.error("Erreur lors de la sauvegarde", getException());
                    AlertUtil.showErrorAlert("Erreur de sauvegarde",
                            "Impossible de sauvegarder l'encaissement",
                            "Vérifiez les données et réessayez.\n\nDétail: " + getException().getMessage());
                });
            }
        };

        Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Réinitialise le formulaire - SUIT LE PATTERN ÉTABLI
     */
    private void resetForm() {
        referenceField.clear();
        dateEncaissementPicker.setValue(LocalDate.now());
        montantEncaisseField.clear();
        statutComboBox.setValue(StatutEncaissement.EN_ATTENTE);
        affaireComboBox.setValue(null);
        modeReglementComboBox.setValue(null);
        banqueComboBox.setValue(null);
        referenceExterneField.clear();
        observationsField.clear();

        encaissementsHistory.clear();
        hideAffaireDetails();
        hideModeReglementInfo();

        // Réinitialisation des styles d'erreur
        montantEncaisseField.setStyle("");

        logger.info("Formulaire réinitialisé");
    }

    /**
     * Vérifie s'il y a des modifications non sauvegardées
     */
    private boolean hasUnsavedChanges() {
        BigDecimal currentMontant = currentEncaissement.getMontantEncaisse();
        if (currentEncaissement == null) {
            // Mode création - vérifier si des champs sont remplis
            return !referenceField.getText().trim().isEmpty() ||
                    !montantEncaisseField.getText().trim().isEmpty() ||
                    affaireComboBox.getValue() != null ||
                    modeReglementComboBox.getValue() != null;
        } else {
            // Mode édition - comparer avec les valeurs originales
            return !referenceField.getText().equals(currentEncaissement.getReference()) ||
                    !dateEncaissementPicker.getValue().equals(currentEncaissement.getDateEncaissement()) ||
                    !montantEncaisseField.getText().equals(currentMontant != null ? currentMontant.toString() : "0") ||
                    !statutComboBox.getValue().equals(currentEncaissement.getStatut());
        }
    }

    /**
     * Met à jour l'interface selon le mode - SUIT LE PATTERN ÉTABLI
     */
    private void updateUIMode() {
        if (isEditMode) {
            formTitleLabel.setText("Modifier l'encaissement");
            modeLabel.setText("Mode édition");
            saveButton.setText("Mettre à jour");
        } else {
            formTitleLabel.setText("Nouvel encaissement");
            modeLabel.setText("Mode création");
            saveButton.setText("Enregistrer");
        }
    }

    /**
     * Ferme le formulaire
     */
    private void closeForm() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    // Méthodes publiques pour l'intégration

    /**
     * Configure le formulaire en mode édition - SUIT LE PATTERN ÉTABLI
     */
    public void setEncaissementToEdit(Encaissement encaissement) {
        this.currentEncaissement = encaissement;
        this.isEditMode = true;

        Platform.runLater(() -> {
            fillFormWithEncaissement(encaissement);
            updateUIMode();
        });
    }

    /**
     * Remplit le formulaire avec les données d'un encaissement - SUIT LE PATTERN ÉTABLI
     */
    private void fillFormWithEncaissement(Encaissement encaissement) {
        referenceField.setText(encaissement.getReference());
        dateEncaissementPicker.setValue(encaissement.getDateEncaissement());
        montantEncaisseField.setText(String.valueOf(encaissement.getMontantEncaisse()));
        statutComboBox.setValue(encaissement.getStatut());
        modeReglementComboBox.setValue(encaissement.getModeReglement());

        // Sélectionner l'affaire correspondante
        affaires.stream()
                .filter(a -> a.getId().equals(encaissement.getAffaireId()))
                .findFirst()
                .ifPresent(affaireComboBox::setValue);

        // Déclencher les mises à jour
        handleAffaireSelection();
        handleModeReglementChange();

        logger.info("Formulaire rempli avec l'encaissement: {}", encaissement.getReference());
    }

    /**
     * Configure le formulaire en mode création avec des valeurs par défaut
     */
    public void setDefaultValues(String reference, Affaire affaire) {
        Platform.runLater(() -> {
            if (reference != null) {
                referenceField.setText(reference);
            }
            if (affaire != null) {
                affaireComboBox.setValue(affaire);
                handleAffaireSelection();
            }
        });
    }

    /**
     * Classe ViewModel pour l'historique des encaissements - SUIT LE PATTERN ÉTABLI
     */
    public static class EncaissementHistoryViewModel {
        private String reference;
        private LocalDate dateEncaissement;
        private Double montantEncaisse;
        private ModeReglement modeReglement;
        private StatutEncaissement statut;

        // Getters et setters
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }

        public LocalDate getDateEncaissement() { return dateEncaissement; }
        public void setDateEncaissement(LocalDate dateEncaissement) { this.dateEncaissement = dateEncaissement; }

        public Double getMontantEncaisse() { return montantEncaisse; }
        public void setMontantEncaisse(Double montantEncaisse) { this.montantEncaisse = montantEncaisse; }

        public ModeReglement getModeReglement() { return modeReglement; }
        public void setModeReglement(ModeReglement modeReglement) { this.modeReglement = modeReglement; }

        public StatutEncaissement getStatut() { return statut; }
        public void setStatut(StatutEncaissement statut) { this.statut = statut; }
    }
}