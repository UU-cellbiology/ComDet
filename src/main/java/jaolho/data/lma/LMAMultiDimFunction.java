/*-
 * #%L
 * ComDet Plugin for ImageJ
 * %%
 * Copyright (C) 2012 - 2025 Cell Biology, Neurobiology and Biophysics
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
package jaolho.data.lma;

import java.util.Arrays;

/**
 * Implement this <i>multidimensional</i> function y = (x[], a[]) for your fit purposes.
 * Used with <code>LMAMultiDim</code>. For simpler, one dimensional fit functions you can use
 * <code>LMAFunction</code> with <code>LMA</code>.
 * 
 * @author Janne Holopainen (jaolho@utu.fi, tojotamies@gmail.com)
 * @version 1.0, 23.03.2007
 */
public abstract class LMAMultiDimFunction {
	private final double[] temp = new double[1]; 
	
	/**
	 * @return The <i>y</i>-value of the function.
	 * @param x The <i>x</i>-values for which the <i>y</i>-value is calculated.
	 * @param a The fitting parameters. 
	 */
	public abstract double getY(double x[], double[] a);
	
	/** 
	 * The method which gives the partial derivates used in the LMA fit.
	 * If you can't calculate the derivate, use a small <code>a</code>-step (e.g., <i>da</i> = 1e-20)
	 * and return <i>dy/da</i> at the given <i>x</i> for each fit parameter.
	 * @return The partial derivate of the function with respect to parameter <code>parameterIndex</code> at <i>x</i>.
	 * @param x The <i>x</i>-value for which the partial derivate is calculated.
	 * @param a The fitting parameters.
	 * @param parameterIndex The parameter index for which the partial derivate is calculated. 
	 */
	public abstract double getPartialDerivate(double x[], double[] a, int parameterIndex);
	
	/**
	 * A convenience method for the one dimensional case.
	 * Not used by the fit algorithm.
	 * @return The <i>y</i>-value of the function.
	 * @param x The <i>x</i> value for which the <i>y</i>-value is calculated.
	 * @param a The fitting parameters. 
	 */
	public final double getY(double x, double a[]) {
		temp[0] = x;
		return getY(temp, a);
	}
	
	/**
	 * @param lma A LMA object from which x- and parameter-values
	 * are extracted for calculating function values. 
	 * @return Calculated function values with the lma x- and
	 * parameter-values, double[function value index].
	 * @see LMA#generateData()
	 */
	public double[] generateData(LMA lma) {
		return generateData(lma.xDataPoints, lma.parameters);
	}
	
	/**
	 * @param x The x-arrays for which the y-values are calculated, double[function value index][x-index]
	 * @param a The fit parameters, double[fit parameter index]
	 * @return Calculated function values with the given x- and parameter-values, double[function value index].
	 */
	public double[] generateData(double[][] x, double[] a) {
		double[] result = new double[x.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = getY(x[i], a);
		}
		return result;
	}
	
	/**
	 * The one dimesional convenience method.
	 * @param x The x-values for which the y-values are calculated, double[function value index]
	 * @param a The fit parameters, double[fit parameter index]
	 * @return Calculated function values with the given x and parameter-values, double[function value index].
	 */
	public double[] generateData(double[] x, double[] a) {
		double[] result = new double[x.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = getY(x[i], a);
		}
		return result;
	}
	
	/**
	 * @param x float[function value index][x-index]
	 * @param a double[fit parameter index]
	 * @return Calculated function values with the given x- and parameter-values.
	 */
	public float[] generateData(float[][] x, double[] a) {
		float[] result = new float[x.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = (float) getY(ArrayConverter.asDoubleArray(x[i]), a);
		}
		return result;
	}
	
	/**
	 * The one dimesional convenience method.
	 * @param x The x-values for which the y-values are calculated, float[function value index]
	 * @param a The fit parameters, double[fit parameter index]
	 * @return Calculated function values with the given x and parameter-values, float[function value index].
	 */
	public float[] generateData(float[] x, double[] a) {
		float[] result = new float[x.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = (float) getY(x[i], a);
		}
		return result;
	}

	/** The default weights-array constructor. Override for your purposes. */
	public double[] constructWeights(double[][] dataPoints) {
		double[] result = new double[dataPoints.length];
		Arrays.fill(result, 1);
		return result;
	}
	
	public float[] generateData(float[][] x, float[] a) {
		return ArrayConverter.asFloatArray(generateData(ArrayConverter.asDoubleArray(x), ArrayConverter.asDoubleArray(a)));
	}
	
	/** One dimensional convenience method. */
	public float[] generateData(float[] x, float[] a) {
		return ArrayConverter.asFloatArray(generateData(ArrayConverter.asDoubleArray(x), ArrayConverter.asDoubleArray(a)));
	}
	
	
}
