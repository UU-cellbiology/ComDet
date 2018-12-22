package fiji.plugin.ComDet;

import java.awt.AWTEvent;
import java.awt.Color;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;


public class CDDialog implements DialogListener{

	public ImagePlus imp;
	CDAnalysis cd;
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
	float [] nSensitivity;
	
	/** number of channels **/
	public int ChNumber;
	
	/** main dialog window **/
	GenericDialog fpDial;
	
	boolean bTwoChannels;
	/** whether to include big particles in the detection**/
	boolean bBigParticles;
	
	
	/** dialog options**/
	String [] sROIManagerOneCh;
	String [] sROIManagerTwoCh;
	
	/** Flag determing whether to add detections to ROI Manager
	 * 0 - do not add
	 * 1 - add all detections
	 * 2 - add only colocalized particles
	 * **/
	int nRoiManagerAdd;
	/** Whether to segment large particles
	 * **/
	boolean bSegmentLargeParticles=false;
	
	//boolean bPreview;

	
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
		nSensitivity = new float[2];
		sROIManagerOneCh = new String [] {
				"Nothing", "All detections"};
		sROIManagerTwoCh = new String [] {
				"Nothing", "All detections","Only colocalized particles"};
		
	}

	//dialog showing options for particle search algorithm		
	public boolean findParticles() {
		
		String nCurrentVersion;

		fpDial = new GenericDialog("Detect Particles");
		
		
		//check if it is new version
		// if yes, display one time message
		nCurrentVersion=Prefs.get("ComDet.PluginVersion","none");
		if(!nCurrentVersion.equals(ComDetConstants.ComDetVersion))
		{
			IJ.showMessage("This is a new installation or an update to "+ComDetConstants.ComDetVersion+" version of ComDet plugin!\n "
					//+ "Take notice that detection algorithm has changed in comparison to previous versions.\n"
					+"Check https://github.com/ekatrukha/ComDet/wiki/Updates-history for description of changes. ");
			Prefs.set("ComDet.PluginVersion",ComDetConstants.ComDetVersion);
		}
		
		//fpDial.addChoice("Particle detection method:", DetectOptions, Prefs.get("ComDet.DetectMethod", "Round shape"));
		fpDial.addMessage("Detection parameters:\n");
		fpDial.addCheckbox("Include larger particles?", Prefs.get("ComDet.bBigParticles", true));		
		fpDial.addCheckbox("Segment larger particles (slow)?", Prefs.get("ComDet.bSegmentLargeParticles", false));
		if(ChNumber == 2)
		{
			//fpDial.addChoice("Two channel detection:", TwoChannelOption, Prefs.get("ComDet.TwoChannelOption", "Detect in both channels independently"));
			fpDial.addMessage("Channel 1:\n");		
		}
			fpDial.addNumericField("ch1a: Approximate particle size", Prefs.get("ComDet.dPSFsigma", 4), 2,5," pixels");
			fpDial.addNumericField("ch1s: Intensity threshold (in SD):", Prefs.get("ComDet.dSNRT", 3), 2,5, "around (3-20)");
			//fpDial.addChoice("ch1s: Sensitivity of detection:", sSensitivityOptions, Prefs.get("ComDet.Sensitivity", "Very dim particles (SNR=3)"));
		if(ChNumber == 2)
		{
				fpDial.addMessage("Channel 2:\n");
				fpDial.addNumericField("ch2a: Approximate particle size", Prefs.get("ComDet.dPSFsigmaTwo", 4), 2, 5," pixels");
				fpDial.addNumericField("ch2s: Intensity threshold (in SD):", Prefs.get("ComDet.dSNRTTwo", 3), 2,5, "around (3-20)");
				//fpDial.addChoice("ch2s: Sensitivity of detection:", sSensitivityOptions, Prefs.get("ComDet.SensitivityTwo", "Very dim particles (SNR=3)"));

				fpDial.addMessage("\n\n Colocalization analysis:\n");
				fpDial.addCheckbox("Calculate colocalization?", Prefs.get("ComDet.bColocalization", false));
				fpDial.addNumericField("Max distance between colocalized spot", Prefs.get("ComDet.dColocDistance", 4), 2,5," pixels");
				fpDial.addCheckbox("Plot detected particles in both channels?", Prefs.get("ComDet.bPlotBothChannels", false));
		}		   
		if(ChNumber == 1)
			fpDial.addChoice("Add to ROI Manager:", sROIManagerOneCh, Prefs.get("ComDet.sROIManagerOne", "Nothing"));
		else
			fpDial.addChoice("Add to ROI Manager:", sROIManagerTwoCh, Prefs.get("ComDet.sROIManagerTwo", "Nothing"));
		//fpDial.addCheckbox("Preview", false);
		
		//work around about preview checkbox not showing in composite images (why??)
		
		ImagePlus fakeimp=new ImagePlus("quick",new FloatProcessor(1,1));
		fakeimp.show();
		fpDial.addPreviewCheckbox(null,"Preview detection..");
		fakeimp.changes=false;
		fakeimp.close();
		fpDial.addDialogListener(this);
		
		fpDial.showDialog();
		if (fpDial.wasCanceled())
		{
			imp.setOverlay(new Overlay());
			imp.updateAndRepaintWindow();
			imp.show();
            return false;
		}
		 if (!dialogItemChanged(fpDial, null))   //read parameters
		 {
			 	//setPrefs();
	            return false;
		 }
		//getValues();
		setPrefs();
		
		return true;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		// TODO Auto-generated method stub
		
		CompositeImage twochannels_imp;
		int[] nImagePos;
		int i;
		Roi RoiSelected;// = imp.getRoi();
		ImageProcessor ip;
		boolean bValuesOk;
		bValuesOk=getValues();
		if(!bValuesOk)
			return false;
		if(gd.isPreviewActive())
		{
			//two channels
			if(ChNumber==2)
			{
				gd.previewRunning(true);
				cd = new CDAnalysis();
				twochannels_imp = (CompositeImage) imp;
				cd.overlay_=new Overlay();
				cd.initConvKernel(this,0);
				cd.initConvKernel(this,1);
				RoiSelected= imp.getRoi();
				nImagePos = imp.convertIndexToPosition(imp.getSlice());
				
				for (i=1;i<3;i++)
				{
					twochannels_imp.setC(i);
					cd.colorCh= twochannels_imp.getChannelColor();
					nImagePos[0]=i;
					imp.setPositionWithoutUpdate(nImagePos[0], nImagePos[1],nImagePos[2]);
					ip = imp.getProcessor().duplicate();
					cd.detectParticles(ip, this,nImagePos, 1, RoiSelected);
				}
				//cd.detectParticles(ip, this,imp.convertIndexToPosition(1), 1, RoiSelected);
				imp.setOverlay(cd.overlay_);
				imp.updateAndRepaintWindow();
				imp.show();
				gd.previewRunning(false);
			}
			// 1 or 3 or more channels
			else
			{
				gd.previewRunning(true);
				cd = new CDAnalysis();
				
				cd.colorCh=Color.yellow;
				cd.overlay_=new Overlay();
				cd.initConvKernel(this,0);
				RoiSelected= imp.getRoi();
				ip = imp.getProcessor().duplicate();
				cd.detectParticles(ip, this,imp.convertIndexToPosition(1), 1, RoiSelected);
				imp.setOverlay(cd.overlay_);
				imp.updateAndRepaintWindow();
				imp.show();
				gd.previewRunning(false);
				
			}
			//IJ.log("preview on!");
		}
		else
		{
			imp.setOverlay(new Overlay());
			imp.updateAndRepaintWindow();
			imp.show();
		}
		return true;
	}
	

	/** function reads parameters values from the dialog **/
	public boolean getValues()
	{

		int i,TotCh;
		bBigParticles = fpDial.getNextBoolean();
		bSegmentLargeParticles = fpDial.getNextBoolean();

		dPSFsigma[0] = fpDial.getNextNumber();
		if(Double.isNaN(dPSFsigma[0]))
			return false;
		
		nSensitivity[0] = (float)fpDial.getNextNumber();
		if(Double.isNaN(nSensitivity[0]))
			return false;
		
		
		if(ChNumber == 2)
		{
			dPSFsigma[1] = fpDial.getNextNumber();
			if(Double.isNaN(dPSFsigma[1]))
				return false;
			
			nSensitivity[1] = (float)fpDial.getNextNumber();
			if(Double.isNaN(nSensitivity[1]))
				return false;
		
			bColocalization = fpDial.getNextBoolean();
			
			dColocDistance = fpDial.getNextNumber();
			
			bPlotBothChannels = fpDial.getNextBoolean();
			
		}
		
		if(ChNumber == 1)
		{
			nRoiManagerAdd = fpDial.getNextChoiceIndex();
			
		}
		else
		{
			nRoiManagerAdd = fpDial.getNextChoiceIndex();
			if(!bColocalization && nRoiManagerAdd==2)
			{
				nRoiManagerAdd=0;
				IJ.log("Cannot add colocalized particles to ROI, since colocalization option is unchecked. Switching to nothing.");
			}
			
		}
		//bPreview = fpDial.getNextBoolean();
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
			nAreaCut[i] = (int) (dPSFsigma[i] * dPSFsigma[i]);
			nKernelSize[i] = (int) Math.ceil(3.0*dPSFsigma[i]);
			if(nKernelSize[i]%2 == 0)
				 nKernelSize[i]++;
	
			/*if(nSensitivity[i]<3)
			{
				nSensitivity[i] += 3;
			}
			else
			{
				nSensitivity[i] = (nSensitivity[i]-2)*10;

			}*/
		}
		return true;
		
	}
	
	
	/** function adds parameters values to registry **/
	public void setPrefs()
	{
		Prefs.set("ComDet.bBigParticles", bBigParticles);
		Prefs.set("ComDet.bSegmentLargeParticles", bSegmentLargeParticles);

		Prefs.set("ComDet.dPSFsigma", dPSFsigma[0]*2.0);
		Prefs.set("ComDet.dSNRT", nSensitivity[0]);
		
		if(ChNumber == 2)
		{
			Prefs.set("ComDet.dPSFsigmaTwo", dPSFsigma[1]*2.0);
			Prefs.set("ComDet.dSNRTTwo", nSensitivity[1]);
			Prefs.set("ComDet.bColocalization", bColocalization);
			Prefs.set("ComDet.dColocDistance", dColocDistance);
			Prefs.set("ComDet.bPlotBothChannels", bPlotBothChannels);
		}
		if(ChNumber == 1)		
			Prefs.set("ComDet.sROIManagerOne", sROIManagerOneCh[nRoiManagerAdd]);		
		else
			Prefs.set("ComDet.sROIManagerTwo", sROIManagerTwoCh[nRoiManagerAdd]);
		
		//Prefs.set("ComDet.bPreview", bPreview);
	}	
	
	
}
