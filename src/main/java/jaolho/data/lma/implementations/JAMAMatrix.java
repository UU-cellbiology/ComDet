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

import jaolho.data.lma.LMAMatrix;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.NumberFormat;

import Jama.Matrix;

public class JAMAMatrix extends Matrix implements LMAMatrix {
	private static final long serialVersionUID = -8925816623803983503L;

	public JAMAMatrix(double[][] elements) {
		super(elements);
	}
	
	public JAMAMatrix(int rows, int cols) {
		super(rows, cols);
	}
	
	@Override
	public void invert() throws LMAMatrix.InvertException {
		try {
			Matrix m = inverse();
			setMatrix(0, this.getRowDimension() - 1, 0, getColumnDimension() - 1, m);
		}
		catch (RuntimeException e) {
			StringWriter s = new StringWriter();
			PrintWriter p = new PrintWriter(s);
			p.println(e.getMessage());
			p.println("Inversion failed for matrix:");
			this.print(p, NumberFormat.getInstance(), 5);
			throw new LMAMatrix.InvertException(s.toString());
		}
	}

	@Override
	public void setElement(int row, int col, double value) {
		set(row, col, value);
	}

	@Override
	public double getElement(int row, int col) {
		return get(row, col);
	}

	@Override
	public void multiply(double[] vector, double[] result) {
		for (int i = 0; i < this.getRowDimension(); i++) {
			result[i] = 0;
			for (int j = 0; j < this.getColumnDimension(); j++) {
				 result[i] += this.getElement(i, j) * vector[j];
			}
		}
	}
	
	public static void main(String[] args) {
		StringWriter s = new StringWriter();
		PrintWriter out = new PrintWriter(s);
		out.println("jakkajaaa");
		System.out.println(s);
	}
}
