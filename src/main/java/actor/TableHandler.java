package actor;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;

import akka.actor.AbstractActor;
import akka.actor.Address;
import utility.WorkerRegionEvent;

public class TableHandler{
	private HashMap<String, Integer> nodeToJobs = new HashMap<String, Integer>();
	
	/*@Override
	public Receive createReceive() {
		System.out.println("In TableHandler!!!");
		return receiveBuilder()
	        .match(WorkerRegionEvent.UpdateTable.class, this::updateTable)
	        .match(Address.class, this::newTableEntry)
	        .matchAny(this::handleAny)
	        .build();
	}*/
		
	public void updateTable(WorkerRegionEvent.UpdateTable update) {
	//	System.out.println("In updateTable method!");
		nodeToJobs.put(update.getKey(), nodeToJobs.get(update.getKey()) + update.getValue());
	//	displayTable();
	}
	
	public void newTableEntry(String nodeHost) {
		System.out.println("In newTableEntry method!: " + nodeHost);
		nodeToJobs.put(nodeHost, 0);
		displayTable();
	}
	
	public void displayTable() {
		System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Table: ");
		nodeToJobs.forEach((k,v) -> 
			System.out.println(k + " -> " + v));
	}
	
	public String getMinJobsRegion() {
		 return Collections.min(nodeToJobs.entrySet(), Comparator.comparing(Entry::getValue)).getKey();
	}
}