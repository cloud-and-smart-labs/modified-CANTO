package actor;

import akka.actor.AbstractActor;
import utility.NNOperationTypes;

import java.io.Serializable;

import org.la4j.Matrix;
import org.la4j.LinearAlgebra.InverterFactory;
import org.la4j.matrix.dense.Basic2DMatrix;

public class ParameterServerShard extends AbstractActor implements Serializable { 
	// Receives learningRate (for weights update) and initial random weights.

	private int ps_id;
	private double learningRate;
	private Matrix weights;
	private Matrix params1Sum;
	private Matrix params2Sum;
	private int counterAdmm;
	
	public ParameterServerShard(int ps_id, double learningRate, Matrix weights) {
		System.out.println("########!!!!!!" + weights);
		this.ps_id = ps_id;
		this.learningRate = learningRate;
		this.weights = weights;
		this.counterAdmm = 0;
		this.params1Sum = Matrix.zero(weights.rows(), weights.columns());
		this.params2Sum = Matrix.zero(weights.rows(), weights.columns());
	}

	@Override
	public Receive createReceive() {
		System.out.println("ParameterServerShard actor received message");
		return receiveBuilder()
				.match(NNOperationTypes.ParameterRequest.class, this::getLatestParameters)
				.match(NNOperationTypes.UpdateWeightParam.class, this::updateWeightsAdmm)
				.match(NNOperationTypes.Gradient.class, this::updateWeights)
				.match(String.class, this::setWeights)
				.build();
	}
	
	// Just for testing framework correctness
	public void setWeights(String s) {
		System.out.println("setWeights");
		this.weights = Matrix.fromCSV(s);
		System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ " + weights);
		sender().tell("success", self());
	}

	public void getLatestParameters(NNOperationTypes.ParameterRequest paramReq) {
		System.out.println("getLatestParams");
//		System.out.println("Lastest weights are: " + this.weights);
		sender().tell(this.weights.toCSV(), getSelf());
	}
	
	public void updateWeights(NNOperationTypes.Gradient g) {
		System.out.println("updateWeights");
		Basic2DMatrix grad = Basic2DMatrix.fromCSV(g.getGradient());
		weights = regularize(weights.add(grad.multiply(learningRate)));
	}

	public void updateWeightsAdmm(NNOperationTypes.UpdateWeightParam req) {
		System.out.println("updateWeightsAdmm");
		params1Sum = params1Sum.add(req.param1);
		params2Sum = params2Sum.add(req.param2);
		counterAdmm++;
		if (counterAdmm == 10) { // Change the number to no of DSs
			weights = params1Sum.multiply(params2Sum.withInverter(InverterFactory.SMART).inverse());
			counterAdmm = 0;
		}
	}

	public Matrix regularize(Matrix weights) {
		return weights.divide(weights.manhattanNorm());
	}
}