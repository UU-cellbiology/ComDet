package fiji.plugin.ComDet;


import java.awt.Color;
import java.awt.Frame;
import java.util.ArrayList;

import fiji.plugin.ComDet.CDAnalysis;
import fiji.plugin.ComDet.CDDialog;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

public class Detect_Particles implements PlugIn {
	

	ImagePlus imp;
	boolean bIsHyperStack;
	int nStackSize;
	CompositeImage multichannels_imp;
	ImageProcessor ip;
	Overlay SpotsPositions;
	Roi RoiActive;
	RoiManager roi_manager;
	int [] imageinfo;
	
	int [] nCurrPos;
	
	CDThread [] cdthreads;
	
	CDDialog cddlg = new CDDialog();
	CDAnalysis cd;// = new CDAnalysis();
	CDProgressCount cdcount = new CDProgressCount(0);

	Color [] colorsCh;
	//Color colorCh2;
	Color colorColoc = Color.yellow;
	public TextWindow SummaryTable;
	public ResultsTable SummaryRT; 
	/**integrated intensities in all channels **/
	double [][] dIntMulti;
	
	/** total number of 2 channels combinations**/
	int nCombinationsN;
	/** table storing channels combinations per index (channel is counting from zero **/
	int [][] indexCombTable;

	//launch search of particles 
	public void run(String arg) {
		
		int nFreeThread = -1;
		int nSlice = 0;
		boolean bContinue = true;
		int i;
		
		IJ.register(Detect_Particles.class);

		
	
		
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
		
		cddlg.initImage(imp, imageinfo);

		
		if (!cddlg.findParticles()) 
			return;
		
		
		
		if(cddlg.bColocalization && imageinfo[2]<2)
		{
		    IJ.error("Colocalization analysis requires image with at least 2 composite color channels!");
		    return;
			
		}
		
		//log parameters to Log window
		ComDetLog();
		cd = new CDAnalysis(cddlg.ChNumber);
		cd.ptable.reset(); // erase particle table
		
		colorsCh= new Color [cddlg.ChNumber];
		if(cddlg.bColocalization)
		{
			//get colors of each channel
			multichannels_imp = (CompositeImage) imp;
		
			for (i=0;i<cddlg.ChNumber;i++)
			{
				multichannels_imp.setC(i+1);
				colorsCh[i] = multichannels_imp.getChannelColor();
			}
			
		}
		else
		{
			for (i=0;i<cddlg.ChNumber;i++)
			{
				colorsCh[i] = colorColoc;
			}
		}
		
		nStackSize = imp.getStackSize();
		//imp.isHyperStack();
		
		//generating convolution kernel with size of PSF
		for (i=0;i<cddlg.ChNumber;i++)
		{
			cd.initConvKernel(cddlg,i);
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
			IJ.error("No particles found!");
		}
		else
		{
			//order results table
			Sort_Results_CD.sorting_external_silent(cd, 3, true);
			Sort_Results_CD.sorting_external_silent(cd, 4, true);
			Sort_Results_CD.sorting_external_silent(cd, 5, true);
			
			//in case we need to add ROIs to ROI Manager
			if(cddlg.nRoiManagerAdd>0)
			{
				roi_manager = RoiManager.getInstance();
			    if(roi_manager == null) 
			    	roi_manager = new RoiManager();
			    roi_manager.reset();
			}
			
			addParticlesToOverlay();
			
			imp.setOverlay(SpotsPositions);
			imp.updateAndRepaintWindow();
			imp.show();
			
			showTable();
			if(cddlg.nRoiManagerAdd>0)
			{
				roi_manager.setVisible(true);
				roi_manager.toFront();
			}
		}

	} 
	/** analyzing colocalization between particles 
	 * and adding them to overlay in case of single or simultaneous detection **/	
	void addParticlesToOverlay()
	{
		int nCount;
		int nPatNumber;
		Roi spotROI;
		
		double [] absframe;
		double [] x;
		double [] y;
		double [] xmin;
		double [] ymin;
		double [] xmax;
		double [] ymax;
		double [] dInt;

	
		double [] frames;
		double [] channel;
		double [] slices;
		boolean [][] colocalizations;
		//double [] colocIntRatio;
		// experimental color
		Color colorExp;
		

		//absolute total number
		absframe = cd.ptable.getColumnAsDoubles(0);
		//coordinates
		x   = cd.ptable.getColumnAsDoubles(1);		
		y   = cd.ptable.getColumnAsDoubles(2);
		//channel
		channel   = cd.ptable.getColumnAsDoubles(3);
		//slice
		slices   = cd.ptable.getColumnAsDoubles(4);
		//frame number
		frames   = cd.ptable.getColumnAsDoubles(5);
		
		//box around detected particle
		xmin   = cd.ptable.getColumnAsDoubles(6);		
		ymin   = cd.ptable.getColumnAsDoubles(7);
		xmax   = cd.ptable.getColumnAsDoubles(8);		
		ymax   = cd.ptable.getColumnAsDoubles(9);
		dInt   = cd.ptable.getColumnAsDoubles(11);
		
		nPatNumber = x.length;
		
		if(!cddlg.bColocalization)
		{
						
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				if(cddlg.nRoiOption==0)
					spotROI = new OvalRoi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);
				else
					spotROI = new Roi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);

