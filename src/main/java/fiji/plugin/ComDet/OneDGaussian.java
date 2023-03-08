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
package fiji.plugin.ComDet;

import jaolho.data.lma.LMAFunction;

public class OneDGaussian extends LMAFunction 
{
	@Override
	public double getY(double x, double[] a) {
		return a[0] * Math.exp(-0.5* ( (x-a[1])*(x-a[1])/(a[2]*a[2]) ) ) ;
	}
	@Override
	public double getPartialDerivate(double x, double[] a, int parameterIndex) {
		switch (parameterIndex) {
			case 0: return Math.exp(-0.5* ( (x-a[1])*(x-a[1])/(a[2]*a[2]) ) );
			case 1: return a[0] * Math.exp(-0.5* ( (x-a[1])*(x-a[1])/(a[2]*a[2]) ) ) * ((x-a[1])/(a[2]*a[2]));
			case 2: return a[0] * Math.exp(-0.5* ( (x-a[1])*(x-a[1])/(a[2]*a[2]) ) ) * ((x-a[1])*(x-a[1])/(a[2]*a[2]*a[2]));
		}
		throw new RuntimeException("No such parameter index: " + parameterIndex);
	}
}
