package io.github.adamw7.tools.code.usecase;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.output.generated.ComputerBuilder;
import org.output.generated.ComputerOptionalIfc;
import org.output.generated.GroupBuilder;
import org.output.generated.GroupingBuilder;
import org.output.generated.OneOptionalFieldOnlyBuilder;
import org.output.generated.UserBuilder;

import io.github.adamw7.tools.code.test.Computer;
import io.github.adamw7.tools.code.test.Grouping;

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
	
	@Test
	public void userTest() {
		assertNotNull(new UserBuilder().setNumber(7).setValue("val").build());
	}
	
	@Test
	public void oneOptionalFieldTest() {
		OneOptionalFieldOnlyBuilder builder = new OneOptionalFieldOnlyBuilder();
		assertNotNull(builder.setValue("V").build());
	}
	
	@Test
	public void groups() {
		GroupingBuilder builder = new GroupingBuilder();
		Grouping grouping = builder.setGroup(new GroupBuilder().setActive(true).build()).build();
		assertNotNull(grouping);
		assertTrue(grouping.getGroup().getActive());
	}
	
}
