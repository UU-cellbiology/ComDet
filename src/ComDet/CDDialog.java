package ComDet;

import java.awt.Panel;

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
	
	//colocalization analysis parameters
	boolean bColocalization;
	double dColocDistance;
	boolean bPlotBothChannels;
	
	//int nDetectionMethod; //method for detecting particles
	

	//dialog showing options for particle search algorithm		
	public boolean findParticles() {

		GenericDialog fpDial = new GenericDialog("Detect Particles");
		//String [] DetectOptions = new String [] {
//				"1. Intensity maximum","2. Intensity shape"};
		String [] sSensitivityOptions = new String [] {
				"Very dim particles (SNR=3)", "Dim particles (SNR=4)" ,"Bright particles (SNR=4)","Brighter particles (SNR=10)", "Very bright particles (SNR=30)" };
		
		//fpDial.addChoice("Particle detection method:", DetectOptions, Prefs.get("ComDet.DetectMethod", "Round shape"));
		fpDial.addMessage("Detection parameters:\n");
		fpDial.addNumericField("Approximate particle size, pix", Prefs.get("ComDet.dPSFsigma", 4), 2);
		fpDial.addChoice("Sensitivity of detection:", sSensitivityOptions, Prefs.get("ComDet.Sensitivity", "Very dim particles (SNR=3)"));
		fpDial.addMessage("\n\n Colocalization analysis:\n");
		fpDial.addCheckbox("Calculate colocalization? (requires image with two color channels)", Prefs.get("ComDet.bColocalization", false));
		fpDial.addNumericField("Max distance between colocalized spot, pix", Prefs.get("ComDet.dColocDistance", 4), 2);
		fpDial.addCheckbox("Plot detected particles in both channels?", Prefs.get("ComDet.bPlotBothChannels", false));
		   
		fpDial.showDialog();
		if (fpDial.wasCanceled())
            return false;
		
		//nDetectionMethod = fpDial.getNextChoiceIndex();
		//Prefs.set("ComDet.DetectMethod", DetectOptions[nDetectionMethod]);
		dPSFsigma = fpDial.getNextNumber();
		Prefs.set("ComDet.dPSFsigma", dPSFsigma);
		nSensitivity = fpDial.getNextChoiceIndex();
		Prefs.set("ComDet.Sensitivity", sSensitivityOptions[nSensitivity]);
		bColocalization = fpDial.getNextBoolean();
		Prefs.set("ComDet.bColocalization", bColocalization);
		dColocDistance = fpDial.getNextNumber();
		Prefs.set("ComDet.dColocDistance", dColocDistance);
		bPlotBothChannels = fpDial.getNextBoolean();
		Prefs.set("ComDet.bPlotBothChannels", bPlotBothChannels);
		
		nAreaMax = (int) (1.5*dPSFsigma*dPSFsigma); 
		dPSFsigma *= 0.5;
		nKernelSize = (int) Math.ceil(3.0*dPSFsigma);
		if(nKernelSize%2 == 0)
			 nKernelSize++;
		nThreads = 50;
		if(nSensitivity<3)
		{
			nSensitivity += 3;
		}
		else
		{
			if(nSensitivity == 3)
				nSensitivity=10;
			else
				nSensitivity=30;
		}
		
		
		return true;
	}

	
}
