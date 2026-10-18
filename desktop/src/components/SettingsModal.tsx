import React, { useState, useEffect } from 'react';
import { 
  Type, 
  CheckSquare, 
  Folder, 
  Download, 
  HardDrive, 
  FileText, 
  Sliders, 
  Vibrate, 
  ArrowUpDown, 
  User, 
  Check, 
  ChevronRight, 
  ChevronDown,
  Cloud,
  Palette,
  Info,
  X
} from 'lucide-react';
import { THEMES } from '../types';
import type { AppSettings, ThemeId, EditorFontSize, MonolithCloudConfig } from '../types';

interface GithubRelease {
  tag_name: string;
  name: string;
  published_at: string;
  html_url: string;
  body?: string;
}

const INITIAL_RELEASES: GithubRelease[] = [
  {
    tag_name: 'v2.2.1',
    name: 'Monolith Notes v2.2.1 - iOS Hierarchical Navigation Architecture',
    published_at: '2026-10-17T12:53:43Z',
    html_url: 'https://github.com/ayush-rdev/monolith-notes/releases/tag/v2.2.1',
    body: `### What's Changed in v2.2.1

#### 🏛️ Authentic iOS Hierarchical Navigation Architecture
- **Root Screen (Level 0)**: Monolith Notes opens cleanly at Folders as the true root. Top bar features only Edit / Done on the right.
- **Stream Clarity**: Renamed 'All Monolith' to 'All Notes' across folder list and notes list headers.
- **Strict Folder Management Purity**: Note creation lives strictly inside active note stream via compose pencil.
- **Dynamic Context Back Buttons**: Notes list displays '< Folders', Deleted displays '< Folders', Note Editor displays dynamic parent folder '< [Folder]'.
- **Hardware & Gesture Back Navigation**: Hardware back returns directly to Folders root.`
  },
  {
    tag_name: 'v2.2.0',
    name: 'Monolith Notes v2.2.0 - Borderless Luxury Solid Surfaces',
    published_at: '2026-10-17T12:33:58Z',
    html_url: 'https://github.com/ayush-rdev/monolith-notes/releases/tag/v2.2.0',
    body: `### Monolith Notes v2.2.0: Borderless Luxury Solid Surfaces

#### 🎨 Solid Surface Luxury Finish
- **Purged All Stroke Borders**: Eliminated wireframe borders and hairline outlines across search capsules, note cards, tag chips, and task badges.
- **Pure Apple Notes / Linear Dark Contrast**: Surfaces float seamlessly using solid dark elevation against pitch black with smooth concentric geometry.
- **Pristine Inset Grouped List**: Notes rendered exclusively as an authentic iOS Inset Grouped stream.`
  },
  {
    tag_name: 'v2.1.9',
    name: 'Monolith Notes v2.1.9 - Apple Gallery Card Overhaul & Luxury Precision Finish',
    published_at: '2026-10-17T12:13:15Z',
    html_url: 'https://github.com/ayush-rdev/monolith-notes/releases/tag/v2.1.9',
    body: `### ✨ What's New in v2.1.9:
- **Luxury Apple Notes Gallery Card Overhaul**: Redesigned note grid cards with uniform fixed-height cards and clean metadata footer with micro-pill badges.
- **Concentric Capsule Geometry**: Authentic Apple concentric pill capsules with tinted specular hairline rims.
- **Compact Spatial Density**: Reduced note row vertical padding from 12dp to 9dp, allowing more notes per screen without visual clutter.`
  }
];

interface SettingsModalProps {
  isOpen: boolean;
  settings: AppSettings;
  cloudConfig: MonolithCloudConfig;
  isCloudSyncing: boolean;
  cloudSyncMessage?: string;
  noteCount: number;
  wordCount: number;
  trashCount: number;
  vaultPath: string;
  onClose: () => void;
  onUpdateSettings: (updater: (prev: AppSettings) => AppSettings) => void;
  onExportNotes: () => void;
  onOpenTrash: () => void;
  onOpenCloudVault: () => void;
  onPerformCloudSync: () => void;
  onChangeVaultPath?: () => void;
}

type SettingsTab = 'appearance' | 'notes' | 'behavior' | 'cloud' | 'storage' | 'about';

interface TabMeta {
  title: string;
  subtitle: string;
}

