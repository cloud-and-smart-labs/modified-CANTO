package utility;

import java.io.Serializable;
import java.util.ArrayList;

import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.transfer.TransferFunction;

import akka.actor.ActorRef;

public interface NNOperationTypes {
	class ParameterResponse implements NNOperationTypes, Serializable {}
	class ParameterRequest implements NNOperationTypes, Serializable {}

	class DoneEpoch implements NNOperationTypes, Serializable {}

	class UpdateWeightParam implements NNOperationTypes, Serializable {
		public String param1;
		public String param2;
		public String activations;
		public UpdateWeightParam(){}
		public UpdateWeightParam(String activations){
			this.activations = activations;
		}
	}

	class GetWeights implements NNOperationTypes, Serializable {
		public String weights;
		public String outputs;
		public GetWeights(){}
	}
	
	class Gradient implements NNOperationTypes, Serializable {
		// Changing to CSV format string because of Serialization issues. Will convert back to matrix on PSShard.
		private String gradient;
		public Gradient(String gradient) {
			this.gradient = gradient;
		}
		public String getGradient() {
			return gradient;
		}
	}
	
	class Ready implements NNOperationTypes, Serializable {}
	class Success implements NNOperationTypes, Serializable {}
	
	class ForwardProp implements NNOperationTypes, Serializable {
		public Vector x;
		public Vector y;
		public boolean isTestData;

		public ForwardProp(Vector x, Vector y, boolean isTestData) {
			this.x = x;
			this.y = y;
			this.isTestData = isTestData;
		}
	}

	class AdmmPredict implements NNOperationTypes, Serializable {
		public Vector input;
		public Vector label;

		public AdmmPredict(Vector input, Vector label) {
			this.input = input;
			this.label = label;
		}
	}

	class Predict implements NNOperationTypes, Serializable {
		public String optimizer;
		public Predict(){
			this.optimizer = "sgd";
		}
		public Predict(String optimizer){
			this.optimizer = optimizer;
		}
	}
	
	class BackProp implements NNOperationTypes, Serializable {
		public Vector childDelta;
		public BackProp(Vector childDelta) {
			this.childDelta = childDelta;
		}
	}

	class WeightUpdate implements NNOperationTypes, Serializable {
		public boolean isTest;
		public WeightUpdate(boolean b) {
			this.isTest = b;
		}
	}
	class DoneUpdatingWeights implements NNOperationTypes, Serializable {}
	class Dummy implements NNOperationTypes, Serializable {}
	class SendWeights implements NNOperationTypes, Serializable {
		public ArrayList<ActorRef> trainedWs;
		public SendWeights(ArrayList<ActorRef> trainedWs) {
			this.trainedWs = trainedWs;
		}
	}
	
	public class DataShardParams implements NNOperationTypes, Serializable {
		public ArrayList<DataSetRow> dataSetPart;
		public ArrayList<DataSetRow> testSetPart;
		public ArrayList<Integer> layerDimensions;
		public TransferFunction activation;
		public ArrayList<ActorRef> parameterShardRefs;
		public int d_id;
		public int lastLayerNeurons;
		public int epochs;
		public int routee_num;
		public String optimizer;
		
		public DataShardParams(int d_id, ArrayList<Integer> layerDimensions, ArrayList<DataSetRow> dataSetPart, ArrayList<DataSetRow> testSetPart, TransferFunction activation, int lastLayerNeurons, String optimizer, int epochs, int routee_num, ArrayList<ActorRef> parameterShardRefs) {
			this.d_id = d_id;
			this.dataSetPart = dataSetPart;
			this.testSetPart = testSetPart;
			this.activation = activation;
			this.parameterShardRefs = parameterShardRefs;
			this.lastLayerNeurons = lastLayerNeurons;
			this.epochs = epochs;
			this.routee_num = routee_num;
			this.layerDimensions = layerDimensions;
			this.optimizer = optimizer;
		}
	}
	
}