package actor;

import java.time.Duration;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.Cluster;
import main.NNJobMessage;
import main.JobQueue;
import utility.MasterWorkerProtocol;
import utility.NNOperationTypes;
import utility.WorkerRegionEvent;
import utility.WorkerRegionState;
import akka.actor.Address;

import java.util.ArrayList;
import java.util.HashMap;

public class Master extends AbstractActor{	
    private Cluster cluster = Cluster.get(getContext().system());
    private final WorkerRegionState workerRegionState;
    private static final MasterWorkerProtocol.WorkerRegionAck WORKER_REGION_ACK = new MasterWorkerProtocol.WorkerRegionAck();
    private final JobQueue jobQueue;
    private final TableHandler tableHandler;
    private ArrayList<ActorRef> trainedWeights;
    private HashMap<String, ActorRef> nodetoRef = new HashMap<String, ActorRef>();
    private long startTime;
    private long endTime;

    public static Props props() {
        return Props.create(Master.class);
    }
    
    public Master() {
    	 this.workerRegionState = new WorkerRegionState();
    	 this.jobQueue = new JobQueue();
    	 this.tableHandler = new TableHandler();
    }
   
	@Override
	public Receive createReceive() {
		System.out.println("Master actor received message");
		return receiveBuilder()	
			.match(MasterWorkerProtocol.RegisterWorkerRegion.class, this::handleRegisterWorkerRegion)
			.match(NNJobMessage.class, this::handleSensorData)
			.match(Address.class, this::addToRefsMap)
			.match(String.class, this::newTableEntry)
            .match(NNOperationTypes.SendWeights.class, this::getTrainedWeights)
			.match(WorkerRegionEvent.UpdateTable.class, this::updateTable)
			.matchAny(this::handleAny)
	        .build();
	}
	
	private void handleAny(Object o) {
		System.out.println("Actor received unknown message: " + o.toString());
	}
	
    private void getTrainedWeights(NNOperationTypes.SendWeights ps)  {
        endTime = System.currentTimeMillis();
        System.out.print("Time taken by CANTO: ");
        System.out.println(endTime - startTime);
        System.out.println("Master received trained weights");
        this.trainedWeights = ps.trainedWs;
    }

	private void addToRefsMap(Address nodeHost) {
		nodetoRef.put(nodeHost.getHost().get(), getSender());
	}
	
	public void newTableEntry(String nodeHost) {
		tableHandler.newTableEntry(nodeHost);
	}
	
	public void updateTable(WorkerRegionEvent.UpdateTable update) {
		tableHandler.updateTable(update);
	}
	
	private void handleRegisterWorkerRegion(MasterWorkerProtocol.RegisterWorkerRegion workerRegion) {
		System.out.println("In master class register worker method");
        ActorRef workerRegionActorRef = workerRegion.getRegionActorRef();
        if (workerRegionState.containsRegisteredRegion(workerRegionActorRef)) {
            System.out.println("Worker region already registered");
            sender().tell(WORKER_REGION_ACK, self());
        } else {
        	System.out.println("Started worker region registration");
        	registerWorkerRegion(new WorkerRegionEvent.Registered(workerRegionActorRef));
        }
    }
	
	private void registerWorkerRegion(WorkerRegionEvent.Registered registered) {
		System.out.println("In main registerWorkerRegion method!!");
        workerRegionState.updateState(registered);
        ActorRef workerRegion = registered.getWorkerRegion();
        System.out.println("Region registered! " + workerRegion.path());
        // Watch workerRegion in case it becomes unavailable (terminated message will be received)
        getContext().watch(workerRegion);
        sender().tell(WORKER_REGION_ACK, self());
        if (jobQueue.hasPendingSensorData()) {
            sendDataToWorkers();
        }
    }
	
	 private void handleSensorData(NNJobMessage msg) {
        if (jobQueue.contains(msg)) {
        	System.out.println("Job already in queue");
        } else {
            System.out.println("Master received job msg!");
            jobQueue.updateState(msg);
            System.out.println("@@@@@@@@@@@@@ " + msg.getPayload());
            sendDataToWorkers();
        }
    }
	 
	 private void sendDataToWorkers() {
		if (nodetoRef.size() == 0) {
			System.out.println("No worker regions to send work to.");
		}
		
		else if (jobQueue.hasPendingSensorData()) {
            System.out.println("Size of pending data: " + jobQueue.getPendingSensorData());
            ActorRef workerRegion = nodetoRef.get(tableHandler.getMinJobsRegion());
            System.out.println("*******Node with min jobs: ");
            System.out.println(tableHandler.getMinJobsRegion());
        
            NNJobMessage nextMessage = jobQueue.nextSensorData();
            startTime = System.currentTimeMillis();
            workerRegion.tell(nextMessage, self());            
        }
	 }	
}