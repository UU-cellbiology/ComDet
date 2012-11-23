package ComDet;

import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Convolver;
import ij.plugin.filter.GaussianBlur;

import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;

import jaolho.data.lma.LMA;

import java.awt.Frame;

import ComDet.CDDialog;


public class CDAnalysis {
	
	ImageStatistics imgstat;
	GaussianBlur lowpassGauss = new GaussianBlur(); //low pass prefiltering
	Convolver      colvolveOp = new Convolver(); //convolution filter
	float []		 fConKernel;  
	int [] nParticlesCount;
	//int nTrue;
	//int nFalse;
	//ResultsTable ptable = new ResultsTable(); // table with particle's approximate center coordinates
	public ResultsTable ptable = Analyzer.getResultsTable(); // table with particle's approximate center coordinates
	public TextWindow SummaryTable;  
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
	void detectParticles(ImageProcessor ip, CDDialog fdg, int nFrame, Overlay SpotsPositions_, Roi RoiActive_)
	{
		int nGlobalThreshold;
		double dLocalTresholdCoeff;
		ImageProcessor dupip; //duplicate of image
		dupip = ip.duplicate(); // dublicate of ip
		
		//low-pass filtering by gaussian blurring
		lowpassGauss.blurGaussian(dupip, fdg.dPSFsigma*0.5, fdg.dPSFsigma*0.5, 0.0002);
		
		
		//convolution with gaussian PSF kernel
		dupip = dupip.convertToFloat();		
		colvolveOp.setNormalize(false);
		colvolveOp.convolveFloat(dupip, fConKernel, fdg.nKernelSize, fdg.nKernelSize);
		dupip = dupip.convertToShort(true);
		//duconv= dupip.duplicate();
		
		//duconv= duconv.convertToFloat();		
		//colvolveOp.convolveFloat(duconv, fConKernel, fdg.nKernelSize, fdg.nKernelSize);
		//duconv = duconv.convertToShort(true);
		//ImagePlus imp = new ImagePlus("result", dupip);  
		//imp.show();  
		//new ImagePlus("result1", dupip).show();
		
		//thresholding
		imgstat = ImageStatistics.getStatistics(dupip, 22, null); //6 means MEAN + STD_DEV, look at ij.measure.Measurements
		nGlobalThreshold = (int)(imgstat.mean + 3.0*imgstat.stdDev);
		//nGlobalThreshold = getThreshold(dupip);
		dLocalTresholdCoeff = ((double)fdg.nSensitivity)+3.0;
		//dupip.threshold(nThreshold);
		//convert to byte
		//dubyte  = (ByteProcessor) dupip.convertToByte(false);
		
		//morphological operations on thresholded image	
		//dubyte.dilate(2, 0);
		//dubyte.erode(2, 0);
		
		//dubyte.dilate();		
		//dubyte.erode();

		//dupip.invert();
		//new ImagePlus("result1", dubyte).show();
		
		labelParticles(dupip, nFrame, nGlobalThreshold, fdg.dPSFsigma, SpotsPositions_, dLocalTresholdCoeff, RoiActive_);//, fdg.dSymmetry/100);
		//labelParticles(dubyte, duconv, nFrame, fdg.dPixelSize, fdg.nAreaCut, fdg.dPSFsigma, fdg.dSymmetry/100);
		

		/*ImagePlus imp = new ImagePlus("result_decon", duconv);  
		imp.show();
		imp = new ImagePlus("result_threshold", dubyte);  
		imp.show();*/

		//ptable.show("Results");

		//ip.setPixels(pixels)
		//ImagePlus imp2 = new ImagePlus("result2", dupip);  

		//imp2.show();  		
	}
	
	//function that finds centroid x,y and area
	//of spots after thresholding
	//based on connected components	labeling Java code
	//implemented by Mariusz Jankowski & Jens-Peer Kuska
	//and in March 2012 available by link
    //http://www.izbi.uni-leipzig.de/izbi/publikationen/publi_2004/IMS2004_JankowskiKuska.pdf
	
