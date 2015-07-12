/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.pca;

/**
 *
 * @author root
 */
public interface RunPCA {
    public static int PCA_NODE_ATTRIBUTES = 1;
    public static int PCA_EDGE_ATTRIBUTES = 2;
    public static int PCA_DISTANCE_METRIC = 3;
    public void computePCA();
    
    // this method assumes that eigen values returned by DenseDoubleEigenvalueDecomposition class
    // are sorted in increasing order
    public ComputationMatrix[] computePCs(ComputationMatrix matrix);
    
    // this method assumes that eigen values returned by DenseDoubleEigenvalueDecomposition class
    // are not sorted in their order from maximum to minimum
    public ComputationMatrix[] computePCsSorted(ComputationMatrix matrix);
}
