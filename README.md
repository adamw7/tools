# tools

Library of tooling for various purposes.
The main one is data.
It contains:
- data sources
  - support relational data loading
  - in memory and iterative loading
  - CSV, GZip, JDBC support
- uniqueness checks tool 
  - for a given set of data and subset of columns you can ask if these columns are unique
  - the tool also tries to find a better (smaller) answer
  - supports in memory and iterative processing
- data structures
  - open addressing hashmap: a simplier alternative to HashMap based only on one array and double hashing
