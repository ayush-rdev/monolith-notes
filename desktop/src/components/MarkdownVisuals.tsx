import React, { useState, useEffect } from 'react';
import { Check, Copy } from 'lucide-react';
import { getAttachmentUrl, getCachedAttachmentUrl } from '../attachments';

interface MarkdownVisualsProps {
  content: string;
  onToggleTask?: (taskIndex: number) => void;
  fontSize?: string;
}

const SEPARATOR_REGEX = /^(-{3,}|\*{3,}|_{3,}|─{3,}|={3,}|(-\s*){3,}|(\*\s*){3,}|(_\s*){3,})$/;
const HEADING_REGEX = /^(#{1,6})\s+(.+)$/;
const TASK_LINE_REGEX = /^[\-*]\s+\[([ xX])\]\s*(.*)$/;

const VisualImage: React.FC<{ alt: string; src: string }> = ({ alt, src }) => {
  const [resolvedSrc, setResolvedSrc] = useState<string>(() => getCachedAttachmentUrl(src) || src);

  useEffect(() => {
    if (src.startsWith('attachments/')) {
      getAttachmentUrl(src).then((url) => {
        if (url) setResolvedSrc(url);
      });
    } else {
      setResolvedSrc(src);
    }
  }, [src]);

  return (
    <img
      src={resolvedSrc}
      alt={alt}
      style={{
        maxWidth: '100%',
        maxHeight: '460px',
        objectFit: 'contain',
        borderRadius: '10px',
        margin: '8px 0',
        display: 'block',
        backgroundColor: 'var(--bg-card)',
        border: '1px solid var(--divider)'
      }}
    />
  );
};

const VisualLink: React.FC<{
  label: React.ReactNode;
  rawUrl: string;
  isFileDownload?: boolean;
  downloadName?: string;
}> = ({ label, rawUrl, isFileDownload, downloadName }) => {
  const [resolvedUrl, setResolvedUrl] = useState<string>(() => getCachedAttachmentUrl(rawUrl) || rawUrl);

  useEffect(() => {
    if (rawUrl.startsWith('attachments/')) {
      getAttachmentUrl(rawUrl).then((url) => {
        if (url) setResolvedUrl(url);
      });
    } else {
      setResolvedUrl(rawUrl);
    }
  }, [rawUrl]);

  return (
    <a
      href={resolvedUrl}
      download={isFileDownload ? downloadName : undefined}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      style={{
        color: 'var(--blue, #0A84FF)',
        textDecoration: 'underline',
        cursor: 'pointer',
        fontWeight: 500,
        wordBreak: 'break-all'
      }}
      onMouseEnter={(e) => { e.currentTarget.style.opacity = '0.8'; }}
      onMouseLeave={(e) => { e.currentTarget.style.opacity = '1'; }}
    >
      {label}
    </a>
  );
};

/**
 * 1-to-1 JavaScript port of parseInlineMarkdown from Android MarkdownVisuals.kt
 */
export function parseInlineMarkdown(text: string, depth = 0): React.ReactNode[] {
  if (!text) return [];
  if (depth > 4) return [text];

  const elements: React.ReactNode[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;

  // MUST be local to each function call so recursive invocations never corrupt lastIndex
  const regex = /(!\[[^\]]*\]\([^)]*\)|\[[^\]]*\]\([^)]*\)|\*\*.*?\*\*|__.*?__|`.*?`|~~.*?~~|\*.*?\*|_.*?_|#[a-zA-Z0-9_\-/]+|https?:\/\/[^\s<]+)/g;

  while ((match = regex.exec(text)) !== null) {
    const start = match.index;
    const end = start + match[0].length;

    if (start > cursor) {
      elements.push(text.substring(cursor, start));
    }

    const matchValue = match[0];
    const key = `inline-${start}-${end}`;

    // Image: ![alt](url)
    if (matchValue.startsWith('![')) {
      const altEnd = matchValue.indexOf('](');
      const alt = matchValue.substring(2, altEnd);
      const url = matchValue.substring(altEnd + 2, matchValue.length - 1);
      elements.push(<VisualImage key={key} alt={alt} src={url} />);
    }
    // Link: [text](url) — renders as clickable blue link
    else if (matchValue.startsWith('[')) {
      const labelEnd = matchValue.indexOf('](');
      const label = matchValue.substring(1, labelEnd);
      const url = matchValue.substring(labelEnd + 2, matchValue.length - 1);
      const isFileDownload = url.startsWith('attachments/') || (url.startsWith('data:') && !url.startsWith('data:image/'));
      const downloadName = typeof label === 'string' ? label.replace(/^📄\s*/, '') : 'attachment';
      elements.push(
        <VisualLink
          key={key}
          label={parseInlineMarkdown(label, depth + 1)}
          rawUrl={url}
          isFileDownload={isFileDownload}
          downloadName={downloadName}
        />
      );
    }
    // Raw URL: https://... or http://...
    else if (matchValue.startsWith('http://') || matchValue.startsWith('https://')) {
      elements.push(
        <VisualLink
          key={key}
          label={matchValue}
          rawUrl={matchValue}
        />
      );
    }
    // Bold: **text** or __text__
    else if (
      (matchValue.startsWith('**') && matchValue.endsWith('**') && matchValue.length >= 4) ||
      (matchValue.startsWith('__') && matchValue.endsWith('__') && matchValue.length >= 4)
    ) {
      const inner = matchValue.substring(2, matchValue.length - 2);
      elements.push(
        <strong key={key} style={{ fontWeight: 700, color: 'inherit' }}>
          {parseInlineMarkdown(inner, depth + 1)}
        </strong>
      );
    }
    // Italic: *text* or _text_
    else if (
      (matchValue.startsWith('*') && matchValue.endsWith('*') && matchValue.length >= 2) ||
      (matchValue.startsWith('_') && matchValue.endsWith('_') && matchValue.length >= 2)
    ) {
      const inner = matchValue.substring(1, matchValue.length - 1);
      elements.push(
        <em key={key} style={{ fontStyle: 'italic', fontSynthesis: 'style', color: 'inherit' }}>
          {parseInlineMarkdown(inner, depth + 1)}
        </em>
      );
    }
    // Inline Code: `code`
    else if (matchValue.startsWith('`') && matchValue.endsWith('`') && matchValue.length >= 2) {
      const inner = matchValue.substring(1, matchValue.length - 1);
      elements.push(
        <code
          key={key}
          style={{
            fontFamily: 'var(--font-mono)',
            backgroundColor: 'var(--bg-elevated)',
            color: 'var(--accent)',
            fontSize: '0.88em',
            padding: '2px 6px',
            borderRadius: '4px',
            margin: '0 2px'
          }}
        >
          {inner}
        </code>
      );
    }
    // Strikethrough: ~~text~~
    else if (matchValue.startsWith('~~') && matchValue.endsWith('~~') && matchValue.length >= 4) {
      const inner = matchValue.substring(2, matchValue.length - 2);
      elements.push(
        <del key={key} style={{ textDecoration: 'line-through', color: 'inherit' }}>
          {parseInlineMarkdown(inner, depth + 1)}
        </del>
      );
    }
    // Hashtags: #tag
    else if (matchValue.startsWith('#')) {
      elements.push(
        <span key={key} style={{ color: 'var(--accent)', fontWeight: 600 }}>
          {matchValue}
        </span>
      );
    } else {
      elements.push(matchValue);
    }

    cursor = end;
  }

  if (cursor < text.length) {
    elements.push(text.substring(cursor));
  }

  return elements;
}

