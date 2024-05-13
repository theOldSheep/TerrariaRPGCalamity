package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

public class Quaternion {
    private double x, y, z, w;

    // Constructor from axis and angle (in radians)
    public Quaternion(Vector axis, double angle) {
        double halfAngle = angle / 2.0;
        double sinHalfAngle = MathHelper.xsin_radian(halfAngle);

        this.x = axis.getX() * sinHalfAngle;
        this.y = axis.getY() * sinHalfAngle;
        this.z = axis.getZ() * sinHalfAngle;
        this.w = MathHelper.xcos_radian(halfAngle);
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