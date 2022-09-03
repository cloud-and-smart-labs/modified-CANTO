package actor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import org.la4j.Matrix;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import akka.actor.ActorRef;
import main.NNJobMessage;
import scala.concurrent.Await;
import scala.concurrent.Future;
import utility.NNOperationTypes;

public class NNMaster extends AbstractActor {
	// Receives paramters like dataset, activation, etc. 
	// Responsible for creating data-shard and parameter-server-shard actors. Initiates the process on the routees.

	ArrayList<ActorRef> psRefs;
	private ActorRef workProcessorRouter;
	public static int routeeReturns = 0;
	public static DataSet testData;
	
	public static Props props(ActorRef workProcessorRouter) {
        return Props.create(NNMaster.class, workProcessorRouter);
    }
	
	@Override
	public Receive createReceive() {
		System.out.println("NNMaster actor received message");
		return receiveBuilder()
				.match(NNJobMessage.class, this::createActors)
				//.match(NNOperationTypes.Predict.class, this::startTesting)
				.matchAny(this::handleAny)
				.build();
	}

	private void handleAny(Object o) {
		System.out.println("nnMaster received unknown message: " + o.toString());
	}

	public NNMaster(ActorRef workProcessorRouter) {
		 this.workProcessorRouter = workProcessorRouter;
		 
	}
	
	public List<List<DataSetRow>> splitDataSet(DataSet d, int sizeOfSplit) {		
		List<List<DataSetRow>> dsSplits = new ArrayList<List<DataSetRow>>();

		int numOfSplits = (int)d.size()/sizeOfSplit;
		
		int j = 0;
		while(j < numOfSplits) {
			dsSplits.add(d.getRows().subList(j*sizeOfSplit, j*sizeOfSplit + sizeOfSplit));
			j += 1;
		}
		System.out.println(dsSplits);
		return dsSplits;
	}

	public void createActors(NNJobMessage nnmsg) throws TimeoutException, InterruptedException {
		List<List<DataSetRow>> splitDataSets = new ArrayList<List<DataSetRow>>();
		List<List<DataSetRow>> splitTestSets = new ArrayList<List<DataSetRow>>();

		// TODO: Split according to number of routees
		splitDataSets = splitDataSet(nnmsg.getDataset(), nnmsg.getDataPerReplica());
		splitTestSets = splitDataSet(nnmsg.getTestData(), nnmsg.getTestPerReplica());

		System.out.println("The sizes: " + splitDataSets.size() + "," +splitTestSets.size());
		
		System.out.println("Number of datasets: " + splitDataSets.size());
		
		// PS shard actors
		int n = nnmsg.getLayerDimensions().size();
		psRefs = new ArrayList<ActorRef>();
		System.out.println("Layer Dimensions!! " + nnmsg.getLayerDimensions());

		for(int i = 0; i < n - 1; i++) {
			int rows = nnmsg.getLayerDimensions().get(i);
			int cols = nnmsg.getLayerDimensions().get(i+1);

			System.out.println(rows + ", " + cols);
			Random r = new Random();
			double[][] weightMat = new double[rows][cols];

			// Generate random 2D array [-1, 1] of row x col
			for(int a = 0; a < rows; a++) {
				for(int b = 0; b < cols; b++) {
					weightMat[a][b] = r.nextDouble() * 2 - 1;
				}
			}

			Matrix weights = Matrix.from2DArray(weightMat);
			if (nnmsg.getOptimizer().equals("admm"))
				weights = weights.transpose();

			System.out.println("Creating PS shard actor for between " + i + " and " + (i+1));
			psRefs.add(getContext().actorOf(Props.create(ParameterServerShard.class, i, nnmsg.getLearningRate(), weights, nnmsg.getDataset().size()/nnmsg.getDataPerReplica()), "ps" + i));
		}
		
		// Send dataset part, psRefs, activation to each routee
		Timeout timeout = Timeout.create(Duration.ofSeconds(60));
		int c = 0;
		int lastLayerNeurons = nnmsg.getLayerDimensions().get(n-1);

		for(int j = 0; j < splitDataSets.size(); j++) {
			System.out.println("Datashard " + c + " init!");
		//	System.out.println("Split data: " + splitTestSets.get(j) + "\n" + splitDataSets.get(j));
		//	System.out.println("&&&&&&&&&&&: " + workProcessorRouter.path());
			Future<Object> future = Patterns.ask(workProcessorRouter, new NNOperationTypes.DataShardParams(c, nnmsg.getLayerDimensions(), new ArrayList<DataSetRow> (splitDataSets.get(j)), new ArrayList<DataSetRow> (splitTestSets.get(j)), nnmsg.getActivation(), lastLayerNeurons, nnmsg.getOptimizer(), nnmsg.getNumOfEpoch(), splitDataSets.size(), psRefs), timeout);
			String result = (String) Await.result(future, timeout.duration());
			System.out.println("The results##########: " + result);
			if(!result.equals("success")) {
				System.out.println("Something went wrong in layer creation");
				return;
			}				
			c++;
		}
		System.out.println("Required actors successfully created");
	//	System.out.println("<<<<<<<<<<<<<<<<<" + self().path());
	}
}