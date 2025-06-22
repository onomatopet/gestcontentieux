package com.regulation.contentieux.util;

/**
 * Utilitaire pour convertir des nombres en lettres (français)
 * Utilisé pour afficher les montants en toutes lettres
 */
public class NumberToWords {

    private static final String[] UNITS = {
            "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf"
    };

    private static final String[] TEENS = {
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
            "dix-sept", "dix-huit", "dix-neuf"
    };

    private static final String[] TENS = {
            "", "", "vingt", "trente", "quarante", "cinquante",
            "soixante", "soixante", "quatre-vingt", "quatre-vingt"
    };

    /**
     * Convertit un nombre en lettres
     * @param number Le nombre à convertir (0 à 999 999 999)
     * @return Le nombre en lettres
     */
    public static String convert(long number) {
        if (number == 0) {
            return "zéro";
        }

        if (number < 0) {
            return "moins " + convert(-number);
        }

        return convertPositive(number).trim();
    }

    private static String convertPositive(long number) {
        if (number < 10) {
            return UNITS[(int) number];
        }

        if (number < 20) {
            return TEENS[(int) (number - 10)];
        }

        if (number < 100) {
            return convertTens(number);
        }

        if (number < 1000) {
            return convertHundreds(number);
        }

        if (number < 1_000_000) {
            return convertThousands(number);
        }

        if (number < 1_000_000_000) {
            return convertMillions(number);
        }

        return "nombre trop grand";
    }

    private static String convertTens(long number) {
        int tens = (int) (number / 10);
        int units = (int) (number % 10);

        if (tens == 7 || tens == 9) {
            // 70-79 : soixante-dix à soixante-dix-neuf
            // 90-99 : quatre-vingt-dix à quatre-vingt-dix-neuf
            return TENS[tens] + "-" + TEENS[units];
        }

        String result = TENS[tens];

        if (units == 1 && tens != 8) {
            result += " et un";
        } else if (units > 0) {
            if (tens == 8) {
                result += "-" + UNITS[units];
            } else {
                result += "-" + UNITS[units];
            }
        } else if (tens == 8) {
            result += "s"; // quatre-vingts
        }

        return result;
    }

    private static String convertHundreds(long number) {
        int hundreds = (int) (number / 100);
        int remainder = (int) (number % 100);

        String result;
        if (hundreds == 1) {
            result = "cent";
        } else {
            result = UNITS[hundreds] + " cent";
            if (remainder == 0) {
                result += "s"; // deux cents, trois cents, etc.
            }
        }

        if (remainder > 0) {
            result += " " + convertPositive(remainder);
        }

        return result;
    }

    private static String convertThousands(long number) {
        int thousands = (int) (number / 1000);
        int remainder = (int) (number % 1000);

        String result;
        if (thousands == 1) {
            result = "mille";
        } else {
            result = convertPositive(thousands) + " mille";
        }

        if (remainder > 0) {
            result += " " + convertPositive(remainder);
        }

        return result;
    }

    private static String convertMillions(long number) {
        int millions = (int) (number / 1_000_000);
        int remainder = (int) (number % 1_000_000);

        String result;
        if (millions == 1) {
            result = "un million";
        } else {
            result = convertPositive(millions) + " millions";
        }

        if (remainder > 0) {
            result += " " + convertPositive(remainder);
        }

        return result;
    }

    /**
     * Convertit un montant en francs CFA en lettres
     * @param amount Le montant en francs CFA
     * @return Le montant en lettres avec la devise
     */
    public static String convertAmount(long amount) {
        if (amount == 0) {
            return "zéro franc CFA";
        }

        String result = convert(amount);

        if (amount == 1) {
            return result + " franc CFA";
        } else {
            return result + " francs CFA";
        }
    }
}