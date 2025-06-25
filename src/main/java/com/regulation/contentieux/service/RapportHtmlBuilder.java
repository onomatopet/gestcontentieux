package com.regulation.contentieux.service;

import com.regulation.contentieux.model.enums.TypeRapport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder pour la génération HTML des rapports via template engine
 * Utilise les données DTO existantes et les templates HTML
 *
 * @author Équipe Contentieux
 * @since 1.0.0
 */
public class RapportHtmlBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RapportHtmlBuilder.class);

    private final RapportService rapportService;
    private final TemplateEngine templateEngine;

    /**
     * Constructeur
     * @param rapportService service pour récupérer les données
     */
    public RapportHtmlBuilder(RapportService rapportService) {
        this.rapportService = rapportService;
        this.templateEngine = new SimpleTemplateEngine();
    }

    /**
     * Génère le HTML pour un type de rapport donné
     *
     * @param type type de rapport à générer
     * @param debut date de début
     * @param fin date de fin
     * @return HTML généré
     */
    public String buildHtml(TypeRapport type, LocalDate debut, LocalDate fin) {
        try {
            logger.info("🎨 Génération HTML pour {} (période {} - {})", type.getLibelle(), debut, fin);

            // 1. Récupérer les données via les méthodes existantes
            Object data = getDataForType(type, debut, fin);

            // 2. Créer le contexte pour le template
            Map<String, Object> context = createContext(data, type, debut, fin);

            // 3. Générer le HTML via le template
            String templateName = getTemplateNameForType(type);
            String html = templateEngine.render(templateName, context);

            logger.info("✅ HTML généré avec succès pour {} ({} caractères)", type.getLibelle(), html.length());
            return html;

        } catch (Exception e) {
            logger.error("❌ Erreur lors de la génération HTML pour {}", type.getLibelle(), e);
            return generateErrorHtml(type, debut, fin, e);
        }
    }

    /**
     * Récupère les données selon le type de rapport
     */
    private Object getDataForType(TypeRapport type, LocalDate debut, LocalDate fin) {
        return switch(type) {
            case ETAT_REPARTITION_AFFAIRES -> {
                logger.debug("📊 Récupération données Template 1 - État répartition affaires");
                yield rapportService.genererDonneesEtatRepartitionAffaires(debut, fin);
            }
            case ETAT_MANDATEMENT -> {
                logger.debug("📊 Récupération données Template 2 - État mandatement");
                yield rapportService.genererDonneesEtatMandatement(debut, fin);
            }
            case CENTRE_REPARTITION -> {
                logger.debug("📊 Récupération données Template 3 - Centre répartition");
                yield rapportService.genererDonneesCentreRepartition(debut, fin);
            }
            case INDICATEURS_REELS -> {
                logger.debug("📊 Récupération données Template 4 - Indicateurs réels");
                yield rapportService.genererDonneesIndicateursReels(debut, fin);
            }
            case REPARTITION_PRODUIT -> {
                logger.debug("📊 Récupération données Template 5 - Répartition produit");
                yield rapportService.genererDonneesRepartitionProduit(debut, fin);
            }
            case ETAT_CUMULE_AGENT -> {
                logger.debug("📊 Récupération données Template 6 - Cumulé par agent");
                yield rapportService.genererDonneesEtatCumuleParAgent(debut, fin);
            }
            case TABLEAU_AMENDES_SERVICE -> {
                logger.debug("📊 Récupération données Template 7 - Amendes par services");
                yield rapportService.genererDonneesTableauAmendesParServices(debut, fin);
            }
            case MANDATEMENT_AGENTS -> {
                logger.debug("📊 Récupération données Template 8 - Mandatement agents");
                yield rapportService.genererDonneesMandatementAgents(debut, fin);
            }
        };
    }

    /**
     * Crée le contexte pour le template avec toutes les variables nécessaires
     */
    private Map<String, Object> createContext(Object data, TypeRapport type, LocalDate debut, LocalDate fin) {
        Map<String, Object> context = new HashMap<>();

        // Informations générales du rapport
        context.put("titre", type.getLibelle());
        context.put("dateDebut", debut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.put("dateFin", fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.put("dateGeneration", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.put("templateName", type.name());
        context.put("numeroTemplate", type.getNumeroTemplate());

        // Données du rapport
        context.put("rapport", data);

        // Ajouter les propriétés spécifiques selon le type de données
        addTypeSpecificProperties(context, data, type);

        logger.debug("🔧 Contexte créé avec {} variables pour {}", context.size(), type.getLibelle());
        return context;
    }

    /**
     * Ajoute les propriétés spécifiques selon le type de DTO
     */
    private void addTypeSpecificProperties(Map<String, Object> context, Object data, TypeRapport type) {
        if (data instanceof RapportService.RapportRepartitionDTO rapport) {
            // Template 1 - État de répartition des affaires
            context.put("affaires", rapport.getAffaires());
            context.put("nombreAffaires", rapport.getNombreAffaires());
            context.put("totalEncaisse", rapport.getTotalEncaisse());
            context.put("totalProduitDisponible", rapport.getTotalProduitDisponible());
            context.put("totalPartIndicateur", rapport.getTotalPartIndicateur());
            context.put("totalPartFLCF", rapport.getTotalPartFLCF());
            context.put("totalPartTresor", rapport.getTotalPartTresor());
            context.put("totalPartAyantsDroits", rapport.getTotalPartAyantsDroits());

        } else if (data instanceof RapportService.EtatMandatementDTO mandatement) {
            // Templates 2 et 8 - États de mandatement
            context.put("mandatements", mandatement.getMandatements());
            context.put("typeEtat", mandatement.getTypeEtat());
            context.put("totalMontantMandatement", mandatement.getTotalMontantMandatement());
            context.put("nombreMandatements", mandatement.getMandatements() != null ? mandatement.getMandatements().size() : 0);

            // Totaux spécifiques Template 2
            context.put("totalProduitNet", mandatement.getTotalProduitNet());
            context.put("totalChefs", mandatement.getTotalChefs());
            context.put("totalSaisissants", mandatement.getTotalSaisissants());
            context.put("totalMutuelleNationale", mandatement.getTotalMutuelleNationale());
            context.put("totalMasseCommune", mandatement.getTotalMasseCommune());
            context.put("totalInteressement", mandatement.getTotalInteressement());

        } else if (data instanceof RapportService.CentreRepartitionDTO centres) {
            // Template 3 - Centre de répartition
            context.put("centres", centres.getCentres());
            context.put("nombreCentres", centres.getCentres() != null ? centres.getCentres().size() : 0);

            // Totaux spécifiques Template 3
            context.put("totalRepartitionBase", centres.getTotalRepartitionBase());
            context.put("totalRepartitionIndicateur", centres.getTotalRepartitionIndicateur());
            context.put("totalPartCentre", centres.getTotalPartCentre());
            context.put("totalGeneral", centres.getTotalGeneral());

        } else if (data instanceof RapportService.IndicateursReelsDTO indicateurs) {
            // Template 4 - Indicateurs réels
            context.put("indicateurs", indicateurs.getIndicateurs());
            context.put("services", indicateurs.getServicesData());
            context.put("nombreIndicateurs", indicateurs.getIndicateurs() != null ? indicateurs.getIndicateurs().size() : 0);

            // Totaux spécifiques Template 4
            context.put("totalEncaissement", indicateurs.getTotalEncaissement());
            context.put("totalPartIndicateur", indicateurs.getTotalPartIndicateur());

        } else if (data instanceof RapportService.RepartitionProduitDTO produit) {
            // Template 5 - Répartition du produit
            context.put("lignes", produit.getLignes());
            context.put("nombreLignes", produit.getLignes() != null ? produit.getLignes().size() : 0);

        } else if (data instanceof RapportService.EtatCumuleAgentDTO cumuleAgent) {
            // Template 6 - Cumulé par agent
            context.put("agents", cumuleAgent.getAgents());
            context.put("nombreAgents", cumuleAgent.getAgents() != null ? cumuleAgent.getAgents().size() : 0);

        } else if (data instanceof RapportService.TableauAmendesParServicesDTO amendes) {
            // Template 7 - Amendes par services
            context.put("services", amendes.getServices());
            context.put("nombreServices", amendes.getNombreServices());
            context.put("totalGeneral", amendes.getTotalGeneral());
            context.put("nombreTotalAffaires", amendes.getNombreTotalAffaires());
        }

        // Ajouter une variable de vérification
        context.put("hasData", data != null);
        context.put("dataType", data != null ? data.getClass().getSimpleName() : "null");
    }

    /**
     * Retourne le nom du template selon le type de rapport
     */
    private String getTemplateNameForType(TypeRapport type) {
        return switch(type) {
            case ETAT_REPARTITION_AFFAIRES -> "template1_repartition_affaires";
            case ETAT_MANDATEMENT -> "template2_mandatement";
            case CENTRE_REPARTITION -> "template3_centre_repartition";
            case INDICATEURS_REELS -> "template4_indicateurs_reels";
            case REPARTITION_PRODUIT -> "template5_repartition_produit";
            case ETAT_CUMULE_AGENT -> "template6_cumule_agent";
            case TABLEAU_AMENDES_SERVICE -> "template7_amendes_service";
            case MANDATEMENT_AGENTS -> "template8_mandatement_agents";
        };
    }

    /**
     * Génère un HTML d'erreur en cas de problème
     */
    private String generateErrorHtml(TypeRapport type, LocalDate debut, LocalDate fin, Exception error) {
        Map<String, Object> context = Map.of(
                "titre", "❌ Erreur - " + type.getLibelle(),
                "dateDebut", debut.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                "dateFin", fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                "dateGeneration", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                "erreur", error.getMessage(),
                "typeErreur", error.getClass().getSimpleName()
        );

        String errorTemplate = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>{{titre}}</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .header { text-align: center; margin-bottom: 30px; }
                    .error { 
                        background-color: #ffe6e6; 
                        border: 2px solid #ff9999; 
                        padding: 20px; 
                        border-radius: 5px;
                        margin: 20px 0;
                    }
                    .error h3 { color: #cc0000; margin-top: 0; }
                    .footer { margin-top: 30px; font-size: 12px; text-align: center; color: #666; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>{{titre}}</h1>
                    <p><strong>Période :</strong> {{dateDebut}} au {{dateFin}}</p>
                </div>
                <div class="error">
                    <h3>🚨 Erreur lors de la génération du rapport</h3>
                    <p><strong>Type d'erreur :</strong> {{typeErreur}}</p>
                    <p><strong>Message :</strong> {{erreur}}</p>
                    <p><em>Veuillez vérifier les paramètres et réessayer, ou contacter l'administrateur.</em></p>
                </div>
                <div class="footer">
                    Tentative de génération le {{dateGeneration}}
                </div>
            </body>
            </html>
            """;

        return templateEngine.renderFromString(errorTemplate, context);
    }
}