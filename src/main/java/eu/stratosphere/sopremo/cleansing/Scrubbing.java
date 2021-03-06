package eu.stratosphere.sopremo.cleansing;

import java.io.IOException;
import java.util.List;

import eu.stratosphere.sopremo.cleansing.scrubbing.RuleBasedScrubbing;
import eu.stratosphere.sopremo.cleansing.scrubbing.StatefulConstant;
import eu.stratosphere.sopremo.cleansing.scrubbing.ValidationRule;
import eu.stratosphere.sopremo.cleansing.scrubbing.ValueCorrection;
import eu.stratosphere.sopremo.expressions.ArrayCreation;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.ExpressionUtil;
import eu.stratosphere.sopremo.expressions.FunctionCall;
import eu.stratosphere.sopremo.expressions.ObjectCreation;
import eu.stratosphere.sopremo.expressions.ObjectCreation.Mapping;
import eu.stratosphere.sopremo.expressions.PathSegmentExpression;
import eu.stratosphere.sopremo.expressions.TernaryExpression;
import eu.stratosphere.sopremo.operator.CompositeOperator;
import eu.stratosphere.sopremo.operator.InputCardinality;
import eu.stratosphere.sopremo.operator.Name;
import eu.stratosphere.sopremo.operator.OutputCardinality;
import eu.stratosphere.sopremo.operator.Property;
import eu.stratosphere.sopremo.operator.SopremoModule;

@Name(verb = "scrub")
@InputCardinality(1)
@OutputCardinality(1)
public class Scrubbing extends CompositeOperator<Scrubbing> {
	private RuleBasedScrubbing ruleBasedScrubbing = new RuleBasedScrubbing();

	@Property
	@Name(preposition = "with rules")
	public void setRuleExpression(ObjectCreation ruleExpression) {
		this.ruleBasedScrubbing.clear();
		this.parseRuleExpression(ruleExpression, EvaluationExpression.VALUE);
	}

	private void parseRuleExpression(ObjectCreation ruleExpression,
			PathSegmentExpression value) {
		final List<Mapping<?>> mappings = ruleExpression.getMappings();
		for (Mapping<?> mapping : mappings) {
			final EvaluationExpression expression = mapping.getExpression();
			final PathSegmentExpression path = ExpressionUtil.makePath(value,
					mapping.getTargetExpression());
			if (expression instanceof ObjectCreation) {
				this.parseRuleExpression((ObjectCreation) expression, path);
			} else if (expression instanceof FunctionCall) {
				this.ruleBasedScrubbing.addRule(
						this.handleFunctionCalls(expression), path);
			} else {
				for (EvaluationExpression expr : this
						.setFixesOnRules(expression)) {
					this.ruleBasedScrubbing.addRule(expr, path);
				}
			}
		}
	}

	private ArrayCreation setFixesOnRules(EvaluationExpression expression) {
		ArrayCreation rulesWithFixes = new ArrayCreation();
		if (expression instanceof TernaryExpression) {
			if (((TernaryExpression) expression).getIfExpression() instanceof ArrayCreation) {
				ValueCorrection generalFix = (ValueCorrection) ((TernaryExpression) expression)
						.getThenExpression();
				for (EvaluationExpression partial : ((TernaryExpression) expression)
						.getIfExpression()) {
					if (partial instanceof TernaryExpression) {
						ValidationRule rule = (ValidationRule) ((TernaryExpression) partial)
								.getIfExpression();
						rule = checkForStatefulConstantAndCopy(rule);
						ValueCorrection explicitFix = (ValueCorrection) ((TernaryExpression) partial)
								.getThenExpression();
						rule.setValueCorrection(explicitFix);
						rulesWithFixes.add(rule);
					} else if (partial instanceof ValidationRule) {
						partial = checkForStatefulConstantAndCopy((ValidationRule) partial);
						((ValidationRule) partial)
								.setValueCorrection(generalFix);
						rulesWithFixes.add(partial);
					} else if (partial instanceof FunctionCall) {
						rulesWithFixes.add(this.handleFunctionCalls(partial));
					} else {
						throw new IllegalArgumentException(
								"No rules for validation provided.");
					}
				}

			} else {
				ValidationRule rule = (ValidationRule) ((TernaryExpression) expression)
						.getIfExpression();
				ValueCorrection fix = (ValueCorrection) ((TernaryExpression) expression)
						.getThenExpression();
				rule = checkForStatefulConstantAndCopy(rule);
				rule.setValueCorrection(fix);
				rulesWithFixes.add(rule);
			}
		} else if (expression instanceof ArrayCreation) {
			for (EvaluationExpression partial : expression) {
				if (partial instanceof TernaryExpression) {
					ValidationRule rule = (ValidationRule) ((TernaryExpression) partial)
							.getIfExpression();
					ValueCorrection explicitFix = (ValueCorrection) ((TernaryExpression) partial)
							.getThenExpression();
					rule = checkForStatefulConstantAndCopy(rule);
					rule.setValueCorrection(explicitFix);
					rulesWithFixes.add(rule);
				} else if (partial instanceof ValidationRule) {
					rulesWithFixes.add(partial);
				} else if (partial instanceof FunctionCall) {
					rulesWithFixes.add(this.handleFunctionCalls(partial));
				} else {
					throw new IllegalArgumentException(
							"No rules for validation provided.");
				}
			}
		} else {
			expression = checkForStatefulConstantAndCopy((ValidationRule) expression);
			rulesWithFixes.add(expression);
		}
		return rulesWithFixes;
	}

	private EvaluationExpression handleFunctionCalls(
			EvaluationExpression function) {
		FunctionCall fct = (FunctionCall) function.copy();
		fct.getParameters().add(0, EvaluationExpression.VALUE);
		return fct;
	}

	private ValidationRule checkForStatefulConstantAndCopy(ValidationRule rule) {
		return (ValidationRule) ((rule instanceof StatefulConstant) ? rule
				.clone() : rule);
	}

	public void addRule(EvaluationExpression ruleExpression,
			PathSegmentExpression target) {
		this.ruleBasedScrubbing.addRule(ruleExpression, target);
	}

	public void removeRule(EvaluationExpression ruleExpression,
			PathSegmentExpression target) {
		this.ruleBasedScrubbing.removeRule(ruleExpression, target);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * eu.stratosphere.sopremo.operator.CompositeOperator#addImplementation(
	 * eu.stratosphere.sopremo.operator.SopremoModule ,
	 * eu.stratosphere.sopremo.EvaluationContext)
	 */
	@Override
	public void addImplementation(SopremoModule module) {
		this.ruleBasedScrubbing.addImplementation(module);
	}

	public Scrubbing withRuleExpression(ObjectCreation ruleExpression) {
		this.setRuleExpression(ruleExpression);
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + this.ruleBasedScrubbing.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		Scrubbing other = (Scrubbing) obj;
		return this.ruleBasedScrubbing.equals(other.ruleBasedScrubbing);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * eu.stratosphere.sopremo.operator.ElementaryOperator#appendAsString(java
	 * .lang.Appendable)
	 */
	@Override
	public void appendAsString(Appendable appendable) throws IOException {
		this.ruleBasedScrubbing.appendAsString(appendable);
	}
}
