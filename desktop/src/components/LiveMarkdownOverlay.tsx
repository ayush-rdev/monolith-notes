interface LiveMarkdownOverlayProps {
  content: string;
  fontSize?: string;
  lineHeight?: string;
}

const HEADING_PREFIX_REGEX = /^(#{1,6})\s+/;
const SEPARATOR_REGEX = /^(-{3,}|\*{3,}|_{3,}|─{3,}|={3,}|(-\s*){3,}|(\*\s*){3,}|(_\s*){3,})$/;

/**
 * 1-to-1 Web implementation of AppleNotesVisualTransformation.kt
 * Applies visual styling with ghost markdown tokens, glowing checklist bubbles, 
 * struck-through completed items, and horizontal dividers.
 */
export const LiveMarkdownOverlay: React.FC<LiveMarkdownOverlayProps> = ({
  content,
  fontSize = '17px',
  lineHeight = '26px'
}) => {
  const lines = content.split('\n');

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        pointerEvents: 'none',
        fontFamily: 'var(--font-text)',
        fontSize,
        lineHeight,
        color: '#FFFFFF',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        overflow: 'hidden'
      }}
    >
      {lines.map((line, idx) => {
        const trimmed = line.trimStart();
        const indentLength = line.length - trimmed.length;
        const indentSpaces = line.substring(0, indentLength);

        // 1. Completed Checklist: "✓ " or "- [x] " or "- [X] "
        if (
          trimmed.startsWith('✓ ') ||
          trimmed.startsWith('- [x] ') ||
          trimmed.startsWith('- [X] ')
        ) {
          const isCheckChar = trimmed.startsWith('✓ ');
          const prefix = isCheckChar ? '✓ ' : trimmed.substring(0, 6);
          const body = trimmed.substring(prefix.length);

          return (
            <div key={idx} style={{ minHeight: lineHeight }}>
              <span>{indentSpaces}</span>
              <span style={{ color: 'var(--accent)', fontWeight: 700 }}>
                {prefix}
              </span>
              <span
                style={{
                  color: 'var(--text-tertiary)',
                  textDecoration: 'line-through'
                }}
              >
                {renderInlineTokens(body)}
              </span>
            </div>
          );
        }

        // 2. Active Checklist: "○ " or "- [ ] "
        if (trimmed.startsWith('○ ') || trimmed.startsWith('- [ ] ')) {
          const isCircle = trimmed.startsWith('○ ');
          const prefix = isCircle ? '○ ' : '- [ ] ';
          const body = trimmed.substring(prefix.length);

          return (
            <div key={idx} style={{ minHeight: lineHeight }}>
              <span>{indentSpaces}</span>
              <span style={{ color: 'var(--accent)', fontWeight: 700 }}>
                {prefix}
              </span>
              <span>{renderInlineTokens(body)}</span>
            </div>
          );
        }

        // 3. Bullets: "• ", "- ", "* "
        if (
          trimmed.startsWith('• ') ||
          (trimmed.startsWith('- ') && !trimmed.startsWith('- [')) ||
          (trimmed.startsWith('* ') && !trimmed.startsWith('* ['))
        ) {
          const prefix = trimmed.substring(0, 2);
          const body = trimmed.substring(2);

          return (
            <div key={idx} style={{ minHeight: lineHeight }}>
              <span>{indentSpaces}</span>
              <span style={{ color: 'var(--accent)', fontWeight: 700 }}>
                {prefix}
              </span>
              <span>{renderInlineTokens(body)}</span>
            </div>
          );
        }

        // 4. Headings: H1 - H6
        const headingMatch = HEADING_PREFIX_REGEX.exec(trimmed);
        if (headingMatch) {
          const level = headingMatch[1].length;
          const prefix = headingMatch[0];
          const headingBody = trimmed.substring(prefix.length);

          const hFontSize =
            level === 1 ? '24px' :
            level === 2 ? '20px' :
            level === 3 ? '18px' :
            level === 4 ? '16px' : '15px';

          const hWeight = level <= 3 ? 700 : 600;

          return (
            <div
              key={idx}
              style={{
                fontSize: hFontSize,
                fontWeight: hWeight,
                minHeight: lineHeight
              }}
            >
              <span>{indentSpaces}</span>
              <span style={{ color: '#545458', fontSize: '13px', fontWeight: 400 }}>
                {prefix}
              </span>
              <span style={{ color: '#FFFFFF' }}>
                {renderInlineTokens(headingBody)}
              </span>
            </div>
          );
        }

        // 5. Blockquote: "> "
        if (trimmed.startsWith('> ')) {
          return (
            <div key={idx} style={{ minHeight: lineHeight }}>
              <span>{indentSpaces}</span>
              <span style={{ color: '#545458' }}>&gt; </span>
              <span style={{ fontStyle: 'italic', color: 'var(--text-secondary)' }}>
                {renderInlineTokens(trimmed.substring(2))}
              </span>
            </div>
          );
        }

        // 6. Horizontal Rule / Separator: "---", "***", "___", "───", "==="
        if (SEPARATOR_REGEX.test(trimmed.trim())) {
          return (
            <div
              key={idx}
              style={{
                minHeight: lineHeight,
                color: '#545458',
                letterSpacing: '6px',
                fontWeight: 700,
                opacity: 0.8
              }}
            >
              {line}
            </div>
          );
        }

        // Empty line
        if (trimmed.length === 0) {
          return <div key={idx} style={{ height: lineHeight }}>&nbsp;</div>;
        }

        // Normal text line with inline formatting
        return (
          <div key={idx} style={{ minHeight: lineHeight }}>
            <span>{indentSpaces}</span>
            <span>{renderInlineTokens(trimmed)}</span>
          </div>
        );
      })}
    </div>
  );
};

