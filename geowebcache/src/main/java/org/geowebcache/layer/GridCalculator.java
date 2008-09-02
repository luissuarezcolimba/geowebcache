/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.util.wms.BBOX;

public class GridCalculator {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.GridCalculator.class);

    // We may want to change this later
    public static final int TILEPIXELS = 256;
    
    //private BBOX gridBounds = null;
    private Grid grid;
    
    // The following are created to save memory. 
    // Note that they can be modified by external code -> not 100% safe!
    private final static double[] RESOLUTIONS4326 = 
        GridCalculator.getResolutionArray(180.0, TILEPIXELS, 26);
    
    private final static double[] RESOLUTIONS900913 = 
        GridCalculator.getResolutionArray(20037508.34*2,TILEPIXELS, 26);
    
    // The following are the width of the actual layer
    private double gridWidth;

    private double gridHeight;
    
    // This is what the grid looks like at zoomlevel 0
    private int gridX = -1;
    
    private int gridY = -1;
    
    private double[] resolutions;
    
    // The following are for a tile, zoomed out all the way
    //private double maxTileWidth;

    //private double maxTileHeight;

    private int zoomStart;

    private int zoomStop;
    
    // Special treatment of "zoomed out tile" for EPSG 4326
    //private boolean worldBoundsCoverTwoTiles = false;

    private int[] zoomedOutGridLoc = null;
    
    private int[][] boundsGridLevels = null;

    public GridCalculator(Grid grid) throws GeoWebCacheException {

        this.grid = grid;
        this.zoomStart = 0;
        this.zoomStop = 25;
        //this.metaWidth = metaWidth;
        //this.metaHeight = metaHeight;

        this.resolutions = grid.resolutions;
        
        //BBOX layerBounds = grid.bounds;
        BBOX gridBounds = grid.gridBounds;
        
        // Calculate
        gridWidth = gridBounds.coords[2] - gridBounds.coords[0];
        gridHeight = gridBounds.coords[3] - gridBounds.coords[1];

        // Figure out the rest
        determineGrid();
        
        boundsGridLevels = calculateGridBounds(grid.dataBounds);
    }
    
    private void determineGrid() throws GeoWebCacheException {
        if(grid.resolutions == null) {
            // Figure out the approriate resolutions
            
            double ratio = gridWidth / gridHeight;
            
            // Allow 2.5% slack
            if(Math.abs(ratio - 1.0) < 0.025) {
                gridX = 1;
                gridY = 1;
            }
            
            // Otherwise we'll try to expand it to an integer grid,
            // failing that we'll just increase the smaller bounds
            // to make the box square
            if(ratio > 1.0) {
                // Wider than tall
                if(Math.abs(ratio - Math.round(ratio)) < 0.025) {
                    gridY = 1;
                    gridX = (int) Math.round(ratio);
                } else {
                    // I give up, expanding Y bounds
                    gridX = gridY = 1;
                    gridHeight = gridWidth;
                }
                determineResolutions();
            } else {
                // Taller than wide
                ratio = gridHeight / gridWidth;
                if(Math.abs(ratio - Math.round(ratio)) < 0.025) {
                    gridY = (int) Math.round(ratio);
                    gridX = 1;
                } else {
                    // I give up, expanding X bounds
                    gridX = gridY = 1;
                    gridWidth = gridHeight;
                }
                determineResolutions();
            }
        } else {
            double denominator = grid.resolutions[0]* GridCalculator.TILEPIXELS;
            this.gridX = (int) Math.round(gridWidth / denominator);
            this.gridY = (int) Math.round(gridHeight / denominator);
        }
    }
    
    private void determineResolutions() throws GeoWebCacheException {
        double baseResolution;
        
        // We use the smaller one 
        if(gridY == 1) {
            baseResolution = gridHeight / GridCalculator.TILEPIXELS;
        } else if (gridX == 1) {
            baseResolution = gridWidth / GridCalculator.TILEPIXELS;
        } else {
            throw new GeoWebCacheException("Unable to find height or width to calculate resolution array.");
        }
        
        this.resolutions = new double[this.zoomStop - this.zoomStart + 1];
        for(int i=this.zoomStart; i<= this.zoomStop; i++) {
            this.resolutions[i] = baseResolution;
            baseResolution = baseResolution / 2;
        }
    }

    private int[][] calculateGridBounds(BBOX layerBounds) {
        BBOX gridBounds = grid.gridBounds;
        
        // We'll just waste a few bytes, for cheap lookups
        int[][] gridLevels = new int[zoomStop + 1][4];

        //int tileCountX = (int) Math.round(gridWidth / resolutions[0]);
        //int tileCountY = (int) Math.round(gridHeight / resolutions[0]);

        //int metaLarger = (metaHeight > metaWidth) ? metaHeight : metaWidth;

        //System.out.println("lb: " +layerBounds+ " base:" +
        //  " tileWidth: " + tileWidth);

        double[] rawNumber = new double[4];
        
        for (int level = 0; level <= zoomStop; level++) {
            //System.out.println("--- Level "+level+"----");
            double tileDelta = resolutions[level] * GridCalculator.TILEPIXELS;
            
            // Min X
            rawNumber[0] = (layerBounds.coords[0] - gridBounds.coords[0]) / tileDelta;
            gridLevels[level][0] = (int) Math.floor(rawNumber[0]);
            
            // Min Y
            rawNumber[1] = (layerBounds.coords[1] - gridBounds.coords[1]) / tileDelta;
            gridLevels[level][1] = (int) Math.floor(rawNumber[1]);
            
            // The gridbounds are defined as inclusive, so they actually cover + 1 
            // compared to the bottom left coordinate -> use floor()
            
            // Max X
            rawNumber[2] = (layerBounds.coords[2] - gridBounds.coords[0] - 0.00001) / tileDelta;
            gridLevels[level][2] = (int) Math.floor(rawNumber[2]);
            
            // Max Y
            rawNumber[3] = (layerBounds.coords[3] - gridBounds.coords[1] - 0.00001) / tileDelta;
            gridLevels[level][3] = (int) Math.floor(rawNumber[3]);

            //System.out.println(Arrays.toString(rawNumber) + " "+ Arrays.toString(gridLevels[level]));
            //System.out.println("postOrig: " +
            //        );
            //
            //System.out.println("tileCountX "+tileCountX + " metaLarger: "
            // + metaLarger);

            //tileCountX = tileCountX * 2;
            //tileCountY = tileCountY * 2;
        }
        return gridLevels;
    }

    public int[] getGridBounds(int zoomLevel) {
        return boundsGridLevels[zoomLevel].clone();
    }

    /**
     * Determines the location in a three dimensional grid based on WMS
     * recommendations.
     * 
     * It creates a grid of (2^zoomLevel x 2^zoomLevel) tiles. 0,0 denotes the
     * bottom left corner. The tile's location in this grid is determined as
     * follows:
     * 
     * <ol>
     * <li>Based on the width of the requested tile the desired zoomlevel is
     * determined.</li>
     * <li>The rounded zoomLevel is used to divide the width into 2^zoomLevel
     * segments</li>
     * <li>The min X value is used to determine the X position on this grid</li>
     * <li>The min Y value is used to determine the Y position on this grid</li>
     * </ol>
     * 
     * @param tileBounds
     *            the bounds of the requested tile
     * @return [0] = x coordinate , [1] y coordinate, [2] = zoomLevel
     */
    public int[] gridLocation(BBOX tileBounds) throws BadTileException {
        int[] retVals = new int[3];

        double reqTileWidth = tileBounds.coords[2] - tileBounds.coords[0];

        //Arrays.binarySearch(a, key)
        //double zoomLevel = Math.log(gridWidth / reqTileWidth) / Math.log(2);
        
        //long roundedZoomLevel = Math.round(zoomLevel);

        
        // (Z) Zoom level
        // For EPSG 4326, reqTileWidth = 0.087 log(4096) / log(2) - 1; -> 11
        retVals[2] = this.binarySearchForResolution(reqTileWidth / GridCalculator.TILEPIXELS);

        double tileWidth = resolutions[retVals[2]] * GridCalculator.TILEPIXELS;

        // Get the bounds
        //BBOX layerBounds = grid.bounds;
        BBOX gridBounds = grid.gridBounds;
        
        // X
        double xdiff = tileBounds.coords[0] - gridBounds.coords[0];
        double xLoc = xdiff / tileWidth;
        retVals[0] = (int) Math.round(xLoc);
        double absdiff = Math.abs(retVals[0] - xLoc);
        if(absdiff/xLoc > 0.05 && absdiff > 0.05) {
            throw new BadTileException("Your bounds in the x direction are offset"
                    + " by more than 5% compared to the underlying grid.");
        }
        
        // Y
        double ydiff = tileBounds.coords[1] - gridBounds.coords[1];
        double yLoc = ydiff / tileWidth;
        retVals[1] = (int) Math.round(yLoc);
        absdiff = Math.abs(retVals[1] - yLoc);
        if(absdiff/yLoc > 0.05 && absdiff > 0.05) {
            throw new BadTileException("Your bounds in the y direction are offset"
                    + " by more than 5% compared to the underlying grid.");
        }

        if (log.isTraceEnabled()) {
            log.trace("x: " + retVals[0] 
                   + "  y: " + retVals[1] 
                   + "  z: " + retVals[2]);
        }

        return retVals;
    }

    public void locationWithinBounds(int[] location) throws OutOfBoundsException {
        // Check Z
        if (location[2] < zoomStart) {
            throw new OutOfBoundsException("zoomlevel (" + location[2] + ") can be at least "
                    + zoomStart);
        }
        if (location[2] >= boundsGridLevels.length) {
            throw new OutOfBoundsException("zoomlevel ("+ location[2] + ") can be at most "
                    + boundsGridLevels.length);
        }

        int[] bounds = boundsGridLevels[location[2]];

        // Check X
        if (location[0] < bounds[0]) {
            throw new OutOfBoundsException("gridX (" + location[0] + ") must be at least " + bounds[0]);
        } else if (location[0] > bounds[2]) {
            throw new OutOfBoundsException("gridX (" + location[0] + ") can be at most " + bounds[2]);
        }

        // Check Y
        if (location[1] < bounds[1]) {
            throw new OutOfBoundsException("gridY (" + location[1] + ") must be at least " + bounds[1]);
        } else if (location[1] > bounds[3]) {
            throw new OutOfBoundsException("gridY (" + location[1] + ") can be at most " + bounds[3]);
        }
    }

    /**
     * Uses the location on the grid to determine bounding box for a single
     * tile.
     * 
     * @param gridLoc
     * @return
     */
    public BBOX bboxFromGridLocation(int[] gridLoc) {
        //double tileWidth = gridWidth / Math.pow(2, gridLoc[2] + gridConstant);
        double tileWidth = resolutions[gridLoc[2]] * GridCalculator.TILEPIXELS;
        
        BBOX gridBounds = grid.gridBounds;
        
        return new BBOX(gridBounds.coords[0] + tileWidth * gridLoc[0],
                gridBounds.coords[1] + tileWidth * gridLoc[1],
                gridBounds.coords[0] + tileWidth * (gridLoc[0] + 1),
                gridBounds.coords[1] + tileWidth * (gridLoc[1] + 1));
    }

    /**
     * Uses the grid bounds to determine the bounding box, presumably for a
     * metatile.
     * 
     * Adds one tilewidth to the top and right.
     * 
     * @param gridBounds
     * @return
     */

    public BBOX bboxFromGridBounds(int[] gridLocBounds) {
        //double tileWidth = gridWidth
        //        / Math.pow(2, gridLocBounds[4] + gridConstant);
        
        double tileWidth = GridCalculator.TILEPIXELS * resolutions[gridLocBounds[4]];
        
        BBOX gridBounds = grid.gridBounds;
        
        return new BBOX(gridBounds.coords[0] + tileWidth * gridLocBounds[0],
                gridBounds.coords[1] + tileWidth * gridLocBounds[1],
                gridBounds.coords[0] + tileWidth * (gridLocBounds[2] + 1),
                gridBounds.coords[1] + tileWidth * (gridLocBounds[3] + 1));
    }

    /**
     * Calculate the extent of the grid for the requested bounds.
     * 
     * It is up to you to verify that these bounds are within the bounds of the layer.
     * 
     * @param requestedBounds bounds for the request
     * @return the corresponding array
     */
    public int[][] coveredGridLevels(BBOX requestedBounds) {
        return calculateGridBounds(requestedBounds);
    }
    
    /**
     * Zooms in one level, returning 4 grid locations like
     * 
     * 0 1
     * 2 3
     * 
     * If a location is outside the bounds then the zoomLevel
     * (third entry) is set to -1
     * 
     * @param gridLoc
     * @return the four underlying tiles
     */
    public int[][] getZoomInGridLoc(int[] gridLoc) {
    	int[][] retVal = new int[4][3];
        
    	int x = gridLoc[0] * 2;
    	int y = gridLoc[1] * 2;
    	int z = gridLoc[2] + 1;
    	
        // Don't link to tiles past the last zoomLevel
        if(z > this.zoomStop) {
            z = -1;
        }
        
    	// Now adjust where appropriate
    	retVal[0][0] = retVal[2][0] = x;
    	retVal[1][0] = retVal[3][0] = x + 1;
    	
    	retVal[0][1] = retVal[1][1] = y;
    	retVal[2][1] = retVal[3][1] = y + 1;

    	retVal[0][2] = retVal[1][2] = retVal[2][2] = retVal[3][2] = z;
    	
    	// Need to check that it doesn't fall outside
    	int[] bounds = boundsGridLevels[z];
    	for(int i=0; i<4; i++) {
    		if( retVal[i][0] < bounds[0] 
    		 || retVal[i][1] < bounds[1]
    		 || retVal[i][0] > bounds[2]
    		 || retVal[i][1] > bounds[3] ) {
    			retVal[i][2] = -1;
    		}
    	}
    	
    	return retVal;
    }
    
    /**
     * Returns the the gridLocation where a single tile
     * covers the entire bounding box
     * 
     * @return the appropriate tile, or {-1,-1,-1} if you need 
     *  world bounds
     */
    public int[] getZoomedOutGridLoc() {
    	if (zoomedOutGridLoc != null) {
            return zoomedOutGridLoc;
        }
    	
    	//TODO fix negative number to return more than one top tile
    	
        // Exception for EPSG:4326, which can zoom out to two tiles
        if (gridX == 2 && gridY == 1) {
            zoomedOutGridLoc = new int[3];
            zoomedOutGridLoc[0] = -1;
            zoomedOutGridLoc[1] = -1;
            zoomedOutGridLoc[2] = -1;

            return zoomedOutGridLoc;
        }
        
        // Otherwise
        int i = boundsGridLevels.length - 1;
        for (; i > 0; i--) {
            if (boundsGridLevels[i][0] == boundsGridLevels[i][2]
                    && boundsGridLevels[i][1] == boundsGridLevels[i][3]) {
                break;
            }
        }

        zoomedOutGridLoc = new int[3];
        zoomedOutGridLoc[0] = boundsGridLevels[i][0];
        zoomedOutGridLoc[1] = boundsGridLevels[i][1];
        zoomedOutGridLoc[2] = i;

        return zoomedOutGridLoc;
    }
    
    public double[] getResolutions() {
        return resolutions;
        //double[] ret = new double[zoomStop - zoomStart + 1];
        //double tileWidth = maxTileWidth / widthPixels;
        
        //for(int i=0; i<ret.length; i++) {
        //    ret[i] = tileWidth;
        //    tileWidth = tileWidth / 2;
       // }
        
        //return ret;
    }
    
    private int binarySearchForResolution(double reqResolution) 
    throws BadTileException {
        return binarySearchForResolution(this.resolutions, reqResolution);
    }
    
    protected static int binarySearchForResolution(double[] resolutions, double reqResolution) 
    throws BadTileException {
        int low = 0;
        int high = resolutions.length - 1;
        
        double reqUpper = reqResolution * 1.10;
        
        double reqLower = reqResolution * 0.90;
        
        // Deal with the edge cases first
        if(reqLower > resolutions[1]) {
            if(resolutions[0] < reqLower) {
                throw new BadTileException("Resolution "+reqResolution+" is too big for grid,"
                        +" biggest supported is " + resolutions[0]);
            }
            return 0;
        }
        
        if(reqUpper < resolutions[high - 1]) {
            if(resolutions[high] / 1.5 > reqUpper) {
                throw new BadTileException("Resolution "+reqResolution+" is too small for grid,"
                        + " smallest supported is " + resolutions[high]);
            }
            return high;
        }
        
        low = 1;
        high = resolutions.length - 2;
        
        while (low <= high) {
            int mid = (low + high) >>> 1;
            
            if(resolutions[mid] > reqUpper) {
                low = mid;
            } else if(resolutions[mid] < reqLower) {
                high = mid;
            } else {
                if(Math.abs(reqResolution - resolutions[mid]) / reqResolution > 1.10) {
                    throw new BadTileException(
                            "The bounds result in a resolution of " + reqResolution 
                            + " but the closes resolution is " + resolutions[mid]);
                }
                return mid;
            }
        }
        throw new BadTileException("Oops. Should never get here.");
    }
    
    public static double[] getResolutionArray(double width, double pixelWidth, int levels) {
        double[] resolutionArray = new double[levels];
        double resolution = width / pixelWidth;
        for(int i=0; i < levels; i++) {
            resolutionArray[i] = resolution;
            resolution = resolution / 2;
        }
        
        return resolutionArray;
    }
    
    public static double[] get900913Resolutions() {
        return GridCalculator.RESOLUTIONS900913;
    }
    
    public static double[] get4326Resolutions() {
        return GridCalculator.RESOLUTIONS4326;
    }
    
}
