package utility;

import org.la4j.Vector;
import org.neuroph.core.transfer.TransferFunction;

/**
 * Activation function which enforces that output neurons have probability distribution (sum of all outputs is one)
 */
public class SoftMax extends TransferFunction {

    private Vector layer;
    private double totalLayerInput;

    public SoftMax(Vector layer) {
        this.layer = layer;
    }

    @Override
    public double getOutput(double netInput) {
        totalLayerInput = 0;
        double max = 0;
        
        for(int i = 0; i < layer.length(); i++) {
            totalLayerInput += Math.exp(layer.get(i)-max);
        }

        output = Math.exp(netInput-max) / totalLayerInput;
        System.out.println(totalLayerInput + ", " + netInput + ", " + output);
        return output;
    }

    @Override
    public double getDerivative(double net) {
        return output * (1d - output); 
    }
}