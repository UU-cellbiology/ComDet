package fiji.plugin.ComDet;

import java.util.Arrays;
import java.util.Comparator;

import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

class tableComparator implements Comparator<double []> {
    private int columnToSortOn;
    private boolean ascending;
   
    //contructor to set the column to sort on.
    tableComparator(int columnToSortOn, boolean ascending) {
      this.columnToSortOn = columnToSortOn;
      this.ascending = ascending;
    }

    // Implement the abstract method which tells
    // how to order the two elements in the array.
    public int compare(double [] o1, double [] o2) {
        double[] row1 = (double[])o1;
        double[] row2 = (double[])o2;
        int res;
        if(row1[columnToSortOn]==row2[columnToSortOn])
        	return 0;
        if(row1[columnToSortOn]>row2[columnToSortOn])
        	res = 1;
        else
        	res = -1;
        if (ascending)
        	return res;
        else 
        	return (-1)*res;
               
        
    }
}

public class Sort_Results_CD implements PlugIn {

	CDAnalysis sml = new CDAnalysis(1);
	
	@Override
	public void run(String arg) {
		
		int i;
		int nCount;
		int nTotalColumns;
		int nTotalColumnsInclHidden;
		boolean bOrder;
		//asking user for sorting criteria
		GenericDialog dgSortParticles = new GenericDialog("Sort Particles");
		String [] SortColumn;// = new String [] {
				//"Amplitude_fit","X_(px)","Y_(px)","False_positive","X_loc_error_(px)","Y_loc_error_(px)","IntegratedInt","SNR","Frame Number"};
		int Colindex;
		String [] SortOrder = new String [] {
				"Ascending","Descending"};
		int Sortindex;
		nTotalColumnsInclHidden=sml.ptable.getLastColumn()+1;
		nTotalColumns = 0; //sml.ptable.getLastColumn()+1;
		
		for(i=0;i<nTotalColumnsInclHidden;i++)
		{
			if(sml.ptable.columnExists(i))
				nTotalColumns++;
			
		}
		if (nTotalColumnsInclHidden <=0 )
		{
			IJ.error("There is no Results table open. Sorry.");
			return;						
		}
		SortColumn = new String [nTotalColumns];
		nCount=0;
		//filling
		for(i=0;i<nTotalColumnsInclHidden;i++)
		{
			if(sml.ptable.columnExists(i))
			{
				SortColumn[nCount] = sml.ptable.getColumnHeading(i);
				nCount++;
			}
		}
		
		dgSortParticles.addChoice("Sort by column:", SortColumn, Prefs.get("SiMoLoc.SortColumn", SortColumn[0]));
		dgSortParticles.addChoice("Sorting order:", SortOrder, Prefs.get("SiMoLoc.SortOrder", "Ascending"));
		
		dgSortParticles.showDialog();
		if (dgSortParticles.wasCanceled())
            return;
		Colindex = dgSortParticles.getNextChoiceIndex();
		Sortindex = dgSortParticles.getNextChoiceIndex();
		Prefs.set("SiMoLoc.SortColumn", SortColumn[Colindex]);
		Prefs.set("SiMoLoc.SortOrder", SortOrder[Sortindex]);

		if (Sortindex == 0)
			bOrder = true;
		else 
			bOrder = false;
		sorting(Colindex,bOrder);
		
	}
	
	public void sorting(int nSortingColumn, boolean ascending) 
	{
		int colNumber,nTotalColumnsInclHidden;
		int len;
		int i,j;
		
		nTotalColumnsInclHidden = sml.ptable.getLastColumn()+1;
		colNumber=0;
		for(i=0;i<nTotalColumnsInclHidden;i++)
		{
			if(sml.ptable.columnExists(i))
				colNumber++;
			
		}

		double [] s = sml.ptable.getColumnAsDoubles(0);
		len = s.length;
		double [][] data = new double[len][colNumber];
		
		IJ.showStatus("Sorting Results Table: Preparation...");
		colNumber=0;
		for (i=0; i<nTotalColumnsInclHidden;i++)
		{
			if(sml.ptable.columnExists(i))
			{
				
				s = sml.ptable.getColumnAsDoubles(i);
				for(j=0; j<len;j++)
					data[j][colNumber]= s[j];
				colNumber++;
			}
		}
		IJ.showStatus("Sorting Results Table: Sorting...");
		Arrays.sort(data, new tableComparator(nSortingColumn, ascending));
		
		IJ.showStatus("Sorting Results Table: Updating Table..."); 		
		colNumber=0;
		
		for (i=0;i<nTotalColumnsInclHidden;i++)
			if(sml.ptable.columnExists(i))
			{
				for(j=0;j<len;j++)
					sml.ptable.setValue(i, j, data[j][colNumber]);
				colNumber++;
			}	
		//sml.showTable();
		sml.ptable.show("Results");
		
	}
	
	/** Function that sorts ResultsTable (used by external calls)
	 * @param smlext CDanalysis object containing a table 
	 * @param nSortingColumn number of column to sort
	 * @param descending order of sorting**/
	public static void sorting_external_silent(CDAnalysis smlext, int nSortingColumn, boolean ascending) 
	{
		int colNumber;
		int len;
		int i,j;
		
		colNumber = smlext.ptable.getLastColumn()+1;

		double [] s = smlext.ptable.getColumnAsDoubles(0);
		len = s.length;
		double [][] data = new double[len][colNumber];
		
		//IJ.showStatus("Sorting Results Table: Preparation...");
		for (i=0; i<colNumber;i++)
		{
			s = smlext.ptable.getColumnAsDoubles(i);
			for(j=0; j<len;j++)
			data[j][i]= s[j];
		}
		//IJ.showStatus("Sorting Results Table: Sorting...");
		Arrays.sort(data, new tableComparator(nSortingColumn, ascending));
		
		//IJ.showStatus("Sorting Results Table: Updating Table..."); 		
		for (i=0;i<colNumber;i++)
			for(j=0;j<len;j++)
				smlext.ptable.setValue(i, j, data[j][i]);		
		//smlext.showTable();
		
	}
	
}
