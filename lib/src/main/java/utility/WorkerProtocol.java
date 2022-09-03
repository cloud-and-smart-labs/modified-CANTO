package utility;

import java.io.Serializable;

import main.NNJobMessage;

public interface WorkerProtocol {
	class PiCalcTask implements WorkerProtocol, Serializable {

        private final NNJobMessage msg;

        public PiCalcTask(NNJobMessage msg) {
            this.msg = msg;
        }

        public NNJobMessage getSensorData() {
            return msg;
        }   
    }
	
	class WorkProcessed implements WorkerProtocol, Serializable {
        private double pi_val;

        public WorkProcessed(double pi_val) {
            this.pi_val = pi_val;
        }

        public double getResult() {
            return pi_val;
        }
    }

}