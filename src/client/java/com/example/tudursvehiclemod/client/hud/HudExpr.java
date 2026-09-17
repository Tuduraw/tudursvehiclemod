package com.example.tudursvehiclemod.client.hud;

import java.util.Map;

/** Parses and evaluates the small expression language used inside MC Heli-style HUD script files.. */
public final class HudExpr {

	private final Node root;
	/** Kept only for error messages / debugging. */
	private final String source;

	private HudExpr(Node root, String source) {
		this.root = root;
		this.source = source;
	}

	/** Parses an expression string. Throws HudScriptException with a message that includes the offending source text if the expression is malformed. */
	public static HudExpr parse(String source) {
		Parser parser = new Parser(source);
		Node node = parser.parseTernary();
		parser.expectEnd();
		return new HudExpr(node, source);
	}

	public double evaluate(Map<String, Double> variables) {
		return root.evaluate(variables);
	}

	@Override
	public String toString() {
		return source;
	}

	// ---- AST ----

	private interface Node {
		double evaluate(Map<String, Double> vars);
	}

	private record NumberNode(double value) implements Node {
		public double evaluate(Map<String, Double> vars) {
			return value;
		}
	}

	private record VarNode(String name) implements Node {
		public double evaluate(Map<String, Double> vars) {
			Double v = vars.get(name);
			// Unknown variables resolve to 0 rather than throwing.
			return v != null ? v : 0.0;
		}
	}

	private record UnaryNode(char op, Node operand) implements Node {
		public double evaluate(Map<String, Double> vars) {
			double v = operand.evaluate(vars);
			return switch (op) {
				case '-' -> -v;
				case '!' -> v == 0.0 ? 1.0 : 0.0;
				default -> throw new IllegalStateException("Unknown unary op " + op);
			};
		}
	}

	private record BinaryNode(String op, Node left, Node right) implements Node {
		public double evaluate(Map<String, Double> vars) {
			double l = left.evaluate(vars);
			// Short-circuit for && and ||.
			if (op.equals("&&")) {
				return (l != 0.0 && right.evaluate(vars) != 0.0) ? 1.0 : 0.0;
			}
			if (op.equals("||")) {
				return (l != 0.0 || right.evaluate(vars) != 0.0) ? 1.0 : 0.0;
			}
			double r = right.evaluate(vars);
			return switch (op) {
				case "+" -> l + r;
				case "-" -> l - r;
				case "*" -> l * r;
				case "/" -> r != 0.0 ? l / r : 0.0;
				case "==" -> l == r ? 1.0 : 0.0;
				case "!=" -> l != r ? 1.0 : 0.0;
				case ">" -> l > r ? 1.0 : 0.0;
				case "<" -> l < r ? 1.0 : 0.0;
				case ">=" -> l >= r ? 1.0 : 0.0;
				case "<=" -> l <= r ? 1.0 : 0.0;
				default -> throw new IllegalStateException("Unknown binary op " + op);
			};
		}
	}

	private record TernaryNode(Node cond, Node whenTrue, Node whenFalse) implements Node {
		public double evaluate(Map<String, Double> vars) {
			return cond.evaluate(vars) != 0.0 ? whenTrue.evaluate(vars) : whenFalse.evaluate(vars);
		}
	}

	// ---- Recursive-descent parser ---- Precedence, low to high: ternary > || > && > equality > comparison > additive > multiplicative > unary > primary

	private static final class Parser {
		private final String src;
		private int pos;

		Parser(String src) {
			this.src = src;
			this.pos = 0;
		}

		void expectEnd() {
			skipWhitespace();
			if (pos != src.length()) {
				throw new HudScriptException("Unexpected trailing content in expression: \"" + src + "\" at " + pos);
			}
		}

		Node parseTernary() {
			Node cond = parseOr();
			skipWhitespace();
			if (peek() == '?') {
				pos++;
				Node whenTrue = parseTernary();
				skipWhitespace();
				if (peek() != ':') {
					throw new HudScriptException("Expected ':' in ternary expression: \"" + src + "\"");
				}
				pos++;
				Node whenFalse = parseTernary();
				return new TernaryNode(cond, whenTrue, whenFalse);
			}
			return cond;
		}

		Node parseOr() {
			Node left = parseAnd();
			while (true) {
				skipWhitespace();
				if (matchLiteral("||")) {
					left = new BinaryNode("||", left, parseAnd());
				} else {
					return left;
				}
			}
		}

		Node parseAnd() {
			Node left = parseEquality();
			while (true) {
				skipWhitespace();
				if (matchLiteral("&&")) {
					left = new BinaryNode("&&", left, parseEquality());
				} else {
					return left;
				}
			}
		}

