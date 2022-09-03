package main;

import com.typesafe.config.Config;

import actor.ClusterListener;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import utility.configs;
import actor.WorkerRegion;

public class MainWorker {
	public static void main(String[] args) {
		System.out.println("In MainWorker");

		String port = args[0];
		Config config = configs.getConfig(port, "worker", "master.conf");
		String clusterName = config.getString("clustering.cluster.name");
		
		ActorSystem system = ActorSystem.create(clusterName, config);
		
		//system.actorOf(FromConfig.getInstance().props(Props.create(PiWorker.class)), "router1");
		Cluster.get(system)
        .registerOnMemberUp(
        		() -> {
        			system.actorOf(Props.create(ClusterListener.class), String.format("listenerOn%s", port));
        			system.actorOf(WorkerRegion.props(port), "workerRegion");
        		});		
    }
}