package main;

import java.io.Serializable;
import java.util.ArrayList;

import org.neuroph.core.data.DataSet;
import org.neuroph.core.transfer.TransferFunction;

public class NNJobMessage implements Serializable {
	private String payload;
    private DataSet dataset;
	private DataSet testSet;
    private int dataPerReplica;
	private int testDataPerReplica;
    private ArrayList<Integer> layerDimensions;
    private double learningRate;
    private TransferFunction activation;
	private int epochs;    
	private String optimizer;    
    
    public NNJobMessage(String payload, DataSet dataset, DataSet testSet, int dataPerReplica, int testDataPerReplica, TransferFunction activation, ArrayList<Integer> layerDimensions, String optimizer, double learningRate, int epochs) {
    	this.payload = payload;
        this.dataset = dataset;
        this.dataPerReplica = dataPerReplica;
		this.testDataPerReplica = testDataPerReplica;
        this.activation = activation;
        this.layerDimensions = layerDimensions;
        this.learningRate = learningRate;
		this.testSet = testSet;
		this.epochs = epochs;
		this.optimizer = optimizer;
    }

	public NNJobMessage(String payload, DataSet dataset, DataSet testSet, int dataPerReplica, int testDataPerReplica, TransferFunction activation, ArrayList<Integer> layerDimensions, String optimizer, int epochs) {
    	this.payload = payload;
        this.dataset = dataset;
        this.dataPerReplica = dataPerReplica;
		this.testDataPerReplica = testDataPerReplica;
        this.activation = activation;
        this.layerDimensions = layerDimensions;
		this.learningRate = 0;
		this.testSet = testSet;
		this.epochs = epochs;
		this.optimizer = optimizer;
    }

	public int getTestPerReplica() {
		return testDataPerReplica;
	}

	public int getNumOfEpoch() {
		return epochs;
	}

	public DataSet getTestData() {
		return testSet;
	}
       
    public TransferFunction getActivation() {
		return activation;
	}

	public String getPayload() {
		return payload;
	}

	public int getDataPerReplica() {
		return dataPerReplica;
	}

	public ArrayList<Integer> getLayerDimensions() {
		return layerDimensions;
	}

	public double getLearningRate() {
		return learningRate;
	}

	public DataSet getDataset() {
    	return dataset;
    }

	public String getOptimizer() {
    	return optimizer;
    }
}