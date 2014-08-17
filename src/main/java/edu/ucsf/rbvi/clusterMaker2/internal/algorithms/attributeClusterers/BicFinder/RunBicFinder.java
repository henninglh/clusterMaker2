package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.attributeClusterers.BicFinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.work.TaskMonitor;

import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.attributeClusterers.DistanceMetric;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.attributeClusterers.Matrix;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.attributeClusterers.BiMine.BETNode;

public class RunBicFinder {

	protected CyNetwork network;
	protected String[] weightAttributes;
	
	protected Matrix matrix;
	protected Matrix biclusterMatrix;
	protected Double arr[][];
	protected int[][] discrete_matrix;
	protected Matrix matrix_t;
	
	protected List<Map<Integer,Integer>> dag;
	protected List<Map<Integer,List<Boolean>>> csl;
	protected List<Map<Integer,Map<Integer,Double>>> csi;
	protected int[] maxCSL;
	
	protected Double geneRho[][];
	protected Double conditionRho[][];
	
	protected int[] clusters;
	protected TaskMonitor monitor;
	protected boolean ignoreMissing = true;
	protected boolean selectedOnly = false;
	BicFinderContext context;	
	double alpha;
	double delta;
	
	int nelements;
	int nattrs;
	protected Map<Integer,List<Integer>> clusterRows;
	protected Map<Integer,List<Integer>> clusterCols;
	protected Map<Integer,List<Long>> clusterNodes;
	protected Map<Integer,List<String>> clusterAttrs;
	
	public Matrix getMatrix() { return matrix; }
	public Matrix getBiclusterMatrix() { return biclusterMatrix; }
	public int[] getClustersArray() {return clusters;}
	
	public RunBicFinder(CyNetwork network, String weightAttributes[],
            TaskMonitor monitor, BicFinderContext context) {
		//super(network, weightAttributes, metric, monitor);
		this.network = network;
		this.weightAttributes = weightAttributes;
		this.monitor = monitor;
		this.context = context;
		this.alpha = context.alpha.getValue();
		this.delta = context.delta.getValue();
	}

