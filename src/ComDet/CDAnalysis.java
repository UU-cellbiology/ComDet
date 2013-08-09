package ComDet;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Convolver;
import ij.plugin.filter.GaussianBlur;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.TypeConverter;
import jaolho.data.lma.LMA;

import java.util.ArrayList;
import java.util.Stack;

import ComDet.CDDialog;
import ComDet.OneDGaussian;


public class CDAnalysis {
	
	ImageStatistics imgstat;
	GaussianBlur lowpassGauss = new GaussianBlur(); //low pass prefiltering
	Convolver      colvolveOp = new Convolver(); //convolution filter
	float []		 fConKernel;
	float []		 fConKernelTwo; //for the second channel  
	int [] nParticlesCount;
	int [][] colocstat;
	//int nTrue;
	//int nFalse;
	//ResultsTable ptable = new ResultsTable(); // table with particle's approximate center coordinates
	public ResultsTable ptable = Analyzer.getResultsTable(); // table with particle's approximate center coordinates	
	java.util.concurrent.locks.Lock ptable_lock = new java.util.concurrent.locks.ReentrantLock();
	
	
	//default constructor 
	public CDAnalysis()
	{
		ptable.setPrecision(5);
		//SummaryTable = new TextWindow("Summary", "Frame Number\tNumber of Particles", "123\t123321", 450, 300);
		//SummaryTable.setColumnHeadings("Frame Number\tNumber of Particles");
		//SummaryTable.append();
		//nTrue=0;
		//nFalse=0;
	}
	
	
	
	// Particle finding routine based on spots enhancement with
	// 2D PSF Gaussian approximated convolution/backgrounds subtraction, thresholding
	// and particle filtering
	void detectParticles(ImageProcessor ip, CDDialog fdg, int[] nImagePos, int nSlice, Roi RoiActive_)
	{
		int nThreshold;
		int [] nNoise;
		int chIndex;
		
		FloatProcessor dupip = null ; //duplicate of image
		ImageProcessor dushort; //duplicate of image
		ImageProcessor duconvolved = null; //duplicate of image		
		ByteProcessor dubyte = null; //tresholded image
		TypeConverter tc; 
		
		if(fdg.bTwoChannels)
			chIndex = nImagePos[0]-1;
		else
			chIndex = 0;
		
		dupip = (FloatProcessor) ip.duplicate().convertToFloat();
		
		//low-pass filtering		
		SMLblur1Direction(dupip, fdg.dPSFsigma[chIndex]*0.5, 0.0002, true, (int)Math.ceil(5*fdg.dPSFsigma[chIndex]*0.5));
		SMLblur1Direction(dupip, fdg.dPSFsigma[chIndex]*0.5, 0.0002, false, 0);

		//convolution with gaussian PSF kernel		
		if((fdg.bTwoChannels) && nImagePos[0]==2)
			SMLconvolveFloat(dupip, fConKernelTwo, fdg.nKernelSize[chIndex], fdg.nKernelSize[chIndex]);
		else
			SMLconvolveFloat(dupip, fConKernel, fdg.nKernelSize[chIndex], fdg.nKernelSize[chIndex]);
		//new ImagePlus("convoluted", dupip.duplicate()).show();
		tc = new TypeConverter(dupip, true);
		dushort =  tc.convertToShort();
		//new ImagePlus("iplowpass", iplowpass.duplicate()).show();				
			
		//making a copy of convoluted image
		duconvolved = dushort.duplicate();

		nNoise  = getThreshold(dushort, fdg.nSensitivity[chIndex]);		
		nThreshold = nNoise[0] +  fdg.nSensitivity[chIndex]*nNoise[1];
		//new ImagePlus("convoluted", dushort.duplicate()).show();
		
		
		//thresholding
		dushort.threshold(nThreshold);
		//convert to byte
		dubyte  = (ByteProcessor) dushort.convertToByte(false);
		
		dubyte.dilate();		
		
		dubyte.erode();
		
		//new ImagePlus("byte", dubyte.duplicate()).show();
			
		//labelParticles(dubyte, ip, fdg, nFrame, SpotsPositions_, RoiActive_,nNoise[1]);
		labelParticles(dubyte, duconvolved, ip, fdg, nImagePos, nSlice, RoiActive_);
		
	}
	
