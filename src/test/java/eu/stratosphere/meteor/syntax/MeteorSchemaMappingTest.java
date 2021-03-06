/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.meteor.syntax;

import org.junit.Test;

import eu.stratosphere.meteor.MeteorParseTest;
import eu.stratosphere.meteor.QueryParser;
import eu.stratosphere.sopremo.expressions.ArrayCreation;
import eu.stratosphere.sopremo.expressions.BooleanExpression;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.io.Sink;
import eu.stratosphere.sopremo.io.Source;
import eu.stratosphere.sopremo.operator.CompositeOperator;
import eu.stratosphere.sopremo.operator.InputCardinality;
import eu.stratosphere.sopremo.operator.Name;
import eu.stratosphere.sopremo.operator.Operator;
import eu.stratosphere.sopremo.operator.OutputCardinality;
import eu.stratosphere.sopremo.operator.Property;
import eu.stratosphere.sopremo.operator.SopremoModule;
import eu.stratosphere.sopremo.operator.SopremoPlan;
import eu.stratosphere.sopremo.query.AdditionalInfoResolver;
import eu.stratosphere.sopremo.query.IConfObjectRegistry;

/**
 * 
 */
public class MeteorSchemaMappingTest extends MeteorParseTest {
	
	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.meteor.MeteorParseTest#initParser(eu.stratosphere.meteor.QueryParser)
	 */
	@Override
	protected void initParser(QueryParser queryParser) {
		final IConfObjectRegistry<Operator<?>> operatorRegistry = queryParser.getPackageManager().getOperatorRegistry();
		operatorRegistry.put(SchemaMapping.class, new AdditionalInfoResolver.None());
		super.initParser(queryParser);
	}
	
	private SopremoPlan getExpectedPlan() {
		
		final SopremoPlan expectedPlan = new SopremoPlan();
		final Source input1 = new Source("file://usCongressMembers.json");
		final Source input2 = new Source("file://usCongressBiographies.json");
		final SchemaMapping extract = new SchemaMapping().withInputs(input1, input2);
		final Sink output1 = new Sink("file://person.json").withInputs(extract.getOutput(0));
		final Sink output2 = new Sink("file://legalEntity.json").withInputs(extract.getOutput(1));
		expectedPlan.setSinks(output1, output2);
		
		return expectedPlan;
	}
	
	@Test
	public void testMinimalSchemaMapping() {
		String query = "$usCongressMembers = read from 'file://usCongressMembers.json';\n" +
			"$usCongressBiographies = read from 'file://usCongressBiographies.json';\n" +
			"$person, $legalEntity = map entities from $usCongressMembers, $usCongressBiographies\n" + 
			"as [\n" +
			"  group $usCongressMembers by $usCongressMembers.id_o into {" + 
			"    name_p: $usCongressMembers.name_o,\n" +
			"    worksFor_p: $usCongressMembers.id_o" + 
			"  }," + 
			"  group $usCongressBiographies by $usCongressBiographies.worksFor_o into {" + 
			"    name_l: $usCongressBiographies.worksFor_o" + 
			"  }" + 
			"];\n" + 
			"write $person to 'file://person.json';\n" +
			"write $legalEntity to 'file://legalEntity.json';";

		final SopremoPlan actualPlan = parseScript(query);
		final SopremoPlan expectedPlan = getExpectedPlan();

		assertPlanEquals(expectedPlan, actualPlan);
	}
	
//	@Test
	public void testRenamedOperator() { // map entities from ... as ...
		String query = "$usCongressMembers = read from 'file://usCongressMembers.json';\n" +
			"$usCongressBiographies = read from 'file://usCongressBiographies.json';\n" +
			"$person, $legalEntity = map entities from $usCongressMembers, $usCongressBiographies\n" + 
			"as [\n" +
			"  group $usCongressMembers by $usCongressMembers.id_o into {" + 
			"    name_p: $usCongressMembers.name_o,\n" +
			"    worksFor_p: $usCongressMembers.id_o" +
			"  }," + 
			"  group $usCongressBiographies by $usCongressBiographies.worksFor_o into {" + 
			"    name_l: $usCongressBiographies.worksFor_o" + 
			"  }" + 
			"];\n" + 
			"write $person to 'file://person.json';\n" +
			"write $legalEntity to 'file://legalEntity.json';";

		final SopremoPlan actualPlan = parseScript(query);
		final SopremoPlan expectedPlan = getExpectedPlan();

		assertPlanEquals(expectedPlan, actualPlan);
	}
	
//	@Test
	public void testWhereClause() {
		String query = "$usCongressMembers = read from 'file://usCongressMembers.json';\n" +
			"$usCongressBiographies = read from 'file://usCongressBiographies.json';\n" +
			"$person, $legalEntity = extract $usCongressMembers, $usCongressBiographies\n" + 
			"where ($usCongressMembers.biography[0:1] == $usCongressBiographies.biographyId[1:1])\n" + 
			"as [\n" +
			"  group $usCongressMembers by $usCongressMembers.id_o into {" + 
			"    name_p: $usCongressMembers.name_o" +
			"    worksFor_p: $usCongressMembers.id_o" + 
			"  }," + 
			"  group $usCongressBiographies by $usCongressBiographies.worksFor_o into {" + 
			"    name_l: $usCongressBiographies.worksFor_o" + 
			"  }" + 
			"];\n" + 
			"write $person to 'file://person.json';\n" +
			"write $legalEntity to 'file://legalEntity.json';";

		final SopremoPlan actualPlan = parseScript(query);
		final SopremoPlan expectedPlan = getExpectedPlan();

		assertPlanEquals(expectedPlan, actualPlan);
	}
	
