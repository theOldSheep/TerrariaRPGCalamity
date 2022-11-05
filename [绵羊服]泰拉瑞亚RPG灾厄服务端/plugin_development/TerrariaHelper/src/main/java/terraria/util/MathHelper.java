package terraria.util;

public class MathHelper {
    // xsin source code: https://stackoverflow.com/questions/523531/fast-transcendent-trigonometric-functions-for-java
    // Return an approx to sin(pi/2 * x) where -1 <= x <= 1.
    // In that range it has a max absolute error of 5e-9
    // according to Hastings, Approximations For Digital Computers.
    public static double xsin(double x) {
        double x2 = x * x;
        return ((((.00015148419 * x2
                - .00467376557) * x2
                + .07968967928) * x2
                - .64596371106) * x2
                + 1.57079631847) * x;
    }
    public static double xsin_degree(double x) {
        return xsin_regularize(x, 360, 180, 90);
    }
    public static double xcos_degree(double x) {
        return xsin_degree(x + 90);
    }
    public static double xsin_radian(double x) {
        return xsin_regularize(x, Math.PI * 2, Math.PI, Math.PI / 2);
    }
    public static double xcos_radian(double x) {
        return xsin_degree(x + Math.PI / 2);
    }
    private static double xsin_regularize(double x, double cycle, double half_cycle, double quarter_cycle) {
        double effectiveX = x % cycle;
        if (effectiveX > half_cycle) effectiveX -= cycle;
        else if (effectiveX < half_cycle * -1) effectiveX += cycle;
        if (effectiveX > quarter_cycle) effectiveX = half_cycle - effectiveX;
        else if (effectiveX < quarter_cycle * -1) effectiveX = (half_cycle * -1) - effectiveX;
        return xsin(effectiveX / quarter_cycle);
    }
    public static int betterFloorDivision(int dividend, int divisor) {
        int result = dividend / divisor;
        if (dividend < 0 && dividend % divisor != 0) result --;
        return result;
    }
    public static boolean isBetween(double toCheck, double lower, double upper) {
        return toCheck <= upper && toCheck >= lower;
    }
    public static boolean isBetween(long toCheck, long lower, long upper) {
        return toCheck <= upper && toCheck >= lower;
    }
    public static boolean isBetween(int toCheck, int lower, int upper) {
        return toCheck <= upper && toCheck >= lower;
    }

}
