package io.github.adamw7.tools.code;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.code.protos.Person;
import io.github.adamw7.tools.code.protos.Person.Builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExampleTest {

	static interface RestIfc {
		RestIfc setEmail(String email);
		RestIfc setName(String name);
		Person build();
	}
	
	static class DepartmentImpl implements DepartmentIfc {

		private final Builder personOrBuilder;

		public DepartmentImpl(Builder personOrBuilder) {
			this.personOrBuilder = personOrBuilder;
		}

		@Override
		public RestIfc setDepartment(String department) {
			personOrBuilder.setDepartment(department);
			return new RestImpl(personOrBuilder);
		}
		
	}
	
	static interface DepartmentIfc {
		RestIfc setDepartment(String department);
	}
	
	static interface IdIfc {
		DepartmentIfc setId(int id);
	}
	
	static class RestImpl implements RestIfc {
		
		private final Builder builder;

		public RestImpl(Builder builder) {
			this.builder = builder;
		}

		@Override
		public RestIfc setEmail(String email) {
			builder.setEmail(email);
			return this;
		}

		@Override
		public RestIfc setName(String name) {
			builder.setName(name);
			return this;
		}

		@Override
		public Person build() {
			return builder.build();
		}
		
	}
	
	private static class PersonBuilder implements IdIfc {
		private final Builder personOrBuilder = Person.newBuilder();
		
		public PersonBuilder() {
			
		}

		@Override
		public DepartmentIfc setId(int id) {
			personOrBuilder.setId(id);
			return new DepartmentImpl(personOrBuilder);
		}
	}
	
	@Test
	public void happyPath() {
		PersonBuilder builder = new PersonBuilder();
		Person person = builder.setId(1).setDepartment("dep").setEmail("sth@sth.net").setName("Adam").build();
		assertEquals(1, person.getId());
		assertEquals("dep", person.getDepartment());
		assertEquals("sth@sth.net", person.getEmail());
		assertEquals("Adam", person.getName());
		
	}
}