	//function that finds centroid x,y and area
	//of spots after thresholding
	//based on connected components	labeling Java code
	//implemented by Mariusz Jankowski & Jens-Peer Kuska
	//and in March 2012 available by link
    //http://www.izbi.uni-leipzig.de/izbi/publikationen/publi_2004/IMS2004_JankowskiKuska.pdf
	
	void labelParticles(ImageProcessor ipBinary,  ImageProcessor ipConvol, ImageProcessor ipRaw,  CDDialog fdg, int [] nImagePos, int nFrame, Roi RoiAct)//, boolean bIgnore)//, double dSymmetry_)
	{
		//double dPSFsigma = fdg.dPSFsigma;
		int width = ipBinary.getWidth();
		int height = ipBinary.getHeight();
		 
		int nArea;
		int chIndex;
		
		int i,j;
				
		double dVal, dInt;
		double dIMax;

		double xCentroid, yCentroid;

		boolean bBorder;
		boolean bInRoi;

		int nPatCount = 0;
		int lab = 1;
		int [] pos;
		
		Stack<int[]> sstack = new Stack<int[]>( );
		ArrayList<int[]> stackPost = new ArrayList<int[]>( );
		int [][] label = new int[width][height] ;
		
		
		if(fdg.bTwoChannels)
			chIndex = nImagePos[0]-1;
		else
			chIndex = 0;
		
		
		int [] nMaxPos;
		int RoiRad = (int) Math.ceil(2.5*fdg.dPSFsigma[chIndex]);
		int nMaxInd, nMaxIntensity;
		int nLocalThreshold;
		int nListLength;
					
		
		for (int r = 1; r < width-1; r++)
			for (int c = 1; c < height-1; c++) {
				
				if (ipBinary.getPixel(r,c) == 0.0) continue ;
				if (label[r][c] > 0.0) continue ;
				/* encountered unlabeled foreground pixel at position r, c */
				/* it means it is a new spot! */
				/* push the position in the stack and assign label */
				sstack.push(new int [] {r, c}) ;
				stackPost.clear();
				stackPost.add(new int [] {r, c, ipRaw.getPixel(r,c)}) ;
				label[r][c] = lab ;
				nArea = 0;
				dIMax = -1000;
				xCentroid = 0; yCentroid = 0;
				//xMax = 0; yMax = 0;
				dInt = 0;
				bBorder = false;

				/* start the float fill */
				while ( !sstack.isEmpty()) 
				{
					pos = (int[]) sstack.pop() ;
					i = pos[0]; j = pos[1];
					
					//remove all spots at border
					if(i==0 || j==0 || i==(width-1) || j==(height-1))
						bBorder = true;
					nArea ++;
					dVal = ipRaw.getPixel(i,j);
					if (dVal > dIMax)
						dIMax = dVal;
					dInt += dVal;
					xCentroid += dVal*i;
					yCentroid += dVal*j;
					
					
					
					if (ipBinary.getPixel(i-1,j-1) > 0 && label[i-1][j-1] == 0) {
						sstack.push( new int[] {i-1,j-1} );
						stackPost.add( new int[] {i-1,j-1,ipConvol.getPixel(i-1,j-1)} );
						label[i-1][j-1] = lab ;
					}
					
					if (ipBinary.getPixel(i-1,j) > 0 && label[i-1][j] == 0) {
						sstack.push( new int[] {i-1,j} );
						stackPost.add( new int[] {i-1,j,ipConvol.getPixel(i-1,j)} );
						label[i-1][j] = lab ;
					}
					
					if (ipBinary.getPixel(i-1,j+1) > 0 && label[i-1][j+1] == 0) {
						sstack.push( new int[] {i-1,j+1,} );
						stackPost.add( new int[] {i-1,j+1,ipConvol.getPixel(i-1,j+1)} );
						label[i-1][j+1] = lab ;
					}
					
					if (ipBinary.getPixel(i,j-1) > 0 && label[i][j-1] == 0) {
						sstack.push( new int[] {i,j-1} );
						stackPost.add( new int[] {i,j-1,ipConvol.getPixel(i,j-1)} );
						label[i][j-1] = lab ;
					}
					
					if (ipBinary.getPixel(i,j+1) > 0 && label[i][j+1] == 0) {
						sstack.push( new int[] {i,j+1} );
						stackPost.add( new int[] {i,j+1,ipConvol.getPixel(i,j+1)} );
						label[i][j+1] = lab ;
					}
					if (ipBinary.getPixel(i+1,j-1) > 0 && label[i+1][j-1] == 0) {
						sstack.push( new int[] {i+1,j-1} );
						stackPost.add( new int[] {i+1,j-1,ipConvol.getPixel(i+1,j-1)} );
						label[i+1][j-1] = lab ;
					}
				
					if (ipBinary.getPixel(i+1,j)>0 && label[i+1][j] == 0) {
						sstack.push( new int[] {i+1,j} );
						stackPost.add( new int[] {i+1,j,ipConvol.getPixel(i+1,j)} );
						label[i+1][j] = lab ;
					}
					
					if (ipBinary.getPixel(i+1,j+1) > 0 && label[i+1][j+1] == 0) {
						sstack.push( new int[] {i+1,j+1} );
						stackPost.add( new int[] {i+1,j+1,ipConvol.getPixel(i+1,j+1)} );
						label[i+1][j+1] = lab ;
					}
					
				} /* end while */
				
				//case of single particle in the thresholded area
				if(!bBorder && nArea > fdg.nAreaCut[chIndex] && nArea < fdg.nAreaMax[chIndex])				
				{					
					xCentroid /= dInt;
					yCentroid /= dInt;

						if ( (xCentroid>0) && (yCentroid>0) && (xCentroid<(width-1)) && (yCentroid<(height-1)) )
						{
							 bInRoi = true;
							 if(RoiAct!=null)
							 {
								 if(!RoiAct.contains((int)xCentroid, (int)yCentroid))
									 bInRoi=false;
							 }
							 if(bInRoi)
							 {
						
							
									ptable_lock.lock();
									ptable.incrementCounter();									
									ptable.addValue("Abs_frame", nFrame+1);
									ptable.addValue("X_(px)",xCentroid);	
									ptable.addValue("Y_(px)",yCentroid);
									//ptable.addValue("Frame_Number", nFrame+1);
									ptable.addValue("Channel", nImagePos[0]);
									ptable.addValue("Slice", nImagePos[1]);
									ptable.addValue("Frame", nImagePos[2]);									
									//ptable.addValue("NArea", nArea);
									
									ptable_lock.unlock();
									//adding spot to the overlay
									/*spotROI = new OvalRoi((int)(0.5+xCentroid-2*dPSFsigma),(int)(0.5+yCentroid-2*dPSFsigma),(int)(4.0*dPSFsigma),(int)(4.0*dPSFsigma));
									spotROI.setStrokeColor(Color.yellow);	
									spotROI.setPosition(nFrame+1);
									SpotsPositions__.add(spotROI);*/
									nPatCount++;

							 }

					}
				
				}
				////probably many particles in the thresholded area				
				if(nArea >= fdg.nAreaMax[chIndex])
				{
					
					while ( !stackPost.isEmpty()) 
					{
						//find element with max intensity
						nMaxInd = getIndexofMaxIntensity(stackPost);
						nMaxPos = stackPost.get(nMaxInd);
						xCentroid = nMaxPos[0];
						yCentroid = nMaxPos[1];
						nMaxIntensity = nMaxPos[2]; 
						//check whether it is inside ROI
						bInRoi = true;
						if(RoiAct!=null)
						{
							if(!RoiAct.contains((int)xCentroid, (int)yCentroid))
								bInRoi=false;
						}
						//yes, inside
						if(bInRoi)
						{
							//check whether it is above the threshold level
							if(xCentroid>RoiRad+1 && yCentroid>RoiRad+1 && xCentroid< width-2-RoiRad && yCentroid< height-2-RoiRad)
							{
								nLocalThreshold = getLocalThreshold(ipConvol,(int)xCentroid,(int)yCentroid, RoiRad, fdg.nSensitivity[chIndex]);								
								if(nLocalThreshold>nMaxIntensity)
									bInRoi = false;
							}
							else
								bInRoi = false;
							//all checks passed
							if(bInRoi)
							{
								ptable_lock.lock();
								ptable.incrementCounter();
								ptable.addValue("Abs_frame", nFrame+1);
								ptable.addValue("X_(px)",xCentroid);	
								ptable.addValue("Y_(px)",yCentroid);
								//ptable.addValue("Frame_Number", nFrame+1);
								//ptable.addValue("NArea", nArea);
								ptable.addValue("Channel", nImagePos[0]);
								ptable.addValue("Slice", nImagePos[1]);
								ptable.addValue("Frame", nImagePos[2]);
								//ptable.addValue("Abs_count", nFrame+1);
								
								ptable_lock.unlock();
								//adding spot to the overlay
								/*spotROI = new OvalRoi((int)(0.5+nMaxPos[0]-2*dPSFsigma),(int)(0.5+nMaxPos[1]-2*dPSFsigma),(int)(4.0*dPSFsigma),(int)(4.0*dPSFsigma));
								spotROI.setStrokeColor(Color.yellow);	
								spotROI.setPosition(nFrame+1);
								SpotsPositions__.add(spotROI);*/
								nPatCount++;				
							}

							
						}
						//remove maximum and its surrounding from the list
						nListLength = stackPost.size();
						i=0;
						while(nListLength>0 && i<nListLength)
						{
							nMaxPos =stackPost.get(i);
							if(nMaxPos[0]>=xCentroid-RoiRad && nMaxPos[0]<=xCentroid+RoiRad && nMaxPos[1]>=yCentroid-RoiRad && nMaxPos[1]<=yCentroid+RoiRad)
							{
								stackPost.remove(i);
								nListLength--;
							}
							else
							{
								i++;
							}
							
						}
						
					
					}//while ( !stackPost.isEmpty())
				
				}
				
				lab++ ;
			} // end for cycle
				
		this.nParticlesCount[nFrame]=nPatCount;
		return;// label ;

		
	}
	


