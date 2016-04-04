package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.ranking.simple;

import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.NodeCluster;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterManager;
import edu.ucsf.rbvi.clusterMaker2.internal.api.Rank;
import edu.ucsf.rbvi.clusterMaker2.internal.commands.GetNetworkClusterTask;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.ClusterUtils;
import org.cytoscape.model.*;
import org.cytoscape.work.*;

import java.util.*;

public class SingleNodeAttribute extends AbstractTask implements Rank {

    private List<NodeCluster> clusters;
    private ClusterManager manager;
    private String attribute;
    private boolean canceled;
    public static String NAME = "Create rank from clusters";
    public static String SHORTNAME = "SNArank";

    @Tunable(description = "Network to look for cluster", context = "nogui")
    public CyNetwork network;

    @ContainsTunables
    public SNAContext context;

    public SingleNodeAttribute(SNAContext context, ClusterManager manager) {
        System.out.println("SingleNodeAttribute constructor");
        this.canceled = false;
        this.manager = manager;
        this.context = context;

        if (this.network == null) {
            this.network = this.manager.getNetwork();
        }

        context.setNetwork(this.network);
        this.context.updateContext();
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
        monitor.setTitle("SingleNodeAttribute.run()");


        if (network == null) {
            this.manager.getNetwork();
        }

        clusters = ClusterUtils.createClusters(network);

        if (!noNullValuesOrCancel(monitor)) {
            return;
        }

        monitor.showMessage(TaskMonitor.Level.INFO, "Getting scorelist for simpleCluster.");
        List<Double> scoreList = createScoreList();
        addScoreToColumns(scoreList, monitor); // This can be abstract for ALL of ranking cluster algorithms
        monitor.showMessage(TaskMonitor.Level.INFO, "Done.");
        System.out.println("SingleNodeAttribute finished.");
    }

    /*
     * For each row in the table, index on the clustering group attribute and add a column with the score
     */
    private void addScoreToColumns(List<Double> scoreList, TaskMonitor monitor) {
        CyTable nodeTable = network.getDefaultNodeTable();
        CyTable networkTable = network.getDefaultNetworkTable();
        List<CyRow> rows = nodeTable.getAllRows();
        String clusterColumnName = this.getClusterColumn();
        String rankColumnName = this.context.getClusterAttribute();
        System.out.println("Number of rows in the table: " + rows.size());

        if (clusterColumnName.equals("")) {
            monitor.showMessage(TaskMonitor.Level.INFO, "Could not find cluster column name to work with");
            return;
        }

        if (nodeTable.getColumn(rankColumnName) != null) {
            nodeTable.deleteColumn(rankColumnName);
        }

        if (networkTable.getColumn(ClusterManager.RANKING_ATTRIBUTE) != null) {
            networkTable.deleteColumn(ClusterManager.RANKING_ATTRIBUTE);
        }

        nodeTable.createColumn(rankColumnName, Double.class, false);
        networkTable.createColumn(ClusterManager.RANKING_ATTRIBUTE, String.class, false);

        for (CyRow rankRow : networkTable.getAllRows()) {
            rankRow.set(ClusterManager.RANKING_ATTRIBUTE, "__SCRank");
        }

        for (CyRow row : rows) {
            int cluster = row.get(clusterColumnName, Integer.class, 0);
            if (cluster != 0) {
                row.set(rankColumnName, scoreList.get(cluster - 1));
            }
        }
    }

    private String getClusterColumn() {
        return this.network.getRow(network).get(ClusterManager.CLUSTER_ATTRIBUTE, String.class, "");
    }

    private boolean clusterIsReady(GetNetworkClusterTask clusterMonitor) {
        this.context.setNetwork(network);
        this.attribute = this.context.getSelectedAttribute();
        clusterMonitor.algorithm = this.context.getSelectedAlgorithm();

        // This should be removed in the future
        if (clusterMonitor.algorithm.equals("None")) {
            return false;
        }

        clusterMonitor.network = network;
        return true;
    }

    private List<Double> createScoreList() {
        CyTable nodeTable = this.network.getDefaultNodeTable();
        List<Double> scoreList = new ArrayList<>(this.clusters.size());

        System.out.println("SingleNodeAttribute is running.");

        for (int i = 0; i < this.clusters.size(); i++) {
            double score = 0;

            for (CyNode node : this.clusters.get(i)) {
                try {
                    score += nodeTable.getRow(node.getSUID()).get(this.attribute, Double.class, 0.0);
                } catch (ClassCastException cce) {
                    score += (double) nodeTable.getRow(node.getSUID()).get(this.attribute, Integer.class, 0);
                }
            }

            scoreList.add(i, score);
        }

        return scoreList;
    }

    private boolean noNullValuesOrCancel(TaskMonitor monitor) {
        CyTable nodeTable = this.network.getDefaultNodeTable();

        if (this.clusters.size() == 0) {
            monitor.showMessage(TaskMonitor.Level.INFO, "No clusters to work with");
            return false;
        } else if (this.attribute == null || this.attribute.equals("None")) {
            monitor.showMessage(TaskMonitor.Level.INFO, "No attribute(s) to work with");
            return false;
        } else if (nodeTable.getColumn(this.attribute) == null) {
            monitor.showMessage(TaskMonitor.Level.INFO, "No column with '" + this.attribute + "' as an attribute");
            return false;
        } else if (this.canceled) {
            monitor.showMessage(TaskMonitor.Level.INFO, "Canceled");
            return false;
        }

        return true;
    }

    // This should go through the clustering algorithms and check if one of them have some results.
    // NB! Only the algorithm run last will have results to work with!
    public static boolean isReady(CyNetwork network, ClusterManager manager) {
        GetNetworkClusterTask clusterMonitor;

        if (network == null) {
            return false;
        }

        clusterMonitor = new GetNetworkClusterTask(manager);
        clusterMonitor.algorithm = "shit"; // Temporary

        return !clusterMonitor.algorithm.equals("None");

    }
}