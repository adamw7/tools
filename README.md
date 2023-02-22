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
  - open addressing hashmap: a simplier alternative to HashMap based only on one array and double hashing, it implements java.util.Map<K, V>
  
Examples:
In memory check:
```java
		AbstractUniqueness check = new InMemoryUniquenessCheck();
		check.setDataSource(new InMemorySQLDataSource(connection, query));
		Result result = check.exec("COLUM1", "COLUMN2", "COLUMN3");
		log.info(result.isUnique());
		Set<Result> betterOptions = result.getBetterOptions();
		for (Result betterOption : betterOptions) {
			log.info(betterOption);	
		}
```

Notes:
In memory checks are using in memory sources that load all the data once and run multiple recursive checks to find better options.
Iterative (no memory) checks are keeping only one row at the time so they require very tiny heapsize but for the recursive checks need to read the source many times. 
