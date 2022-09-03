package utility;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class configs {
	public static Config getConfig(String port, String role, String configFile) {
	        final Map<String, Object> properties = new HashMap<>();
	
	        if (role != null && !role.isEmpty()) {
	        	properties.put("akka.cluster.sharding.role", role);
	            properties.put("akka.cluster.roles", Arrays.asList(role));
	        }
	        
	        if (port != null) {
	            properties.put("akka.remote.artery.canonical.port", port);
	        }
	
	        Config baseConfig = ConfigFactory.load(configFile);
	        return ConfigFactory.parseMap(properties)
	                .withFallback(baseConfig);
	}
}