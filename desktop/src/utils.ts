import type { Note } from './types';

export function getDisplayTitle(note: Note): string {
  if (note.title && note.title.trim().length > 0) {
    return note.title.trim();
  }
  if (note.content && note.content.trim().length > 0) {
    const lines = note.content.split('\n');
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.length > 0) {
        return trimmed.replace(/^#+\s*/, '').replace(/^[○✓•\-\*]\s*/, '').replace(/^\[[ xX]\]\s*/, '').trim() || 'New Note';
      }
    }
  }
  return 'New Note';
}

export function getPreviewSnippet(content: string): string {
  if (!content) return '';
  const lines = content.split('\n');
  const snippets: string[] = [];

  for (const line of lines) {
    let clean = line.trim();
    if (!clean || clean.startsWith('#')) continue;
    clean = clean
      .replace(/^([○✓•\-\*]|\d+\.)\s*/, '')
      .replace(/^\[[ xX]\]\s*/, '')
      .replace(/[*_`~#]/g, '')
      .trim();
    if (clean) {
      snippets.push(clean);
      if (snippets.length >= 2) break;
    }
  }

  return snippets.join(' ');
}

export function extractTags(content: string): string[] {
  const tagRegex = /(?:^|\s)#([a-zA-Z0-9_\-]+)/g;
  const tags = new Set<string>();
  let match;
  while ((match = tagRegex.exec(content)) !== null) {
    tags.add(match[1]);
  }
  return Array.from(tags).sort();
}

export function getTaskStats(content: string): { completed: number; total: number } | null {
  const checkboxRegex = /^\s*(?:[-*]\s*\[([ xX])\]|[○✓])\s*(.*)$/gm;
  let total = 0;
  let completed = 0;
  let match;

  while ((match = checkboxRegex.exec(content)) !== null) {
    total++;
    if (match[1] === 'x' || match[1] === 'X' || match[0].trim().startsWith('✓')) {
      completed++;
    }
  }

  if (total === 0) return null;
  return { completed, total };
}

export function formatDate(timestamp: number): string {
  const date = new Date(timestamp);
  const now = new Date();
  
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return 'Yesterday';
  }

  return date.toLocaleDateString([], { day: '2-digit', month: '2-digit', year: '2-digit' });
}
