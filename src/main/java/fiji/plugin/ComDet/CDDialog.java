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

	
	/** current active ImagePlus **/
	public ImagePlus imp;
	
	
	CDAnalysis cd;
	/**image width of original image**/
	int nWidth;  
	/**image height of original image**/
	int nHeight; 
	/**number of slices**/
	int nSlices; 
	
	/** original overlay **/
	Overlay savedOverlay;
	
	
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
	/** whether to include big particles in the detection (for each channel) **/
	boolean [] bBigParticles;
	/** Whether to segment large particles (for each channel) **/
	boolean [] bSegmentLargeParticles;	
	
	/** number of channels **/
	public int ChNumber;
	/** current channel in processing (starting from 1!) **/
	int iCh;
	
	/** main dialog window **/
	GenericDialog fpDial;
	
	boolean bMultiChannelDetection;

	
	
	/** dialog options**/
	String [] sROIManagerOneCh;
	String [] sROIManagerMultiCh;
	
	/** Flag determing whether to add detections to ROI Manager
	 * 0 - do not add
	 * 1 - add all detections
	 * 2 - add only colocalized particles
	 * **/
	int nRoiManagerAdd;

	
	//boolean bPreview;

	
	//colocalization analysis parameters
	boolean bColocalization;
	double dColocDistance;
	boolean bPlotMultiChannels;
	
	/** default constructor **/ 
	public CDDialog()
	{
		//dPSFsigma = new double[2];
		//nKernelSize = new int[2];
		//nAreaCut = new int[2];
		//nAreaMax =  new int[2];
		//nSensitivity = new float[2];
		sROIManagerOneCh = new String [] {
				"Nothing", "All detections"};
		sROIManagerMultiCh = new String [] {
				"Nothing", "All detections","Only colocalized particles"};
		
	}
	/** function initializing dialog for particles detection,
	 * reads parameters of current image (or Stack/HyperStack, etc) **/
	public void initImage(ImagePlus imp_in, int [] imageinfo_in)
	{
		imp=imp_in;
		ChNumber=imageinfo_in[2];
		dPSFsigma = new double[ChNumber];
		nKernelSize = new int[ChNumber];
		nAreaCut = new int[ChNumber];
		nAreaMax =  new int[ChNumber];
		nSensitivity = new float[ChNumber];
		bBigParticles = new boolean[ChNumber];
		bSegmentLargeParticles = new boolean[ChNumber];	
		
	}

	//dialog showing options for particle search algorithm		
	public boolean findParticles() {
		
		String nCurrentVersion;
		
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
		
		//multichannel input, ask user for the parameters
		if(ChNumber>1)
		{
			fpDial = new GenericDialog("Detect Particles");
			fpDial.addMessage("Multi-channel image is detected as input. \n");
			fpDial.addCheckbox("Calculate colocalization?", Prefs.get("ComDet.bColocalization", false));
			fpDial.addMessage("Colocalization analysis parameters:\n");
			fpDial.addNumericField("Max distance between colocalized spots", Prefs.get("ComDet.dColocDistance", 4), 2,5," pixels");
			fpDial.addCheckbox("Plot detected particles in all channels?", Prefs.get("ComDet.bPlotMultiChannels", false));
			fpDial.addChoice("Add to ROI Manager:", sROIManagerMultiCh, Prefs.get("ComDet.sROIManagerMulti", "Nothing"));
			
			fpDial.showDialog();
			if (fpDial.wasCanceled())
	            return false;
			bColocalization = fpDial.getNextBoolean();
			Prefs.set("ComDet.bColocalization", bColocalization);			
			dColocDistance = fpDial.getNextNumber();
			Prefs.set("ComDet.dColocDistance", dColocDistance);	
			bPlotMultiChannels = fpDial.getNextBoolean();
			Prefs.set("ComDet.bPlotMultiChannels", bPlotMultiChannels);
			nRoiManagerAdd = fpDial.getNextChoiceIndex();
			if(!bColocalization && nRoiManagerAdd==2)
			{
				nRoiManagerAdd=0;
				IJ.log("Cannot add colocalized particles to ROI, since colocalization option is unchecked. Nothing will be added.");
			}
			Prefs.set("ComDet.sROIManagerMultiCh", sROIManagerMultiCh[nRoiManagerAdd]);
		}

		savedOverlay=imp.getOverlay();
		
		
		for (iCh=1;iCh<=ChNumber;iCh++)
		{
			String sChN = Integer.toString(iCh);
			fpDial = new GenericDialog("Detect Particles channel"+sChN);
			fpDial.addMessage("Detection parameters:\n");
			fpDial.addCheckbox("ch"+sChN+"i: Include larger particles?", Prefs.get("ComDet.bBigParticles"+sChN, true));	
			fpDial.addCheckbox("ch"+sChN+"l: Segment larger particles (slow)?", Prefs.get("ComDet.bSegmentLargeParticles"+sChN, false));
			fpDial.addNumericField("ch"+sChN+"a: Approximate particle size", Prefs.get("ComDet.dPSFsigma"+sChN, 4), 2,5," pixels");
			fpDial.addNumericField("ch"+sChN+"s: Intensity threshold (in SD):", Prefs.get("ComDet.dSNRT"+sChN, 3), 2,5, "around (3-20)");
			if(ChNumber == 1)
				fpDial.addChoice("Add to ROI Manager:", sROIManagerOneCh, Prefs.get("ComDet.sROIManagerOne", "Nothing"));

			ImagePlus fakeimp=new ImagePlus("quick",new FloatProcessor(1,1));
			fakeimp.show();
			fpDial.addPreviewCheckbox(null,"Preview detection..");
			fakeimp.changes=false;
			fakeimp.close();
			fpDial.addDialogListener(this);

			fpDial.showDialog();

			imp.setOverlay(savedOverlay);
			imp.updateAndRepaintWindow();
			imp.show();
			
			if (fpDial.wasCanceled())
				{return false;}

			setPrefs();

		}
		
		
		
		return true;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		// TODO Auto-generated method stub
		
		CompositeImage multichannel_imp;
		int[] nImagePos;
		Roi RoiSelected;// = imp.getRoi();
		ImageProcessor ip;
		boolean bValuesOk;
		bValuesOk=getValues();
		if(!bValuesOk)
			return false;
		if(gd.wasOKed())
		{
			return true;
		}
		if(gd.isPreviewActive())
		{
				gd.previewRunning(true);
				cd = new CDAnalysis(ChNumber);
				
				multichannel_imp=(CompositeImage) imp;
				multichannel_imp.setC(iCh);
				cd.colorCh= multichannel_imp.getChannelColor();
				cd.overlay_= new Overlay();
				cd.initConvKernel(this,iCh-1);
				RoiSelected= imp.getRoi();
				nImagePos = imp.convertIndexToPosition(imp.getSlice());
				nImagePos[0]=iCh;
				imp.setPositionWithoutUpdate(nImagePos[0], nImagePos[1],nImagePos[2]);
				ip = imp.getProcessor().duplicate();
				
				cd.detectParticles(ip, this,nImagePos, 1, RoiSelected);
				imp.setOverlay(cd.overlay_);
				imp.updateAndRepaintWindow();
				imp.show();
				gd.previewRunning(false);
				
		}
		else
		{
			imp.setOverlay(savedOverlay);
			imp.updateAndRepaintWindow();
			imp.show();
		}
		return true;
	}
	

	/** function reads parameters values from the dialog **/
	public boolean getValues()
	{
		int indCh=iCh-1;

		bBigParticles[indCh] = fpDial.getNextBoolean();
		bSegmentLargeParticles[indCh] = fpDial.getNextBoolean();

		dPSFsigma[indCh] = fpDial.getNextNumber();
		if(Double.isNaN(dPSFsigma[indCh]))
			return false;
		
		nSensitivity[indCh] = (float)fpDial.getNextNumber();
		if(Double.isNaN(nSensitivity[indCh]))
			return false;
		
		
		
		if(ChNumber == 1)
		{
			nRoiManagerAdd = fpDial.getNextChoiceIndex();
			
		}
		//bPreview = fpDial.getNextBoolean();
		nThreads = 50;

			
		dPSFsigma[indCh] *= 0.5;
		nAreaMax[indCh] = (int) (12.0*dPSFsigma[indCh]*dPSFsigma[indCh]);
		//putting limiting criteria on spot size
		nAreaCut[indCh] = (int) (dPSFsigma[indCh] * dPSFsigma[indCh]);
		nKernelSize[indCh] = (int) Math.ceil(3.0*dPSFsigma[indCh]);
		if(nKernelSize[indCh]%2 == 0)
			 nKernelSize[indCh]++;

		return true;
		
	}
	
	
	/** function adds parameters values to registry **/
	public void setPrefs()
	{
		String sChN = Integer.toString(iCh);
		Prefs.set("ComDet.bBigParticles"+sChN, bBigParticles[iCh-1]);
		Prefs.set("ComDet.bSegmentLargeParticles"+sChN, bSegmentLargeParticles[iCh-1]);

		Prefs.set("ComDet.dPSFsigma"+sChN, dPSFsigma[iCh-1]*2.0);
		Prefs.set("ComDet.dSNRT+sChN", nSensitivity[iCh-1]);
		
		if(ChNumber == 1)		
			Prefs.set("ComDet.sROIManagerOne", sROIManagerOneCh[nRoiManagerAdd]);		

		
		//Prefs.set("ComDet.bPreview", bPreview);
	}	
	
	
}
