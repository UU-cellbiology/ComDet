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