	// function calculating convolution kernel of 2D Gaussian shape
	// with background subtraction for spots enhancement
	public void initConvKernel(CDDialog fdg, int nChannel)
	{
		
		int i,j; //counters
		int nBgPixCount; //number of kernel pixels for background subtraction 
		float GaussSum; //sum of all integrated Gaussian function values inside 'spot circle'
		float fSpot; // spot circle radius
		float fSpotSqr; // spot circle radius squared
		
		
		//intermediate values to speed up calculations
		float fIntensity;
		float fDivFactor;
		float fDist;
		float fPixVal;
		float [][] fKernel;
		float nCenter; // center coordinate of the convolution kernel
		
		nCenter = (float) ((fdg.nKernelSize[nChannel] - 1.0)*0.5); // center coordinate of the convolution kernel
		
		//kernel matrix
		fKernel = new float [fdg.nKernelSize[nChannel]][fdg.nKernelSize[nChannel]];
		//kernel string 
		if(nChannel==0)
			fConKernel = new float [fdg.nKernelSize[nChannel]*fdg.nKernelSize[nChannel]];
		else 
			fConKernelTwo = new float [fdg.nKernelSize[nChannel]*fdg.nKernelSize[nChannel]];
		//Gaussian spot region
		if (3*fdg.dPSFsigma[nChannel] > nCenter)
			fSpot = nCenter;
		else
			fSpot = (float) (3.0*fdg.dPSFsigma[nChannel]);
		
		fSpotSqr = fSpot*fSpot;
		
		//intermediate values to speed up calculations
		fIntensity = (float) (fdg.dPSFsigma[nChannel]*fdg.dPSFsigma[nChannel]*0.5*Math.PI);
		fDivFactor = (float) (1.0/(Math.sqrt(2)*fdg.dPSFsigma[nChannel]));
		GaussSum = 0;
		nBgPixCount = 0;
		
		//first run, filling array with gaussian function Approximation values (integrated over pixel)
		//and calculating number of pixels which will serve as a background
		for (i=0; i<fdg.nKernelSize[nChannel]; i++)
		{
			for (j=0; j<fdg.nKernelSize[nChannel]; j++)
			{
				fDist = (i-nCenter)*(i-nCenter) + (j-nCenter)*(j-nCenter);
				
				//background pixels
				if (fDist > fSpotSqr)
					nBgPixCount++;
				
				//gaussian addition
				fPixVal  = errorfunction((i-nCenter-0.5)*fDivFactor) - errorfunction((i-nCenter+0.5)*fDivFactor);
				fPixVal *= errorfunction((j-nCenter-0.5)*fDivFactor) - errorfunction((j-nCenter+0.5)*fDivFactor);
				fPixVal *= fIntensity;
				fKernel[i][j] = fPixVal;
				GaussSum += fPixVal;													
			}				
		}
		
		//background subtraction coefficient
		fDivFactor = (float)(1.0/(double)nBgPixCount);
		
		//second run, normalization and background subtraction
		for (i=0; i<fdg.nKernelSize[nChannel]; i++)
		{
			for (j=0; j<fdg.nKernelSize[nChannel]; j++)
			{
				fDist = (i-nCenter)*(i-nCenter) + (j-nCenter)*(j-nCenter);
				//normalization
				if(nChannel==0)
					fConKernel[i+j*(fdg.nKernelSize[nChannel])] = fKernel[i][j] / GaussSum;
				else
					fConKernelTwo[i+j*(fdg.nKernelSize[nChannel])] = fKernel[i][j] / GaussSum;
				if (fDist > fSpotSqr)
				{
					//background subtraction
					if(nChannel==0)
						fConKernel[i+j*(fdg.nKernelSize[nChannel])] -=fDivFactor;
					else
						fConKernelTwo[i+j*(fdg.nKernelSize[nChannel])] -=fDivFactor;
				}
				
			}		
		}
		
		return;
	}
	
