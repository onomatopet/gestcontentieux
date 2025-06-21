package com.regulation.contentieux.controller;

import com.regulation.contentieux.service.RapportService;
import com.regulation.contentieux.util.CurrencyFormatter;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Contrôleur pour l'affichage des rapports de répartition
 * Corrigé pour utiliser les API non dépréciées de Java 21
 */
public class RapportRepartitionController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(RapportRepartitionController.class);

    @FXML private Label periodeLabel;
    @FXML private Label totalEncaisseLabel;
    @FXML private Label partEtatLabel;
    @FXML private Label partCollectiviteLabel;

    @FXML private TableView<RapportService.AffaireRepartitionDTO> affairesTableView;
    @FXML private TableColumn<RapportService.AffaireRepartitionDTO, String> numeroAffaireColumn;
    @FXML private TableColumn<RapportService.AffaireRepartitionDTO, String> contrevenantColumn;
    @FXML private TableColumn<RapportService.AffaireRepartitionDTO, BigDecimal> montantTotalColumn;
    @FXML private TableColumn<RapportService.AffaireRepartitionDTO, BigDecimal> montantEncaisseColumn;
    @FXML private TableColumn<RapportService.AffaireRepartitionDTO, BigDecimal> partEtatColumn;
    @FXML private TableColumn<RapportService.AffaireRepartitionDTO, BigDecimal> partCollectiviteColumn;

    private RapportService.RapportRepartitionDTO rapportData;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeTable();
    }

    private void initializeTable() {
        // Configuration des colonnes
        numeroAffaireColumn.setCellValueFactory(new PropertyValueFactory<>("numeroAffaire"));
        contrevenantColumn.setCellValueFactory(new PropertyValueFactory<>("contrevenant"));
        montantTotalColumn.setCellValueFactory(new PropertyValueFactory<>("montantTotal"));
        montantEncaisseColumn.setCellValueFactory(new PropertyValueFactory<>("montantEncaisse"));
        partEtatColumn.setCellValueFactory(new PropertyValueFactory<>("partEtat"));
        partCollectiviteColumn.setCellValueFactory(new PropertyValueFactory<>("partCollectivite"));

        // Formatage des montants
        montantTotalColumn.setCellFactory(col -> new CurrencyTableCell<>());
        montantEncaisseColumn.setCellFactory(col -> new CurrencyTableCell<>());
        partEtatColumn.setCellFactory(col -> new CurrencyTableCell<>());
        partCollectiviteColumn.setCellFactory(col -> new CurrencyTableCell<>());
    }

    /**
     * Charge les données du rapport
     */
    public void setRapportData(RapportService.RapportRepartitionDTO rapport) {
        this.rapportData = rapport;
        displayRapportData();
    }

    private void displayRapportData() {
        if (rapportData == null) return;

        // Affichage des informations générales
        periodeLabel.setText(rapportData.getPeriodeLibelle());
        totalEncaisseLabel.setText(CurrencyFormatter.format(rapportData.getTotalEncaisse()));
        partEtatLabel.setText(CurrencyFormatter.format(rapportData.getTotalPartEtat()));
        partCollectiviteLabel.setText(CurrencyFormatter.format(rapportData.getTotalPartCollectivite()));

        // Chargement des affaires dans le tableau
        affairesTableView.getItems().clear();
        affairesTableView.getItems().addAll(rapportData.getAffaires());

        // Calcul et affichage des statistiques
        displayStatistics();
    }

    private void displayStatistics() {
        // Calcul des pourcentages avec RoundingMode au lieu de ROUND_HALF_UP déprécié
        BigDecimal totalEncaisse = rapportData.getTotalEncaisse();
        if (totalEncaisse.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentEtat = rapportData.getTotalPartEtat()
                    .multiply(new BigDecimal("100"))
                    .divide(totalEncaisse, 2, RoundingMode.HALF_UP);

            BigDecimal percentCollectivite = rapportData.getTotalPartCollectivite()
                    .multiply(new BigDecimal("100"))
                    .divide(totalEncaisse, 2, RoundingMode.HALF_UP);

            logger.info("Répartition: État {}%, Collectivité {}%",
                    percentEtat, percentCollectivite);
        }
    }

    /**
     * Calcule des statistiques supplémentaires
     */
    private void calculateAdditionalStats() {
        if (rapportData == null || rapportData.getAffaires().isEmpty()) {
            return;
        }

        // Montant moyen par affaire avec API non dépréciée
        BigDecimal totalMontant = rapportData.getAffaires().stream()
                .map(RapportService.AffaireRepartitionDTO::getMontantEncaisse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal nombreAffaires = new BigDecimal(rapportData.getAffaires().size());
        BigDecimal montantMoyen = totalMontant.divide(nombreAffaires, 2, RoundingMode.HALF_UP);

        logger.debug("Montant moyen par affaire: {}", CurrencyFormatter.format(montantMoyen));
    }

    /**
     * Cellule de tableau pour l'affichage des montants
     */
    private static class CurrencyTableCell<T> extends javafx.scene.control.TableCell<T, BigDecimal> {
        @Override
        protected void updateItem(BigDecimal item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(CurrencyFormatter.format(item));
            }
        }
    }

    /**
     * Export des données du rapport
     */
    @FXML
    private void exportReport() {
        logger.info("Export du rapport de répartition");
        // TODO: Implémenter l'export
    }

    /**
     * Impression du rapport
     */
    @FXML
    private void printReport() {
        logger.info("Impression du rapport de répartition");
        // TODO: Implémenter l'impression
    }
}