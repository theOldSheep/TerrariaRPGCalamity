package terraria.util;

import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;

public class MathHelper {
    public static class Quaternion {
        private double x, y, z, w;

        // Constructor from axis and angle (in radians)
        public Quaternion(Vector axis, double angle) {
            double sinHalfAngle = xsin_radian(angle);

            this.x = axis.getX() * sinHalfAngle;
            this.y = axis.getY() * sinHalfAngle;
            this.z = axis.getZ() * sinHalfAngle;
            this.w = xcos_radian(angle);
        }

        public Quaternion(double x, double y, double z, double w) {
            this.x = x; this.y = y; this.z = z; this.w = w;
        }

        public Quaternion multiply(Quaternion q) {
            double newW = w * q.w - x * q.x - y * q.y - z * q.z;
            double newX = w * q.x + x * q.w + y * q.z - z * q.y;
            double newY = w * q.y + y * q.w + z * q.x - x * q.z;
            double newZ = w * q.z + z * q.w + x * q.y - y * q.x;
            return new Quaternion(newX, newY, newZ, newW);
        }

        public Vector interpolate(Vector v) {
            Quaternion vector = new Quaternion(v.getX(), v.getY(), v.getZ(), 0);
            // we are dealing with normalized quaternions only
            Quaternion conjugate = new Quaternion(-x, -y, -z, w);

            Quaternion result = this.multiply(vector).multiply(conjugate);

            return new Vector(result.x,result.y,result.z);
        }
    }
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
        return xsin_radian(x + Math.PI / 2);
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
        return vectorFromYawPitch_approx(Math.random() * 360 - 180, Math.random() * 180 - 90);
    }
    public static Vector vectorFromYawPitch_approx(double yaw, double pitch) {
        // algorithm from Skript
        // uses xsin and xcos so that it is quicker. a bit less accurate though.
        double y = -1 * xsin_degree(pitch);
        double div = xcos_degree(pitch);
        double x = -1 * xsin_degree(yaw);
        double z = xcos_degree(yaw);
        x *= div;
        z *= div;
        return new Vector(x,y,z);
    }
    public static int randomRound(double decimal) {
        int result = (int) decimal;
        if (Math.random() < decimal % 1)
            result ++;
        return result;
    }
    public static double getVectorYaw(Vector vector) {
        if (vector.getX() == 0 && vector.getZ() == 0) {
            return -90;
        }
        double resultYaw = Math.atan2(vector.getZ(), vector.getX()) * RAD_TO_DEG - 90;
        if (resultYaw < -180)
            resultYaw += 360;
        return resultYaw;
    }
    public static double getVectorPitch(Vector vector) {
        double xz = Math.sqrt(vector.getX() * vector.getX() + vector.getZ() * vector.getZ());
        if (xz == 0d) return vector.getY() >= 0 ? -90d : 90d;
        return Math.atan(vector.getY() / xz) * RAD_TO_DEG * -1;
    }
    public static ArrayList<Vector> getCircularProjectileDirections(int amountPerArc, int amountArcs, double halfArcAngleDeg, Vector forwardDir, double length) {
        ArrayList<Vector> results = new ArrayList<>();
        // regularize forward direction
        Vector fwdDir = new Vector().copy(forwardDir);
        double fwdLenSqr = fwdDir.lengthSquared();
        if (fwdLenSqr < 0.999 || fwdLenSqr > 1.001)
            fwdDir.normalize();
        // get offset direction for each arc
        Vector[] arcDirs = new Vector[amountArcs];
        {
            Vector orthVec1 = getNonZeroCrossProd(fwdDir, fwdDir);
            orthVec1.normalize();
            if (amountArcs == 1)
                arcDirs[0] = orthVec1;
            else {
                Vector orthVec2 = fwdDir.getCrossProduct(orthVec1);
                double offset = 180d / amountArcs;
                double angle = 0;
                for (int i = 0; i < amountArcs; i ++) {
                    Vector newArcDir = orthVec1.clone();
                    newArcDir.multiply(xsin_degree(angle));
                    Vector offsetArcDir = orthVec2.clone();
                    offsetArcDir.multiply(xcos_degree(angle));
                    newArcDir.add(offsetArcDir);
                    arcDirs[i] = newArcDir;
                    angle += offset;
                }
            }
        }
        // then setup all direction vectors
        int loopAmount = amountPerArc / 2;
        double angle = 0, offset = halfArcAngleDeg * 2 / amountPerArc;
        if (amountPerArc % 2 == 1)
            results.add(fwdDir.clone());
        else
            angle -= offset / 2;
        for (int i = 0; i < loopAmount; i ++) {
            angle += offset;
            double sinVal = xsin_degree(angle), cosVal = xcos_degree(angle);
            Vector fwdComp = fwdDir.clone().multiply(cosVal);
            for (int arcIndex = 0; arcIndex < amountArcs; arcIndex ++) {
                Vector offsetComp1 = arcDirs[arcIndex].clone().multiply(sinVal);
                Vector offsetComp2 = arcDirs[arcIndex].clone().multiply(-sinVal);
                results.add(offsetComp1.add(fwdComp));
                results.add(offsetComp2.add(fwdComp));
            }
        }
        // setup speed
        for (Vector vec : results)
            vec.multiply(length);
        return results;
    }
    public static ArrayList<Vector> getCircularProjectileDirections(int amountPerArc, int amountArcs, double halfArcAngleDeg,
                                                                    Entity target, Location shootLoc, double length) {
        EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions().setProjectileSpeed(length);
        Location targetLoc = EntityHelper.helperAimEntity(shootLoc, target, aimHelper);
        Vector fwdDir = targetLoc.subtract(shootLoc).toVector();
        ArrayList<Vector> result = getCircularProjectileDirections(amountPerArc, amountArcs, halfArcAngleDeg, fwdDir, length);
        return result;
    }
    public static ArrayList<Vector> getEvenlySpacedProjectileDirections(double projectileIntervalDegree, double spreadAngleDegree, Vector forwardDir, double length) {
        ArrayList<Vector> results = new ArrayList<>();
        // Regularize forward direction
        Vector fwdDir = new Vector().copy(forwardDir);
        double fwdLenSqr = fwdDir.lengthSquared();
        if (fwdLenSqr < 0.999 || fwdLenSqr > 1.001)
            fwdDir.normalize();
        // Set up the orthogonal vectors for the perpendicular direction
        Vector orthVec1 = getNonZeroCrossProd(fwdDir, fwdDir).normalize();
        Vector orthVec2 = fwdDir.getCrossProduct(orthVec1);
        // The parameters are passed in as degrees, so let's work with a unit sphere with radius 1.
        double intervalArcLen = Math.toRadians( projectileIntervalDegree );
        // Iterate through the circles on the sphere sector
        for (double theta = 0; theta <= spreadAngleDegree + 1e-9; theta += projectileIntervalDegree) {
            double sinValCircle = xsin_degree(theta), cosValCircle = xcos_degree(theta);
            double circleDiam = Math.PI * 2 * sinValCircle;
            // make sure at least one iteration at the center; this offset fills up holes close to the center.
            int iterations = (int) Math.ceil( (circleDiam + 0.1) / intervalArcLen);
            Vector fwdComp = fwdDir.clone().multiply(cosValCircle);

            double phi = Math.random() * 360d, dPhi = 360d / iterations;
            for (int iteration = 0; iteration < iterations; iteration ++) {
                double sinPhi = xsin_degree(phi), cosPhi = xcos_degree(phi);

                Vector horComp1 = orthVec1.clone().multiply(sinPhi * sinValCircle);
                Vector horComp2 = orthVec2.clone().multiply(cosPhi * sinValCircle);
                Vector result = horComp1.add(horComp2).add(fwdComp);
                results.add(result);

                phi += dPhi;
            }
        }
        // Setup speed
        for (Vector vec : results)
            vec.multiply(length);
        return results;
    }
    public static ArrayList<Vector> getEvenlySpacedProjectileDirections(double projectileIntervalDegree, double spreadAngleDegree,
                                                                        Entity target, Location shootLoc, double length) {
        EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                .setProjectileSpeed(length);
        return getEvenlySpacedProjectileDirections(projectileIntervalDegree, spreadAngleDegree, target, shootLoc, aimHelper, length);
    }
    public static ArrayList<Vector> getEvenlySpacedProjectileDirections(double projectileIntervalDegree, double spreadAngleDegree,
                                                                        Entity target, Location shootLoc, EntityHelper.AimHelperOptions aimHelper,
                                                                        double length) {
        Location targetLoc = EntityHelper.helperAimEntity(shootLoc, target, aimHelper);
        Vector fwdDir = targetLoc.subtract(shootLoc).toVector();
        return getEvenlySpacedProjectileDirections(projectileIntervalDegree, spreadAngleDegree, fwdDir, length);
    }
    public static <T> T selectWeighedRandom(HashMap<T, Double> weighedMap, T defaultVal) {
        double total = 0;
        for (double curr : weighedMap.values()) total += curr;
        if (total == 0) {
            return defaultVal;
        }
        double rdm = Math.random() * total;
        for (T curr : weighedMap.keySet()) {
            double currWeight = weighedMap.get(curr);
            if (rdm <= currWeight) return curr;
            rdm -= currWeight;
        }
        // this is technically never reachable. It is here to prevent IDE reporting an error.
        return defaultVal;
    }
    public static String selectWeighedRandom(HashMap<String, Double> weighedMap) {
        return selectWeighedRandom(weighedMap, "");
    }
    public static double getAngleRadian(Vector v1, Vector v2) {
        double dot = v1.dot(v2) / Math.sqrt(v1.lengthSquared() * v2.lengthSquared());
        // precision issue: sometimes its absolute value is slightly higher than 1, producing NaN
        if (dot > 1)
            dot = 1;
        if (dot < -1)
            dot = -1;
        return (float)Math.acos(dot);
    }
    public static Vector setVectorLength(Vector vec, double targetLength) {
        return setVectorLength(vec, targetLength, false);
    }
    public static Vector setVectorLength(Vector vec, double targetLength, boolean keepOriginalBelowLength) {
        return setVectorLengthSquared(vec, targetLength * targetLength, keepOriginalBelowLength);
    }
    public static Vector setVectorLengthSquared(Vector vec, double targetLengthSquared) {
        return setVectorLengthSquared(vec, targetLengthSquared, false);
    }
    public static Vector setVectorLengthSquared(Vector vec, double targetLengthSquared, boolean keepOriginalBelowLength) {
        double vLS = vec.lengthSquared();
        if (keepOriginalBelowLength && vLS <= targetLengthSquared)
            return vec;
        if (vLS < 1e-9) vec.setY(Math.sqrt(targetLengthSquared));
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
    public static Vector rotateAroundAxisDegree(Vector vec, Vector axis, double angleDegree) {
        return rotateAroundAxisRadian(vec, axis, Math.toRadians(angleDegree));
    }
    public static Vector rotateAroundAxisRadian(Vector vec, Vector axis, double angleRadian) {
        if ( Math.abs( axis.lengthSquared() - 1) > 1e-5 )
            setVectorLength(axis, 1);

        try {
            vec.checkFinite();
            axis.checkFinite();
        }
        catch (Exception e) {
            return new Vector();
        }

        Quaternion rotation = new Quaternion(axis, angleRadian / 2 );

        return rotation.interpolate(vec);
    }
    public static Vector rotationInterpolateDegree(Vector start, Vector end, double maxAngleDegree) {
        return rotationInterpolateRadian(start, end, Math.toRadians(maxAngleDegree));
    }
    public static Vector rotationInterpolateRadian(Vector start, Vector end, double maxAngleRadian) {
        if (start.lengthSquared() < 1e-9)
            return start;

        Vector axis = getNonZeroCrossProd(start, end);
        double angle = getAngleRadian(start, end);

        if (angle < maxAngleRadian) {
            return setVectorLength(end.clone(), start.length());
        }

        return rotateAroundAxisRadian(start, axis, maxAngleRadian);
    }
    public static Vec3D toNMSVector(Vector vec) {
        return new Vec3D(vec.getX(), vec.getY(), vec.getZ());
    }
    public static Vector toBukkitVector(Vec3D vec) {
        return new Vector(vec.x, vec.y, vec.z);
    }
    public static Vector getDirection(Location initialLoc, Location finalLoc, double length) {
        return getDirection(initialLoc, finalLoc, length, false);
    }
    public static Vector getDirection(Location initialLoc, Location finalLoc, double length, boolean keepOriginalBelowLength) {
        // handle different world
        if (initialLoc.getWorld() != finalLoc.getWorld())
            return new Vector(0, length, 0);

        Vector dir = finalLoc.clone().subtract(initialLoc).toVector();
        double len = dir.length();
        // if the direction is shorter than length,  keep it intact.
        if (keepOriginalBelowLength && len <= length)
            return dir;
        if (len < 1e-9)
            return new Vector(0, length, 0);
        dir.multiply(length / len);
        return dir;
    }

    // creates the non-zero cross product: if the two vectors are collinear, return a random one that is orthogonal to them. DOES NOT NORMALIZE.
    public static Vector getNonZeroCrossProd(Vector vec1, Vector vec2) {
        if (vec1.lengthSquared() < 1e-9)
            return new Vector(0, 1, 0);

        Vector result = vec1.getCrossProduct(vec2);
        // within the loop, vec1 and vec2 are collinear.
        while (result.lengthSquared() < 1e-6) {
            result = randomVector();
            result.subtract(vectorProjection(vec1, result));
        }
        return result;
    }
}
