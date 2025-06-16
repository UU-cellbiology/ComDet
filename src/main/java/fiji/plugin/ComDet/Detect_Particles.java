/*-
 * #%L
 * ComDet Plugin for ImageJ
 * %%
 * Copyright (C) 2012 - 2025 Cell Biology, Neurobiology and Biophysics
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


import java.awt.Color;
import java.util.ArrayList;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

public class Detect_Particles implements PlugIn 
{
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
	@Override
	public void run(String arg) 
	{
		
		int nFreeThread = -1;
		int nSlice = 0;
		boolean bContinue = true;
		int i;
		
		IJ.register(Detect_Particles.class);
	
		//checking whether there is any open images
		imp = IJ.getImage();
		
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
			//IJ.error("No particles found!");
			IJ.log("No particles found!");
			showTableNoParticles();
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
		absframe = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_absFrame);
		//coordinates
		x   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_X);		
		y   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Y);
		//channel
		channel   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Channel);
		//slice
		slices   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Slice);
		//frame number
		frames   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Frame);
		
		//box around detected particle
		xmin   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_xMin);		
		ymin   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_yMin);
		xmax   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_xMax);		
		ymax   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_yMax);
		dInt   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_dInt);
		
		nPatNumber = x.length;
		
		//no colocalization, let's just add detected particles
		if(!cddlg.bColocalization)
		{
						
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				if(cddlg.nRoiOption==0)
					spotROI = new OvalRoi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);
				else
					spotROI = new Roi(xmin[nCount],ymin[nCount],xmax[nCount]-xmin[nCount],ymax[nCount]-ymin[nCount]);

				spotROI.setStrokeColor(colorColoc);
				if(nStackSize==1 && imageinfo[2]==1)
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
				spotROI.setName(String.format("ind%d_ch%d_sl%d_fr%d", nCount+1,(int)channel[nCount],(int)slices[nCount],(int)frames[nCount]));
				SpotsPositions.add(spotROI);	
				if(cddlg.nRoiManagerAdd>0)
					roi_manager.addRoi(spotROI);
				
			}
		}
		//analyzing colocalization
		else		
		{
			//total number of colocalization combinations			
			nCombinationsN = cddlg.ChNumber*(cddlg.ChNumber-1)/2;
			
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
			
			ArrayList<double[]> spotsCh1 = new ArrayList<>();
			ArrayList<double[]> spotsCh2 = new ArrayList<>();
			
			//double[] spotsChFolInt;

			
			colocalizations = new boolean [nCombinationsN][nPatNumber];
			//colocIntRatio = new double[nPatNumber];
			nColocFriend = new double[nCombinationsN][nPatNumber];
			nColocROIadded =new boolean[nCombinationsN][nPatNumber];
			dColocRoiXYArray = new double [nCombinationsN][nPatNumber][4];
			
			nPatN = new double[nPatNumber];
			cd.colocstat = new int [nCombinationsN][imageinfo[3]][imageinfo[4]];
			//intensities in both channels
			dIntMulti = new double[cddlg.ChNumber][nPatNumber];
			
			
			
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				nPatN[nCount]=nCount+1;
				for (i=0;i<nCombinationsN;i++)
					nColocROIadded[i][nCount]=false;
			}
			indexCombTable = new int [nCombinationsN][2];

			nCurrCombination = -1;

			//colocalizing each channel with each channel
			for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber-1;nCurrCh1++)
				for(nCurrCh2=nCurrCh1+1;nCurrCh2<cddlg.ChNumber;nCurrCh2++)
				{
					nCurrCombination++;
					indexCombTable[nCurrCombination][0] = nCurrCh1;
					indexCombTable[nCurrCombination][1] = nCurrCh2;

					//filling up arrays with detections from current image (slice, frame)
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
								nCandidate = -1;
								dDistanceCand = 2.0*dThreshold;
								dIntermediate1 = spotsCh1.get(i);
								
								//finding minimal distance below the threshold
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
									//average x, y coordinates
									dIntermediate2[0] = 0.5*(dIntermediate2[0]+dIntermediate1[0]);
									dIntermediate2[1] = 0.5*(dIntermediate2[1]+dIntermediate1[1]);
									
									final int [] indices = new int[2];
									indices[0] = (int) dIntermediate1[2];
									indices[1] = (int) dIntermediate2[2];
									//mark as colocalized
									colocalizations[nCurrCombination][indices[0]] = true;
									colocalizations[nCurrCombination][indices[1]] = true;


									//make a roi that includes everything
									if(cddlg.bJoinROIs)
									{	
										final double xminav = Math.min(dIntermediate1[4],dIntermediate2[4]);
										final double xmaxav = Math.max(dIntermediate1[5],dIntermediate2[5]);
										final double yminav = Math.min(dIntermediate1[6],dIntermediate2[6]);
										final double ymaxav = Math.max(dIntermediate1[7],dIntermediate2[7]);
			
										//update box coordinates
										dIntermediate1[4] = xminav; dIntermediate2[4] = xminav;
										dIntermediate1[5] = xmaxav; dIntermediate2[5] = xmaxav;
										dIntermediate1[6] = yminav; dIntermediate2[6] = yminav;
										dIntermediate1[7] = ymaxav; dIntermediate2[7] = ymaxav;
										
										//store ROI coordinates to add to ROI manager later	
										for(int k=0;k<2;k++)
										{
											dColocRoiXYArray[nCurrCombination][indices[k]][0]=xminav;
											dColocRoiXYArray[nCurrCombination][indices[k]][1]=yminav;
											dColocRoiXYArray[nCurrCombination][indices[k]][2]=xmaxav;
											dColocRoiXYArray[nCurrCombination][indices[k]][3]=ymaxav;											
										}
									}
									else
									{
										//store ROI coordinates to add to ROI manager later
										dColocRoiXYArray[nCurrCombination][indices[0]][0] = dIntermediate1[4];
										dColocRoiXYArray[nCurrCombination][indices[0]][1] = dIntermediate1[6];
										dColocRoiXYArray[nCurrCombination][indices[0]][2] = dIntermediate1[5];
										dColocRoiXYArray[nCurrCombination][indices[0]][3] = dIntermediate1[7];

										dColocRoiXYArray[nCurrCombination][indices[1]][0] = dIntermediate2[4];
										dColocRoiXYArray[nCurrCombination][indices[1]][1] = dIntermediate2[6];
										dColocRoiXYArray[nCurrCombination][indices[1]][2] = dIntermediate2[5];
										dColocRoiXYArray[nCurrCombination][indices[1]][3] = dIntermediate2[7];										
									}
									//store detection indexes
									nColocFriend[nCurrCombination][indices[0]] = dIntermediate2[9];
									nColocFriend[nCurrCombination][indices[1]] = dIntermediate1[9];
								
									nColocCount++;
									
								}
							}
						
							cd.colocstat[nCurrCombination][nSlice-1][nFrame-1] = nColocCount;							
							
							//finding/updating corresponding intensities in another channel
							correspChannelIntensitiesUpdate(spotsCh1, nCurrCh2+1, nCurrCh1+1, nFrame, nSlice);
							correspChannelIntensitiesUpdate(spotsCh2, nCurrCh1+1, nCurrCh2+1, nFrame, nSlice);
						}//for(nSlice=1;												
						
					}//for(nFrame=1
				
				}//end of pairwise colocalization analysis
				//here all channels combinations are done

			
			cd.ptable.deleteColumn("IntegratedInt");
			// make new columns
			for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber;nCurrCh1++)
			{
				cd.ptable.setValue("IntegrIntCh"+Integer.toString(nCurrCh1+1), 0,0);
			}

			for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber;nCurrCh1++)
			{
				cd.ptable.setValue("ColocCh"+Integer.toString(nCurrCh1+1), 0,0);
			}
			for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber;nCurrCh1++)
			{
				cd.ptable.setValue("ColocIndCh"+Integer.toString(nCurrCh1+1), 0,0);
			}
			
			
			//adding info about colocalization to Results table		
			for(nCount = 0; nCount<nPatNumber; nCount++)
			{
				int nChPresense[] = new int[cddlg.ChNumber];
				nChPresense[(int)(channel[nCount]-1)]=1;
				double xminROI = xmin[nCount];
				double yminROI = ymin[nCount];
				double xmaxROI = xmax[nCount];
				double ymaxROI = ymax[nCount];
				cd.ptable.setValue("ColocCh"+Integer.toString((int)channel[nCount]), nCount,1);
				cd.ptable.setValue("ColocIndCh"+Integer.toString((int)channel[nCount]), nCount,Integer.toString(nCount+1));
				
				for(nCurrCombination=0;nCurrCombination<nCombinationsN;nCurrCombination++)
				{
					nCurrCh1 = indexCombTable[nCurrCombination][0];
					nCurrCh2 = indexCombTable[nCurrCombination][1];
					
					if(colocalizations[nCurrCombination][nCount])
					{
						cd.ptable.setValue("ColocCh"+Integer.toString(nCurrCh1+1), nCount,1);
						cd.ptable.setValue("ColocCh"+Integer.toString(nCurrCh2+1), nCount,1);
						if((int)channel[nCount] == nCurrCh1+1)
						{
							nChPresense[nCurrCh2]=1;
							cd.ptable.setValue("ColocIndCh"+Integer.toString(nCurrCh2+1),nCount,nColocFriend[nCurrCombination][nCount]);
						}
						else
						{
							nChPresense[nCurrCh1]=1;
							cd.ptable.setValue("ColocIndCh"+Integer.toString(nCurrCh1+1),nCount,nColocFriend[nCurrCombination][nCount]);
						}

						if(xminROI>dColocRoiXYArray[nCurrCombination][nCount][0])
							xminROI=dColocRoiXYArray[nCurrCombination][nCount][0];
						if(yminROI>dColocRoiXYArray[nCurrCombination][nCount][1])
							yminROI=dColocRoiXYArray[nCurrCombination][nCount][1];
						if(xmaxROI<dColocRoiXYArray[nCurrCombination][nCount][2])
							xmaxROI=dColocRoiXYArray[nCurrCombination][nCount][2];
						if(ymaxROI<dColocRoiXYArray[nCurrCombination][nCount][3])
							ymaxROI=dColocRoiXYArray[nCurrCombination][nCount][3];

					}
					cd.ptable.setValue("IntegrIntCh"+Integer.toString(nCurrCh1+1), nCount,dIntMulti[nCurrCh1][nCount]);
					cd.ptable.setValue("IntegrIntCh"+Integer.toString(nCurrCh2+1), nCount,dIntMulti[nCurrCh2][nCount]);
				}
				cd.ptable.setValue("xMin", nCount, xminROI);
				cd.ptable.setValue("yMin", nCount, yminROI);
				cd.ptable.setValue("xMax", nCount, xmaxROI);
				cd.ptable.setValue("yMax", nCount, ymaxROI);
				cd.ptable.setValue("Index", nCount,nCount+1);
				
				//add ROI to the image				
				int colTot[] = new int[3];
				int nColocN=0;
				
				for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber;nCurrCh1++)
				{
					if (nChPresense[nCurrCh1]>0)
					{
						colTot[0]+=colorsCh[nCurrCh1].getRed();
						colTot[1]+=colorsCh[nCurrCh1].getGreen();
						colTot[2]+=colorsCh[nCurrCh1].getBlue();
						nColocN++;
					}					
				}
				for(nCurrCh1=0;nCurrCh1<3;nCurrCh1++)
					if(colTot[nCurrCh1]>255)
						colTot[nCurrCh1]=255;
				colorExp = new Color(colTot[0],colTot[1],colTot[2]);
								
				
				if(cddlg.nRoiOption==0)
					spotROI = new OvalRoi(xminROI,yminROI,xmaxROI-xminROI,ymaxROI-yminROI);
				else
					spotROI = new Roi(xminROI,yminROI,xmaxROI-xminROI,ymaxROI-yminROI);
				spotROI.setStrokeColor(colorExp);
				spotROI.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
				//spotROI.setName(String.format("d%d_ch%d_sl%d_fr%d_c1", nCount+1,(int)channel[nCount],(int)slices[nCount],(int)frames[nCount]));
				
				if(cddlg.nRoiManagerAdd>0)
				{
					///adding only colocalized particles
					if(cddlg.nRoiManagerAdd==2 && nColocN >1 )
					{						
						spotROI.setName(String.format("ind%d_sl%d_fr%d_ch%d", nCount+1,(int)slices[nCount],(int)frames[nCount],(int)channel[nCount]));
						imp.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
						roi_manager.addRoi(spotROI);
					}
					///adding only non-colocalized particles
					if( cddlg.nRoiManagerAdd==3 && nColocN ==1 )
					{						
						spotROI.setName(String.format("ind%d_sl%d_fr%d_ch%d", nCount+1,(int)slices[nCount],(int)frames[nCount],(int)channel[nCount]));
						imp.setPosition((int)channel[nCount],(int)slices[nCount],(int)frames[nCount]);
						roi_manager.addRoi(spotROI);
					}
				}
				if(cddlg.bPlotMultiChannels)
				{
					for(nCurrCh1=0;nCurrCh1<cddlg.ChNumber;nCurrCh1++)
					{
						if(cddlg.nRoiOption==0)
							spotROI = new OvalRoi(xminROI,yminROI,xmaxROI-xminROI,ymaxROI-yminROI);
						else
							spotROI = new Roi(xminROI,yminROI,xmaxROI-xminROI,ymaxROI-yminROI);
						spotROI.setStrokeColor(colorExp);
						spotROI.setPosition(nCurrCh1+1,(int)slices[nCount],(int)frames[nCount]);
						SpotsPositions.add(spotROI);
					}
					
				}
				else
				{
					SpotsPositions.add(spotROI);
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
			boolean bSwitch=false;
			ResultsTable xTemp = null;
			double [] channel;
			double [] slices;
			double [] frames;
			double [][] colocInfo;
			
			if(WindowManager.getWindow("Summary")==null || cddlg.nSummaryOptions==0)
				SummaryRT = new ResultsTable();
			else
			{
				xTemp=Analyzer.getResultsTable();
				//IJ.renameResults("Temp");
				IJ.renameResults("Summary", "Results");
				SummaryRT = Analyzer.getResultsTable();
				bSwitch = true;
			}
			//no need to generate colocalization info
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
	        	//colocalization info
	        	//channel
	    		channel   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Channel);
	    		//slice
	    		slices   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Slice);
	    		//frame number
	    		frames   = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_Frame);
	    		
	    		colocInfo = new double [cddlg.ChNumber][frames.length];
	    		for (int nCh=0; nCh<cddlg.ChNumber;nCh++)
	    		{
	    			colocInfo[nCh] = cd.ptable.getColumnAsDoubles(ComDetConstants.Col_dInt+nCh+cddlg.ChNumber+1);
	    		}
	
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
		        		//pairwise combinations
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
			        		SummaryRT.addValue(sCombinationPercent, 100*(double)(nColocNumber)/(nChannel1Pat));
			        		sCombinationPercent = "%_Ch"+Integer.toString(nCurrCh2+1)+"_coloc"+sCombination;
			        		SummaryRT.addValue(sCombinationPercent, 100*(double)(nColocNumber)/(nChannel2Pat));
						}	         	
		        		//special case: all channels colocalized
		        		if(cddlg.ChNumber>2)
		        		{
		        			int nAllColoc=0;
		        			for (int k=0;k<channel.length;k++)
		        			{
		        				//good position
		        				if(channel[k]==1 && slices[k]==i && frames[k]==j)
		        				{
		        					int nChColoc=0;
		        					for (int nCh=0; nCh<cddlg.ChNumber;nCh++)
		        		    		{
		        						
		        						nChColoc+=colocInfo[nCh][k];
		        		    		}
		        					//present in all channels
		        					if (nChColoc==cddlg.ChNumber)
		        						nAllColoc++;
		        						
		        				}
		        			}
		        			//string name
		        			String sCombination="_ch1";
		        			for (int nCh=2; nCh<=cddlg.ChNumber;nCh++)
        		    		{
		        				sCombination=sCombination+"&ch"+Integer.toString(nCh);
        		    		}
		        			SummaryRT.addValue("Colocalized" + sCombination,nAllColoc);
		        			for (int nCh=1; nCh<=cddlg.ChNumber;nCh++)
        		    		{
		        				String sCombinationPercent = "%_Ch"+Integer.toString(nCh)+"_coloc"+sCombination;
		        				SummaryRT.addValue(sCombinationPercent, 100*(double)(nAllColoc)/(cd.nParticlesCount[imp.getStackIndex(nCh,i,j)-1]));
        		    		}
		        		}
		        		
		        	} 
	        	}
	        	

	        }	       
			
	        //Show Results table with coordinates
	  
	       	if(bSwitch)
	       	{
	       		SummaryRT.show("Results");
	        	IJ.renameResults("Summary");
	       		//IJ.renameResults("Temp", "Results")
	        	cd.ptable=xTemp;
	        }
	       	else
	       	{
	       		SummaryRT.show("Summary");
	       	}
	        cd.ptable.show("Results");	       

	}
	/** show Summary and Results tables in case there are no particles **/
	void showTableNoParticles()
	{
	
		
		if(WindowManager.getWindow("Summary")==null || cddlg.nSummaryOptions==0)
			{SummaryRT = new ResultsTable();}
		else
		{
			//xTemp=Analyzer.getResultsTable();
			//IJ.renameResults("Temp");
			IJ.renameResults("Summary", "Results");
			SummaryRT = Analyzer.getResultsTable();
			//bSwitch = true;
		}

    	for(int i = 1;i<=imageinfo[3]; i++)//slice
    	{
        	for(int j = 1;j<=imageinfo[4]; j++)//frame
        	{
        		SummaryRT.incrementCounter();
        		SummaryRT.addValue("Slice", i);
        		SummaryRT.addValue("Frame", j);
        		for (int k=1;k<=imageinfo[2]; k++)
        		{
        			SummaryRT.addValue("Particles_in_Ch"+Integer.toString(k), 0);
        			
        		}
        	}
    	}

    	SummaryRT.show("Results");
    	IJ.renameResults("Summary");

    	cd.ptable.show("Results");

	}
	
	
	void correspChannelIntensitiesUpdate(ArrayList<double[]> spotsChRefX, int nFolChNum, int nChNext, int nFrame, int nSlice)	
	{
		//double[] spotsChFol = new double[spotsChRefX.size()];
		ImageProcessor ip_;
		
		int i;

		double [] tempval;

		imp.setPositionWithoutUpdate(nFolChNum, nSlice, nFrame);

		ip_ = imp.getProcessor();
		for(i=0;i<spotsChRefX.size();i++)
		{
			tempval=spotsChRefX.get(i);
			dIntMulti[nFolChNum-1][(int)tempval[2]]=CDAnalysis.getIntIntNoise(ip_, (int)tempval[4], (int)tempval[5], (int)tempval[6], (int)tempval[7]);
		}
		//	recalculating intensity for colocalized spots,
		// since ROI is updated
		imp.setPositionWithoutUpdate(nChNext, nSlice, nFrame);

		for(i=0;i<spotsChRefX.size();i++)
		{
			ip_ = imp.getProcessor();
			tempval=spotsChRefX.get(i);
			if(tempval[3]>0)
			{
				dIntMulti[nChNext-1][(int)tempval[2]]=CDAnalysis.getIntIntNoise(ip_, (int)tempval[4], (int)tempval[5], (int)tempval[6], (int)tempval[7]);
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

		IJ.log("Number of channels: " + Integer.toString(cddlg.ChNumber));
		IJ.log("Detection parameters");
		for (i=0;i<cddlg.ChNumber;i++)
		{
			IJ.log("Channel: "+Integer.toString(i+1));
			IJ.log("Include larger particles: " + Boolean.toString(cddlg.bBigParticles[i]));
			IJ.log("Segment larger particles: " + Boolean.toString(cddlg.bSegmentLargeParticles[i]));
			IJ.log("Approximate particle size: " + Double.toString(cddlg.dPSFsigma[i]*2.0));
			IJ.log("Intensity threshold (in SD): " + Double.toString(cddlg.nSensitivity[i]));
		}
		if(cddlg.ChNumber>1)
		{
			IJ.log("Calculate colocalization: "+ Boolean.toString(cddlg.bColocalization));
			if(cddlg.bColocalization)
			{
				IJ.log("Max distance between colocalized spots: " + Double.toString(cddlg.dColocDistance));
				IJ.log("Join ROIs for intensity of colocalized particles: " + Boolean.toString(cddlg.bJoinROIs));
			}	
			
		}
		
	}
	
	public static void main(String... args) throws Exception
	{
		
		new ImageJ();
		Detect_Particles dp = new Detect_Particles();
		IJ.open("/home/eugene/Desktop/Composite-1.tif");
		dp.run( "" );

	}

}

class CDThread extends Thread 
{

	private ImageProcessor ip;
	private CDDialog cddlg;
	private int [] nImagePos;
	private int nSlice;
	private CDAnalysis cd;
	private Roi roiActive;
	private CDProgressCount cdcount;
	private int nStackSize;
	
	public void cdsetup(final ImageProcessor ip_, final CDAnalysis cd_, final CDDialog cddlg_, final int [] nImagePos_, final int nSlice_, final Roi roiActive_, final int nStackSize_, final CDProgressCount cdcount_)
	{
		this.cd     = cd_;
		this.ip     = ip_;
		this.cddlg  = cddlg_;
		this.nImagePos = nImagePos_;
		this.nSlice = nSlice_;
		this.roiActive = roiActive_;
		this.cdcount = cdcount_;
		this.nStackSize = nStackSize_;		
	}
	
	@Override
	public void run()
	{
		this.cd.detectParticles(this.ip, this.cddlg, this.nImagePos, this.nSlice, this.roiActive);
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
