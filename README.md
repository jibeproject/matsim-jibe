# Branch for Valencia GLASST/JIBE Training

This is a training branch of the matsim-jibe repository, containing a selection of code for creating a MATSim network from a geopackage, devloping link-based disutility indicators (e.g. attractiveness, stress) and building different types of routes between origin-destination pairs (e.g. fastest, shortest, JIBE daytime, JIBE nighttime).

Further details on the full code set can be found in the README of the master branch.

This tutorial only considers walking routes and does not include cycling. We will use the following runnable methods:


## Network package (src/main/java/network)

This package contains tools for creating, converting, and writing the MATSim network

### CreateMatsimNetworkRoad.java

Creates a MATSim network (.xml) using the edges and nodes .gpkg files. Any edge attributes to use used in MATSim routing must be specified in this code.

### WriteNetworkGpkg.java

Creates a directed 2-way edges file (.gpkg) using the matsim network given, which whatever attributes that might be useful 
(the attributes to be written can be specified in this code).
This is useful for visualisations in which attributes are different in each direction.
Note that the output from this code is only meant for visualisation, it is not meant to be taken as input anywhere else.

## RouteComparison.java

Calculates walking routes for active travel modes between any number of zones (minimum 2). Zone names are passed in as arguments. 
Can specify one or multiple routing algorithms in this code by defining different travel disutility functions. 
In the current code there are 4 routing possibilities:
- shortest
- fastest
- jibe walk daytime
- jibe walk nighttime

Results can be written as either a .gpkg file (with geometries, for visualising) or a .csv file (without geometries, faster). 

The following postcodes provide an example. These can be passed into the code in arguments 5–6.

BL98LA
M251GJ
