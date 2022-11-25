package terraria.util;

import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.util.Vector;

import java.util.HashMap;

public class MathHelper {
    public static final double DEG_TO_RAD = Math.PI / 180;

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
    public static Vector vectorFromYawPitch(double yaw, double pitch) {
        // source code from Skript
        double y = -1 * Math.sin(pitch * DEG_TO_RAD);
        double div = Math.cos(pitch * DEG_TO_RAD);
        double x = -1 * Math.sin(yaw * DEG_TO_RAD);
        double z = Math.cos(yaw * DEG_TO_RAD);
        x *= div;
        z *= div;
        return new Vector(x,y,z);
    }
    public static Vector randomVector() {
        return vectorFromYawPitch_quick(Math.random() * 360 - 180, Math.random() * 180 - 90);
    }
    public static Vector vectorFromYawPitch_quick(double yaw, double pitch) {
        // algorithm from Skript
        // uses xsin and xcos so that it is quicker. a bit less accurate though.
        double y = -1 * MathHelper.xsin_degree(pitch);
        double div = MathHelper.xcos_degree(pitch);
        double x = -1 * MathHelper.xsin_degree(yaw);
        double z = MathHelper.xcos_degree(yaw);
        x *= div;
        z *= div;
        return new Vector(x,y,z);
    }
    public static String selectWeighedRandom(HashMap<String, Double> weighedMap) {
        double total = 0;
        for (double curr : weighedMap.values()) total += curr;
        if (total == 0) return "";
        double rdm = Math.random() * total;
        for (String curr : weighedMap.keySet()) {
            double currWeight = weighedMap.get(curr);
            if (rdm <= currWeight) return curr;
            rdm -= currWeight;
        }
        // this is technically never reachable. It is here to prevent IDE reporting an error.
        return "";
    }
    public static Vector setVectorLength(Vector vec, double targetLength) {
        if (vec.lengthSquared() < 1e-9) vec.setY(targetLength);
        else vec.normalize().multiply(targetLength);
        return vec;
    }
    public static Vector setVectorLengthSquared(Vector vec, double targetLengthSquared) {
        if (vec.lengthSquared() < 1e-9) vec.setY(Math.sqrt(targetLengthSquared));
        else vec.multiply(Math.sqrt(targetLengthSquared / vec.lengthSquared()));
        return vec;
    }
    public static Vec3D toNMSVector(Vector vec) {
        return new Vec3D(vec.getX(), vec.getY(), vec.getZ());
    }
    public static Vector toBukkitVector(Vec3D vec) {
        return new Vector(vec.x, vec.y, vec.z);
    }
}