	void labelParticles(ImageProcessor ipConv,  int nFrame, double nThreshold_, double dPSFsigma_, Overlay SpotsPositions__, double coeff_, Roi RoiAct)
	{
		int width = ipConv.getWidth();
		int height = ipConv.getHeight();
		int nFitRadius = 3; //radius in number of SD around center point to fit Gaussian
		int dBorder; // radius in pixels around center point to fit Gaussian
 		
		int nPatCount = 0;
		
		OvalRoi spotROI;
		
		//int nArea;

		int i,j;
		//double dVal, dInt;
		//double dIMax;
		int RoiRad = (int) (2.0*dPSFsigma_);
		
		int xCentroid, yCentroid;
		//boolean bBorder;

		//int lab = 1;
		int [] maxpos ;	
		int sMax;
		int nLocalThreshold;
		boolean bInRoi;
		
		//Stack<int[]> sstack = new Stack<int[]>( );
		//Stack<int[]> stackPost = new Stack<int[]>( );
		//int [][] label = new int[width][height] ;

		dBorder= (int)(dPSFsigma_*nFitRadius);
		

		
		/*ImageProcessor afterFit; //duplicate of image
		afterFit = ipRaw.duplicate();
		double dGValue;
		double [] dCorrsxy = new double [2];*/
		sMax = (int) (2*nThreshold_);
		while (sMax > nThreshold_)
		{
		//for (int r = 1; r < width-1; r++)
			//for (int c = 1; c < height-1; c++) {
				
				//if (ipBinary.getPixel(r,c) == 0.0) continue ;
				//if (label[r][c] > 0.0) continue ;
				/* encountered unlabeled foreground pixel at position r, c */
				/* it means it is a new spot! */
				/* push the position in the stack and assign label */
				/*sstack.push(new int [] {r, c}) ;
				stackPost.push(new int [] {r, c}) ;
				label[r][c] = lab ;
				nArea = 0;
				dIMax = -1000;
				xCentroid = 0; yCentroid = 0;
				//xMax = 0; yMax = 0;
				dInt = 0;
				bBorder = false;*/

				/* start the float fill */
				/*while ( !sstack.isEmpty()) 
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
						stackPost.push( new int[] {i-1,j-1} );
						label[i-1][j-1] = lab ;
					}
					
					if (ipBinary.getPixel(i-1,j) > 0 && label[i-1][j] == 0) {
						sstack.push( new int[] {i-1,j} );
						stackPost.push( new int[] {i-1,j} );
						label[i-1][j] = lab ;
					}
					
					if (ipBinary.getPixel(i-1,j+1) > 0 && label[i-1][j+1] == 0) {
						sstack.push( new int[] {i-1,j+1} );
						stackPost.push( new int[] {i-1,j+1} );
						label[i-1][j+1] = lab ;
					}
					
					if (ipBinary.getPixel(i,j-1) > 0 && label[i][j-1] == 0) {
						sstack.push( new int[] {i,j-1} );
						stackPost.push( new int[] {i,j-1} );
						label[i][j-1] = lab ;
					}
					
					if (ipBinary.getPixel(i,j+1) > 0 && label[i][j+1] == 0) {
						sstack.push( new int[] {i,j+1} );
						stackPost.push( new int[] {i,j+1} );
						label[i][j+1] = lab ;
					}
					if (ipBinary.getPixel(i+1,j-1) > 0 && label[i+1][j-1] == 0) {
						sstack.push( new int[] {i+1,j-1} );
						stackPost.push( new int[] {i+1,j-1} );
						label[i+1][j-1] = lab ;
					}
				
					if (ipBinary.getPixel(i+1,j)>0 && label[i+1][j] == 0) {
						sstack.push( new int[] {i+1,j} );
						stackPost.push( new int[] {i+1,j} );
						label[i+1][j] = lab ;
					}
					
					if (ipBinary.getPixel(i+1,j+1) > 0 && label[i+1][j+1] == 0) {
						sstack.push( new int[] {i+1,j+1} );
						stackPost.push( new int[] {i+1,j+1} );
						label[i+1][j+1] = lab ;
					}
					
				} /* end while */
				//if(!bBorder && nArea > nAreaCut)
				//if(!bBorder)
				maxpos = getMaxPositions(ipConv);
				sMax = maxpos[0];
				xCentroid = maxpos[1];
				yCentroid = maxpos[2];
				if(sMax>nThreshold_)
				{					
					//xCentroid /= dInt;
					//yCentroid /= dInt;
					

						if ( (xCentroid>dBorder) && (yCentroid>dBorder) && (xCentroid<(width-1-dBorder)) && (yCentroid<(height-1-dBorder)) )
						{
				
							 nLocalThreshold= getLocalThreshold(ipConv, xCentroid,yCentroid,RoiRad,coeff_);
							 //check for ROI
							 bInRoi = true;
							 if(RoiAct!=null)
							 {
								 if(!RoiAct.contains(xCentroid, yCentroid))
									 bInRoi=false;
							 }
							 if(sMax>nLocalThreshold && bInRoi)
							 {
							
								ptable_lock.lock();
								ptable.incrementCounter();

								/*ptable.addValue("Amplitude_fit",SMLlma.parameters[1]);
								
								ptable.addValue("X (px)",SMLlma.parameters[2]);							
								ptable.addValue("Y (px)",SMLlma.parameters[3]);
								ptable.addValue("X (nm)",SMLlma.parameters[2]*dPixelSize_);							
								ptable.addValue("Y (nm)",SMLlma.parameters[3]*dPixelSize_);
								ptable.addValue("Z (nm)",0);
								ptable.addValue("False positive?", nFalsePositive);
								ptable.addValue("X_loc_error, pix", dFitErrors[2]);
								ptable.addValue("Y_loc_error, pix", dFitErrors[3]);

								ptable.addValue("BGfit",SMLlma.parameters[0]);							
								ptable.addValue("IntegratedInt",dIntAmp);
								ptable.addValue("SNR", dSNR);

								ptable.addValue("chi2_fit",SMLlma.chi2);*/			
								ptable.addValue("X (px)", xCentroid);
								ptable.addValue("Y (px)", yCentroid);
								//ptable.addValue("nArea, pix", nArea);
								ptable.addValue("Frame Number", nFrame+1);
								/*ptable.addValue("Iterations_fit",SMLlma.iterationCount);
								ptable.addValue("SD_X_fit, pix",SMLlma.parameters[4]);
								ptable.addValue("SD_Y_fit, pix",SMLlma.parameters[5]);
								ptable.addValue("Amp_loc_error",dFitErrors[1]);*/
								
								ptable_lock.unlock();

								//adding overlay circle around to image  
								spotROI = new OvalRoi((int)xCentroid-RoiRad,(int)yCentroid-RoiRad,2*RoiRad,2*RoiRad);
								spotROI.setPosition(nFrame+1);
								SpotsPositions__.add(spotROI);

								nPatCount++;
							 }
						}
						
						for(i=xCentroid-RoiRad;i<=xCentroid+RoiRad; i++)
							for(j=yCentroid-RoiRad;j<=yCentroid+RoiRad; j++)
							{
								if((i>=0)&&(j>=0)&&(i<width)&&(j<height))
									ipConv.set(i,j,0);
								
							}
						
					}
								
			} // end for big while cycle
		
		this.nParticlesCount[nFrame]=nPatCount;
		return;// label ;

		
	}
	


