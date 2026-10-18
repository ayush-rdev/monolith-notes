import React, { useState } from 'react';
import { 
  FolderCheck, 
  Folder, 
  Plus,
  Trash2, 
  Settings, 
  ChevronRight, 
  Search,
  Cloud,
  RefreshCw,
  FileText,
  X,
  ClipboardPaste,
  HardDrive
} from 'lucide-react';
import type { Note } from '../types';
import { getDisplayTitle, getPreviewSnippet } from '../utils';

interface VaultScreenProps {
  notes: Note[];
  folders: string[];
  trashCount: number;
  selectedFolder: string | null;
  isTrashSelected: boolean;
  onSelectFolder: (folder: string | null) => void;
  onSelectTrash: () => void;
  onSelectNote?: (note: Note) => void;
  onOpenSettings: () => void;
  onCreateFolder: (name: string) => void;
  onDeleteFolder?: (name: string) => void;
  onNewNote: () => void;
  isCloudSyncing?: boolean;
  isCloudLoggedIn?: boolean;
  onTriggerCloudSync?: () => void;
  onOpenCloudModal?: () => void;
  copiedNote?: Note | null;
  onPasteNote?: (targetFolder?: string) => void;
  vaultPath?: string | null;
  onOpenVaultSetup?: () => void;
}

