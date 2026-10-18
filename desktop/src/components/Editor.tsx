import React, { useState, useEffect, useRef } from 'react';
import { 
  Edit3,
  List,
  Undo2,
  Redo2,
  Bold,
  Italic,
  Strikethrough,
  Heading,
  ChevronDown,
  ListOrdered,
  CheckSquare,
  Code,
  Minus,
  Quote,
  Copy,
  Paperclip,
  PenLine,
  Trash2,
  RotateCcw
} from 'lucide-react';
import type { Note, AppSettings, EditorFontSize } from '../types';
import { extractTags } from '../utils';
import { MarkdownRenderView } from './MarkdownVisuals';
import { 
  VisualEditor, 
  type VisualEditorRef,
  HEADING_PREFIX_REGEX 
} from './VisualEditor';

interface EditorProps {
  note: Note | null;
  settings: AppSettings;
  isTrash?: boolean;
  hasNotes?: boolean;
  onNewNote?: () => void;
  isSidebarOpen?: boolean;
  isNoteListOpen?: boolean;
  onToggleSidebar?: () => void;
  onToggleNoteList?: () => void;
  onBackToList?: () => void;
  onUpdateNote: (updated: Note) => void;
  onDeleteNote?: (note: Note) => void;
  onRestoreNote?: (note: Note) => void;
}

