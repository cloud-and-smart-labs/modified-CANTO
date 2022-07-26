package utility;

import java.io.Serializable;

import akka.actor.ActorRef;

public interface WorkerRegionEvent {
    class Registered implements WorkerRegionEvent, Serializable {
        private ActorRef workerRegion;

        public Registered(ActorRef workerRegion){
            this.workerRegion = workerRegion;
        }

        public ActorRef getWorkerRegion() {
            return workerRegion;
        }
    }
    class Unregistered implements WorkerRegionEvent, Serializable {
        private ActorRef workerRegion;

        public Unregistered(ActorRef workerRegion){
            this.workerRegion = workerRegion;
        }

        public ActorRef getWorkerRegion() {
            return workerRegion;
        }
    }
    
    class UpdateTable implements WorkerRegionEvent, Serializable {
    	private String key;
    	private int value;
    	
    	public UpdateTable(String key, int value) {
    		this.key = key;
    		this.value = value;
    	}
    	
    	public String getKey() {
    		return key;
    	}
    	
    	public int getValue() {
    		return value;
    	}
    }
    
    class JobSimulate implements WorkerRegionEvent, Serializable {
    	private String delayOnNode;
    	
    	public JobSimulate(String delayOnNode) {
    		this.delayOnNode = delayOnNode;
    	}
    	
    	public String getNode() {
    		return this.delayOnNode;
    	}
    }
}