	public Integer[] cluster(boolean transpose) {
		// Create the matrix
		matrix = new Matrix(network, weightAttributes, transpose, ignoreMissing, selectedOnly);
		monitor.showMessage(TaskMonitor.Level.INFO,"cluster matrix has "+matrix.nRows()+" rows");
		
		// Create a weight vector of all ones (we don't use individual weighting, yet)
		matrix.setUniformWeights();
				
		if (monitor != null) 
			monitor.setStatusMessage("Clustering...");
		
		nelements = matrix.nRows();
		nattrs = matrix.nColumns();
		
		//Generate the transpose matrix for rho calculation 
		matrix_t = new Matrix(network,nattrs,nelements);
		for(int i = 0; i < nelements; i++){
			for(int j = 0; j < nattrs; j++)matrix_t.setValue(j, i, matrix.getValue(i, j));
		}
		calculateRhos();
		
		discrete_matrix = getDiscreteMatrix();
		generateCSL();
		generateCSI();
		dag = generateDag();
		
		clusterRows = new HashMap<Integer,List<Integer>>();
		clusterCols = new HashMap<Integer,List<Integer>>();
		
		//Iteratively create Biclusters
		for(int i = 0; i < nelements; i++){
			List<Integer> genes = new ArrayList<Integer>();
			List<Integer> conditions = new ArrayList<Integer>();
			
			//Sort the edges leaving ith node according to the number of trues
			List<Map.Entry<Integer,Integer>> edges = new LinkedList<Map.Entry<Integer,Integer>>(dag.get(i).entrySet());
			Collections.sort(edges, new Comparator<Map.Entry<Integer,Integer>>() {
				public int compare(Map.Entry<Integer,Integer> o1, Map.Entry<Integer,Integer> o2) {
					return o2.getValue().compareTo(o1.getValue());
				}
			});
			
			for (Map.Entry<Integer,Integer> entry: edges){
				int k = entry.getKey();
				List<Integer> genes_c = new ArrayList<Integer>();
				List<Integer> conditions_c = new ArrayList<Integer>();
				
				genes_c = union(genes,Arrays.asList(i,k));
				conditions_c = union(conditions,getCommonConditions(i,k));
				if(getACSI(i,genes_c,conditions_c) >= alpha){
					//Assign bicluster i to current genes and conditions
					genes = genes_c;
					conditions = conditions_c;					
				}
			}
			
			//Add the new bicluster to the complete list 
			if(getASR(genes,conditions) >= delta){
				if(!(clusterRows.containsValue(genes) && clusterCols.containsValue(conditions))){
					clusterRows.put(clusterRows.size()+1, genes);
					clusterCols.put(clusterCols.size()+1, conditions);
				}
			}
		}
		
		int totalRows = 0;
		for(List<Integer> biclust: clusterRows.values())totalRows+= biclust.size();
		
		clusters = new int[totalRows];
		CyNode rowNodes[] = new CyNode[totalRows];
		biclusterMatrix = new Matrix(network,totalRows,nattrs);
		
		int i = 0;
		for(Integer biclust: clusterRows.keySet()){
			for(Integer node: clusterRows.get(biclust)){				
				biclusterMatrix.setRowLabel(i, matrix.getRowLabel(node));
				rowNodes[i] = matrix.getRowNode(node);
				
				for(int j = 0; j< nattrs; j++){
					biclusterMatrix.setValue(i, j,matrix.getValue(node, j));					
				}
				clusters[i] = biclust;
				i++;
			}			
		}
		for(int j = 0; j<nattrs;j++){
			biclusterMatrix.setColLabel(j, matrix.getColLabel(j));			
		}
		
		biclusterMatrix.setRowNodes(rowNodes);
		Integer[] rowOrder;
		rowOrder = biclusterMatrix.indexSort(clusters, clusters.length);
		return rowOrder;
	}
	
	private double getACSI(int gene_i, List<Integer> genes,
			List<Integer> conditions) {
		if (genes.size() <= 2)return 1.0;
		double acsi = 0.0;
		int i = genes.indexOf(gene_i);
		for(int j = i+1; j < genes.size(); j++){
			for(int k = j+1; k < genes.size(); k++){
				acsi += csi.get(gene_i).get(genes.get(j)).get(genes.get(k)); 
			}
		}
		acsi *= 2;
		acsi /= ((genes.size()-1)*(genes.size()-2));
		return acsi;
	}
	
	private double getASR(List<Integer> genes, List<Integer> conditions) {
		double asr = 0.0;
		double asr_g = 0.0;
		double asr_c = 0.0;
		
		for(int i = 0; i < genes.size(); i++){			
			for(int j = i+1; j < genes.size(); j++){
				asr_g += geneRho[genes.get(i)][genes.get(j)];
			}
		}
		asr_g /= genes.size()*(genes.size()-1);
		
		for(int i = 0; i < genes.size(); i++){			
			for(int j = i+1; j < genes.size(); j++){
				asr_c += conditionRho[conditions.get(i)][conditions.get(j)];
			}
		}
		asr_c /= conditions.size()*(conditions.size()-1);
		
		asr = 2*Math.max(asr_g, asr_c);
		return asr;
	}
	
	private void calculateRhos() {
		int nelements = matrix.nRows();
		int nattrs = matrix.nColumns();
		DistanceMetric spearman = DistanceMetric.SPEARMANS_RANK;
		geneRho = new Double[nelements][nelements];
		conditionRho = new Double[nattrs][nattrs];
		
		for(int i=0; i < nelements; i++){
			for(int j = i+1;j < nelements;j++){
				geneRho[i][j] = spearman.getMetric(matrix, matrix, matrix.getWeights(), i, j);
			}			
		}
		
		for(int i=0; i< nattrs; i++){
			for(int j = i+1; j < nattrs;j++){
				conditionRho[i][j] = spearman.getMetric(matrix_t, matrix_t, matrix_t.getWeights(), i, j);
			}			
		}
	}
	
