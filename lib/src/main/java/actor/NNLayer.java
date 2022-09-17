package actor;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.Random;

import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.LinearAlgebra.InverterFactory;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.neuroph.core.transfer.TransferFunction;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import utility.NNOperationTypes;
import utility.NNOperations;
import utility.SoftMax;
import utility.WorkerRegionEvent;


public class NNLayer extends AbstractActor {
	// Receives id, activation (for forward prop), parentLayer ref and corressponding PS shard id
	// First updates the layer weights from PS actor ref
	// Perform forward prop -> multiply activation and layer weights until the current layer doesn't have a child
	// Perform backprop -> compute gradient, compute deltas
	private int ps_id;
	private TransferFunction activation;
	private ActorRef parentRef;
	private ActorRef childRef;
	private ActorRef psShardRef;
	private Basic2DMatrix layerWeights;
	private Basic2DMatrix activations;
	private Basic2DMatrix outputs;
	private Basic2DMatrix lambda;
	private Basic2DMatrix labels;
	private Vector activatedInput;
	private final ActorSelection master;

	// ps_id required?
	public NNLayer(int ps_id, int numberOfLayers, int numberofRows, TransferFunction activation, ActorRef parentRef, ActorRef childRef, ActorRef psShardRef, Basic2DMatrix lambda, Basic2DMatrix labels) {
		this.ps_id = ps_id;
		this.activation = activation;
		this.parentRef = parentRef;
		this.childRef = childRef;
		this.psShardRef = psShardRef;
		this.activatedInput = null;
		this.activations =Basic2DMatrix.random(numberOfLayers, numberofRows, new Random());
		this.outputs =Basic2DMatrix.random(numberOfLayers, numberofRows, new Random());
		this.lambda = lambda;
		this.labels = labels;
		master = getContext().actorSelection("akka://MasterSystem@master:2550/user/master");
	}
	
	public void setChildRef(ActorRef childRef) {
		this.childRef = childRef;
	}

	@Override
	public Receive createReceive() {
		System.out.println("NNLayer actor received message");
		return receiveBuilder()
				.match(ActorRef.class, this::setChildRef)
				.match(NNOperationTypes.ParameterRequest.class, this::weightsRequest)
				.match(NNOperationTypes.UpdateWeightParam.class, this::updateWeightParam)
				.match(NNOperationTypes.GetWeights.class, this::getWeights)
				.match(NNOperationTypes.AdmmPredict.class, this::admmPredict)
				.match(NNOperationTypes.ForwardProp.class, this::forwardProp)
				.match(NNOperationTypes.BackProp.class, this::backProp)
			//	.match(String.class, this::updateWeights)
				.build();
	}
	
	public void weightsRequest(NNOperationTypes.ParameterRequest paramReq) throws TimeoutException, InterruptedException {
		Timeout timeout = Timeout.create(Duration.ofSeconds(3));
		Future<Object> future = Patterns.ask(psShardRef, paramReq, timeout);
		String result = (String) Await.result(future, timeout.duration());
		//System.out.println(result);
		if(result.length() > 0) {
			layerWeights = (Basic2DMatrix) Matrix.fromCSV(result);	
			sender().tell(new NNOperationTypes.ParameterResponse(), self());
		//	System.out.println("weightsRequest completed in NNLayer!");
		}
	}

	public void getWeights(NNOperationTypes.GetWeights req) throws TimeoutException, InterruptedException {
		req.weights = layerWeights.toCSV();
		req.outputs = outputs.toCSV();
		sender().tell(req, self());
	}