	//implements error function calculation with precision ~ 10^(-4) 
	//used for calculation of Gaussian convolution kernel
	float errorfunction (double d)
	{
		float t = (float) (1.0/(1.0+Math.abs(d)*0.47047));
		float ans = (float) (1.0 - t*(0.3480242 - 0.0958798*t+0.7478556*t*t)*Math.exp((-1.0)*(d*d))); 
		
		if (d >= 0)
			return ans;
		else
			return -ans;
		
	}
	
	
	

	int getIndexofMaxIntensity(ArrayList<int[]> stackPost)
	{
		
		int maxindex = 0;		
		int s = 0;
		
		for (int i=0;i<stackPost.size();i++)
		{					
				if (stackPost.get(i)[2]>s)
				{
					s=stackPost.get(i)[2];
					maxindex = i;
				}
			
		}
		return maxindex;			
	}

	
	int getLocalThreshold(ImageProcessor ip_, int x, int y, int nRad, double coeff)
	{
		double dIntNoise;
		double dSD;
		int i,j;
		//double coeff;
		
		dIntNoise = 0;
		dSD = 0;
		//averaged noise around spot
		j = y-nRad-1;
		for(i=x-nRad-1; i<=x+nRad+1; i++)							
			dIntNoise += ip_.getPixel(i,j);
		j = y+nRad+1;
		for(i=x-nRad-1; i<=x+nRad+1; i++)							
			dIntNoise += ip_.getPixel(i,j);
		i=x-nRad-1;
		for(j=y-nRad; j<=y+nRad; j++)
			dIntNoise += ip_.getPixel(i,j);
		i=x+nRad+1;
		for(j=y-nRad; j<=y+nRad; j++)
			dIntNoise += ip_.getPixel(i,j);
		dIntNoise = dIntNoise/(8*nRad+8);

		//averaged SD of noise around spot
		j = y-nRad-1;
		for(i=x-nRad-1; i<=x+nRad+1; i++)
			dSD += Math.pow(dIntNoise - ip_.getPixel(i,j),2);			
		j = y+nRad+1;
		for(i=x-nRad-1; i<=x+nRad+1; i++)							
			dSD += Math.pow(dIntNoise - ip_.getPixel(i,j),2);
		i=x-nRad-1;
		for(j=y-nRad; j<=y+nRad; j++)
			dSD += Math.pow(dIntNoise - ip_.getPixel(i,j),2);
		i=x+nRad+1;
		for(j=y-nRad; j<=y+nRad; j++)
			dSD += Math.pow(dIntNoise - ip_.getPixel(i,j),2);
		
		dSD = Math.sqrt(dSD/(8*nRad+7));
		
		//if()
		
		return (int)(dIntNoise + coeff*dSD) ;			
	}


	
	//returns value of mean intensity+nThreshold*SD based on 
	//fitting of image histogram to gaussian function
	int [] getThreshold(ImageProcessor thImage, int nSDgap)
	{
		ImageStatistics imgstat;
		double  [][] dHistogram;
		double  [][] dNoiseFit;
		int nHistSize;
		int nCount, nMaxCount;
		int nDownCount, nUpCount;
		int i,nPeakPos; 
		double dRightWidth, dLeftWidth, dWidth;
		double dMean, dSD;
		double [] dFitErrors;
		double dErrCoeff;
		LMA fitlma;
		int [] results;
		
		
		
		//mean, sd, min, max
		//imgstat = ImageStatistics.getStatistics(thImage, 38, null);
		imgstat = ImageStatistics.getStatistics(thImage, Measurements.MEAN+Measurements.STD_DEV+Measurements.MIN_MAX, null);
		dMean = imgstat.mean;
		nPeakPos = 0;
		
		nHistSize = imgstat.histogram.length;		
		dHistogram = new double [2][nHistSize];
		nMaxCount = 0;
	
		//determine position and height of maximum count in histogram (mode)
		//and height at maximum
		for (i=0; i<nHistSize; i++)
		{
			
			nCount=imgstat.histogram[i];
			dHistogram[0][i]=imgstat.min + i*imgstat.binSize;			
			dHistogram[1][i] = (double)nCount;
			if(nMaxCount < nCount)
			{
				nMaxCount= nCount;
				dMean = imgstat.min + i*imgstat.binSize;
				nPeakPos = i;
			}			
		}
		//estimating width of a peak
		//going to the left
		i=nPeakPos;
		while (i>0 && imgstat.histogram[i]>0.5*nMaxCount)
		{
			i--;			
		}
		if(i<0)
			i=0;
		dLeftWidth = i;
		//going to the right
		i=nPeakPos;
		while (i<nHistSize && imgstat.histogram[i]>0.5*nMaxCount)
		{
			i++;			
		}
		if(i==nHistSize)
			i=nHistSize-1;
		dRightWidth = i;
		//FWHM in bins
		dWidth = (dRightWidth-dLeftWidth);
		dSD = dWidth*imgstat.binSize/2.35;
		//fitting range +/- 3*SD
		dLeftWidth = nPeakPos - 3*dWidth/2.35;
		if(dLeftWidth<0)
			dLeftWidth=0;
		dRightWidth = nPeakPos + 3*dWidth/2.35;
		if(dRightWidth>nHistSize)
			dRightWidth=nHistSize;
		nUpCount = (int)dRightWidth;
		nDownCount = (int)dLeftWidth;
		//preparing histogram range for fitting
		dNoiseFit = new double [2][nUpCount-nDownCount+1];
		for(i=nDownCount;i<=nUpCount;i++)
		{
			dNoiseFit[0][i-nDownCount] = dHistogram[0][i];
			dNoiseFit[1][i-nDownCount] = dHistogram[1][i];
		}
		
		fitlma = new LMA(new OneDGaussian(), new double[] {(double)nMaxCount, dMean, dSD}, dNoiseFit);
		fitlma.fit();
		dMean = fitlma.parameters[1];
		dSD = fitlma.parameters[2];
		
		dFitErrors = fitlma.getStandardErrorsOfParameters();
		// scaling coefficient for parameters errors estimation 
		// (Standard deviation of residuals)
		dErrCoeff = Math.sqrt(fitlma.chi2/(nUpCount-nDownCount+1-3));
		for (i=0;i<3;i++)
			dFitErrors[i] *= dErrCoeff;
		for (i=0;i<3;i++)
			dFitErrors[i] *= 100/fitlma.parameters[i]; 
		
		if (dFitErrors[1]> 20 || dMean<imgstat.min || dMean> imgstat.max ||  dSD < imgstat.min || dSD> imgstat.max)
		{			
			//fit somehow failed
			//return (int)(imgstat.mean + nSDgap*imgstat.stdDev);
			results = new int [] {(int) imgstat.mean,(int) imgstat.stdDev};
			return results;
		}
		else
		{
			//return (int)(dMean + nSDgap*dSD);
			results = new int [] {(int) dMean,(int) dSD};
			return results;
		}
		
		
	}
	
