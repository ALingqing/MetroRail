package cn.aqcraft.metrorail.rail;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 轨道层服务集合：图、道岔、应答器、占用、MA。
 */
public final class RailServices {

    private final RailGraph graph = new RailGraph();
    private final GraphStore store;
    private final SwitchRegistry switches;
    private final BaliseRegistry balises;
    private final OccupancyLedger ledger;
    private final MovementAuthority ma;
    private final TrainLocator locator;
    private final TimsMonitor tims;
    private final TrainProperties trainProperties = new TrainProperties();
    private final TrainStore trainStore;
    private final EventLog events;

    public RailServices(JavaPlugin plugin) {
        this.store = new GraphStore(plugin);
        this.switches = new SwitchRegistry(plugin);
        this.balises = new BaliseRegistry(plugin);
        this.ledger = new OccupancyLedger(plugin);
        this.ma = new MovementAuthority(graph, ledger, balises);
        this.locator = new TrainLocator(graph);
        this.tims = new TimsMonitor();
        this.trainStore = new TrainStore(plugin);
        this.events = new EventLog(200);
    }

    public void loadAll() {
        store.load(graph);
        switches.load();
        balises.load();
        ledger.load();
        trainStore.load(trainProperties);
    }

    public void saveAll() {
        store.save(graph);
        switches.save();
        balises.save();
        ledger.save();
        trainStore.save(trainProperties);
    }

    /** 从起点洪泛建图并落盘。 */
    public int rebuild(org.bukkit.Location origin, int maxNodes) {
        int count = graph.scan(origin, maxNodes);
        store.save(graph);
        return count;
    }

    public RailGraph graph() {
        return graph;
    }

    public GraphStore store() {
        return store;
    }

    public SwitchRegistry switches() {
        return switches;
    }

    public BaliseRegistry balises() {
        return balises;
    }

    public OccupancyLedger ledger() {
        return ledger;
    }

    public MovementAuthority ma() {
        return ma;
    }

    public TrainLocator locator() {
        return locator;
    }

    public TimsMonitor tims() {
        return tims;
    }

    public TrainProperties trainProperties() {
        return trainProperties;
    }

    public EventLog events() {
        return events;
    }
}
