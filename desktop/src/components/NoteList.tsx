import React, { useState, useRef, useEffect, useMemo } from 'react';
import { 
  Search, 
  Pin, 
  Trash2, 
  Plus, 
  X, 
  ArrowLeft, 
  MoreHorizontal, 
  Share2, 
  Pencil, 
  FolderInput, 
  Folder, 
  Check, 
  Cloud, 
  RefreshCw,
  ChevronDown,
  Hash,
  RotateCcw,
  Copy,
  CopyPlus,
  FolderPlus,
  ClipboardPaste
} from 'lucide-react';
import type { Note, AppSettings, NoteSortOrder } from '../types';
import { getDisplayTitle, getTaskStats, formatDate } from '../utils';

interface NoteListProps {
  notes: Note[];
  selectedNoteId: string | null;
  currentFolder: string | null;
  isTrash?: boolean;
  onEmptyTrash?: () => void;
  onRestoreNote?: (note: Note) => void;
  searchQuery: string;
  selectedTag: string | null;
  settings: AppSettings;
  folders?: string[];
  isSidebarOpen?: boolean;
  isNoteListOpen?: boolean;
  onToggleSidebar?: () => void;
  onToggleNoteList?: () => void;
  onBackToVault?: () => void;
  onSelectNote: (note: Note) => void;
  onNewNote: () => void;
  onPinNote: (note: Note) => void;
  onDeleteNote: (note: Note) => void;
  onUpdateNote?: (note: Note) => void;
  onSearchChange: (query: string) => void;
  onTagSelect: (tag: string | null) => void;
  isCloudSyncing?: boolean;
  isCloudLoggedIn?: boolean;
  onTriggerCloudSync?: () => void;
  onOpenCloudModal?: () => void;
  copiedNote?: Note | null;
  onCopyNote?: (note: Note) => void;
  onDuplicateNote?: (note: Note, targetFolder?: string) => void;
  onPasteNote?: (targetFolder?: string) => void;
}

