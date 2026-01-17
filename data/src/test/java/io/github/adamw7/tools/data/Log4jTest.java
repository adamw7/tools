package io.github.adamw7.tools.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Log4jTest {

    private static final Logger logger = LogManager.getLogger(Log4jTest.class);

    @Test
    void testLog4jWorks() {
        assertNotNull(logger, "Logger should not be null");

        logger.info("Log4j is working!");
        logger.debug("Debug message");
        logger.warn("Warning message");
        logger.error("Error message");
    }
}
