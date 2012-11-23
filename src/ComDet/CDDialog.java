package ComDet;

import ij.Prefs;
import ij.gui.GenericDialog;


public class CDDialog {

	
	int nWidth;  //image width of original image
	int nHeight; //image height of original image
	int nSlices; //number of slices
	
	//finding particles
	double dPSFsigma; //standard deviation of PSF approximated by Gaussian
	int nKernelSize; //size of Gaussian kernel for particles enhancement
	//double dPixelSize; //size of pixel in nm of original image
	int nThreads; //number of threads for calculation
	int nAreaCut; //threshold of particles area
	
	int nSensitivity; //sensitivity of detection
	

	//dialog showing options for particle search algorithm		
	public boolean findParticles() {
		GenericDialog fpDial = new GenericDialog("Detect Particles");
		String [] sSensitivityOptions = new String [] {
				"Dim particles (low SNR)", "Intermediate" ,"Bright particles (high SNR)" };
		
		
		fpDial.addNumericField("Approximate particle size, pix", Prefs.get("ComDet.dPSFsigma", 2), 3);
		fpDial.addChoice("Sensitivity of detection:", sSensitivityOptions, Prefs.get("ComDet.Sensitivity", "Bright particles (high SNR)"));

		   
		fpDial.showDialog();
		if (fpDial.wasCanceled())
            return false;
		
		dPSFsigma = fpDial.getNextNumber();
		Prefs.set("ComDet.dPSFsigma", dPSFsigma);
		dPSFsigma *= 0.5;
		nKernelSize = (int) Math.ceil(2.0*dPSFsigma);
		 if(nKernelSize%2 == 0)
			 nKernelSize++;
		 nSensitivity = fpDial.getNextChoiceIndex();
		 Prefs.set("ComDet.Sensitivity", sSensitivityOptions[nSensitivity]);
		 nThreads = 50;
		
		return true;
	}

	
}
