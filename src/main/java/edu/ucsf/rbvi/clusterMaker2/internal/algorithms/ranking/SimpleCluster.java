package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.ranking;

import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterManager;
import edu.ucsf.rbvi.clusterMaker2.internal.api.Rank;
import edu.ucsf.rbvi.clusterMaker2.internal.commands.GetNetworkClusterTask;
import org.cytoscape.model.*;
import org.cytoscape.work.*;

import java.util.*;

public class SimpleCluster extends AbstractTask implements Rank {

    private GetNetworkClusterTask clusterMonitor;
    private List<List<CyNode>> clusters;
    private ClusterManager manager;
    private String attribute;
    private boolean canceled;
    public static String NAME = "Create rank from clusters";
    public static String SHORTNAME = "ranklust";
    public static String GROUP_ATTRIBUTE = SHORTNAME;

    @Tunable(description = "Network to look for cluster", context = "nogui")
    public CyNetwork network;

    @ContainsTunables
    public SimpleClusterContext context;

    public SimpleCluster(SimpleClusterContext context, ClusterManager manager) {
        System.out.println("SimpleCluster constructor");
        this.canceled = false;
        this.manager = manager;
        this.context = context;
        this.network = this.manager.getNetwork();
    }

    public String getShortName() {
        return SHORTNAME;
    }

    public String getName() {
        return NAME;
    }

    public Object getContext() {
        return this.context;
    }

    @SuppressWarnings("unchecked")
    public void run(TaskMonitor monitor) {
        monitor.setTitle("SimpleCluster.run()");

        if (network == null) {
            this.manager.getNetwork();
        }

        /*
         * Update the GUI
         */
        this.context.setNetwork(network);

       /*
        * Get the cluster etc.
        */
        this.attribute = this.context.getSelectedAttribute();
        this.clusterMonitor = new GetNetworkClusterTask(manager);
        this.clusterMonitor.algorithm = this.context.getSelectedAlgorithm();
        this.clusterMonitor.network = this.network;
        this.clusterMonitor.run(monitor);
        this.clusters = new ArrayList<>((Collection<List<CyNode>>)
                ((Map<String, Object>) this.clusterMonitor.getResults(Map.class)).get("networkclusters"));

        // necessary?
        CyTable nodeTable = network.getDefaultNodeTable();


        if (clusters.size() == 0) {
            monitor.showMessage(TaskMonitor.Level.INFO, "No clusters to work with");
            return;
        } else if (this.attribute == null || this.attribute.equals("--None--")) {
            monitor.showMessage(TaskMonitor.Level.INFO, "No attribute(s) to work with");
            return;
        } else if (nodeTable.getColumn(this.attribute) == null) {
            monitor.showMessage(TaskMonitor.Level.INFO, "No column with '" + this.attribute + "' as an attribute");
            return;
        } else if (this.canceled) {
            monitor.showMessage(TaskMonitor.Level.INFO, "Canceled");
            return;
        }

        // Start algorithm here
        monitor.showMessage(TaskMonitor.Level.INFO, "Running.");

        List<Integer> scoreList = new ArrayList<>(this.clusters.size());
        for (int i = 0; i < this.clusters.size(); i++) {
            for (CyNode node : this.clusters.get(i)) {
                if (nodeTable.getRow(node.getSUID()).get(this.attribute, Integer.class) == 1) {
                    scoreList.set(i, scoreList.get(i) + 1);
                }
            }
        }
        System.out.println("SimpleCluster is running."); // Find another way to log
        System.out.println("RESULTS:");
        Arrays.sort(scoreList.toArray());
        for (int i = 0; i < scoreList.size(); i++) {
            System.out.println("Cluster <" + this.clusters.get(i).toString() + ">: " + scoreList.get(i));
        }
        monitor.showMessage(TaskMonitor.Level.INFO, "Done.");
        System.out.println("SimpleCluster finished."); // Find another way to log
    }

    public boolean isAvailable() {
        return SimpleCluster.isReady(network);
    }

    public static boolean isReady(CyNetwork network) {
        return network != null;
    }

    public void cancel() {
        this.canceled = true;
    }
}