export const VaultScreen: React.FC<VaultScreenProps> = ({
  notes,
  folders,
  trashCount,
  onSelectFolder,
  onSelectTrash,
  onSelectNote,
  onOpenSettings,
  onCreateFolder,
  onNewNote: _onNewNote,
  isCloudSyncing,
  isCloudLoggedIn,
  onTriggerCloudSync,
  onOpenCloudModal,
  copiedNote,
  onPasteNote,
  vaultPath,
  onOpenVaultSetup
}) => {
  const [isCreatingFolder, setIsCreatingFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [folderContextMenu, setFolderContextMenu] = useState<{ folder: string; x: number; y: number } | null>(null);

  const isSearching = searchQuery.trim().length > 0;
  const q = searchQuery.toLowerCase().trim();

  const totalNotesCount = notes.filter(n => !n.isTrash).length;
  const rootNotesCount = notes.filter(n => !n.isTrash && (n.folder === 'Notes' || !n.folder)).length;

  const customFolders = folders.filter(f => f !== 'Notes').sort();
  const filteredCustomFolders = isSearching
    ? customFolders.filter(f => f.toLowerCase().includes(q))
    : customFolders;

  const isLibraryMatch = !isSearching || 'library'.includes(q);
  const isNotesMatch = !isSearching || 'notes'.includes(q);
  const isDeletedMatch = !isSearching || 'deleted'.includes(q) || 'trash'.includes(q);

  const matchingNotes = isSearching
    ? notes.filter(n => {
        if (n.isTrash) return false;
        const title = (n.title || '').toLowerCase();
        const content = (n.content || '').toLowerCase();
        const tags = (n.tags || []).join(' ').toLowerCase();
        return title.includes(q) || content.includes(q) || tags.includes(q);
      })
    : [];

  const hasFolderMatches = isLibraryMatch || isNotesMatch || filteredCustomFolders.length > 0;
  const hasAnyMatches = hasFolderMatches || matchingNotes.length > 0 || (isSearching && isDeletedMatch);

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (newFolderName.trim()) {
      onCreateFolder(newFolderName.trim());
      setNewFolderName('');
      setIsCreatingFolder(false);
    }
  };

  return (
    <div style={{
      width: '100vw',
      height: '100vh',
      backgroundColor: 'var(--bg-primary)',
      display: 'flex',
      flexDirection: 'column',
      userSelect: 'none'
    }}>
      {/* 1. Top Navigation Bar */}
      <div style={{
        height: '52px',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderBottom: '0.5px solid var(--divider)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span style={{
            fontFamily: 'var(--font-brand)',
            fontWeight: 800,
            fontSize: '20px',
            letterSpacing: '-0.3px',
            color: 'var(--text-primary)'
          }}>
            M<span style={{ position: 'relative', display: 'inline-block' }}>
              onolith
              <svg
                viewBox="0 0 70 8"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                style={{
                  position: 'absolute',
                  left: '-2px',
                  bottom: '-2px',
                  width: 'calc(100% + 4px)',
                  height: '8px',
                  overflow: 'visible',
                  pointerEvents: 'none'
                }}
              >
                {/* 1st Stroke: undulating hand-drawn pen pass */}
                <path
                  d="M 2 5.2 C 18 2.2, 42 6.8, 69 3.2"
                  stroke="var(--accent)"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                {/* 2nd Stroke: counter-curving crossing sketch stroke */}
                <path
                  d="M 3 2.6 C 22 6.4, 48 1.8, 67 4.8"
                  stroke="var(--accent)"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  opacity="0.88"
                />
                {/* 3rd Stroke: subtle middle reinforcing sketch flick */}
                <path
                  d="M 8 4.2 C 26 3.0, 44 5.2, 60 3.8"
                  stroke="var(--accent)"
                  strokeWidth="1.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  opacity="0.55"
                />
              </svg>
            </span>
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>

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
              position: 'relative'
            }}
          >
            {isCloudSyncing ? (
              <RefreshCw size={19} style={{ animation: 'spin 1s linear infinite' }} />
            ) : (
              <Cloud size={20} />
            )}
            {isCloudLoggedIn && !isCloudSyncing && (
              <div style={{
                position: 'absolute',
                bottom: '4px',
                right: '4px',
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                backgroundColor: '#10B981',
                boxShadow: '0 0 6px #10B981'
              }} />
            )}
          </button>

          {/* Vault Switcher Button in Header */}
          {onOpenVaultSetup && (
            <button
              onClick={onOpenVaultSetup}
              title={vaultPath ? `Vault: ${vaultPath}\nClick to switch folder` : 'Choose Vault Location'}
              style={{
                background: 'none',
                border: '0.5px solid var(--divider)',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                padding: '5px 11px',
                borderRadius: '8px',
                display: 'flex',
                alignItems: 'center',
                gap: '7px',
                fontSize: '12.5px',
                backgroundColor: 'var(--bg-elevated)',
                maxWidth: '220px'
              }}
              onMouseEnter={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
              onMouseLeave={(e) => e.currentTarget.style.borderColor = 'var(--divider)'}
            >
              <HardDrive size={14} color="var(--accent)" />
              <span style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                fontWeight: 500,
                color: 'var(--text-primary)'
              }}>
                {vaultPath ? (vaultPath.split('/').pop() || vaultPath.split('\\').pop() || 'Vault') : 'Select Vault'}
              </span>
            </button>
          )}

          <button
            onClick={onOpenSettings}
            title="Settings"
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--accent)',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '6px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Settings size={20} />
          </button>
        </div>
      </div>

      {/* 2. Scrollable Body Area */}
      <div style={{
        flex: 1,
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        padding: '32px 24px'
      }}>
        <div style={{ width: '100%', maxWidth: '640px' }}>
          {/* Vault Path Badge */}
          {vaultPath && (
            <div
              onClick={onOpenVaultSetup}
              title="Click to change vault location"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '5px 12px',
                borderRadius: '20px',
                backgroundColor: 'var(--bg-elevated)',
                border: '0.5px solid var(--divider)',
                fontSize: '12px',
                color: 'var(--text-secondary)',
                marginBottom: '12px',
                cursor: 'pointer',
                transition: 'border-color 0.15s ease'
              }}
              onMouseEnter={(e) => e.currentTarget.style.borderColor = 'var(--accent)'}
              onMouseLeave={(e) => e.currentTarget.style.borderColor = 'var(--divider)'}
            >
              <HardDrive size={13} color="var(--accent)" />
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: '11.5px', color: 'var(--text-primary)' }}>
                {vaultPath}
              </span>
              <span style={{ color: 'var(--accent)', fontWeight: 600, fontSize: '11px', textDecoration: 'underline' }}>
                Change
              </span>
            </div>
          )}

          {/* Large Title: "Folders" */}
          <h1 style={{
            fontFamily: 'var(--font-display)',
            fontSize: '34px',
            fontWeight: 700,
            letterSpacing: '-0.5px',
            color: '#FFFFFF',
            margin: '0 0 16px 4px'
          }}>
            Folders
          </h1>

          {/* Search Pill with New Folder Button */}
          <div style={{
            height: '40px',
            backgroundColor: 'var(--bg-search)',
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            padding: '0 8px 0 12px',
            gap: '8px',
            border: '0.5px solid var(--divider)',
            marginBottom: '28px'
          }}>
            <Search size={16} color="var(--text-tertiary)" style={{ flexShrink: 0 }} />
            <input
              type="text"
              placeholder="Search folders and notes"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Escape') setSearchQuery('');
              }}
              style={{
                flex: 1,
                background: 'none',
                border: 'none',
                outline: 'none',
                color: 'var(--text-primary)',
                fontFamily: 'var(--font-text)',
                fontSize: '15px'
              }}
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                title="Clear search"
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-tertiary)',
                  cursor: 'pointer',
                  padding: '2px 4px',
                  display: 'flex',
                  alignItems: 'center',
                  borderRadius: '4px'
                }}
              >
                <X size={15} />
              </button>
            )}
            <div style={{ width: '1px', height: '18px', backgroundColor: 'var(--divider)', margin: '0 4px' }} />
            <button
              onClick={() => setIsCreatingFolder(true)}
              title="Create New Folder"
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--accent)',
                cursor: 'pointer',
                padding: '5px 8px',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                fontFamily: 'var(--font-text)',
                fontSize: '13px',
                fontWeight: 600,
                flexShrink: 0,
                transition: 'background 0.15s ease'
              }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Plus size={16} />
              <span>New</span>
            </button>
          </div>

          {/* Empty Search Results */}
          {isSearching && !hasAnyMatches && (
            <div style={{
              padding: '60px 16px',
              textAlign: 'center',
              color: 'var(--text-tertiary)',
              fontFamily: 'var(--font-text)'
            }}>
              <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                No results found
              </div>
              <div style={{ fontSize: '13px' }}>
                No folders or notes matching "{searchQuery}"
              </div>
            </div>
          )}

          {/* Group 1: VAULT (Folders) */}
          {hasFolderMatches && (
            <div style={{ marginBottom: '28px' }}>
              <div style={{
                fontFamily: 'var(--font-text)',
                fontSize: '12px',
                fontWeight: 700,
                color: 'var(--text-tertiary)',
                letterSpacing: '0.6px',
                textTransform: 'uppercase',
                margin: '0 0 8px 12px'
              }}>
                VAULT
              </div>

              <div style={{
                backgroundColor: 'var(--bg-card)',
                borderRadius: '14px',
                overflow: 'hidden',
                border: '0.5px solid var(--divider)'
              }}>
                {/* Row: Library */}
                {isLibraryMatch && (
                  <div
                    onClick={() => onSelectFolder(null)}
                    onContextMenu={(e) => {
                      e.preventDefault();
                      setFolderContextMenu({
                        folder: 'Notes',
                        x: Math.min(e.clientX, window.innerWidth - 190),
                        y: Math.min(e.clientY, window.innerHeight - 150)
                      });
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '13px 16px',
                      cursor: 'pointer',
                      transition: 'background 0.12s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                      <FolderCheck size={21} color="var(--accent)" />
                      <span style={{
                        fontFamily: 'var(--font-text)',
                        fontSize: '16px',
                        fontWeight: 500,
                        color: 'var(--text-primary)'
                      }}>
                        Library
                      </span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontSize: '15px', color: 'var(--text-secondary)' }}>
                        {totalNotesCount}
                      </span>
                      <ChevronRight size={17} color="var(--text-tertiary)" />
                    </div>
                  </div>
                )}

                {isLibraryMatch && isNotesMatch && (
                  <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '50px' }} />
                )}

                {/* Row: Notes (Default Vault Root) */}
                {isNotesMatch && (
                  <div
                    onClick={() => onSelectFolder('Notes')}
                    onContextMenu={(e) => {
                      e.preventDefault();
                      setFolderContextMenu({
                        folder: 'Notes',
                        x: Math.min(e.clientX, window.innerWidth - 190),
                        y: Math.min(e.clientY, window.innerHeight - 150)
                      });
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '13px 16px',
                      cursor: 'pointer',
                      transition: 'background 0.12s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                      <Folder size={21} color="var(--accent)" />
                      <span style={{
                        fontFamily: 'var(--font-text)',
                        fontSize: '16px',
                        fontWeight: 500,
                        color: 'var(--text-primary)'
                      }}>
                        Notes
                      </span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontSize: '15px', color: 'var(--text-secondary)' }}>
                        {rootNotesCount}
                      </span>
                      <ChevronRight size={17} color="var(--text-tertiary)" />
                    </div>
                  </div>
                )}

                {/* Custom Folders */}
                {filteredCustomFolders.map((folder, index) => {
                  const count = notes.filter(n => !n.isTrash && n.folder === folder).length;
                  const showDivider = isLibraryMatch || isNotesMatch || index > 0;
                  return (
                    <React.Fragment key={folder}>
                      {showDivider && (
                        <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '50px' }} />
                      )}
                      <div
                        onClick={() => onSelectFolder(folder)}
                        onContextMenu={(e) => {
                          e.preventDefault();
                          setFolderContextMenu({
                            folder,
                            x: Math.min(e.clientX, window.innerWidth - 190),
                            y: Math.min(e.clientY, window.innerHeight - 150)
                          });
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '13px 16px',
                          cursor: 'pointer',
                          transition: 'background 0.12s ease'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                          <Folder size={21} color="var(--accent)" />
                          <span style={{
                            fontFamily: 'var(--font-text)',
                            fontSize: '16px',
                            fontWeight: 500,
                            color: 'var(--text-primary)'
                          }}>
                            {folder}
                          </span>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '15px', color: 'var(--text-secondary)' }}>
                            {count}
                          </span>
                          <ChevronRight size={17} color="var(--text-tertiary)" />
                        </div>
                      </div>
                    </React.Fragment>
                  );
                })}
              </div>

              {/* Inline New Folder Creator (Above Safety Net) */}
              {isCreatingFolder && (
                <form onSubmit={handleCreateSubmit} style={{ marginTop: '12px' }}>
                  <div style={{
                    backgroundColor: 'var(--bg-card)',
                    borderRadius: '12px',
                    padding: '10px 14px',
                    border: '1.5px solid var(--accent)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}>
                    <Folder size={20} color="var(--accent)" />
                    <input
                      type="text"
                      autoFocus
                      placeholder="New Folder Name"
                      value={newFolderName}
                      onChange={(e) => setNewFolderName(e.target.value)}
                      style={{
                        flex: 1,
                        background: 'none',
                        border: 'none',
                        outline: 'none',
                        color: 'var(--text-primary)',
                        fontFamily: 'var(--font-text)',
                        fontSize: '15px'
                      }}
                      onBlur={() => {
                        if (!newFolderName.trim()) setIsCreatingFolder(false);
                      }}
                    />
                    <button
                      type="submit"
                      style={{
                        background: 'var(--accent)',
                        border: 'none',
                        color: '#000000',
                        fontWeight: 700,
                        borderRadius: '6px',
                        padding: '4px 10px',
                        cursor: 'pointer',
                        fontSize: '13px'
                      }}
                    >
                      Create
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}

          {/* Group 2: NOTES (Files) */}
          {matchingNotes.length > 0 && (
            <div style={{ marginBottom: '28px' }}>
              <div style={{
                fontFamily: 'var(--font-text)',
                fontSize: '12px',
                fontWeight: 700,
                color: 'var(--text-tertiary)',
                letterSpacing: '0.6px',
                textTransform: 'uppercase',
                margin: '0 0 8px 12px'
              }}>
                NOTES ({matchingNotes.length})
              </div>

              <div style={{
                backgroundColor: 'var(--bg-card)',
                borderRadius: '14px',
                overflow: 'hidden',
                border: '0.5px solid var(--divider)'
              }}>
                {matchingNotes.map((note, index) => {
                  const snippet = getPreviewSnippet(note.content);
                  return (
                    <React.Fragment key={note.id}>
                      {index > 0 && (
                        <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '50px' }} />
                      )}
                      <div
                        onClick={() => onSelectNote?.(note)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '12px 16px',
                          cursor: 'pointer',
                          transition: 'background 0.12s ease'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '14px', minWidth: 0, flex: 1, marginRight: '12px' }}>
                          <FileText size={20} color="var(--accent)" style={{ flexShrink: 0 }} />
                          <div style={{ minWidth: 0, flex: 1 }}>
                            <div style={{
                              fontFamily: 'var(--font-text)',
                              fontSize: '15px',
                              fontWeight: 500,
                              color: 'var(--text-primary)',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap'
                            }}>
                              {getDisplayTitle(note)}
                            </div>
                            {snippet && (
                              <div style={{
                                fontFamily: 'var(--font-text)',
                                fontSize: '12px',
                                color: 'var(--text-tertiary)',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                whiteSpace: 'nowrap',
                                marginTop: '2px'
                              }}>
                                {snippet}
                              </div>
                            )}
                          </div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                          <span style={{
                            fontSize: '12px',
                            color: 'var(--text-secondary)',
                            backgroundColor: 'var(--bg-elevated)',
                            padding: '2px 8px',
                            borderRadius: '6px',
                            fontFamily: 'var(--font-text)'
                          }}>
                            {note.folder || 'Notes'}
                          </span>
                          <ChevronRight size={17} color="var(--text-tertiary)" />
                        </div>
                      </div>
                    </React.Fragment>
                  );
                })}
              </div>
            </div>
          )}

          {/* Group 3: TRASH */}
          {isDeletedMatch && (
            <div>
              <div style={{
                fontFamily: 'var(--font-text)',
                fontSize: '12px',
                fontWeight: 700,
                color: 'var(--text-tertiary)',
                letterSpacing: '0.6px',
                textTransform: 'uppercase',
                margin: '0 0 8px 12px'
              }}>
                TRASH
              </div>

              <div style={{
                backgroundColor: 'var(--bg-card)',
                borderRadius: '14px',
                overflow: 'hidden',
                border: '0.5px solid var(--divider)'
              }}>
                <div
                  onClick={onSelectTrash}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '13px 16px',
                    cursor: 'pointer',
                    transition: 'background 0.12s ease'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                    <Trash2 size={21} color="var(--accent)" />
                    <span style={{
                      fontFamily: 'var(--font-text)',
                      fontSize: '16px',
                      fontWeight: 500,
                      color: 'var(--text-primary)'
                    }}>
                      Deleted
                    </span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '15px', color: 'var(--text-secondary)' }}>
                      {trashCount}
                    </span>
                    <ChevronRight size={17} color="var(--text-tertiary)" />
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Folder Context Menu (Right Click on folder to Paste Note Here) */}
      {folderContextMenu && (
        <>
          <div
            onClick={() => setFolderContextMenu(null)}
            onContextMenu={(e) => {
              e.preventDefault();
              setFolderContextMenu(null);
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
              left: `${Math.max(12, folderContextMenu.x)}px`,
              top: `${Math.max(12, folderContextMenu.y)}px`,
              zIndex: 999,
              minWidth: '180px',
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
                  const target = folderContextMenu.folder;
                  setFolderContextMenu(null);
                  onPasteNote?.(target);
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '7px 10px',
                  borderRadius: '6px',
                  border: 'none',
                  backgroundColor: 'transparent',
                  color: 'var(--accent)',
                  cursor: 'pointer',
                  fontSize: '13px',
                  fontWeight: 500
                }}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                <ClipboardPaste size={14} color="var(--accent)" />
                <span>Paste Note Here</span>
              </button>
            )}
            <button
              onClick={() => {
                const target = folderContextMenu.folder;
                setFolderContextMenu(null);
                onSelectFolder(target);
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '7px 10px',
                borderRadius: '6px',
                border: 'none',
                backgroundColor: 'transparent',
                color: 'var(--text-primary)',
                cursor: 'pointer',
                fontSize: '13px'
              }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Folder size={14} color="var(--text-secondary)" />
              <span>Open Folder</span>
            </button>
          </div>
        </>
      )}
    </div>
  );
};