export const NoteList: React.FC<NoteListProps> = ({
  notes,
  selectedNoteId,
  currentFolder,
  searchQuery,
  selectedTag,
  settings,
  folders = [],
  isSidebarOpen: _isSidebarOpen,
  isNoteListOpen: _isNoteListOpen,
  onToggleSidebar: _onToggleSidebar,
  onToggleNoteList: _onToggleNoteList,
  onBackToVault,
  onSelectNote,
  onNewNote,
  onPinNote,
  onDeleteNote,
  onUpdateNote,
  onSearchChange,
  onTagSelect,
  isCloudSyncing,
  isCloudLoggedIn,
  onTriggerCloudSync,
  onOpenCloudModal,
  isTrash = false,
  onEmptyTrash,
  onRestoreNote,
  copiedNote,
  onCopyNote,
  onDuplicateNote,
  onPasteNote,
}) => {
  const inTrash = isTrash || currentFolder === 'Deleted';
  const [contextMenu, setContextMenu] = useState<{ note: Note; x: number; y: number } | null>(null);
  const [showEmptyTrashModal, setShowEmptyTrashModal] = useState(false);
  const [renameNote, setRenameNote] = useState<Note | null>(null);
  const [renameTitle, setRenameTitle] = useState('');
  const [folderActionModal, setFolderActionModal] = useState<{ type: 'move' | 'copy'; note: Note } | null>(null);
  const [bgContextMenu, setBgContextMenu] = useState<{ x: number; y: number } | null>(null);
  const [newFolderName, setNewFolderName] = useState('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [hoveredNoteId, setHoveredNoteId] = useState<string | null>(null);
  const [isTagDropdownOpen, setIsTagDropdownOpen] = useState(false);
  const [tagSearchQuery, setTagSearchQuery] = useState('');
  const tagDropdownRef = useRef<HTMLDivElement>(null);

  // Keyboard shortcut: Ctrl+V / Cmd+V to paste copied note when not typing in an input
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && (e.key === 'v' || e.key === 'V')) {
        const activeTag = (document.activeElement?.tagName || '').toLowerCase();
        if (activeTag !== 'input' && activeTag !== 'textarea' && copiedNote) {
          e.preventDefault();
          onPasteNote?.(currentFolder === 'Deleted' ? 'Notes' : (currentFolder || 'Notes'));
          showToast(`Pasted note into ${currentFolder === 'Deleted' ? 'Notes' : (currentFolder || 'Notes')}`);
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [copiedNote, currentFolder, onPasteNote]);

  useEffect(() => {
    if (!isTagDropdownOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (tagDropdownRef.current && !tagDropdownRef.current.contains(e.target as Node)) {
        setIsTagDropdownOpen(false);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsTagDropdownOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isTagDropdownOpen]);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 2400);
  };

  const handleShare = (note: Note) => {
    setContextMenu(null);
    const textToShare = `${getDisplayTitle(note)}\n\n${note.content}`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(textToShare);
      showToast('Note copied to clipboard');
    }
  };

  const handleStartRename = (note: Note) => {
    setContextMenu(null);
    setRenameNote(note);
    setRenameTitle(note.title || getDisplayTitle(note));
  };

  const handleSaveRename = () => {
    if (renameNote && onUpdateNote) {
      onUpdateNote({
        ...renameNote,
        title: renameTitle.trim(),
        lastModified: Date.now()
      });
      showToast('Note renamed');
    }
    setRenameNote(null);
  };

  const handleStartMoveTo = (note: Note) => {
    setContextMenu(null);
    setFolderActionModal({ type: 'move', note });
    setNewFolderName('');
  };

  const handleStartCopyTo = (note: Note) => {
    setContextMenu(null);
    setFolderActionModal({ type: 'copy', note });
    setNewFolderName('');
  };

  const handleConfirmFolderAction = (targetFolder: string) => {
    if (!folderActionModal) return;
    const { type, note } = folderActionModal;
    if (type === 'move') {
      if (onUpdateNote) {
        onUpdateNote({
          ...note,
          folder: targetFolder,
          lastModified: Date.now()
        });
        showToast(`Moved to ${targetFolder}`);
      }
    } else {
      if (onDuplicateNote) {
        onDuplicateNote(note, targetFolder);
        showToast(`Copied to ${targetFolder}`);
      }
    }
    setFolderActionModal(null);
  };

  const handlePin = (note: Note) => {
    setContextMenu(null);
    onPinNote(note);
  };

  const handleDelete = (note: Note) => {
    setContextMenu(null);
    onDeleteNote(note);
    showToast('Note moved to Trash');
  };

  const allFolders = Array.from(new Set(['Notes', ...folders, ...notes.map(n => n.folder || 'Notes')])).sort();

  // Filter notes by search & tag
  const filteredNotes = notes.filter((note) => {
    const displayTitle = getDisplayTitle(note);
    const matchesSearch =
      searchQuery.trim() === '' ||
      displayTitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
      note.content.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesTag = selectedTag === null || note.tags.includes(selectedTag);
    return matchesSearch && matchesTag;
  });

  // Extract all unique tags
  const allTags = Array.from(new Set(notes.flatMap(n => n.tags))).sort();

  const getNoteCreatedTime = (n: Note): number => {
    if (n.createdAt) return n.createdAt;
    if (n.id && n.id.startsWith('note-')) {
      const parsed = parseInt(n.id.replace('note-', ''), 10);
      if (!isNaN(parsed) && parsed > 1000000000) return parsed;
    }
    return n.lastModified;
  };

  const sortNotesList = (list: Note[], sortOrder: NoteSortOrder): Note[] => {
    return [...list].sort((a, b) => {
      if (sortOrder === 'TITLE') {
        return getDisplayTitle(a).localeCompare(getDisplayTitle(b));
      }
      if (sortOrder === 'DATE_CREATED') {
        return getNoteCreatedTime(b) - getNoteCreatedTime(a);
      }
      return b.lastModified - a.lastModified;
    });
  };

  // Separate pinned and unpinned notes, sorted by settings.sortOrder
  const pinnedNotes = sortNotesList(filteredNotes.filter(n => n.isPinned), settings.sortOrder);
  const unpinnedNotes = sortNotesList(filteredNotes.filter(n => !n.isPinned), settings.sortOrder);

  // Group unpinned notes by time category or alphabetical group
  const groupNotes = (list: Note[]) => {
    if (settings.sortOrder === 'TITLE') {
      const groups: { [key: string]: Note[] } = {};
      list.forEach(note => {
        const title = getDisplayTitle(note);
        const first = title.charAt(0).toUpperCase();
        const key = first >= 'A' && first <= 'Z' ? first : '#';
        if (!groups[key]) groups[key] = [];
        groups[key].push(note);
      });
      return Object.keys(groups).sort().map(k => ({
        title: k,
        notes: groups[k].sort((a, b) => getDisplayTitle(a).localeCompare(getDisplayTitle(b)))
      }));
    }

    const now = Date.now();
    const oneDay = 24 * 60 * 60 * 1000;
    const sevenDays = 7 * oneDay;
    const thirtyDays = 30 * oneDay;

    const today: Note[] = [];
    const prev7: Note[] = [];
    const prev30: Note[] = [];
    const earlier: Note[] = [];

    const isSortCreated = settings.sortOrder === 'DATE_CREATED';
    const getTime = (n: Note) => isSortCreated ? getNoteCreatedTime(n) : n.lastModified;

    list.forEach(note => {
      const diff = now - getTime(note);
      if (diff < oneDay) today.push(note);
      else if (diff < sevenDays) prev7.push(note);
      else if (diff < thirtyDays) prev30.push(note);
      else earlier.push(note);
    });

    const sortByTime = (a: Note, b: Note) => getTime(b) - getTime(a);
    today.sort(sortByTime);
    prev7.sort(sortByTime);
    prev30.sort(sortByTime);
    earlier.sort(sortByTime);

    const res = [];
    if (today.length) res.push({ title: 'TODAY', notes: today });
    if (prev7.length) res.push({ title: 'PREVIOUS 7 DAYS', notes: prev7 });
    if (prev30.length) res.push({ title: 'PREVIOUS 30 DAYS', notes: prev30 });
    if (earlier.length) res.push({ title: 'EARLIER', notes: earlier });
    return res;
  };

  const tagCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    notes.forEach(note => {
      note.tags.forEach(t => {
        counts[t] = (counts[t] || 0) + 1;
      });
    });
    return counts;
  }, [notes]);

  const filteredTags = allTags.filter(t => t.toLowerCase().includes(tagSearchQuery.toLowerCase().trim()));

  const grouped = groupNotes(unpinnedNotes);

  return (
    <div style={{
      width: '100%',
      height: '100%',
      backgroundColor: 'transparent',
      display: 'flex',
      flexDirection: 'column',
      flexShrink: 0,
      userSelect: 'none'
    }}>
      {/* Top Header */}
      <div style={{
        padding: '12px 16px 8px 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {/* Back to Vault button (Arrow icon only) */}
          {onBackToVault && (
            <button
              onClick={onBackToVault}
              title="Back to Vault (Folders)"
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--accent)',
                cursor: 'pointer',
                padding: '6px',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'background 0.15s ease'
              }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <ArrowLeft size={19} color="var(--accent)" />
            </button>
          )}

          <h1 style={{
            fontFamily: 'var(--font-display)',
            fontSize: '22px',
            fontWeight: 700,
            color: 'var(--text-primary)',
            letterSpacing: '-0.3px',
            margin: 0
          }}>
            {currentFolder || 'Notes'}
          </h1>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          {inTrash ? (
            <button
              onClick={() => setShowEmptyTrashModal(true)}
              disabled={notes.length === 0}
              title={notes.length === 0 ? 'Trash is empty' : 'Permanently erase all notes in trash'}
              style={{
                background: 'none',
                border: 'none',
                color: notes.length > 0 ? 'var(--danger)' : 'var(--text-tertiary)',
                cursor: notes.length > 0 ? 'pointer' : 'default',
                padding: '5px 12px',
                borderRadius: '7px',
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                fontWeight: 600,
                fontSize: '13px',
                opacity: notes.length > 0 ? 1 : 0.45,
                backgroundColor: notes.length > 0 ? 'rgba(255, 69, 58, 0.1)' : 'transparent',
                transition: 'all 0.15s ease'
              }}
              onMouseEnter={(e) => {
                if (notes.length > 0) e.currentTarget.style.backgroundColor = 'rgba(255, 69, 58, 0.2)';
              }}
              onMouseLeave={(e) => {
                if (notes.length > 0) e.currentTarget.style.backgroundColor = 'rgba(255, 69, 58, 0.1)';
              }}
            >
              <Trash2 size={14} />
              <span>Empty</span>
            </button>
          ) : (
            <>
              <button
                onClick={() => {
                  if (isCloudLoggedIn) {
                    onTriggerCloudSync?.();
                  } else {
                    onOpenCloudModal?.();
                  }
                }}
                title={isCloudSyncing ? 'Syncing with Monolith Cloud...' : isCloudLoggedIn ? 'Monolith Cloud Vault: Connected (Click to sync)' : 'Connect Monolith Cloud Vault'}
                style={{
                  background: 'none',
                  border: 'none',
                  color: isCloudLoggedIn ? '#10B981' : 'var(--accent)',
                  cursor: 'pointer',
                  padding: '6px',
                  borderRadius: '6px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  position: 'relative',
                  transition: 'background 0.15s'
                }}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                {isCloudSyncing ? (
                  <RefreshCw size={18} style={{ animation: 'spin 1s linear infinite' }} />
                ) : (
                  <Cloud size={19} />
                )}
                {isCloudLoggedIn && !isCloudSyncing && (
                  <div style={{
                    position: 'absolute',
                    bottom: '4px',
                    right: '4px',
                    width: '5px',
                    height: '5px',
                    borderRadius: '50%',
                    backgroundColor: '#10B981',
                    boxShadow: '0 0 5px #10B981'
                  }} />
                )}
              </button>

              <button
                onClick={onNewNote}
                title="New Note"
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--accent)',
                  cursor: 'pointer',
                  padding: '6px',
                  borderRadius: '6px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  transition: 'background 0.15s'
                }}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                <Plus size={20} />
              </button>
            </>
          )}
        </div>
      </div>

      {/* Search & Tag Filter Bar */}
      <div style={{
        padding: '0 16px 10px 16px',
        position: 'relative',
        width: '100%',
        boxSizing: 'border-box'
      }} ref={tagDropdownRef}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '5px',
          width: '100%',
          boxSizing: 'border-box'
        }}>
          {/* iOS Search Capsule */}
          <div style={{
            flex: '1 1 0px',
            minWidth: 0,
            width: 0,
            height: '36px',
            backgroundColor: 'var(--bg-search)',
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            padding: '0 10px',
            gap: '8px',
            border: '0.5px solid var(--divider)',
            boxSizing: 'border-box'
          }}>
            <Search size={16} color="var(--text-tertiary)" style={{ flexShrink: 0 }} />
            <input
              type="text"
              placeholder="Search"
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              style={{
                flex: '1 1 0px',
                minWidth: 0,
                width: '100%',
                background: 'none',
                border: 'none',
                outline: 'none',
                color: 'var(--text-primary)',
                fontFamily: 'var(--font-text)',
                fontSize: '14px',
                padding: 0
              }}
            />
            {searchQuery && (
              <button
                onClick={() => onSearchChange('')}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-tertiary)',
                  cursor: 'pointer',
                  padding: '2px',
                  display: 'flex',
                  flexShrink: 0
                }}
              >
                <X size={14} />
              </button>
            )}
          </div>

          {/* Tag Dropdown Trigger Button (matching search capsule height: 36px & style) */}
          {allTags.length > 0 && (
            <button
              onClick={() => setIsTagDropdownOpen(!isTagDropdownOpen)}
              title={selectedTag ? `Filter: #${selectedTag} (Click to change)` : 'Filter by hashtag'}
              style={{
                height: '36px',
                width: '42px',
                padding: 0,
                backgroundColor: isTagDropdownOpen ? 'var(--bg-elevated)' : 'var(--bg-search)',
                borderRadius: '10px',
                border: '0.5px solid var(--divider)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '3px',
                cursor: 'pointer',
                color: selectedTag ? 'var(--accent)' : 'var(--text-tertiary)',
                boxSizing: 'border-box',
                flexShrink: 0,
                transition: 'background-color 0.15s ease'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--bg-elevated)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = isTagDropdownOpen ? 'var(--bg-elevated)' : 'var(--bg-search)';
              }}
            >
              <Hash size={14} color={selectedTag ? 'var(--accent)' : 'currentColor'} />
              <ChevronDown
                size={12}
                color={selectedTag ? 'var(--accent)' : 'currentColor'}
                style={{
                  transform: isTagDropdownOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                  transition: 'transform 0.18s ease'
                }}
              />
            </button>
          )}
        </div>

        {/* Floating Dropdown Menu (Reddit-style) */}
        {isTagDropdownOpen && allTags.length > 0 && (
          <div style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            right: '16px',
            zIndex: 150,
            width: 'min(240px, calc(100% - 32px))',
            backgroundColor: 'var(--bg-card)',
            border: '0.5px solid var(--divider)',
            borderRadius: '12px',
            boxShadow: '0 12px 32px rgba(0,0,0,0.65)',
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column'
          }}>

            {/* Search tags input if > 4 tags */}
            {allTags.length > 4 && (
              <div style={{
                padding: '8px 10px',
                borderBottom: '0.5px solid var(--divider)',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}>
                <Search size={13} color="var(--text-tertiary)" />
                <input
                  type="text"
                  value={tagSearchQuery}
                  onChange={(e) => setTagSearchQuery(e.target.value)}
                  placeholder="Search tags..."
                  autoFocus
                  style={{
                    background: 'none',
                    border: 'none',
                    outline: 'none',
                    fontSize: '12px',
                    color: 'var(--text-primary)',
                    fontFamily: 'var(--font-text)',
                    width: '100%'
                  }}
                />
                {tagSearchQuery && (
                  <button
                    onClick={() => setTagSearchQuery('')}
                    style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', color: 'var(--text-tertiary)' }}
                  >
                    <X size={12} />
                  </button>
                )}
              </div>
            )}

            {/* Tag options list */}
            <div style={{ maxHeight: '220px', overflowY: 'auto', padding: '4px' }}>
              {/* Option: All Tags */}
              {(!tagSearchQuery || 'all tags'.includes(tagSearchQuery.toLowerCase())) && (
                <div
                  onClick={() => {
                    onTagSelect(null);
                    setIsTagDropdownOpen(false);
                    setTagSearchQuery('');
                    if (settings.hapticsEnabled && 'vibrate' in navigator) {
                      try { navigator.vibrate(8); } catch (_) {}
                    }
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '7px 10px',
                    borderRadius: '7px',
                    fontSize: '12.5px',
                    fontWeight: selectedTag === null ? 600 : 400,
                    color: selectedTag === null ? 'var(--accent)' : 'var(--text-primary)',
                    backgroundColor: selectedTag === null ? 'rgba(255,255,255,0.06)' : 'transparent',
                    cursor: 'pointer'
                  }}
                  onMouseEnter={(e) => {
                    if (selectedTag !== null) e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.04)';
                  }}
                  onMouseLeave={(e) => {
                    if (selectedTag !== null) e.currentTarget.style.backgroundColor = 'transparent';
                  }}
                >
                  <span>All Tags</span>
                  <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{notes.length} notes</span>
                </div>
              )}

              <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', margin: '4px 0' }} />

              {filteredTags.length === 0 ? (
                <div style={{ padding: '8px 10px', fontSize: '12px', color: 'var(--text-tertiary)', textAlign: 'center' }}>
                  No tags found
                </div>
              ) : (
                filteredTags.map((tag) => {
                  const isSelected = selectedTag === tag;
                  return (
                    <div
                      key={tag}
                      onClick={() => {
                        onTagSelect(isSelected ? null : tag);
                        setIsTagDropdownOpen(false);
                        setTagSearchQuery('');
                        if (settings.hapticsEnabled && 'vibrate' in navigator) {
                          try { navigator.vibrate(8); } catch (_) {}
                        }
                      }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '7px 10px',
                        borderRadius: '7px',
                        fontSize: '12.5px',
                        fontWeight: isSelected ? 600 : 400,
                        color: isSelected ? 'var(--accent)' : 'var(--text-primary)',
                        backgroundColor: isSelected ? 'var(--accent-container)' : 'transparent',
                        cursor: 'pointer'
                      }}
                      onMouseEnter={(e) => {
                        if (!isSelected) e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.04)';
                      }}
                      onMouseLeave={(e) => {
                        if (!isSelected) e.currentTarget.style.backgroundColor = 'transparent';
                      }}
                    >
                      <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <span style={{ opacity: 0.6 }}>#</span>
                        <span>{tag}</span>
                      </span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                          {tagCounts[tag] || 0}
                        </span>
                        {isSelected && <Check size={13} color="var(--accent)" />}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>

      {/* Note List Scrollable Container */}
      <div 
        onContextMenu={(e) => {
          if ((e.target as HTMLElement).closest('.note-row-item')) return;
          e.preventDefault();
          setContextMenu(null);
          setBgContextMenu({
            x: Math.min(e.clientX, window.innerWidth - 190),
            y: Math.min(e.clientY, window.innerHeight - 150)
          });
        }}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '0 12px 20px 12px',
          display: 'flex',
          flexDirection: 'column',
          gap: '16px'
        }}
      >
        {filteredNotes.length === 0 ? (
          inTrash ? null : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '240px',
              color: 'var(--text-tertiary)',
              gap: '8px',
              fontSize: '13.5px',
              padding: '0 20px',
              textAlign: 'center'
            }}>
              <span>No notes</span>
            </div>
          )
        ) : (
          <>
            {/* Pinned Section */}
            {pinnedNotes.length > 0 && (
              <div>
                <div style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '11px',
                  fontWeight: 700,
                  color: 'var(--text-tertiary)',
                  letterSpacing: '0.5px',
                  padding: '0 8px 6px 8px'
                }}>
                  PINNED
                </div>
                <div style={{
                  backgroundColor: 'var(--bg-card)',
                  borderRadius: '12px',
                  overflow: 'hidden',
                  border: '0.5px solid var(--divider)'
                }}>
                  {pinnedNotes.map((note, index) => renderNoteRow(note, index))}
                </div>
              </div>
            )}

            {/* Grouped Unpinned Sections */}
            {grouped.map(grp => (
              <div key={grp.title}>
                <div style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '11px',
                  fontWeight: 700,
                  color: 'var(--text-tertiary)',
                  letterSpacing: '0.5px',
                  padding: '0 8px 6px 8px'
                }}>
                  {grp.title}
                </div>
                <div style={{
                  backgroundColor: 'var(--bg-card)',
                  borderRadius: '12px',
                  overflow: 'hidden',
                  border: '0.5px solid var(--divider)'
                }}>
                  {grp.notes.map((note, index) => renderNoteRow(note, index))}
                </div>
              </div>
            ))}
          </>
        )}
      </div>

      {/* Modern Context Menu (Right Click & 3-Dots Button) */}
      {contextMenu && (
        <>
          <div
            onClick={() => setContextMenu(null)}
            onContextMenu={(e) => {
              e.preventDefault();
              setContextMenu(null);
            }}
            style={{
              position: 'fixed',
              inset: 0,
              zIndex: 998,
              backgroundColor: 'transparent'
            }}
          />
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'fixed',
              left: `${Math.max(12, contextMenu.x)}px`,
              top: `${Math.max(12, contextMenu.y)}px`,
              zIndex: 999,
              minWidth: '175px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              borderRadius: '10px',
              boxShadow: '0 12px 32px rgba(0, 0, 0, 0.75)',
              padding: '5px',
              display: 'flex',
              flexDirection: 'column',
              gap: '2px',
              userSelect: 'none'
            }}
          >
            {/* If in Trash / Deleted */}
            {(contextMenu.note.isTrash || inTrash) ? (
              <>
                {/* Put Back / Restore */}
                <button
                  onClick={() => {
                    const noteToRestore = contextMenu.note;
                    setContextMenu(null);
                    onRestoreNote?.(noteToRestore);
                    setToastMessage('Note restored');
                    setTimeout(() => setToastMessage(null), 2500);
                  }}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <RotateCcw size={14} color="var(--accent)" />
                  <span style={{ color: 'var(--accent)', fontWeight: 500 }}>Put Back</span>
                </button>

                <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', margin: '3px 0' }} />

                {/* Delete Permanently */}
                <button
                  onClick={() => {
                    const noteToDelete = contextMenu.note;
                    setContextMenu(null);
                    onDeleteNote(noteToDelete);
                    setToastMessage('Note deleted permanently');
                    setTimeout(() => setToastMessage(null), 2500);
                  }}
                  style={{
                    ...menuItemBtnStyle,
                    color: 'var(--danger)'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 69, 58, 0.12)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Trash2 size={14} color="var(--danger)" />
                  <span>Delete Permanently</span>
                </button>
              </>
            ) : (
              <>
                {/* Copy Note */}
                <button
                  onClick={() => {
                    const noteToCopy = contextMenu.note;
                    setContextMenu(null);
                    onCopyNote?.(noteToCopy);
                    showToast('Note copied');
                  }}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Copy size={14} color="var(--text-secondary)" />
                  <span>Copy Note</span>
                </button>

                {/* Duplicate Note */}
                <button
                  onClick={() => {
                    const noteToDup = contextMenu.note;
                    setContextMenu(null);
                    onDuplicateNote?.(noteToDup);
                    showToast('Note duplicated');
                  }}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <CopyPlus size={14} color="var(--text-secondary)" />
                  <span>Duplicate</span>
                </button>

                {/* Copy to Folder */}
                <button
                  onClick={() => handleStartCopyTo(contextMenu.note)}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <FolderPlus size={14} color="var(--text-secondary)" />
                  <span>Copy to...</span>
                </button>

                {/* Move to Folder */}
                <button
                  onClick={() => handleStartMoveTo(contextMenu.note)}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <FolderInput size={14} color="var(--text-secondary)" />
                  <span>Move to...</span>
                </button>

                {/* Rename */}
                <button
                  onClick={() => handleStartRename(contextMenu.note)}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Pencil size={14} color="var(--text-secondary)" />
                  <span>Rename</span>
                </button>

                {/* Pin / Unpin */}
                <button
                  onClick={() => handlePin(contextMenu.note)}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Pin size={14} color="var(--text-secondary)" />
                  <span>{contextMenu.note.isPinned ? 'Unpin' : 'Pin'}</span>
                </button>

                {/* Share */}
                <button
                  onClick={() => handleShare(contextMenu.note)}
                  style={menuItemBtnStyle}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Share2 size={14} color="var(--text-secondary)" />
                  <span>Share</span>
                </button>

                <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', margin: '3px 0' }} />

                {/* Delete */}
                <button
                  onClick={() => handleDelete(contextMenu.note)}
                  style={{
                    ...menuItemBtnStyle,
                    color: 'var(--danger)'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 69, 58, 0.12)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Trash2 size={14} color="var(--danger)" />
                  <span>Delete</span>
                </button>
              </>
            )}
          </div>
        </>
      )}

      {/* Background Context Menu (Right Click on empty space in NoteList) */}
      {bgContextMenu && (
        <>
          <div
            onClick={() => setBgContextMenu(null)}
            onContextMenu={(e) => {
              e.preventDefault();
              setBgContextMenu(null);
            }}
            style={{
              position: 'fixed',
              inset: 0,
              zIndex: 998,
              backgroundColor: 'transparent'
            }}
          />
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'fixed',
              left: `${Math.max(12, bgContextMenu.x)}px`,
              top: `${Math.max(12, bgContextMenu.y)}px`,
              zIndex: 999,
              minWidth: '175px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              borderRadius: '10px',
              boxShadow: '0 12px 32px rgba(0, 0, 0, 0.75)',
              padding: '5px',
              display: 'flex',
              flexDirection: 'column',
              gap: '2px',
              userSelect: 'none'
            }}
          >
            {copiedNote && (
              <button
                onClick={() => {
                  setBgContextMenu(null);
                  onPasteNote?.(currentFolder === 'Deleted' ? 'Notes' : (currentFolder || 'Notes'));
                  showToast(`Pasted note into ${currentFolder === 'Deleted' ? 'Notes' : (currentFolder || 'Notes')}`);
                }}
                style={menuItemBtnStyle}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                <ClipboardPaste size={14} color="var(--accent)" />
                <span style={{ color: 'var(--accent)', fontWeight: 500 }}>Paste Note</span>
              </button>
            )}
            <button
              onClick={() => {
                setBgContextMenu(null);
                onNewNote();
              }}
              style={menuItemBtnStyle}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Plus size={14} color="var(--text-secondary)" />
              <span>New Note</span>
            </button>
          </div>
        </>
      )}

      {/* Rename Note Modal */}
      {renameNote && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1000,
            backgroundColor: 'rgba(0,0,0,0.6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backdropFilter: 'blur(3px)'
          }}
          onClick={() => setRenameNote(null)}
        >
          <div
            style={{
              width: '320px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              borderRadius: '12px',
              padding: '16px',
              boxShadow: '0 20px 48px rgba(0,0,0,0.85)',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ fontFamily: 'var(--font-display)', fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
              Rename Note
            </div>
            <input
              autoFocus
              value={renameTitle}
              onChange={(e) => setRenameTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSaveRename();
                if (e.key === 'Escape') setRenameNote(null);
              }}
              placeholder="Note title..."
              style={{
                width: '100%',
                boxSizing: 'border-box',
                padding: '8px 12px',
                borderRadius: '6px',
                border: '1px solid var(--divider)',
                backgroundColor: 'var(--bg-elevated)',
                color: 'var(--text-primary)',
                fontSize: '14px',
                outline: 'none'
              }}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button
                onClick={() => setRenameNote(null)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  border: 'none',
                  background: 'none',
                  color: 'var(--text-secondary)',
                  cursor: 'pointer',
                  fontSize: '13px'
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleSaveRename}
                style={{
                  padding: '6px 16px',
                  borderRadius: '6px',
                  border: 'none',
                  backgroundColor: 'var(--accent)',
                  color: '#000',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontSize: '13px'
                }}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Move to Folder Modal */}
      {/* Move / Copy to Folder Modal */}
      {folderActionModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1000,
            backgroundColor: 'rgba(0,0,0,0.6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backdropFilter: 'blur(3px)'
          }}
          onClick={() => setFolderActionModal(null)}
        >
          <div
            style={{
              width: '320px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              borderRadius: '12px',
              padding: '16px',
              boxShadow: '0 20px 48px rgba(0,0,0,0.85)',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span style={{ fontFamily: 'var(--font-display)', fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
                {folderActionModal.type === 'copy' ? 'Copy Note to Folder' : 'Move Note'}
              </span>
              <button
                onClick={() => setFolderActionModal(null)}
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', padding: '2px' }}
              >
                <X size={16} />
              </button>
            </div>

            <div style={{ maxHeight: '180px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '3px' }}>
              {allFolders.map(folder => (
                <button
                  key={folder}
                  onClick={() => handleConfirmFolderAction(folder)}
                  style={{
                    padding: '8px 10px',
                    borderRadius: '6px',
                    border: 'none',
                    backgroundColor: folderActionModal.note.folder === folder ? 'var(--bg-elevated)' : 'transparent',
                    color: folderActionModal.note.folder === folder ? 'var(--accent)' : 'var(--text-primary)',
                    fontFamily: 'var(--font-text)',
                    fontSize: '13.5px',
                    textAlign: 'left',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    cursor: 'pointer'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => {
                    if (folderActionModal.note.folder !== folder) e.currentTarget.style.backgroundColor = 'transparent';
                  }}
                >
                  <Folder size={14} />
                  <span style={{ flex: 1 }}>{folder}</span>
                  {folderActionModal.note.folder === folder && <Check size={14} />}
                </button>
              ))}
            </div>

            {/* Create new folder input */}
            <div style={{ display: 'flex', gap: '6px', marginTop: '4px' }}>
              <input
                placeholder="New folder name..."
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && newFolderName.trim()) {
                    handleConfirmFolderAction(newFolderName.trim());
                  }
                }}
                style={{
                  flex: 1,
                  padding: '6px 10px',
                  borderRadius: '6px',
                  border: '1px solid var(--divider)',
                  backgroundColor: 'var(--bg-elevated)',
                  color: 'var(--text-primary)',
                  fontSize: '13px',
                  outline: 'none'
                }}
              />
              <button
                disabled={!newFolderName.trim()}
                onClick={() => newFolderName.trim() && handleConfirmFolderAction(newFolderName.trim())}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  border: 'none',
                  backgroundColor: 'var(--accent)',
                  color: '#000',
                  fontWeight: 600,
                  fontSize: '12.5px',
                  cursor: newFolderName.trim() ? 'pointer' : 'default',
                  opacity: newFolderName.trim() ? 1 : 0.5
                }}
              >
                {folderActionModal.type === 'copy' ? 'Copy' : 'Move'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Empty Trash Confirmation Modal */}
      {showEmptyTrashModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1000,
            backgroundColor: 'rgba(0,0,0,0.65)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backdropFilter: 'blur(4px)'
          }}
          onClick={() => setShowEmptyTrashModal(false)}
        >
          <div
            style={{
              width: '320px',
              backgroundColor: 'var(--bg-card)',
              border: '0.5px solid var(--divider)',
              borderRadius: '14px',
              padding: '20px',
              boxShadow: '0 24px 64px rgba(0,0,0,0.85)',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{
                width: '34px',
                height: '34px',
                borderRadius: '8px',
                backgroundColor: 'rgba(255, 69, 58, 0.12)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <Trash2 size={18} color="var(--danger)" />
              </div>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: '16px', fontWeight: 700, color: 'var(--text-primary)' }}>
                Empty Trash?
              </div>
            </div>

            <div style={{ fontSize: '13.5px', color: 'var(--text-secondary)', lineHeight: '19px' }}>
              Permanently delete all notes in Trash? This cannot be undone.
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '6px' }}>
              <button
                onClick={() => setShowEmptyTrashModal(false)}
                style={{
                  padding: '7px 14px',
                  borderRadius: '7px',
                  border: '0.5px solid var(--divider)',
                  background: 'transparent',
                  color: 'var(--text-primary)',
                  cursor: 'pointer',
                  fontSize: '13px',
                  fontWeight: 500
                }}
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  setShowEmptyTrashModal(false);
                  onEmptyTrash?.();
                  setToastMessage('Trash emptied');
                  setTimeout(() => setToastMessage(null), 2500);
                }}
                style={{
                  padding: '7px 16px',
                  borderRadius: '7px',
                  border: 'none',
                  backgroundColor: 'var(--danger)',
                  color: '#FFF',
                  fontWeight: 600,
                  cursor: 'pointer',
                  fontSize: '13px'
                }}
              >
                Empty Trash
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast Feedback */}
      {toastMessage && (
        <div style={{
          position: 'fixed',
          bottom: '24px',
          left: '50%',
          transform: 'translateX(-50%)',
          backgroundColor: 'var(--bg-card)',
          border: '0.5px solid var(--divider)',
          color: 'var(--text-primary)',
          padding: '8px 16px',
          borderRadius: '8px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
          fontSize: '13px',
          fontFamily: 'var(--font-text)',
          fontWeight: 500,
          zIndex: 2000,
          display: 'flex',
          alignItems: 'center',
          gap: '8px'
        }}>
          <Check size={14} color="var(--emerald)" />
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );

  function renderNoteRow(note: Note, index: number) {
    const isSelected = selectedNoteId === note.id;
    const isHovered = hoveredNoteId === note.id;
    const taskStats = getTaskStats(note.content);
    const displayTitle = getDisplayTitle(note);

    return (
      <React.Fragment key={note.id}>
        {index > 0 && (
          <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '16px' }} />
        )}
        <div
          className="note-row-item"
          onClick={() => {
            onSelectNote(note);
            if (settings.hapticsEnabled && 'vibrate' in navigator) {
              try { navigator.vibrate(8); } catch (_) {}
            }
          }}
          onContextMenu={(e) => {
            e.preventDefault();
            e.stopPropagation();
            setContextMenu({
              note,
              x: Math.min(e.clientX, window.innerWidth - 190),
              y: Math.min(e.clientY, window.innerHeight - 240)
            });
          }}
          style={{
            padding: '11px 16px',
            backgroundColor: isSelected ? 'var(--bg-elevated)' : 'transparent',
            cursor: 'pointer',
            transition: 'background 0.1s ease',
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}
          onMouseEnter={(e) => {
            setHoveredNoteId(note.id);
            if (!isSelected) e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.03)';
          }}
          onMouseLeave={(e) => {
            setHoveredNoteId((prev) => (prev === note.id ? null : prev));
            if (!isSelected) e.currentTarget.style.backgroundColor = 'transparent';
          }}
        >
          {/* Note content: Heading on line 1, Time on line 2 */}
          <div style={{
            flex: 1,
            minWidth: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: '3px'
          }}>
            {/* Top row: Pinned icon, Heading, Task status */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              minWidth: 0
            }}>
              {note.isPinned && (
                <Pin size={12} color="var(--accent)" style={{ transform: 'rotate(45deg)', flexShrink: 0 }} />
              )}
              <span style={{
                fontFamily: 'var(--font-display)',
                fontSize: '14.5px',
                fontWeight: isSelected ? 600 : 500,
                color: isSelected ? 'var(--text-primary)' : 'var(--text-secondary)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis'
              }}>
                {displayTitle}
              </span>

              {taskStats && (
                <span style={{
                  fontSize: '10px',
                  fontWeight: 700,
                  padding: '1px 5px',
                  borderRadius: '4px',
                  backgroundColor: taskStats.completed === taskStats.total ? 'rgba(52, 199, 89, 0.2)' : 'var(--accent-container)',
                  color: taskStats.completed === taskStats.total ? 'var(--emerald)' : 'var(--accent)',
                  flexShrink: 0
                }}>
                  {taskStats.completed === taskStats.total ? '✓ Done' : `✓ ${taskStats.completed}/${taskStats.total}`}
                </span>
              )}
            </div>

            {/* Bottom row: Time */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              <span style={{
                fontSize: '11px',
                fontFamily: 'var(--font-text)',
                color: 'var(--text-tertiary)',
                whiteSpace: 'nowrap',
                letterSpacing: '-0.1px'
              }}>
                {settings.sortOrder === 'DATE_CREATED'
                  ? `Created at ${formatDate(getNoteCreatedTime(note))}`
                  : `Edited at ${formatDate(note.lastModified)}`}
              </span>
            </div>
          </div>

          {/* Three Dots More Menu Button */}
          <button
            onClick={(e) => {
              e.stopPropagation();
              const rect = e.currentTarget.getBoundingClientRect();
              setContextMenu({
                note,
                x: Math.min(rect.right - 175, window.innerWidth - 185),
                y: Math.min(rect.bottom + 4, window.innerHeight - 240)
              });
            }}
            title="Note options"
            style={{
              background: 'none',
              border: 'none',
              padding: '3px 4px',
              borderRadius: '4px',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              opacity: isHovered || isSelected ? 1 : 0,
              transition: 'opacity 0.15s ease, background 0.15s ease',
              flexShrink: 0
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = 'var(--bg-card)';
              e.currentTarget.style.color = 'var(--text-primary)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
              e.currentTarget.style.color = 'var(--text-secondary)';
            }}
          >
            <MoreHorizontal size={15} />
          </button>
        </div>
      </React.Fragment>
    );
  }
};

const menuItemBtnStyle: React.CSSProperties = {
  width: '100%',
  padding: '7px 10px',
  background: 'none',
  border: 'none',
  borderRadius: '6px',
  color: 'var(--text-primary)',
  fontFamily: 'var(--font-text)',
  fontSize: '13px',
  fontWeight: 500,
  display: 'flex',
  alignItems: 'center',
  gap: '10px',
  cursor: 'pointer',
  textAlign: 'left',
  transition: 'background 0.12s ease'
};
