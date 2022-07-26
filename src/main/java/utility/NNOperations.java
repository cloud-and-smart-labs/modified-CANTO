package utility;

import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.matrix.dense.Basic2DMatrix;
import org.la4j.matrix.functor.MatrixFunction;
import org.neuroph.core.transfer.TransferFunction;

public class NNOperations {
	public static Basic2DMatrix computeGradient(Vector delta, TransferFunction activation, Vector activatedInputs) {
		Vector actDerivativeOutputs = activatedInputs;
		actDerivativeOutputs.forEach(i -> activation.getDerivative(i));

		return (Basic2DMatrix) delta.outerProduct(actDerivativeOutputs.hadamardProduct(activatedInputs)).transpose();
	}
	
	// hadamard product of delta and inputs
	// activationDerivative(inputs) . weights * activatedInputs
	public static Vector computeDelta(Vector delta, Matrix weights, TransferFunction activation, Vector activatedInputs) {
		Vector actDerivativeOutputs = activatedInputs;
		actDerivativeOutputs.forEach(i -> activation.getDerivative(i));
		
	//	System.out.println("Act derivative outputs: " + actDerivativeOutputs);
	//	System.out.println("Delta dimensions: " + delta.length() + " weights dimns: " + weights.rows() + ", " + weights.columns());
		
		Vector parentDelta = actDerivativeOutputs.hadamardProduct(delta.multiply(weights.transpose()));
		return parentDelta.toColumnMatrix().toColumnVector();
	}
	
	public static Vector applyActivation(Vector x, TransferFunction activation) {
		double[] actOuts = new double[x.length()];
		int i = 0;
		for(double val: x) {
			actOuts[i] = activation.getOutput(val);
			i++;
		}
		return Vector.fromArray(actOuts);
	}
	
	public static Vector oneHotEncoding(Vector x, int n) {
		int val = (int) x.get(0);
		Vector encodedX = Vector.zero(n);

		for(int i = 0; i < n; i++) {
			if(i == val)
				encodedX.set(i, 1);
		}
		return encodedX;
	}
	public static Vector errorDerivative(Vector x, Vector y) {
		return x.subtract(y).multiply(-2);
	}

	public static Vector computeError(Vector x, Vector y) {
		Vector diff =  x.subtract(y);
		double[] error = new double[x.length()];
		
		int i = 0;
		for(double d: diff) {
			error[i] = d * d;
			i++;
		}
		return Vector.fromArray(error);
	}

	public static class RELU implements MatrixFunction {
		public double evaluate(int i, int j, double value) {
			return Math.max(0, value);
		}
	}
	
	public static class PositiveElements implements MatrixFunction {
		public double evaluate(int i, int j, double value) {
			return Math.max(0, value);
		}
	}

	public static class NegativeElements implements MatrixFunction {
		public double evaluate(int i, int j, double value) {
			return Math.min(0, value);
		}
	}
	
	public static class Square implements MatrixFunction {
		public double evaluate(int i, int j, double value) {
			return value*value;
		}
	}
}