	// function calculating convolution kernel of 2D Gaussian shape
	// with background subtraction for spots enhancement
	public void initConvKernel(CDDialog fdg)
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
		
		float nCenter = (float) ((fdg.nKernelSize - 1.0)*0.5);; // center coordinate of the convolution kernel
		
		//kernel matrix
		fKernel = new float [fdg.nKernelSize][fdg.nKernelSize];
		//kernel string 
		fConKernel = new float [fdg.nKernelSize*fdg.nKernelSize];
		
		//Gaussian spot region
		if (3*fdg.dPSFsigma > nCenter)
			fSpot = nCenter;
		else
			fSpot = (float) (3.0*fdg.dPSFsigma);
		
		fSpotSqr = fSpot*fSpot;
		
		//intermediate values to speed up calculations
		fIntensity = (float) (fdg.dPSFsigma*fdg.dPSFsigma*0.5*Math.PI);
		fDivFactor = (float) (1.0/(Math.sqrt(2)*fdg.dPSFsigma));
		GaussSum = 0;
		nBgPixCount = 0;
		
		//first run, filling array with gaussian function Approximation values (integrated over pixel)
		//and calculating number of pixels which will serve as a background
		for (i=0; i<fdg.nKernelSize; i++)
		{
			for (j=0; j<fdg.nKernelSize; j++)
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
		for (i=0; i<fdg.nKernelSize; i++)
		{
			for (j=0; j<fdg.nKernelSize; j++)
			{
				fDist = (i-nCenter)*(i-nCenter) + (j-nCenter)*(j-nCenter);
				//normalization
				fConKernel[i+j*(fdg.nKernelSize)] = fKernel[i][j] / GaussSum;
				if (fDist > fSpotSqr)
				{
					//background subtraction
					fConKernel[i+j*(fdg.nKernelSize)] -=fDivFactor;
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
	
	//show results table
	void showTable()
	{
			String sSumData ="";
            ptable.show("Results");
            for(int i = 0;i<nParticlesCount.length; i++)
            	sSumData = sSumData + Integer.toString(i+1)+"\t"+Integer.toString(nParticlesCount[i])+"\n";
			
            Frame frame = WindowManager.getFrame("Summary");
			if (frame!=null && (frame instanceof TextWindow) )
			{
				SummaryTable = (TextWindow)frame;
				SummaryTable.getTextPanel().clear();
				SummaryTable.getTextPanel().append(sSumData);
				SummaryTable.getTextPanel().updateDisplay();			
			}
			else
				SummaryTable = new TextWindow("Summary", "Frame Number\tNumber of Particles", sSumData, 450, 300);
            //SummaryTable.setVisible(true);

	}
	
	int [] getMaxPositions(ImageProcessor ip)
	{
		int [] results = new int [3];
		results[0]=0;
		results[1]=0;
		results[2]=0;
		int s = 0;
		
		for (int i=0;i<ip.getWidth();i++)
		{
			for (int j=0;j<ip.getHeight();j++)
			{
				s=ip.get(i, j);	
				if (s>results[0])
				{
					results[0]=s;
					results[1]=i;
					results[2]=j;
				}
			}
		}
		return results;			
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
	
	//returns value of mean intensity+3*SD based on 
	//fitting of image histogram to gaussian function
	int getThreshold(ImageProcessor thImage)
	{
		ImageStatistics imgstat;
		double  [][] dHistogram;		
		int nUpCount, nDownCount;
		double dUpRef, dDownRef;
		int nCount, nMaxCount;
		int i,j;
		double dMean, dSD;
		LMA fitlma;
		
		//mean, sd, min, max
		imgstat = ImageStatistics.getStatistics(thImage, 38, null);
		
		dUpRef   = (imgstat.mean + 3.0*imgstat.stdDev);
		dDownRef = (imgstat.mean - 3.0*imgstat.stdDev);
		if(dUpRef>imgstat.max)
			dUpRef=imgstat.max;
		if(dDownRef<imgstat.min)
			dDownRef=imgstat.min;
		
		nDownCount =   (int) ((dDownRef - imgstat.min)/imgstat.binSize);
		nUpCount =   (int) ((dUpRef - imgstat.min)/imgstat.binSize);
		if(nUpCount>255)
			nUpCount = 255;

		dHistogram = new double [2][nUpCount-nDownCount +1];
		j=0;
		nMaxCount = 0;
		dMean = imgstat.mean;
		for (i=nDownCount; i<=nUpCount; i++)
		{
			
			nCount=imgstat.histogram[i];
			
			dHistogram[0][j] = dDownRef + j*imgstat.binSize;
			dHistogram[1][j] = (double)nCount;
			if(nMaxCount < nCount)
			{
				nMaxCount= nCount;
				dMean = dDownRef + j*imgstat.binSize;
			}
			j++;
		}
		fitlma = new LMA(new OneDGaussian(), new double[] {(double)nMaxCount, dMean, imgstat.stdDev}, dHistogram);
		fitlma.fit();
		dMean = fitlma.parameters[1];
		dSD = fitlma.parameters[2];
		
		if (fitlma.iterationCount== 101 || dMean<imgstat.min || dMean> imgstat.max ||  dSD < imgstat.min || dSD> imgstat.max)
			//fit somehow failed
			return (int)(imgstat.mean + 3.0*imgstat.stdDev);
		else
			return (int)(dMean + 3.0*dSD);
		
		
	}

}
