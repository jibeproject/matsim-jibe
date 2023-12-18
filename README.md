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

# General

The relevant inputs, outputs, and parameters are specified in a properties file which is passed in as the first argument to all runnable code. A template for this properties file is located in src/main/java/resources/example.properties. All paths given in this properties file are relative from the working directory which can be specified in each run configuration.

Most code requires the MATSim road network to run. The MATSim road network can be created by running src/main/java/network/CreateMatsimNetworkRoad.java, and it will be saved to the location specified by "matsim.road.network" in the properties file.

Some code also requires the PT network, which can be generated using src/main/java/network/CreateMatsimNetworkPT.java.

## Network package (src/main/java/network)

This package contains tools for creating, converting, and writing the MATSim network

### CreateMatsimNetworkRoad.java

Creates a MATSim network (.xml) using the edges and nodes .gpkg files. Currently valid for version 3.11 of the JIBE Manchester network.

### CreateMatsimNetworkSingleMode.java

Simple method to produce a single-mode matsim network (e.g. a car network, walk network, or cycle network).

### CreateMatsimNetworkPt.java

Creates a MATSim public transport network and schedule from GTFS data using the pt2matsim extension. Bus trips are mapped to the car network (this can take several days to run). Uses the pt2matsim extension. More details on the pt2matsim extension can be found at https://github.com/matsim-org/pt2matsim.

### WriteNetworkGpkg.java

Creates a directed 2-way edges file (.gpkg) based on the matsim network. Includes attributes useful for visualising and debugging.
(additional attributes can be specified in this code).
This is especially useful for visualisations in which attributes are different in each direction.
Note that the output from this code is only meant for visualisation, it is not to be taken as input anywhere else.

## Trads package (src/main/java/diary)
This package contains everything related to routing trips from the Greater Manchester TRADS travel survey and collecting attributes to expand the trips dataset.

### RunTradsAnalysis.java

Uses the MATSim routing engine to estimates key route parameters for each origin-destination pair in the travel survey. 
Important for mode choice model estimation. Outputs a .csv file with one line for each survey record.

### RunTradsRouter.java

Uses the MATSim routing engine to calculate routes for each origin-desination pair for the specified mode (walk and bike only). 
Outputs the short, fast and jibe version of each route as a .gpkg file.

### RunTradsMcRouter.java

Similar to RunTripRouter.java, but uses monte-carlo simulation to sample many different marginal costs for the JIBE disutility function. 
Outputs route data as a .csv file, and corresponding routes as a .gpkg file.

## Accessibility package (src/main/java/accessibility)

This package contains tools and methods for calculating accessibility. 
Unlike zone-based accessibilities, the methods here calculate fully disaggregate accessibilities from every network node to every possible destination. 
We currently support cumulative accessibility (with time and/or distance as a cutoff) and exponential accessibility (with an optional time and/or distance cutoff).

Accessibilities can be calculated for every network node and/or for hexagonal grid cells.
Grid cells can be created using **_gis/grid/CreateGrid.java_**

Accessibility is calculated using the class **_RunAccessibility.java_**. 
As with all other runnable classes, the main properties file must be passed in as the first argument. 
Next, the configuration for each accessibility calculation can be calculated using an accessibility properties file. 
You can pass in as many accessibility properties files as you would like, making it possible to run accessibility back-to-back without restarting the code.

### Accessibility properties file

An example accessibility properties file is given in accessibility/resources/example.properties.

In this file, we specify:
- **Destinations** file path (see below)
- [optional] **Node results** file path (.gpkg)
- [optional] **Grid input** file path (.gpkg, created using _gis/grid/CreateGrid.java_)
- [optional] **Grid results** file path (.gpkg)
- **Mode** (walk, bike, or car)
- **Disutility** (short, fast, or jibe)
- [optional] **Marginal cost overrides** (only applicable if using jibe disutility)
- **decay function** (cumulative or exponential)
- **cutoff** time and/or distance (optional for exponential decay function, but at least one must be specified for cumulative decay)
- **beta** (decay parameter for exponential decay function. If not provided, the code will estimate a beta parameter based on travel survey data for the mode and purposes specified)

Further instructions on specifying accessibility properties are given in the accessibility properties file. 

### Destinations file

The destinations file is a .csv file specifying the destinations to be routed to and their weights. 
It should have the following attributes:
 
 - **ID:** a unique ID for each destination. The ID cannot include commas.
 - **X:** the x-coordinate
 - **Y:** the y-coordinate
 - **WEIGHT:** the weight of each destination. If not provided, we assume all destinations have equal weight of 1.
 
This code also supports destinations with multiple access points (e.g. large green spaces). 
If a destination has multiple access points, include each access point as a separate line in the destinations file, but keep the destination ID the same.
The code will calculate costs to all possible access points but the final accessibility calculation will only consider the access point with the lowest cost from the origin.

## Other methods

### routing/RouteComparison.java

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

### gis/CreateGridCellGpkg.java
For use with grid-based accessibility calculations.
Creates a geopackage of hexagonal grid cells within the region boundary
(specified by the property _region.boundary_ in the properties file).
The desired side length for each cell must be specified as an argument.