export const Editor: React.FC<EditorProps> = ({
  note,
  settings,
  isTrash = false,
  hasNotes = true,
  onNewNote,
  isSidebarOpen: _isSidebarOpen,
  isNoteListOpen: _isNoteListOpen,
  onToggleSidebar: _onToggleSidebar,
  onToggleNoteList: _onToggleNoteList,
  onBackToList: _onBackToList,
  onUpdateNote,
  onDeleteNote,
  onRestoreNote
}) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isEditing, setIsEditing] = useState(false);

  const handleDone = () => {
    if (!title.trim() && !content.trim()) {
      if (note) {
        onDeleteNote?.(note);
      }
      return;
    }
    setIsEditing(false);
  };
  const [showInfoSheet, setShowInfoSheet] = useState(false);
  const [showHeadingDropdown, setShowHeadingDropdown] = useState(false);
  const [copied, setCopied] = useState(false);

  // Undo / Redo stacks
  const [undoStack, setUndoStack] = useState<string[]>([]);
  const [redoStack, setRedoStack] = useState<string[]>([]);

  const visualEditorRef = useRef<VisualEditorRef>(null);
  const titleInputRef = useRef<HTMLInputElement>(null);
  const headingMenuRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const editorContainerRef = useRef<HTMLDivElement>(null);
  const newNoteStartStyleRef = useRef(settings.newNoteStartStyle);
  newNoteStartStyleRef.current = settings.newNoteStartStyle;

  // Floating Edit Button Draggable State
  const [fabPos, setFabPos] = useState<{ right: number; bottom: number }>(() => {
    try {
      const saved = localStorage.getItem('monolith_fab_pos');
      if (saved) {
        const parsed = JSON.parse(saved);
        if (typeof parsed.right === 'number' && typeof parsed.bottom === 'number') {
          return parsed;
        }
      }
    } catch {}
    return { right: 24, bottom: 24 };
  });
  const fabPosRef = useRef(fabPos);
  fabPosRef.current = fabPos;
  const [isDraggingFab, setIsDraggingFab] = useState(false);
  const fabDragRef = useRef<{
    startX: number;
    startY: number;
    startRight: number;
    startBottom: number;
    hasDragged: boolean;
  } | null>(null);
  const lastFabDraggedAtRef = useRef<number>(0);

  const handleFabPointerDown = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (e.button !== 0) return;
    (e.currentTarget as HTMLButtonElement).setPointerCapture(e.pointerId);
    fabDragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startRight: fabPos.right,
      startBottom: fabPos.bottom,
      hasDragged: false
    };
    setIsDraggingFab(true);
  };

  const handleFabPointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (!fabDragRef.current) return;
    const { startX, startY, startRight, startBottom } = fabDragRef.current;
    const dx = e.clientX - startX;
    const dy = e.clientY - startY;

    if (!fabDragRef.current.hasDragged && Math.hypot(dx, dy) > 4) {
      fabDragRef.current.hasDragged = true;
    }

    if (fabDragRef.current.hasDragged) {
      const containerRect = editorContainerRef.current?.getBoundingClientRect();
      const containerWidth = containerRect?.width || window.innerWidth;
      const containerHeight = containerRect?.height || window.innerHeight;

      const rawRight = startRight - dx;
      const rawBottom = startBottom - dy;

      const buttonSize = 38;
      const margin = 12;
      const clampedRight = Math.max(margin, Math.min(containerWidth - buttonSize - margin, rawRight));
      const clampedBottom = Math.max(margin, Math.min(containerHeight - buttonSize - margin, rawBottom));

      setFabPos({ right: clampedRight, bottom: clampedBottom });
    }
  };

  const handleFabPointerUp = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (!fabDragRef.current) return;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}

    const wasDragged = fabDragRef.current.hasDragged;
    fabDragRef.current = null;
    setIsDraggingFab(false);

    if (wasDragged) {
      lastFabDraggedAtRef.current = Date.now();
      try {
        localStorage.setItem('monolith_fab_pos', JSON.stringify(fabPosRef.current));
      } catch {}
    }
  };

  const handleFabClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (Date.now() - lastFabDraggedAtRef.current < 250) {
      return;
    }
    setIsEditing(true);
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      if (!isEditing) setIsEditing(true);
      for (let i = 0; i < files.length; i++) {
        visualEditorRef.current?.insertFile(files[i]);
      }
      e.target.value = '';
    }
  };

  // Close heading dropdown on outside click
  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      if (headingMenuRef.current && !headingMenuRef.current.contains(e.target as Node)) {
        setShowHeadingDropdown(false);
      }
    };
    if (showHeadingDropdown) {
      document.addEventListener('mousedown', handleOutsideClick);
    }
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [showHeadingDropdown]);

  useEffect(() => {
    if (note) {
      let initialTitle = note.title;
      let initialContent = note.content;

      // Ensure title is extracted and not duplicated as leading '# Title' in body
      const lines = initialContent.split('\n');
      if (lines.length > 0 && lines[0].trim().startsWith('# ') && !lines[0].trim().startsWith('## ')) {
        const leadingHeading = lines[0].trim().substring(2).trim();
        if (!initialTitle || initialTitle.trim() === '' || initialTitle.trim() === leadingHeading) {
          initialTitle = leadingHeading;
          // Strip the duplicate heading line and any immediately following blank line
          let restLines = lines.slice(1);
          if (restLines.length > 0 && restLines[0].trim().length === 0) {
            restLines = restLines.slice(1);
          }
          initialContent = restLines.join('\n');
        }
      }

      setTitle(initialTitle);
      setContent(initialContent);
      const isNew = !note.title && !note.content;
      setIsEditing(isNew);
      setUndoStack([]);
      setRedoStack([]);
      if (isNew) {
        setTimeout(() => {
          if (newNoteStartStyleRef.current === 'BODY') {
            visualEditorRef.current?.focus();
          } else {
            titleInputRef.current?.focus();
          }
        }, 100);
      }
    } else {
      setTitle('');
      setContent('');
      setIsEditing(false);
    }
  }, [note?.id]);

  if (!note) {
    const isCreateAction = !isTrash && !hasNotes;

    return (
      <div
        onClick={() => {
          if (isCreateAction && onNewNote) {
            onNewNote();
          }
        }}
        style={{
          flex: 1,
          height: '100%',
          backgroundColor: 'var(--bg-primary)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--text-tertiary)',
          gap: '12px',
          cursor: isCreateAction ? 'pointer' : 'default',
          userSelect: 'none'
        }}
      >
        <div style={{
          width: '64px',
          height: '64px',
          borderRadius: '16px',
          backgroundColor: 'var(--bg-card)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: '0.5px solid var(--divider)'
        }}>
          {isTrash ? (
            <Trash2 size={28} color="var(--text-tertiary)" style={{ opacity: 0.6 }} />
          ) : (
            <Edit3 size={28} color="var(--accent)" />
          )}
        </div>
        <span style={{
          fontSize: '15px',
          fontWeight: 500,
          color: isCreateAction ? 'var(--text-primary)' : 'var(--text-tertiary)'
        }}>
          {isTrash
            ? 'No deleted notes'
            : (!hasNotes ? 'Create a note' : 'Select a note')}
        </span>
      </div>
    );
  }

  const pushUndo = (prevContent: string) => {
    setUndoStack(prev => [...prev.slice(-30), prevContent]);
    setRedoStack([]);
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    setUndoStack(prevStack => prevStack.slice(0, -1));
    setRedoStack(prevStack => [...prevStack, content]);
    setContent(prev);
    saveChanges(title, prev);
  };

  const handleRedo = () => {
    if (redoStack.length === 0) return;
    const next = redoStack[redoStack.length - 1];
    setRedoStack(prevStack => prevStack.slice(0, -1));
    setUndoStack(prevStack => [...prevStack, content]);
    setContent(next);
    saveChanges(title, next);
  };

  const handleTitleChange = (newTitle: string) => {
    setTitle(newTitle);
    saveChanges(newTitle, content);
  };

  const handleContentChange = (newContent: string) => {
    pushUndo(content);
    setContent(newContent);
    saveChanges(title, newContent);
  };

  const saveChanges = (newTitle: string, newContent: string) => {
    const updatedTags = extractTags(newContent);
    onUpdateNote({
      ...note,
      title: newTitle,
      content: newContent,
      tags: updatedTags,
      lastModified: Date.now()
    });
  };

  // Toggle Line Prefix: Checklist (○ ), Bullets (• ), Headings (## )
  const toggleLinePrefix = (targetPrefix: string, alternatePrefix?: string) => {
    pushUndo(content);
    if (visualEditorRef.current) {
      visualEditorRef.current.toggleLinePrefix(targetPrefix, alternatePrefix);
    }
  };

  // Wrap Selection (Bold, Italic, Strikethrough, Code)
  const wrapSelection = (token: string) => {
    pushUndo(content);
    if (visualEditorRef.current) {
      visualEditorRef.current.wrapSelection(token);
    }
  };

  // Insert Separator (─── or ---)
  const handleInsertDivider = () => {
    pushUndo(content);
    if (visualEditorRef.current) {
      visualEditorRef.current.insertDivider();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter') {
      const selRange = visualEditorRef.current?.getSelectionRange();
      const cursor = selRange ? selRange.start : content.length;
      const currentLine = content.substring(0, cursor).split('\n').pop() || '';

      // If pressing enter on a Heading (e.g. #, ##, ###)
      // When at the end or inside a heading, pressing enter should break to a clean normal line (no markdown heading hashes)
      if (HEADING_PREFIX_REGEX.test(currentLine.trimStart())) {
        e.preventDefault();
        // Use insertText through VisualEditor so DOM update + cursor placement are atomic
        insertRawText('\n');
        return;
      }

      // Smart auto-continue checklists/bullets
      if (settings.autoContinueChecklists) {
        if (currentLine.startsWith('○ ') || currentLine.startsWith('✓ ')) {
          e.preventDefault();
          if (currentLine === '○ ' || currentLine === '✓ ') {
            // Remove prefix on empty enter
            const lineStart = cursor - currentLine.length;
            const newContent = content.substring(0, lineStart) + content.substring(cursor);
            setContent(newContent);
            saveChanges(title, newContent);
          } else {
            insertRawText('\n○ ');
          }
        } else if (currentLine.startsWith('- [ ] ') || currentLine.startsWith('- [x] ')) {
          e.preventDefault();
          if (currentLine === '- [ ] ' || currentLine === '- [x] ') {
            const lineStart = cursor - currentLine.length;
            const newContent = content.substring(0, lineStart) + content.substring(cursor);
            setContent(newContent);
            saveChanges(title, newContent);
          } else {
            insertRawText('\n- [ ] ');
          }
        } else if (currentLine.startsWith('• ') || currentLine.startsWith('- ')) {
          e.preventDefault();
          if (currentLine === '• ' || currentLine === '- ') {
            const lineStart = cursor - currentLine.length;
            const newContent = content.substring(0, lineStart) + content.substring(cursor);
            setContent(newContent);
            saveChanges(title, newContent);
          } else {
            insertRawText('\n• ');
          }
        } else {
          const trimmedCurrent = currentLine.trimStart();
          const indent = currentLine.substring(0, currentLine.length - trimmedCurrent.length);
          const numMatch = /^(\d+)\.\s*(.*)/.exec(trimmedCurrent);
          if (numMatch) {
            e.preventDefault();
            const currentNum = parseInt(numMatch[1], 10);
            const body = numMatch[2];
            if (!body.trim()) {
              // Empty numbered item: remove prefix and return to regular paragraph
              const lineStart = cursor - currentLine.length;
              const newContent = content.substring(0, lineStart) + content.substring(cursor);
              setContent(newContent);
              saveChanges(title, newContent);
            } else {
              // Auto-increment: e.g. "1. " -> "2. ", preserving any leading indentation
              const nextPrefix = `\n${indent}${currentNum + 1}. `;
              insertRawText(nextPrefix);
            }
          }
        }
      }
    }
  };

  const insertRawText = (text: string) => {
    pushUndo(content);
    if (visualEditorRef.current) {
      visualEditorRef.current.insertText(text);
    }
  };

  const getFontSizePx = (size: EditorFontSize) => {
    switch (size) {
      case 'COMPACT': return '15px';
      case 'LARGE': return '19px';
      case 'EXTRA_LARGE': return '21px';
      default: return '17px';
    }
  };

  const fontSize = getFontSizePx(settings.editorFontSize);
  const words = content.trim().split(/\s+/).filter(Boolean).length;
  const chars = content.length;

  return (
    <div
      ref={editorContainerRef}
      style={{
        flex: 1,
        height: '100%',
        backgroundColor: 'transparent',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        position: 'relative'
      }}
    >
      {/* 1. Desktop Obsidian-style Top Header (Slides down in Edit mode without shifting content) */}
      <div style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        height: '46px',
        padding: '0 16px',
        display: 'grid',
        gridTemplateColumns: 'minmax(120px, 1fr) auto minmax(120px, 1fr)',
        alignItems: 'center',
        backgroundColor: 'var(--bg-primary)',
        userSelect: 'none',
        gap: '12px',
        zIndex: 40,
        transform: isEditing ? 'translateY(0)' : 'translateY(-100%)',
        opacity: isEditing ? 1 : 0,
        pointerEvents: isEditing ? 'auto' : 'none',
        transition: 'transform 0.22s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.15s ease'
      }}>
        {/* Left Section: Word & Char Count (Badge with background) */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifySelf: 'start',
          minWidth: 0
        }}>
          {settings.showWordCount && (
            <div style={{
              display: 'inline-flex',
              alignItems: 'center',
              fontSize: '11.5px',
              fontFamily: 'var(--font-mono)',
              color: 'var(--text-secondary)',
              padding: '3px 8px',
              borderRadius: '6px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              whiteSpace: 'nowrap',
              letterSpacing: '-0.1px'
            }}>
              {words} {words === 1 ? 'word' : 'words'} • {chars} {chars === 1 ? 'char' : 'chars'}
            </div>
          )}
        </div>

        {/* Center Section: Centered Island Toolbar (Edit mode) or Reading Stats (View mode) */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          justifySelf: 'center'
        }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '2px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              borderRadius: '8px',
              padding: '2px 5px',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.25)',
              overflow: 'visible'
            }}>
            {/* Consolidated Headings Dropdown (Notion-style) */}
            <div ref={headingMenuRef} style={{ position: 'relative' }}>
              <button
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => setShowHeadingDropdown(!showHeadingDropdown)}
                title="Headings (H1, H2, H3)"
                style={{
                  ...topToolbarBtnStyle,
                  gap: '2px',
                  padding: '5px 6px',
                  backgroundColor: showHeadingDropdown ? 'var(--bg-elevated)' : 'transparent'
                }}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                onMouseLeave={(e) => {
                  if (!showHeadingDropdown) e.currentTarget.style.backgroundColor = 'transparent';
                }}
              >
                <Heading size={16} color="var(--text-secondary)" />
                <ChevronDown size={12} color="var(--text-secondary)" style={{ transform: showHeadingDropdown ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s ease' }} />
              </button>

              {/* Notion-style Visual Headings Menu */}
              {showHeadingDropdown && (
                <div style={{
                  position: 'absolute',
                  top: '100%',
                  left: 0,
                  marginTop: '4px',
                  backgroundColor: 'var(--bg-card)',
                  border: '1px solid var(--divider)',
                  borderRadius: '10px',
                  boxShadow: '0 12px 28px rgba(0,0,0,0.65)',
                  padding: '6px',
                  zIndex: 200,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '2px',
                  minWidth: '190px'
                }}>
                  {/* Heading 1 */}
                  <button
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => {
                      toggleLinePrefix('# ');
                      setShowHeadingDropdown(false);
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '8px 12px',
                      background: 'none',
                      border: 'none',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      textAlign: 'left',
                      color: 'var(--text-primary)',
                      transition: 'background 0.1s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                  >
                    <span style={{ fontSize: '18px', fontWeight: 700, fontFamily: 'var(--font-display)', lineHeight: '22px' }}>
                      Heading 1
                    </span>
                  </button>

                  {/* Heading 2 */}
                  <button
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => {
                      toggleLinePrefix('## ');
                      setShowHeadingDropdown(false);
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '8px 12px',
                      background: 'none',
                      border: 'none',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      textAlign: 'left',
                      color: 'var(--text-primary)',
                      transition: 'background 0.1s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                  >
                    <span style={{ fontSize: '15px', fontWeight: 600, fontFamily: 'var(--font-display)', lineHeight: '19px' }}>
                      Heading 2
                    </span>
                  </button>

                  {/* Heading 3 */}
                  <button
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => {
                      toggleLinePrefix('### ');
                      setShowHeadingDropdown(false);
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '8px 12px',
                      background: 'none',
                      border: 'none',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      textAlign: 'left',
                      color: 'var(--text-primary)',
                      transition: 'background 0.1s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                  >
                    <span style={{ fontSize: '13px', fontWeight: 600, fontFamily: 'var(--font-display)', lineHeight: '17px' }}>
                      Heading 3
                    </span>
                  </button>
                </div>
              )}
            </div>

            <div style={{ width: '1px', height: '16px', backgroundColor: 'var(--divider)', margin: '0 4px' }} />

            {/* Bold */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => wrapSelection('**')}
              title="Bold (Ctrl+B)"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Bold size={16} color="var(--text-secondary)" />
            </button>

            {/* Italic */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => wrapSelection('*')}
              title="Italic (Ctrl+I)"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Italic size={16} color="var(--text-secondary)" />
            </button>

            {/* Strikethrough */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => wrapSelection('~~')}
              title="Strikethrough"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Strikethrough size={16} color="var(--text-secondary)" />
            </button>

            {/* Inline Code */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => wrapSelection('`')}
              title="Code"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Code size={16} color="var(--text-secondary)" />
            </button>

            <div style={{ width: '1px', height: '16px', backgroundColor: 'var(--divider)', margin: '0 4px' }} />

            {/* Checklist */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => toggleLinePrefix('- [ ] ', '- [x] ')}
              title="Checklist"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <CheckSquare size={16} color="var(--text-secondary)" />
            </button>

            {/* Bullet List */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => toggleLinePrefix('• ')}
              title="Bullet List"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <List size={16} color="var(--text-secondary)" />
            </button>

            {/* Numbered List */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => toggleLinePrefix('1. ')}
              title="Numbered List"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <ListOrdered size={16} color="var(--text-secondary)" />
            </button>

            {/* Blockquote */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => toggleLinePrefix('> ')}
              title="Quote"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Quote size={15} color="var(--text-secondary)" />
            </button>

            {/* Horizontal Divider */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={handleInsertDivider}
              title="Horizontal Divider"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Minus size={16} color="var(--text-secondary)" />
            </button>

            {/* Attach File or Image */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => fileInputRef.current?.click()}
              title="Attach File or Image"
              style={topToolbarBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Paperclip size={15} color="var(--text-secondary)" />
            </button>
            <input
              type="file"
              ref={fileInputRef}
              multiple
              onChange={handleFileInputChange}
              style={{ display: 'none' }}
            />

            <div style={{ width: '1px', height: '16px', backgroundColor: 'var(--divider)', margin: '0 4px' }} />

            {/* Undo */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={handleUndo}
              disabled={undoStack.length === 0}
              title="Undo (Ctrl+Z)"
              style={{
                ...topToolbarBtnStyle,
                opacity: undoStack.length > 0 ? 1 : 0.35,
                cursor: undoStack.length > 0 ? 'pointer' : 'default'
              }}
              onMouseEnter={(e) => { if (undoStack.length > 0) e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'; }}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Undo2 size={16} color="var(--text-secondary)" />
            </button>

            {/* Redo */}
            <button
              onMouseDown={(e) => e.preventDefault()}
              onClick={handleRedo}
              disabled={redoStack.length === 0}
              title="Redo (Ctrl+Y)"
              style={{
                ...topToolbarBtnStyle,
                opacity: redoStack.length > 0 ? 1 : 0.35,
                cursor: redoStack.length > 0 ? 'pointer' : 'default'
              }}
              onMouseEnter={(e) => { if (redoStack.length > 0) e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'; }}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Redo2 size={16} color="var(--text-secondary)" />
            </button>
          </div>
        </div>

        {/* Right Actions: Done */}
        <div style={{ display: 'flex', alignItems: 'center', justifySelf: 'end' }}>
          <button
            onClick={handleDone}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--accent)',
              fontFamily: 'var(--font-text)',
              fontSize: '15px',
              fontWeight: 600,
              cursor: 'pointer',
              padding: '4px 8px',
              borderRadius: '6px',
              transition: 'background 0.15s ease'
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            Done
          </button>
        </div>
      </div>

      {/* 2. Scrollable Canvas Area (scrollbar on rightmost edge) */}
      <div 
        style={{
          flex: 1,
          overflowY: 'auto',
          width: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          cursor: isEditing ? 'default' : 'text',
          userSelect: 'text'
        }}
        onDoubleClick={(e) => {
          if (!isEditing) {
            const target = e.target as HTMLElement;
            if (target.closest('[data-no-double-click]') || target.tagName === 'BUTTON' || target.closest('button')) {
              return;
            }
            setIsEditing(true);
            setTimeout(() => visualEditorRef.current?.focus(), 50);
          }
        }}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          const files = e.dataTransfer?.files;
          if (files && files.length > 0) {
            e.preventDefault();
            if (!isEditing) setIsEditing(true);
            for (let i = 0; i < files.length; i++) {
              visualEditorRef.current?.insertFile(files[i]);
            }
          }
        }}
      >
        <div
          style={{
            maxWidth: '900px',
            width: '100%',
            padding: '60px 44px 60px 44px',
            boxSizing: 'border-box',
            display: 'flex',
            flexDirection: 'column',
            flex: 1
          }}
        >
        {/* Trashed Note Banner */}
        {note?.isTrash && (
          <div style={{
            margin: '0 0 20px 0',
            padding: '10px 14px',
            backgroundColor: 'rgba(255, 69, 58, 0.08)',
            border: '0.5px solid rgba(255, 69, 58, 0.25)',
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-secondary)', fontSize: '13.5px' }}>
              <Trash2 size={16} color="var(--danger)" />
              <span>This note is in <strong>Deleted</strong>.</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <button
                onClick={() => onRestoreNote?.(note)}
                style={{
                  padding: '5px 12px',
                  borderRadius: '6px',
                  border: '0.5px solid var(--divider)',
                  backgroundColor: 'var(--bg-card)',
                  color: 'var(--accent)',
                  fontWeight: 600,
                  fontSize: '12.5px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px'
                }}
              >
                <RotateCcw size={13} />
                <span>Put Back</span>
              </button>
              <button
                onClick={() => onDeleteNote?.(note)}
                style={{
                  padding: '5px 12px',
                  borderRadius: '6px',
                  border: 'none',
                  backgroundColor: 'var(--danger)',
                  color: '#FFF',
                  fontWeight: 600,
                  fontSize: '12.5px',
                  cursor: 'pointer'
                }}
              >
                Delete Permanently
              </button>
            </div>
          </div>
        )}

        {/* Title Input / Display */}
        {isEditing ? (
          <input
            ref={titleInputRef}
            type="text"
            value={title}
            onChange={(e) => handleTitleChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                visualEditorRef.current?.focus();
              }
            }}
            placeholder="Title"
            spellCheck={false}
            autoCorrect="off"
            style={{
              width: '100%',
              background: 'none',
              border: 'none',
              outline: 'none',
              fontFamily: 'var(--font-display)',
              fontWeight: 700,
              fontSize: '26px',
              lineHeight: '32px',
              color: '#FFFFFF',
              marginBottom: '16px'
            }}
          />
        ) : (
          title && (
            <h1
              onDoubleClick={(e) => {
                if (note?.isTrash) return;
                e.stopPropagation();
                setIsEditing(true);
                setTimeout(() => titleInputRef.current?.focus(), 50);
              }}
              style={{
                fontFamily: 'var(--font-display)',
                fontWeight: 700,
                fontSize: '26px',
                lineHeight: '32px',
                color: '#FFFFFF',
                margin: '0 0 16px 0',
                cursor: 'text',
                userSelect: 'text'
              }}
            >
              {title}
            </h1>
          )
        )}

        {/* Content Canvas */}
        {isEditing ? (
          <VisualEditor
            ref={visualEditorRef}
            content={content}
            onChange={(newContent) => handleContentChange(newContent)}
            onKeyDown={handleKeyDown}
            fontSize={fontSize}
            lineHeight="26px"
          />
        ) : (
          /* WYSIWYG Formatted View when viewing note */
          <div
            onDoubleClick={(e) => {
              if (!isEditing && !note?.isTrash) {
                const target = e.target as HTMLElement;
                if (target.closest('[data-no-double-click]') || target.tagName === 'BUTTON' || target.closest('button')) {
                  return;
                }
                setIsEditing(true);
                setTimeout(() => visualEditorRef.current?.focus(), 50);
              }
            }}
            style={{ flex: 1, minHeight: '400px', cursor: 'text', userSelect: 'text' }}
          >
            <MarkdownRenderView
              content={content}
              fontSize={fontSize}
              onToggleTask={(taskIndex) => {
                let taskCount = 0;
                const lines = content.split('\n');
                for (let l = 0; l < lines.length; l++) {
                  const line = lines[l];
                  const trimmed = line.trim();
                  if (
                    trimmed.startsWith('○ ') ||
                    trimmed.startsWith('✓ ') ||
                    /^[\-*]\s+\[[ xX]\]/.test(trimmed)
                  ) {
                    if (taskCount === taskIndex) {
                      if (trimmed.startsWith('○ ')) {
                        lines[l] = line.replace('○ ', '✓ ');
                      } else if (trimmed.startsWith('✓ ')) {
                        lines[l] = line.replace('✓ ', '○ ');
                      } else if (/\[ \]/i.test(trimmed)) {
                        lines[l] = line.replace(/\[ \]/, '[x]');
                      } else {
                        lines[l] = line.replace(/\[[xX]\]/, '[ ]');
                      }
                      break;
                    }
                    taskCount++;
                  }
                }
                const updated = lines.join('\n');
                setContent(updated);
                saveChanges(title, updated);
                if (settings.hapticsEnabled && 'vibrate' in navigator) {
                  try { navigator.vibrate(10); } catch (_) {}
                }
              }}
            />
          </div>
        )}
        </div>
      </div>



      {/* 5. Note Intel Bottom Sheet */}
      {showInfoSheet && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.6)',
          backdropFilter: 'blur(8px)',
          zIndex: 1000,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <div style={{
            width: '420px',
            backgroundColor: 'var(--bg-primary)',
            borderRadius: '16px',
            border: '0.5px solid var(--divider)',
            padding: '20px',
            boxShadow: '0 24px 48px rgba(0,0,0,0.9)'
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '16px'
            }}>
              <span style={{
                fontFamily: 'var(--font-display)',
                fontWeight: 700,
                fontSize: '12px',
                color: 'var(--text-secondary)',
                letterSpacing: '0.5px'
              }}>
                NOTE INTEL
              </span>
              <button
                onClick={() => setShowInfoSheet(false)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--accent)',
                  fontSize: '15px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Done
              </button>
            </div>

            <div style={{
              backgroundColor: 'var(--bg-card)',
              borderRadius: '12px',
              border: '0.5px solid var(--divider)',
              overflow: 'hidden'
            }}>
              <IntelRow title="Words" value={`${words}`} />
              <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '16px' }} />
              <IntelRow title="Characters" value={`${chars}`} />
              <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '16px' }} />
              <IntelRow title="Reading Time" value={`${Math.max(1, Math.round(words / 200))} min`} />
              <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '16px' }} />
              <IntelRow title="Format" value="Standard Markdown (.md)" />
              <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '16px' }} />
              <IntelRow title="Location" value="Documents/MonolithNotes" />
            </div>

            <button
              onClick={() => {
                navigator.clipboard.writeText(`${title}\n\n${content}`.trim());
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
              }}
              style={{
                width: '100%',
                height: '46px',
                marginTop: '16px',
                backgroundColor: 'var(--bg-card)',
                border: '0.5px solid var(--divider)',
                borderRadius: '12px',
                color: copied ? 'var(--emerald)' : 'var(--accent)',
                fontFamily: 'var(--font-text)',
                fontSize: '15px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                cursor: 'pointer'
              }}
            >
              <Copy size={16} />
              {copied ? 'Copied to Clipboard!' : 'Copy Note as Markdown'}
            </button>
          </div>
        </div>
      )}

      {/* Floating Movable Edit Pencil Button (Visible only in View Mode when note is not in Trash) */}
      {!isEditing && !note?.isTrash && (
        <button
          onPointerDown={handleFabPointerDown}
          onPointerMove={handleFabPointerMove}
          onPointerUp={handleFabPointerUp}
          onPointerCancel={handleFabPointerUp}
          onClick={handleFabClick}
          title="Edit Note (Drag to move)"
          style={{
            position: 'absolute',
            bottom: `${fabPos.bottom}px`,
            right: `${fabPos.right}px`,
            width: '38px',
            height: '38px',
            borderRadius: '8px',
            backgroundColor: isDraggingFab ? 'var(--bg-elevated)' : 'var(--bg-card)',
            border: '0.5px solid var(--divider)',
            boxShadow: isDraggingFab ? '0 8px 24px rgba(0, 0, 0, 0.45)' : '0 4px 12px rgba(0, 0, 0, 0.35)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: isDraggingFab ? 'grabbing' : 'grab',
            userSelect: 'none',
            touchAction: 'none',
            color: isDraggingFab ? 'var(--accent)' : 'var(--text-secondary)',
            zIndex: 90,
            transform: isDraggingFab ? 'scale(1.08)' : 'scale(1)',
            transition: isDraggingFab
              ? 'none'
              : 'transform 0.15s ease, background-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease'
          }}
          onMouseEnter={(e) => {
            if (!isDraggingFab) {
              e.currentTarget.style.backgroundColor = 'var(--bg-elevated)';
              e.currentTarget.style.color = 'var(--accent)';
            }
          }}
          onMouseLeave={(e) => {
            if (!isDraggingFab) {
              e.currentTarget.style.backgroundColor = 'var(--bg-card)';
              e.currentTarget.style.color = 'var(--text-secondary)';
            }
          }}
        >
          <PenLine size={18} />
        </button>
      )}
    </div>
  );
};

const topToolbarBtnStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  padding: '6px 8px',
  cursor: 'pointer',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  borderRadius: '6px',
  transition: 'background 0.15s ease'
};

const IntelRow: React.FC<{ title: string; value: string }> = ({ title, value }) => (
  <div style={{
    padding: '12px 16px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    fontSize: '14px'
  }}>
    <span style={{ color: 'var(--text-primary)' }}>{title}</span>
    <span style={{ color: 'var(--text-secondary)' }}>{value}</span>
  </div>
);