const TAB_METAS: Record<SettingsTab, TabMeta> = {
  appearance: {
    title: 'Appearance',
    subtitle: 'Theme and visual style'
  },
  notes: {
    title: 'Notes',
    subtitle: 'Editor text size and drafting behavior'
  },
  behavior: {
    title: 'Behavior',
    subtitle: 'Sorting order and tactile feedback'
  },
  cloud: {
    title: 'Sync',
    subtitle: 'Encrypted multi-device sync'
  },
  storage: {
    title: 'Storage',
    subtitle: 'Vault folder, backup archive, and note statistics'
  },
  about: {
    title: 'About',
    subtitle: 'Version, creators, and release notes'
  }
};

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  settings,
  cloudConfig,
  isCloudSyncing,
  cloudSyncMessage,
  noteCount,
  wordCount,
  trashCount: _trashCount,
  vaultPath,
  onClose,
  onUpdateSettings,
  onExportNotes,
  onOpenTrash: _onOpenTrash,
  onOpenCloudVault,
  onPerformCloudSync,
  onChangeVaultPath
}) => {
  const [activeTab, setActiveTab] = useState<SettingsTab>('appearance');
  const [releases, setReleases] = useState<GithubRelease[]>(INITIAL_RELEASES);
  const [expandedVersion, setExpandedVersion] = useState<string | null>(null);

  useEffect(() => {
    fetch('https://api.github.com/repos/ayush-rdev/monolith-notes/releases')
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data) && data.length > 0) {
          setReleases(data.slice(0, 5).map(r => ({
            tag_name: r.tag_name,
            name: r.name,
            published_at: r.published_at,
            html_url: r.html_url,
            body: r.body || ''
          })));
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const navItems: { id: SettingsTab; label: string; icon: React.ReactNode }[] = [
    { id: 'appearance', label: 'Appearance', icon: <Palette size={17} /> },
    { id: 'notes', label: 'Notes', icon: <FileText size={17} /> },
    { id: 'behavior', label: 'Behavior', icon: <Sliders size={17} /> },
    { id: 'cloud', label: 'Sync', icon: <Cloud size={17} /> },
    { id: 'storage', label: 'Storage', icon: <Folder size={17} /> },
    { id: 'about', label: 'About', icon: <Info size={17} /> }
  ];

  const currentMeta = TAB_METAS[activeTab];

  return (
    <div 
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.65)',
        backdropFilter: 'blur(10px)',
        zIndex: 1000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        userSelect: 'none'
      }}
    >
      <div 
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '840px',
          maxWidth: '92vw',
          height: '580px',
          maxHeight: '88vh',
          backgroundColor: 'var(--bg-primary)',
          borderRadius: '16px',
          border: '0.5px solid var(--divider)',
          display: 'flex',
          overflow: 'hidden',
          boxShadow: '0 24px 64px rgba(0,0,0,0.95)'
        }}
      >
        {/* Left Column: Sidebar Navigation */}
        <div style={{
          width: '230px',
          backgroundColor: 'var(--bg-primary)',
          borderRight: '0.5px solid var(--divider)',
          display: 'flex',
          flexDirection: 'column',
          padding: '24px 14px 16px 14px',
          flexShrink: 0
        }}>
          <h2 style={{
            fontFamily: 'var(--font-display)',
            fontSize: '22px',
            fontWeight: 800,
            letterSpacing: '-0.3px',
            color: 'var(--text-primary)',
            margin: '0 0 18px 10px'
          }}>
            Settings
          </h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', flex: 1 }}>
            {navItems.map((item) => {
              const isSelected = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                    padding: '9px 12px',
                    borderRadius: '9px',
                    border: 'none',
                    backgroundColor: isSelected ? 'var(--bg-elevated)' : 'transparent',
                    color: isSelected ? 'var(--text-primary)' : 'var(--text-secondary)',
                    cursor: 'pointer',
                    fontFamily: 'var(--font-text)',
                    fontSize: '14px',
                    fontWeight: 500,
                    width: '100%',
                    textAlign: 'left',
                    transition: 'background-color 0.12s ease, color 0.12s ease'
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.04)';
                      e.currentTarget.style.color = 'var(--text-primary)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.backgroundColor = 'transparent';
                      e.currentTarget.style.color = 'var(--text-secondary)';
                    }
                  }}
                >
                  <span style={{ 
                    color: isSelected ? 'var(--accent)' : 'inherit', 
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0 
                  }}>
                    {item.icon}
                  </span>
                  <span>
                    {item.label}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right Column: Active Tab Content */}
        <div style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          backgroundColor: 'var(--bg-primary)',
          overflow: 'hidden'
        }}>
          {/* Header */}
          <div style={{
            padding: '22px 28px 16px 28px',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            borderBottom: '0.5px solid var(--divider)',
            flexShrink: 0
          }}>
            <div>
              <h3 style={{
                fontFamily: 'var(--font-display)',
                fontSize: '20px',
                fontWeight: 700,
                letterSpacing: '-0.3px',
                color: 'var(--text-primary)',
                margin: '0 0 4px 0'
              }}>
                {currentMeta.title}
              </h3>
              <div style={{
                fontFamily: 'var(--font-text)',
                fontSize: '12.5px',
                color: 'var(--text-secondary)'
              }}>
                {currentMeta.subtitle}
              </div>
            </div>

            <button
              onClick={onClose}
              title="Close Settings (Esc)"
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--text-tertiary)',
                cursor: 'pointer',
                padding: '6px',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--bg-elevated)';
                e.currentTarget.style.color = 'var(--text-primary)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'transparent';
                e.currentTarget.style.color = 'var(--text-tertiary)';
              }}
            >
              <X size={18} />
            </button>
          </div>

          {/* Scrollable Body */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '24px 28px',
            display: 'flex',
            flexDirection: 'column',
            gap: '20px'
          }}>
            {/* TAB: Appearance */}
            {activeTab === 'appearance' && (
              <div>
                <div style={sectionHeaderStyle}>MINIMAL PREMIUM THEMES</div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                  gap: '12px'
                }}>
                  {(Object.keys(THEMES) as ThemeId[]).map((themeKey) => {
                    const theme = THEMES[themeKey];
                    const isSelected = settings.theme === themeKey;
                    return (
                      <div
                        key={themeKey}
                        onClick={() => {
                          onUpdateSettings(prev => ({ ...prev, theme: themeKey }));
                          if (settings.hapticsEnabled && 'vibrate' in navigator) {
                            try { navigator.vibrate(8); } catch (_) {}
                          }
                        }}
                        style={{
                          borderRadius: '12px',
                          backgroundColor: theme.cardBackground,
                          border: isSelected ? `2px solid ${theme.accent}` : `0.5px solid ${theme.divider}`,
                          cursor: 'pointer',
                          padding: '12px',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '10px',
                          transition: 'border-color 0.15s ease'
                        }}
                      >
                        {/* Swatch Preview Bar */}
                        <div style={{
                          width: '100%',
                          height: '28px',
                          borderRadius: '6px',
                          backgroundColor: theme.background,
                          padding: '0 8px',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between'
                        }}>
                          <div style={{
                            width: '10px',
                            height: '10px',
                            borderRadius: '50%',
                            backgroundColor: theme.accent
                          }} />
                          {isSelected && (
                            <Check size={14} color={theme.accent} />
                          )}
                        </div>

                        {/* Title */}
                        <div style={{
                          fontFamily: 'var(--font-display)',
                          fontSize: '14px',
                          fontWeight: 700,
                          color: theme.textPrimary
                        }}>
                          {theme.displayName}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* TAB: Notes & Editing */}
            {activeTab === 'notes' && (
              <div>
                <div style={sectionHeaderStyle}>EDITOR CONFIGURATION</div>
                <div style={cardGroupStyle}>
                  {/* Editor Font Size */}
                  <SettingsRow
                    icon={<Type size={17} />}
                    iconTint="#0A84FF"
                    title="Editor Text Size"
                    value={
                      settings.editorFontSize === 'COMPACT' ? 'Compact (15sp)' :
                      settings.editorFontSize === 'DEFAULT' ? 'Default (17sp)' :
                      settings.editorFontSize === 'LARGE' ? 'Large (19sp)' : 'Extra Large (21sp)'
                    }
                    onClick={() => {
                      const sizes: EditorFontSize[] = ['COMPACT', 'DEFAULT', 'LARGE', 'EXTRA_LARGE'];
                      const next = sizes[(sizes.indexOf(settings.editorFontSize) + 1) % sizes.length];
                      onUpdateSettings(prev => ({ ...prev, editorFontSize: next }));
                    }}
                  />

                  <div style={dividerStyle} />

                  {/* New Notes Start With */}
                  <SettingsRow
                    icon={<FileText size={17} />}
                    iconTint="#FF9F0A"
                    title="New Notes Start With"
                    value={settings.newNoteStartStyle === 'TITLE' ? 'Title' : 'Body'}
                    onClick={() => {
                      onUpdateSettings(prev => ({
                        ...prev,
                        newNoteStartStyle: prev.newNoteStartStyle === 'TITLE' ? 'BODY' : 'TITLE'
                      }));
                    }}
                  />

                  <div style={dividerStyle} />

                  {/* Live Word Count in Editor */}
                  <SettingsToggleRow
                    icon={<Sliders size={17} />}
                    iconTint="#34C759"
                    title="Live Word Count in Editor"
                    checked={settings.showWordCount}
                    onCheckedChange={(val) => onUpdateSettings(prev => ({ ...prev, showWordCount: val }))}
                  />

                  <div style={dividerStyle} />

                  {/* Auto-Continue Lists */}
                  <SettingsToggleRow
                    icon={<CheckSquare size={17} />}
                    iconTint="#34C759"
                    title="Auto-Continue Lists"
                    checked={settings.autoContinueChecklists}
                    onCheckedChange={(val) => onUpdateSettings(prev => ({ ...prev, autoContinueChecklists: val }))}
                  />
                </div>
              </div>
            )}

            {/* TAB: Behavior & Feedback */}
            {activeTab === 'behavior' && (
              <div>
                <div style={sectionHeaderStyle}>BEHAVIOR</div>
                <div style={cardGroupStyle}>
                  {/* Sort Notes By */}
                  <SettingsRow
                    icon={<ArrowUpDown size={17} />}
                    iconTint="var(--accent)"
                    title="Sort Notes By"
                    value={
                      settings.sortOrder === 'DATE_EDITED' ? 'Date Edited' :
                      settings.sortOrder === 'DATE_CREATED' ? 'Date Created' : 'Title (A-Z)'
                    }
                    onClick={() => {
                      const next = settings.sortOrder === 'DATE_EDITED' ? 'DATE_CREATED' :
                                   settings.sortOrder === 'DATE_CREATED' ? 'TITLE' : 'DATE_EDITED';
                      onUpdateSettings(prev => ({ ...prev, sortOrder: next }));
                    }}
                  />

                  <div style={dividerStyle} />

                  {/* Tactile Haptics */}
                  <SettingsToggleRow
                    icon={<Vibrate size={17} />}
                    iconTint="#FF375F"
                    title="Tactile Feedback"
                    checked={settings.hapticsEnabled}
                    onCheckedChange={(val) => onUpdateSettings(prev => ({ ...prev, hapticsEnabled: val }))}
                  />
                </div>
              </div>
            )}

            {/* TAB: Cloud Vault Sync */}
            {activeTab === 'cloud' && (
              <div>
                <div style={sectionHeaderStyle}>MONOLITH CLOUD SYNC</div>
                <div style={cardGroupStyle}>
                  {(() => {
                    const isCloudLoggedIn = Boolean(cloudConfig?.token && cloudConfig?.email);
                    const cloudSubtitle = isCloudSyncing
                      ? (cloudSyncMessage || 'Syncing with Monolith Cloud...')
                      : !isCloudLoggedIn
                      ? 'Instant zero-knowledge E2EE cloud sync'
                      : cloudSyncMessage || (cloudConfig.lastServerSyncTime > 0
                        ? `Connected (${cloudConfig.email}) • Synced ${new Date(cloudConfig.lastServerSyncTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                        : `Connected (${cloudConfig.email}) • Ready to sync`);

                    return (
                      <>
                        <SettingsDetailRow
                          icon={<Cloud size={17} />}
                          iconTint={isCloudLoggedIn ? '#10B981' : 'var(--accent)'}
                          title="Monolith Cloud Vault"
                          subtitle={cloudSubtitle}
                          actionLabel={isCloudSyncing ? 'SYNCING...' : (isCloudLoggedIn ? 'SYNC NOW' : 'CONNECT')}
                          trailingArrow={!isCloudLoggedIn}
                          onClick={() => {
                            if (!isCloudLoggedIn) {
                              onOpenCloudVault();
                            } else if (!isCloudSyncing) {
                              onPerformCloudSync();
                            }
                          }}
                        />
                        {isCloudLoggedIn && (
                          <>
                            <div style={dividerStyle} />
                            <SettingsDetailRow
                              icon={<Sliders size={17} />}
                              iconTint="var(--text-secondary)"
                              title="Cloud Vault Settings"
                              subtitle={`Server: ${cloudConfig.serverUrl || 'Default'}`}
                              actionLabel="MANAGE"
                              trailingArrow
                              onClick={onOpenCloudVault}
                            />
                          </>
                        )}
                      </>
                    );
                  })()}
                </div>
              </div>
            )}

            {/* TAB: Data & Storage */}
            {activeTab === 'storage' && (
              <div>
                <div style={sectionHeaderStyle}>STORAGE</div>
                <div style={cardGroupStyle}>
                  {/* Vault Location */}
                  <SettingsDetailRow
                    icon={<HardDrive size={17} />}
                    iconTint="var(--accent)"
                    title="Vault Location"
                    subtitle={vaultPath}
                    actionLabel="CHANGE"
                    onClick={onChangeVaultPath}
                  />

                  <div style={dividerStyle} />

                  {/* Export Vault */}
                  <SettingsDetailRow
                    icon={<Download size={17} />}
                    iconTint="#0A84FF"
                    title="Export Vault"
                    subtitle="Backup archive of all markdown notes"
                    actionLabel="EXPORT"
                    onClick={onExportNotes}
                  />

                  <div style={dividerStyle} />

                  {/* Total Notes & Words */}
                  <SettingsRow
                    icon={<FileText size={17} />}
                    iconTint="#34C759"
                    title="Total Notes"
                    value={`${noteCount} notes`}
                  />

                  {wordCount > 0 && (
                    <>
                      <div style={dividerStyle} />
                      <SettingsRow
                        icon={<Type size={17} />}
                        iconTint="#FF9F0A"
                        title="Vault Words"
                        value={`${wordCount} words`}
                      />
                    </>
                  )}
                </div>
              </div>
            )}

            {/* TAB: About */}
            {activeTab === 'about' && (
              <>
                {/* App Banner Card */}
                <div style={{
                  backgroundColor: 'var(--bg-card)',
                  borderRadius: '12px',
                  padding: '16px 18px',
                  border: '0.5px solid var(--divider)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{
                      fontFamily: 'var(--font-display)',
                      fontSize: '18px',
                      fontWeight: 700,
                      color: 'var(--text-primary)'
                    }}>
                      Monolith
                    </span>
                    <span style={{
                      borderRadius: '6px',
                      backgroundColor: 'rgba(255, 255, 255, 0.08)',
                      padding: '2px 8px',
                      fontSize: '11px',
                      fontWeight: 600,
                      color: 'var(--accent)'
                    }}>
                      {releases[0]?.tag_name || 'v2.2.1'}
                    </span>
                  </div>
                  <div style={{
                    fontFamily: 'var(--font-text)',
                    fontSize: '13px',
                    color: 'var(--text-secondary)'
                  }}>
                    A beautiful, fast, and encrypted markdown notes app.
                  </div>
                </div>

                {/* CREATORS Section */}
                <div>
                  <div style={sectionHeaderStyle}>PEOPLE</div>
                  <div style={cardGroupStyle}>
                    <div
                      onClick={() => window.open('https://github.com/parixiit', '_blank')}
                      style={{
                        padding: '12px 16px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        cursor: 'pointer',
                        userSelect: 'none'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center' }}>
                        <GithubAvatar username="parixiit" name="Parixit" />
                        <div>
                          <div style={{
                            fontFamily: 'var(--font-text)',
                            fontSize: '14.5px',
                            fontWeight: 500,
                            color: 'var(--text-primary)'
                          }}>
                            Parixit
                          </div>
                          <div style={{
                            fontFamily: 'var(--font-text)',
                            fontSize: '12px',
                            color: 'var(--text-secondary)',
                            marginTop: '1px'
                          }}>
                            @parixiit
                          </div>
                        </div>
                      </div>
                      <button
                        style={pillButtonStyle}
                        onClick={(e) => {
                          e.stopPropagation();
                          window.open('https://github.com/parixiit', '_blank');
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.14)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.08)'}
                      >
                        GitHub
                      </button>
                    </div>

                    <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '64px' }} />

                    <div
                      onClick={() => window.open('https://github.com/ayush-rdev', '_blank')}
                      style={{
                        padding: '12px 16px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        cursor: 'pointer',
                        userSelect: 'none'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center' }}>
                        <GithubAvatar username="ayush-rdev" name="Ayush" />
                        <div>
                          <div style={{
                            fontFamily: 'var(--font-text)',
                            fontSize: '14.5px',
                            fontWeight: 500,
                            color: 'var(--text-primary)'
                          }}>
                            Ayush
                          </div>
                          <div style={{
                            fontFamily: 'var(--font-text)',
                            fontSize: '12px',
                            color: 'var(--text-secondary)',
                            marginTop: '1px'
                          }}>
                            @ayush-rdev
                          </div>
                        </div>
                      </div>
                      <button
                        style={pillButtonStyle}
                        onClick={(e) => {
                          e.stopPropagation();
                          window.open('https://github.com/ayush-rdev', '_blank');
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.14)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.08)'}
                      >
                        GitHub
                      </button>
                    </div>
                  </div>
                </div>

                {/* WHAT'S NEW Section (Click version to view description) */}
                <div>
                  <div style={sectionHeaderStyle}>WHAT'S NEW</div>
                  <div style={cardGroupStyle}>
                    {releases.map((rel, idx) => {
                      const displayVer = `Version ${rel.tag_name.replace(/^v/, '')}`;
                      const isExpanded = expandedVersion === rel.tag_name;
                      let formattedDate = '';
                      try {
                        const d = new Date(rel.published_at);
                        formattedDate = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
                      } catch {
                        formattedDate = rel.published_at;
                      }

                      return (
                        <div key={rel.tag_name}>
                          {idx > 0 && (
                            <div style={{ height: '0.5px', backgroundColor: 'var(--divider)', marginLeft: '16px' }} />
                          )}
                          <div
                            onClick={() => setExpandedVersion(isExpanded ? null : rel.tag_name)}
                            title={isExpanded ? 'Click to collapse description' : 'Click to view description'}
                            style={{
                              padding: '12px 16px',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              cursor: 'pointer',
                              backgroundColor: isExpanded ? 'rgba(255, 255, 255, 0.03)' : 'transparent',
                              transition: 'background-color 0.15s ease',
                              userSelect: 'none'
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span style={{
                                fontFamily: 'var(--font-text)',
                                fontSize: '14px',
                                fontWeight: 500,
                                color: 'var(--text-primary)'
                              }}>
                                {displayVer}
                              </span>
                              {idx === 0 && (
                                <span style={{
                                  borderRadius: '6px',
                                  backgroundColor: 'rgba(255, 255, 255, 0.08)',
                                  padding: '2px 7px',
                                  fontSize: '11px',
                                  fontWeight: 500,
                                  color: 'var(--accent)'
                                }}>
                                  installed
                                </span>
                              )}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span style={{
                                fontFamily: 'var(--font-text)',
                                fontSize: '13px',
                                color: 'var(--text-secondary)'
                              }}>
                                {formattedDate}
                              </span>
                              <ChevronDown
                                size={15}
                                color="var(--text-tertiary)"
                                style={{
                                  transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)',
                                  transition: 'transform 0.2s ease'
                                }}
                              />
                            </div>
                          </div>

                          {isExpanded && (
                            <ReleaseBodyView body={rel.body || 'No release description provided.'} releaseUrl={rel.html_url} />
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

// Reusable Settings Rows
const getIconContainerStyle = (tint: string): React.CSSProperties => {
  const isVar = tint.startsWith('var(');
  return {
    width: '30px',
    height: '30px',
    borderRadius: '7px',
    backgroundColor: isVar ? 'var(--accent-container, rgba(229, 169, 60, 0.18))' : `${tint}29`,
    color: tint,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: '14px',
    flexShrink: 0
  };
};

const SettingsRow: React.FC<{
  icon: React.ReactNode;
  iconTint: string;
  title: string;
  value?: string;
  trailingArrow?: boolean;
  onClick?: () => void;
}> = ({ icon, iconTint, title, value, trailingArrow, onClick }) => (
  <div
    onClick={onClick}
    style={{
      minHeight: '48px',
      padding: '11px 16px',
      display: 'flex',
      alignItems: 'center',
      cursor: onClick ? 'pointer' : 'default',
      userSelect: 'none'
    }}
  >
    <div style={getIconContainerStyle(iconTint)}>
      {icon}
    </div>

    <span style={{
      fontFamily: 'var(--font-text)',
      fontSize: '15px',
      color: 'var(--text-primary)',
      flex: 1
    }}>
      {title}
    </span>

    {value && (
      <span style={{
        fontFamily: 'var(--font-text)',
        fontSize: '14px',
        color: 'var(--text-secondary)',
        marginRight: trailingArrow ? '4px' : '0'
      }}>
        {value}
      </span>
    )}

    {trailingArrow && (
      <ChevronRight size={16} color="var(--text-tertiary)" opacity={0.5} />
    )}
  </div>
);

const SettingsToggleRow: React.FC<{
  icon: React.ReactNode;
  iconTint: string;
  title: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}> = ({ icon, iconTint, title, checked, onCheckedChange }) => (
  <div
    onClick={() => {
      onCheckedChange(!checked);
      if ('vibrate' in navigator) {
        try { navigator.vibrate(8); } catch (_) {}
      }
    }}
    style={{
      minHeight: '48px',
      padding: '6px 16px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      cursor: 'pointer',
      userSelect: 'none'
    }}
  >
    <div style={{ display: 'flex', alignItems: 'center' }}>
      <div style={getIconContainerStyle(iconTint)}>
        {icon}
      </div>

      <span style={{
        fontFamily: 'var(--font-text)',
        fontSize: '15px',
        color: 'var(--text-primary)'
      }}>
        {title}
      </span>
    </div>

    {/* Cupertino Toggle Switch */}
    <div
      onClick={(e) => {
        e.stopPropagation();
        onCheckedChange(!checked);
        if ('vibrate' in navigator) {
          try { navigator.vibrate(8); } catch (_) {}
        }
      }}
      style={{
        width: '46px',
        height: '28px',
        borderRadius: '14px',
        backgroundColor: checked ? 'var(--emerald)' : '#3A3A3C',
        padding: '2px',
        cursor: 'pointer',
        transition: 'background-color 0.2s ease',
        display: 'flex',
        alignItems: 'center'
      }}
    >
      <div style={{
        width: '24px',
        height: '24px',
        borderRadius: '50%',
        backgroundColor: '#FFFFFF',
        boxShadow: '0 2px 4px rgba(0,0,0,0.3)',
        transform: checked ? 'translateX(18px)' : 'translateX(0)',
        transition: 'transform 0.2s cubic-bezier(0.2, 0.8, 0.2, 1)'
      }} />
    </div>
  </div>
);

const SettingsDetailRow: React.FC<{
  icon: React.ReactNode;
  iconTint: string;
  title: string;
  subtitle: string;
  actionLabel?: string;
  trailingArrow?: boolean;
  onClick?: () => void;
}> = ({ icon, iconTint, title, subtitle, actionLabel, trailingArrow, onClick }) => (
  <div
    onClick={onClick}
    style={{
      padding: '12px 16px',
      display: 'flex',
      alignItems: 'center',
      cursor: onClick ? 'pointer' : 'default',
      userSelect: 'none'
    }}
  >
    <div style={getIconContainerStyle(iconTint)}>
      {icon}
    </div>

    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{
        fontFamily: 'var(--font-text)',
        fontSize: '15px',
        color: 'var(--text-primary)'
      }}>
        {title}
      </div>
      <div style={{
        fontFamily: 'var(--font-text)',
        fontSize: '12px',
        lineHeight: '16px',
        color: 'var(--text-secondary)',
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis'
      }}>
        {subtitle}
      </div>
    </div>

    {actionLabel && (
      <div style={{
        borderRadius: '6px',
        backgroundColor: 'var(--accent-container)',
        padding: '4px 8px',
        fontSize: '11px',
        fontWeight: 700,
        color: 'var(--accent)',
        marginLeft: '10px'
      }}>
        {actionLabel}
      </div>
    )}

    {trailingArrow && (
      <ChevronRight size={16} color="var(--text-tertiary)" opacity={0.5} style={{ marginLeft: '4px' }} />
    )}
  </div>
);

const sectionHeaderStyle: React.CSSProperties = {
  fontFamily: 'var(--font-text)',
  fontSize: '11.5px',
  fontWeight: 700,
  color: 'var(--text-secondary)',
  letterSpacing: '0.4px',
  padding: '0 0 8px 12px',
  textTransform: 'uppercase'
};

const cardGroupStyle: React.CSSProperties = {
  backgroundColor: 'var(--bg-card)',
  borderRadius: '12px',
  overflow: 'hidden',
  border: '0.5px solid var(--divider)'
};

const dividerStyle: React.CSSProperties = {
  height: '0.5px',
  backgroundColor: 'var(--divider)',
  marginLeft: '58px'
};

const pillButtonStyle: React.CSSProperties = {
  backgroundColor: 'rgba(255, 255, 255, 0.08)',
  border: '0.5px solid rgba(255, 255, 255, 0.12)',
  borderRadius: '10px',
  padding: '7px 15px',
  fontSize: '13px',
  fontWeight: 500,
  color: 'var(--text-primary)',
  cursor: 'pointer',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: '6px',
  transition: 'background-color 0.15s ease',
  fontFamily: 'var(--font-text)',
  userSelect: 'none'
};

const GithubAvatar: React.FC<{ username: string; name: string }> = ({ username, name }) => {
  const [imgError, setImgError] = useState(false);
  const localSrc = username === 'parixiit' ? '/avatars/parixiit.png' : '/avatars/ayush.png';
  const fallbackUrl = `https://avatars.githubusercontent.com/${username}`;

  return (
    <div style={{
      width: '34px',
      height: '34px',
      borderRadius: '50%',
      overflow: 'hidden',
      backgroundColor: 'var(--bg-elevated)',
      border: '1px solid rgba(255, 255, 255, 0.12)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      marginRight: '14px',
      flexShrink: 0
    }}>
      {!imgError ? (
        <img
          src={localSrc}
          alt={name}
          onError={(e) => {
            if (e.currentTarget.src !== fallbackUrl) {
              e.currentTarget.src = fallbackUrl;
            } else {
              setImgError(true);
            }
          }}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        <User size={17} color="var(--accent)" />
      )}
    </div>
  );
};

const ReleaseBodyView: React.FC<{ body: string; releaseUrl: string }> = ({ body, releaseUrl }) => {
  const lines = body
    .split('\n')
    .map(l => l.trim())
    .filter(l => l && !l.startsWith('---'));

  return (
    <div style={{
      padding: '12px 18px 16px 18px',
      backgroundColor: 'rgba(0, 0, 0, 0.22)',
      borderTop: '0.5px solid var(--divider)',
      fontFamily: 'var(--font-text)',
      display: 'flex',
      flexDirection: 'column',
      gap: '8px'
    }}>
      {lines.map((line, idx) => {
        if (line.startsWith('#')) {
          const headerText = line.replace(/^#+\s*/, '').replace(/^[✨🌟🏛️🎨📦\s]+/, '');
          return (
            <div key={idx} style={{
              fontSize: '13px',
              fontWeight: 600,
              color: 'var(--text-primary)',
              marginTop: idx > 0 ? '6px' : '0'
            }}>
              {headerText}
            </div>
          );
        }

        if (line.startsWith('- ') || line.startsWith('• ') || line.startsWith('* ')) {
          const rawBullet = line.replace(/^[-•*]\s*/, '');
          const boldMatch = rawBullet.match(/^\*\*([^*]+)\*\*:\s*(.*)$/);
          if (boldMatch) {
            return (
              <div key={idx} style={{
                fontSize: '12.5px',
                lineHeight: '18px',
                color: 'var(--text-secondary)',
                paddingLeft: '14px',
                position: 'relative'
              }}>
                <span style={{ position: 'absolute', left: '2px', color: 'var(--accent)' }}>•</span>
                <strong style={{ color: 'var(--text-primary)', fontWeight: 600 }}>{boldMatch[1]}: </strong>
                <span>{boldMatch[2]}</span>
              </div>
            );
          }
          return (
            <div key={idx} style={{
              fontSize: '12.5px',
              lineHeight: '18px',
              color: 'var(--text-secondary)',
              paddingLeft: '14px',
              position: 'relative'
            }}>
              <span style={{ position: 'absolute', left: '2px', color: 'var(--accent)' }}>•</span>
              {rawBullet.replace(/\*\*/g, '')}
            </div>
          );
        }

        return (
          <div key={idx} style={{
            fontSize: '12.5px',
            lineHeight: '18px',
            color: 'var(--text-secondary)'
          }}>
            {line.replace(/\*\*/g, '')}
          </div>
        );
      })}

      <div style={{
        marginTop: '6px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'flex-end'
      }}>
        <button
          onClick={(e) => {
            e.stopPropagation();
            window.open(releaseUrl, '_blank');
          }}
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--accent)',
            fontSize: '12px',
            fontWeight: 500,
            cursor: 'pointer',
            padding: '4px 0',
            fontFamily: 'var(--font-text)'
          }}
        >
          View full release on GitHub ↗
        </button>
      </div>
    </div>
  );
};

