import React, { useState, useEffect } from 'react';
import JSZip from 'jszip';
import { NoteList } from './components/NoteList';
import { Editor } from './components/Editor';
import { VaultScreen } from './components/VaultScreen';
import { SettingsModal } from './components/SettingsModal';
import { MonolithCloudModal } from './components/MonolithCloudModal';
import { runCloudSync, recordTombstone } from './sync/cloudSyncEngine';
import { DEFAULT_SERVER_URL } from './sync/cloudClient';
import { noteToFileName } from './sync/markdownSyncConverter';
import type { Note, AppSettings, MonolithCloudConfig } from './types';

const DEFAULT_SETTINGS: AppSettings = {
  theme: 'obsidian',
  editorFontSize: 'DEFAULT',
  newNoteStartStyle: 'TITLE',
  showWordCount: true,
  autoContinueChecklists: true,
  sortOrder: 'DATE_EDITED',
  biometricLockEnabled: false,
  hapticsEnabled: true
};

const DEFAULT_CLOUD_CONFIG: MonolithCloudConfig = {
  serverUrl: DEFAULT_SERVER_URL,
  email: '',
  token: '',
  encryptionKey: '',
  autoSyncOnLaunch: true,
  lastServerSyncTime: 0
};

const INITIAL_NOTES: Note[] = [
  {
    id: 'welcome-monolith',
    title: 'Welcome to Monolith Notes',
    content: `A Tactile, Distraction-Free Markdown Journal for Desktop with Pure Local-First File Sovereignty.

### ⚡ The Four Pillars
- [x] File Over App (Pure .md Vault)
- [x] Tactile Craft (OLED Themes & Precision Typography)
- [x] Obsidian Interoperability
- [ ] 30-Day Trash Recovery

### 🎨 Themes
You can change between **Obsidian**, **Titanium**, **Nocturne**, **Kanso**, and **Midnight** directly from Settings.

#monolith #welcome #journal`,
    tags: ['monolith', 'welcome', 'journal'],
    lastModified: Date.now(),
    isPinned: true,
    folder: 'Notes'
  },
  {
    id: 'weekly-sprint',
    title: 'Weekly Sprint Tasks',
    content: `- [x] Implement SF Pro typography suite
- [x] Configure OLED true black themes
- [ ] Connect Tauri native filesystem pipeline
- [ ] Obsidian Vault file watching

#tasks #work`,
    tags: ['tasks', 'work'],
    lastModified: Date.now() - 3600000,
    isPinned: false,
    folder: 'Work'
  }
];

