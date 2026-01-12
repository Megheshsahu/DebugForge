// Kotlin syntax highlighting - returns HTML with highlighted spans
export function highlightKotlin(code: string): string {
  // Keywords
  const keywords = [
    'package', 'import', 'class', 'object', 'interface', 'fun', 'val', 'var',
    'if', 'else', 'when', 'for', 'while', 'do', 'try', 'catch', 'finally',
    'return', 'throw', 'break', 'continue', 'as', 'is', 'in', 'out',
    'suspend', 'override', 'open', 'final', 'abstract', 'private', 'protected',
    'public', 'internal', 'sealed', 'data', 'enum', 'companion', 'inline',
    'expect', 'actual', 'typealias', 'null', 'true', 'false', 'this', 'super',
    'lateinit', 'by', 'where', 'get', 'set', 'constructor', 'init'
  ];

  // Types
  const types = [
    'String', 'Int', 'Long', 'Float', 'Double', 'Boolean', 'Char', 'Unit',
    'Any', 'Nothing', 'Array', 'List', 'Map', 'Set', 'MutableList', 'MutableMap',
    'MutableSet', 'Flow', 'StateFlow', 'MutableStateFlow', 'SharedFlow',
    'Result', 'Pair', 'Triple', 'Sequence', 'Exception', 'Throwable'
  ];

  let result = code;

  // Escape HTML first
  result = result
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // Strings (double and triple quotes)
  result = result.replace(
    /"""[\s\S]*?"""|"(?:[^"\\]|\\.)*"/g,
    '<span class="token-string">$&</span>'
  );

  // Comments
  result = result.replace(
    /\/\/.*$/gm,
    '<span class="token-comment">$&</span>'
  );

  // Annotations
  result = result.replace(
    /@\w+/g,
    '<span class="token-annotation">$&</span>'
  );

  // Numbers
  result = result.replace(
    /\b(\d+\.?\d*[fFL]?)\b/g,
    '<span class="token-number">$1</span>'
  );

  // Keywords
  keywords.forEach(kw => {
    const regex = new RegExp(`\\b(${kw})\\b`, 'g');
    result = result.replace(regex, '<span class="token-keyword">$1</span>');
  });

  // Types
  types.forEach(type => {
    const regex = new RegExp(`\\b(${type})\\b`, 'g');
    result = result.replace(regex, '<span class="token-type">$1</span>');
  });

  // Function calls
  result = result.replace(
    /\b([a-z_][a-zA-Z0-9_]*)\s*\(/g,
    '<span class="token-function">$1</span>('
  );

  // Type references with generics
  result = result.replace(
    /\b([A-Z][a-zA-Z0-9_]*)\b(?![^<]*>)/g,
    (match, p1) => {
      if (types.includes(p1)) return match; // Already highlighted
      return `<span class="token-type">${p1}</span>`;
    }
  );

  return result;
}
