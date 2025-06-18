package com.regulation.contentieux.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utilitaire pour le formatage des montants en devise FCFA
 */
public class CurrencyFormatter {

    private static final String CURRENCY_SYMBOL = "FCFA";
    private static final Locale FRENCH_LOCALE = Locale.FRANCE;

    // Format avec symbole FCFA
    private static final DecimalFormat CURRENCY_FORMAT;

    // Format numérique simple
    private static final DecimalFormat NUMBER_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(FRENCH_LOCALE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');

        CURRENCY_FORMAT = new DecimalFormat("#,##0", symbols);
        NUMBER_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    /**
     * Formate un montant avec le symbole FCFA
     *
     * @param amount Le montant à formater
     * @return Le montant formaté avec FCFA (ex: "1 500 FCFA")
     */
    public static String format(Double amount) {
        if (amount == null) {
            return "0 " + CURRENCY_SYMBOL;
        }
        return CURRENCY_FORMAT.format(amount) + " " + CURRENCY_SYMBOL;
    }

    /**
     * Formate un montant avec le symbole FCFA
     *
     * @param amount Le montant à formater
     * @return Le montant formaté avec FCFA
     */
    public static String format(double amount) {
        return CURRENCY_FORMAT.format(amount) + " " + CURRENCY_SYMBOL;
    }

    /**
     * Formate un montant sans le symbole de devise
     *
     * @param amount Le montant à formater
     * @return Le montant formaté sans symbole (ex: "1 500")
     */
    public static String formatNumber(Double amount) {
        if (amount == null) {
            return "0";
        }
        return CURRENCY_FORMAT.format(amount);
    }

    /**
     * Formate un montant sans le symbole de devise
     *
     * @param amount Le montant à formater
     * @return Le montant formaté sans symbole
     */
    public static String formatNumber(double amount) {
        return CURRENCY_FORMAT.format(amount);
    }

    /**
     * Formate un montant avec décimales
     *
     * @param amount Le montant à formater
     * @return Le montant formaté avec décimales (ex: "1 500,75")
     */
    public static String formatWithDecimals(Double amount) {
        if (amount == null) {
            return "0,00";
        }
        return NUMBER_FORMAT.format(amount);
    }

    /**
     * Formate un montant avec décimales
     *
     * @param amount Le montant à formater
     * @return Le montant formaté avec décimales
     */
    public static String formatWithDecimals(double amount) {
        return NUMBER_FORMAT.format(amount);
    }

    /**
     * Formate un montant avec décimales et symbole FCFA
     *
     * @param amount Le montant à formater
     * @return Le montant formaté avec décimales et FCFA (ex: "1 500,75 FCFA")
     */
    public static String formatCurrencyWithDecimals(Double amount) {
        if (amount == null) {
            return "0,00 " + CURRENCY_SYMBOL;
        }
        return NUMBER_FORMAT.format(amount) + " " + CURRENCY_SYMBOL;
    }

    /**
     * Formate un montant avec décimales et symbole FCFA
     *
     * @param amount Le montant à formater
     * @return Le montant formaté avec décimales et FCFA
     */
    public static String formatCurrencyWithDecimals(double amount) {
        return NUMBER_FORMAT.format(amount) + " " + CURRENCY_SYMBOL;
    }

    /**
     * Parse une chaîne formatée vers un double
     *
     * @param formattedAmount La chaîne formatée (ex: "1 500" ou "1 500 FCFA")
     * @return Le montant en double
     * @throws NumberFormatException Si le format est invalide
     */
    public static double parse(String formattedAmount) throws NumberFormatException {
        if (formattedAmount == null || formattedAmount.trim().isEmpty()) {
            return 0.0;
        }

        // Nettoyer la chaîne
        String cleaned = formattedAmount.trim()
                .replace(CURRENCY_SYMBOL, "")
                .replace(" ", "")
                .trim();

        // Remplacer la virgule par un point pour le parsing
        cleaned = cleaned.replace(",", ".");

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Format de montant invalide: " + formattedAmount);
        }
    }

    /**
     * Vérifie si une chaîne représente un montant valide
     *
     * @param formattedAmount La chaîne à vérifier
     * @return true si le format est valide
     */
    public static boolean isValidAmount(String formattedAmount) {
        try {
            parse(formattedAmount);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Formate un montant pour l'affichage court (ex: "1,5K FCFA" pour 1500)
     *
     * @param amount Le montant à formater
     * @return Le montant formaté en format court
     */
    public static String formatShort(double amount) {
        if (amount < 1000) {
            return CURRENCY_FORMAT.format(amount) + " " + CURRENCY_SYMBOL;
        } else if (amount < 1000000) {
            return String.format("%.1fK %s", amount / 1000, CURRENCY_SYMBOL);
        } else if (amount < 1000000000) {
            return String.format("%.1fM %s", amount / 1000000, CURRENCY_SYMBOL);
        } else {
            return String.format("%.1fB %s", amount / 1000000000, CURRENCY_SYMBOL);
        }
    }

    /**
     * Convertit un montant vers le format d'affichage adapté
     *
     * @param amount Le montant
     * @param includeDecimals Inclure les décimales
     * @param includeCurrency Inclure le symbole de devise
     * @return Le montant formaté
     */
    public static String formatCustom(double amount, boolean includeDecimals, boolean includeCurrency) {
        String formatted;

        if (includeDecimals) {
            formatted = NUMBER_FORMAT.format(amount);
        } else {
            formatted = CURRENCY_FORMAT.format(amount);
        }

        if (includeCurrency) {
            formatted += " " + CURRENCY_SYMBOL;
        }

        return formatted;
    }

    /**
     * @return Le symbole de devise utilisé
     */
    public static String getCurrencySymbol() {
        return CURRENCY_SYMBOL;
    }

    /**
     * @return Le locale utilisé pour le formatage
     */
    public static Locale getLocale() {
        return FRENCH_LOCALE;
    }
}