// Deliberately no `requires org.junit.*`: the only main source here is this
// descriptor, so junit is test-scoped and unreadable at main-compile time —
// naming it fails the build with "module not found". Surefire patches the test
// classes into this module and adds --add-reads ALL-UNNAMED, so they reach
// junit from the classpath without it being required here.
module io.github.adamw7.tools.data.test {
	requires io.github.adamw7.tools.data;
	requires org.apache.logging.log4j;
}