	///Convolution speed optimized routines	
	boolean SMLconvolveFloat(ImageProcessor dupip, float[] kernel, int kw, int kh)
	{
		
		int width = dupip.getWidth();
		int height = dupip.getHeight();
		
		
		int x1 = 0;
		int y1 = 0;
		int x2 = x1 + width;
		int y2 = y1 + height;
		int uc = kw/2;    
		int vc = kh/2;
		float[] pixels = (float[])dupip.getPixels();
		float[] pixels2 = (float[])dupip.getSnapshotPixels();
		if (pixels2==null)
			pixels2 = (float[])dupip.getPixelsCopy();
       
		double sum;
		int offset, i;
		boolean edgePixel;
		int xedge = width-uc;
		int yedge = height-vc;
		
		for(int y=y1; y<y2; y++) {
			for(int x=x1; x<x2; x++) {
				sum = 0.0;
				i = 0;
				edgePixel = y<vc || y>=yedge || x<uc || x>=xedge;
				for(int v=-vc; v <= vc; v++) {
					offset = x+(y+v)*width;
					for(int u = -uc; u <= uc; u++) {
						if (edgePixel) {
 							if (i>=kernel.length) // work around for JIT compiler bug on Linux
 								IJ.log("kernel index error: "+i);
							sum += SMLgetPixel(x+u, y+v, pixels2, width, height)*kernel[i++];
						} else
							sum += pixels2[offset+u]*kernel[i++];
					}
		    	}
				pixels[x+y*width] = (float)(sum);
			}
    	}
	
   		return true;
	}
	
