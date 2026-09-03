# Mode choice estimation with latent paths

This repository contains the mode choice estimation tool that uses latent paths to incorporate route-level 
street-environment into walking and cycling utility. It is designed to create empirical models that are straightforward 
to interperet while being effective for forecasting active travel policy in large-scale transport models.   

It is referenced in the paper "Latent paths in mode choice models: incorporating street-level environment features for 
active travel policy" published in Transportation Research Part A: Policy and Practice.

DOI: https://doi.org/10.1016/j.tra.2026.105223

A more technical description of each component can be found in the paper's supplementary materials, located at:
https://ars.els-cdn.com/content/image/1-s2.0-S0965856426003642-mmc1.docx

Key features:
- 📊 Easily interpretable street-level environment coefficients
- ⚡ Efficient implementation for large-scale forecasting
- 👥 Demographic interactions
- 🗺️ Sensitivity to environments along the full trip (not just at origin and destination)
- ❌ No reliance on route choice data (which is often unavailable)

## Required inputs
This code is written in Java and has been tested with the Java 25 SDK

### Properties file
Key inputs and computational parameters are specified in a properties file, including:

- **matsim.network** The road network in MATSim .xml format. This should include the full network for pedestrians and cyclists.
- **network.boundary** A polygon defining the network extent, in .gpkg format
- **number.of.threads** Threads used for computing least-cost paths
- **network.links** (optional for outputting routes) Link geometries in .gpkg format
- **diary.file** Filename of diary file, which should contain origin and destination locations for each trip.
- **diary.delimeter** Delimeter used to separate columns (usually comma or semicolon)
- **diary.household.id** Column name in diary containing household IDs
- **diary.person.id** Column name in diary containing person IDs
- **diary.trip.id** Column name in diary containing trip IDs
- **diary.origin.x** and **diary.origin.y** X and Y coordinates of origin locations
- **diary.destination.x** and **diary.destination.y** X and Y coordinates of destination locations

An example of the properties file is provided in src/main/java/resources/example.properties.

### Logit data file
This file contains the choice dataset and all non-spatial (e.g., sociodemogaphic) predictors. 
At the very least, each record needs a unique identifier and a "choice" column containing the chosen mode.

Every record in the logit dataset should correspond to a record in the diary.
The correspondence is specified using a DiaryReader.IdMatcher object, 
and can be passed in as an argument to the MnlWithRouting.run method
(see the estimation section below)

## Specification
Specifications should implement the class estimation/specifications/AbstractModelSpecification.java.
To estimate with your chosen specification, it needs to be entered as the first argument in run() in 
the estimation/MultinomialLogit.java class.

Simple model specifications applying the SP and RP examples in Apollo (http://www.apollochoicemodelling.com/examples.html).
are presented in estimation/specifications/apollo_examples/. These demonstrate the maximum likelihood estimation 
procedure without spatial information or latent routes. 

The specifications used for the published article are in estimation/specifications/manchester and estimation/specifications/melbourne.

Example main methods are described RunMnlApolloExample.java (for running the above apollo examples)
and RunMnlWithLatentRoutes.java (for running the models published in the article).

### Street-level environment features
Street-level environment features should be defined as attributes of the MATSim links.
These features can then be queried in the specification. 
See specifications/manchester and specifications/melbourne for examples.

The *routing* package contains helpful methods for computing travel time and disutility for active modes, incorporating the street-level environment.
The walk and cycle routing algorithms are based largely on code from the "bicycle" MATSim extension which
can be found here: https://github.com/matsim-org/matsim-libs/tree/master/contribs/bicycle.
The extension is also described in this paper: https://doi.org/10.1016/j.procs.2017.05.424

## Estimation
The estimation currently supports only multinomial logit models estimated using the BGFS algorithm.
It is based on the estimation tools in Smile (https://haifengl.github.io) and Apollo (http://www.apollochoicemodelling.com).

For estimating with latent paths, a useful setup script can be found in estimation/MnlWithRouting.java.
In addition to estimation, this script also includes functionality for custom starting values, prediction, and writing latent routes.

For a quick estimation without latent routes, the estimation can be also run using pre-calculated initial route data from the previous run.
This can be enabled by setting the computeRouteData argument to false in MnlWithRouting.run().

### Handling matching origin and destination nodes

Matching origin and destination nodes can happen for very short trips.
For these trips, paths cannot be computed, so travel time and street environment predictors are static instead of latent. 

For these trips, travel time is estimated using the "dist" attribute in the choice dataset, divided by average speed.
These average speeds can be customised using the *avg.walk.speed* and *avg.bike.speed* properties.

Their street-level environment predictors are computed in one of two ways:
1) If the diary contains origin and destination zone columns and a zone polygons is provided, then take an average of all links in that zone. In this case the folllowing properties must be specified:
- **zone.system** Zone polygons in .gpkg format
- **zone.id** Attribute name in zone system corresponding to diary zone IDs
- **diary.origin.zone** Column name in diary containing origin zone IDs
- **diary.destination.zone** Column name in diary containing destination zone IDs
2) If no zone system if provided, then it uses the link(s) closest to the original origin and destination coordinates.

This process is coded into the latent router in estimation/latent/LatentRouter.java,
specifically the computeVeryShortTripData method.