package eu.stratosphere.sopremo.type;

import java.math.BigDecimal;

import junit.framework.Assert;

public class DecimalNodeTest extends JsonNodeTest<DecimalNode> {

	@Override
	public void testValue() {
		final DecimalNode decimalnode = new DecimalNode(BigDecimal.valueOf(42));
		Assert.assertEquals(BigDecimal.valueOf(42), decimalnode.getDecimalValue());
	}

	@Override
	protected IJsonNode lowerNode() {
		return DecimalNode.valueOf(BigDecimal.valueOf(10042, 2));
	}

	@Override
	protected IJsonNode higherNode() {
		return DecimalNode.valueOf(BigDecimal.valueOf(10042, -1));
	}

}
