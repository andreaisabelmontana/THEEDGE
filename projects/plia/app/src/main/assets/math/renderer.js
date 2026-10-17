(function () {
  "use strict";
  var MAX_HEIGHT = 1000000, MAX_ROWS = 256, MAX_CHARACTERS = 262144, MAX_MATH = 16000;
  var root = document.getElementById("solution");
  window.solutionReady = false;
  window.solutionRenderId = 0;
  window.solutionErrors = 0;
  window.solutionFontError = false;

  function element(name, className, text) {
    var node = document.createElement(name);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function mathError(target, source) {
    window.solutionErrors++;
    target.replaceChildren();
    var error = element("span", "render-error");
    error.appendChild(element("span", "error-label", "Math notation could not be rendered"));
    var raw = element("pre", "math-source", source);
    raw.setAttribute("aria-label", "Original math notation");
    raw.setAttribute("tabindex", "0");
    error.appendChild(raw); target.appendChild(error);
  }

  function renderKatex(target, source, display) {
      if (!window.katex || source.length > MAX_MATH) throw new Error("Unsupported math input");
      var deniedTrustedCommand = false;
      // A new macros object for every expression prevents definitions leaking between rows/replies.
      window.katex.render(source, target, {
        displayMode: display, output: "htmlAndMathml", throwOnError: true,
        // Equivalent to trust:false; also detect commands KaTeX otherwise prints as red raw text.
        trust: function () { deniedTrustedCommand = true; return false; },
        maxExpand: 1000, maxSize: 20, macros: Object.create(null), strict: "error"
      });
      if (deniedTrustedCommand) throw new Error("Unsupported trusted command");
  }

  function renderMath(target, source, display) {
    try { renderKatex(target, source, display); return true; }
    catch (error) { mathError(target, source); return false; }
  }

  function escaped(text, index) {
    var count = 0;
    while (index > 0 && text.charAt(--index) === "\\") count++;
    return count % 2 !== 0;
  }

  /** Presentation-only scanner. A boundary is an equality outside every supplied TeX group.
   * Macro definitions, comments, unbalanced input and existing manual line breaks are not rewritten. */
  function groupedArguments(source, from, count) {
    for (var argument = 0; argument < count; argument++) {
      while (/\s/.test(source.charAt(from)) && from < source.length) from++;
      if (source.charAt(from++) !== "{") return false;
      var depth = 1;
      while (from < source.length && depth) {
        var ch = source.charAt(from++);
        if (ch === "\\") from++;
        else if (ch === "{") { if (++depth > 64) return false; }
        else if (ch === "}") depth--;
      }
      if (depth) return false;
    }
    return true;
  }

  function knownArgumentCount(command) {
    if (/^(?:frac|dfrac|tfrac|binom|dbinom|tbinom|overset|underset|stackrel|colorbox|href|htmlClass|htmlId|htmlStyle|htmlData)$/.test(command)) return 2;
    if (command === "includegraphics") return 1;
    if (command === "fcolorbox") return 3;
    if (command === "mathchoice") return 4;
    if (command === "genfrac") return 6;
    if (/^(?:sqrt|boxed|text|textrm|textnormal|textbf|textit|textsf|texttt|textup|textmd|textsc|emph|mathrm|mathbf|mathit|mathsf|mathtt|mathcal|mathbb|mathfrak|mathnormal|boldsymbol|bm|operatorname|overbrace|underbrace|overline|underline|cancel|bcancel|xcancel|phantom|hphantom|vphantom|smash|mathop|mathbin|mathrel|mathord|mathopen|mathclose|mathpunct|mathinner|substack|hspace|vspace|hat|widehat|tilde|widetilde|bar|vec|dot|ddot|breve|check|acute|grave|mathring)$/.test(command)) return 1;
    return 0;
  }

  function knownIndependentCommand(command) {
    return /^(?:i|j|imath|jmath|S|P|O|L)$/.test(command)
      || /^(?:alpha|beta|gamma|delta|epsilon|varepsilon|zeta|eta|theta|vartheta|iota|kappa|varkappa|lambda|mu|nu|xi|omicron|pi|varpi|rho|varrho|sigma|varsigma|tau|upsilon|phi|varphi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega|int|iint|iiint|oint|sum|prod|coprod|bigcup|bigcap|lim|limsup|liminf|sup|inf|min|max|log|ln|exp|sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|sinh|cosh|tanh|det|dim|gcd|Pr|infty|partial|nabla|ell|hbar|Re|Im|emptyset|varnothing|forall|exists|nexists|neg|land|lor|pm|mp|times|cdot|div|circ|bullet|star|ast|oplus|otimes|le|leq|ge|geq|ne|neq|approx|equiv|sim|simeq|cong|propto|in|notin|subset|subseteq|supset|supseteq|to|rightarrow|leftarrow|leftrightarrow|Rightarrow|Leftarrow|Leftrightarrow|longrightarrow|longleftarrow|longleftrightarrow|Longrightarrow|Longleftarrow|Longleftrightarrow|mapsto|uparrow|downarrow|quad|qquad|enspace|thinspace|medspace|thickspace|negthinspace|ldots|cdots|vdots|ddots|dots|limits|nolimits|displaystyle|not|mid|parallel|perp)$/.test(command);
  }

  function equalityBoundaries(source) {
    if (typeof source !== "string" || source.length > MAX_MATH) return [];
    var braces = 0, delimiters = [], environments = [], leftRight = 0, boundaries = [], previous = "";
    for (var i = 0; i < source.length; i++) {
      var ch = source.charAt(i);
      if (ch === "\\") {
        var token = /^\\([A-Za-z]+|[^\r\n])/.exec(source.slice(i));
        if (!token) return [];
        var command = token[1]; i += token[0].length - 1;
        if (/^(?:def|gdef|edef|xdef|let|futurelet|global|newcommand|renewcommand|providecommand|color|bf|it|rm|sf|tt)$/.test(command)) return [];
        if (command === "\\" && braces === 0 && environments.length === 0) return [];
        var argumentCount = knownArgumentCount(command), argumentStart = i + 1;
        if (command === "sqrt") {
          var index = /^\s*\[[^\[\]{}]*\]/.exec(source.slice(argumentStart));
          if (index) argumentStart += index[0].length;
        }
        // TeX permits bare arguments (even '=' as a denominator/exponent). Do not guess their extent.
        if (argumentCount && !groupedArguments(source, argumentStart, argumentCount)) return [];
        if (!argumentCount && /^[A-Za-z]+$/.test(command) && !knownIndependentCommand(command)
            && !/^(?:verb|begin|end|left|right|middle|lvert|rvert|vert|lVert|rVert|Vert|langle|rangle|lbrace|rbrace|lfloor|rfloor|lceil|rceil)$/.test(command)) return [];
        if (!/^[A-Za-z]+$/.test(command) && !/^[\\,;:!%{}_$&# |]$/.test(command)) return [];
        if (braces === 0 && environments.length === 0 && leftRight === 0) {
          var closing = { rangle: "langle", rbrace: "lbrace", rfloor: "lfloor", rceil: "lceil", rvert: "lvert", rVert: "lVert", "}": "{" };
          if (/^(?:langle|lbrace|lfloor|lceil|lvert|lVert|\{)$/.test(command)) {
            if (delimiters.length >= 64) return [];
            delimiters.push(command);
          } else if (Object.prototype.hasOwnProperty.call(closing, command)) {
            if (delimiters.pop() !== closing[command]) return [];
          } else if (/^(?:vert|Vert|\|)$/.test(command)) return [];
        }
        if (command === "verb") {
          if (source.charAt(i + 1) === "*") i++;
          var delimiter = source.charAt(++i), close = source.indexOf(delimiter, i + 1);
          if (!delimiter || /\s/.test(delimiter) || close < 0) return [];
          i = close;
        } else if (command === "begin" || command === "end") {
          var environment = /^\s*\{([A-Za-z*]+)\}/.exec(source.slice(i + 1));
          if (!environment) return [];
          i += environment[0].length;
          if (command === "begin") { if (environments.length >= 64) return []; environments.push(environment[1]); }
          else if (environments.pop() !== environment[1]) return [];
        } else if (command === "left" || command === "right") {
          if (command === "left") { if (++leftRight > 64) return []; }
          else if (--leftRight < 0) return [];
          // A delimiter can itself be a control sequence (\lvert, \{, ...).
          var side = /^\s*(?:\\(?:[A-Za-z]+|[^\r\n])|[^\s])/.exec(source.slice(i + 1));
          if (!side) return [];
          i += side[0].length;
        }
        previous = "\\" + command; continue;
      }
      if (ch === "%") return [];
      if ((ch === "^" || ch === "_") && /^\s*=/.test(source.slice(i + 1))) return [];
      if (ch === "{") { if (++braces > 64) return []; }
      else if (ch === "}") { if (--braces < 0) return []; }
      else if (braces === 0 && environments.length === 0 && leftRight === 0) {
        if (ch === "(" || ch === "[") { if (delimiters.length >= 64) return []; delimiters.push(ch); }
        else if (ch === ")" || ch === "]") {
          if (delimiters.pop() !== (ch === ")" ? "(" : "[")) return [];
        } else if ((ch === "," || ch === ";") && delimiters.length === 0) {
          // Independent assignments are not one equality chain.
          return [];
        } else if (ch === "=" && delimiters.length === 0 && previous !== "\\not"
            && !/[<>!:=]/.test(previous) && !/[=<>]/.test(source.charAt(i + 1))) {
          if (boundaries.length >= 64) return [];
          boundaries.push(i);
        } else if (ch === "=" && delimiters.length === 0 && (previous === "=" || source.charAt(i + 1) === "=")) {
          return [];
        }
      }
      if (!/\s/.test(ch)) previous = ch;
    }
    if (braces !== 0 || environments.length !== 0 || leftRight !== 0 || delimiters.length !== 0) return [];
    if (boundaries.length && (source.slice(0, boundaries[0]).trim() === ""
        || source.slice(boundaries[boundaries.length - 1] + 1).trim() === "")) return [];
    return boundaries;
  }

  function outerBox(source) {
    var match = /^\s*\\boxed\s*\{/.exec(source);
    if (!match) return null;
    var depth = 1;
    for (var i = match[0].length; i < source.length; i++) {
      if (source.charAt(i) === "\\") { i++; continue; }
      if (source.charAt(i) === "{") depth++;
      else if (source.charAt(i) === "}" && --depth === 0) {
        return source.slice(i + 1).trim() === "" ? source.slice(match[0].length, i) : null;
      }
    }
    return null;
  }

  function wrappedEquality(source) {
    var boxed = outerBox(source), body = boxed === null ? source : boxed;
    var boundaries = equalityBoundaries(body);
    if (!boundaries.length) return null;
    var lines = [], from = 0;
    for (var i = 0; i < boundaries.length; i++) {
      if (body.slice(from + (i ? 1 : 0), boundaries[i]).trim() === "") return null;
      lines.push(body.slice(from, boundaries[i]).trim()); from = boundaries[i];
    }
    if (body.slice(from + 1).trim() === "") return null;
    lines.push(body.slice(from).trim());
    var result = "\\begin{aligned}" + lines.map(function (line) { return "&\\displaystyle " + line; }).join("\\\\\n") + "\\end{aligned}";
    return boxed === null ? result : "\\boxed{" + result + "}";
  }

  function overflowing(cell) { return cell.clientWidth > 0 && cell.scrollWidth > cell.clientWidth + 1; }

  function boxedPlainText(source) {
    var body = outerBox(source);
    if (body === null) return null;
    // Only a single literal text argument. Commands, nested math and escapes retain KaTeX rendering.
    var match = /^\s*\\text\s*\{([^{}\\$%]*)\}\s*$/.exec(body);
    return match ? match[1] : null;
  }

  function showWideFinalSource(cell) {
    var fallback = element("div", "width-fallback");
    fallback.appendChild(element("span", "width-label", "This expression needs more width. Original notation:"));
    fallback.appendChild(element("pre", "wide-source", cell.mathSource));
    cell.replaceChildren(fallback); cell.mathWrapped = true; cell.finalFallback = true;
  }

  function fitMathRows() {
    var cells = root.querySelectorAll(".math-cell"), layoutWidth = root.clientWidth;
    for (var i = 0; i < cells.length; i++) {
      var cell = cells[i];
      if (!cell.mathValid || (cell.layoutWidth === layoutWidth && cell.fitWidth === cell.clientWidth)) continue;
      if (cell.layoutWidth !== undefined && cell.layoutWidth !== layoutWidth) {
        cell.parentNode.className = cell.parentNode.className.replace(/\s+wide-math\b/g, "");
        if (cell.mathWrapped) {
          var original = element("div", "math-render");
          try { renderKatex(original, cell.mathSource, true); cell.replaceChildren(original); cell.mathWrapped = false; cell.finalFallback = false; }
          catch (error) { /* Keep the last successful rendering if the engine is unavailable. */ }
        }
      }
      if (overflowing(cell) && !cell.mathWrapped) {
        var candidate = wrappedEquality(cell.mathSource);
        if (candidate) {
          // Keep the successfully rendered original if the presentation wrapper is unsupported.
          var rendered = element("div", "math-render");
          try { renderKatex(rendered, candidate, true); cell.replaceChildren(rendered); cell.mathWrapped = true; }
          catch (error) { /* Original source and rendering stay intact. */ }
        }
      }
      if (overflowing(cell) && cell.parentNode.className.indexOf("wide-math") < 0) cell.parentNode.className += " wide-math";
      if (overflowing(cell)) {
        var plainText = boxedPlainText(cell.mathSource);
        if (plainText !== null) {
          cell.replaceChildren(element("div", "boxed-text", plainText)); cell.mathWrapped = true;
        } else if (cell.isFinal) {
          // Do not conceal any part of an unsplittable final result behind horizontal scrolling.
          showWideFinalSource(cell);
        }
      }
      cell.fitWidth = cell.clientWidth; cell.layoutWidth = layoutWidth;
    }
  }

  // Preserve code source, including its delimiters, as literal monospace text. No Markdown HTML.
  function codeEnd(text, at, budget) {
    if (escaped(text, at)) return -1;
    var marker = text.charAt(at), count = 0;
    if (marker !== "`" && marker !== "~") return -1;
    while (text.charAt(at + count) === marker) count++;
    budget.remaining -= count;
    if (budget.remaining < 0) return text.length;
    var lineStart = text.lastIndexOf("\n", at - 1) + 1;
    var fenced = count >= 3 && /^ {0,3}$/.test(text.slice(lineStart, at));
    if (marker === "~" && !fenced) return -1;
    var from = at + count;
    while (from < text.length) {
      var next = text.indexOf(marker, from);
      budget.remaining -= (next < 0 ? text.length : next) - from;
      if (budget.remaining < 0) return text.length;
      if (next < 0) break;
      var length = 0;
      while (text.charAt(next + length) === marker) length++;
      budget.remaining -= length;
      if (budget.remaining < 0) return text.length;
      var closingLine = text.lastIndexOf("\n", next - 1) + 1;
      if (!escaped(text, next) && (fenced
          ? length >= count && /^ {0,3}$/.test(text.slice(closingLine, next))
            && /^[ \t]*(?:\r?\n|$)/.test(text.slice(next + length))
          : length === count)) return next + length;
      from = next + length;
    }
    return fenced ? text.length : -1;
  }

  function explanationText(text) {
    var node = element("span"), from = 0, at = 0, budget = { remaining: text.length * 4 + 64 };
    while (at < text.length) {
      var end = codeEnd(text, at, budget);
      if (end < 0) { at++; continue; }
      if (at > from) node.appendChild(element("span", "explanation-text", text.slice(from, at)));
      node.appendChild(element("code", "explanation-code", text.slice(at, end)));
      at = end; from = at;
    }
    if (from < text.length) node.appendChild(element("span", "explanation-text", text.slice(from)));
    return node;
  }

  function prose(text) {
    var node = element("div", "prose"), from = 0, at = 0, budget = { remaining: text.length * 4 + 64 };
    // Explicit delimiters only. A single dollar remains prose, including prices/currency.
    var pairs = [{open: "\\(", close: "\\)", display: false}, {open: "\\[", close: "\\]", display: true},
      {open: "$$", close: "$$", display: true}];
    while (at < text.length) {
      var code = codeEnd(text, at, budget);
      if (code >= 0) {
        if (at > from) node.appendChild(element("span", "explanation-text", text.slice(from, at)));
        node.appendChild(element("code", "explanation-code", text.slice(at, code)));
        at = code; from = at; continue;
      }
      var match = null;
      for (var p = 0; p < pairs.length; p++) {
        if (text.slice(at, at + pairs[p].open.length) === pairs[p].open && !escaped(text, at)) { match = pairs[p]; break; }
      }
      if (!match) { at++; continue; }
      var end = text.indexOf(match.close, at + match.open.length);
      while (end >= 0 && escaped(text, end)) end = text.indexOf(match.close, end + match.close.length);
      if (end < 0) { at += match.open.length; continue; }
      if (at > from) node.appendChild(element("span", "explanation-text", text.slice(from, at)));
      var math = element("span", "prose-math" + (match.display ? " display" : ""));
      renderMath(math, text.slice(at + match.open.length, end), match.display);
      node.appendChild(math); at = end + match.close.length; from = at;
    }
    if (from < text.length) node.appendChild(element("span", "explanation-text", text.slice(from)));
    return node;
  }

  function validDocument(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)
        || typeof value.before !== "string" || typeof value.after !== "string"
        || !Array.isArray(value.rows) || value.rows.length > MAX_ROWS) return false;
    var characters = value.before.length + value.after.length;
    for (var i = 0; i < value.rows.length; i++) {
      var row = value.rows[i];
      if (!row || typeof row !== "object" || typeof row.math !== "string" || typeof row.words !== "string") return false;
      characters += row.math.length + row.words.length;
      if (characters > MAX_CHARACTERS) return false;
    }
    return characters <= MAX_CHARACTERS;
  }

  function updateOverflowHints() {
    var cells = root.querySelectorAll(".math-cell");
    for (var i = 0; i < cells.length; i++) {
      var cell = cells[i], hint = cell.querySelector(".math-overflow-hint");
      var needsScroll = overflowing(cell) && !cell.finalFallback;
      if (needsScroll && !hint) cell.appendChild(element("span", "math-overflow-hint", "Long expression: scroll horizontally"));
      else if (!needsScroll && hint) cell.removeChild(hint);
    }
  }

  window.measureSolution = function () {
    if (window.solutionReady) { fitMathRows(); updateOverflowHints(); }
    var height = root.getBoundingClientRect().height;
    return Number.isFinite(height) ? Math.max(1, Math.min(MAX_HEIGHT, Math.ceil(height))) : 1;
  };

  function frame() { return new Promise(function (resolve) { window.requestAnimationFrame(resolve); }); }

  function waitForFonts(renderId) {
    var ready = document.fonts && document.fonts.ready && !window.solutionFontError ? document.fonts.ready : Promise.resolve();
    return ready.catch(function () {
      if (renderId === window.solutionRenderId && !window.solutionFontError) {
        window.solutionFontError = true; window.solutionErrors++;
        root.appendChild(element("div", "document-error", "Math fonts could not be loaded."));
      }
    });
  }

  window.renderSolution = function (value) {
    var renderId = ++window.solutionRenderId;
    window.solutionReady = false; window.solutionErrors = 0; window.solutionFontError = false;
    root.replaceChildren();
    root.className = "";
    if (!validDocument(value)) {
      root.appendChild(element("div", "document-error", "This reply could not be displayed."));
    } else {
      if (value.explanationFont === "andrea") root.className = "handwritten-explanations";
      if (value.showHeading === true) root.appendChild(element("h3", "solution-heading", "Solution"));
      if (value.before) root.appendChild(prose(value.before));
      var rows = element("div", "solution-rows");
      for (var i = 0; i < value.rows.length; i++) {
        var source = value.rows[i], row = element("div", "solution-row" + (i === value.rows.length - 1 ? " final-row" : ""));
        var math = element("div", "math-cell"), rendered = element("div", "math-render");
        math.setAttribute("tabindex", "0");
        math.setAttribute("aria-label", "Mathematical step");
        math.isFinal = i === value.rows.length - 1;
        math.mathSource = source.math; math.mathValid = renderMath(rendered, source.math, true);
        math.appendChild(rendered);
        var words = element("div", "words-cell"); words.appendChild(explanationText(source.words));
        row.appendChild(math); row.appendChild(words); rows.appendChild(row);
      }
      if (value.rows.length) root.appendChild(rows);
      if (value.after) root.appendChild(prose(value.after));
    }
    // Force current layout to initiate font requests before reading the font readiness promise.
    root.getBoundingClientRect();
    return frame().then(function () {
      return waitForFonts(renderId);
    }).then(frame).then(function () {
      if (renderId !== window.solutionRenderId) return;
      fitMathRows(); root.getBoundingClientRect();
      return waitForFonts(renderId);
    }).then(frame).then(function () {
      if (renderId !== window.solutionRenderId) return null;
      updateOverflowHints();
      window.solutionReady = true; return window.measureSolution();
    });
  };
}());
