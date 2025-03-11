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

Most code requires the MATSim vehicle/bike/walk network to run. This network can be created with src/main/java/network/CreateMatsimNetwork[area].java, and it will be saved to the location specified by "matsim.road.network" in the properties file.

Some code also requires the PT network, which can be generated using src/main/java/network/CreateMatsimNetworkPt.java.

## Network package (src/main/java/network)

This package contains tools for creating, converting, and writing the MATSim network

### CreateMatsimNetworkManchester.java

Creates the MATSim network for the Greater Manchester study area. Requires nodes and edges files in .gpkg format.

### CreateMatsimNetworkMelbourne.java

Creates the MATSim network for the Greater Melbourne study area. Requires nodes and edges files in .gpkg format.

### WriteNetworkXmlSingleMode.java

Writes a single-mode matsim network (e.g. car, walk, or cycle network).

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
This computes all links a person could feasibly use for their journey up to a specified detour threshold. Not this is computationally intensive,
recommend detour threshold does not exceed 10%.

### RunRubberBandingAnalysis.java

Tool for tour-based analysis, to evaluate placement of stops with respect to the home and main activity.
Useful for preparing activity-based models. Requires the 'main' activity to be specified for each trip.

## Accessibility package (src/main/java/accessibility)

This package contains tools and methods for calculating accessibility. See accessibility/README.md for further details.

## Skim package (src/main/java/skim)
Package for generating skim matrices for MITO in .omx format

### RunSkims.java
Creates various skims for car, bike, and walk.
Includes purpose-specific generalised time skims for MITO.

### RunSkimsPt.java
Creates skim matrices for public transport

## Estimation package (src/main/java/estimation)
This package contains a tool for empirical mode choice estimation incorporating the street-level environment.
The estimation currently supports only multinomial logit models estimated using the BGFS algorithm.
This is based on the estimation tools in Smile (https://haifengl.github.io) and Apollo (http://www.apollochoicemodelling.com).

### RunMnl.java
Example model applying the SP and RP examples in Apollo (http://www.apollochoicemodelling.com/examples.html). 
 results should match the Apollo examples

### RunMnlManchester.java
Script to run models for Manchester. The estimation requires two diary datasets.
First, the dataset containing fixed non-route-based predictors (e.g., sociodemographic attributes). 
Second, the dataset used for routing (see Routing package), includes origin and destination locations for each trip.
The fixed predictors dataset is passed in as an argument, while the routing dataset is specified in the _diary.file_ property. 
The network must also be specified in the _matsim.road.network_ property.
Every record in the travel diary should correspond to a record in the routing dataset 
(based on the ID variable in the predictor dataset and the household/person/trip ID in the routing dataset, see lines 70-76).

Dynamic updating of route estimates can be enabled or disabled using _ENABLE_DYNAMIC_ROUTING_ in dynamic/RouteDataDynamic.java (line 37).
Otherwise, the fastest route is computed once and used over all iterations of the estimation.

For a quick estimation, the routine can be also run with pre-calculated fastest route data from the previous run.
This can be enabled by setting _COMPUTE_ROUTE_DATA_ to false in RunMnlManchester.java (line 28).
This quicker option is not possible with dynamic updating.

## Demand package
Package for setting up and running MATSim simulation using travel demand data provided by TfGM in OMX format.
The most important methods are

### GenerateManchesterPlans.java
Creates a MATSim plans file using origin-destination demand data from TfGM's transport model.

### RunSimulation.java
Runs the simulation. The exampleConfig.xml and exampleVehicles.xml are useful templates for this.

## Other methods
### gis/CreateGrid.java
For use with grid-based accessibility calculations.
Creates a geopackage of hexagonal grid cells within the region boundary
(specified by the property _region.boundary_ in the properties file).
The desired side length for each cell must be specified as an argument.
