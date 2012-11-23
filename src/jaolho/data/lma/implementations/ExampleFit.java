package jaolho.data.lma.implementations;

import jaolho.data.lma.LMA;
import jaolho.data.lma.LMAFunction;


/** An example fit which fits a straight line to some data points and prints out the resulting fit parameters. */
public class ExampleFit {
	/** An example function with a form of y = a0 * x + a1 */
	public static class ExampleFunction extends LMAFunction {
		@Override
		public double getY(double x, double[] a) {
			return a[0] * x + a[1];
		}
		@Override
		public double getPartialDerivate(double x, double[] a, int parameterIndex) {
			switch (parameterIndex) {
				case 0: return x;
				case 1: return 1;
			}
			throw new RuntimeException("No such parameter index: " + parameterIndex);
		}
	}
	
	/** Does the actual fitting by using the above ExampleFunction (a line) */
	public static void main(String[] args) {
		LMA lma = new LMA(
			new ExampleFunction(),
			new double[] {1, 1},
			new double[][] {
				{0, 2, 6, 8, 9}, 
				{5, 10, 23, 33, 40}}
		);
		lma.fit();
		System.out.println("iterations: " + lma.iterationCount);
		System.out.println("chi2: " + lma.chi2 + ", param0: " + lma.parameters[0] + ", param1: " + lma.parameters[1]);
	}
}
