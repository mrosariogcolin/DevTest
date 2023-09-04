package com.company;

public class Helper {

    public static double getPercentError(double measured, double actual) {
        return Math.abs((measured - actual) / actual);
    }

    /**
     * This method converts a number string that has a metric suffix (e.g. 1.23k) and
     * returns its number equivalent, e.g. 1230
     * */
    public static double getNonSuffixedValue(String rawVal) {
        char prefix = rawVal.charAt(rawVal.length() - 1);
        double mult = 1;
        if (!Character.isDigit(prefix)) {
            if (prefix == 'T') mult = 1e12;
            else if (prefix == 'G') mult = 1e9;
            else if (prefix == 'M') mult = 1e6;
            else if (prefix == 'k') mult = 1e3;
            else mult = 1;
            return Double.parseDouble(rawVal.substring(0, rawVal.length() - 1)) * mult;
        } else {
            return Double.parseDouble(rawVal);
        }
    }

    /**
     * This method converts a number string in percentage form (e.g. 10%) and
     * returns its number equivalent, e.g. 0.1
     * */
    public static double getNonPercentageFormValue(String rawVal) {
        if (rawVal.endsWith("%")) {
            return Double.parseDouble(rawVal.substring(0, rawVal.length() - 1)) / 100;
        } else {
            return Double.parseDouble(rawVal);
        }
    }
}
