package ComDet;


import java.awt.Color;
import java.awt.Frame;
import java.awt.List;
import java.util.ArrayList;

import ComDet.CDAnalysis;
import ComDet.CDDialog;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

public class Detect_Particles implements PlugIn {
	

	ImagePlus imp;
	CompositeImage twochannels_imp;
	ImageProcessor ip;
	Overlay SpotsPositions;
	Roi RoiActive;

	int [] imageinfo;
	
	int [] nCurrPos;
	
	CDThread [] cdthreads;
	
	CDDialog cddlg = new CDDialog();
	CDAnalysis cd = new CDAnalysis();
	CDProgressCount cdcount = new CDProgressCount(0);

	Color colorCh1;
	Color colorCh2;
	Color colorColoc = Color.yellow;
	public TextWindow SummaryTable;  

	//launch search of particles 
	public void run(String arg) {
		
		int nFreeThread = -1;
		int nSlice = 0;
		boolean bContinue = true;
		int nStackSize;
		
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
	
		//ok, image seems legit. 
		//Let's get info about it	
		imageinfo = imp.getDimensions();
		
		if (!cddlg.findParticles(imageinfo[2])) return;
		
	
		
		if(cddlg.bColocalization && imageinfo[2]!=2)
		{
		    IJ.error("Colocalization analysis requires image with 2 composite color channels!");
		    return;
			
		}
		
		if(cddlg.bColocalization)
		{
			twochannels_imp = (CompositeImage) imp;
			//get colors of each channel
			twochannels_imp.setC(1);
			colorCh1 = twochannels_imp.getChannelColor();
			twochannels_imp.setC(2);
			colorCh2 = twochannels_imp.getChannelColor();			
		}
		else
		{
			colorCh1 = Color.yellow;
			colorCh2 = Color.yellow;
		}
		
		nStackSize = imp.getStackSize();
		
		//generating convolution kernel with size of PSF
		if(cddlg.bTwoChannels)
		{
			cd.initConvKernel(cddlg,0);
			cd.initConvKernel(cddlg,1);
		}
		else
		{
			cd.initConvKernel(cddlg,0);
		}
		cd.nParticlesCount = new int [nStackSize];
		//getting active roi
		RoiActive = imp.getRoi();
		
		
		
		// using only necessary number of threads	 
		if(cddlg.nThreads < nStackSize) 
			cdthreads = new CDThread[cddlg.nThreads];
		else
			cdthreads = new CDThread[nStackSize];
		
		
		IJ.showStatus("Finding particles...");
		IJ.showProgress(0, nStackSize);
		nFreeThread = -1;
		nSlice = 0;
		bContinue = true;
		
		
		while (bContinue)
		{
			//check whether reached the end of stack
			if (nSlice >= nStackSize) bContinue = false;
			else
			{
				imp.setSlice(nSlice+1);
				nCurrPos = imp.convertIndexToPosition(nSlice+1);
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
				cdthreads[nFreeThread].cdsetup(ip, cd, cddlg, nCurrPos, nSlice, RoiActive, nStackSize, cdcount);
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
		

		IJ.showStatus("Finding particles...done.");
		if(cd.ptable.getCounter()<1)
		{
			IJ.showStatus("No particles found!");
		}
		else
		{
			Sort_Results_CD.sorting_external_silent(cd, 5, true);
			//Sort_Results_CD.sorting_external_silent(cd, 2, true);
			addParticlesToOverlay(nStackSize);
			imp.setOverlay(SpotsPositions);
			imp.updateAndRepaintWindow();
			imp.show();
			//imp.updateAndDraw();
			showTable();
		}

	} 
	
	
	void addParticlesToOverlay(int nStackSize_)
	{
		int nCount;
		int nPatNumber;
		double dRadius;
		Roi spotROI;
		double [] dSigma = cddlg.dPSFsigma;
		double [] absframe;
		double [] x;
		double [] y;
		double [] frames;
		double [] channel;
		double [] slices;
		boolean [] colocalizations;

		//coordinates
		x   = cd.ptable.getColumnAsDoubles(1);		
		y   = cd.ptable.getColumnAsDoubles(2);
		
		//absolute total number
		absframe = cd.ptable.getColumnAsDoubles(0);
		//channel
		channel   = cd.ptable.getColumnAsDoubles(3);
		//slice
		slices   = cd.ptable.getColumnAsDoubles(4);
		//frame number
		frames   = cd.ptable.getColumnAsDoubles(5);
		
		nPatNumber = x.length;
		
		if(!cddlg.bColocalization)
		{
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				if(cddlg.bTwoChannels)
					dRadius = 2*dSigma[(int)(channel[nCount]-1)];
				else
					dRadius = 2*dSigma[0];
									
				spotROI = new OvalRoi((int)(0.5+x[nCount]-dRadius),(int)(0.5+y[nCount]-dRadius),(int)(2*dRadius),(int)(2*dRadius));
				spotROI.setStrokeColor(colorColoc);
				if(nStackSize_==1)
					spotROI.setPosition(0);
				else
					spotROI.setPosition((int)absframe[nCount]);
				SpotsPositions.add(spotROI);									
			}
		}
		else
		//analyzing colocalization
		{
						
			int nFrame;
			int nSlice;
			double dThreshold;

			double dDistance;
			double dDistanceCand;
			int nCandidate;
			//boolean bOverlap;
			double [] dIntermediate1;
			double [] dIntermediate2;
			//double [][] spotsArr2;			
			int i,j;
			int nColocCount;
			dThreshold = cddlg.dColocDistance;
			ArrayList<double[]> spotsCh1 = new ArrayList<double[]>();
			ArrayList<double[]> spotsCh2 = new ArrayList<double[]>();
			//ArrayList<double[]> spotsCh2;

			
			colocalizations = new boolean [nPatNumber];
			cd.colocstat = new int [imageinfo[3]][imageinfo[4]];
			//filling up arrays with current frame
			for(nFrame=1; nFrame<=imageinfo[4]; nFrame++)
			{
				for(nSlice=1; nSlice<=imageinfo[3]; nSlice++)
				{
					//filling up arrays with current detections in both channels
					spotsCh1.clear();
					spotsCh2.clear();
					for(nCount = 0; nCount<nPatNumber; nCount++)
					{
						if(frames[nCount]==nFrame && slices[nCount]==nSlice)
						{
							if(channel[nCount]==1)
								spotsCh1.add(new double [] {x[nCount],y[nCount], nCount, 0});
							else
								spotsCh2.add(new double [] {x[nCount],y[nCount], nCount, 0});
						}
					}
					nColocCount = 0;
					//let's compare each particle against each					
					for(i=0;i<spotsCh1.size();i++)
					{
						//bOverlap = false;
						nCandidate = -1;
						dDistanceCand = 2.0*dThreshold;
						dIntermediate1 = spotsCh1.get(i);
						
						for(j=0;j<spotsCh2.size();j++)
						{
							if(spotsCh2.get(j)[3]==0)
							{
								dDistance = Math.sqrt(Math.pow(dIntermediate1[0]-spotsCh2.get(j)[0], 2.0)+Math.pow(dIntermediate1[1]-spotsCh2.get(j)[1], 2.0));
								if(dDistance <= dThreshold)
								{
									if(dDistance<dDistanceCand)
									{
										dDistanceCand=dDistance;
										nCandidate = j;
									}
									
								}
								
							}
						
						}//for(j=0;j<spotsCh2.size();j++)
						//found colocalization
						if(nCandidate>=0)
						{
							dIntermediate2 = spotsCh2.get(nCandidate);
							dIntermediate2[3] = 1;
							spotsCh2.set(nCandidate,dIntermediate2);
							dIntermediate2[0] = 0.5*(dIntermediate2[0]+dIntermediate1[0]);
							dIntermediate2[1] = 0.5*(dIntermediate2[1]+dIntermediate1[1]);
							colocalizations[(int) dIntermediate2[2]]=true;
							colocalizations[(int) dIntermediate1[2]]=true;
							for(j=1; j<3; j++)
							{
								dRadius = dSigma[0]+dSigma[1];
								spotROI = new OvalRoi((int)(0.5+dIntermediate2[0]-dRadius),(int)(0.5+dIntermediate2[1]-dRadius),(int)(2*dRadius),(int)(2*dRadius));
								spotROI.setStrokeColor(colorColoc);
								spotROI.setPosition(j,nSlice,nFrame);
								SpotsPositions.add(spotROI);
							}
							nColocCount++;
							
						}
					}
				
					cd.colocstat[nSlice-1][nFrame-1] = nColocCount;
				}//for(nSlice=1;
				
			}//for(nFrame=1

			//drawing the rest
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				if(!colocalizations[nCount])
				{
					if( !cddlg.bPlotBothChannels)
					{
						dRadius = 2*dSigma[(int) (channel[nCount]-1)];
						spotROI = new OvalRoi((int)(0.5+x[nCount]-dRadius),(int)(0.5+y[nCount]-dRadius),(int)(2*dRadius),(int)(2*dRadius));
						if((int)(channel[nCount])==1)
							spotROI.setStrokeColor(colorCh1);
						else
							spotROI.setStrokeColor(colorCh2);
						spotROI.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
						SpotsPositions.add(spotROI);
					}
					else
					{
						for (int k=1;k<3;k++)
						{
							dRadius = 2*dSigma[(int) (channel[nCount]-1)];
							spotROI = new OvalRoi((int)(0.5+x[nCount]-dRadius),(int)(0.5+y[nCount]-dRadius),(int)(2*dRadius),(int)(2*dRadius));
							if((int)(channel[nCount])==1)
								spotROI.setStrokeColor(colorCh1);
							else
								spotROI.setStrokeColor(colorCh2);
							spotROI.setPosition(k,(int)slices[nCount],(int)frames[nCount]);
							SpotsPositions.add(spotROI);
						}
						
					}
				}				
			}
			//adding info about colocalization to Results table
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				if(colocalizations[nCount])
					cd.ptable.setValue("Colocalized", nCount,1);
				else
					cd.ptable.setValue("Colocalized", nCount,0);
			}
		}//if(!cddlg.bColocalization)
	
	}
	
	//show summary and results tables

	void showTable()
	{
			String sSumData ="";
			String sColHeadings ="";
			int nChannel1Pat, nChannel2Pat, nColocNumber;
						
	        if(!cddlg.bColocalization)
	        {
	        	sColHeadings = "Channel\tSlice\tFrame\tNumber_of_Particles";
	        	for(int i = 0;i<cd.nParticlesCount.length; i++)
	        	{
	        		nCurrPos = imp.convertIndexToPosition(i+1);
	        		
	        		sSumData = sSumData +  Integer.toString(nCurrPos[0])+"\t" +Integer.toString(nCurrPos[1])+"\t" +Integer.toString(nCurrPos[2])+"\t" +Integer.toString(cd.nParticlesCount[i])+"\n";;
	         	//	sSumData = sSumData + Integer.toString(i+1)+"\t"+Integer.toString(cd.nParticlesCount[i])+"\n";
	         	
	        		//sSumData = sSumData + 
	        	}
			
	        	Frame frame = WindowManager.getFrame("Summary");
	        	if (frame!=null && (frame instanceof TextWindow) )
	        	{
	        		SummaryTable = (TextWindow)frame;
	        		SummaryTable.getTextPanel().clear();
	        		SummaryTable.getTextPanel().setColumnHeadings(sColHeadings);
	        		SummaryTable.getTextPanel().append(sSumData);
	        		SummaryTable.getTextPanel().updateDisplay();			
	        	}
	        	else
	        		SummaryTable = new TextWindow("Summary",sColHeadings , sSumData, 450, 300);
	        }
	        else
	        {
	        	sColHeadings = "Slice\tFrame\tParticles_in_Ch1\tParticles_in_Ch2\tColocalized\t%_Ch1_coloc\t%Ch2_coloc\t";
	        	for(int i = 1;i<=imageinfo[3]; i++)//slice
	        	{
		        	for(int j = 1;j<=imageinfo[4]; j++)//frame
		        	{

		        		nChannel1Pat = cd.nParticlesCount[imp.getStackIndex(1,i,j)-1];
		        		nChannel2Pat = cd.nParticlesCount[imp.getStackIndex(2,i,j)-1];
		        		nColocNumber = cd.colocstat[i-1][j-1];
		        		sSumData = sSumData +  Integer.toString(i)+"\t" +Integer.toString(j)+"\t";// +Integer.toString(nCurrPos[2])+"\t" +Integer.toString(cd.nParticlesCount[i])+"\n";;
	        		
		        		sSumData = sSumData + Integer.toString(nChannel1Pat)+"\t"+Integer.toString(nChannel2Pat)+"\t" + Integer.toString(nColocNumber)+"\t";
	        		
		        		sSumData = sSumData + String.format("%.2f", (100*(double)(nColocNumber)/(double)(nChannel1Pat)))+"\t"+String.format("%.2f", 100*(double)(nColocNumber)/(double)(nChannel2Pat))+"\n";
	         	
		        	} 
	        	}
			
	        	Frame frame = WindowManager.getFrame("Summary");
	        	if (frame!=null && (frame instanceof TextWindow) )
	        	{	        		
	        		SummaryTable = (TextWindow)frame;	        		
	        		SummaryTable.getTextPanel().clear();
	        		SummaryTable.getTextPanel().setColumnHeadings(sColHeadings);
	        		SummaryTable.getTextPanel().append(sSumData);
	        		SummaryTable.getTextPanel().updateDisplay();		

	        	}
	        	else
	        		SummaryTable = new TextWindow("Summary",sColHeadings , sSumData, 450, 300);
	        }
			
	        //Show Results table with coordinates
	            
			cd.ptable.show("Results");

	}
	

}

