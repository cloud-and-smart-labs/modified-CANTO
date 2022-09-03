package actor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.pattern.Patterns;
import akka.routing.FromConfig;
import main.NNJobMessage;
import utility.MasterWorkerProtocol;

public class WorkerRegion extends AbstractActor{
	private String regionId;
	private final ActorRef workerRegion;
	private final ActorSelection master;
	private Address selfAddress;
	
	public static Props props(String regionId) {
        return Props.create(WorkerRegion.class, regionId);
    }
	
	 public WorkerRegion(String regionId) {
        this.regionId = regionId;
        master = getContext().actorSelection("akka://MasterSystem@master:2550/user/master");
        ActorSystem system = getContext().getSystem();
        ClusterShardingSettings settings = ClusterShardingSettings.create(system);
        
        ActorRef workProcessorRouter = getContext().actorOf(
                FromConfig.getInstance()
                        .withSupervisorStrategy(supervisorStrategy())
                        .props(Props.create(DataShard.class)),
                "workProcessorRouter");

        workerRegion = ClusterSharding.get(system)
                .start(
                        "workers",
                        Router.props(workProcessorRouter),
                        settings,
                        new WorkRegionMessageExtractor(5));
        
        registerRegionToMaster();  
    }
	 
	private void registerRegionToMaster() {
		Duration timeout = Duration.ofSeconds(5);
		
		selfAddress = getContext().provider().getDefaultAddress();
        System.out.println("Create table entry for: " + selfAddress.getHost().get());
        master.tell(selfAddress.getHost().get(), self());
        
		System.out.println("Add to nodeToRef table");
		master.tell(selfAddress, self());
		
		System.out.println("Asking master to register: " + workerRegion.path());
		CompletionStage<Object> future = Patterns.ask(master, new MasterWorkerProtocol.RegisterWorkerRegion(this.regionId, self()), timeout);
		future.thenAccept(result -> {
            if (result instanceof MasterWorkerProtocol.WorkerRegionAck) {
                System.out.println("Received register ack form Master : " + result.getClass().getName());	
            } else {
            	System.out.println("Unknown message received from Master : " + result.getClass().getName());
            }
        }).exceptionally(e ->
                {
                	System.out.println("No registration Ack from Master. Exception : " + e.getCause().getMessage());
                	System.out.println("Retrying ...");
                    registerRegionToMaster();
                    return null;
                }
        );
	}
	
	@Override
	public Receive createReceive() {
		System.out.println("WorkerRegion received message");
		return receiveBuilder()
		    .match(NNJobMessage.class, this::handleWork)
		    .build();	
	}
	
	private void handleWork(NNJobMessage msg) {
        System.out.println("WorkerRegion >>> Worker Region {}" + regionId);
        workerRegion.tell(msg, self());
    }
}