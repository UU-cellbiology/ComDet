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
