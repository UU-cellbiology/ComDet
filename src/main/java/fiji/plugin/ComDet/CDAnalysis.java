package fiji.plugin.ComDet;

import ij.IJ;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Convolver;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.MaximumFinder;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import jaolho.data.lma.LMA;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

import fiji.plugin.ComDet.CDDialog;
import fiji.plugin.ComDet.OneDGaussian;


public class CDAnalysis {
	
	ImageStatistics imgstat;
	GaussianBlur lowpassGauss = new GaussianBlur(); //low pass prefiltering
	Convolver      colvolveOp = new Convolver(); //convolution filter
	
	/** convolution filter for specific channel **/
	float [][]		 fConKernel;  
	/** array holding number of particles in each slice/frame **/
	int [] nParticlesCount;
	int [][][] colocstat;
	
	Color colorCh;
	/** Results table with particle's approximate center coordinates **/
	public ResultsTable ptable = Analyzer.getResultsTable(); 	
	java.util.concurrent.locks.Lock ptable_lock = new java.util.concurrent.locks.ReentrantLock();
	public Overlay overlay_;
	
	//default constructor 
	public CDAnalysis(int nChannelN)
	{
		ptable.setPrecision(5);
		overlay_ = null;
		fConKernel = new float[nChannelN][];  
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
		float nThreshold;
		float [] nNoise;
		int chIndex;
		
		/** duplicate of image, convolved **/
		FloatProcessor dupip = null ; 
		/** thresholded image **/
		ByteProcessor dubyte = null; 
		ByteProcessor dubyteWS = null; 
		MaximumFinder maxF = new MaximumFinder(); 
		
		//if(fdg.bTwoChannels)
			chIndex = nImagePos[0]-1;
		//else
		//	chIndex = 0;
		
		dupip = (FloatProcessor) ip.duplicate().convertToFloat();
		
		//low-pass filtering		
		SMLblur1Direction(dupip, fdg.dPSFsigma[chIndex]*0.5, 0.0002, true, (int)Math.ceil(5*fdg.dPSFsigma[chIndex]*0.5));
		SMLblur1Direction(dupip, fdg.dPSFsigma[chIndex]*0.5, 0.0002, false, 0);

		//convolution with gaussian PSF kernel		
//		if((fdg.bTwoChannels) && nImagePos[0]==2)
//			SMLconvolveFloat(dupip, fConKernelTwo, fdg.nKernelSize[chIndex], fdg.nKernelSize[chIndex]);
//		else
			SMLconvolveFloat(dupip, fConKernel[chIndex], fdg.nKernelSize[chIndex], fdg.nKernelSize[chIndex]);
		
		
		//new ImagePlus("convoluted", dupip.duplicate()).show();
		
		nNoise  = getThreshold(dupip);
		//nNoise  = getThreshold(dupip.duplicate(), fdg.nSensitivity[chIndex]);
						
		nThreshold = nNoise[0] +  fdg.nSensitivity[chIndex]*nNoise[1];

		
		dubyte  = thresholdFloat(dupip,nThreshold);
		//new ImagePlus("thresholded", dubyte.duplicate()).show();
		
		if(fdg.nKernelSize[chIndex]>5 )
		{
			dubyte.dilate();		
			
			dubyte.erode();
		}
		if(fdg.bSegmentLargeParticles[chIndex] && fdg.bBigParticles[chIndex])
		{
			dubyteWS= maxF.findMaxima(dupip, fdg.nSensitivity[chIndex]*nNoise[1], MaximumFinder.SEGMENTED, false);
			dubyte.copyBits(dubyteWS, 0, 0, Blitter.AND);
		}
		
		

		//new ImagePlus("thresholded_eroded", dubyte.duplicate()).show();

		labelParticles(dubyte, dupip, ip, fdg, nImagePos, nSlice, RoiActive_);
		
	}
	
