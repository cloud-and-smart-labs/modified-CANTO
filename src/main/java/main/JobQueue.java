package main;

import java.util.LinkedList;
import java.util.Queue;

public class JobQueue {
	private Queue<NNJobMessage> pendingSensorData = new LinkedList<>();
	
	public void updateState(NNJobMessage msg) {
		pendingSensorData.add(msg);
	}
	
	public void removeFromQueue(NNJobMessage msg) {
		pendingSensorData.remove(msg);
	}
	
	public int getPendingSensorData() {
	   return pendingSensorData.size();
	}
	  
    public Boolean hasPendingSensorData() {
        return !pendingSensorData.isEmpty();
    }
    
    public NNJobMessage nextSensorData() {
        return pendingSensorData.poll();
    }
    
    public Boolean contains(NNJobMessage msg) {
        return pendingSensorData.contains(msg);
    }
}