	public void updateWeightParam(NNOperationTypes.UpdateWeightParam req) throws TimeoutException, InterruptedException {
		// Update table
		String nodeHost;
		nodeHost = getContext().provider().getDefaultAddress().getHost().get();
		master.tell(new WorkerRegionEvent.UpdateTable(nodeHost, 1), self());

		// Pre process
		Basic2DMatrix previousActivations = (Basic2DMatrix) Matrix.fromCSV(req.activations);

		// Define hyperparameters
		double gamma = 5.0;
		double beta = 5.0;

		// Compute the two params
		System.out.println("al-1: " + previousActivations.rows() + "*" + previousActivations.columns());
		System.out.println("zl: " + outputs.rows() + "*" + outputs.columns());
		System.out.println("wl: " + layerWeights.rows() + "*" + layerWeights.columns());
		
		req.param1 = outputs.multiply(previousActivations.transpose()).toCSV();
		req.param2 = previousActivations.multiplyByItsTranspose().toCSV();

		// Update and request weights for the current layer
		// psShardRef.tell(req, getSelf());
		Timeout timeout = Timeout.create(Duration.ofSeconds(3));
		Future<Object> future = Patterns.ask(psShardRef, req, timeout);
		String result = (String) Await.result(future, timeout.duration());
		layerWeights = (Basic2DMatrix) Matrix.fromCSV(result);

		// Handle Last layer seperately
		if (childRef == null) {
			// Update output of last layer
			Matrix m = layerWeights.multiply(previousActivations);
			outputs = (Basic2DMatrix) labels.add(m.multiply(beta).subtract(lambda)).divide(beta+1);
			// Update lambda
			lambda = (Basic2DMatrix) lambda.add(outputs.subtract(m).multiply(beta));
			// Stop epoch
			master.tell(new WorkerRegionEvent.UpdateTable(nodeHost, 1), self());
			getContext().parent().tell(new NNOperationTypes.DoneEpoch(), self());
		}
		else {
			// Get weights and outputs of next layer
			future = Patterns.ask(childRef, new NNOperationTypes.GetWeights(), timeout);
			NNOperationTypes.GetWeights nextLayerParams = (NNOperationTypes.GetWeights) Await.result(future, timeout.duration());
			Basic2DMatrix nextLayerWeights = (Basic2DMatrix) Matrix.fromCSV(nextLayerParams.weights);
			Basic2DMatrix nextLayerOutputs = (Basic2DMatrix) Matrix.fromCSV(nextLayerParams.outputs);
			
			System.out.println("wl+1: " + nextLayerWeights.rows() + "*" + nextLayerWeights.columns());
			System.out.println("zl+1: " + nextLayerOutputs.rows() + "*" + nextLayerOutputs.columns());

			// Update activations locally

			Matrix m1 = nextLayerWeights.transpose().multiplyByItsTranspose().multiply(beta);
			Matrix m2 = Matrix.identity(m1.rows()).multiply(beta);
			Matrix av = m1.add(m2).withInverter(InverterFactory.SMART).inverse();
			
			Matrix m3 = nextLayerWeights.transpose().multiply(nextLayerOutputs).multiply(beta);
			Matrix m4 = outputs.transform(new NNOperations.RELU()).multiply(beta);
			Matrix af = m3.add(m4);
			
			activations = (Basic2DMatrix) av.multiply(af);

			// Update outputs
			Matrix m = layerWeights.multiply(previousActivations);
			Matrix sol1 = activations.multiply(gamma).add(m.multiply(beta)).divide(gamma+beta);
			Matrix sol2 = m;
			Matrix z1 = sol1.transform(new NNOperations.PositiveElements());
			Matrix z2 = sol2.transform(new NNOperations.NegativeElements());

			Matrix fz1 = activations.subtract(z1.transform(new NNOperations.RELU())).multiply(gamma);
			fz1 = fz1.add(z1.subtract(m).transform(new NNOperations.Square()).multiply(beta));
			fz1 = fz1.transform(new NNOperations.Square());

			Matrix fz2 = activations.subtract(z2.transform(new NNOperations.RELU())).multiply(gamma);
			fz2 = fz2.add(z2.subtract(m).transform(new NNOperations.Square()).multiply(beta));
			fz2 = fz2.transform(new NNOperations.Square());
			
			outputs = (Basic2DMatrix) fz1.subtract(fz2).transform(new NNOperations.NegativeElements()).add(fz2);
			
			// Move to next layer
			master.tell(new WorkerRegionEvent.UpdateTable(nodeHost, 1), self());
			childRef.tell(new NNOperationTypes.UpdateWeightParam(activations.toCSV()), self());
		}
	}

	public void admmPredict(NNOperationTypes.AdmmPredict params) {
		Vector input = params.input;
		Vector label = params.label;
		
		if(parentRef != null) {
			this.activatedInput = NNOperations.applyActivation(input, activation);
		}
		else {
			this.activatedInput = input;
		}
		
		System.out.println("Activated Input: " + this.activatedInput);
		System.out.println("Layer weights: " + layerWeights.rows() + "*" + layerWeights.columns());
		Vector output = layerWeights.multiply(this.activatedInput);
		
		if(childRef != null) {
			childRef.tell(new NNOperationTypes.AdmmPredict(output, label), getSelf());
		}
		else {
			Vector activatedOutput = NNOperations.applyActivation(output, activation);

			// Compare actual and predicted label
			DataShard.testPointCount++;
			if(getOuputIndex(label) == getOuputIndex(activatedOutput)) {
				DataShard.accuracy += 1;
			}
			if(DataShard.testPointCount == DataShard.testSetPart.size()) {
				DataShard.accuracy = (DataShard.accuracy / DataShard.testPointCount) * 100;
				System.out.println("Accuracy of this test set part [ADMM]: " + DataShard.accuracy);
			}
			System.out.println("Done!");			
		}
	}

