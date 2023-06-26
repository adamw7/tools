package io.github.adamw7.tools.code.usecase;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.code.ComputerBuilder;
import io.github.adamw7.tools.code.ComputerOptionalIfc;
import io.github.adamw7.tools.code.test.Computer;

public class GeneretedCodeTest {

	@Test
	public void happyPath() {
		ComputerBuilder builder = new ComputerBuilder();
		ComputerOptionalIfc computerOptional = builder.setId(5);
		assertNotNull(computerOptional);
		Computer computer = computerOptional.setName("Desktop").build();
		assertNotNull(computer);
		assertEquals("Desktop", computer.getName());
		assertEquals(5, computer.getId());
		
		assertTrue(computerOptional.hasName());
		assertTrue(builder.hasId());
		
		computerOptional.clearName();
		
		assertFalse(computerOptional.hasName());
				
		assertEquals("", computerOptional.build().getName());
	}
}
