package io.github.adamw7.tools.code.usecase;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.code.ComputerBuilder;
import io.github.adamw7.tools.code.test.Computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GeneretedCodeTest {

	
	
	@Test
	public void happyPath() {
		ComputerBuilder builder = new ComputerBuilder();
		Computer computer = builder.setId(5).setName("Desktop").build();
		assertNotNull(computer);
		assertEquals("Desktop", computer.getName());
		assertEquals(5, computer.getId());
	}
}