	@Test
	public void testMultipleSourcesPerGroupBy() {
		String query = "$usCongressMembers = read from 'file://usCongressMembers.json';\n" +
			"$usCongressBiographies = read from 'file://usCongressBiographies.json';\n" +
			"$person, $legalEntity = map entities from $usCongressMembers, $usCongressBiographies\n" + 
			"as [\n" +
			"  group $usCongressMembers by $usCongressMembers.id_o into {" + 
			"    name_p: $usCongressMembers.name_o,\n" +
			"    biography_p: $usCongressBiographies.biographyId_o,\n" + 
			"    worksFor_p: $legalEntity.id" + 
			"  }," + 
			"  group $usCongressBiographies by $usCongressBiographies.worksFor_o into {" + 
			"    name_l: $usCongressBiographies.worksFor_o" + 
			"  }" + 
			"];\n" + 
			"write $person to 'file://person.json';\n" +
			"write $legalEntity to 'file://legalEntity.json';";

		final SopremoPlan actualPlan = parseScript(query);
		final SopremoPlan expectedPlan = getExpectedPlan();

		assertPlanEquals(expectedPlan, actualPlan);
	}
	
	@Test
	public void testFinalSchemaMapping() {
		String query = "$usCongressMembers = read from 'file://usCongressMembers.json';\n" +
			"$usCongressBiographies = read from 'file://usCongressBiographies.json';\n" +
			"$person, $legalEntity = map entities from $usCongressMembers, $usCongressBiographies\n" +
			"where ($usCongressMembers.biography[0:1] == $usCongressBiographies.biographyId[1:1])\n" + 
			"as [\n" +  
			"  group $usCongressMembers by $usCongressMembers.id_o into {" + 
			"    name_p: $usCongressMembers.name_o,\n" +
			"    biography_p: $usCongressBiographies.biographyId_o,\n" + 
			"    worksFor_p: $legalEntity.id" + 
			"  }," + 
			"  group $usCongressBiographies by $usCongressBiographies.worksFor_o into {" + 
			"    name_l: $usCongressBiographies.worksFor_o" + 
			"  }" + 
			"];\n" + 
			"write $person to 'file://person.json';\n" +
			"write $legalEntity to 'file://legalEntity.json';";

		final SopremoPlan actualPlan = parseScript(query);
		final SopremoPlan expectedPlan = getExpectedPlan();
		
		assertPlanEquals(expectedPlan, actualPlan);
	}

	@Name(noun = "map entities from")
	@InputCardinality(min = 1, max = Integer.MAX_VALUE)
	@OutputCardinality(min = 1, max = Integer.MAX_VALUE)
	public static class SchemaMapping extends CompositeOperator<SchemaMapping> {
		private EvaluationExpression projection;
//		private MappingTask mappingTask;
		
		/**
		 * Sets the projection to the specified value.
		 *
		 * @param projection the projection to set
		 */
		@Property
		@Name(adjective = "as")
		public void setMappingTask(ArrayCreation assignment) {
			
//			Projection
			
			if (assignment == null)
				throw new NullPointerException("projection must not be null");
			
			this.projection = assignment;
		}

		@Property
		@Name(adjective = "where")
		public void setKeyExpression(BooleanExpression assignment) {
			
//			Projection
			
			if (assignment == null)
				throw new NullPointerException("projection must not be null");
			
			this.projection = assignment;
		}
		
		/**
		 * Returns the projection.
		 * 
		 * @return the projection
		 */
		public EvaluationExpression getProjection() {
			return this.projection;
		}
		
		/*
		 * (non-Javadoc)
		 * @see eu.stratosphere.sopremo.operator.CompositeOperator#addImplementation(eu.stratosphere.sopremo.operator.
		 * SopremoModule, eu.stratosphere.sopremo.EvaluationContext)
		 */
		@Override
		public void addImplementation(SopremoModule module) {
		}
	}
}