package utility;

import java.io.Serializable;

import akka.actor.ActorRef;

public interface MasterWorkerProtocol {
	class RegisterWorkerRegion implements Serializable {
        private String workerRegionId;
        private ActorRef regionActorRef;

        public RegisterWorkerRegion(String workerRegionId, ActorRef regionActorRef) {
        	System.out.println("In RegisterWorkerRegion");
            this.workerRegionId = workerRegionId;
            this.regionActorRef = regionActorRef;
        }

        String getWorkerRegionId() {
            return workerRegionId;
        }

        public ActorRef getRegionActorRef() {
            return regionActorRef;
        }
    }
	
	 class WorkerRegionAck implements Serializable {
	 }
}