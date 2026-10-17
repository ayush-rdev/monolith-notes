import React, { useRef, useEffect, useCallback, useImperativeHandle, forwardRef } from 'react';
import { saveAttachment } from '../attachments';

export interface VisualEditorRef {
  toggleLinePrefix: (targetPrefix: string, alternatePrefix?: string) => void;
  wrapSelection: (token: string) => void;
  insertDivider: () => void;
  insertText: (text: string) => void;
  insertFile: (file: File) => void;
  focus: () => void;
  getSelectionRange: () => { start: number; end: number };
  setSelectionRange: (start: number, end: number) => void;
}

interface VisualEditorProps {
  content: string;
  onChange: (newContent: string) => void;
  onKeyDown?: (e: React.KeyboardEvent<HTMLTextAreaElement>) => void;
  fontSize?: string;
  lineHeight?: string;
}

export const HEADING_PREFIX_REGEX = /^(#{1,6})\s+/;
export const NUMBERED_LIST_PREFIX_REGEX = /^(\d+)\.\s+/;
export const SEPARATOR_REGEX = /^(-{3,}|\*{3,}|_{3,}|─{3,}|={3,}|(-\s*){3,}|(\*\s*){3,}|(_\s*){3,})$/;
export const LINE_PREFIX_REGEX = /^(#{1,6}\s+|○\s+|✓\s+|•\s+|\-\s+\[[ xX]\]\s+|\-\s+|\*\s+|\d+\.\s+|>\s+)/;

export function splitLinePrefix(str: string): { prefix: string; body: string } {
  const match = LINE_PREFIX_REGEX.exec(str);
  if (match) {
    return { prefix: match[0], body: str.substring(match[0].length) };
  }
  return { prefix: '', body: str };
}

/**
 * Textarea + overlay approach identical in spirit to Android VisualTransformation.
 * Textarea holds raw markdown (source of truth). Overlay div renders syntax-highlighted HTML on top.
 * All cursor / selection / typing is 100% native — no DOM extraction, no innerText bugs.
 */
export const VisualEditor = forwardRef<VisualEditorRef, VisualEditorProps>((
  { content, onChange, onKeyDown, fontSize = '17px', lineHeight = '26px' },
  ref
) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);

  const sharedStyle: React.CSSProperties = {
    fontFamily: 'var(--font-text)',
    fontSize,
    lineHeight,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
    overflowWrap: 'break-word',
    padding: 0,
    margin: 0,
    border: 'none',
    outline: 'none',
    width: '100%',
    minHeight: '400px',
    boxSizing: 'border-box' as const,
    letterSpacing: '0px',
    wordSpacing: '0px',
    fontVariantLigatures: 'none',
    fontFeatureSettings: '"kern" 0',
  };

  const renderFormattedHtml = useCallback((text: string, lh: string): string => {
    if (!text) return `<div class="editor-line" style="line-height:${lh}"><br></div>`;

    const lines = text.split('\n');
    return lines.map((line) => {
      const trimmed = line.trimStart();
      const indent = line.substring(0, line.length - trimmed.length);
      const lhs = `line-height:${lh}`;

      if (trimmed.startsWith('✓ ') || trimmed.startsWith('- [x] ') || trimmed.startsWith('- [X] ')) {
        const prefix = trimmed.startsWith('✓ ') ? '✓ ' : trimmed.substring(0, 6);
        const rest = trimmed.substring(prefix.length);
        return `<div class="editor-line" style="${lhs}">${escapeHtml(indent)}<span style="color:var(--accent);-webkit-text-stroke:0.4px var(--accent);text-shadow:0 0 0.5px var(--accent)">${escapeHtml(prefix)}</span><span style="color:var(--text-secondary);text-decoration:line-through">${formatInlineSpans(rest)}</span></div>`;
      }

      if (trimmed.startsWith('○ ') || trimmed.startsWith('- [ ] ')) {
        const prefix = trimmed.startsWith('○ ') ? '○ ' : '- [ ] ';
        const rest = trimmed.substring(prefix.length);
        return `<div class="editor-line" style="${lhs}">${escapeHtml(indent)}<span style="color:var(--accent);-webkit-text-stroke:0.4px var(--accent);text-shadow:0 0 0.5px var(--accent)">${escapeHtml(prefix)}</span><span>${formatInlineSpans(rest)}</span></div>`;
      }

      if (trimmed.startsWith('• ') || (trimmed.startsWith('- ') && !trimmed.startsWith('- [')) || (trimmed.startsWith('* ') && !trimmed.startsWith('* ['))) {
        const prefix = trimmed.substring(0, 2);
        const rest = trimmed.substring(2);
        return `<div class="editor-line" style="${lhs}">${escapeHtml(indent)}<span style="color:var(--accent);-webkit-text-stroke:0.4px var(--accent);text-shadow:0 0 0.5px var(--accent)">${escapeHtml(prefix)}</span><span>${formatInlineSpans(rest)}</span></div>`;
      }

      const numberedMatch = NUMBERED_LIST_PREFIX_REGEX.exec(trimmed);
      if (numberedMatch) {
        const prefix = numberedMatch[0];
        const rest = trimmed.substring(prefix.length);
        return `<div class="editor-line" style="${lhs}">${escapeHtml(indent)}<span style="color:var(--accent);-webkit-text-stroke:0.4px var(--accent);text-shadow:0 0 0.5px var(--accent)">${escapeHtml(prefix)}</span><span>${formatInlineSpans(rest)}</span></div>`;
      }

      const headingMatch = HEADING_PREFIX_REGEX.exec(trimmed);
      if (headingMatch) {
        const prefix = headingMatch[0];
        const rest = trimmed.substring(prefix.length);
        // Uniform line-height and metrics so cursor is always 100% aligned
        return `<div class="editor-line" style="color:#FFF;-webkit-text-stroke:0.45px #FFF;text-shadow:0 0 0.5px #FFF;${lhs}">${escapeHtml(indent)}<span style="color:#545458;-webkit-text-stroke:0;text-shadow:none">${escapeHtml(prefix)}</span><span>${formatInlineSpans(rest)}</span></div>`;
      }

      if (trimmed.startsWith('> ')) {
        const rest = trimmed.substring(2);
        return `<div class="editor-line" style="color:var(--text-secondary);font-style:italic;${lhs}">${escapeHtml(indent)}<span style="color:#545458;font-style:normal">&gt; </span><span>${formatInlineSpans(rest)}</span></div>`;
      }

      if (SEPARATOR_REGEX.test(trimmed.trim())) {
        return `<div class="editor-line" style="color:#545458;${lhs}">${escapeHtml(line)}</div>`;
      }

      if (line.length === 0) return `<div class="editor-line" style="${lhs}"><br></div>`;

      return `<div class="editor-line" style="${lhs}">${escapeHtml(indent)}${formatInlineSpans(trimmed)}</div>`;
    }).join('');
  }, []);

  useEffect(() => {
    if (overlayRef.current) {
      overlayRef.current.innerHTML = renderFormattedHtml(content, lineHeight);
    }
    const ta = textareaRef.current;
    const ov = overlayRef.current;
    if (ta && ov) {
      ta.style.height = 'auto';
      const newHeight = Math.max(400, ta.scrollHeight);
      ta.style.height = `${newHeight}px`;
      ov.style.height = `${newHeight}px`;
    }
  }, [content, lineHeight, renderFormattedHtml]);

  const syncScroll = () => {
    if (textareaRef.current && overlayRef.current) {
      overlayRef.current.scrollTop = textareaRef.current.scrollTop;
    }
  };

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    onChange(e.target.value);
  };

  useImperativeHandle(ref, () => ({
    focus: () => textareaRef.current?.focus(),

    getSelectionRange: () => {
      const ta = textareaRef.current;
      if (!ta) return { start: 0, end: 0 };
      return { start: ta.selectionStart, end: ta.selectionEnd };
    },

    setSelectionRange: (start: number, end: number) => {
      const ta = textareaRef.current;
      if (!ta) return;
      ta.focus();
      ta.setSelectionRange(start, end);
    },

    toggleLinePrefix: (targetPrefix: string, alternatePrefix?: string) => {
      const ta = textareaRef.current;
      if (!ta) return;
      const text = ta.value;
      const selStart = ta.selectionStart;
      const selEnd = ta.selectionEnd;

      // Find the start of the first selected line
      const firstLineStart = text.lastIndexOf('\n', selStart - 1) + 1;

      // Find the end of the last selected line
      let lastLineEnd: number;
      if (selEnd > selStart && text[selEnd - 1] === '\n') {
        lastLineEnd = selEnd - 1;
      } else {
        const nextNewline = text.indexOf('\n', selEnd);
        lastLineEnd = nextNewline === -1 ? text.length : nextNewline;
      }

      const fullBlock = text.substring(firstLineStart, lastLineEnd);
      const lines = fullBlock.split('\n');

      const isNumbered = /^\d+\.\s/.test(targetPrefix);

      // Check if all non-empty lines already have the target prefix (or alternate)
      const nonEmptyLines = lines.filter((l) => l.trim().length > 0);
      const allHavePrefix =
        nonEmptyLines.length > 0 &&
        nonEmptyLines.every((l) => {
          const trimmed = l.trimStart();
          if (isNumbered) {
            return /^\d+\.\s/.test(trimmed);
          }
          return (
            trimmed.startsWith(targetPrefix) ||
            (alternatePrefix ? trimmed.startsWith(alternatePrefix) : false)
          );
        });

      let newLines: string[];
      if (allHavePrefix) {
        // UNTASK: Remove prefix from all lines
        newLines = lines.map((line) => {
          const trimmed = line.trimStart();
          const indent = line.substring(0, line.length - trimmed.length);
          if (isNumbered) {
            return indent + trimmed.replace(/^\d+\.\s+/, '');
          }
          if (trimmed.startsWith(targetPrefix)) {
            return indent + trimmed.substring(targetPrefix.length);
          }
          if (alternatePrefix && trimmed.startsWith(alternatePrefix)) {
            return indent + trimmed.substring(alternatePrefix.length);
          }
          return line;
        });
      } else {
        // TASK: Add targetPrefix to all lines (replacing any existing list/task prefix)
        const isSingleLine = lines.length === 1;
        let numCounter = 1;
        newLines = lines.map((line) => {
          if (!line.trim()) {
            return isSingleLine ? (isNumbered ? '1. ' : targetPrefix) : line;
          }
          const trimmed = line.trimStart();
          const indent = line.substring(0, line.length - trimmed.length);
          const clean = trimmed.replace(
            /^(#{1,6}\s+|○\s+|✓\s+|•\s+|\-\s+\[[ xX]\]\s+|\-\s+|\*\s+|\d+\.\s+|>\s+)/,
            ''
          );
          const prefix = isNumbered ? `${numCounter++}. ` : targetPrefix;
          return indent + prefix + clean;
        });
      }

      const replacement = newLines.join('\n');
      const newText = text.substring(0, firstLineStart) + replacement + text.substring(lastLineEnd);
      onChange(newText);

      setTimeout(() => {
        if (textareaRef.current) {
          textareaRef.current.focus();
          if (selStart === selEnd) {
            // Single cursor: if line was empty, place cursor right after targetPrefix
            const wasEmptyLine = lines.length === 1 && !lines[0].trim();
            const newCursor = wasEmptyLine
              ? firstLineStart + targetPrefix.length
              : Math.max(firstLineStart, Math.min(newText.length, selStart + (replacement.length - fullBlock.length)));
            textareaRef.current.setSelectionRange(newCursor, newCursor);
          } else {
            // Range selection: keep the entire formatted block selected
            textareaRef.current.setSelectionRange(firstLineStart, firstLineStart + replacement.length);
          }
        }
      }, 0);
    },

    wrapSelection: (token: string) => {
      const ta = textareaRef.current;
      if (!ta) return;
      const text = ta.value;
      const start = ta.selectionStart;
      const end = ta.selectionEnd;
      const tLen = token.length;
      const selected = text.substring(start, end);

      if (start === end) {
        // Nothing selected: insert token pair and place cursor between them
        const replacement = `${token}${token}`;
        const newCursor = start + tLen;
        onChange(text.substring(0, start) + replacement + text.substring(end));
        setTimeout(() => {
          ta.focus();
          ta.setSelectionRange(newCursor, newCursor);
        }, 0);
        return;
      }

      // If selection spans multiple lines, handle line by line!
      if (selected.includes('\n')) {
        const lines = selected.split('\n');
        const nonEmptyLines = lines.filter((l) => l.trim().length > 0);
        const allWrapped =
          nonEmptyLines.length > 0 &&
          nonEmptyLines.every((l) => {
            const trimmed = l.trim();
            const { body } = splitLinePrefix(trimmed);
            return body.startsWith(token) && body.endsWith(token) && body.length >= tLen * 2;
          });

        let newLines: string[];
        if (allWrapped) {
          // Unwrap all lines: remove token from start and end of each line body
          newLines = lines.map((l) => {
            const trimmedStart = l.trimStart();
            const indent = l.substring(0, l.length - trimmedStart.length);
            const trimmed = trimmedStart.trimEnd();
            const trailing = trimmedStart.substring(trimmed.length);

            const { prefix, body } = splitLinePrefix(trimmed);
            if (body.startsWith(token) && body.endsWith(token) && body.length >= tLen * 2) {
              return `${indent}${prefix}${body.slice(tLen, -tLen)}${trailing}`;
            }
            return l;
          });
        } else {
          // Wrap all non-empty lines with token (leaving prefix outside!)
          newLines = lines.map((l) => {
            if (!l.trim()) return l;
            const trimmedStart = l.trimStart();
            const indent = l.substring(0, l.length - trimmedStart.length);
            const trimmed = trimmedStart.trimEnd();
            const trailing = trimmedStart.substring(trimmed.length);

            const { prefix, body } = splitLinePrefix(trimmed);
            if (body.startsWith(token) && body.endsWith(token) && body.length >= tLen * 2) {
              return l;
            }
            return `${indent}${prefix}${token}${body}${token}${trailing}`;
          });
        }

        const replacement = newLines.join('\n');
        onChange(text.substring(0, start) + replacement + text.substring(end));
        setTimeout(() => {
          ta.focus();
          ta.setSelectionRange(start, start + replacement.length);
        }, 0);
        return;
      }

      // Single line selection
      // If the selection has a prefix (e.g. user selected whole task line "○ task text")
      const trimmedSel = selected.trim();
      const { prefix: selPrefix, body: selBody } = splitLinePrefix(trimmedSel);
      if (selPrefix && selBody) {
        const isWrapped = selBody.startsWith(token) && selBody.endsWith(token) && selBody.length >= tLen * 2;
        const newBody = isWrapped ? selBody.slice(tLen, -tLen) : `${token}${selBody}${token}`;
        const replacement = `${selPrefix}${newBody}`;
        onChange(text.substring(0, start) + replacement + text.substring(end));
        setTimeout(() => {
          ta.focus();
          ta.setSelectionRange(start, start + replacement.length);
        }, 0);
        return;
      }

      // Check if already wrapped inside selection
      if (selected.startsWith(token) && selected.endsWith(token) && selected.length >= tLen * 2) {
        const unwrapped = selected.slice(tLen, -tLen);
        onChange(text.substring(0, start) + unwrapped + text.substring(end));
        setTimeout(() => {
          ta.focus();
          ta.setSelectionRange(start, start + unwrapped.length);
        }, 0);
        return;
      }

      // Check if wrapped outside selection (e.g. *|selected|*)
      if (start >= tLen && end + tLen <= text.length) {
        const before = text.substring(start - tLen, start);
        const after = text.substring(end, end + tLen);
        if (before === token && after === token) {
          onChange(text.substring(0, start - tLen) + selected + text.substring(end + tLen));
          setTimeout(() => {
            ta.focus();
            ta.setSelectionRange(start - tLen, end - tLen);
          }, 0);
          return;
        }
      }

      // Wrap single line selection
      const replacement = `${token}${selected}${token}`;
      onChange(text.substring(0, start) + replacement + text.substring(end));
      setTimeout(() => {
        ta.focus();
        ta.setSelectionRange(start + tLen, start + tLen + selected.length);
      }, 0);
    },

    insertDivider: () => {
      const ta = textareaRef.current;
      if (!ta) return;
      const text = ta.value;
      const cursor = ta.selectionStart;
      const needsNewline = cursor > 0 && text[cursor - 1] !== '\n';
      const separator = (needsNewline ? '\n' : '') + '---\n';
      const newText = text.substring(0, cursor) + separator + text.substring(cursor);
      onChange(newText);
      setTimeout(() => { ta.focus(); ta.setSelectionRange(cursor + separator.length, cursor + separator.length); }, 0);
    },

    insertText: (text: string) => {
      insertTextAtCursor(text);
    },

    insertFile: (file: File) => {
      processFile(file);
    },
  }), [onChange]);

  const insertTextAtCursor = useCallback((textToInsert: string) => {
    const ta = textareaRef.current;
    if (!ta) return;
    const current = ta.value;
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const newText = current.substring(0, start) + textToInsert + current.substring(end);
    const newOffset = start + textToInsert.length;
    onChange(newText);
    setTimeout(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        textareaRef.current.setSelectionRange(newOffset, newOffset);
      }
    }, 0);
  }, [onChange]);

  const processFile = useCallback(async (file: File) => {
    if (file.type.startsWith('image/')) {
      const name = file.name || 'image.png';
      try {
        const attachmentPath = await saveAttachment(file, name);
        const displayName = name.replace(/\.[^/.]+$/, '');
        insertTextAtCursor(`\n\n![${displayName}](${attachmentPath})\n\n`);
      } catch {
        // Fallback to data URL if IndexedDB fails
        const reader = new FileReader();
        reader.onload = () => {
          insertTextAtCursor(`\n\n![Image](${reader.result as string})\n\n`);
        };
        reader.readAsDataURL(file);
      }
    } else if (file.type.startsWith('text/') || file.name.endsWith('.md') || file.name.endsWith('.txt')) {
      const reader = new FileReader();
      reader.onload = () => {
        const text = reader.result as string;
        insertTextAtCursor(`\n\n${text}\n\n`);
      };
      reader.readAsText(file);
    } else {
      const name = file.name || 'Document';
      try {
        const attachmentPath = await saveAttachment(file, name);
        insertTextAtCursor(`\n\n📄 [${name}](${attachmentPath})\n\n`);
      } catch {
        const reader = new FileReader();
        reader.onload = () => {
          insertTextAtCursor(`\n\n📄 [${name}](${reader.result as string})\n\n`);
        };
        reader.readAsDataURL(file);
      }
    }
  }, [insertTextAtCursor]);

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const items = e.clipboardData?.items;
    if (items && items.length > 0) {
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        if (item.kind === 'file') {
          const file = item.getAsFile();
          if (file) {
            e.preventDefault();
            processFile(file);
            return;
          }
        }
      }
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLTextAreaElement>) => {
    e.preventDefault();
    const files = e.dataTransfer?.files;
    if (files && files.length > 0) {
      for (let i = 0; i < files.length; i++) {
        processFile(files[i]);
      }
    }
  };

  return (
    <div style={{ position: 'relative', width: '100%', minHeight: '400px', flex: 1 }}>
      {/* Syntax-highlighted overlay (visual only, no interaction) */}
      <div
        ref={overlayRef}
        aria-hidden="true"
        style={{
          ...sharedStyle,
          position: 'absolute',
          top: 0,
          left: 0,
          color: '#FFFFFF',
          pointerEvents: 'none',
          overflow: 'hidden',
          background: 'transparent',
        }}
      />
      {/* Transparent textarea: source of truth for all text input */}
      <textarea
        ref={textareaRef}
        className="visual-editor-ta"
        value={content}
        onChange={handleInput}
        onScroll={syncScroll}
        onKeyDown={(e) => { if (onKeyDown) onKeyDown(e); }}
        onPaste={handlePaste}
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
        spellCheck={false}
        autoCorrect="off"
        autoCapitalize="sentences"
        style={{
          ...sharedStyle,
          position: 'relative',
          zIndex: 1,
          resize: 'none',
          background: 'transparent',
          color: 'transparent',
          caretColor: 'var(--accent)',
          overflow: 'hidden',
        }}
      />
    </div>
  );
});

