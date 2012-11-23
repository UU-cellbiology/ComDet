package jaolho.data.lma.implementations;

import jaolho.data.lma.LMA;
import jaolho.data.lma.LMAFunction;

import java.util.Arrays;

public class TestFunctions {
	
	public static LMAFunction sin = new LMAFunction() {
		public double getY(double x, double[] a) {
			return a[0] * Math.sin(x / a[1]);
		}
		public double getPartialDerivate(double x, double[] a, int parameterIndex) {
			switch (parameterIndex) {
				case 0: return Math.sin(x / a[1]);
				case 1: return a[0] * Math.cos(x / a[1]) * (-x / (a[1] * a[1]));
			}
			throw new RuntimeException("No such fit parameter: " + parameterIndex);
		}
	};
	
	public static void main(String[] args) {
		double[] x = {0.0, 0.1, 0.2, 0.3, 0.5, 0.7};//, 1.1, 1.4, 2.5, 6.4, 7.9, 10.4, 12.6};
		double[] a = {2.2, 0.4};
		double[][] data = {x, sin.generateData(x, a)}; 
		LMA lma = new LMA(sin, new double[] {0.1, 10}, data, null);
		lma.fit();
		System.out.println("RESULT PARAMETERS: " + Arrays.toString(lma.parameters));
		
		/*
		ArrayTool.writeToFileByColumns("fittest.dat", data);
		GnuPlotTool2.plot(ArrayTool.toFloatArray(data));
		double[] af = {2.370453217483242, 0.43162827642649365};
		for (int i = 0; i < x.length; i++) {
			double y = sin.getY(x[i], a);
			double y_f = sin.getY(x[i], af);
			System.out.println("y = "+ y + ", y_f = " + y_f + ", dy = " + (y - y_f));
		}
		*/
	}
}