				spotROI.setStrokeColor(colorColoc);
				if(nStackSize==1)
					spotROI.setPosition(0);
				else
				{
					if(bIsHyperStack|| (imageinfo[2]>1))
					{
						spotROI.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
						imp.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
					}
					else
						spotROI.setPosition((int)absframe[nCount]);
				}
				spotROI.setName(String.format("d%d_ch%d_sl%d_fr%d", nCount+1,(int)channel[nCount],(int)slices[nCount],(int)frames[nCount]));
				SpotsPositions.add(spotROI);	
				if(cddlg.nRoiManagerAdd>0)
					roi_manager.addRoi(spotROI);
				
			}
		}
		else
		//analyzing colocalization
		{
			//total number of colocalization combinations
			
			nCombinationsN= cddlg.ChNumber*(cddlg.ChNumber-1)/2;
			
			int nCurrCombination;
			
			int nCurrCh1;
			int nCurrCh2;
						
			int nFrame;
			int nSlice;
			double dThreshold;

			double dDistance;
			double dDistanceCand;
			int nCandidate;
			
			/** particle (detection) number in Results Table
			 * **/
			double [] nPatN;
			/**array containing indexes of detections of colocalization
			 * **/
			double [][] nColocFriend;
			/**array marking if colocalization ROI was added to ROI manager
			 * **/
			boolean [][] nColocROIadded;
			
			/** Array containing ROIs of colocalized particles
			 * **/
			double [][][] dColocRoiXYArray;
			
			double [] dIntermediate1;
			double [] dIntermediate2;
			//double [][] spotsArr2;			
			int i,j;
			int nColocCount;
			dThreshold = cddlg.dColocDistance;
			
			ArrayList<double[]> spotsCh1 = new ArrayList<double[]>();
			ArrayList<double[]> spotsCh2 = new ArrayList<double[]>();
			
			double[] spotsChFolInt;

			
			colocalizations = new boolean [nCombinationsN][nPatNumber];
			//colocIntRatio = new double[nPatNumber];
			nColocFriend = new double[nCombinationsN][nPatNumber];
			nColocROIadded =new boolean[nCombinationsN][nPatNumber];
			dColocRoiXYArray = new double [nCombinationsN][nPatNumber][4];
			
			nPatN = new double[nPatNumber];
			cd.colocstat = new int [nCombinationsN][imageinfo[3]][imageinfo[4]];
			//intensities in both channels
			dIntMulti= new double[cddlg.ChNumber][nPatNumber];
			
			
			
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				nPatN[nCount]=nCount+1;
				for (i=0;i<nCombinationsN;i++)
					nColocROIadded[i][nCount]=false;
			}
			indexCombTable = new int [nCombinationsN][2];
			nCurrCombination=-1;
			for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber-1;nCurrCh1++)
				for(nCurrCh2=nCurrCh1+1;nCurrCh2<cddlg.ChNumber;nCurrCh2++)
				{
					nCurrCombination++;
					indexCombTable[nCurrCombination][0]=nCurrCh1;
					indexCombTable[nCurrCombination][1]=nCurrCh2;
					//colorsCh[nCurrCh1].getHSBColor(h, s, b)
					//colorsCh[nCurrCh1].RGBtoHSB(r, g, b, hsbvals)
					float[] hsbvals1 = new float [3];
					float[] hsbvals2 = new float [3];
					Color.RGBtoHSB(colorsCh[nCurrCh1].getRed(), colorsCh[nCurrCh1].getGreen(), colorsCh[nCurrCh1].getBlue(), hsbvals1);
					Color.RGBtoHSB(colorsCh[nCurrCh2].getRed(), colorsCh[nCurrCh2].getGreen(), colorsCh[nCurrCh2].getBlue(), hsbvals2);
					
					float h, s,b;
					h=(float)0.5*(hsbvals1[0]+hsbvals2[0]);
					s=(float)0.5*(hsbvals1[1]+hsbvals2[1]);
					b=(float)0.5*(hsbvals1[2]+hsbvals2[2]);
					colorExp = Color.getHSBColor(h,s,b);
					//colorExp = new Color(colorC.getRed(),colorC.getGreen(),colorC.getBlue());
					//int dR=(int)Math.sqrt(colorsCh[nCurrCh1].getRed()*colorsCh[nCurrCh1].getRed()+colorsCh[nCurrCh2].getRed()*colorsCh[nCurrCh2].getRed());
					//int dG=(int)Math.sqrt(colorsCh[nCurrCh1].getGreen()*colorsCh[nCurrCh1].getGreen()+colorsCh[nCurrCh2].getGreen()*colorsCh[nCurrCh2].getGreen());
					//int dB=(int)Math.sqrt(colorsCh[nCurrCh1].getBlue()*colorsCh[nCurrCh1].getBlue()+colorsCh[nCurrCh2].getBlue()*colorsCh[nCurrCh2].getBlue());

					//colorExp=colorsCh[nCurrCh1]+colorsCh[nCurrCh2];
					//colorExp = new Color(dR,dG,dB);
					
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
									if(channel[nCount]==nCurrCh1+1)
									{
										spotsCh1.add(new double [] {x[nCount],y[nCount], nCount, 0, xmin[nCount],xmax[nCount],ymin[nCount],ymax[nCount], dInt[nCount],nPatN[nCount]});
										dIntMulti[nCurrCh1][nCount]=dInt[nCount];
									}
									if(channel[nCount]==nCurrCh2+1)
									{
										spotsCh2.add(new double [] {x[nCount],y[nCount], nCount, 0, xmin[nCount],xmax[nCount],ymin[nCount],ymax[nCount], dInt[nCount],nPatN[nCount]});
										dIntMulti[nCurrCh2][nCount]=dInt[nCount];
									}
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
								
								}
								//found colocalization
								if(nCandidate>=0)
								{
									dIntermediate2 = spotsCh2.get(nCandidate);
									dIntermediate2[3] = 1;
									dIntermediate1[3] = 1;
									spotsCh2.set(nCandidate,dIntermediate2);
									//average x, y coordinames
									dIntermediate2[0] = 0.5*(dIntermediate2[0]+dIntermediate1[0]);
									dIntermediate2[1] = 0.5*(dIntermediate2[1]+dIntermediate1[1]);
									//mark as colocalized
									colocalizations[nCurrCombination][(int) dIntermediate2[2]]=true;
									colocalizations[nCurrCombination][(int) dIntermediate1[2]]=true;
									//calculate ratio
									//colocIntRatio[(int) dIntermediate1[2]]=dIntermediate1[8]/dIntermediate2[8];
									//colocIntRatio[(int) dIntermediate2[2]]=dIntermediate2[8]/dIntermediate1[8];
									//adjust roi as average
									double xminav=Math.min(dIntermediate1[4],dIntermediate2[4]);
									double xmaxav=Math.max(dIntermediate1[5],dIntermediate2[5]);
									double yminav=Math.min(dIntermediate1[6],dIntermediate2[6]);
									double ymaxav=Math.max(dIntermediate1[7],dIntermediate2[7]);
		
									//update box coordinates
									dIntermediate1[4]=xminav;dIntermediate2[4]=xminav;
									dIntermediate1[5]=xmaxav;dIntermediate2[5]=xmaxav;
									dIntermediate1[6]=yminav;dIntermediate2[6]=yminav;
									dIntermediate1[7]=ymaxav;dIntermediate2[7]=ymaxav;
									
									//store detection indexes
									nColocFriend[nCurrCombination][(int)dIntermediate1[2]]=dIntermediate2[9];
									nColocFriend[nCurrCombination][(int)dIntermediate2[2]]=dIntermediate1[9];
									
									
									//store ROI coordinates to add to ROI manager later
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate2[2]][0]=xminav;
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate2[2]][1]=yminav;
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate2[2]][2]=xmaxav;
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate2[2]][3]=ymaxav;
									
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate1[2]][0]=xminav;
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate1[2]][1]=yminav;
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate1[2]][2]=xmaxav;
									dColocRoiXYArray[nCurrCombination][(int)dIntermediate1[2]][3]=ymaxav;
									
									
									//first channel
									if(cddlg.nRoiOption==0)
										spotROI = new OvalRoi(xminav,yminav,xmaxav-xminav,ymaxav-yminav);
									else
										spotROI = new Roi(xminav,yminav,xmaxav-xminav,ymaxav-yminav);
									
									//spotROI.setStrokeColor(colorColoc);
									spotROI.setStrokeColor(colorExp);
									
									spotROI.setPosition(nCurrCh1+1,nSlice,nFrame);
									
									if(cddlg.nRoiManagerAdd>0)
									{
							
											spotROI.setName(String.format("d%d_sl%d_fr%d_ch1_%d_ch2_%d_c1", (int)dIntermediate1[9],nSlice,nFrame,nCurrCh1+1,nCurrCh2+1));																														
											imp.setPosition(nCurrCh1+1,nSlice,nFrame);
											roi_manager.addRoi(spotROI);
											nColocROIadded[nCurrCombination][(int)dIntermediate1[2]]=true;
									}
									SpotsPositions.add(spotROI);
									
									//second channel
									if(cddlg.nRoiOption==0)
										spotROI = new OvalRoi(xminav,yminav,xmaxav-xminav,ymaxav-yminav);
									else
										spotROI = new Roi(xminav,yminav,xmaxav-xminav,ymaxav-yminav);
									
									//spotROI.setStrokeColor(colorColoc);
									spotROI.setStrokeColor(colorExp);
									spotROI.setPosition(nCurrCh2+1,nSlice,nFrame);
									SpotsPositions.add(spotROI);
									
									nColocCount++;
									
								}
							}
						
							cd.colocstat[nCurrCombination][nSlice-1][nFrame-1] = nColocCount;
							
							
							//finding corresponding intensities in another channel
							correspChannelIntensitiesUpdate(spotsCh1, nCurrCh2+1,nCurrCh1+1, nFrame, nSlice);
							correspChannelIntensitiesUpdate(spotsCh2, nCurrCh1+1, nCurrCh2+1, nFrame, nSlice);
						}//for(nSlice=1;
						
						
						
					}//for(nFrame=1
				
				}//end of pairwise colocalization analysis

			//drawing the rest
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				boolean bPatColocalizedOnce = false;
				
				for (i=0;i<nCombinationsN;i++)
					if(colocalizations[i][nCount])
						bPatColocalizedOnce = true;
				
				if(!bPatColocalizedOnce)
				{
					if( !cddlg.bPlotMultiChannels)
					{
						if(cddlg.nRoiOption==0)
							spotROI = new OvalRoi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);
						else	
							spotROI = new Roi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);
						
						spotROI.setStrokeColor(colorsCh[(int)(channel[nCount])-1]);

						spotROI.setName(String.format("d%d_sl%d_fr%d_ch%d_c0", nCount+1,(int)slices[nCount],(int)frames[nCount],(int)channel[nCount]));
						SpotsPositions.add(spotROI);
						spotROI.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
						SpotsPositions.add(spotROI);
						if(cddlg.nRoiManagerAdd==1)
						{
							imp.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
							roi_manager.addRoi(spotROI);
						}
					}
					else
					{
						for (int k=1;k<=cddlg.ChNumber;k++)
						{
							if(cddlg.nRoiOption==0)

								spotROI = new OvalRoi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);
							else
								spotROI = new Roi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);
							
							spotROI.setStrokeColor(colorsCh[(int)(channel[nCount])-1]);
							spotROI.setName(String.format("d%d_sl%d_fr%d_ch%d_c0_%d", nCount+1,(int)slices[nCount],(int)frames[nCount],(int)channel[nCount],k));

							//spotROI.setName(String.format("d%d_ch%d_sl%d_fr%d_c0_%d", nCount+1,(int)channel[nCount],(int)slices[nCount],(int)frames[nCount],k));
							spotROI.setPosition(k,(int)slices[nCount],(int)frames[nCount]);
							SpotsPositions.add(spotROI);
							if(cddlg.nRoiManagerAdd==1 && k==channel[nCount])
							{
								imp.setPosition(k,(int)slices[nCount],(int)frames[nCount]);
								roi_manager.addRoi(spotROI);
							}

						}
						
					}
				}				
			}
			
			cd.ptable.deleteColumn("IntegratedInt");
			// make new columns
			for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber;nCurrCh1++)
				cd.ptable.setValue("IntegrIntCh"+Integer.toString(nCurrCh1+1), 0,0);
			for(nCurrCombination=0;nCurrCombination<nCombinationsN;nCurrCombination++)
			{
				nCurrCh1 = indexCombTable[nCurrCombination][0];
				nCurrCh2 = indexCombTable[nCurrCombination][1];
				String sCombination="_ch"+Integer.toString(nCurrCh1+1)+"&ch"+Integer.toString(nCurrCh2+1);
				cd.ptable.setValue("Colocalized"+sCombination, 0,0);

			}
			for(nCurrCombination=0;nCurrCombination<nCombinationsN;nCurrCombination++)
			{
				nCurrCh1 = indexCombTable[nCurrCombination][0];
				nCurrCh2 = indexCombTable[nCurrCombination][1];
				String sCombination="_ch"+Integer.toString(nCurrCh1+1)+"&ch"+Integer.toString(nCurrCh2+1);
				cd.ptable.setValue("ColocIndex"+sCombination, 0,0);

			}
			
			
			
			//adding info about colocalization to Results table
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				for(nCurrCombination=0;nCurrCombination<nCombinationsN;nCurrCombination++)
				{
					nCurrCh1 = indexCombTable[nCurrCombination][0];
					nCurrCh2 = indexCombTable[nCurrCombination][1];
					String sCombination="_ch"+Integer.toString(nCurrCh1+1)+"&ch"+Integer.toString(nCurrCh2+1);
					float[] hsbvals1 = new float [3];
					float[] hsbvals2 = new float [3];
					Color.RGBtoHSB(colorsCh[nCurrCh1].getRed(), colorsCh[nCurrCh1].getGreen(), colorsCh[nCurrCh1].getBlue(), hsbvals1);
					Color.RGBtoHSB(colorsCh[nCurrCh2].getRed(), colorsCh[nCurrCh2].getGreen(), colorsCh[nCurrCh2].getBlue(), hsbvals2);
					
					float h, s,b;
					h=(float)0.5*(hsbvals1[0]+hsbvals2[0]);
					s=(float)0.5*(hsbvals1[1]+hsbvals2[1]);
					b=(float)0.5*(hsbvals1[2]+hsbvals2[2]);
					colorExp = Color.getHSBColor(h,s,b);
					
					if(colocalizations[nCurrCombination][nCount])
					{
						
						cd.ptable.setValue("Colocalized"+sCombination, nCount,1);
						//cd.ptable.setValue("IntensityRatio", nCount,colocIntRatio[nCount]);
						cd.ptable.setValue("ColocIndex"+sCombination, nCount,nColocFriend[nCurrCombination][nCount]);
						//check if we already added ROI to ROI manager
						if(!nColocROIadded[nCurrCombination][nCount] && cddlg.nRoiManagerAdd>0)
						{
							if(cddlg.nRoiOption==0)
								spotROI = new OvalRoi(dColocRoiXYArray[nCurrCombination][nCount][0],dColocRoiXYArray[nCurrCombination][nCount][1],dColocRoiXYArray[nCurrCombination][nCount][2]-dColocRoiXYArray[nCurrCombination][nCount][0],dColocRoiXYArray[nCurrCombination][nCount][3]-dColocRoiXYArray[nCurrCombination][nCount][1]);
							else
								spotROI = new Roi(dColocRoiXYArray[nCurrCombination][nCount][0],dColocRoiXYArray[nCurrCombination][nCount][1],dColocRoiXYArray[nCurrCombination][nCount][2]-dColocRoiXYArray[nCurrCombination][nCount][0],dColocRoiXYArray[nCurrCombination][nCount][3]-dColocRoiXYArray[nCurrCombination][nCount][1]);

							//spotROI.setStrokeColor(colorColoc);
							spotROI.setStrokeColor(colorExp);
							spotROI.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
							spotROI.setName(String.format("d%d_ch%d_sl%d_fr%d_c1", nCount+1,(int)channel[nCount],(int)slices[nCount],(int)frames[nCount]));
							imp.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
							roi_manager.addRoi(spotROI);
						}
						cd.ptable.setValue("xMin", nCount, dColocRoiXYArray[nCurrCombination][nCount][0]);
						cd.ptable.setValue("yMin", nCount, dColocRoiXYArray[nCurrCombination][nCount][1]);
						cd.ptable.setValue("xMax", nCount, dColocRoiXYArray[nCurrCombination][nCount][2]);
						cd.ptable.setValue("yMax", nCount, dColocRoiXYArray[nCurrCombination][nCount][3]);
					}
					else
					{
						//cd.ptable.deleteColumn(column);
						cd.ptable.setValue("Colocalized"+sCombination, nCount,0);
						//cd.ptable.setValue("IntensityRatio", nCount,0);
						cd.ptable.setValue("ColocIndex"+sCombination, nCount,0);
					}
					cd.ptable.setValue("IntegrIntCh"+Integer.toString(nCurrCh1+1), nCount,dIntMulti[nCurrCh1][nCount]);
					cd.ptable.setValue("IntegrIntCh"+Integer.toString(nCurrCh2+1), nCount,dIntMulti[nCurrCh2][nCount]);
				}
			}
		}//if(!cddlg.bColocalization)
	
	}
	
	/**show summary and results tables**/
	void showTable()
	{

			int nChannel1Pat, nChannel2Pat, nColocNumber;
			int nCurrCombination;
			int nCurrCh1;
			int nCurrCh2;
			
			SummaryRT = new ResultsTable();
	        if(!cddlg.bColocalization)
	        {
	        	for(int i = 0;i<cd.nParticlesCount.length; i++)
	        	{
	        		nCurrPos = imp.convertIndexToPosition(i+1);
	        		SummaryRT.incrementCounter();
	        		SummaryRT.addValue("Channel", nCurrPos[0]);
	        		SummaryRT.addValue("Slice", nCurrPos[1]);
	        		SummaryRT.addValue("Frame", nCurrPos[2]);
	        		SummaryRT.addValue("Number_of_Particles", cd.nParticlesCount[i]);	        		

	        	}

	        }
	        else
	        {
	
	        	for(int i = 1;i<=imageinfo[3]; i++)//slice
	        	{
		        	for(int j = 1;j<=imageinfo[4]; j++)//frame
		        	{
		        		SummaryRT.incrementCounter();
		        		SummaryRT.addValue("Slice", i);
		        		SummaryRT.addValue("Frame", j);
		        		for (int k=1;k<=imageinfo[2]; k++)
		        		{
			        		nChannel1Pat = cd.nParticlesCount[imp.getStackIndex(k,i,j)-1];
			        		SummaryRT.addValue("Particles_in_Ch"+Integer.toString(k), nChannel1Pat);
		        		}
		        		
		        		for(nCurrCombination=0;nCurrCombination<nCombinationsN;nCurrCombination++)
						{
							nCurrCh1 = indexCombTable[nCurrCombination][0];
							nCurrCh2 = indexCombTable[nCurrCombination][1];
							String sCombination="_ch"+Integer.toString(nCurrCh1+1)+"&ch"+Integer.toString(nCurrCh2+1);
							
			        		nChannel1Pat = cd.nParticlesCount[imp.getStackIndex(nCurrCh1+1,i,j)-1];
			        		nChannel2Pat = cd.nParticlesCount[imp.getStackIndex(nCurrCh2+1,i,j)-1];
			        		nColocNumber = cd.colocstat[nCurrCombination][i-1][j-1];
			        	
			        		SummaryRT.addValue("Colocalized"+sCombination, nColocNumber);
			        		String sCombinationPercent = "%_Ch"+Integer.toString(nCurrCh1+1)+"_coloc"+sCombination;
			        		SummaryRT.addValue(sCombinationPercent, 100*(double)(nColocNumber)/(double)(nChannel1Pat));
			        		sCombinationPercent = "%_Ch"+Integer.toString(nCurrCh2+1)+"_coloc"+sCombination;
			        		SummaryRT.addValue(sCombinationPercent, 100*(double)(nColocNumber)/(double)(nChannel2Pat));
						}	         	
		        	} 
	        	}
	        	

	        }
	        SummaryRT.show("Summary");
			
	        //Show Results table with coordinates
	            
			cd.ptable.show("Results");

	}
	
	
	void correspChannelIntensitiesUpdate(ArrayList<double[]> spotsChRefX, int nFolChNum, int nChNext, int nFrame, int nSlice)	
	{
		//double[] spotsChFol = new double[spotsChRefX.size()];
		ImageProcessor ip;
		
		int i;

		double [] tempval;

		imp.setPositionWithoutUpdate(nFolChNum, nSlice, nFrame);

		ip = imp.getProcessor();
		for(i=0;i<spotsChRefX.size();i++)
		{
			tempval=spotsChRefX.get(i);
			dIntMulti[nFolChNum-1][(int)tempval[2]]=CDAnalysis.getIntIntNoise(ip, (int)tempval[4], (int)tempval[5], (int)tempval[6], (int)tempval[7]);
		}
		//	recalculating intensity for colocalized spots,
		// since ROI is updated
		imp.setPositionWithoutUpdate(nChNext, nSlice, nFrame);

		for(i=0;i<spotsChRefX.size();i++)
		{
			ip = imp.getProcessor();
			tempval=spotsChRefX.get(i);
			if(tempval[3]>0)
			{
				dIntMulti[nChNext-1][(int)tempval[2]]=CDAnalysis.getIntIntNoise(ip, (int)tempval[4], (int)tempval[5], (int)tempval[6], (int)tempval[7]);
			}
		}
		
		//return spotsChFol;		
	}
	
	
	void ComDetLog()
	{
		int i;
		
		//let's log stuff
		IJ.log(" --- ComDet plugin version " + ComDetConstants.ComDetVersion+ " --- ");
		IJ.log("Image title: \"" + imp.getTitle() + "\"");

		IJ.log("Number of channels: "+Integer.toString(cddlg.ChNumber));
		IJ.log("Detection parameters");
		for (i=0;i<cddlg.ChNumber;i++)
		{
			IJ.log("Channel: "+Integer.toString(i+1));
			IJ.log("Include larger particles: "+Boolean.toString(cddlg.bBigParticles[i]));
			IJ.log("Segment larger particles: "+Boolean.toString(cddlg.bSegmentLargeParticles[i]));
			IJ.log("Approximate particle size: "+Double.toString(cddlg.dPSFsigma[i]*2.0));
			IJ.log("Intensity threshold (in SD): "+Double.toString(cddlg.nSensitivity[i]));
		}
		if(cddlg.ChNumber>1)
		{
			IJ.log("Calculate colocalization: "+Boolean.toString(cddlg.bColocalization));
			if(cddlg.bColocalization)
				IJ.log("Max distance between colocalized spots: "+Double.toString(cddlg.dColocDistance));
				
			
		}
		
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
