package actor;

import java.util.concurrent.TimeoutException;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import main.NNJobMessage;

public class Router extends AbstractActor {
	  private ActorRef workProcessorRouter;
	  private ActorRef nnMaster;

	  public static Props props(ActorRef workProcessorRouter) {
	    return Props.create(Router.class, workProcessorRouter);
	  }
	  
	  public Router(ActorRef workProcessorRouter) {
	        this.workProcessorRouter = workProcessorRouter;
	  }
	  
	  @Override
	    public Receive createReceive() {
	        return receiveBuilder()
	        	.match(NNJobMessage.class, this::initiateJob)
	        	.build();
	  }
	  
	  private void initiateJob(NNJobMessage nnmsg) throws TimeoutException, InterruptedException {
		  // NNMaster actor creation
		  nnMaster = getContext().actorOf(Props.create(NNMaster.class, workProcessorRouter), "nn_master" + nnmsg.getPayload());
		  System.out.println("!!!" + nnMaster.path());
		  nnMaster.tell(nnmsg, self());
	  }
}