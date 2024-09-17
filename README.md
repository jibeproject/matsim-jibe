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

Some code also requires the PT network, which can be generated using src/main/java/network/CreateMatsimNetworkPt.java.

## Network package (src/main/java/network)

This package contains tools for creating, converting, and writing the MATSim network

### CreateMatsimNetworkRoad.java

Creates a MATSim network (.xml) using the edges and nodes .gpkg files.

### CreateMatsimNetworkSingleMode.java

Simple method to produce a single-mode matsim network (e.g. a car, walk, or cycle network).

### CreateMatsimNetworkPt.java

Creates a MATSim public transport network and schedule from GTFS data using the pt2matsim extension.
Bus trips are mapped to the car network (this can take several days to run).
More details on the pt2matsim extension can be found at https://github.com/matsim-org/pt2matsim.

### WriteNetworkGpkg.java

Creates a directed 2-way edges file (.gpkg) based on the matsim network. Includes attributes useful for visualising and debugging.
(additional attributes can be specified in this code).
This is especially useful for visualisations in which attributes are different in each direction.
Note that the output from this code is only meant for visualisation, it is not to be taken as input anywhere else.

## Diary package (src/main/java/diary)
This package contains everything related to routing trips from a travel diary survey.

### RunRouter.java

Calculates routes for diary trips. Can specify one or multiple routing algorithms in this code by defining different travel disutility functions.
(see routing/disutility for custom functions used in this project). Results can be written as either a .gpkg file (with geometries) or a .csv file (without geometries, faster).
Route attributes can be computed by aggregating over all links in each route, using a method from routing/ActiveAttributes.Java (or create your own)
and passing this in as an argument to the router.


### RunCorridors.java

Calculates corridors for each diary trip, using the methodology described in Zhang et al. 2024 (https://doi.org/10.17863/CAM.107588).
This computers all links a person could feasibly use for their journey up to a specified detour treshold. Not this is computationally intensive,
recommend detour threshold does not exceed 10%.

### RunRubberBandingAnalysis.java

Tool for tour-based analysis, to evaluate placement of stops with respect to the home and main activity.
Useful for preparing activity-based models. Requires the 'main' activity to be specified for each trip.

## Accessibility package (src/main/java/accessibility)

This package contains tools and methods for calculating accessibility. See accessibility/README.md for further details.

## Other methods
### gis/CreateGrid.java
For use with grid-based accessibility calculations.
Creates a geopackage of hexagonal grid cells within the region boundary
(specified by the property _region.boundary_ in the properties file).
The desired side length for each cell must be specified as an argument.
