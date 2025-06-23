package com.regulation.contentieux.controller;

import com.regulation.contentieux.model.*;
import com.regulation.contentieux.dao.*;
import com.regulation.contentieux.service.ValidationService;
import com.regulation.contentieux.util.AlertUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la gestion des données de référence - VERSION HARMONISÉE
 * Gestion des Services, Bureaux, Centres, Contraventions, Banques
 * COMPATIBLE AVEC TOUS LES MODÈLES HARMONISÉS
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
    @FXML private ComboBox<Object> parentComboBox; // Pour les hiérarchies
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

    // DAOs pour chaque type - HARMONISÉS
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
        this.validationService = ValidationService.getInstance();

        // Initialisation des DAOs harmonisés
        this.serviceDAO = new ServiceDAO();
        this.bureauDAO = new BureauDAO();
        this.centreDAO = new CentreDAO();
        this.contraventionDAO = new ContraventionDAO();
        this.banqueDAO = new BanqueDAO();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            setupComboBoxes();
            setupTableColumns();
            setupEventHandlers();
            setupFormValidation();
            setupInitialState();

            logger.info("✅ ReferentielController harmonisé initialisé avec succès");
        } catch (Exception e) {
            logger.error("❌ Erreur lors de l'initialisation de ReferentielController", e);
        }
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

        // StringConverter pour le type de référentiel
        typeReferentielComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String string) {
                return string != null ? string : "";
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });

        // Filtres de statut
        filtreStatutComboBox.setItems(FXCollections.observableArrayList(
                "Tous les statuts", "Actif", "Inactif"
        ));
        filtreStatutComboBox.setValue("Tous les statuts");

        // StringConverter pour le ComboBox parent générique
        parentComboBox.setConverter(new StringConverter<Object>() {
            @Override
            public String toString(Object object) {
                if (object == null) return "";
                if (object instanceof Service) return ((Service) object).toString();
                if (object instanceof Centre) return ((Centre) object).toString();
                return object.toString();
            }

            @Override
            public Object fromString(String string) {
                return null; // Pas utilisé
            }
        });
    }

    private void setupTableColumns() {
        // Configuration des colonnes avec les méthodes harmonisées
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
            String description = getDescription(item);
            return new SimpleStringProperty(description != null ? description : "");
        });

        statutColumn.setCellValueFactory(cellData -> {
            Object item = cellData.getValue();
            return new SimpleStringProperty(isActif(item) ? "Actif" : "Inactif");
        });

        // Configuration de la table
        referentielTableView.setItems(donneesFiltrees = FXCollections.observableArrayList());
        referentielTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void setupEventHandlers() {
        // Changement de type de référentiel
        typeReferentielComboBox.setOnAction(e -> {
            String type = typeReferentielComboBox.getValue();
            if (type != null) {
                chargerDonnees(type);
            }
        });

        // Sélection dans la table
        referentielTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        afficherElementSelectionne(newSelection);
                        activerBoutonsSelection(true);
                    } else {
                        masquerFormulaire();
                        activerBoutonsSelection(false);
                    }
                });

        // Boutons d'action
        nouveauButton.setOnAction(e -> creerNouvelElement());
        modifierButton.setOnAction(e -> modifierElementSelectionne());
        supprimerButton.setOnAction(e -> supprimerElementSelectionne());
        enregistrerButton.setOnAction(e -> enregistrerElement());
        annulerButton.setOnAction(e -> annulerFormulaire());
        actualiserButton.setOnAction(e -> actualiserDonnees());

        // Recherche et filtres
        rechercheField.textProperty().addListener((obs, oldVal, newVal) -> appliquerFiltres());
        filtreStatutComboBox.setOnAction(e -> appliquerFiltres());
    }

    private void setupFormValidation() {
        // Validation en temps réel
        codeField.textProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
        libelleField.textProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
        parentComboBox.valueProperty().addListener((obs, oldVal, newVal) -> validerFormulaire());
    }

    private void setupInitialState() {
        donneesOriginales = FXCollections.observableArrayList();
        donneesFiltrees = FXCollections.observableArrayList();

        masquerFormulaire();
        activerBoutonsSelection(false);
        progressBar.setVisible(false);

        // Sélectionner le premier type par défaut
        if (!typeReferentielComboBox.getItems().isEmpty()) {
            typeReferentielComboBox.setValue("Services");
        }
    }

    // ==================== CHARGEMENT DES DONNÉES ====================

    private void chargerDonnees(String type) {
        typeActuel = type;

        Task<List<Object>> task = new Task<List<Object>>() {
            @Override
            protected List<Object> call() throws Exception {
                Thread.sleep(100); // Petite pause pour l'UX
                return chargerDonneesParType(type);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    List<Object> donnees = getValue();
                    donneesOriginales.setAll(donnees);
                    appliquerFiltres();
                    configurerFormulairePourType(type);
                    progressBar.setVisible(false);
                    statusLabel.setText(String.format("%d %s chargé(s)",
                            donnees.size(), type.toLowerCase()));
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("Erreur lors du chargement");
                    AlertUtil.showErrorAlert("Erreur", "Chargement",
                            "Impossible de charger les données: " + getException().getMessage());
                });
            }
        };

        progressBar.setVisible(true);
        new Thread(task).start();
    }

    @SuppressWarnings("unchecked")
    private List<Object> chargerDonneesParType(String type) {
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
                return FXCollections.observableArrayList();
        }
    }

    private void configurerFormulairePourType(String type) {
        // Configuration spécifique selon le type
        switch (type) {
            case "Services":
                parentLabel.setText("Centre:");
                parentComboBox.setVisible(true);
                parentLabel.setVisible(true);
                chargerParentsServices();
                break;
            case "Bureaux":
                parentLabel.setText("Service:");
                parentComboBox.setVisible(true);
                parentLabel.setVisible(true);
                chargerParentsBureaux();
                break;
            default:
                parentComboBox.setVisible(false);
                parentLabel.setVisible(false);
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private void chargerParentsServices() {
        List<Centre> centres = centreDAO.findAllActive();
        parentComboBox.setItems((ObservableList<Object>) (ObservableList<?>)
                FXCollections.observableArrayList(centres));
    }

    @SuppressWarnings("unchecked")
    private void chargerParentsBureaux() {
        List<Service> services = serviceDAO.findAllActive();
        parentComboBox.setItems((ObservableList<Object>) (ObservableList<?>)
                FXCollections.observableArrayList(services));
    }

    // ==================== GESTION DES FILTRES ====================

    private void appliquerFiltres() {
        if (donneesOriginales == null) return;

        String recherche = rechercheField.getText().trim().toLowerCase();
        String filtreStatut = filtreStatutComboBox.getValue();

        donneesFiltrees.clear();

        donneesOriginales.stream()
                .filter(item -> {
                    // Filtre par recherche
                    if (!recherche.isEmpty()) {
                        String code = getCode(item).toLowerCase();
                        String libelle = getLibelle(item).toLowerCase();
                        if (!code.contains(recherche) && !libelle.contains(recherche)) {
                            return false;
                        }
                    }

                    // Filtre par statut
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

    // ==================== GESTION DU FORMULAIRE ====================

    private void afficherElementSelectionne(Object element) {
        elementEnCours = element;
        modeCreation = false;

        // Remplir les champs
        codeField.setText(getCode(element));
        libelleField.setText(getLibelle(element));
        descriptionTextArea.setText(getDescription(element));
        actifCheckBox.setSelected(isActif(element));

        // Parent spécifique
        if (element instanceof Service) {
            Service service = (Service) element;
            parentComboBox.setValue(service.getCentre());
        } else if (element instanceof Bureau) {
            Bureau bureau = (Bureau) element;
            parentComboBox.setValue(bureau.getService());
        }

        afficherFormulaire(false);
    }

    private void afficherFormulaire(boolean lecture) {
        formulaireBox.setVisible(true);
        formulaireBox.setDisable(lecture);
        enregistrerButton.setVisible(!lecture);
        annulerButton.setVisible(!lecture);
    }

    private void masquerFormulaire() {
        formulaireBox.setVisible(false);
        viderFormulaire();
    }

    private void viderFormulaire() {
        codeField.clear();
        libelleField.clear();
        descriptionTextArea.clear();
        actifCheckBox.setSelected(true);
        parentComboBox.setValue(null);
        elementEnCours = null;
    }

    private void activerBoutonsSelection(boolean activer) {
        modifierButton.setDisable(!activer);
        supprimerButton.setDisable(!activer);
    }

    // ==================== ACTIONS ====================

    private void creerNouvelElement() {
        viderFormulaire();
        modeCreation = true;
        elementEnCours = creerNouvelObjet(typeActuel);
        afficherFormulaire(false);

        // Focus sur le code
        Platform.runLater(() -> codeField.requestFocus());
    }

    private void modifierElementSelectionne() {
        if (elementEnCours != null) {
            modeCreation = false;
            afficherFormulaire(false);
        }
    }

    private void supprimerElementSelectionne() {
        if (elementEnCours == null) return;

        boolean confirme = AlertUtil.showConfirmationAlert("Confirmation",
                "Supprimer l'élément",
                String.format("Êtes-vous sûr de vouloir supprimer %s - %s ?",
                        getCode(elementEnCours), getLibelle(elementEnCours)));

        if (confirme) {
            try {
                supprimerObjet(elementEnCours);
                masquerFormulaire();
                actualiserDonnees();
                AlertUtil.showInfoAlert("Succès", "Suppression", "Élément supprimé avec succès");
            } catch (Exception e) {
                logger.error("Erreur lors de la suppression", e);
                AlertUtil.showErrorAlert("Erreur", "Suppression",
                        "Erreur lors de la suppression: " + e.getMessage());
            }
        }
    }

    private void enregistrerElement() {
        if (!validerSaisie()) return;

        try {
            // Remplir l'objet avec les données du formulaire
            setCode(elementEnCours, codeField.getText().trim());
            setLibelle(elementEnCours, libelleField.getText().trim());
            setDescription(elementEnCours, descriptionTextArea.getText().trim());
            setActif(elementEnCours, actifCheckBox.isSelected());

            // Parent si applicable
            if (parentComboBox.isVisible() && parentComboBox.getValue() != null) {
                setParent(elementEnCours, parentComboBox.getValue());
            }

            // Sauvegarde
            if (modeCreation) {
                sauvegarderNouvelObjet(elementEnCours);
            } else {
                mettreAJourObjet(elementEnCours);
            }

            masquerFormulaire();
            actualiserDonnees();
            AlertUtil.showInfoAlert("Succès", "Enregistrement",
                    (modeCreation ? "Création" : "Modification") + " réussie");

        } catch (Exception e) {
            logger.error("Erreur lors de l'enregistrement", e);
            AlertUtil.showErrorAlert("Erreur", "Enregistrement",
                    "Erreur lors de l'enregistrement: " + e.getMessage());
        }
    }

    private void annulerFormulaire() {
        masquerFormulaire();
        referentielTableView.getSelectionModel().clearSelection();
    }

    private void actualiserDonnees() {
        if (!typeActuel.isEmpty()) {
            chargerDonnees(typeActuel);
        }
    }

    // ==================== CRÉATION ET SAUVEGARDE D'OBJETS ====================

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
        if (item instanceof Service) serviceDAO.deleteById(((Service) item).getId());
        else if (item instanceof Bureau) bureauDAO.deleteById(((Bureau) item).getId());
        else if (item instanceof Centre) centreDAO.deleteById(((Centre) item).getId());
        else if (item instanceof Contravention) contraventionDAO.deleteById(((Contravention) item).getId());
        else if (item instanceof Banque) banqueDAO.deleteById(((Banque) item).getId());
    }

    // ==================== VALIDATION ====================

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
        } else if (libelleField.getText().trim().length() < 3) {
            erreurs.append("- Le libellé doit contenir au moins 3 caractères\n");
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

    // ==================== MÉTHODES D'ACCÈS EXTERNE ====================

    /**
     * Sélectionne un type de référentiel depuis l'extérieur
     */
    public void selectionnerType(String type) {
        typeReferentielComboBox.setValue(type);
    }

    /**
     * Actualise toutes les données
     */
    public void actualiserTout() {
        if (!typeActuel.isEmpty()) {
            chargerDonnees(typeActuel);
        }
    }
}