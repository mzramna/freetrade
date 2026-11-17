package dev.freetrade.market;

import java.util.Locale;

/**
 * Tiny arithmetic expression evaluator for the configurable price formula.
 * Variables: x (stock), b (base value), r (reference stock). Operators: + - * / ^ (right assoc), unary -.
 * Functions: min(a,b) max(a,b) pow(a,b) log(a) ln(a) sqrt(a) abs(a) exp(a). Constants: e, pi.
 * The expression is compiled once into a tree; evaluation is allocation-free.
 */
public final class PriceExpression {
    @FunctionalInterface
    private interface Node {
        double eval(double x, double b, double r);
    }

    private final String source;
    private final Node root;

    private PriceExpression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    public static PriceExpression parse(String src) {
        Parser p = new Parser(src);
        Node n = p.expr();
        p.skipWs();
        if (p.pos != src.length()) throw p.error("unexpected '" + src.charAt(p.pos) + "'");
        return new PriceExpression(src, n);
    }

    public double eval(double x, double b, double r) {
        return root.eval(x, b, r);
    }

    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return source;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid price expression '" + s + "' at " + pos + ": " + msg);
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        boolean eat(char c) {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        Node expr() {
            Node left = term();
            while (true) {
                if (eat('+')) {
                    Node l = left, r = term();
                    left = (x, b, rr) -> l.eval(x, b, rr) + r.eval(x, b, rr);
                } else if (eat('-')) {
                    Node l = left, r = term();
                    left = (x, b, rr) -> l.eval(x, b, rr) - r.eval(x, b, rr);
                } else return left;
            }
        }

        Node term() {
            Node left = unary();
            while (true) {
                if (eat('*')) {
                    Node l = left, r = unary();
                    left = (x, b, rr) -> l.eval(x, b, rr) * r.eval(x, b, rr);
                } else if (eat('/')) {
                    Node l = left, r = unary();
                    left = (x, b, rr) -> l.eval(x, b, rr) / r.eval(x, b, rr);
                } else return left;
            }
        }

        Node unary() {
            if (eat('-')) {
                Node n = unary();
                return (x, b, r) -> -n.eval(x, b, r);
            }
            if (eat('+')) return unary();
            return power();
        }

        Node power() {
            Node base = atom();
            if (eat('^')) {
                Node exp = unary(); // right associative, allows 2^-1
                return (x, b, r) -> Math.pow(base.eval(x, b, r), exp.eval(x, b, r));
            }
            return base;
        }

        Node atom() {
            skipWs();
            if (pos >= s.length()) throw error("unexpected end");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                Node n = expr();
                if (!eat(')')) throw error("expected ')'");
                return n;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')
                        && pos + 1 < s.length() && (Character.isDigit(s.charAt(pos + 1)) || s.charAt(pos + 1) == '-')) {
                    pos += 2;
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                }
                double v;
                try {
                    v = Double.parseDouble(s.substring(start, pos));
                } catch (NumberFormatException e) {
                    throw error("bad number");
                }
                return (x, b, r) -> v;
            }
            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
                String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
                skipWs();
                if (pos < s.length() && s.charAt(pos) == '(') {
                    pos++;
                    Node a = expr();
                    Node b2 = null;
                    if (eat(',')) b2 = expr();
                    if (!eat(')')) throw error("expected ')'");
                    return function(name, a, b2);
                }
                return switch (name) {
                    case "x" -> (x, b, r) -> x;
                    case "b" -> (x, b, r) -> b;
                    case "r" -> (x, b, r) -> r;
                    case "e" -> (x, b, r) -> Math.E;
                    case "pi" -> (x, b, r) -> Math.PI;
                    default -> throw error("unknown variable '" + name + "' (use x, b, r)");
                };
            }
            throw error("unexpected '" + c + "'");
        }

        Node function(String name, Node a, Node b2) {
            boolean two = b2 != null;
            switch (name) {
                case "min":
                case "max":
                case "pow":
                    if (!two) throw error(name + " needs 2 arguments");
                    break;
                default:
                    if (two) throw error(name + " takes 1 argument");
            }
            Node b = b2;
            return switch (name) {
                case "min" -> (x, bb, r) -> Math.min(a.eval(x, bb, r), b.eval(x, bb, r));
                case "max" -> (x, bb, r) -> Math.max(a.eval(x, bb, r), b.eval(x, bb, r));
                case "pow" -> (x, bb, r) -> Math.pow(a.eval(x, bb, r), b.eval(x, bb, r));
                case "log" -> (x, bb, r) -> Math.log10(a.eval(x, bb, r));
                case "ln" -> (x, bb, r) -> Math.log(a.eval(x, bb, r));
                case "sqrt" -> (x, bb, r) -> Math.sqrt(a.eval(x, bb, r));
                case "abs" -> (x, bb, r) -> Math.abs(a.eval(x, bb, r));
                case "exp" -> (x, bb, r) -> Math.exp(a.eval(x, bb, r));
                default -> throw error("unknown function '" + name + "'");
            };
        }
    }
}
