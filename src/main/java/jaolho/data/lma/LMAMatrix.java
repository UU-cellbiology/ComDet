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
package jaolho.data.lma;

/**
 * The matrix to be used in LMA.
 * Implement this to make LMA operational if you
 * don't or can't use jama or flanagan math libraries.
 */
public interface LMAMatrix {
	public static class InvertException extends RuntimeException {
		public InvertException(String message) {
			super(message);
		}
	}
	/**
	 * Inverts the matrix for solving linear equations for
	 * parameter increments.
	 */
	public void invert() throws InvertException;
	
	/**
	 * Set the value of a matrix element.
	 */
	public void setElement(int row, int col, double value);
	
	/**
	 * Get the value of a matrix element.
	 */
	public double getElement(int row, int col);
	
	/**
	 * Multiplies this matrix with an array (result = this * vector).
	 * The lengths of the arrays must be equal to the number of rows in the matrix.
	 * @param vector The array to be multiplied with the matrix.
	 * @param result The result of the multiplication will be put here.
	 */
	public void multiply(double[] vector, double[] result);
}
