package main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import org.neuroph.core.data.DataSet;

import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;

import actor.ClusterListener;
import actor.Master;
import utility.configs; 
import org.neuroph.core.transfer.RectifiedLinear;

public class MainMaster {	
	public static void main(String[] args) {
		System.out.println("In MainMaster");
		String port = args[0];
        Config config = configs.getConfig(port, "master", "master.conf");
		String clusterName = config.getString("clustering.cluster.name");
		
		ActorSystem system = ActorSystem.create(clusterName, config);
		System.out.println("Created master actor system");
		
		system.actorOf(Props.create(ClusterListener.class), "cluster-listener-master");
      
        ActorRef master = system.actorOf(Props.create(Master.class), "master");
        
       // master.tell(new MasterWorkerProtocol.RegisterWorkerRegion("2550", master), master);
    	FiniteDuration interval = Duration.create(10, TimeUnit.SECONDS);
        Timeout timeout = new Timeout(Duration.create(15, TimeUnit.SECONDS));
        ExecutionContext ec = system.dispatcher();
        
        DataSet trainingSet = DataSet.createFromFile("/root/datasets/iris_train.csv", 4, 1, ",");
		DataSet testSet = DataSet.createFromFile("/root/datasets/iris_test.csv", 4, 1, ",");

		System.out.println("Dataset inited: " + trainingSet.size());
		System.out.println("Dataset inited: " + testSet.size());
        
		ArrayList<Integer> layerDimensions = new ArrayList<>(List.of(4, 4, 4, 3));
		// Sigmoid sigmoid = new Sigmoid();		
		RectifiedLinear rl  = new RectifiedLinear();

		int routee_num = Integer.parseInt(config.getString("akka.actor.deployment./workerRegion/workProcessorRouter.nr-of-instances"));
        
      /*   system.scheduler().schedule(interval, interval, () -> Patterns.ask(master, new NNJobMessage("XOR_task1", trainingSet, 15, sigmoid, layerDimensions, 0.1), timeout)
         		.onComplete(result -> {
                     System.out.println(result);do
                     return CompletableFuture.completedFuture(result);
                 }, ec)
         		, ec);
        */ 
//		system.scheduler().scheduleOnce(interval, master, new NNJobMessage("XOR_task1", trainingSet, 4, sigmoid, layerDimensions, 0.1), system.dispatcher(), null);

		// Forest fire dataset: http://www3.dsi.uminho.pt/pcortez/forestfires/
		system.scheduler().scheduleOnce(interval, master, new NNJobMessage("iris_task", trainingSet, testSet, 25, 25, rl, layerDimensions, "sgd", 0.1, 100), system.dispatcher(), null);
		
		//system.scheduler().scheduleOnce(interval, master, new NNJobMessage("wine_task", trainingSet, testSet, 75, 75, rl, layerDimensions, "sgd", 0.1, 100), system.dispatcher(), null);
		// system.scheduler().scheduleOnce(interval, master, new NNJobMessage("iris_task", trainingSet, testSet, 75, 75, rl, layerDimensions, "admm", 1), system.dispatcher(), null);
	}
}