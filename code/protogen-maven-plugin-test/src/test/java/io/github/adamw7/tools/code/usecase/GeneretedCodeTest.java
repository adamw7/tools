package io.github.adamw7.tools.code.usecase;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.output.generated.AccountBuilder;
import org.output.generated.AccountOptionalIfc;
import org.output.generated.CarBuilder;
import org.output.generated.ComputerBuilder;
import org.output.generated.ComputerOptionalIfc;
import org.output.generated.GroupBuilder;
import org.output.generated.GroupingBuilder;
import org.output.generated.MeasurementBuilder;
import org.output.generated.MyMessageBuilder;
import org.output.generated.OneOptionalFieldOnlyBuilder;
import org.output.generated.ProfileBuilder;
import org.output.generated.ServerBuilder;
import org.output.generated.TeamBuilder;
import org.output.generated.UserBuilder;
import org.output.generated.WheelBuilder;

import com.google.protobuf.ByteString;

import io.github.adamw7.tools.code.foo.Foo;
import io.github.adamw7.tools.code.test.Account;
import io.github.adamw7.tools.code.test.Car;
import io.github.adamw7.tools.code.test.Computer;
import io.github.adamw7.tools.code.test.Extension;
import io.github.adamw7.tools.code.test.Grouping;
import io.github.adamw7.tools.code.test.Measurement;
import io.github.adamw7.tools.code.test.MyMessage;
import io.github.adamw7.tools.code.test.Server;
import io.github.adamw7.tools.code.test.Wheel;
import io.github.adamw7.tools.code.test4.Profile;
import io.github.adamw7.tools.code.test4.Team;

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

	@Test
	public void multipleRequiredFields() {
		ServerBuilder builder = new ServerBuilder();
		Server server = builder.setHost("localhost").setPort(8080).setSecure(true).setDescription("primary").build();
		assertNotNull(server);
		assertEquals("localhost", server.getHost());
		assertEquals(8080, server.getPort());
		assertTrue(server.getSecure());
		assertEquals("primary", server.getDescription());
		assertTrue(builder.hasHost());
	}

	@Test
	public void requiredFieldsWithoutOptional() {
		Wheel wheel = new WheelBuilder().setSize(15).build();
		assertNotNull(wheel);
		assertEquals(15, wheel.getSize());
	}

	@Test
	public void messageTypedRequiredField() {
		Wheel wheel = new WheelBuilder().setSize(17).build();
		Car car = new CarBuilder().setWheel(wheel).setModel("Sedan").build();
		assertNotNull(car);
		assertEquals(17, car.getWheel().getSize());
		assertEquals("Sedan", car.getModel());
	}

	@Test
	public void scalarTypeMappings() {
		MeasurementBuilder builder = new MeasurementBuilder();
		Measurement measurement = builder.setCount(42L).setRatio(1.5).setWeight(2.5f).setEnabled(true)
				.setPayload(ByteString.copyFromUtf8("data")).setSmall(7).setBig(99L).setDelta(-3)
				.setUnit(Measurement.Unit.METRIC).build();
		assertNotNull(measurement);
		assertEquals(42L, measurement.getCount());
		assertEquals(1.5, measurement.getRatio());
		assertEquals(2.5f, measurement.getWeight());
		assertTrue(measurement.getEnabled());
		assertEquals("data", measurement.getPayload().toStringUtf8());
		assertEquals(7, measurement.getSmall());
		assertEquals(99L, measurement.getBig());
		assertEquals(-3, measurement.getDelta());
		assertEquals(Measurement.Unit.METRIC, measurement.getUnit());
	}

	@Test
	public void extendableMessageBuilds() {
		AccountBuilder builder = new AccountBuilder();
		AccountOptionalIfc optional = builder.setOwner("alice");
		assertNotNull(optional);
		Account account = optional.setLabel("primary").build();
		assertNotNull(account);
		assertEquals("alice", account.getOwner());
		assertEquals("primary", account.getLabel());
		assertTrue(builder.hasOwner());
	}

	@Test
	public void extensionsRemainSettableOnBuiltMessage() {
		Account account = new AccountBuilder().setOwner("bob").build();
		Account extended = account.toBuilder().setExtension(Extension.priority, 7)
				.setExtension(Extension.note, "vip").build();
		assertEquals(7, extended.getExtension(Extension.priority));
		assertEquals("vip", extended.getExtension(Extension.note));
	}

	@Test
	public void customOptionsArePreservedOnBuiltMessage() {
		MyMessageBuilder builder = new MyMessageBuilder();
		MyMessage message = builder.setId(11).setName("widget").build();
		assertNotNull(message);
		assertEquals(11, message.getId());
		assertEquals("widget", message.getName());

		String messageOption = MyMessage.getDescriptor().getOptions().getExtension(Foo.myOption);
		assertEquals("Hello world!", messageOption);

		String fieldOption = MyMessage.getDescriptor().findFieldByName("name").getOptions()
				.getExtension(Foo.myFieldOption);
		assertEquals("field option value", fieldOption);
	}

	@Test
	public void clearOptionalFieldReturnsBuilder() {
		MeasurementBuilder builder = new MeasurementBuilder();
		builder.setEnabled(true);
		assertTrue(builder.hasEnabled());
		assertFalse(builder.clearEnabled().build().getEnabled());
	}

	@Test
	public void proto3MessageWithoutRequiredFieldsBuilds() {
		Profile profile = new ProfileBuilder().setUsername("neo").setNickname("the one").setAge(30).build();
		assertNotNull(profile);
		assertEquals("neo", profile.getUsername());
		assertEquals("the one", profile.getNickname());
		assertEquals(30, profile.getAge());
	}

	@Test
	public void proto3ExplicitOptionalFieldTracksPresence() {
		ProfileBuilder builder = new ProfileBuilder();
		builder.setNickname("trinity");
		assertTrue(builder.hasNickname());
		assertFalse(builder.clearNickname().build().hasNickname());
	}

	@Test
	public void proto3ImplicitFieldDefaultsToZeroValue() {
		Profile profile = new ProfileBuilder().build();
		assertNotNull(profile);
		assertEquals("", profile.getUsername());
		assertEquals(0, profile.getAge());
		assertFalse(profile.hasNickname());
	}

	@Test
	public void proto3MessageFieldAndRepeatedFieldBuild() {
		Profile lead = new ProfileBuilder().setUsername("morpheus").build();
		Team team = new TeamBuilder().setMembers(java.util.List.of("neo", "trinity")).setLead(lead).build();
		assertNotNull(team);
		assertEquals("morpheus", team.getLead().getUsername());
		assertEquals(java.util.List.of("neo", "trinity"), team.getMembersList());
		assertTrue(team.hasLead());
	}

}
