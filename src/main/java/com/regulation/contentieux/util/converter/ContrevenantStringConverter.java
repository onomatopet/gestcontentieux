package com.regulation.contentieux.util.converter;

import com.regulation.contentieux.model.Contrevenant;
import javafx.util.StringConverter;

/**
 * Convertisseur pour l'affichage des contrevenants dans les ComboBox
 * Suit le même pattern que les autres converters existants dans l'application
 */
public class ContrevenantStringConverter extends StringConverter<Contrevenant> {

    /**
     * Convertit un Contrevenant en String pour l'affichage
     * Format cohérent avec les autres converters de l'app
     */
    @Override
    public String toString(Contrevenant contrevenant) {
        if (contrevenant == null) {
            return "";
        }

        // Format similaire aux autres entités : "Code - Nom Description"
        StringBuilder sb = new StringBuilder();

        // Code si disponible
        if (contrevenant.getCode() != null && !contrevenant.getCode().trim().isEmpty()) {
            sb.append(contrevenant.getCode()).append(" - ");
        }

        // Nom complet
        String nomComplet = "";
        if (contrevenant.getNom() != null && !contrevenant.getNom().trim().isEmpty()) {
            nomComplet = contrevenant.getNom().toUpperCase();
        }
        if (contrevenant.getPrenom() != null && !contrevenant.getPrenom().trim().isEmpty()) {
            if (!nomComplet.isEmpty()) nomComplet += " ";
            nomComplet += contrevenant.getPrenom();
        }

        if (!nomComplet.isEmpty()) {
            sb.append(nomComplet);
        } else {
            // Fallback si pas de nom
            sb.append("Contrevenant #").append(contrevenant.getId() != null ? contrevenant.getId() : "?");
        }

        // Type de personne si pertinent
        if (contrevenant.getTypePersonne() != null) {
            sb.append(" (").append(contrevenant.getTypePersonne()).append(")");
        }

        return sb.toString();
    }

    /**
     * Non utilisé pour les ComboBox en lecture seule
     */
    @Override
    public Contrevenant fromString(String string) {
        return null;
    }
}