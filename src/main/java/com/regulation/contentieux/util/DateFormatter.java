package com.regulation.contentieux.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Utilitaire pour le formatage des dates et heures
 */
public class DateFormatter {

    // Formatters principaux
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    public static final DateTimeFormatter DATETIME_SHORT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Formatters alternatifs
    public static final DateTimeFormatter DATE_LONG_FORMAT = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy", Locale.FRANCE);
    public static final DateTimeFormatter DATE_SHORT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");
    public static final DateTimeFormatter TIME_SHORT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Formatters ISO
    public static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter ISO_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Formatter pour les noms de fichiers
    public static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Formate une date au format standard (dd/MM/yyyy)
     * Méthode ajoutée pour compatibilité
     *
     * @param date La date à formater
     * @return La date formatée ou chaîne vide si null
     */
    public static String format(LocalDate date) {
        return formatDate(date);
    }

    /**
     * Formate une date au format standard (dd/MM/yyyy)
     *
     * @param date La date à formater
     * @return La date formatée ou chaîne vide si null
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMAT);
    }

    /**
     * Formate une heure au format standard (HH:mm:ss)
     *
     * @param time L'heure à formater
     * @return L'heure formatée ou chaîne vide si null
     */
    public static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMAT);
    }

    /**
     * Formate une date-heure au format standard (dd/MM/yyyy HH:mm:ss)
     *
     * @param dateTime La date-heure à formater
     * @return La date-heure formatée ou chaîne vide si null
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATETIME_FORMAT);
    }

    /**
     * Formate une date-heure au format court (dd/MM/yyyy HH:mm)
     *
     * @param dateTime La date-heure à formater
     * @return La date-heure formatée ou chaîne vide si null
     */
    public static String formatDateTimeShort(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATETIME_SHORT_FORMAT);
    }

    /**
     * Formate une date au format long (ex: "Vendredi 15 mars 2024")
     *
     * @param date La date à formater
     * @return La date formatée en format long ou chaîne vide si null
     */
    public static String formatDateLong(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_LONG_FORMAT);
    }

    /**
     * Formate une date au format court (dd/MM/yy)
     *
     * @param date La date à formater
     * @return La date formatée en format court ou chaîne vide si null
     */
    public static String formatDateShort(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_SHORT_FORMAT);
    }

    /**
     * Formate une heure au format court (HH:mm)
     *
     * @param time L'heure à formater
     * @return L'heure formatée en format court ou chaîne vide si null
     */
    public static String formatTimeShort(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_SHORT_FORMAT);
    }

    /**
     * Formate une date pour utilisation dans un nom de fichier (yyyy-MM-dd)
     * Méthode ajoutée pour compatibilité
     *
     * @param date La date à formater
     * @return La date formatée pour nom de fichier
     */
    public static String formatForFilename(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(FILENAME_DATE_FORMAT);
    }

    /**
     * Parse une date depuis une chaîne au format dd/MM/yyyy
     *
     * @param dateString La chaîne à parser
     * @return La date parsée
     * @throws DateTimeParseException Si le format est invalide
     */
    public static LocalDate parseDate(String dateString) throws DateTimeParseException {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateString.trim(), DATE_FORMAT);
    }

    /**
     * Parse une heure depuis une chaîne au format HH:mm:ss
     *
     * @param timeString La chaîne à parser
     * @return L'heure parsée
     * @throws DateTimeParseException Si le format est invalide
     */
    public static LocalTime parseTime(String timeString) throws DateTimeParseException {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }
        return LocalTime.parse(timeString.trim(), TIME_FORMAT);
    }

    /**
     * Parse une date-heure depuis une chaîne au format dd/MM/yyyy HH:mm:ss
     *
     * @param dateTimeString La chaîne à parser
     * @return La date-heure parsée
     * @throws DateTimeParseException Si le format est invalide
     */
    public static LocalDateTime parseDateTime(String dateTimeString) throws DateTimeParseException {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString.trim(), DATETIME_FORMAT);
    }

    /**
     * Vérifie si une chaîne représente une date valide
     *
     * @param dateString La chaîne à vérifier
     * @return true si le format est valide
     */
    public static boolean isValidDate(String dateString) {
        try {
            parseDate(dateString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Vérifie si une chaîne représente une heure valide
     *
     * @param timeString La chaîne à vérifier
     * @return true si le format est valide
     */
    public static boolean isValidTime(String timeString) {
        try {
            parseTime(timeString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Vérifie si une chaîne représente une date-heure valide
     *
     * @param dateTimeString La chaîne à vérifier
     * @return true si le format est valide
     */
    public static boolean isValidDateTime(String dateTimeString) {
        try {
            parseDateTime(dateTimeString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Calcule la différence en jours entre deux dates
     *
     * @param startDate Date de début
     * @param endDate Date de fin
     * @return Nombre de jours entre les deux dates
     */
    public static long daysBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Vérifie si une date est dans le passé
     *
     * @param date La date à vérifier
     * @return true si la date est dans le passé
     */
    public static boolean isPastDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        return date.isBefore(LocalDate.now());
    }

    /**
     * Vérifie si une date est dans le futur
     *
     * @param date La date à vérifier
     * @return true si la date est dans le futur
     */
    public static boolean isFutureDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        return date.isAfter(LocalDate.now());
    }

    /**
     * Obtient le premier jour du mois pour une date donnée
     *
     * @param date La date de référence
     * @return Le premier jour du mois
     */
    public static LocalDate getStartOfMonth(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.withDayOfMonth(1);
    }

    /**
     * Obtient le dernier jour du mois pour une date donnée
     *
     * @param date La date de référence
     * @return Le dernier jour du mois
     */
    public static LocalDate getEndOfMonth(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.withDayOfMonth(date.lengthOfMonth());
    }

    /**
     * Formate une période entre deux dates
     *
     * @param startDate Date de début
     * @param endDate Date de fin
     * @return Description de la période formatée
     */
    public static String formatPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return "";
        } else if (startDate == null) {
            return "Jusqu'au " + formatDate(endDate);
        } else if (endDate == null) {
            return "À partir du " + formatDate(startDate);
        } else if (startDate.equals(endDate)) {
            return "Le " + formatDate(startDate);
        } else {
            return "Du " + formatDate(startDate) + " au " + formatDate(endDate);
        }
    }

    /**
     * Obtient la date actuelle formatée
     *
     * @return La date actuelle au format dd/MM/yyyy
     */
    public static String getCurrentDate() {
        return formatDate(LocalDate.now());
    }

    /**
     * Obtient la date-heure actuelle formatée
     *
     * @return La date-heure actuelle au format dd/MM/yyyy HH:mm:ss
     */
    public static String getCurrentDateTime() {
        return formatDateTime(LocalDateTime.now());
    }

    /**
     * Obtient l'heure actuelle formatée
     *
     * @return L'heure actuelle au format HH:mm:ss
     */
    public static String getCurrentTime() {
        return formatTime(LocalTime.now());
    }
}