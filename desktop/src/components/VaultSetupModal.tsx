import React, { useState, useEffect } from 'react';
import { 
  Folder, 
  FolderPlus, 
  FolderCheck, 
  X, 
  Check, 
  HardDrive,
  Sparkles
} from 'lucide-react';
import { 
  pickVaultFolder, 
  setVaultLocation, 
  createVault, 
  getDefaultVaultLocation 
} from '../services/vaultFileSystem';

interface VaultSetupModalProps {
  isOpen: boolean;
  onClose?: () => void;
  currentVaultPath: string | null;
  onVaultSelected: (path: string) => void;
  canDismiss?: boolean;
}

export const VaultSetupModal: React.FC<VaultSetupModalProps> = ({
  isOpen,
  onClose,
  currentVaultPath,
  onVaultSelected,
  canDismiss = true,
}) => {
  const [activeTab, setActiveTab] = useState<'open' | 'create'>('open');
  const [createParentPath, setCreateParentPath] = useState('');
  const [createVaultName, setCreateVaultName] = useState('MonolithNotes');
  const [defaultPath, setDefaultPath] = useState('~/Documents/MonolithNotes');
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    getDefaultVaultLocation().then((dp) => {
      setDefaultPath(dp);
      if (!createParentPath) {
        // Parent folder of default path
        const parent = dp.substring(0, dp.lastIndexOf('/') || dp.lastIndexOf('\\'));
        setCreateParentPath(parent || dp);
      }
    });
  }, []);

  if (!isOpen) return null;

  const handleOpenFolder = async () => {
    setErrorMessage(null);
    setIsProcessing(true);
    try {
      const picked = await pickVaultFolder();
      if (picked) {
        await setVaultLocation(picked);
        onVaultSelected(picked);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to open selected folder');
    } finally {
      setIsProcessing(false);
    }
  };

  const handlePickCreateParent = async () => {
    try {
      const picked = await pickVaultFolder();
      if (picked) {
        setCreateParentPath(picked);
      }
    } catch (err: any) {
      console.error(err);
    }
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createVaultName.trim() || !createParentPath.trim()) return;
    setErrorMessage(null);
    setIsProcessing(true);
    try {
      const created = await createVault(createParentPath.trim(), createVaultName.trim());
      onVaultSelected(created);
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to create vault folder');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleUseDefault = async () => {
    setErrorMessage(null);
    setIsProcessing(true);
    try {
      const path = await setVaultLocation(defaultPath);
      onVaultSelected(path);
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to initialize default vault');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 2000,
        backgroundColor: 'rgba(0, 0, 0, 0.75)',
        backdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px'
      }}
      onClick={() => {
        if (canDismiss && onClose) onClose();
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '560px',
          maxWidth: '100%',
          backgroundColor: 'var(--bg-card)',
          border: '1px solid var(--divider)',
          borderRadius: '16px',
          boxShadow: '0 24px 64px rgba(0, 0, 0, 0.85)',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {/* Top Header */}
        <div style={{
          padding: '24px 28px 16px 28px',
          borderBottom: '1px solid var(--divider)',
          position: 'relative'
        }}>
          {canDismiss && onClose && (
            <button
              onClick={onClose}
              style={{
                position: 'absolute',
                top: '20px',
                right: '20px',
                background: 'none',
                border: 'none',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                padding: '4px',
                borderRadius: '6px'
              }}
            >
              <X size={18} />
            </button>
          )}

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              backgroundColor: 'var(--accent-container)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--accent)'
            }}>
              <HardDrive size={18} />
            </div>
            <h2 style={{
              margin: 0,
              fontFamily: 'var(--font-display)',
              fontSize: '19px',
              fontWeight: 700,
              color: 'var(--text-primary)'
            }}>
              Choose Vault Location
            </h2>
          </div>

          <p style={{
            margin: 0,
            fontSize: '13.5px',
            color: 'var(--text-secondary)',
            lineHeight: '20px'
          }}>
            Monolith stores notes as local Markdown files on your computer. Select a folder to use as your vault or connect an existing Obsidian vault.
          </p>

          {currentVaultPath && (
            <div style={{
              marginTop: '12px',
              padding: '6px 12px',
              borderRadius: '8px',
              backgroundColor: 'var(--bg-elevated)',
              fontSize: '12px',
              color: 'var(--text-secondary)',
              display: 'flex',
              alignItems: 'center',
              gap: '6px'
            }}>
              <Check size={13} color="var(--accent)" />
              <span>Current Vault: </span>
              <span style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}>
                {currentVaultPath}
              </span>
            </div>
          )}
        </div>

        {/* Tab Switcher */}
        <div style={{
          display: 'flex',
          borderBottom: '1px solid var(--divider)',
          backgroundColor: 'var(--bg-primary)'
        }}>
          <button
            onClick={() => setActiveTab('open')}
            style={{
              flex: 1,
              padding: '12px',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'open' ? '2px solid var(--accent)' : '2px solid transparent',
              color: activeTab === 'open' ? 'var(--accent)' : 'var(--text-secondary)',
              fontWeight: activeTab === 'open' ? 600 : 400,
              cursor: 'pointer',
              fontSize: '13.5px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px'
            }}
          >
            <Folder size={15} />
            <span>Open Existing Folder</span>
          </button>
          <button
            onClick={() => setActiveTab('create')}
            style={{
              flex: 1,
              padding: '12px',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'create' ? '2px solid var(--accent)' : '2px solid transparent',
              color: activeTab === 'create' ? 'var(--accent)' : 'var(--text-secondary)',
              fontWeight: activeTab === 'create' ? 600 : 400,
              cursor: 'pointer',
              fontSize: '13.5px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px'
            }}
          >
            <FolderPlus size={15} />
            <span>Create New Vault</span>
          </button>
        </div>

        {/* Body Content */}
        <div style={{ padding: '24px 28px' }}>
          {errorMessage && (
            <div style={{
              marginBottom: '16px',
              padding: '10px 14px',
              borderRadius: '8px',
              backgroundColor: 'rgba(255, 69, 58, 0.12)',
              border: '1px solid rgba(255, 69, 58, 0.25)',
              color: 'var(--danger)',
              fontSize: '13px'
            }}>
              {errorMessage}
            </div>
          )}

          {activeTab === 'open' ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{
                padding: '16px',
                borderRadius: '12px',
                border: '1px solid var(--divider)',
                backgroundColor: 'var(--bg-elevated)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '16px'
              }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: '14.5px', color: 'var(--text-primary)', marginBottom: '4px' }}>
                    Open Folder as Vault
                  </div>
                  <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: '18px' }}>
                    Choose any existing folder on your computer. All Markdown files and folders will load immediately.
                  </div>
                </div>
                <button
                  disabled={isProcessing}
                  onClick={handleOpenFolder}
                  style={{
                    flexShrink: 0,
                    padding: '9px 18px',
                    borderRadius: '8px',
                    backgroundColor: 'var(--accent)',
                    color: '#000000',
                    fontWeight: 600,
                    border: 'none',
                    cursor: isProcessing ? 'default' : 'pointer',
                    fontSize: '13.5px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                >
                  <FolderCheck size={16} />
                  <span>Choose Folder...</span>
                </button>
              </div>

              {/* Quick Start Option */}
              <div style={{
                padding: '16px',
                borderRadius: '12px',
                border: '1px dashed var(--divider)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: '16px'
              }}>
                <div>
                  <div style={{ fontWeight: 500, fontSize: '13.5px', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Sparkles size={14} color="var(--accent)" />
                    <span>Quick Start (Default Vault)</span>
                  </div>
                  <div style={{ fontSize: '12.5px', color: 'var(--text-secondary)', fontFamily: 'var(--font-mono)', marginTop: '2px' }}>
                    {defaultPath}
                  </div>
                </div>
                <button
                  disabled={isProcessing}
                  onClick={handleUseDefault}
                  style={{
                    flexShrink: 0,
                    padding: '8px 16px',
                    borderRadius: '7px',
                    backgroundColor: 'var(--bg-elevated)',
                    border: '1px solid var(--divider)',
                    color: 'var(--text-primary)',
                    fontWeight: 500,
                    cursor: isProcessing ? 'default' : 'pointer',
                    fontSize: '13px'
                  }}
                >
                  Use Default
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleCreateSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12.5px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                  VAULT NAME
                </label>
                <input
                  type="text"
                  value={createVaultName}
                  onChange={(e) => setCreateVaultName(e.target.value)}
                  placeholder="My Notes"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '9px 12px',
                    borderRadius: '8px',
                    border: '1px solid var(--divider)',
                    backgroundColor: 'var(--bg-elevated)',
                    color: 'var(--text-primary)',
                    fontSize: '14px',
                    outline: 'none'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12.5px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                  LOCATION
                </label>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <input
                    type="text"
                    value={createParentPath}
                    onChange={(e) => setCreateParentPath(e.target.value)}
                    placeholder="/path/to/folder"
                    style={{
                      flex: 1,
                      padding: '9px 12px',
                      borderRadius: '8px',
                      border: '1px solid var(--divider)',
                      backgroundColor: 'var(--bg-elevated)',
                      color: 'var(--text-primary)',
                      fontSize: '13.5px',
                      fontFamily: 'var(--font-mono)',
                      outline: 'none'
                    }}
                  />
                  <button
                    type="button"
                    onClick={handlePickCreateParent}
                    style={{
                      padding: '9px 14px',
                      borderRadius: '8px',
                      border: '1px solid var(--divider)',
                      backgroundColor: 'var(--bg-elevated)',
                      color: 'var(--text-primary)',
                      fontSize: '13px',
                      cursor: 'pointer'
                    }}
                  >
                    Browse...
                  </button>
                </div>
              </div>

              <div style={{
                fontSize: '12.5px',
                color: 'var(--text-tertiary)',
                padding: '8px 12px',
                backgroundColor: 'rgba(255, 255, 255, 0.02)',
                borderRadius: '8px'
              }}>
                A new folder named <strong style={{ color: 'var(--accent)' }}>{createVaultName || 'Untitled'}</strong> will be created inside <span style={{ fontFamily: 'var(--font-mono)' }}>{createParentPath || '...'}</span>.
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '6px' }}>
                <button
                  type="submit"
                  disabled={isProcessing || !createVaultName.trim() || !createParentPath.trim()}
                  style={{
                    padding: '9px 22px',
                    borderRadius: '8px',
                    backgroundColor: 'var(--accent)',
                    color: '#000000',
                    fontWeight: 600,
                    border: 'none',
                    cursor: isProcessing || !createVaultName.trim() ? 'default' : 'pointer',
                    fontSize: '13.5px',
                    opacity: createVaultName.trim() && createParentPath.trim() ? 1 : 0.5,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                >
                  <FolderPlus size={16} />
                  <span>Create Vault</span>
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