	public int getOuputIndex(Vector x) {
		int maxInd = 0;
		for(int i = 0; i < x.length(); i++) 
			if(x.get(i) > x.get(maxInd))
				maxInd = i;

		return maxInd;
	}

	public void forwardProp(NNOperationTypes.ForwardProp forwardParams) {
//		System.out.println("In forward prop");
		Vector inputs = forwardParams.x;
		Vector target = forwardParams.y;

//		System.out.println("Inputs: " + inputs + " targets: " +  target);
		
		if(parentRef != null) {
	//		System.out.println("Has parent");
			this.activatedInput = NNOperations.applyActivation(inputs, activation);
		}
		else {
	//		System.out.println("No parent");
			this.activatedInput = inputs;
		}
			
	//	System.out.println("Activated Input: " + activatedInput) ;
	//	System.out.println("Layer weights: " + layerWeights);
		
		// vector = vector * matrix
		Vector outputs = this.activatedInput.multiply(layerWeights);
	//	System.out.println("Output " + outputs) ;
		
		if(childRef != null) {
	//		System.out.println("Has child");
			childRef.tell(new NNOperationTypes.ForwardProp(outputs, target, forwardParams.isTestData), getSelf());
		}
		else {
	//		System.out.println("No child");
			Vector activatedOutputs = NNOperations.applyActivation(outputs, activation);
	//		System.out.println("Activated Outputs " + activatedOutputs);
	//		System.out.println("Actual Output: " + target);

			if (forwardParams.isTestData) {
				// Compare actual and predicted label
				DataShard.testPointCount++;
				System.out.println("Actual: " + getOuputIndex(target));
				System.out.println("Predicted: " + getOuputIndex(activatedOutputs));
				if(getOuputIndex(target) == getOuputIndex(activatedOutputs)) {
	//				System.out.println("Correct prediction");
					DataShard.accuracy += 1;
				}
				else {
	//				System.out.println("Wrong prediction");
				}
				if(DataShard.testPointCount == DataShard.testSetPart.size()) {
					DataShard.accuracy = (DataShard.accuracy / DataShard.testPointCount) * 100;
					System.out.println("Accuracy of this test set part: " + DataShard.accuracy);
				}
				System.out.println("Done!");			
			}
			else{
				// Compute error and start backprop
				Vector delta = NNOperations.errorDerivative(activatedOutputs, target);
			
		//		System.out.println("Delta: " + delta);
				// Outer product of delta and activatedInputs  
				Basic2DMatrix gradient = NNOperations.computeGradient(delta, activation, this.activatedInput);
		//		System.out.println("Gradient: " + gradient);
				psShardRef.tell(new NNOperationTypes.Gradient(gradient.toCSV()), getSelf());
				Vector parentDelta = NNOperations.computeDelta(delta, layerWeights, activation, this.activatedInput);
	//			System.out.println("Parent Delta " + parentDelta);
				parentRef.tell(new NNOperationTypes.BackProp(parentDelta), getSelf());
			}
		}
	}
	
	public void backProp(NNOperationTypes.BackProp backParams) {
	//	System.out.println("In backprop!");
		Vector childDelta = backParams.childDelta;
	//	System.out.println("Child Delta " + childDelta);
		
		Matrix gradient = NNOperations.computeGradient(childDelta, activation, this.activatedInput);
	//	System.out.println("Gradient" + gradient);
		psShardRef.tell(new NNOperationTypes.Gradient(gradient.toCSV()), getSelf());
	
	//	 System.out.println("Layer weights" + layerWeights);
		if(parentRef != null) {
	//		System.out.println("Has parent");
			Vector parentDelta = NNOperations.computeDelta(childDelta, layerWeights, activation, this.activatedInput);
	//		System.out.println("Parent Delta " + parentDelta);
			parentRef.tell(new NNOperationTypes.BackProp(parentDelta), getSelf());
		}
		else {
	//		System.out.println("No parent");
			// -1 table entry here
			String nodeHost = getContext().provider().getDefaultAddress().getHost().get();
	//		System.out.println("Address of node of routee: " + nodeHost);
			master.tell(new WorkerRegionEvent.UpdateTable(nodeHost, -1), self());
			
			getContext().parent().tell(new NNOperationTypes.WeightUpdate(false), getSelf());
		}
	}
}
