package actor;

import akka.cluster.sharding.ShardRegion;

public class WorkRegionMessageExtractor extends ShardRegion.HashCodeMessageExtractor {
	private int x = 0;
	public WorkRegionMessageExtractor(int maxNumberOfShards) {
		super(maxNumberOfShards);
	}

	@Override
	public String entityId(Object message) {
		if(message != null) {
			x++;
			return x%5 + "";	
		}
		else
			return null;	
	}
	
}