export const App: React.FC = () => {
  const [settings, setSettings] = useState<AppSettings>(() => {
    const saved = localStorage.getItem('monolith_settings');
    return saved ? JSON.parse(saved) : DEFAULT_SETTINGS;
  });

  const [notes, setNotes] = useState<Note[]>(() => {
    const saved = localStorage.getItem('monolith_notes');
    const parsed: Note[] = saved ? JSON.parse(saved) : INITIAL_NOTES;
    // Normalize notes so leading `# Title` is not duplicated in content if title is present
    return parsed
      .filter(n => (n.title && n.title.trim() !== '') || (n.content && n.content.trim() !== ''))
      .map(note => {
      let content = note.content;
      const lines = content.split('\n');
      if (lines.length > 0 && lines[0].trim().startsWith('# ') && !lines[0].trim().startsWith('## ')) {
        const hTitle = lines[0].trim().substring(2).trim();
        if (note.title.trim() === hTitle || !note.title) {
          let rest = lines.slice(1);
          if (rest.length > 0 && rest[0].trim().length === 0) rest = rest.slice(1);
          content = rest.join('\n');
          return {
            ...note,
            title: note.title || hTitle,
            content
          };
        }
      }
      return note;
    });
  });

  // Current view state: 'vault' (whole screen vault/folders page) or 'workspace' (folder notes & editor)
  const [currentView, setCurrentView] = useState<'vault' | 'workspace'>('vault');
  const [isNoteListOpen, setIsNoteListOpen] = useState<boolean>(true);
  const [selectedFolder, setSelectedFolder] = useState<string | null>(null);
  const [isTrashSelected, setIsTrashSelected] = useState<boolean>(false);
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(INITIAL_NOTES[0].id);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false);
  const [cloudConfig, setCloudConfig] = useState<MonolithCloudConfig>(() => {
    const saved = localStorage.getItem('monolith_cloud_config');
    return saved ? JSON.parse(saved) : DEFAULT_CLOUD_CONFIG;
  });
  const [isCloudModalOpen, setIsCloudModalOpen] = useState<boolean>(false);
  const [isCloudSyncing, setIsCloudSyncing] = useState<boolean>(false);
  const [cloudSyncMessage, setCloudSyncMessage] = useState<string | undefined>(undefined);
  const [noteListWidth, setNoteListWidth] = useState<number>(() => {
    const saved = localStorage.getItem('monolith_notelist_width');
    return saved ? parseInt(saved, 10) : 320;
  });
  const [isResizing, setIsResizing] = useState<boolean>(false);

  // Sync cloudConfig to localStorage
  useEffect(() => {
    localStorage.setItem('monolith_cloud_config', JSON.stringify(cloudConfig));
  }, [cloudConfig]);

  // Handler for Cloud Vault Sync
  const handlePerformCloudSync = async () => {
    if (isCloudSyncing) return;
    if (!cloudConfig.token || !cloudConfig.email) {
      setIsCloudModalOpen(true);
      return;
    }

    setIsCloudSyncing(true);
    setCloudSyncMessage('Syncing with Monolith Cloud...');

    const result = await runCloudSync(notes, cloudConfig, (progressMsg) => {
      setCloudSyncMessage(progressMsg);
    });

    setIsCloudSyncing(false);
    setCloudSyncMessage(result.message);

    if (result.isSuccess) {
      setNotes(result.notes);
      setCloudConfig(result.updatedConfig);
    }
  };

  // Auto-sync on startup if enabled and logged in
  useEffect(() => {
    if (cloudConfig.token && cloudConfig.email && cloudConfig.autoSyncOnLaunch) {
      handlePerformCloudSync();
    }
  }, []);

  // Keyboard shortcuts:
  // Ctrl+\ or Cmd+\ : Toggle back to whole-screen Vault page
  // Ctrl+B or Cmd+B : Toggle Note List (focus editor mode)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === '\\') {
        e.preventDefault();
        setNotes(prev => prev.filter(n => !isNoteEmpty(n)));
        setCurrentView(prev => prev === 'vault' ? 'workspace' : 'vault');
      } else if ((e.ctrlKey || e.metaKey) && (e.key === 'b' || e.key === 'B')) {
        e.preventDefault();
        setIsNoteListOpen(prev => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Sync settings to document attribute for CSS variables
  useEffect(() => {
    document.body.setAttribute('data-theme', settings.theme);
    localStorage.setItem('monolith_settings', JSON.stringify(settings));
  }, [settings]);

  // Helper to identify empty notes with no title and no content
  const isNoteEmpty = (n: Note) => (!n.title || n.title.trim() === '') && (!n.content || n.content.trim() === '');

  // Sync notes to local storage (only persist non-empty notes)
  useEffect(() => {
    const notesToPersist = notes.filter(n => !isNoteEmpty(n));
    localStorage.setItem('monolith_notes', JSON.stringify(notesToPersist));
  }, [notes]);

  // Handle Drag Resizing between Note List and Editor
  useEffect(() => {
    if (!isResizing) return;

    const handleMouseMove = (e: MouseEvent) => {
      // 4px outer gap
      const newWidth = Math.max(220, Math.min(600, e.clientX - 4));
      setNoteListWidth(newWidth);
    };

    const handleMouseUp = () => {
      setIsResizing(false);
      localStorage.setItem('monolith_notelist_width', String(noteListWidth));
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing, noteListWidth]);

  const [customFolders, setCustomFolders] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('monolith_custom_folders');
      if (saved) return JSON.parse(saved);
    } catch {}
    return [];
  });

  useEffect(() => {
    localStorage.setItem('monolith_custom_folders', JSON.stringify(customFolders));
  }, [customFolders]);

  // Extract all distinct folders
  const folders = Array.from(new Set([
    'Notes',
    ...customFolders,
    ...notes.filter(n => !n.isTrash).map(n => n.folder || 'Notes')
  ])).sort();

  // Active notes for current folder view
  const activeNotes = notes.filter(n => {
    if (isTrashSelected) return n.isTrash;
    if (n.isTrash) return false;
    if (selectedFolder === null) return true;
    return n.folder === selectedFolder;
  });

  const selectedNote = notes.find(n => n.id === selectedNoteId) || (activeNotes.length > 0 ? activeNotes[0] : null);

  const handleNavigateToVault = () => {
    setNotes(prev => prev.filter(n => !isNoteEmpty(n)));
    setCurrentView('vault');
  };

  const handleSelectNote = (noteId: string) => {
    setNotes(prev => prev.filter(n => n.id === noteId || !isNoteEmpty(n)));
    setSelectedNoteId(noteId);
  };

  const handleCreateFolder = (name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    if (!customFolders.includes(trimmed)) {
      setCustomFolders(prev => [...prev, trimmed]);
    }
    setNotes(prev => prev.filter(n => !isNoteEmpty(n)));
    setSelectedFolder(trimmed);
  };

  const handleNewNote = () => {
    const targetFolder = isTrashSelected ? 'Notes' : (selectedFolder || 'Notes');
    const newNoteId = `note-${Date.now()}`;
    const newNote: Note = {
      id: newNoteId,
      title: '',
      content: '',
      tags: [],
      createdAt: Date.now(),
      lastModified: Date.now(),
      isPinned: false,
      folder: targetFolder
    };
    // Discard any abandoned empty notes when creating a new one
    setNotes(prev => [newNote, ...prev.filter(n => !isNoteEmpty(n))]);
    setSelectedNoteId(newNoteId);
    setIsTrashSelected(false);
  };

  const [copiedNote, setCopiedNote] = useState<Note | null>(() => {
    try {
      const saved = sessionStorage.getItem('monolith_copied_note');
      if (saved) return JSON.parse(saved);
    } catch {}
    return null;
  });

  const handleCopyNote = (note: Note) => {
    const noteData = { ...note };
    setCopiedNote(noteData);
    try {
      sessionStorage.setItem('monolith_copied_note', JSON.stringify(noteData));
    } catch {}
    try {
      const fullText = note.title ? `# ${note.title}\n\n${note.content}` : note.content;
      if (navigator.clipboard) {
        navigator.clipboard.writeText(fullText).catch(() => {});
      }
    } catch {}
  };

  const handleDuplicateNote = (note: Note, targetFolder?: string) => {
    const folder = targetFolder || note.folder || selectedFolder || 'Notes';
    const newNoteId = `note-${Date.now()}`;
    const baseTitle = note.title
      ? (note.title.endsWith(' (Copy)') ? note.title : `${note.title} (Copy)`)
      : 'Untitled Note (Copy)';
    const newNote: Note = {
      ...note,
      id: newNoteId,
      title: baseTitle,
      folder,
      createdAt: Date.now(),
      lastModified: Date.now(),
      isPinned: false,
      isTrash: false,
      trashedAt: undefined
    };
    setNotes(prev => [newNote, ...prev.filter(n => !isNoteEmpty(n))]);
    setSelectedNoteId(newNoteId);
    return newNote;
  };

  const handlePasteNote = (targetFolder?: string) => {
    if (!copiedNote) return null;
    const folder = targetFolder || (isTrashSelected ? 'Notes' : (selectedFolder || 'Notes'));
    const newNoteId = `note-${Date.now()}`;
    const newNote: Note = {
      ...copiedNote,
      id: newNoteId,
      folder,
      createdAt: Date.now(),
      lastModified: Date.now(),
      isPinned: false,
      isTrash: false,
      trashedAt: undefined
    };
    setNotes(prev => [newNote, ...prev.filter(n => !isNoteEmpty(n))]);
    setSelectedNoteId(newNoteId);
    return newNote;
  };

  const handleUpdateNote = (updated: Note) => {
    setNotes(prev => prev.map(n => n.id === updated.id ? updated : n));
  };

  const handlePinNote = (note: Note) => {
    setNotes(prev => prev.map(n => n.id === note.id ? { ...n, isPinned: !n.isPinned } : n));
  };

  const handleDeleteNote = (note: Note) => {
    const fileName = note.filePath || noteToFileName(note);
    recordTombstone(fileName);

    if (note.isTrash) {
      // Permanently delete
      setNotes(prev => prev.filter(n => n.id !== note.id));
      if (selectedNoteId === note.id) setSelectedNoteId(null);
    } else {
      // Move to safety net (.trash)
      setNotes(prev => prev.map(n => n.id === note.id ? { ...n, isTrash: true, trashedAt: Date.now() } : n));
      if (selectedNoteId === note.id) setSelectedNoteId(null);
    }
  };

  const handleRestoreNote = (note: Note) => {
    setNotes(prev => prev.map(n => n.id === note.id ? { ...n, isTrash: false, trashedAt: undefined } : n));
  };

  const handleEmptyTrash = () => {
    const trashed = notes.filter(n => n.isTrash);
    trashed.forEach(note => {
      const fileName = note.filePath || noteToFileName(note);
      recordTombstone(fileName);
    });
    setNotes(prev => prev.filter(n => !n.isTrash));
    if (selectedNote?.isTrash) {
      setSelectedNoteId(null);
    }
  };

  const trashCount = notes.filter(n => n.isTrash).length;
  const wordCount = notes.reduce((sum, n) => sum + (n.content.trim().split(/\s+/).filter(Boolean).length || 0), 0);

  const handleExportNotes = async () => {
    const zip = new JSZip();
    const activeNotes = notes.filter(n => !n.isTrash);
    activeNotes.forEach(note => {
      const folder = (note.folder && note.folder.trim()) || 'Notes';
      const cleanTitle = (note.title || 'Untitled Note').replace(/[/\\?%*:|"<>]/g, '-').trim();
      const fileName = `${folder}/${cleanTitle || 'Untitled'}.md`;
      const fileContent = `# ${note.title || 'Untitled'}\n\n${note.content || ''}`;
      zip.file(fileName, fileContent);
    });

    const content = await zip.generateAsync({ type: 'blob' });
    const url = URL.createObjectURL(content);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Monolith_Vault_Backup_${new Date().toISOString().slice(0, 10)}.zip`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const [vaultPath, setVaultPath] = useState<string>(() => {
    return localStorage.getItem('monolith_vault_path') || '~/Documents/MonolithNotes';
  });

  const handleChangeVaultPath = () => {
    const newPath = window.prompt('Enter new local vault storage path:', vaultPath);
    if (newPath && newPath.trim()) {
      const trimmed = newPath.trim();
      setVaultPath(trimmed);
      localStorage.setItem('monolith_vault_path', trimmed);
    }
  };

  return (
    <div style={{
      width: '100vw',
      height: '100vh',
      display: 'flex',
      backgroundColor: 'var(--bg-primary)',
      overflow: 'hidden'
    }}>
      {/* 1. Full Screen Vault / Folders Page (Obsidian / Monolith Mobile Start Screen) */}
      {currentView === 'vault' ? (
        <VaultScreen
          notes={notes}
          folders={folders}
          trashCount={trashCount}
          selectedFolder={selectedFolder}
          isTrashSelected={isTrashSelected}
          onSelectFolder={(folder) => {
            setNotes(prev => prev.filter(n => !isNoteEmpty(n)));
            setSelectedFolder(folder);
            setIsTrashSelected(false);
            setCurrentView('workspace');
          }}
          onSelectTrash={() => {
            setNotes(prev => prev.filter(n => !isNoteEmpty(n)));
            setIsTrashSelected(true);
            setCurrentView('workspace');
          }}
          onSelectNote={(note) => {
            setNotes(prev => prev.filter(n => n.id === note.id || !isNoteEmpty(n)));
            setSelectedFolder(note.folder || 'Notes');
            setIsTrashSelected(Boolean(note.isTrash));
            setSelectedNoteId(note.id);
            setCurrentView('workspace');
          }}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onCreateFolder={handleCreateFolder}
          onNewNote={() => {
            handleNewNote();
            setCurrentView('workspace');
          }}
          isCloudSyncing={isCloudSyncing}
          isCloudLoggedIn={Boolean(cloudConfig.token && cloudConfig.email)}
          onTriggerCloudSync={handlePerformCloudSync}
          onOpenCloudModal={() => setIsCloudModalOpen(true)}
          copiedNote={copiedNote}
          onPasteNote={(targetFolder) => {
            handlePasteNote(targetFolder);
            setCurrentView('workspace');
          }}
        />
      ) : (
        /* 2. Workspace View: Notes List & Editor in VS Code floating pane cards with gap & rounded corners */
        <div style={{
          flex: 1,
          height: '100%',
          display: 'flex',
          padding: '4px',
          boxSizing: 'border-box',
          overflow: 'hidden',
          backgroundColor: 'var(--bg-gap)',
          userSelect: isResizing ? 'none' : 'auto'
        }}>
          {/* Notes List Card */}
          {isNoteListOpen && (
            <div style={{
              width: `${noteListWidth}px`,
              height: '100%',
              backgroundColor: 'var(--bg-primary)',
              borderRadius: '8px',
              border: '1px solid var(--divider)',
              display: 'flex',
              flexDirection: 'column',
              flexShrink: 0,
              overflow: 'hidden'
            }}>
              <NoteList
                notes={activeNotes}
                selectedNoteId={selectedNote?.id || null}
                currentFolder={isTrashSelected ? 'Deleted' : (selectedFolder || 'Library')}
                isTrash={isTrashSelected}
                searchQuery={searchQuery}
                selectedTag={selectedTag}
                settings={settings}
                isSidebarOpen={false}
                isNoteListOpen={isNoteListOpen}
                onToggleSidebar={handleNavigateToVault}
                onToggleNoteList={() => setIsNoteListOpen(!isNoteListOpen)}
                onBackToVault={handleNavigateToVault}
                onSelectNote={(note) => {
                  handleSelectNote(note.id);
                }}
                onNewNote={handleNewNote}
                onPinNote={handlePinNote}
                onDeleteNote={handleDeleteNote}
                onRestoreNote={handleRestoreNote}
                onEmptyTrash={handleEmptyTrash}
                onUpdateNote={handleUpdateNote}
                folders={folders}
                onSearchChange={setSearchQuery}
                onTagSelect={setSelectedTag}
                isCloudSyncing={isCloudSyncing}
                isCloudLoggedIn={Boolean(cloudConfig.token && cloudConfig.email)}
                onTriggerCloudSync={handlePerformCloudSync}
                onOpenCloudModal={() => setIsCloudModalOpen(true)}
                copiedNote={copiedNote}
                onCopyNote={handleCopyNote}
                onDuplicateNote={handleDuplicateNote}
                onPasteNote={handlePasteNote}
              />
            </div>
          )}

          {/* VS Code Draggable Sash with 3-Dots Resizer Handle */}
          {isNoteListOpen && (
            <div
              onMouseDown={(e) => {
                e.preventDefault();
                setIsResizing(true);
              }}
              title="Drag to resize panel"
              style={{
                width: '6px',
                height: '100%',
                cursor: 'col-resize',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                position: 'relative',
                zIndex: 10
              }}
            >
              {/* Three vertical dots handle (VS Code style) */}
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '2.5px',
                alignItems: 'center',
                justifyContent: 'center',
                opacity: isResizing ? 1 : 0.45,
                transition: 'opacity 0.15s ease'
              }}
              onMouseEnter={(e) => (e.currentTarget.style.opacity = '1')}
              onMouseLeave={(e) => { if (!isResizing) e.currentTarget.style.opacity = '0.45'; }}
              >
                <div style={{ width: '2.5px', height: '2.5px', borderRadius: '50%', backgroundColor: 'var(--text-secondary)' }} />
                <div style={{ width: '2.5px', height: '2.5px', borderRadius: '50%', backgroundColor: 'var(--text-secondary)' }} />
                <div style={{ width: '2.5px', height: '2.5px', borderRadius: '50%', backgroundColor: 'var(--text-secondary)' }} />
              </div>
            </div>
          )}

          {/* Active Editor Card */}
          <div style={{
            flex: 1,
            height: '100%',
            backgroundColor: 'var(--bg-primary)',
            borderRadius: '8px',
            border: '1px solid var(--divider)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden'
          }}>
            <Editor
              note={selectedNote}
              settings={settings}
              isTrash={isTrashSelected}
              hasNotes={activeNotes.length > 0}
              onNewNote={handleNewNote}
              isSidebarOpen={false}
              isNoteListOpen={isNoteListOpen}
              onToggleSidebar={handleNavigateToVault}
              onToggleNoteList={() => setIsNoteListOpen(!isNoteListOpen)}
              onBackToList={() => {
                setIsNoteListOpen(true);
              }}
              onUpdateNote={handleUpdateNote}
              onDeleteNote={handleDeleteNote}
              onRestoreNote={handleRestoreNote}
            />
          </div>
        </div>
      )}

      {/* Settings Modal - Exact replica of Android SettingsSheet */}
      <SettingsModal
        isOpen={isSettingsOpen}
        settings={settings}
        cloudConfig={cloudConfig}
        isCloudSyncing={isCloudSyncing}
        cloudSyncMessage={cloudSyncMessage}
        noteCount={notes.filter(n => !n.isTrash).length}
        wordCount={wordCount}
        trashCount={trashCount}
        vaultPath={vaultPath}
        onChangeVaultPath={handleChangeVaultPath}
        onClose={() => setIsSettingsOpen(false)}
        onUpdateSettings={setSettings}
        onExportNotes={handleExportNotes}
        onOpenTrash={() => {
          setIsTrashSelected(true);
          setCurrentView('workspace');
        }}
        onOpenCloudVault={() => setIsCloudModalOpen(true)}
        onPerformCloudSync={handlePerformCloudSync}
      />

      {/* Monolith Cloud Vault Modal */}
      <MonolithCloudModal
        isOpen={isCloudModalOpen}
        config={cloudConfig}
        isSyncing={isCloudSyncing}
        lastSyncMessage={cloudSyncMessage}
        onClose={() => setIsCloudModalOpen(false)}
        onSaveConfig={setCloudConfig}
        onTriggerSync={handlePerformCloudSync}
      />
    </div>
  );
};

export default App;
