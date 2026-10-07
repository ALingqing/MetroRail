package cn.aqcraft.metrorail.drive;

/**
 * 车型物理参数。速度单位 blocks/tick。
 */
public final class VehicleType {

    private final String id;
    private final String displayName;
    private final double maxSpeed;
    private final double acceleration;
    private final double brake;
    private final double emergencyBrake;
    private final double friction;

    public VehicleType(String id, String displayName, double maxSpeed, double acceleration,
                       double brake, double emergencyBrake, double friction) {
        this.id = id;
        this.displayName = displayName;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.brake = brake;
        this.emergencyBrake = emergencyBrake;
        this.friction = friction;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    public double brake() {
        return brake;
    }

    public double emergencyBrake() {
        return emergencyBrake;
    }

    public double friction() {
        return friction;
    }

    public double maxSpeedKmh() {
        return maxSpeed * 72.0;
    }

    public String describe() {
        return String.format(java.util.Locale.ROOT,
                "%s (max=%.3f, accel=%.3f, brake=%.3f, eb=%.3f, friction=%.3f)",
                id, maxSpeed, acceleration, brake, emergencyBrake, friction);
    }
}