	float SMLgetPixel(int x, int y, float[] pixels, int width, int height) {
		if (x<=0) x = 0;
		if (x>=width) x = width-1;
		if (y<=0) y = 0;
		if (y>=height) y = height-1;
		return pixels[x+y*width];
	}
	
	void SMLblur1Direction( final FloatProcessor ippp, final double sigma, final double accuracy,
            final boolean xDirection, final int extraLines) {
        
		final float[] pixels = (float[])ippp.getPixels();
        final int width = ippp.getWidth();
        final int height = ippp.getHeight();
        final int length = xDirection ? width : height;     //number of points per line (line can be a row or column)
        final int pointInc = xDirection ? 1 : width;        //increment of the pixels array index to the next point in a line
        final int lineInc = xDirection ? width : 1;         //increment of the pixels array index to the next line
        final int lineFromA = 0 - extraLines;  //the first line to process
        final int lineFrom;
        if (lineFromA < 0) lineFrom = 0;
        else lineFrom = lineFromA;
        final int lineToA = (xDirection ? height : width) + extraLines; //the last line+1 to process
        final int lineTo;
        if (lineToA > (xDirection ? height:width)) lineTo = (xDirection ? height:width);
        else lineTo = lineToA;
        final int writeFrom = 0;    //first point of a line that needs to be written
        final int writeTo = xDirection ? width : height;
 
        final float[][] gaussKernel = lowpassGauss.makeGaussianKernel(sigma, accuracy, length);
        final int kRadius = gaussKernel[0].length;             //Gaussian kernel radius after upscaling
        final int readFrom = (writeFrom-kRadius < 0) ? 0 : writeFrom-kRadius; //not including broadening by downscale&upscale
        final int readTo = (writeTo+kRadius > length) ? length : writeTo+kRadius;
        final int newLength = length;
       
           
          final float[] cache1 = new float[newLength];  //holds data before convolution (after downscaling, if any)
                  
          int pixel0 = 0;
          for (int line=lineFrom; line<lineTo; line ++, pixel0+=lineInc) 
          {
                                    int p = pixel0 + readFrom*pointInc;
                                    for (int i=readFrom; i<readTo; i++ ,p+=pointInc)
                                        cache1[i] = pixels[p];
                                    SMLconvolveLine(cache1, pixels, gaussKernel, readFrom, readTo, writeFrom, writeTo, pixel0, pointInc);
                               
                                    
         }
            
        return;
    }
	 
