**[Work in progress]** This repository contains a collection of tools and code for routing, matrix generation, and accessibility calculations for JIBE.

The routing algorithms are based largely on code by the swiss federal railway (SBB) which can be found here:
https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions

##Runnable methods:
###network/CreateMatsimNetworkRoad.java

Creates a MATSim network (.xml) using the edges and nodes .gpkg files. The attributes to be brought over from the edges
file to MATSim must be defined in this code.

###network/WriteDirectedNetwork.java

Creates a directed 2-way edges file (.gpkg) using the matsim network given, which whatever attributes that might be useful 
(the attributes to be written can be specified in this code).
This is useful for visualisations in which attributes are different in each direction.

###RouteComparison.java

Calculates the routes between any number of zones (minimum 2). Zone names are passed in as arguments. 
Can specify one or multiple routing algorithms in this code by defining different travel disutility functions. 
In the current code there are 4 routing possibilities:
- shortest distance
- fastest time
- berlin (default form the MATSim bicycle extension)
- jibe (an example custom algorithm which considers infrastructure quality, surface, ndvi, gradient, and traffic speed)

The routes are written to a .gpkg file along with some attributes (e.g. distance, travel time, cost) which can be defined within the code.

You can try the following postcodes as an example. These can be passed into the code in arguments 6–11.

M192AN
M139PL
M12PQ
M42DW
M328PR
M85RB
M252SW