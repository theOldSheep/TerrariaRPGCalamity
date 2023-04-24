package terraria.util;

import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;

public class MathHelper {
    public static final double DEG_TO_RAD = Math.PI / 180;
    public static final double RAD_TO_DEG = 180 / Math.PI;

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
    // vector math
    public static Vector vectorProjection(Vector toProjectOnto, Vector vector) {
        // toProjectOnto.lengthSquared is equivalent to toProjectOnto.dot(toProjectOnto)
        return toProjectOnto.clone().multiply(toProjectOnto.dot(vector) / toProjectOnto.lengthSquared());
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
    public static double getVectorYaw(Vector vector) {
        // algorithm from Skript
        if (vector.getX() == 0 && vector.getZ() == 0) {
            return -90;
        }
        double resultYaw = Math.atan2(vector.getZ(), vector.getX()) * RAD_TO_DEG - 90;
        if (resultYaw < -180)
            resultYaw += 360;
        return resultYaw;
    }
    public static double getVectorPitch(Vector vector) {
        // algorithm from Skript
        double xz = Math.sqrt(vector.getX() * vector.getX() + vector.getZ() * vector.getZ());
        if (xz == 0d) return vector.getY() >= 0 ? -90d : 90d;
        return Math.atan(vector.getY() / xz) * RAD_TO_DEG * -1;
    }
    public static ArrayList<Vector> getCircularProjectileDirections(int amount) {
        ArrayList<Vector> results = new ArrayList<>();
        for (int i = 0; i < amount * 1.5; i ++)
            results.add(randomVector());
        return results;
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
    public static Vector rotateX(Vector vec, double rotationAngleDegCCW) {
        return rotateX(vec, xsin_degree(rotationAngleDegCCW), xcos_degree(rotationAngleDegCCW));
    }
    public static Vector rotateX(Vector vec, double sineVal, double cosineVal) {
        double newX = vec.getX();
        double newY = vec.getY() * cosineVal - vec.getZ() * sineVal;
        double newZ = vec.getY() * sineVal + vec.getZ() * cosineVal;
        return new Vector(newX, newY, newZ);
    }
    public static Vector rotateY(Vector vec, double rotationAngleDegCCW) {
        return rotateY(vec, xsin_degree(rotationAngleDegCCW), xcos_degree(rotationAngleDegCCW));
    }
    public static Vector rotateY(Vector vec, double sineVal, double cosineVal) {
        double newX = vec.getX() * cosineVal + vec.getZ() * sineVal;
        double newY = vec.getY();
        double newZ = -vec.getX() * sineVal + vec.getZ() * cosineVal;
        return new Vector(newX, newY, newZ);
    }
    public static Vector rotateZ(Vector vec, double rotationAngleDegCCW) {
        return rotateZ(vec, xsin_degree(rotationAngleDegCCW), xcos_degree(rotationAngleDegCCW));
    }
    public static Vector rotateZ(Vector vec, double sineVal, double cosineVal) {
        double newX = vec.getX() * cosineVal - vec.getY() * sineVal;
        double newY = vec.getX() * sineVal + vec.getY() * cosineVal;
        double newZ = vec.getZ();
        return new Vector(newX, newY, newZ);
    }
    public static Vec3D toNMSVector(Vector vec) {
        return new Vec3D(vec.getX(), vec.getY(), vec.getZ());
    }
    public static Vector toBukkitVector(Vec3D vec) {
        return new Vector(vec.x, vec.y, vec.z);
    }
    public static Vector getDirection(Location initialLoc, Location finalLoc, double length) {
        Vector dir = finalLoc.clone().subtract(initialLoc).toVector();
        double len = dir.length();
        if (len < 1e-9)
            return new Vector(0, length, 0);
        dir.multiply(length / len);
        return dir;
    }
}
