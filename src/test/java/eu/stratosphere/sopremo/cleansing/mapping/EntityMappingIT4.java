package eu.stratosphere.sopremo.cleansing.mapping;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import eu.stratosphere.meteor.MeteorIT;
import eu.stratosphere.sopremo.operator.SopremoPlan;
import eu.stratosphere.sopremo.type.IJsonNode;

/**
 * This test shall introduce new design concepts for the EntityMapping operator.
 * @author fabian
 *
 */

public class EntityMappingIT4 extends MeteorIT {

	@Test @Ignore
	public void testSwitchedOutputs() throws IOException {
		final SopremoPlan plan = parseScript(new File("src/test/resources/MappingIT4.script"));

		this.client.submit(plan, null, true);
		IJsonNode[] personsArray = getContentsToCheckFrom("src/test/resources/MappingIT4TestOutputPersons.json");

		IJsonNode[] leArray = getContentsToCheckFrom("src/test/resources/MappingIT4TestOutputCompanies.json");

		this.testServer.checkContentsOf("MappingIT4TestOutputPersons.json", personsArray);

		this.testServer.checkContentsOf("MappingIT4TestOutputCompanies.json", leArray);
	}
}
