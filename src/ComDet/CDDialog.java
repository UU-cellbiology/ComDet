package ComDet;

import ij.Prefs;
import ij.gui.GenericDialog;


public class CDDialog {

	/**image width of original image**/
	int nWidth;  
	/**image height of original image**/
	int nHeight; 
	/**number of slices**/
	int nSlices; 
	
	//finding particles
	/**standard deviation of PSF approximated by Gaussian**/
	double [] dPSFsigma; 
	/**size of Gaussian kernel for particles enhancement**/
	int [] nKernelSize; 
	/**number of threads for calculation**/
	int nThreads; 
	/**threshold of minimum particle area for each channel **/
	int [] nAreaCut; 
	/**threshold of maximum particle area for each channel **/
	int [] nAreaMax; 
	/**sensitivity of detection for each channel **/
	int [] nSensitivity; 
	
	boolean bTwoChannels;
	/** whether to include big particles in the detection**/
	boolean bBigParticles;
	/**0= detect particles independently in each channel
	 * 1= detect particles in channel 1, quantify in channel 2
	 * 2= detect particles in channel 2, quantify in channel 1**/
	int nTwoChannelOption;

	
	//colocalization analysis parameters
	boolean bColocalization;
	double dColocDistance;
	boolean bPlotBothChannels;
	
	//default constructor 
	public CDDialog()
	{
		dPSFsigma = new double[2];
		nKernelSize = new int[2];
		nAreaCut = new int[2];
		nAreaMax =  new int[2];
		nSensitivity = new int[2];
		
	}

	//dialog showing options for particle search algorithm		
	public boolean findParticles(int ChNumber) {
		int i,TotCh;

		GenericDialog fpDial = new GenericDialog("Detect Particles");
		String [] TwoChannelOption = new String [] {
				"Detect in both channels independently","Detect in channel 1, quantify channel 2","Detect in channel 2, quantify channel 1"};
		String [] sSensitivityOptions = new String [] {
				"Very dim particles (SNR=3)", "Dim particles (SNR=4)" ,"Bright particles (SNR=5)","Brighter particles (SNR=10)", "Pretty bright particles (SNR=20)", "Very bright particles (SNR=30)" };
		
		//fpDial.addChoice("Particle detection method:", DetectOptions, Prefs.get("ComDet.DetectMethod", "Round shape"));
		fpDial.addMessage("Detection parameters:\n");
		fpDial.addCheckbox("Include larger particles?", Prefs.get("ComDet.bBigParticles", true));		
		if(ChNumber == 2)
		{
			fpDial.addChoice("Two channel detection:", TwoChannelOption, Prefs.get("ComDet.TwoChannelOption", "Detect in both channels independently"));
			fpDial.addMessage("Channel 1:\n");		
		}
			fpDial.addNumericField("ch1a: Approximate particle size, pix", Prefs.get("ComDet.dPSFsigma", 4), 2);
			fpDial.addChoice("ch1s: Sensitivity of detection:", sSensitivityOptions, Prefs.get("ComDet.Sensitivity", "Very dim particles (SNR=3)"));
		if(ChNumber == 2)
		{
				fpDial.addMessage("Channel 2:\n");
				fpDial.addNumericField("ch2a: Approximate particle size, pix", Prefs.get("ComDet.dPSFsigmaTwo", 4), 2);
				fpDial.addChoice("ch2s: Sensitivity of detection:", sSensitivityOptions, Prefs.get("ComDet.SensitivityTwo", "Very dim particles (SNR=3)"));

				fpDial.addMessage("\n\n Colocalization analysis:\n");
				fpDial.addCheckbox("Calculate colocalization? (requires image with two color channels)", Prefs.get("ComDet.bColocalization", false));
				fpDial.addNumericField("Max distance between colocalized spot, pix", Prefs.get("ComDet.dColocDistance", 4), 2);
				fpDial.addCheckbox("Plot detected particles in both channels?", Prefs.get("ComDet.bPlotBothChannels", false));
		}		   
		fpDial.showDialog();
		if (fpDial.wasCanceled())
            return false;
		bBigParticles = fpDial.getNextBoolean();
		Prefs.set("ComDet.bBigParticles", bBigParticles);
		if(ChNumber == 2)
		{
			nTwoChannelOption = fpDial.getNextChoiceIndex();
			Prefs.set("ComDet.TwoChannelOption", TwoChannelOption[nTwoChannelOption]);		
		}
		dPSFsigma[0] = fpDial.getNextNumber();
		Prefs.set("ComDet.dPSFsigma", dPSFsigma[0]);
		nSensitivity[0] = fpDial.getNextChoiceIndex();
		Prefs.set("ComDet.Sensitivity", sSensitivityOptions[nSensitivity[0]]);
		if(ChNumber == 2)
		{
			dPSFsigma[1] = fpDial.getNextNumber();
			Prefs.set("ComDet.dPSFsigmaTwo", dPSFsigma[1]);
			nSensitivity[1] = fpDial.getNextChoiceIndex();
			Prefs.set("ComDet.SensitivityTwo", sSensitivityOptions[nSensitivity[1]]);
		
			bColocalization = fpDial.getNextBoolean();
			Prefs.set("ComDet.bColocalization", bColocalization);
			dColocDistance = fpDial.getNextNumber();
			Prefs.set("ComDet.dColocDistance", dColocDistance);
			bPlotBothChannels = fpDial.getNextBoolean();
			Prefs.set("ComDet.bPlotBothChannels", bPlotBothChannels);
		
		}

		nThreads = 50;
		TotCh=1;
		bTwoChannels = false;
		if(ChNumber == 2)
		{
			TotCh=2;
			bTwoChannels = true;
		}
		for(i=0;i<TotCh;i++)
		{
			
			dPSFsigma[i] *= 0.5;
			nAreaMax[i] = (int) (12.0*dPSFsigma[i]*dPSFsigma[i]);
			//putting limiting criteria on spot size
			nAreaCut[i] = (int) (4.0*dPSFsigma[i] * dPSFsigma[i]);
			nKernelSize[i] = (int) Math.ceil(3.0*dPSFsigma[i]);
			if(nKernelSize[i]%2 == 0)
				 nKernelSize[i]++;
	
			if(nSensitivity[i]<3)
			{
				nSensitivity[i] += 3;
			}
			else
			{
				nSensitivity[i] = (nSensitivity[i]-2)*10;

			}
		}
		return true;
	}

	
}
