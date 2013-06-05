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
	int nAreaCut; //threshold of minimum particle area
	int nAreaMax; //threshold of maximum particle area
	
	int nSensitivity; //sensitivity of detection
	
	//int nDetectionMethod; //method for detecting particles
	

	//dialog showing options for particle search algorithm		
	public boolean findParticles() {
		GenericDialog fpDial = new GenericDialog("Detect Particles");
		//String [] DetectOptions = new String [] {
//				"1. Intensity maximum","2. Intensity shape"};
		String [] sSensitivityOptions = new String [] {
				"Dim particles (low SNR)", "Intermediate" ,"Bright particles (high SNR)" };
		
		//fpDial.addChoice("Particle detection method:", DetectOptions, Prefs.get("ComDet.DetectMethod", "Round shape"));
		fpDial.addNumericField("Approximate particle size, pix", Prefs.get("ComDet.dPSFsigma", 2), 3);
		fpDial.addChoice("Sensitivity of detection (for method 1 only):", sSensitivityOptions, Prefs.get("ComDet.Sensitivity", "Bright particles (high SNR)"));

		   
		fpDial.showDialog();
		if (fpDial.wasCanceled())
            return false;
		
		//nDetectionMethod = fpDial.getNextChoiceIndex();
		//Prefs.set("ComDet.DetectMethod", DetectOptions[nDetectionMethod]);
		dPSFsigma = fpDial.getNextNumber();
		Prefs.set("ComDet.dPSFsigma", dPSFsigma);
		nSensitivity = fpDial.getNextChoiceIndex();
		Prefs.set("ComDet.Sensitivity", sSensitivityOptions[nSensitivity]);
		
		nAreaMax = (int) (1.5*dPSFsigma*dPSFsigma); 
		dPSFsigma *= 0.5;
		nKernelSize = (int) Math.ceil(3.0*dPSFsigma);
		if(nKernelSize%2 == 0)
			 nKernelSize++;
		nThreads = 50;
		nSensitivity += 3;
		
		
		return true;
	}

	
}
