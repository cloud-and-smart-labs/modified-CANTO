package actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import utility.NNOperationTypes;
import java.util.ArrayList;
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
	private int numberOfDataShards;
	private ArrayList<ActorRef> dsRefs;
	
	public ParameterServerShard(int ps_id, double learningRate, Matrix weights, int numberOfDataShards) {
		System.out.println("########!!!!!!" + weights);
		this.ps_id = ps_id;
		this.learningRate = learningRate;
		this.weights = weights;
		this.counterAdmm = 0;
		this.numberOfDataShards = numberOfDataShards;
		this.dsRefs = new ArrayList<ActorRef>();
		this.params1Sum = Matrix.zero(weights.rows(), weights.columns());
		this.params2Sum = Matrix.zero(weights.columns(), weights.columns());
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
	//	System.out.println("setWeights");
		this.weights = Matrix.fromCSV(s);
		System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ " + weights);
		sender().tell("success", self());
	}

	public void getLatestParameters(NNOperationTypes.ParameterRequest paramReq) {
	//	System.out.println("getLatestParams");
//		System.out.println("Lastest weights are: " + this.weights);
		sender().tell(this.weights.toCSV(), getSelf());
	//	System.out.println("sent latest params");
	}
	
	public void updateWeights(NNOperationTypes.Gradient g) {
	//	System.out.println("updateWeights");
		Basic2DMatrix grad = Basic2DMatrix.fromCSV(g.getGradient());
		weights = regularize(weights.add(grad.multiply(learningRate)));
	}

	public void updateWeightsAdmm(NNOperationTypes.UpdateWeightParam req) {
		System.out.println("Sender: " + sender());
		System.out.println("param1: " + Matrix.fromCSV(req.param1).rows() + "*" + Matrix.fromCSV(req.param1).columns());
		System.out.println("param2: " + Matrix.fromCSV(req.param2).rows() + "*" + Matrix.fromCSV(req.param2).columns());

		params1Sum = params1Sum.add(Matrix.fromCSV(req.param1));
		params2Sum = params2Sum.add(Matrix.fromCSV(req.param2));
		dsRefs.add(sender());
		counterAdmm++;
		if (counterAdmm == numberOfDataShards) {
			System.out.println("updateWeightsAdmm");
			weights = params1Sum.multiply(params2Sum.withInverter(InverterFactory.SMART).inverse());
			
			for (ActorRef dsRef : dsRefs) {
				dsRef.tell(weights.toCSV(), self());
			}

			params1Sum = Matrix.zero(weights.rows(), weights.columns());
			params2Sum = Matrix.zero(weights.rows(), weights.columns());
			dsRefs = new ArrayList<ActorRef>();
			counterAdmm = 0;
		}
	}

	public Matrix regularize(Matrix weights) {
		return weights.divide(weights.manhattanNorm());
	}
}