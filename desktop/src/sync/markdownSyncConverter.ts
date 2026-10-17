/**
 * Bi-directional converter between Monolith Desktop Note state and Obsidian-compatible
 * standard Markdown (.md) storage format, matching Android's MarkdownConverter.kt.
 */
import type { Note } from '../types';
import { extractTags } from '../utils';

export function sanitizeFileName(name: string): string {
  const clean = name.trim().replace(/[\\/:*?"<>|]/g, '_').replace(/\s+/g, ' ');
  return clean || 'Untitled';
}

export function noteToFileName(note: Note): string {
  if (note.filePath && note.filePath.endsWith('.md')) {
    return note.filePath;
  }
  const baseName = sanitizeFileName(note.title || 'Untitled');
  if (note.folder && note.folder !== 'Notes') {
    return `${sanitizeFileName(note.folder)}/${baseName}.md`;
  }
  return `${baseName}.md`;
}

/**
 * Converts a Note to standard Obsidian Markdown string.
 */
export function noteToMarkdown(note: Note): string {
  const parts: string[] = [];

  // Frontmatter
  if (note.isPinned) {
    parts.push('---\npinned: true\n---');
  }

  // Heading 1 Title
  const cleanTitle = (note.title || '').trim();
  if (cleanTitle) {
    parts.push(`# ${cleanTitle}`);
  }

  // Body content: convert Apple Notes UI markers back to standard Markdown
  if (note.content) {
    const lines = note.content.split('\n');
    const convertedLines = lines.map(line => {
      const trimmed = line.trimStart();
      const indent = line.substring(0, line.length - trimmed.length);

      if (trimmed.startsWith('○ ')) {
        return `${indent}- [ ] ${trimmed.substring(2)}`;
      }
      if (trimmed.startsWith('✓ ')) {
        return `${indent}- [x] ${trimmed.substring(2)}`;
      }
      if (trimmed.startsWith('• ')) {
        return `${indent}- ${trimmed.substring(2)}`;
      }
      return line;
    });

    parts.push(convertedLines.join('\n'));
  }

  return parts.join('\n\n').trim() + '\n';
}

/**
 * Converts standard Markdown string to Note representation.
 */
export function markdownToNote(fileName: string, rawContent: string, lastModified: number): Note {
  let content = rawContent.replace(/\r\n/g, '\n');
  let isPinned = false;

  // 1. Parse YAML Frontmatter
  if (content.startsWith('---')) {
    const secondSeparator = content.indexOf('\n---', 3);
    if (secondSeparator !== -1) {
      const frontmatter = content.substring(3, secondSeparator);
      if (/pinned:\s*true/i.test(frontmatter)) {
        isPinned = true;
      }
      const postFrontmatter = content.indexOf('\n', secondSeparator + 4);
      content = postFrontmatter !== -1 ? content.substring(postFrontmatter + 1) : '';
    }
  }

  // 2. Parse Folder & Title from File Path / Content
  let folder = 'Notes';
  let fallbackTitle = 'Untitled';
  const cleanPath = fileName.replace(/\\/g, '/');
  if (cleanPath.includes('/')) {
    const segments = cleanPath.split('/');
    if (segments.length > 1 && segments[0] !== '.') {
      folder = segments[0];
      fallbackTitle = segments[segments.length - 1].replace(/\.md$/i, '');
    }
  } else {
    fallbackTitle = cleanPath.replace(/\.md$/i, '');
  }

  let titleFound = false;
  let cleanTitle = fallbackTitle;
  const bodyLines: string[] = [];
  const lines = content.split('\n');
  let inCodeBlock = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trimStart();
    const indent = line.substring(0, line.length - trimmed.length);

    if (trimmed.startsWith('```') || trimmed.startsWith('~~~')) {
      inCodeBlock = !inCodeBlock;
    }

    if (!titleFound && !inCodeBlock && trimmed.length > 0) {
      if (trimmed.startsWith('# ') && !trimmed.startsWith('##')) {
        cleanTitle = trimmed.substring(2).trim() || fallbackTitle;
        titleFound = true;
        continue;
      } else if (!trimmed.startsWith('#') && !trimmed.startsWith('-') && !trimmed.startsWith('*') && !trimmed.startsWith('`')) {
        cleanTitle = trimmed.trim() || fallbackTitle;
        titleFound = true;
        continue;
      } else {
        cleanTitle = fallbackTitle;
        titleFound = true;
      }
    }

    // Convert standard Markdown lists to Apple Notes tactile symbols in body
    if (inCodeBlock) {
      bodyLines.push(line);
    } else if (trimmed.startsWith('- [ ] ') || trimmed.startsWith('* [ ] ')) {
      bodyLines.push(`${indent}○ ${trimmed.substring(6)}`);
    } else if (trimmed.startsWith('- [x] ') || trimmed.startsWith('- [X] ') || trimmed.startsWith('* [x] ') || trimmed.startsWith('* [X] ')) {
      bodyLines.push(`${indent}✓ ${trimmed.substring(6)}`);
    } else if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      bodyLines.push(`${indent}• ${trimmed.substring(2)}`);
    } else {
      bodyLines.push(line);
    }
  }

  // Remove leading blank lines
  while (bodyLines.length > 0 && bodyLines[0].trim().length === 0) {
    bodyLines.shift();
  }

  const finalBody = bodyLines.join('\n');
  const tags = extractTags(finalBody);

  return {
    id: `note-${cleanPath.replace(/[^a-zA-Z0-9_\-]/g, '_')}`,
    filePath: cleanPath,
    title: cleanTitle,
    content: finalBody,
    tags,
    lastModified: lastModified || Date.now(),
    isPinned,
    folder,
    isTrash: false
  };
}
