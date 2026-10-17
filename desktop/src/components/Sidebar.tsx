import React from 'react';
import { 
  Folder, 
  FolderCheck, 
  Trash2, 
  Plus, 
  Settings 
} from 'lucide-react';
import type { Note } from '../types';

interface SidebarProps {
  notes: Note[];
  folders: string[];
  selectedFolder: string | null; // null for All Notes
  trashCount: number;
  isTrashSelected: boolean;
  onSelectFolder: (folder: string | null) => void;
  onSelectTrash: () => void;
  onOpenSettings: () => void;
  onCreateFolder: (name: string) => void;
  activeNoteCount: number;
}

export const Sidebar: React.FC<SidebarProps> = ({
  notes,
  folders,
  selectedFolder,
  trashCount,
  isTrashSelected,
  onSelectFolder,
  onSelectTrash,
  onOpenSettings,
  onCreateFolder,
}) => {
  const [isCreatingFolder, setIsCreatingFolder] = React.useState(false);
  const [newFolderName, setNewFolderName] = React.useState('');

  const totalNotesCount = notes.filter(n => !n.isTrash).length;
  const rootNotesCount = notes.filter(n => !n.isTrash && (n.folder === 'Notes' || !n.folder)).length;

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (newFolderName.trim()) {
      onCreateFolder(newFolderName.trim());
      setNewFolderName('');
      setIsCreatingFolder(false);
    }
  };

  return (
    <aside style={{
      width: '260px',
      height: '100%',
      backgroundColor: 'var(--bg-primary)',
      borderRight: '0.5px solid var(--divider)',
      display: 'flex',
      flexDirection: 'column',
      flexShrink: 0,
      userSelect: 'none'
    }}>
      {/* Top Header */}
      <div style={{
        height: '52px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 16px',
        borderBottom: '0.5px solid var(--divider)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{
            width: '12px',
            height: '12px',
            borderRadius: '50%',
            backgroundColor: '#FF5F56',
            boxShadow: '16px 0 0 #FFBD2E, 32px 0 0 #27C93F'
          }} />
          <span style={{ 
            fontFamily: 'var(--font-display)', 
            fontWeight: 700, 
            fontSize: '17px',
            letterSpacing: '-0.3px',
            marginLeft: '42px',
            color: 'var(--text-primary)'
          }}>
            Monolith
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
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
              justifyContent: 'center',
              transition: 'background 0.15s ease'
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg-elevated)'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <Settings size={18} />
          </button>
        </div>
      </div>

      {/* Vault & Folder List */}
      <div style={{ 
        flex: 1, 
        overflowY: 'auto', 
        padding: '16px 12px',
        display: 'flex',
        flexDirection: 'column',
        gap: '20px'
      }}>
        {/* Section 1: Vault */}
        <div>
          <div style={{
            fontFamily: 'var(--font-text)',
            fontSize: '11px',
            fontWeight: 700,
            color: 'var(--text-tertiary)',
            letterSpacing: '0.5px',
            padding: '0 8px 8px 8px',
            textTransform: 'uppercase'
          }}>
            Vault
          </div>

          <div style={{
            backgroundColor: 'var(--bg-card)',
            borderRadius: '12px',
            overflow: 'hidden',
            border: '0.5px solid var(--divider)'
          }}>
            {/* Library */}
            <div
              onClick={() => { onSelectFolder(null); }}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 12px',
                cursor: 'pointer',
                backgroundColor: (!isTrashSelected && selectedFolder === null) ? 'var(--bg-elevated)' : 'transparent',
                transition: 'background 0.12s ease'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <FolderCheck size={18} color="var(--accent)" />
                <span style={{ 
                  fontFamily: 'var(--font-text)', 
                  fontWeight: (!isTrashSelected && selectedFolder === null) ? 600 : 400,
                  fontSize: '14px',
                  color: 'var(--text-primary)' 
                }}>
                  Library
                </span>
              </div>
              <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                {totalNotesCount}
              </span>
            </div>

            <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '40px' }} />

            {/* Root "Notes" Folder */}
            <div
              onClick={() => { onSelectFolder('Notes'); }}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 12px',
                cursor: 'pointer',
                backgroundColor: (!isTrashSelected && selectedFolder === 'Notes') ? 'var(--bg-elevated)' : 'transparent',
                transition: 'background 0.12s ease'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Folder size={18} color="var(--accent)" />
                <span style={{ 
                  fontFamily: 'var(--font-text)', 
                  fontWeight: (!isTrashSelected && selectedFolder === 'Notes') ? 600 : 400,
                  fontSize: '14px',
                  color: 'var(--text-primary)' 
                }}>
                  Notes
                </span>
              </div>
              <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                {rootNotesCount}
              </span>
            </div>

            {/* Custom Folders */}
            {folders.filter(f => f !== 'Notes').map((folder) => {
              const count = notes.filter(n => !n.isTrash && n.folder === folder).length;
              const isSelected = !isTrashSelected && selectedFolder === folder;
              return (
                <React.Fragment key={folder}>
                  <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '40px' }} />
                  <div
                    onClick={() => onSelectFolder(folder)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '10px 12px',
                      cursor: 'pointer',
                      backgroundColor: isSelected ? 'var(--bg-elevated)' : 'transparent',
                      transition: 'background 0.12s ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <Folder size={18} color="var(--accent)" />
                      <span style={{ 
                        fontFamily: 'var(--font-text)', 
                        fontWeight: isSelected ? 600 : 400,
                        fontSize: '14px',
                        color: 'var(--text-primary)' 
                      }}>
                        {folder}
                      </span>
                    </div>
                    <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                      {count}
                    </span>
                  </div>
                </React.Fragment>
              );
            })}
          </div>
        </div>

        {/* Section 2: Trash */}
        <div>
          <div style={{
            fontFamily: 'var(--font-text)',
            fontSize: '11px',
            fontWeight: 700,
            color: 'var(--text-tertiary)',
            letterSpacing: '0.5px',
            padding: '0 8px 8px 8px',
            textTransform: 'uppercase'
          }}>
            Trash
          </div>

          <div style={{
            backgroundColor: 'var(--bg-card)',
            borderRadius: '12px',
            overflow: 'hidden',
            border: '0.5px solid var(--divider)'
          }}>
            <div
              onClick={onSelectTrash}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 12px',
                cursor: 'pointer',
                backgroundColor: isTrashSelected ? 'var(--bg-elevated)' : 'transparent',
                transition: 'background 0.12s ease'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Trash2 size={18} color="var(--accent)" />
                <span style={{ 
                  fontFamily: 'var(--font-text)', 
                  fontWeight: isTrashSelected ? 600 : 400,
                  fontSize: '14px',
                  color: 'var(--text-primary)' 
                }}>
                  Deleted
                </span>
              </div>
              <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                {trashCount}
              </span>
            </div>
          </div>
        </div>

        {/* New Folder input modal / inline */}
        {isCreatingFolder && (
          <form onSubmit={handleCreateSubmit} style={{ marginTop: '8px' }}>
            <input
              type="text"
              autoFocus
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              placeholder="Folder name..."
              style={{
                width: '100%',
                padding: '8px 12px',
                borderRadius: '8px',
                backgroundColor: 'var(--bg-card)',
                border: '1px solid var(--accent)',
                color: 'var(--text-primary)',
                outline: 'none',
                fontFamily: 'var(--font-text)',
                fontSize: '13px'
              }}
              onBlur={() => {
                if (!newFolderName.trim()) setIsCreatingFolder(false);
              }}
            />
          </form>
        )}
      </div>

      {/* Bottom Bar */}
      <div style={{
        height: '48px',
        padding: '0 16px',
        borderTop: '0.5px solid var(--divider)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <button
          onClick={() => setIsCreatingFolder(true)}
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--accent)',
            fontFamily: 'var(--font-text)',
            fontSize: '14px',
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            cursor: 'pointer'
          }}
        >
          <Plus size={16} />
          New Folder
        </button>
      </div>
    </aside>
  );
};
