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

import java.util.Arrays;

public class TestFunctions {
	
	public static LMAFunction sin = new LMAFunction() {
		@Override
		public double getY(double x, double[] a) {
			return a[0] * Math.sin(x / a[1]);
		}
		@Override
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