class CDThread extends Thread 
{

	private ImageProcessor ip;
	private CDDialog cddlg;
	private int [] nImagePos;
	private int nSlice;
	private CDAnalysis cd;
	private Roi RoiActive;
	private CDProgressCount cdcount;
	private int nStackSize;
	
	public void cdsetup(ImageProcessor ip, CDAnalysis cd, CDDialog cddlg, int [] nImagePos, int nSlice, Roi RoiActive,  int nStackSize, CDProgressCount cdcount)
	{
		this.cd     = cd;
		this.ip     = ip;
		this.cddlg  = cddlg;
		this.nImagePos = nImagePos;
		this.nSlice = nSlice;
		this.RoiActive = RoiActive;
		this.cdcount = cdcount;
		this.nStackSize = nStackSize;		
	}
	
	public void run()
	{
		this.cd.detectParticles(this.ip, this.cddlg, this.nImagePos, this.nSlice, this.RoiActive);
		cdcount.CDProgressCountIncrease();
		IJ.showProgress(cdcount.nSliceLeft-1, nStackSize);
	}
}



class CDProgressCount
{
	 
	public int nSliceLeft;
	public CDProgressCount (int ini_value)
	{
		nSliceLeft = ini_value;
	}
	public void CDProgressCountIncrease()
	{
		synchronized (this)
		{ 
			nSliceLeft++;
		}
		
	}
	public void CDProgressCountIncreaseValue(int inc_value)
	{
		synchronized (this)
		{ 
			nSliceLeft+=inc_value;
		}
		
	}
	
}
