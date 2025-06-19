package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.*;
import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la gestion des données de référence
 * Gestion des Services, Bureaux, Centres, Contraventions, Banques
 */
public class ReferentielController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ReferentielController.class);

    // Sélection du type de référentiel
    @FXML private ComboBox<String> typeReferentielComboBox;

    // Table générique
    @FXML private TableView<Object> referentielTableView;
    @FXML private TableColumn<Object, String> codeColumn;
    @FXML private TableColumn<Object, String> libelleColumn;
    @FXML private TableColumn<Object, String> descriptionColumn;
    @FXML private TableColumn<Object, String> statutColumn;

    // Formulaire générique
    @FXML private VBox formulaireBox;
    @FXML private TextField codeField;
    @FXML private TextField libelleField;
    @FXML private TextArea descriptionTextArea;
    @FXML private CheckBox actifCheckBox;
    @FXML private ComboBox<Object> parentComboBox; // Pour les hiérarchies (Section->Service->Centre)
    @FXML private Label parentLabel;

    // Boutons
    @FXML private Button nouveauButton;
    @FXML private Button modifierButton;
    @FXML private Button supprimerButton;
    @FXML private Button enregistrerButton;
    @FXML private Button annulerButton;
    @FXML private Button actualiserButton;

    // Recherche
    @FXML private TextField rechercheField;
    @FXML private ComboBox<String> filtreStatutComboBox;

    // Informations
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private final ValidationService validationService;

    // DAOs pour chaque type
    private ServiceDAO serviceDAO;
    private BureauDAO bureauDAO;
    private CentreDAO centreDAO;
    private ContraventionDAO contraventionDAO;
    private BanqueDAO banqueDAO;

    private ObservableList<Object> donneesOriginales;
    private ObservableList<Object> donneesFiltrees;
    private Object elementEnCours;
    private boolean modeCreation = false;
    private String typeActuel = "";

    public ReferentielController() {
        this.validationService = new ValidationService();

        // Initialisation des DAOs
        this.serviceDAO = new ServiceDAO();
        this.bureauDAO = new BureauDAO();
        this.centreDAO = new CentreDAO();
        this.contraventionDAO = new ContraventionDAO();
        this.banqueDAO = new BanqueDAO();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupComboBoxes();
        setupTableColumns();
        setupEventHandlers();
        setupFormValidation();
        setupInitialState();

        logger.info("ReferentielController initialisé");
    }

    private void setupComboBoxes() {
        // Types de référentiels disponibles
        typeReferentielComboBox.setItems(FXCollections.observableArrayList(
                "Services",
                "Bureaux",
                "Centres",
                "Contraventions",
                "Banques"
        ));

        // Filtres
        filtreStatutComboBox.getItems().addAll("Tous les statuts", "Actif", "Inactif");
        filtreStatutComboBox.setValue("Tous les statuts");
    }

    private void setupTableColumns() {
        // Colonnes génériques adaptables
        codeColumn.setCellValueFactory(cellData -> {
            Object item = cellData.getValue();
            return new SimpleStringProperty(getCode(item));
        });

        libelleColumn.setCellValueFactory(cellData -> {
            Object item = cellData.getValue();
            return new SimpleStringProperty(getLibelle(item));
        });

        descriptionColumn.setCellValueFactory(cellData -> {
            Object item = cellData.getValue();
            return new SimpleStringProperty(getDescription(item));
        });

        statutColumn.setCellValueFactory(cellData -> {
            Object item = cellData.getValue();
            return new SimpleStringProperty(isActif(item) ? "Actif" : "Inactif");
        });

        // Style conditionnel pour les éléments inactifs
        referentielTableView.setRowFactory(tv -> {
            TableRow<Object> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null && !isActif(newItem)) {
                    row.setStyle("-fx-background-color: #ffebee;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    private void setupEventHandlers() {
        // Changement de type de référentiel
        typeReferentielComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                changerTypeReferentiel(newVal);
            }
        });

        // Sélection dans la table
        referentielTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    modifierButton.setDisable(newSelection == null);
                    supprimerButton.setDisable(newSelection == null);
                }
        );

        nouveauButton.setOnAction(e -> creerNouvelElement());
        modifierButton.setOnAction(e -> modifierElementSelectionne());
        supprimerButton.setOnAction(e -> supprimerElementSelectionne());
        enregistrerButton.setOnAction(e -> enregistrerElement());
        annulerButton.setOnAction(e -> annulerEdition());
        actualiserButton.setOnAction(e -> actualiserDonnees());

        // Filtrage en temps réel
        rechercheField.textProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
        filtreStatutComboBox.valueProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
    }

    private void setupFormValidation() {
        // Validation du code en temps réel
        codeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty() && newVal.length() >= 2) {
                codeField.setStyle("-fx-border-color: green;");
            } else {
                codeField.setStyle("-fx-border-color: red;");
            }
            validerFormulaire();
        });

        // Validation du libellé
        libelleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.trim().isEmpty()) {
                libelleField.setStyle("-fx-border-color: green;");
            } else {
                libelleField.setStyle("-fx-border-color: red;");
            }
            validerFormulaire();
        });
    }

    private void setupInitialState() {
        formulaireBox.setDisable(true);
        modifierButton.setDisable(true);
        supprimerButton.setDisable(true);
        progressBar.setVisible(false);
        statusLabel.setText("Sélectionnez un type de référentiel");

        // Masquer initialement les champs de hiérarchie
        parentComboBox.setVisible(false);
        parentLabel.setVisible(false);
    }

    private void changerTypeReferentiel(String nouveauType) {
        typeActuel = nouveauType;

        // Configuration spécifique selon le type
        configurerPourType(nouveauType);

        // Chargement des données
        chargerDonnees(nouveauType);

        statusLabel.setText("Type sélectionné: " + nouveauType);
    }

    private void configurerPourType(String type) {
        // Configuration des labels et visibilité selon le type
        switch (type) {
            case "Services":
                parentLabel.setText("Centre:");
                parentLabel.setVisible(true);
                parentComboBox.setVisible(true);
                chargerCentresPourServices();
                break;
            case "Bureaux":
                parentLabel.setText("Service:");
                parentLabel.setVisible(true);
                parentComboBox.setVisible(true);
                chargerServicesPourBureaux();
                break;
            default:
                parentLabel.setVisible(false);
                parentComboBox.setVisible(false);
                break;
        }
    }

    private void chargerDonnees(String type) {
        Task<List<Object>> task = new Task<List<Object>>() {
            @Override
            protected List<Object> call() throws Exception {
                updateMessage("Chargement des " + type.toLowerCase() + "...");

                switch (type) {
                    case "Services":
                        return (List<Object>) (List<?>) serviceDAO.findAll();
                    case "Bureaux":
                        return (List<Object>) (List<?>) bureauDAO.findAll();
                    case "Centres":
                        return (List<Object>) (List<?>) centreDAO.findAll();
                    case "Contraventions":
                        return (List<Object>) (List<?>) contraventionDAO.findAll();
                    case "Banques":
                        return (List<Object>) (List<?>) banqueDAO.findAll();
                    default:
                        return FXCollections.emptyObservableList();
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<Object> donnees = task.getValue();
            donneesOriginales = FXCollections.observableArrayList(donnees);
            donneesFiltrees = FXCollections.observableArrayList(donnees);
            referentielTableView.setItems(donneesFiltrees);

            statusLabel.setText(String.format("%d %s chargé(s)", donnees.size(), type.toLowerCase()));
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            Throwable exception = task.getException();
            logger.error("Erreur lors du chargement des " + type, exception);
            AlertUtil.showErrorAlert("Erreur", "Chargement impossible",
                    "Impossible de charger les " + type.toLowerCase() + ": " + exception.getMessage());
            progressBar.setVisible(false);
        });

        progressBar.setVisible(true);
        statusLabel.textProperty().bind(task.messageProperty());

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void chargerCentresPourServices() {
        try {
            List<Centre> centres = centreDAO.findAll();
            parentComboBox.setItems(FXCollections.observableArrayList((List<Object>)(List<?>)centres));
        } catch (Exception e) {
            logger.error("Erreur lors du chargement des centres", e);
        }
    }

    private void chargerServicesPourBureaux() {
        try {
            List<Service> services = serviceDAO.findAll();
            parentComboBox.setItems(FXCollections.observableArrayList((List<Object>)(List<?>)services));
        } catch (Exception e) {
            logger.error("Erreur lors du chargement des services", e);
        }
    }

    @FXML
    private void creerNouvelElement() {
        if (typeActuel.isEmpty()) {
            AlertUtil.showWarningAlert("Attention", "Type non sélectionné",
                    "Veuillez d'abord sélectionner un type de référentiel.");
            return;
        }

        elementEnCours = creerNouvelObjet(typeActuel);
        modeCreation = true;

        afficherFormulaireEdition();
        viderFormulaire();

        codeField.requestFocus();
        statusLabel.setText("Création d'un nouveau " + typeActuel.substring(0, typeActuel.length()-1).toLowerCase());
    }

    @FXML
    private void modifierElementSelectionne() {
        Object selected = referentielTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showWarningAlert("Attention", "Aucune sélection",
                    "Veuillez sélectionner un élément à modifier.");
            return;
        }

        elementEnCours = selected;
        modeCreation = false;

        afficherFormulaireEdition();
        remplirFormulaire(selected);

        statusLabel.setText("Modification de: " + getLibelle(selected));
    }

    @FXML
    private void supprimerElementSelectionne() {
        Object selected = referentielTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Optional<ButtonType> result = AlertUtil.showConfirmationAlert(
                "Confirmation",
                "Supprimer l'élément",
                "Êtes-vous sûr de vouloir supprimer \"" + getLibelle(selected) + "\" ?\n" +
                        "Cette action peut affecter les données liées."
        );

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                supprimerObjet(selected);
                donneesOriginales.remove(selected);
                appliquerFiltres();

                statusLabel.setText("Élément supprimé: " + getLibelle(selected));
                AlertUtil.showInfoAlert("Succès", "Suppression réussie",
                        "L'élément a été supprimé avec succès.");

            } catch (Exception e) {
                logger.error("Erreur lors de la suppression", e);
                AlertUtil.showErrorAlert("Erreur", "Suppression échouée",
                        "Impossible de supprimer l'élément: " + e.getMessage());
            }
        }
    }

    @FXML
    private void enregistrerElement() {
        try {
            if (!validerSaisie()) {
                return;
            }

            // Remplissage des données communes
            setCode(elementEnCours, codeField.getText().trim().toUpperCase());
            setLibelle(elementEnCours, libelleField.getText().trim());
            setDescription(elementEnCours, descriptionTextArea.getText().trim());
            setActif(elementEnCours, actifCheckBox.isSelected());

            // Remplissage des données spécifiques
            if (parentComboBox.isVisible() && parentComboBox.getValue() != null) {
                setParent(elementEnCours, parentComboBox.getValue());
            }

            if (modeCreation) {
                sauvegarderNouvelObjet(elementEnCours);
                donneesOriginales.add(elementEnCours);
                statusLabel.setText("Élément créé: " + getLibelle(elementEnCours));
                AlertUtil.showInfoAlert("Succès", "Création réussie",
                        "L'élément a été créé avec succès.");
            } else {
                mettreAJourObjet(elementEnCours);
                statusLabel.setText("Élément modifié: " + getLibelle(elementEnCours));
                AlertUtil.showInfoAlert("Succès", "Modification réussie",
                        "L'élément a été modifié avec succès.");
            }

            appliquerFiltres();
            masquerFormulaireEdition();

        } catch (Exception e) {
            logger.error("Erreur lors de l'enregistrement", e);
            AlertUtil.showErrorAlert("Erreur", "Enregistrement échoué",
                    "Impossible d'enregistrer l'élément: " + e.getMessage());
        }
    }

    @FXML
    private void annulerEdition() {
        masquerFormulaireEdition();
        statusLabel.setText("Édition annulée");
    }

    @FXML
    private void actualiserDonnees() {
        if (!typeActuel.isEmpty()) {
            chargerDonnees(typeActuel);
        }
    }

    private void appliquerFiltres() {
        if (donneesOriginales == null) {
            return;
        }

        String recherche = rechercheField.getText().toLowerCase().trim();
        String filtreStatut = filtreStatutComboBox.getValue();

        donneesFiltrees.clear();

        donneesOriginales.stream()
                .filter(item -> {
                    // Filtre de recherche
                    if (!recherche.isEmpty()) {
                        String searchText = (getCode(item) + " " + getLibelle(item) + " " + getDescription(item)).toLowerCase();
                        if (!searchText.contains(recherche)) {
                            return false;
                        }
                    }

                    // Filtre statut
                    if (!"Tous les statuts".equals(filtreStatut)) {
                        boolean estActif = "Actif".equals(filtreStatut);
                        if (isActif(item) != estActif) {
                            return false;
                        }
                    }

                    return true;
                })
                .forEach(donneesFiltrees::add);

        if (donneesOriginales.size() > 0) {
            statusLabel.setText(String.format("Affichage: %d/%d %s",
                    donneesFiltrees.size(), donneesOriginales.size(), typeActuel.toLowerCase()));
        }
    }

    // ==================== MÉTHODES GÉNÉRIQUES POUR TOUS LES TYPES ====================

    private String getCode(Object item) {
        if (item instanceof Service) return ((Service) item).getCode();
        if (item instanceof Bureau) return ((Bureau) item).getCode();
        if (item instanceof Centre) return ((Centre) item).getCode();
        if (item instanceof Contravention) return ((Contravention) item).getCode();
        if (item instanceof Banque) return ((Banque) item).getCode();
        return "";
    }

    private String getLibelle(Object item) {
        if (item instanceof Service) return ((Service) item).getLibelle();
        if (item instanceof Bureau) return ((Bureau) item).getLibelle();
        if (item instanceof Centre) return ((Centre) item).getLibelle();
        if (item instanceof Contravention) return ((Contravention) item).getLibelle();
        if (item instanceof Banque) return ((Banque) item).getLibelle();
        return "";
    }

    private String getDescription(Object item) {
        if (item instanceof Service) return ((Service) item).getDescription();
        if (item instanceof Bureau) return ((Bureau) item).getDescription();
        if (item instanceof Centre) return ((Centre) item).getDescription();
        if (item instanceof Contravention) return ((Contravention) item).getDescription();
        if (item instanceof Banque) return ((Banque) item).getDescription();
        return "";
    }

    private boolean isActif(Object item) {
        if (item instanceof Service) return ((Service) item).isActif();
        if (item instanceof Bureau) return ((Bureau) item).isActif();
        if (item instanceof Centre) return ((Centre) item).isActif();
        if (item instanceof Contravention) return ((Contravention) item).isActif();
        if (item instanceof Banque) return ((Banque) item).isActif();
        return true;
    }

    private void setCode(Object item, String code) {
        if (item instanceof Service) ((Service) item).setCode(code);
        if (item instanceof Bureau) ((Bureau) item).setCode(code);
        if (item instanceof Centre) ((Centre) item).setCode(code);
        if (item instanceof Contravention) ((Contravention) item).setCode(code);
        if (item instanceof Banque) ((Banque) item).setCode(code);
    }

    private void setLibelle(Object item, String libelle) {
        if (item instanceof Service) ((Service) item).setLibelle(libelle);
        if (item instanceof Bureau) ((Bureau) item).setLibelle(libelle);
        if (item instanceof Centre) ((Centre) item).setLibelle(libelle);
        if (item instanceof Contravention) ((Contravention) item).setLibelle(libelle);
        if (item instanceof Banque) ((Banque) item).setLibelle(libelle);
    }

    private void setDescription(Object item, String description) {
        if (item instanceof Service) ((Service) item).setDescription(description);
        if (item instanceof Bureau) ((Bureau) item).setDescription(description);
        if (item instanceof Centre) ((Centre) item).setDescription(description);
        if (item instanceof Contravention) ((Contravention) item).setDescription(description);
        if (item instanceof Banque) ((Banque) item).setDescription(description);
    }

    private void setActif(Object item, boolean actif) {
        if (item instanceof Service) ((Service) item).setActif(actif);
        if (item instanceof Bureau) ((Bureau) item).setActif(actif);
        if (item instanceof Centre) ((Centre) item).setActif(actif);
        if (item instanceof Contravention) ((Contravention) item).setActif(actif);
        if (item instanceof Banque) ((Banque) item).setActif(actif);
    }

    private void setParent(Object item, Object parent) {
        if (item instanceof Service && parent instanceof Centre) {
            ((Service) item).setCentre((Centre) parent);
        }
        if (item instanceof Bureau && parent instanceof Service) {
            ((Bureau) item).setService((Service) parent);
        }
    }

    private Object creerNouvelObjet(String type) {
        switch (type) {
            case "Services": return new Service();
            case "Bureaux": return new Bureau();
            case "Centres": return new Centre();
            case "Contraventions": return new Contravention();
            case "Banques": return new Banque();
            default: return null;
        }
    }

    private void sauvegarderNouvelObjet(Object item) throws Exception {
        if (item instanceof Service) serviceDAO.save((Service) item);
        else if (item instanceof Bureau) bureauDAO.save((Bureau) item);
        else if (item instanceof Centre) centreDAO.save((Centre) item);
        else if (item instanceof Contravention) contraventionDAO.save((Contravention) item);
        else if (item instanceof Banque) banqueDAO.save((Banque) item);
    }

    private void mettreAJourObjet(Object item) throws Exception {
        if (item instanceof Service) serviceDAO.update((Service) item);
        else if (item instanceof Bureau) bureauDAO.update((Bureau) item);
        else if (item instanceof Centre) centreDAO.update((Centre) item);
        else if (item instanceof Contravention) contraventionDAO.update((Contravention) item);
        else if (item instanceof Banque) banqueDAO.update((Banque) item);
    }

    private void supprimerObjet(Object item) throws Exception {
        if (item instanceof Service) serviceDAO.delete(((Service) item).getId());
        else if (item instanceof Bureau) bureauDAO.delete(((Bureau) item).getId());
        else if (item instanceof Centre) centreDAO.delete(((Centre) item).getId());
        else if (item instanceof Contravention) contraventionDAO.delete(((Contravention) item).getId());
        else if (item instanceof Banque) banqueDAO.delete(((Banque) item).getId());
    }

    // ==================== GESTION DU FORMULAIRE ====================

    private void afficherFormulaireEdition() {
        formulaireBox.setDisable(false);
        nouveauButton.setDisable(true);
        modifierButton.setDisable(true);
        supprimerButton.setDisable(true);
        actualiserButton.setDisable(true);
        referentielTableView.setDisable(true);
    }

    private void masquerFormulaireEdition() {
        formulaireBox.setDisable(true);
        nouveauButton.setDisable(false);
        modifierButton.setDisable(true);
        supprimerButton.setDisable(true);
        actualiserButton.setDisable(false);
        referentielTableView.setDisable(false);

        viderFormulaire();
        elementEnCours = null;
    }

    private void viderFormulaire() {
        codeField.clear();
        libelleField.clear();
        descriptionTextArea.clear();
        actifCheckBox.setSelected(true);
        if (parentComboBox.isVisible()) {
            parentComboBox.setValue(null);
        }

        // Reset des styles
        codeField.setStyle("");
        libelleField.setStyle("");
    }

    private void remplirFormulaire(Object item) {
        codeField.setText(getCode(item));
        libelleField.setText(getLibelle(item));
        descriptionTextArea.setText(getDescription(item));
        actifCheckBox.setSelected(isActif(item));

        // Remplissage du parent si applicable
        if (parentComboBox.isVisible()) {
            if (item instanceof Service) {
                Service service = (Service) item;
                if (service.getCentre() != null) {
                    parentComboBox.setValue(service.getCentre());
                }
            } else if (item instanceof Bureau) {
                Bureau bureau = (Bureau) item;
                if (bureau.getService() != null) {
                    parentComboBox.setValue(bureau.getService());
                }
            }
        }
    }

    private boolean validerSaisie() {
        StringBuilder erreurs = new StringBuilder();

        // Code obligatoire
        if (codeField.getText().trim().isEmpty()) {
            erreurs.append("- Le code est obligatoire\n");
        } else if (codeField.getText().trim().length() < 2) {
            erreurs.append("- Le code doit contenir au moins 2 caractères\n");
        }

        // Libellé obligatoire
        if (libelleField.getText().trim().isEmpty()) {
            erreurs.append("- Le libellé est obligatoire\n");
        }

        // Vérification unicité du code (simplifiée)
        if (modeCreation) {
            // TODO: Vérifier l'unicité selon le type
        }

        // Parent obligatoire pour certains types
        if (parentComboBox.isVisible() && parentComboBox.getValue() == null) {
            String parentType = parentLabel.getText().replace(":", "");
            erreurs.append("- Le " + parentType.toLowerCase() + " est obligatoire\n");
        }

        if (erreurs.length() > 0) {
            AlertUtil.showWarningAlert("Validation", "Erreurs de saisie", erreurs.toString());
            return false;
        }

        return true;
    }

    private void validerFormulaire() {
        boolean isValid = !codeField.getText().trim().isEmpty() &&
                !libelleField.getText().trim().isEmpty() &&
                (!parentComboBox.isVisible() || parentComboBox.getValue() != null);

        enregistrerButton.setDisable(!isValid);
    }

    /**
     * Méthodes d'accès externe
     */
    public void selectionnerType(String type) {
        typeReferentielComboBox.setValue(type);
    }

    public void actualiserTout() {
        if (!typeActuel.isEmpty()) {
            chargerDonnees(typeActuel);
        }
    }
}