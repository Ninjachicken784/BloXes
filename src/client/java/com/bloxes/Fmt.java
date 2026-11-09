package com.bloxes;

import java.util.Locale;

/**
 * Number formatting for block counts: 4.52M, 440k, 59.5k, 400.
 */
public final class Fmt {
	private Fmt() { }

	/** Short form for the HUD and the list. */
	public static String count(long n) {
		if (n >= 1_000_000L) {
			return trim(String.format(Locale.ROOT, "%.2f", n / 1_000_000.0)) + "M";
		}
		if (n >= 100_000L) {
			return (n / 1000L) + "k";
		}
		if (n >= 1000L) {
			return trim(String.format(Locale.ROOT, "%.1f", n / 1000.0)) + "k";
		}
		return Long.toString(n);
	}

	/** Full number with thousands separators, like 4,520,311. */
	public static String full(long n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	public static String percent(double fraction) {
		return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
	}

	private static String trim(String s) {
		if (s.indexOf('.') < 0) return s;
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == '0') end--;
		if (end > 0 && s.charAt(end - 1) == '.') end--;
		return s.substring(0, end);
	}
}
