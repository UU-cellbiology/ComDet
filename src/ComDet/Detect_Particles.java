package ComDet;


import ComDet.CDAnalysis;
import ComDet.CDDialog;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class Detect_Particles implements PlugIn {
	
	ImagePlus imp;
	ImageProcessor ip;
	Overlay SpotsPositions;
	Roi RoiActive;
	
	CDThread [] cdthreads;
	
	CDDialog cddlg = new CDDialog();
	CDAnalysis cd = new CDAnalysis();
	
	//launch search of particles 
	public void run(String arg) {
		
		int nFreeThread = -1;
		int nSlice = 0;
		boolean bContinue = true;
		
		IJ.register(Detect_Particles.class);
		
		
		cd.ptable.reset(); // erase particle table
		
		//checking whether there is any open images
		imp = IJ.getImage();
		//SpotsPositions = imp.getOverlay();
		SpotsPositions = new Overlay();
		if (imp==null)
		{
		    IJ.noImage();
		    return;
		}
		else if (imp.getType() != ImagePlus.GRAY8 && imp.getType() != ImagePlus.GRAY16 ) 
		{
		    IJ.error("8 or 16 bit greyscale image required");
		    return;
		}
		
		if (!cddlg.findParticles()) return;
		
		//generating convolution kernel with size of PSF
		cd.initConvKernel(cddlg);
		//putting limiting criteria on spot size
		cddlg.nAreaCut = (int) (cddlg.dPSFsigma * cddlg.dPSFsigma);
		//cddlg.nAreaCut = cddlg.nKernelSize + 1;
		cd.nParticlesCount = new int [imp.getStackSize()];
		//getting active roi
		RoiActive = imp.getRoi();
		
		// using only necessary number of threads	 
		if(cddlg.nThreads < imp.getStackSize()) 
			cdthreads = new CDThread[cddlg.nThreads];
		else
			cdthreads = new CDThread[imp.getStackSize()];
		
		
		nFreeThread = -1;
		nSlice = 0;
		bContinue = true;
		
		while (bContinue)
		{
			//check whether reached the end of stack
			if (nSlice >= imp.getStackSize()) bContinue = false;
			else
			{
				imp.setSlice(nSlice+1);
				ip = imp.getProcessor().duplicate();
			}
			
			if (bContinue)
			{
				//filling free threads in the beginning
				if (nSlice < cdthreads.length)
					nFreeThread = nSlice;				
				else
				//looking for available free thread
				{
					nFreeThread = -1;
					while (nFreeThread == -1)
					{
						for (int t=0; t < cdthreads.length; t++)
						{
							if (!cdthreads[t].isAlive())
							{
								nFreeThread = t;
								break;
							}
						}
						if (nFreeThread == -1)
						{
							try
							{
								Thread.currentThread();
								Thread.sleep(1);
							}
							catch(Exception e)
							{
									IJ.error(""+e);
							}
						}
					}
				}
				cdthreads[nFreeThread] = new CDThread();
				cdthreads[nFreeThread].cdsetup(ip, cd, cddlg, nSlice, SpotsPositions, RoiActive);
				cdthreads[nFreeThread].start();
			
			} //end of if (bContinue)
			nSlice++;
		} // end of while (bContinue)
		
		for (int t=0; t<cdthreads.length;t++)
		{
			try
			{
				cdthreads[t].join();
			}
			catch(Exception e)
			{
				IJ.error(""+e);
			}
		}
		
		if(imp.getStackSize()==1)	
		{
			int nRois = SpotsPositions.size();
			for(int i = 0;i<nRois;i++)
			{
				SpotsPositions.get(i).setPosition(0);
			}
			
		}
		imp.setOverlay(SpotsPositions);
		imp.updateAndRepaintWindow();
		imp.show();
		//imp.updateAndDraw();
		cd.showTable();

	} 

}

class CDThread extends Thread 
{
	private Overlay SpotsPositions;
	private ImageProcessor ip;
	private CDDialog cddlg;
	private int nFrame;
	private CDAnalysis cd;
	private Roi RoiActive;
	
	public void cdsetup(ImageProcessor ip, CDAnalysis cd, CDDialog cddlg, int nFrame, Overlay SpotsPositions, Roi RoiActive)
	{
		this.cd     = cd;
		this.ip     = ip;
		this.cddlg  = cddlg;
		this.nFrame = nFrame;
		this.SpotsPositions = SpotsPositions;
		this.RoiActive = RoiActive;
		
	}
	
	public void run()
	{
		this.cd.detectParticles(this.ip, this.cddlg, this.nFrame, this.SpotsPositions, this.RoiActive);
	}
}