	void SMLconvolveLine( final float[] input, final float[] pixels, final float[][] kernel, final int readFrom,
	            final int readTo, final int writeFrom, final int writeTo, final int point0, final int pointInc) {
	        final int length = input.length;
	        final float first = input[0];                 //out-of-edge pixels are replaced by nearest edge pixels
	        final float last = input[length-1];
	        final float[] kern = kernel[0];               //the kernel itself
	        final float kern0 = kern[0];
	        final float[] kernSum = kernel[1];            //the running sum over the kernel
	        final int kRadius = kern.length;
	        final int firstPart = kRadius < length ? kRadius : length;
	        int p = point0 + writeFrom*pointInc;
	        int i = writeFrom;
	        for (; i<firstPart; i++,p+=pointInc) {  //while the sum would include pixels < 0
	            float result = input[i]*kern0;
	            result += kernSum[i]*first;
	            if (i+kRadius>length) result += kernSum[length-i-1]*last;
	            for (int k=1; k<kRadius; k++) {
	                float v = 0;
	                if (i-k >= 0) v += input[i-k];
	                if (i+k<length) v+= input[i+k];
	                result += kern[k] * v;
	            }
	            pixels[p] = result;
	        }
	        final int iEndInside = length-kRadius<writeTo ? length-kRadius : writeTo;
	        for (;i<iEndInside;i++,p+=pointInc) {   //while only pixels within the line are be addressed (the easy case)
	            float result = input[i]*kern0;
	            for (int k=1; k<kRadius; k++)
	                result += kern[k] * (input[i-k] + input[i+k]);
	            pixels[p] = result;
	        }
	        for (; i<writeTo; i++,p+=pointInc) {    //while the sum would include pixels >= length 
	            float result = input[i]*kern0;
	            if (i<kRadius) result += kernSum[i]*first;
	            if (i+kRadius>=length) result += kernSum[length-i-1]*last;
	            for (int k=1; k<kRadius; k++) {
	                float v = 0;
	                if (i-k >= 0) v += input[i-k];
	                if (i+k<length) v+= input[i+k];
	                result += kern[k] * v;
	            }
	            pixels[p] = result;
	        }
	    }

	
	
}
