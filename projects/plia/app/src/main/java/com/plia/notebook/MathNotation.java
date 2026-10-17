package com.plia.notebook;

/** Conservative presentation-only Unicode/legacy notation conversion. No algebra or slash inference. */
public final class MathNotation {
    private MathNotation() {}

    public static String normalize(String source) {
        if (source == null) return "";
        String s = source.trim();
        if (s.length() > 16000) return s;
        for (String[] pair : new String[][] { {"\\[", "\\]"}, {"\\(", "\\)"}, {"$$", "$$"}, {"$", "$"} }) {
            if (s.length() > pair[0].length() + pair[1].length() && s.startsWith(pair[0]) && s.endsWith(pair[1])) {
                s = s.substring(pair[0].length(), s.length() - pair[1].length()).trim(); break;
            }
        }
        return convert(s, 0);
    }

    private static String convert(String s, int depth) {
        if (depth > 32) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Keep existing commands and text arguments intact, including aligned/matrix \\\\ separators.
            if (c == '\\') {
                int end = i + 1;
                if (end < s.length() && Character.isLetter(s.charAt(end))) {
                    while (end < s.length() && Character.isLetter(s.charAt(end))) end++;
                    String command = s.substring(i + 1, end);
                    if (command.equals("text") || command.startsWith("text") || command.equals("operatorname")) {
                        int open = end;
                        while (open < s.length() && Character.isWhitespace(s.charAt(open))) open++;
                        if (open < s.length() && s.charAt(open) == '{') {
                            int close = matching(s, open, '{', '}');
                            if (close >= 0) end = close + 1;
                        }
                    }
                } else if (end < s.length()) end++;
                out.append(s, i, end); i = end - 1; continue;
            }
            // Legacy replies use ^(expression). Only an explicit balanced group establishes scope.
            if (c == '^' && i + 1 < s.length() && s.charAt(i + 1) == '(') {
                int end = matching(s, i + 1, '(', ')');
                if (end >= 0) {
                    out.append("^{").append(convert(s.substring(i + 2, end), depth + 1)).append('}');
                    i = end;
                    continue;
                }
            }
            String supers = script(c, true), subs = script(c, false);
            if (supers != null || subs != null) {
                boolean sup = supers != null;
                out.append(sup ? "^{" : "_{");
                int end = i;
                String mapped;
                while (end < s.length() && (mapped = script(s.charAt(end), sup)) != null) { out.append(mapped); end++; }
                out.append('}'); i = end - 1; continue;
            }
            if (c == '√' || c == '∛' || c == '∜') {
                int at = i + 1;
                while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++;
                String root = c == '√' ? "\\sqrt" : c == '∛' ? "\\sqrt[3]" : "\\sqrt[4]";
                if (at < s.length() && (s.charAt(at) == '(' || s.charAt(at) == '{')) {
                    char open = s.charAt(at), close = open == '(' ? ')' : '}';
                    int end = matching(s, at, open, close);
                    if (end >= 0) { out.append(root).append('{').append(convert(s.substring(at + 1, end), depth + 1)).append('}'); i = end; continue; }
                }
                // A bare radical has no unambiguous radicand extent; retain its glyph meaning.
                out.append(c == '√' ? "\\surd " : c == '∛' ? "\\sqrt[3]{}" : "\\sqrt[4]{}");
                continue;
            }
            String replacement = symbol(c);
            out.append(replacement == null ? String.valueOf(c) : replacement);
        }
        return out.toString().trim();
    }

    private static int matching(String s, int start, char open, char close) {
        int level = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '\\') { i++; continue; }
            if (s.charAt(i) == open) level++;
            else if (s.charAt(i) == close && --level == 0) return i;
        }
        return -1;
    }

    private static String script(char c, boolean superScript) {
        String glyphs = superScript ? "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ" : "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ";
        String ascii = superScript ? "0123456789+-=()ni" : "0123456789+-=()aehijklmnoprstuvx";
        int at = glyphs.indexOf(c);
        return at < 0 ? null : String.valueOf(ascii.charAt(at));
    }

    private static String symbol(char c) {
        switch (c) {
            case '−': return "-";
            case '′': return "'"; case '″': return "''"; case '‴': return "'''";
            case '∞': return "\\infty "; case '∫': return "\\int "; case '∬': return "\\iint "; case '∭': return "\\iiint "; case '∮': return "\\oint ";
            case '∑': return "\\sum "; case '∏': return "\\prod ";
            case '×': return "\\times "; case '·': case '⋅': return "\\cdot "; case '÷': return "\\div ";
            case '≤': return "\\le "; case '≥': return "\\ge "; case '≠': return "\\ne "; case '≈': return "\\approx ";
            case '→': return "\\to "; case '↦': return "\\mapsto "; case '⇒': return "\\Rightarrow "; case '⇔': return "\\Leftrightarrow ";
            case '∂': return "\\partial "; case '∇': return "\\nabla "; case '±': return "\\pm ";
            case '∈': return "\\in "; case '∉': return "\\notin "; case '∅': return "\\emptyset ";
            case 'α': return "\\alpha "; case 'β': return "\\beta "; case 'γ': return "\\gamma "; case 'δ': return "\\delta ";
            case 'ε': return "\\varepsilon "; case 'ϵ': return "\\epsilon "; case 'ζ': return "\\zeta "; case 'η': return "\\eta ";
            case 'θ': return "\\theta "; case 'ϑ': return "\\vartheta "; case 'ι': return "\\iota "; case 'κ': return "\\kappa ";
            case 'λ': return "\\lambda "; case 'μ': return "\\mu "; case 'ν': return "\\nu "; case 'ξ': return "\\xi ";
            case 'π': return "\\pi "; case 'ρ': return "\\rho "; case 'σ': return "\\sigma "; case 'ς': return "\\varsigma ";
            case 'τ': return "\\tau "; case 'υ': return "\\upsilon "; case 'φ': return "\\varphi "; case 'ϕ': return "\\phi ";
            case 'χ': return "\\chi "; case 'ψ': return "\\psi "; case 'ω': return "\\omega ";
            case 'Γ': return "\\Gamma "; case 'Δ': return "\\Delta "; case 'Θ': return "\\Theta "; case 'Λ': return "\\Lambda ";
            case 'Ξ': return "\\Xi "; case 'Π': return "\\Pi "; case 'Σ': return "\\Sigma "; case 'Υ': return "\\Upsilon ";
            case 'Φ': return "\\Phi "; case 'Ψ': return "\\Psi "; case 'Ω': return "\\Omega ";
            default: return null;
        }
    }
}
