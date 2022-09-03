package utility;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import akka.actor.ActorRef;
import utility.WorkerRegionEvent.Registered;
import utility.WorkerRegionEvent.Unregistered;

public class WorkerRegionState {
	private Set<ActorRef> workerRegionSet = new HashSet<>();
	
	public void updateState(WorkerRegionEvent event) {
        if (event instanceof WorkerRegionEvent.Registered) {
            ActorRef registeredRegion = ((WorkerRegionEvent.Registered) event).getWorkerRegion();
            workerRegionSet.add(registeredRegion);
        } else if (event instanceof WorkerRegionEvent.Unregistered) {
            ActorRef unregisteredRegion = ((WorkerRegionEvent.Unregistered) event).getWorkerRegion();
            workerRegionSet.remove(unregisteredRegion);
        } else {
            throw new IllegalArgumentException();
        }
    }
	
	public boolean containsRegisteredRegion(ActorRef workerRegion) {
        return workerRegionSet.contains(workerRegion);
    }

    public Optional<ActorRef> getAvailableWorkerRegion() {
         return workerRegionSet.stream().findAny();    	
    	// Get the worker that has minimum number of jobs in the nodeToJobs hashmap - implemented in TableHandler
    }

    public int getWorkerRegionsCount() {
        return workerRegionSet.size();
    }

    public Set<ActorRef> getWorkerRegions() {
        return workerRegionSet;
    }
}