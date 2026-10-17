export type ThemeId = 'obsidian' | 'titanium' | 'nocturne' | 'kanso' | 'midnight';

export interface ThemeConfig {
  id: ThemeId;
  displayName: string;
  subtitle: string;
  background: string;
  cardBackground: string;
  elevatedCard: string;
  accent: string;
  accentContainer: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  divider: string;
}

export const THEMES: Record<ThemeId, ThemeConfig> = {
  obsidian: {
    id: 'obsidian',
    displayName: 'Obsidian',
    subtitle: 'OLED Pure Black & Gold',
    background: '#000000',
    cardBackground: '#18181B',
    elevatedCard: '#27272A',
    accent: '#E5A93C',
    accentContainer: 'rgba(229, 169, 60, 0.2)',
    textPrimary: '#FFFFFF',
    textSecondary: '#A1A1AA',
    textTertiary: '#71717A',
    divider: '#27272A',
  },
  titanium: {
    id: 'titanium',
    displayName: 'Titanium',
    subtitle: 'Nordic Silver & Graphite',
    background: '#0C0D10',
    cardBackground: '#16181D',
    elevatedCard: '#22252C',
    accent: '#E2E8F0',
    accentContainer: 'rgba(226, 232, 240, 0.2)',
    textPrimary: '#F8FAFC',
    textSecondary: '#94A3B8',
    textTertiary: '#64748B',
    divider: '#22252C',
  },
  nocturne: {
    id: 'nocturne',
    displayName: 'Nocturne',
    subtitle: 'British Racing & Sage',
    background: '#070B09',
    cardBackground: '#101814',
    elevatedCard: '#1A2620',
    accent: '#34D399',
    accentContainer: 'rgba(52, 211, 153, 0.2)',
    textPrimary: '#F0FDF4',
    textSecondary: '#86EFAC',
    textTertiary: '#4ADE80',
    divider: '#1A2620',
  },
  kanso: {
    id: 'kanso',
    displayName: 'Kanso',
    subtitle: 'Tokyo Espresso & Sand',
    background: '#0F0D0C',
    cardBackground: '#1C1916',
    elevatedCard: '#2A2522',
    accent: '#E6BA82',
    accentContainer: 'rgba(230, 186, 130, 0.2)',
    textPrimary: '#FAF7F5',
    textSecondary: '#B5A99F',
    textTertiary: '#786E65',
    divider: '#2A2522',
  },
  midnight: {
    id: 'midnight',
    displayName: 'Midnight',
    subtitle: 'Deep Space & Cyan',
    background: '#060911',
    cardBackground: '#0E1424',
    elevatedCard: '#18223B',
    accent: '#38BDF8',
    accentContainer: 'rgba(56, 189, 248, 0.2)',
    textPrimary: '#F0F9FF',
    textSecondary: '#93C5FD',
    textTertiary: '#60A5FA',
    divider: '#18223B',
  },
};

export type NoteSortOrder = 'DATE_EDITED' | 'DATE_CREATED' | 'TITLE';
export type EditorFontSize = 'COMPACT' | 'DEFAULT' | 'LARGE' | 'EXTRA_LARGE';
export type NewNoteStartStyle = 'TITLE' | 'BODY';

export interface AppSettings {
  theme: ThemeId;
  editorFontSize: EditorFontSize;
  newNoteStartStyle: NewNoteStartStyle;
  showWordCount: boolean;
  autoContinueChecklists: boolean;
  sortOrder: NoteSortOrder;
  biometricLockEnabled: boolean;
  hapticsEnabled: boolean;
}

export interface Note {
  id: string;
  filePath?: string;
  title: string;
  content: string;
  tags: string[];
  createdAt?: number;
  lastModified: number;
  isPinned: boolean;
  folder: string; // "Notes" or custom folder name
  isTrash?: boolean;
  trashedAt?: number;
}

export interface MonolithCloudConfig {
  serverUrl: string;
  email: string;
  token: string;
  encryptionKey: string;
  autoSyncOnLaunch: boolean;
  lastServerSyncTime: number;
}

export interface CloudSyncStatus {
  isSyncing: boolean;
  message?: string;
  lastSynced?: number;
}