	/** function that finds centroid x,y and area
	//of spots after thresholding
	//based on connected components	labeling Java code
	//implemented by Mariusz Jankowski & Jens-Peer Kuska
	//and in March 2012 available by link
    //http://www.izbi.uni-leipzig.de/izbi/publikationen/publi_2004/IMS2004_JankowskiKuska.pdf
     */	
	void labelParticles(ImageProcessor ipBinary,  ImageProcessor ipConvol, ImageProcessor ipRaw,  CDDialog fdg, int [] nImagePos, int nFrame, Roi RoiAct)//, boolean bIgnore)//, double dSymmetry_)
	{

		
		Roi spotROI;
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
		int xMin,xMax,yMin,yMax;
		
		Stack<int[]> sstack = new Stack<int[]>( );
		ArrayList<int[]> stackPost = new ArrayList<int[]>( );
		int [][] label = new int[width][height] ;
		
		
		chIndex = nImagePos[0]-1;

		boolean bBigParticles=fdg.bBigParticles[chIndex];

		
					
		
		for (int r = 1; r < width-1; r++)
			for (int c = 1; c < height-1; c++) {
				
				if (ipBinary.getPixel(r,c) == 0.0) continue ;
				if (label[r][c] > 0.0) continue ;
				/* encountered unlabeled foreground pixel at position r, c */
				/* it means it is a new spot! */
				/* push the position in the stack and assign label */
				sstack.push(new int [] {r, c}) ;
				stackPost.clear();
				stackPost.add(new int [] {r, c, ipConvol.getPixel(r,c)}) ;
				label[r][c] = lab ;
				nArea = 0;
				dIMax = -1000;
				xCentroid = 0; yCentroid = 0;
				//xMax = 0; yMax = 0;
				dInt = 0;
				bBorder = false;
				yMin=height+1;
				xMin=width+1;
				yMax=-100;
				xMax=-100;

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
					if(i>xMax)
						xMax=i;
					if(j>yMax)
						yMax=j;					
					if(i<xMin)
						xMin=i;					
					if(j<yMin)
						yMin=j;
					
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
				
				//extra border check
				xMin=xMin-1;
				yMin=yMin-1;
				xMax=xMax+1;
				yMax=yMax+1;
				if(xMin<=0 || yMin<=0 || xMax>=(width-1) || yMax>=(height-1))
					bBorder = true;
				
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
								 
								 //add to table
								 if(overlay_==null)
								 {
								 	dIMax=getIntIntNoise( ipRaw, xMin, xMax, yMin, yMax);
									ptable_lock.lock();
									ptable.incrementCounter();									
									ptable.addValue("Abs_frame", nFrame+1);
									ptable.addValue("X_(px)",xCentroid);	
									ptable.addValue("Y_(px)",yCentroid);
									//ptable.addValue("Frame_Number", nFrame+1);
									ptable.addValue("Channel", nImagePos[0]);
									ptable.addValue("Slice", nImagePos[1]);
									ptable.addValue("Frame", nImagePos[2]);
									ptable.addValue("xMin", xMin);
									ptable.addValue("yMin", yMin);
									ptable.addValue("xMax", xMax);
									ptable.addValue("yMax", yMax);
									ptable.addValue("NArea", nArea);
									ptable.addValue("IntegratedInt", dIMax);
									
									ptable_lock.unlock();
								 }
								 //preview mode, show overlay
								 else
								 {
									//adding spot to the overlay
									spotROI = new Roi(xMin,yMin,xMax-xMin,yMax-yMin);
									spotROI.setStrokeColor(colorCh);									
									overlay_.add(spotROI);
								 }
									nPatCount++;

							 }

					}
				
				}
				////probably many particles in the thresholded area				
				if(nArea >= fdg.nAreaMax[chIndex] && !bBorder && bBigParticles)
				{
					//if(bBigParticles)
					//{
						
						
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
									 dIMax=getIntIntNoise( ipRaw, xMin, xMax, yMin, yMax);
									 //add to table
									 if(overlay_==null)
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
										ptable.addValue("xMin", xMin);
										ptable.addValue("yMin", yMin);
										ptable.addValue("xMax", xMax);
										ptable.addValue("yMax", yMax);
										ptable.addValue("NArea", nArea);
										ptable.addValue("IntegratedInt", dIMax);
										ptable_lock.unlock();
									 }
									 else
									 {
										//adding spot to the overlay
										spotROI = new Roi(xMin,yMin,xMax-xMin,yMax-yMin);
										spotROI.setStrokeColor(colorCh);									
										overlay_.add(spotROI); 
									 }
									nPatCount++;
								 }
							}
				
				}
				
				lab++ ;
			} // end for cycle
		if(overlay_==null)		 
			this.nParticlesCount[nFrame]=nPatCount;
		return;// label ;

		
	}
	
	/** function calculating integrated intensity and noise around spot
	 * 
	 
	 */
	public static double getIntIntNoise(ImageProcessor ipRaw, int xMin, int xMax, int yMin, int yMax)
	{
		double dNoise;
		int nNoisePix, nIntIntPix, i,j;
		double dIntInt;
		
		nNoisePix = 0;
		dNoise = 0;
		for (i=xMin;i<=xMax;i++)
		{
			nNoisePix+=2;
			dNoise +=ipRaw.getPixel(i,yMin) +ipRaw.getPixel(i,yMax);
		}
		for (j=(yMin+1);j<=(yMax-1);j++)
		{
			nNoisePix+=2;
			dNoise +=ipRaw.getPixel(xMin,j) +ipRaw.getPixel(xMax,j);
		}
		dNoise =dNoise /nNoisePix;
		dIntInt = 0;
		nIntIntPix =0;
		
		for(i=xMin;i<=xMax;i++)
			for(j=yMin;j<=yMax;j++)
			{
				nIntIntPix ++;
				dIntInt += ipRaw.getPixel(i,j);
				
			}
		dIntInt = dIntInt - dNoise*nIntIntPix;
		return dIntInt; 
		
	}

	/** function calculating convolution kernel of 2D Gaussian shape
	 * with background subtraction for spots enhancement
	 * 
	 * @param fdg
	 * @param nChannel
	 */
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
		fConKernel[nChannel] = new float [fdg.nKernelSize[nChannel]*fdg.nKernelSize[nChannel]];
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
				fConKernel[nChannel][i+j*(fdg.nKernelSize[nChannel])] = fKernel[i][j] / GaussSum;
				if (fDist > fSpotSqr)
				{
					//background subtraction
					fConKernel[nChannel][i+j*(fdg.nKernelSize[nChannel])] -=fDivFactor;
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
	
	


	
	double [] getLocalThreshold(ImageProcessor ip_, int x, int y, int nRad, double coeff)
	{
		double dIntNoise;
		double dSD;
		int i,j;
		double [] ret = new double[2];
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
		ret[0]=dIntNoise;
		ret[1]=dSD;
		return ret;
		//return (int)(dIntNoise + coeff*dSD) ;			
	}


	
	/** 
	 *  returns value of mean intensity and SD in float array based on 
	 *  fitting of image histogram to Gaussian function
	**/
	float [] getThreshold(ImageProcessor thImage)
	{
		ImageStatistics imgstat;

		double  [][] dNoiseFit;
		int nHistSize;
		int nMaxCount;
		int nDownCount, nUpCount;
		int i,nPeakPos,k; 
		double dRightWidth, dLeftWidth;
		double dWidth=0;
		double dMean, dSD;
		double [] dFitErrors;
		double dErrCoeff;
		LMA fitlma;
		float [] results;
		int [] nHistgr;
		int nBinSizeEst = 256;
		boolean bOptimal = false;
		int nPeakNew;
		
		
		
		nBinSizeEst =getBinOptimalNumber(thImage);
		
		//searching for the optimal for fitting intensity histogram's bin size
	
		thImage.setHistogramSize(nBinSizeEst);			
		imgstat = ImageStatistics.getStatistics(thImage, Measurements.MODE + Measurements.MEAN+Measurements.STD_DEV+Measurements.MIN_MAX, null);
		nHistSize = imgstat.histogram.length;											
		nPeakPos = imgstat.mode;
		nMaxCount = imgstat.maxCount;
		nHistgr = new int [nHistSize];
		for(k=0;k<nHistSize;k++)
			nHistgr[k]=imgstat.histogram[k];
		
		//Plot histplot = new Plot("Histogram","intensity", "count", dHistogram[0], dHistogram[1]);
		//histplot.show();

		while (!bOptimal)
		{
			//estimating width of a peak
			//going to the left
			i = nPeakPos;
			while (i>0 && nHistgr[i]>0.5*nMaxCount)
			{
				i--;			
			}
			if(i<0)
				i=0;
			dLeftWidth = i;
			//going to the right
			i=nPeakPos;
			while (i<nHistSize && nHistgr[i]>0.5*nMaxCount)
			{
				i++;			
			}
			if(i==nHistSize)
				i=nHistSize-1;
			dRightWidth = i;
			//FWHM in bins
			dWidth = (dRightWidth-dLeftWidth);
			//histogram is too narrow for fitting, increase number of bins
			if(dWidth<12)
			{
				nBinSizeEst = nBinSizeEst + 100;
				//bin set is too dense
				if(nBinSizeEst> 1000)
				{
					//ok, seems there is one very high peak/bin, let's remove it					
					//nBinSizeEst = 256;
					thImage.setHistogramSize(nBinSizeEst);	
					imgstat = ImageStatistics.getStatistics(thImage, Measurements.MODE + Measurements.MEAN+Measurements.STD_DEV+Measurements.MIN_MAX, null);
					nHistSize = imgstat.histogram.length;											
					nPeakPos = imgstat.mode;
					//nMaxCount = imgstat.maxCount;
					//remove peak value
					nHistgr = new int [nHistSize-1];
					nPeakNew=0; nMaxCount=0;
					for(k=0;k<nPeakPos;k++)
					{
						nHistgr[k]=imgstat.histogram[k];
						if(nHistgr[k]>nMaxCount)
						{
							nMaxCount = nHistgr[k];
							nPeakNew = k;
						}
					}
					for(k=nPeakPos+1;k<nHistSize;k++)
					{
						nHistgr[k-1]=imgstat.histogram[k];
						if(nHistgr[k-1]>nMaxCount)
						{
							nMaxCount = nHistgr[k-1];
							nPeakNew = k-1;
						}
					}
					nPeakPos=nPeakNew;
					nHistSize = nHistSize-1;
					
					//estimating width of a peak
					//going to the left
					i = nPeakPos;
					while (i>0 && nHistgr[i]>0.5*nMaxCount)
					{
						i--;			
					}
					if(i<0)
						i=0;
					dLeftWidth = i;
					//going to the right
					i=nPeakPos;
					while (i<nHistSize && nHistgr[i]>0.5*nMaxCount)
					{
						i++;			
					}
					if(i==nHistSize)
						i=nHistSize-1;
					dRightWidth = i;
					//FWHM in bins
					dWidth = (dRightWidth-dLeftWidth);
					bOptimal = true;
				}
				else
				{
					//recalculate parameters
					thImage.setHistogramSize(nBinSizeEst);			
					imgstat = ImageStatistics.getStatistics(thImage, Measurements.MODE + Measurements.MEAN+Measurements.STD_DEV+Measurements.MIN_MAX, null);
					nHistSize = imgstat.histogram.length;											
					nPeakPos = imgstat.mode;
					nMaxCount = imgstat.maxCount;
					nHistgr = new int[nHistSize];
					for(k=0;k<nHistSize;k++)
						nHistgr[k]=imgstat.histogram[k];					
				}
			}
			//histogram is ok, proceed to fitting
			else
			{
				bOptimal = true;				
			}
		}

					
		dMean = imgstat.min + nPeakPos*imgstat.binSize;
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
			dNoiseFit[0][i-nDownCount] = imgstat.min + i*imgstat.binSize;
			dNoiseFit[1][i-nDownCount] = (double)nHistgr[i];
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
		
	
		
			results = new float [] {(float) dMean,(float) dSD};
			return results;
	
		
		
	}
	
	/** function returns optimal bin number for the image histogram
	 *  according to the Freedman-Diaconis rule (check wiki) **/
	int getBinOptimalNumber(ImageProcessor ip)
	{
		//int nBinSize;
		int width, height;
		int pixelCount;

		
		width=ip.getWidth();
		height=ip.getHeight();
		pixelCount=width*height;
		

		float[] pixels2 = new float[pixelCount];
		//float[] pixels2;
		System.arraycopy((float[])ip.getPixels(),0,pixels2,0,pixelCount);
	
		Arrays.sort(pixels2);
		//int middle = pixels2.length/2;
		int qi25 = Math.round(pixelCount*0.25f);
		int qi75 = Math.round(pixelCount*0.75f);
	
		float IQR = pixels2[qi75]-pixels2[qi25];
		double h= 2*IQR*Math.pow((double)pixelCount, -1.0/3.0);
		
		return (int)Math.round((pixels2[pixelCount-1]-pixels2[0])/h);
			
	}
	
	/** function thresholds FloatProcessor and returns byte version
	 * 
	 */
	ByteProcessor thresholdFloat(FloatProcessor ip, float dThreshold)
	{
		int width=ip.getWidth();
		int height= ip.getHeight();
		int i;
		ByteProcessor bp = new ByteProcessor(width, height);
		float[] flPixels = (float[])ip.getPixels();
//		byte[] btPixels = (byte[])bp.getPixels();
		
		for (int y=0; y<height; y++) 
		{
			i = y*width;
			for (int x=0; x<width; x++) 
			{
				if(flPixels[i]>=dThreshold)
				{
					//btPixels[i]=(byte)255;
					bp.set(x, y, 255);
				}
				else
				{
					//btPixels[i]=(byte)0;
					bp.set(x, y, 0);
				}				
				i++;
			}
		}
				
		return bp;
	}
	
	/** Convolution speed optimized routines
	 * 	
	 * @param dupip image processor
	 * @param kernel convolution 1D kernel
	 * @param kw
	 * @param kh
	 * @return
	 */
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
