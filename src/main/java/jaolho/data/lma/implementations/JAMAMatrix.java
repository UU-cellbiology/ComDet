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

	public void setElement(int row, int col, double value) {
		set(row, col, value);
	}

	public double getElement(int row, int col) {
		return get(row, col);
	}

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
