package actor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.transfer.TransferFunction;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import utility.NNOperationTypes;
import utility.NNOperations;
import utility.WorkerRegionEvent;

public class DataShard extends AbstractActor {
	// Receives a partition of training data, activation (to pass on to the layer actors) and a list of PS shard actor refs
	// Creates the layer actors and link them
	// The layers are updated from PS shards after each data point/mini-batch
	// Then initiate forward pass for the next data point/mini-batch
	
	private int d_id;
	private ArrayList<DataSetRow> dataSetPart;
	public static ArrayList<DataSetRow> testSetPart;      // Made public and static for reference in NNLayer convenience. To check if all test points have been covered.    TO-DO: Find better way.
	private TransferFunction activation;
	private ArrayList<ActorRef> parameterShardRefs;
	private ArrayList<ActorRef> layerRefs; 
	private Iterator<DataSetRow> dsIter;
	private final ActorSelection master;
	private int lastLayerNeurons;
	//private ActorSelection nnMaster;
	public static double accuracy;
	public static int testPointCount;
	public int epochs;
	public int epochCount;
	public int routee_num;
	public ArrayList<Integer> layerDimensions;
	private Basic2DMatrix lambda;
	private Basic2DMatrix input;
	private Basic2DMatrix labels;
	private String optimizer;

	public DataShard() {
		master = getContext().actorSelection("akka://MasterSystem@master:2550/user/master");
		accuracy = 0;
		testPointCount = 0;
		epochCount = 0;
	}
	
	@Override
	public Receive createReceive() {
		System.out.println("DataShard actor received message");
		return receiveBuilder()
		//		.match(NNJobMessage.class, this::createLayerActors)
				.match(NNOperationTypes.Dummy.class, this::dummy)
				.match(NNOperationTypes.Predict.class, this::prediction)
				.match(NNOperationTypes.WeightUpdate.class, this::fetchWeights)
				.match(NNOperationTypes.DoneUpdatingWeights.class, this::startTraining)
				.match(NNOperationTypes.DoneEpoch.class, this::startADMMTraining)
				.match(NNOperationTypes.DataShardParams.class, this::setParameters)
				.match(String.class, this::successMsg)
				.matchAny(this::handleAny)
				.build();
	}

	private void handleAny(Object o) {
		System.out.println("Actor received unknown message: " + o.toString());
	}
	
	public void dummy(NNOperationTypes.Dummy d) {
		System.out.println("In dummy!!");
	}
	
	public void setParameters(NNOperationTypes.DataShardParams dsParams) {
		System.out.print("Data Shard set params method");
		this.d_id = dsParams.d_id;
		this.dataSetPart = dsParams.dataSetPart;
		this.testSetPart = dsParams.testSetPart;
		this.activation = dsParams.activation;
		this.parameterShardRefs = dsParams.parameterShardRefs;
		this.lastLayerNeurons = dsParams.lastLayerNeurons;
		this.epochs = dsParams.epochs;
		this.routee_num = dsParams.routee_num;
		this.layerDimensions = dsParams.layerDimensions;
		this.optimizer = dsParams.optimizer;
		this.lambda = Basic2DMatrix.unit(lastLayerNeurons, dataSetPart.size());
		
		// Build input and labels
		DataSetRow sampleRow = dataSetPart.get(0);
		int inputLen = sampleRow.getInput().length;
		Vector sampleY = Vector.fromArray(sampleRow.getDesiredOutput());
		int labelLen = NNOperations.oneHotEncoding(sampleY, lastLayerNeurons).length();
		this.input = (Basic2DMatrix) Matrix.unit(1, inputLen);
		this.labels = (Basic2DMatrix) Matrix.unit(1, labelLen);
		
		for(DataSetRow row: dataSetPart) {
			Vector x = Vector.fromArray(row.getInput());
			Vector y = Vector.fromArray(row.getDesiredOutput());
			y = NNOperations.oneHotEncoding(y, lastLayerNeurons);
			// System.out.println("Data point: " + x + ", output: " + y);
			this.input = (Basic2DMatrix) this.input.insertRow(0, x);
			this.labels = (Basic2DMatrix) this.labels.insertRow(0, y);
		}
		this.input = (Basic2DMatrix) this.input.removeLastRow();
		this.input = (Basic2DMatrix) this.input.transpose();
		this.labels = (Basic2DMatrix) this.labels.removeLastRow();
		this.labels = (Basic2DMatrix) this.labels.transpose();

		dsIter = dataSetPart.iterator();
		createLayerActors();		
	}
	
	public void successMsg(String s) {
		System.out.println("Layer actor creation success. ****** "); // + self().path());
		sender().tell("success", getSelf());
		System.out.println("Init training!");
		getSelf().tell(new NNOperationTypes.WeightUpdate(false), getSelf());
	}
	