VisualEditor.displayName = 'VisualEditor';

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function formatInlineSpans(text: string, depth = 0): string {
  if (!text) return '';
  if (depth > 4) return escapeHtml(text);

  // Exact parity with Android AppleNotesVisualTransformation:
  // Requires at least one character inside formatting delimiters. Never matches empty '**' or '****'.
  const REGEX = /(!\[[^\]\n]*\]\([^)\n]+\)|(?<!\!)\[\[([^\]|\n]+)(?:\|([^\]\n]+))?\]\]|(?<!\!)\[([^\]\n]+)\]\(([^)\n]+)\)|\*\*(?!\*)([^\n]+?)(?<!\*)\*\*|__(?!_)([^\n]+?)(?<!_)__|~~(?!~)([^\n]+?)(?<!~)~~|`([^`\n]+)`|(?<!\*)\*(?!\*)([^*\n]+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)([^_\n]+?)(?<!_)_|#[a-zA-Z0-9_\-/]+|https?:\/\/[a-zA-Z0-9_\-.~:/?#[\]@!$&'()*+,;=%]+)/g;
  let result = '';
  let cursor = 0;
  let match: RegExpExecArray | null;

  // Ghost color for syntax tokens — same font-size/metrics as body text for pixel-perfect cursor alignment
  const G = 'color:#545458';
  const BLUE = 'color:var(--blue, #0A84FF)';

  while ((match = REGEX.exec(text)) !== null) {
    const start = match.index;
    const end = start + match[0].length;
    if (start > cursor) result += escapeHtml(text.substring(cursor, start));

    const val = match[0];

    // Image: ![alt](url)
    if (val.startsWith('![')) {
      const altEnd = val.indexOf('](');
      const alt = val.substring(2, altEnd);
      const url = val.substring(altEnd + 2, val.length - 1);
      result += `<span style="${G}">![</span><span style="${BLUE};font-weight:600">${escapeHtml(alt)}</span><span style="${G}">](${escapeHtml(url)})</span>`;
    }
    // WikiLink: [[target]] or [[target|alias]]
    else if (val.startsWith('[[')) {
      const inner = val.substring(2, val.length - 2);
      const pipeIdx = inner.indexOf('|');
      if (pipeIdx !== -1) {
        const target = inner.substring(0, pipeIdx);
        const alias = inner.substring(pipeIdx + 1);
        result += `<span style="${G}">[[</span><span style="${G};opacity:0.6">${escapeHtml(target)}|</span><span style="color:var(--accent);font-weight:600;text-decoration:underline">${escapeHtml(alias)}</span><span style="${G}">]]</span>`;
      } else {
        result += `<span style="${G}">[[</span><span style="color:var(--accent);font-weight:600;text-decoration:underline">${escapeHtml(inner)}</span><span style="${G}">]]</span>`;
      }
    }
    // Markdown Link: [text](url)
    else if (val.startsWith('[')) {
      const labelEnd = val.indexOf('](');
      const label = val.substring(1, labelEnd);
      const url = val.substring(labelEnd + 2, val.length - 1);
      result += `<span style="${G}">[</span><span style="${BLUE};text-decoration:underline">${formatInlineSpans(label, depth + 1)}</span><span style="${G}">](${escapeHtml(url)})</span>`;
    }
    // Raw URL: https://... or http://...
    else if (val.startsWith('http://') || val.startsWith('https://')) {
      result += `<span style="${BLUE};text-decoration:underline">${escapeHtml(val)}</span>`;
    }
    // Bold: **text** or __text__ (strictly >= 5 chars so never matches empty tokens)
    else if ((val.startsWith('**') && val.endsWith('**') && val.length >= 5) || (val.startsWith('__') && val.endsWith('__') && val.length >= 5)) {
      const inner = val.substring(2, val.length - 2);
      const token = val.substring(0, 2);
      result += `<span style="${G}">${token}</span><strong style="color:inherit;-webkit-text-stroke:0.45px currentColor;text-shadow:0 0 0.5px currentColor;font-weight:inherit">${formatInlineSpans(inner, depth + 1)}</strong><span style="${G}">${token}</span>`;
    }
    // Italic: *text* or _text_ (strictly >= 3 chars so never matches empty tokens)
    else if ((val.startsWith('*') && val.endsWith('*') && val.length >= 3) || (val.startsWith('_') && val.endsWith('_') && val.length >= 3)) {
      const inner = val.substring(1, val.length - 1);
      const token = val[0];
      result += `<span style="${G}">${token}</span><em style="font-style:italic;font-synthesis:style;color:inherit">${formatInlineSpans(inner, depth + 1)}</em><span style="${G}">${token}</span>`;
    }
    // Inline code: `code`
    else if (val.startsWith('`') && val.endsWith('`') && val.length >= 3) {
      const inner = val.substring(1, val.length - 1);
      result += `<span style="${G}">\`</span><code style="color:var(--accent);background:#2C2C2E;border-radius:2px;font-family:inherit;font-size:inherit;font-weight:inherit;letter-spacing:inherit;padding:0;margin:0">${escapeHtml(inner)}</code><span style="${G}">\`</span>`;
    }
    // Strikethrough: ~~text~~
    else if (val.startsWith('~~') && val.endsWith('~~') && val.length >= 5) {
      const inner = val.substring(2, val.length - 2);
      result += `<span style="${G}">~~</span><span style="text-decoration:line-through;color:inherit">${formatInlineSpans(inner, depth + 1)}</span><span style="${G}">~~</span>`;
    }
    // Hashtags
    else if (val.startsWith('#')) {
      result += `<span style="color:var(--accent);-webkit-text-stroke:0.3px var(--accent)">${escapeHtml(val)}</span>`;
    } else {
      result += escapeHtml(val);
    }
    cursor = end;
  }

  if (cursor < text.length) result += escapeHtml(text.substring(cursor));
  return result;
}