	private void generateCSI() {
		csi = new ArrayList<Map<Integer,Map<Integer,Double>>>(nelements); 
		
		for(int i = 0; i < nelements-2; i++){
			Map<Integer,Map<Integer,Double>> jmap = new HashMap<Integer,Map<Integer,Double>>();
			
			for(int j = i+1; j < nelements-1; j++){
				Map<Integer,Double> kmap = new HashMap<Integer,Double>();
				
				for(int k = j+1; k < nelements; k++){
					double sum = 0;
					for(int l = 0; l < nattrs-1;l++){
						if(discrete_matrix[i][l] == discrete_matrix[j][l] && discrete_matrix[i][l] == discrete_matrix[k][l]){
							sum += 1.0;
						}
					}
					sum /= maxCSL[i];
					kmap.put(k, sum);
				}
				jmap.put(j, kmap);
			}			
			csi.add(i,jmap);
		}
	}
	
	private void generateCSL() {
		csl = new ArrayList<Map<Integer,List<Boolean>>>(nelements);
		maxCSL = new int[nelements];
		for(int i = 0; i < nelements; i++){
			Map<Integer,List<Boolean>> simlistMap = new HashMap<Integer,List<Boolean>>();
			maxCSL[i] = -1;
			for(int j = i+1; j < nelements ; j++){
				
				List<Boolean> simlist = new ArrayList<Boolean>(nattrs-1);
				int trueCount = 0;
				for(int k = 0; k < nattrs-1; k++){
					if(discrete_matrix[i][k] == discrete_matrix[j][k]){
						simlist.add(true);
						trueCount++;
					}
					else simlist.add(false);						
				}
				if (trueCount > maxCSL[i])maxCSL[i] = trueCount;
				simlistMap.put(j, simlist);
			}
			csl.add(simlistMap);
		}		
	}
	
	private List<Map<Integer, Integer>> generateDag() {
		List<Map<Integer,Integer>> graph = new ArrayList<Map<Integer,Integer>>();
		
		for(int i = 0; i < nelements; i++){
			Map<Integer,Integer> edges = new HashMap<Integer,Integer>();
			for(int j = i+1; j < nelements ; j++){
				edges.put(j, Collections.frequency(csl.get(i).get(j), true));
			}
			graph.add(edges);
		}
		return graph;
	}
	private int[][] getDiscreteMatrix() {
		int M[][] = new int[nelements][nattrs-1];
		for(int i = 0 ;i < nelements; i++){
			for(int j = 0; j < nattrs-1; j++){
				Double current = matrix.getValue(i, j);
				if (current==null) current = 0.0;
				Double next = matrix.getValue(i, j+1);
				if (next==null) next = 0.0;
				
				if (current > next)M[i][j] = -1;
				else if(current < next)M[i][j] = 1;
				else M[i][j] = 0; 
			}			
		}		
		return M;
	}
	
	private List<Integer> getCommonConditions(int i, int k) {
		Set<Integer> newConds = new HashSet<Integer>();
		for(int l = 0; l < nattrs-1; l++){
			if(discrete_matrix[i][l] == discrete_matrix[k][l]){
				newConds.add(l);
				newConds.add(l+1);
			}
		}
		return new ArrayList(newConds);
	}
	
	public List<Integer> union(List<Integer> a, List<Integer> b){
		List<Integer> unionList = new ArrayList<Integer>(a);
		unionList.removeAll(b);
		unionList.addAll(b);
		Collections.sort(unionList);
		return unionList;
	}
	
	public List<Integer> intersection(List<Integer> a, List<Integer> b){
		List<Integer> intersectionList = new ArrayList<Integer>(a);
		intersectionList.retainAll(b);		
		return intersectionList;
	}
	
	public Map<Integer, List<Long>> getClusterNodes(){
		return clusterNodes;
	}
	
	public Map<Integer, List<String>> getClusterAttrs(){
		return clusterAttrs;
	}
}
