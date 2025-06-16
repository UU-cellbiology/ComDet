/*-
 * #%L
 * ComDet Plugin for ImageJ
 * %%
 * Copyright (C) 2012 - 2023 Cell Biology, Neurobiology and Biophysics
 * Department of Utrecht University.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
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
	@Override
	public double getPartialDerivate(double x, double[] a, int parameterIndex) {
		return pow(x, parameterIndex);
	}

	/**
	 * Polynomial y = a_n * x^n + ... + a_2 * x^2 + a_1 * x + a_0
	 * @param a 0: a_0, 1: a_1, 2: a_2, ..., a_n
	 */
	@Override
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