/**
 * 1-to-1 Port of Monolith MarkdownRenderView and components
 */
export const MarkdownRenderView: React.FC<MarkdownVisualsProps> = ({
  content,
  onToggleTask,
  fontSize = '17px'
}) => {
  const lines = content.split('\n');
  const renderedNodes: React.ReactNode[] = [];

  let taskCounter = 0;
  let inCodeBlock = false;
  let codeBlockLines: string[] = [];
  let codeBlockLang = '';

  let i = 0;

  // In Monolith, the first line if starting with `# ` is often the title header.
  // If the view already renders the Title above the canvas, skip the leading `# Title` line to prevent duplicate header.
  if (lines.length > 0 && lines[0].trim().startsWith('# ') && !lines[0].trim().startsWith('## ')) {
    i = 1;
    // Also skip following blank line if present
    if (i < lines.length && lines[i].trim().length === 0) {
      i++;
    }
  }

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    // Code Blocks (```)
    if (trimmed.startsWith('```')) {
      if (inCodeBlock) {
        const codeText = codeBlockLines.join('\n');
        const lang = codeBlockLang;
        renderedNodes.push(
          <CodeBlockCard key={`code-${i}`} code={codeText} language={lang} />
        );
        codeBlockLines = [];
        inCodeBlock = false;
      } else {
        inCodeBlock = true;
        codeBlockLang = trimmed.replace(/^```/, '').trim();
      }
      i++;
      continue;
    }

    if (inCodeBlock) {
      codeBlockLines.push(line);
      i++;
      continue;
    }

    // GFM Markdown Table
    if (trimmed.includes('|') && i + 1 < lines.length && isTableDelimiter(lines[i + 1])) {
      const headerLine = line;
      const delimiterLine = lines[i + 1];
      const dataRows: string[] = [];
      i += 2;
      while (i < lines.length) {
        const cand = lines[i].trim();
        if (cand.includes('|') && cand.length > 0) {
          dataRows.push(lines[i]);
          i++;
        } else {
          break;
        }
      }
      renderedNodes.push(
        <TableCard
          key={`table-${i}`}
          headerLine={headerLine}
          delimiterLine={delimiterLine}
          dataRows={dataRows}
        />
      );
      continue;
    }

    // Headings: H1 - H6
    const headingMatch = HEADING_REGEX.exec(trimmed);
    if (headingMatch) {
      const level = headingMatch[1].length;
      const headingText = headingMatch[2];
      const headingFontSize =
        level === 1 ? '24px' :
        level === 2 ? '20px' :
        level === 3 ? '18px' :
        level === 4 ? '16px' : '15px';

      const topMargin = i === 0 ? '0px' : (level === 1 ? '12px' : level === 2 ? '8px' : '6px');

      renderedNodes.push(
        <div
          key={`heading-${i}`}
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: headingFontSize,
            fontWeight: level <= 3 ? 700 : 600,
            color: 'var(--text-primary)',
            marginTop: topMargin,
            marginBottom: '4px',
            lineHeight: '1.3'
          }}
        >
          {parseInlineMarkdown(headingText)}
        </div>
      );
      i++;
      continue;
    }

    // Horizontal Rule / Separator: ---, ***, ___, ───, ===
    if (SEPARATOR_REGEX.test(trimmed)) {
      renderedNodes.push(
        <div
          key={`sep-${i}`}
          style={{
            padding: '16px 0',
            display: 'flex',
            alignItems: 'center',
            width: '100%'
          }}
        >
          <div
            style={{
              width: '100%',
              height: '1px',
              backgroundColor: 'rgba(255, 255, 255, 0.12)',
              borderBottom: '0.5px solid var(--divider)'
            }}
          />
        </div>
      );
      i++;
      continue;
    }

    // Circular Apple Notes Task Checkbox (supports both ○/✓ and - [ ]/- [x])
    const taskMatch = TASK_LINE_REGEX.exec(trimmed);
    const isCircleTask = trimmed.startsWith('○ ') || trimmed.startsWith('✓ ');

    if (taskMatch || isCircleTask) {
      const isChecked = isCircleTask
        ? trimmed.startsWith('✓ ')
        : (taskMatch ? taskMatch[1].toLowerCase() === 'x' : false);

      const taskText = isCircleTask
        ? trimmed.substring(2)
        : (taskMatch ? taskMatch[2] : '');

      const currentIdx = taskCounter++;

      renderedNodes.push(
        <div
          key={`task-${i}`}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            padding: '4px 2px',
            userSelect: 'text'
          }}
        >
          {/* Apple Notes Circular Checklist Bubble (unselectable circle) */}
          <div
            onClick={(e) => {
              e.stopPropagation();
              onToggleTask && onToggleTask(currentIdx);
            }}
            data-no-double-click="true"
            style={{
              width: '20px',
              height: '20px',
              borderRadius: '50%',
              backgroundColor: isChecked ? 'var(--accent)' : 'transparent',
              border: isChecked ? `1.5px solid var(--accent)` : `1.5px solid var(--text-secondary)`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
              cursor: 'pointer',
              userSelect: 'none',
              transition: 'all 0.15s ease'
            }}
          >
            {isChecked && <Check size={13} color="#000000" strokeWidth={3} />}
          </div>

          <span
            style={{
              fontFamily: 'var(--font-text)',
              fontSize,
              color: isChecked ? 'var(--text-tertiary)' : 'var(--text-primary)',
              textDecoration: isChecked ? 'line-through' : 'none',
              lineHeight: '1.4',
              cursor: 'text',
              userSelect: 'text'
            }}
          >
            {parseInlineMarkdown(taskText)}
          </span>
        </div>
      );
      i++;
      continue;
    }

    // Bullet List (- or *)
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      const bulletText = trimmed.substring(2);
      renderedNodes.push(
        <div
          key={`bullet-${i}`}
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: '10px',
            padding: '3px 4px'
          }}
        >
          <div
            style={{
              width: '5px',
              height: '5px',
              borderRadius: '50%',
              backgroundColor: 'var(--accent)',
              marginTop: '9px',
              flexShrink: 0
            }}
          />
          <span
            style={{
              fontFamily: 'var(--font-text)',
              fontSize,
              lineHeight: '1.5',
              color: 'var(--text-primary)'
            }}
          >
            {parseInlineMarkdown(bulletText)}
          </span>
        </div>
      );
      i++;
      continue;
    }

    // Numbered List (1. , 2. , etc.)
    const numMatch = /^(\d+)\.\s+(.*)/.exec(trimmed);
    if (numMatch) {
      const numVal = numMatch[1];
      const numText = numMatch[2];
      renderedNodes.push(
        <div
          key={`num-${i}`}
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: '8px',
            padding: '3px 4px',
            userSelect: 'text',
            cursor: 'text'
          }}
        >
          <span
            style={{
              fontFamily: 'var(--font-mono)',
              fontSize: '13px',
              fontWeight: 600,
              color: 'var(--accent)',
              minWidth: '22px',
              flexShrink: 0,
              textAlign: 'right',
              userSelect: 'none',
              lineHeight: '1.5'
            }}
          >
            {numVal}.
          </span>
          <span
            style={{
              fontFamily: 'var(--font-text)',
              fontSize,
              lineHeight: '1.5',
              color: 'var(--text-primary)'
            }}
          >
            {parseInlineMarkdown(numText)}
          </span>
        </div>
      );
      i++;
      continue;
    }

    // Blockquote (> )
    if (trimmed.startsWith('> ')) {
      const quoteText = trimmed.substring(2);
      renderedNodes.push(
        <div
          key={`quote-${i}`}
          style={{
            display: 'flex',
            alignItems: 'center',
            backgroundColor: 'var(--bg-card)',
            borderRadius: '8px',
            padding: '8px 14px',
            margin: '6px 0',
            gap: '10px'
          }}
        >
          <div
            style={{
              width: '3px',
              height: '22px',
              borderRadius: '2px',
              backgroundColor: 'var(--accent)',
              flexShrink: 0
            }}
          />
          <span
            style={{
              fontFamily: 'var(--font-text)',
              fontStyle: 'italic',
              fontSize,
              color: 'var(--text-secondary)'
            }}
          >
            {parseInlineMarkdown(quoteText)}
          </span>
        </div>
      );
      i++;
      continue;
    }

    // Blank line (consistent with edit mode line height)
    if (trimmed.length === 0) {
      renderedNodes.push(<div key={`blank-${i}`} style={{ height: '22px' }} />);
      i++;
      continue;
    }

    // Normal Paragraph
    renderedNodes.push(
      <div
        key={`para-${i}`}
        style={{
          fontFamily: 'var(--font-text)',
          fontSize,
          lineHeight: '1.6',
          color: 'var(--text-primary)',
          minHeight: '22px'
        }}
      >
        {parseInlineMarkdown(trimmed)}
      </div>
    );
    i++;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', cursor: 'text', userSelect: 'text' }}>
      {renderedNodes}
    </div>
  );
};

// Table Helper
function isTableDelimiter(line: string): boolean {
  const trimmed = line.trim();
  if (!trimmed.includes('|') || !trimmed.includes('-')) return false;
  const inner = trimmed.replace(/^\|/, '').replace(/\|$/, '').trim();
  if (!inner) return false;
  const parts = inner.split('|');
  return parts.every(col => /^:?-{1,}:?$/.test(col.trim()));
}

function splitTableRow(line: string): string[] {
  let trimmed = line.trim();
  if (trimmed.startsWith('|')) trimmed = trimmed.substring(1);
  if (trimmed.endsWith('|')) trimmed = trimmed.substring(0, trimmed.length - 1);
  return trimmed.split('|').map(c => c.trim());
}

const TableCard: React.FC<{
  headerLine: string;
  delimiterLine: string;
  dataRows: string[];
}> = ({ headerLine, dataRows }) => {
  const headers = splitTableRow(headerLine);
  const rows = dataRows.map(r => splitTableRow(r));

  return (
    <div
      style={{
        margin: '12px 0',
        borderRadius: '12px',
        backgroundColor: 'var(--bg-card)',
        border: '1px solid var(--divider)',
        overflow: 'hidden'
      }}
    >
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr style={{ backgroundColor: 'rgba(255,255,255,0.05)', borderBottom: '1px solid var(--divider)' }}>
              {headers.map((h, idx) => (
                <th
                  key={idx}
                  style={{
                    padding: '10px 14px',
                    fontFamily: 'var(--font-display)',
                    fontWeight: 700,
                    fontSize: '14px',
                    color: '#FFFFFF'
                  }}
                >
                  {parseInlineMarkdown(h)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, rIdx) => (
              <tr
                key={rIdx}
                style={{
                  borderBottom: rIdx < rows.length - 1 ? '0.5px solid var(--divider)' : 'none'
                }}
              >
                {row.map((cell, cIdx) => (
                  <td
                    key={cIdx}
                    style={{
                      padding: '10px 14px',
                      fontFamily: 'var(--font-text)',
                      fontSize: '14px',
                      color: 'var(--text-primary)'
                    }}
                  >
                    {parseInlineMarkdown(cell)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const CodeBlockCard: React.FC<{ code: string; language: string }> = ({ code, language }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div
      style={{
        margin: '10px 0',
        borderRadius: '12px',
        backgroundColor: 'var(--bg-card)',
        border: '1px solid var(--divider)',
        overflow: 'hidden'
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 14px',
          backgroundColor: 'rgba(255,255,255,0.03)',
          borderBottom: '0.5px solid var(--divider)'
        }}
      >
        <span
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: '11px',
            textTransform: 'uppercase',
            color: 'var(--text-tertiary)'
          }}
        >
          {language || 'code'}
        </span>
        <button
          onClick={handleCopy}
          style={{
            background: 'none',
            border: 'none',
            color: copied ? 'var(--emerald)' : 'var(--accent)',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            fontSize: '11px',
            fontFamily: 'var(--font-text)'
          }}
        >
          {copied ? <Check size={12} /> : <Copy size={12} />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>

      <pre
        style={{
          padding: '14px',
          margin: 0,
          fontFamily: 'var(--font-mono)',
          fontSize: '13px',
          color: 'var(--text-primary)',
          overflowX: 'auto',
          lineHeight: '1.5'
        }}
      >
        <code>{code}</code>
      </pre>
    </div>
  );
};
