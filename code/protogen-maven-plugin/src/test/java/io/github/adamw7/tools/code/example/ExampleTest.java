package io.github.adamw7.tools.code.example;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.code.protos.Person;
import io.github.adamw7.tools.code.protos.Person.Builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

interface OptionalIfc {
	OptionalIfc setEmail(String email);
	OptionalIfc setName(String name);
	Person build();
}

interface DepartmentIfc {
	OptionalIfc setDepartment(String department);
}

interface IdIfc {
	DepartmentIfc setId(int id);
}

class OptionalImpl implements OptionalIfc {
	
	private final Builder builder;

	public OptionalImpl(Builder builder) {
		this.builder = builder;
	}

	@Override
	public OptionalIfc setEmail(String email) {
		builder.setEmail(email);
		return this;
	}

	@Override
	public OptionalIfc setName(String name) {
		builder.setName(name);
		return this;
	}

	@Override
	public Person build() {
		return builder.build();
	}
}

class DepartmentImpl implements DepartmentIfc {

	private final Builder personOrBuilder;

	public DepartmentImpl(Builder personOrBuilder) {
		this.personOrBuilder = personOrBuilder;
	}

	@Override
	public OptionalIfc setDepartment(String department) {
		personOrBuilder.setDepartment(department);
		return new OptionalImpl(personOrBuilder);
	}	
}

public class ExampleTest {
	
	private static class PersonBuilderExample implements IdIfc {
		private final Builder personBuilder = Person.newBuilder();
		
		@Override
		public DepartmentIfc setId(int id) {
			personBuilder.setId(id);
			return new DepartmentImpl(personBuilder);
		}
	}
	
	@Test
	public void happyPath() {
		PersonBuilderExample builder = new PersonBuilderExample();
		Person person = builder.setId(1).setDepartment("dep").setEmail("sth@sth.net").setName("Adam").build();
		assertEquals(1, person.getId());
		assertEquals("dep", person.getDepartment());
		assertEquals("sth@sth.net", person.getEmail());
		assertEquals("Adam", person.getName());
		
	}
}