	public void createLayerActors() {
		System.out.println("Creating layer actors!");
		int n = this.parameterShardRefs.size();
		layerRefs = new ArrayList<ActorRef>();
		
		layerRefs.add(getContext().actorOf(Props.create(NNLayer.class, 0, layerDimensions.get(1), dataSetPart.size(), activation, null, null, parameterShardRefs.get(0), null, null), d_id + "layer0"));
	
		for(int i = 1; i < n-1; i++) {
			System.out.println("Layer: " + i);			
			layerRefs.add(getContext().actorOf(Props.create(NNLayer.class, i, layerDimensions.get(i+1), dataSetPart.size(), activation, layerRefs.get(i-1), null, parameterShardRefs.get(i), null, null), d_id + "layer" + i));
			//System.out.println("Layer " + i + " parent: " + layerRefs.get(i).path().parent());
		}
		layerRefs.add(getContext().actorOf(Props.create(NNLayer.class, n-1, layerDimensions.get(n), dataSetPart.size(), activation, layerRefs.get(n-2), null, parameterShardRefs.get(n-1), lambda, labels), d_id + "layer" + (n-1)));
		
		System.out.println("Linking the child actors");
		for(int i = 0; i < n-1; i++) {
			layerRefs.get(i).tell(layerRefs.get(i+1), self());
		}
		getSelf().tell("success", sender());		
	}
	 
	public void fetchWeights(NNOperationTypes.WeightUpdate msg) throws TimeoutException, InterruptedException {
	//	System.out.println("DS actor fetching current weights");
		Timeout timeout = Timeout.create(Duration.ofSeconds(5));
		
		for(ActorRef l: layerRefs) {
			//l.tell(new NNOperationTypes.ParameterRequest(), self());
			Future<Object> future = Patterns.ask(l, new NNOperationTypes.ParameterRequest(), timeout);
			Object result = Await.result(future, timeout.duration());
			if(result.getClass() != NNOperationTypes.ParameterResponse.class) {
				System.out.println("Current weights could NOT be retrieved!");
				return;
			}	
		}
	//	System.out.println("Current weights retrieved successfully.");
		if(!msg.isTest) {
			if ("sgd".equals(optimizer))
				getSelf().tell(new NNOperationTypes.DoneUpdatingWeights(), getSelf());
			else if ("admm".equals(optimizer))
				getSelf().tell(new NNOperationTypes.DoneEpoch(), getSelf());
		}
	}
	
	// Once weights are updated, then forwardProp is initiated
	public void startTraining(NNOperationTypes.DoneUpdatingWeights msg) {
		//self().tell(new NNOperationTypes.Predict(), self());
		String nodeHost;
		// System.out.println("In startTraining method!!");
		if(dsIter.hasNext()) {
			DataSetRow ds_row = dsIter.next();
		//	System.out.println("Current row: " + ds_row);
			Vector x = Vector.fromArray(ds_row.getInput());
			Vector y = Vector.fromArray(ds_row.getDesiredOutput());

			// +1 table entry
			nodeHost = getContext().provider().getDefaultAddress().getHost().get();
	//		System.out.println("Address of node of routee: " + nodeHost);
			master.tell(new WorkerRegionEvent.UpdateTable(nodeHost, 1), self());
			
			layerRefs.get(0).tell(new NNOperationTypes.ForwardProp(x, NNOperations.oneHotEncoding(y, lastLayerNeurons), false), getSelf());
		}
		else if(++this.epochCount < epochs) {
			System.out.println("Epoch " + this.epochCount + " done");
			dsIter = dataSetPart.iterator(); 
			getSelf().tell(new NNOperationTypes.DoneUpdatingWeights(), getSelf());
			
		}
		else {
			NNMaster.routeeReturns++;
			if(NNMaster.routeeReturns == routee_num) {
				// Send trained weights back to master.
				ActorSelection master = getContext().actorSelection("akka://MasterSystem@master:2550/user/master");
				master.tell(new NNOperationTypes.SendWeights(parameterShardRefs), self());
			}
			System.out.println("Routee returns so far: " + NNMaster.routeeReturns);
		//	System.out.println("Address of node of routee: " + getContext().provider().getDefaultAddress().getHost().get());
			self().tell(new NNOperationTypes.Predict(), self());
		}
	}

	public void startADMMTraining(NNOperationTypes.DoneEpoch req) {
		if (++this.epochCount <= epochs) {
			layerRefs.get(0).tell(new NNOperationTypes.UpdateWeightParam(input.toCSV()), self());
		}
		else {
			NNMaster.routeeReturns++;
			if(NNMaster.routeeReturns == routee_num) {
				// Send trained weights back to master.
				ActorSelection master = getContext().actorSelection("akka://MasterSystem@master:2550/user/master");
				master.tell(new NNOperationTypes.SendWeights(parameterShardRefs), self());
			}
			System.out.println("Routee returns so far: " + NNMaster.routeeReturns);
			self().tell(new NNOperationTypes.Predict("admm"), self());
		}
	}

	public void prediction(NNOperationTypes.Predict p) throws TimeoutException, InterruptedException {
		// Get predictions for test dataset part. Calculate accuracy
		
		System.out.println("Starting testing");
		System.out.println("Address of node of routee: " + getContext().provider().getDefaultAddress().getHost().get());
		
		for(DataSetRow test_row: testSetPart) {
			Vector x = Vector.fromArray(test_row.getInput());
			Vector y = Vector.fromArray(test_row.getDesiredOutput());
			System.out.println("Test data point: " + x + ", output: " + y);
			
			if (p.optimizer.equals("admm"))
				layerRefs.get(0).tell(new NNOperationTypes.AdmmPredict(x, NNOperations.oneHotEncoding(y, lastLayerNeurons)), getSelf());
			else
				layerRefs.get(0).tell(new NNOperationTypes.ForwardProp(x, NNOperations.oneHotEncoding(y, lastLayerNeurons), true), getSelf());
		}
	}
}