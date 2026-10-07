package cn.aqcraft.metrorail.drive;

import cn.aqcraft.metrorail.rail.ControlMode;
import cn.aqcraft.metrorail.rail.MovementAuthority;
import cn.aqcraft.metrorail.rail.TrainState;
import org.bukkit.Location;

import java.util.UUID;

/**
 * 一次驾驶会话：一名司机 + 一辆矿车。
 */
public final class DriveSession {

    private final UUID driver;
    private final UUID cart;
    private final String driverName;
    private String vehicleId;
    private String consistId;
    private Notch notch = Notch.N;
    private Reverser reverser = Reverser.FORWARD;
    private double speed;
    private double headingX;
    private double headingZ;
    private boolean headingValid;
    private double mileage;

    // ---- 列控 / 状态机 ----
    private String train;
    private ControlMode mode = ControlMode.SHADOW;
    private TrainState state;
    private MovementAuthority.Result ma;
    private boolean maAcknowledged = true;
    private boolean integrityOk = true;
    private String integrityReason = "ok";
    private String unit = "kph";
    private Location lastKnown;

    public DriveSession(UUID driver, String driverName, UUID cart, String vehicleId) {
        this.driver = driver;
        this.driverName = driverName;
        this.cart = cart;
        this.vehicleId = vehicleId;
    }

    public UUID driver() {
        return driver;
    }

    public String driverName() {
        return driverName;
    }

    public UUID cart() {
        return cart;
    }

    public String vehicleId() {
        return vehicleId;
    }

    public void vehicleId(String id) {
        this.vehicleId = id;
    }

    public String consistId() {
        return consistId;
    }

    public void consistId(String id) {
        this.consistId = id;
    }

    public Notch notch() {
        return notch;
    }

    public void notch(Notch notch) {
        this.notch = notch;
    }

    public Reverser reverser() {
        return reverser;
    }

    public void reverser(Reverser reverser) {
        this.reverser = reverser;
    }

    public double speed() {
        return speed;
    }

    public void speed(double speed) {
        this.speed = speed;
    }

    public double headingX() {
        return headingX;
    }

    public double headingZ() {
        return headingZ;
    }

    public void heading(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        if (len > 1.0E-6) {
            this.headingX = x / len;
            this.headingZ = z / len;
            this.headingValid = true;
        }
    }

    public boolean headingValid() {
        return headingValid;
    }

    public void headingInvalid() {
        this.headingValid = false;
    }

    public double mileage() {
        return mileage;
    }

    public void addMileage(double blocks) {
        this.mileage += Math.max(0.0, blocks);
    }

    public double speedKmh() {
        return speed * 72.0;
    }

    // ---------------------------------------------------------- 列控 / 状态机

    public String train() {
        return train != null ? train : driverName;
    }

    public void train(String id) {
        this.train = id;
    }

    public ControlMode mode() {
        return mode;
    }

    public void mode(ControlMode mode) {
        this.mode = mode;
    }

    public TrainState state() {
        return state;
    }

    public void state(TrainState state) {
        this.state = state;
    }

    public MovementAuthority.Result ma() {
        return ma;
    }

    public void ma(MovementAuthority.Result ma) {
        this.ma = ma;
    }

    public boolean maAcknowledged() {
        return maAcknowledged;
    }

    public void maAcknowledged(boolean value) {
        this.maAcknowledged = value;
    }

    public boolean integrityOk() {
        return integrityOk;
    }

    public void integrity(boolean ok, String reason) {
        this.integrityOk = ok;
        this.integrityReason = reason;
    }

    public String integrityReason() {
        return integrityReason;
    }

    public String unit() {
        return unit;
    }

    public void unit(String unit) {
        this.unit = unit;
    }

    public Location lastKnown() {
        return lastKnown;
    }

    public void lastKnown(Location location) {
        this.lastKnown = location;
    }

    /** MA 剩余距离（无结果时返回 -1）。 */
    public double eoaDistance() {
        return ma == null ? -1.0 : ma.eoaDistance();
    }

    /** 当前有效限速（blocks/tick）。 */
    public double limitSpeed(double fallback) {
        return ma == null ? fallback : Math.min(fallback, ma.limitSpeed());
    }
}
