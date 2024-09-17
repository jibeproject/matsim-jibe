# Accessibility Package

This package contains tools and methods for calculating active mode accessibilities using micro-scale network and land use data.
Accessibilities can be computed for multiple scales including:
- Fully disaggregate (e.g., specific dwellings)
- Zones (e.g., transport analysis zones or grid cells, which can be created using gis/CreateGrid.java)

Accessibility is computed using the class **_RunAnalysis.java_**.

As with all other runnable classes, the main properties file must be passed in as the first argument. 
This describes the network, coordinate system, study area boundary, network boundary, number of threads, 
default marginal cost parameters (applicable for composite disutiltiy incorporating the street-level environment),
maximum cycling speed (applicable for non-distance bike disutilities) 
and travel diary details (applicable when estimating decay parameters using a travel diary).
See ../resources/example.properties for an example.

The accessibility calculations are configured using accessibility properties files.
Multiple accessibility properties files can be prepared and passed in as arguments (starting from the second argument), 
making it possible to run accessibility analyses back-to-back without manually restarting the code.

## Accessibility properties file

The properties file defines the inputs, outputs, and accessibility specification including the mode, impedance, decay function, and desired cutoff:

In this file, we specify:
- **end.coords.[i]** End point (destination) coordinates. Can specify multiple, using [i] as an integer starting from 0. See following section for details.
- **end.description.[i]** Short end point (destination) description, without spaces, corresponding to each coordinate file (above). Used to differentiate destinations in attribute name when results are written. 
- **end.alpha.[i]** Power transform for destination weights, corresponding to each coordinate file (above). For no power transform, set to 1.
- **input** Input file in .gpkg formt. Can either be points (for fully disaggregate analysis) or polygons (for zone-based analysis).
- **output** Output file in .gpkg format. The output will be an extended version of the input file, containing additional attributes with the accessibility results.
- **mode** walk or bike
- **disutility** desired impedance function. Can specify shortest, fastest, or composite (currently includes jibe_day and jibe_night).
- (OPTIONAL) **mc.[type]** Overrides default marginal costs associated with each built environment feature type (only relevant for composite disutilty).
- (OPTIONAL) **forward** Set to _true_ to compute impedance in the forward direction, _false_ for the reverse direction, or comment out to compute in both directions.
- **decay function** Cumulative, exponential, gaussian, power, or cumulative gaussian. Must also include corresponding parameters:
  - _cumulative_: specify either **cutoff.time** (seconds) or **cutoff.distance** (metres)
  - _exponential_: specify **beta** (decay parameter). Alternatively, leave blank to estimate beta using the travel diary
  - _gaussian_: specify **v** (variance parameter)
  - _power_: specify **a** (alpha parameter)
  - _cumulative gaussian_: specify **a** (acceptable impedance) and **v** (variance parameter). Alternatively, for non-distance impedance functions, can estimate these parameters using the travel diary and desired distance-based decay by specifying: 
    - **acceptable.dist**: Acceptable distance to match, on average, the acceptable non-distance impedance
    - **decay.dist** and **decay.value**: Distance (exceeding acceptable distance) and desired decay (between 0–1) at this distance, which will match the decay for the non-distance impedance function on average
  - Note that **cutoff.time** and/or **cutoff.distance** may be specified for all decay functions to reduce computational burden, but it is only required when a cumulative decay function is specified.
  - Note that parameter estimation based on travel diary data requires diary details to be included in the main properties file (see ../resources/example.properties). 

Further instructions on specifying accessibility properties are given in the accessibility properties file (resources/analysisExample.properties).

### End coords file

These are is a .csv file, seperated with a semicolon (;) specifying the destinations to be routed to and their weights.
They should have the following attributes:

- **ID:** a unique ID for each destination.
- **X:** the x-coordinate
- **Y:** the y-coordinate
- **WEIGHT:** the weight of each destination. If not provided, we assume all destinations have equal weight of 1.

The coordinate system is specified in the main proerties file (see ../resources/example.properties). The coordinate system used for destinations must match the coordinate system used for the transport network.

This code also supports destinations with multiple access points (e.g. large green spaces).
If a destination has multiple access points, include each access point coordinates as a separate line in the destinations file, but keep their destination ID the same.
The code will calculate costs to all possible access points but the final accessibility calculation will only consider the access point with the lowest cost from the origin.
