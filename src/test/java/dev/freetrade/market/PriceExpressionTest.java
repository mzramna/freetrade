package dev.freetrade.market;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PriceExpressionTest {
    @Test
    void defaultPowerLaw() {
        PriceExpression e = PriceExpression.parse("b * (r / x)^1");
        assertEquals(4.0, e.eval(1000, 4, 1000), 1e-9);
        assertEquals(8.0, e.eval(500, 4, 1000), 1e-9);
    }

    @Test
    void precedenceAndFunctions() {
        PriceExpression e = PriceExpression.parse("2 + 3 * 2 ^ 2 - min(1, 5) + max(x, 0) / 2");
        assertEquals(2 + 12 - 1 + 5, e.eval(10, 0, 0), 1e-9);
        assertEquals(0.5, PriceExpression.parse("2^-1").eval(0, 0, 0), 1e-9);
        assertEquals(512, PriceExpression.parse("2^3^2").eval(0, 0, 0), 1e-9);
        assertEquals(2, PriceExpression.parse("log(100)").eval(0, 0, 0), 1e-9);
        assertEquals(1e3, PriceExpression.parse("1e3").eval(0, 0, 0), 1e-9);
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> PriceExpression.parse("b * y"));
        assertThrows(IllegalArgumentException.class, () -> PriceExpression.parse("(b"));
        assertThrows(IllegalArgumentException.class, () -> PriceExpression.parse("foo(1)"));
        assertThrows(IllegalArgumentException.class, () -> PriceExpression.parse("b x"));
    }
}
