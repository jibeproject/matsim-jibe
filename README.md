This repository contains a collection of tools and code for network 
conversion, routing, travel diary processing, matrix generation, and accessibility calculations for 
the JIBE and GLASST projects.

The walk and cycle routing algorithms are based largely on code from the "bicycle" MATSim extension which
can be found here: https://github.com/matsim-org/matsim-libs/tree/master/contribs/bicycle.
The extension is also described in this paper: https://doi.org/10.1016/j.procs.2017.05.424

Our calculations for skim matrices and accessibility require efficiently routing to a large number of destinations.
Our code for doing this is based largely on code by the swiss federal railway (SBB) which can be found here:
https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions

The runnable methods are described below.

## Network package (src/main/java/network)

This package contains tools for creating, converting, and writing the MATSim network

### CreateMatsimNetworkRoad.java

Creates a MATSim network (.xml) using the edges and nodes .gpkg files. Any edge attributes to use used in MATSim routing must be specified in this code.

### CreateMatsimNetworkSingleMode.java

Simple method to read in a multi-modal MATSim network and outputs a single-mode matsim network (e.g. car network, walk network, cycle network).

### CreateMatsimNetworkPt.java

Creates a MATSim public transport network and schedule from GTFS data using the pt2matsim extension. Bus trips are mapped to the car network (this can take several days to run). More details on the pt2matsim extension can be found at https://github.com/matsim-org/pt2matsim.

### WriteNetworkGpkg.java

Creates a directed 2-way edges file (.gpkg) using the matsim network given, which whatever attributes that might be useful 
(the attributes to be written can be specified in this code).
This is useful for visualisations in which attributes are different in each direction.
Note that the output from this code is only meant for visualisation, it is not meant to be taken as input anywhere else.

## Trads package (src/main/java/trads)
This package contains everything related to routing trips from the Greater Manchester TRADS travel survey and collecting attributes to expand the trips dataset. The main script is RunTradsAnalysis.java.

## Other methods
### RouteComparison.java

Calculates routes for active travel modes between any number of zones (minimum 2). Zone names are passed in as arguments. 
Can specify one or multiple routing algorithms in this code by defining different travel disutility functions. 
In the current code there are 3 routing possibilities:
- shortest
- fastest
- jibe (our custom jibe algorithm)

Results can be written as either a .gpkg file (with geometries) or a .csv file (without geometries, faster). 

The following postcodes provide an example. These can be passed into the code in arguments 6–11.

M192AN
M139PL
M12PQ
M42DW
M328PR
M85RB
M252SW

### AccessibilityComparison.java

(Work in progress)
