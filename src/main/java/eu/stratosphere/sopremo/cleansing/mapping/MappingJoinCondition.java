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
package eu.stratosphere.sopremo.cleansing.mapping;

import it.unibas.spicy.model.datasource.JoinCondition;
import it.unibas.spicy.model.paths.PathExpression;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Arvid Heise, Tommy Neubert
 */
public class MappingJoinCondition {
	private List<List<String>> fromPaths;
	private List<List<String>> toPaths;
	private boolean isMandatory;
	private boolean isMonodirectional;

	public MappingJoinCondition(List<List<String>> fromPaths,
			List<List<String>> toPaths, boolean isMandatory,
			boolean isMonodirectional) {
		this.fromPaths = fromPaths;
		this.toPaths = toPaths;
		this.isMandatory = isMandatory;
		this.isMonodirectional = isMonodirectional;
	}

	public MappingJoinCondition(JoinCondition condition) {
		this.fromPaths = this.extractPathFrom(condition.getFromPaths());
		this.toPaths = this.extractPathFrom(condition.getToPaths());
		this.isMandatory = condition.isMandatory();
		this.isMonodirectional = condition.isMonodirectional();
	}

	private List<List<String>> extractPathFrom(List<PathExpression> fromPaths) {
		List<List<String>> paths = new LinkedList<List<String>>();
		for (PathExpression currentPath : fromPaths) {
			List<String> pathSteps = currentPath.getPathSteps();
			paths.add(pathSteps);
		}
		return paths;
	}

	public List<List<String>> getFromPaths() {
		return this.fromPaths;
	}

	public List<List<String>> getToPaths() {
		return this.toPaths;
	}

	public boolean isMandatory() {
		return this.isMandatory;
	}

	public boolean isMonodirectional() {
		return this.isMonodirectional;
	}

	public JoinCondition generateSpicyType() {
		List<PathExpression> fromPaths = createPaths(this.fromPaths);
		List<PathExpression> toPaths = createPaths(this.toPaths);
		return new JoinCondition(fromPaths, toPaths, this.isMonodirectional,
				this.isMandatory);
	}

	public static List<PathExpression> createPaths(List<List<String>> paths) {
		List<PathExpression> spicyPaths = new LinkedList<PathExpression>();
		for (List<String> currentPath : paths) {
			spicyPaths.add(new PathExpression(currentPath));
		}
		return spicyPaths;
	}
}