/**
 * Port of AppleNotesVisualTransformation inline parser
 */
function renderInlineTokens(text: string): React.ReactNode[] {
  if (!text) return [];

  // Match bold, italic, code, strikethrough, hashtags
  const REGEX = /(\*\*.*?\*\*|__.*?__|\*.*?\*|_.*?_|`.*?`|~~.*?~~|#[a-zA-Z0-9_\-/]+)/g;
  const parts: React.ReactNode[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = REGEX.exec(text)) !== null) {
    const start = match.index;
    const end = start + match[0].length;

    if (start > cursor) {
      parts.push(text.substring(cursor, start));
    }

    const val = match[0];
    const key = `inline-${start}-${end}`;

    // Bold
    if ((val.startsWith('**') && val.endsWith('**')) || (val.startsWith('__') && val.endsWith('__'))) {
      const inner = val.substring(2, val.length - 2);
      parts.push(
        <span key={key}>
          <span style={{ color: '#545458', fontSize: '11px' }}>{val.substring(0, 2)}</span>
          <strong style={{ fontWeight: 700, color: '#FFFFFF' }}>{inner}</strong>
          <span style={{ color: '#545458', fontSize: '11px' }}>{val.substring(0, 2)}</span>
        </span>
      );
    }
    // Italic
    else if ((val.startsWith('*') && val.endsWith('*')) || (val.startsWith('_') && val.endsWith('_'))) {
      const inner = val.substring(1, val.length - 1);
      parts.push(
        <span key={key}>
          <span style={{ color: '#545458', fontSize: '11px' }}>{val[0]}</span>
          <em style={{ fontStyle: 'italic', color: '#FFFFFF' }}>{inner}</em>
          <span style={{ color: '#545458', fontSize: '11px' }}>{val[0]}</span>
        </span>
      );
    }
    // Code
    else if (val.startsWith('`') && val.endsWith('`')) {
      const inner = val.substring(1, val.length - 1);
      parts.push(
        <span key={key}>
          <span style={{ color: '#545458', fontSize: '11px' }}>`</span>
          <code
            style={{
              fontFamily: 'var(--font-mono)',
              color: 'var(--accent)',
              backgroundColor: '#2C2C2E',
              padding: '0 2px'
            }}
          >
            {inner}
          </code>
          <span style={{ color: '#545458', fontSize: '11px' }}>`</span>
        </span>
      );
    }
    // Strikethrough
    else if (val.startsWith('~~') && val.endsWith('~~')) {
      const inner = val.substring(2, val.length - 2);
      parts.push(
        <span key={key}>
          <span style={{ color: '#545458', fontSize: '11px' }}>~~</span>
          <span style={{ textDecoration: 'line-through', color: 'var(--text-secondary)' }}>
            {inner}
          </span>
          <span style={{ color: '#545458', fontSize: '11px' }}>~~</span>
        </span>
      );
    }
    // Hashtags
    else if (val.startsWith('#')) {
      parts.push(
        <span key={key} style={{ color: 'var(--accent)', fontWeight: 600 }}>
          {val}
        </span>
      );
    } else {
      parts.push(val);
    }

    cursor = end;
  }

  if (cursor < text.length) {
    parts.push(text.substring(cursor));
  }

  return parts;
}