		Node parseEquality() {
			Node left = parseComparison();
			while (true) {
				skipWhitespace();
				if (matchLiteral("==")) {
					left = new BinaryNode("==", left, parseComparison());
				} else if (matchLiteral("!=")) {
					left = new BinaryNode("!=", left, parseComparison());
				} else {
					return left;
				}
			}
		}

		Node parseComparison() {
			Node left = parseAdditive();
			while (true) {
				skipWhitespace();
				if (matchLiteral(">=")) {
					left = new BinaryNode(">=", left, parseAdditive());
				} else if (matchLiteral("<=")) {
					left = new BinaryNode("<=", left, parseAdditive());
				} else if (peek() == '>') {
					pos++;
					left = new BinaryNode(">", left, parseAdditive());
				} else if (peek() == '<') {
					pos++;
					left = new BinaryNode("<", left, parseAdditive());
				} else {
					return left;
				}
			}
		}

		Node parseAdditive() {
			Node left = parseMultiplicative();
			while (true) {
				skipWhitespace();
				char c = peek();
				if (c == '+') {
					pos++;
					left = new BinaryNode("+", left, parseMultiplicative());
				} else if (c == '-') {
					pos++;
					left = new BinaryNode("-", left, parseMultiplicative());
				} else {
					return left;
				}
			}
		}

		Node parseMultiplicative() {
			Node left = parseUnary();
			while (true) {
				skipWhitespace();
				char c = peek();
				if (c == '*') {
					pos++;
					left = new BinaryNode("*", left, parseUnary());
				} else if (c == '/') {
					pos++;
					left = new BinaryNode("/", left, parseUnary());
				} else {
					return left;
				}
			}
		}

		Node parseUnary() {
			skipWhitespace();
			char c = peek();
			if (c == '-') {
				pos++;
				return new UnaryNode('-', parseUnary());
			}
			if (c == '+') {
				// Unary plus - MC Heli's own scripts use "+140"-style explicit positive notation freely (e.g.
				pos++;
				return parseUnary();
			}
			if (c == '!') {
				pos++;
				return new UnaryNode('!', parseUnary());
			}
			return parsePrimary();
		}

		Node parsePrimary() {
			skipWhitespace();
			char c = peek();
			if (c == '(') {
				pos++;
				Node inner = parseTernary();
				skipWhitespace();
				if (peek() != ')') {
					throw new HudScriptException("Expected ')' in expression: \"" + src + "\"");
				}
				pos++;
				return inner;
			}
			if (c == '#') {
				pos++;
				return new NumberNode(parseHexDigits());
			}
			if (c == '0' && pos + 1 < src.length() && (src.charAt(pos + 1) == 'x' || src.charAt(pos + 1) == 'X')) {
				pos += 2;
				return new NumberNode(parseHexDigits());
			}
			if (Character.isDigit(c) || c == '.') {
				return new NumberNode(parseDecimalNumber());
			}
			if (Character.isLetter(c) || c == '_') {
				return new VarNode(parseIdentifier().toLowerCase(java.util.Locale.ROOT));
			}
			throw new HudScriptException("Unexpected character '" + c + "' in expression: \"" + src + "\" at " + pos);
		}

		double parseHexDigits() {
			int start = pos;
			while (pos < src.length() && isHexDigit(src.charAt(pos))) {
				pos++;
			}
			if (pos == start) {
				throw new HudScriptException("Expected hex digits in expression: \"" + src + "\"");
			}
			try {
				return (double) Long.parseLong(src.substring(start, pos), 16);
			} catch (NumberFormatException e) {
				// Wrapped rather than left to propagate as-is.
				throw new HudScriptException("Invalid hex literal in expression: \"" + src + "\"", e);
			}
		}

		double parseDecimalNumber() {
			int start = pos;
			while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
				pos++;
			}
			try {
				return Double.parseDouble(src.substring(start, pos));
			} catch (NumberFormatException e) {
				// Wrapped rather than left to propagate as-is.
				throw new HudScriptException("Invalid number in expression: \"" + src + "\"", e);
			}
		}

		String parseIdentifier() {
			int start = pos;
			while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
				pos++;
			}
			return src.substring(start, pos);
		}

		boolean isHexDigit(char c) {
			return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
		}

		char peek() {
			return pos < src.length() ? src.charAt(pos) : '\0';
		}

		boolean matchLiteral(String literal) {
			if (src.regionMatches(pos, literal, 0, literal.length())) {
				pos += literal.length();
				return true;
			}
			return false;
		}

		void skipWhitespace() {
			while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
				pos++;
			}
		}
	}
}
