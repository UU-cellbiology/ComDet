package jaolho.data.lma.implementations;

import jaolho.data.lma.LMAFunction;

/**
 * LMA polynomial y = a_n * x^n + ... + a_2 * x^2 + a_1 * x + a_0
 */
public class Polynomial extends LMAFunction {

	/**
	 * @return The partial derivate of the polynomial which is
	 * x to the power of the parameter index.
	 */
	public double getPartialDerivate(double x, double[] a, int parameterIndex) {
		return pow(x, parameterIndex);
	}

	/**
	 * Polynomial y = a_n * x^n + ... + a_2 * x^2 + a_1 * x + a_0
	 * @param a 0: a_0, 1: a_1, 2: a_2, ..., a_n
	 */
	public double getY(double x, double[] a) {
		double result = 0;
		for (int i = 0; i < a.length; i++) {
			result += pow(x, i) * a[i]; 
		}
		return result;
	}
	
	/** fast power */
	private static double pow(double x, int exp) {
		double result = 1;
		for (int i = 0; i < exp; i++) {
			result *= x;
		}
		return result;
	}
	
	public static void main(String[] args) {
		System.out.println(pow(2, 1));
	}

}
