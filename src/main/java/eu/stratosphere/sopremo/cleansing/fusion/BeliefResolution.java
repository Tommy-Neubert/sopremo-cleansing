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
package eu.stratosphere.sopremo.cleansing.fusion;

import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.pact.SopremoUtil;
import eu.stratosphere.sopremo.type.ArrayNode;
import eu.stratosphere.sopremo.type.BooleanNode;
import eu.stratosphere.sopremo.type.IArrayNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.NullNode;

/**
 * @author Arvid Heise
 */
public class BeliefResolution extends ConflictResolution {
	private final EvaluationExpression[] evidences;

	/**
	 * Initializes BelieveResolution.
	 * 
	 * @param evidences
	 */
	public BeliefResolution(final EvaluationExpression... evidences) {
		this.evidences = evidences;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.cleansing.fusion.FusionRule#fuse(eu.stratosphere.sopremo.type.IArrayNode, double[],
	 * eu.stratosphere.sopremo.cleansing.fusion.FusionContext)
	 */
	@Override
	public void fuse(final IArrayNode<IJsonNode> values, final Map<String, CompositeEvidence> weights) {
		final List<IJsonNode> mostProbableValues = this.getFinalMassFunction(values, weights).getMostProbableValues();
		values.clear();
		values.addAll(mostProbableValues);
	}

	protected BeliefMassFunction getFinalMassFunction(final IArrayNode<IJsonNode> values,
			final Map<String, CompositeEvidence> weights) {
		final Deque<BeliefMassFunction> massFunctions = new LinkedList<BeliefMassFunction>();

		// TODO: add support for arrays
		for (int index = 0, size = values.size(); index < size; index++)
			if (values.get(index) != NullNode.getInstance())
				massFunctions.add(new BeliefMassFunction(this.getValueFromSourceTaggedObject(values.get(index)),
					this.getWeightForValue(values.get(index), weights)));

		while (massFunctions.size() > 1)
			massFunctions.addFirst(massFunctions.removeFirst().combine(massFunctions.removeFirst(), this.evidences));

		return massFunctions.getFirst();
	}

	static class BeliefMassFunction {
		private final Object2DoubleMap<IJsonNode> valueMasses = new Object2DoubleArrayMap<IJsonNode>();

		private final static IJsonNode ALL = new ArrayNode<IJsonNode>();

		/**
		 * Initializes BeliefMassFunction.
		 */
		public BeliefMassFunction(final IJsonNode value, final double initialMass) {
			this.valueMasses.put(value, initialMass);
			this.valueMasses.put(ALL, 1 - initialMass);
		}

		/**
		 * Initializes BeliefResolution.BeliefMassFunction.
		 */
		public BeliefMassFunction() {
		}

		/**
		 * @return
		 */
		public List<IJsonNode> getMostProbableValues() {
			double maxBelief = 0;
			final List<IJsonNode> maxValues = new LinkedList<IJsonNode>();
			for (final Object2DoubleMap.Entry<IJsonNode> entry : this.valueMasses.object2DoubleEntrySet())
				if (entry.getDoubleValue() > maxBelief) {
					maxValues.clear();
					maxValues.add(entry.getKey());
					maxBelief = entry.getDoubleValue();
				} else if (entry.getDoubleValue() == maxBelief)
					maxValues.add(entry.getKey());
			return maxValues;
		}

		/**
		 * Returns the valueMasses.
		 * 
		 * @return the valueMasses
		 */
		public Object2DoubleMap<IJsonNode> getValueMasses() {
			return this.valueMasses;
		}

		/**
		 * @param removeLast
		 */
		public BeliefMassFunction combine(final BeliefMassFunction other,
				final EvaluationExpression[] evidenceExpressions) {
			final BeliefMassFunction combined = new BeliefMassFunction();

			final Object2DoubleMap<IJsonNode> nominators1 = new Object2DoubleArrayMap<IJsonNode>();
			final Object2DoubleMap<IJsonNode> nominators2 = new Object2DoubleArrayMap<IJsonNode>();
			// Object2DoubleMap<IJsonNode> denominators2 = new Object2DoubleArrayMap<IJsonNode>();

			double denominator = 1;

			for (final Object2DoubleMap.Entry<IJsonNode> entry1 : this.valueMasses.object2DoubleEntrySet())
				for (final Object2DoubleMap.Entry<IJsonNode> entry2 : other.valueMasses.object2DoubleEntrySet()) {
					final IJsonNode value1 = entry1.getKey();
					final IJsonNode value2 = entry2.getKey();
					final boolean equal = value1.equals(value2);
					final boolean isFirstEvidenceForSecond = equal
						|| this.isEvidence(value1, value2, evidenceExpressions);
					final boolean isSecondEvidenceForFirst = equal
						|| this.isEvidence(value2, value1, evidenceExpressions);

					final double mass1 = entry1.getDoubleValue();
					final double mass2 = entry2.getDoubleValue();
					final double massProduct = mass1 * mass2;
					if (isSecondEvidenceForFirst)
						nominators1.put(value1, nominators1.getDouble(value1) + massProduct);
					if (isFirstEvidenceForSecond)
						nominators2.put(value2, nominators2.getDouble(value2) + massProduct);
					if (!isFirstEvidenceForSecond && !isSecondEvidenceForFirst)
						denominator -= massProduct;
				}

			for (final Object2DoubleMap.Entry<IJsonNode> entry1 : this.valueMasses.object2DoubleEntrySet()) {
				final IJsonNode value = entry1.getKey();
				combined.valueMasses.put(value, combined.valueMasses.getDouble(value) + nominators1.getDouble(value)
					/ denominator);
			}
			for (final Object2DoubleMap.Entry<IJsonNode> entry2 : other.valueMasses.object2DoubleEntrySet()) {
				final IJsonNode value = entry2.getKey();
				combined.valueMasses.put(value, nominators2.getDouble(value) / denominator);
			}

			return combined;
		}

		private boolean isEvidence(final IJsonNode node1, final IJsonNode node2,
				final EvaluationExpression[] evidenceExpressions) {
			if (node1 == ALL)
				return true;

			if (node2 == ALL)
				return false;

			for (int index = 0; index < evidenceExpressions.length; index++)
				if (evidenceExpressions[index].evaluate(new ArrayNode<IJsonNode>(node1, node2)) == BooleanNode.TRUE)
					return true;

			return false;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			for (final Object2DoubleMap.Entry<IJsonNode> entry : this.valueMasses.object2DoubleEntrySet())
				builder.append(String.format("%s=%.2f; ", entry.getKey(), entry.getDoubleValue()));
			return builder.toString();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.cleansing.scrubbing.CleansingRule#appendAsString(java.lang.Appendable)
	 */
	@Override
	public void appendAsString(final Appendable appendable) throws IOException {
		super.appendAsString(appendable);
		SopremoUtil.append(appendable, " with evidences ", Arrays.asList(this.evidences));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + this.evidences.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		final BeliefResolution other = (BeliefResolution) obj;
		return Arrays.equals(this.evidences, other.evidences);
	}

}
