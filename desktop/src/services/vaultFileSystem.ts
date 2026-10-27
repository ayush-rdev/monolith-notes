import { invoke, isTauri } from '@tauri-apps/api/core';
import type { Note } from '../types';
import { extractTags } from '../utils';

export interface NotePayload {
  id: string;
  title: string;
  content: string;
  folder: string;
  last_modified: number;
}

export const isTauriEnvironment = (): boolean => {
  try {
    return isTauri();
  } catch {
    return false;
  }
};

export const getVaultLocation = async (): Promise<string | null> => {
  if (!isTauriEnvironment()) {
    return localStorage.getItem('monolith_vault_location');
  }
  try {
    const loc = await invoke<string | null>('get_vault_location');
    return loc;
  } catch (err) {
    console.error('Failed to get vault location from Tauri:', err);
    return localStorage.getItem('monolith_vault_location');
  }
};

export const getDefaultVaultLocation = async (): Promise<string> => {
  if (!isTauriEnvironment()) {
    return 'Documents/MonolithNotes';
  }
  try {
    return await invoke<string>('get_default_vault_location');
  } catch {
    return 'Documents/MonolithNotes';
  }
};

export const setVaultLocation = async (path: string): Promise<string> => {
  localStorage.setItem('monolith_vault_location', path);
  if (!isTauriEnvironment()) {
    return path;
  }
  return await invoke<string>('set_vault_location', { path });
};

export const pickVaultFolder = async (): Promise<string | null> => {
  if (!isTauriEnvironment()) {
    return null;
  }
  try {
    return await invoke<string | null>('pick_vault_folder');
  } catch (err) {
    console.error('Failed to pick vault folder:', err);
    return null;
  }
};

export const createVault = async (parentDir: string, vaultName: string): Promise<string> => {
  if (!isTauriEnvironment()) {
    const full = `${parentDir}/${vaultName}`;
    localStorage.setItem('monolith_vault_location', full);
    return full;
  }
  const created = await invoke<string>('create_vault', { parentDir, vaultName });
  localStorage.setItem('monolith_vault_location', created);
  return created;
};

export const listVaultNotesFromDisk = async (customVault?: string): Promise<Note[]> => {
  if (!isTauriEnvironment()) return [];
  try {
    const rawNotes = await invoke<NotePayload[]>('list_vault_notes', {
      customVault: customVault || null,
    });
    return rawNotes.map((rn) => ({
      id: `disk-${rn.id}`,
      title: rn.title,
      content: rn.content,
      folder: rn.folder || 'Notes',
      tags: extractTags(rn.content),
      createdAt: rn.last_modified || Date.now(),
      lastModified: rn.last_modified || Date.now(),
      isPinned: false,
      isTrash: false,
    }));
  } catch (err) {
    console.error('Failed to read notes from disk:', err);
    return [];
  }
};

export const saveNoteToDisk = async (
  note: Note,
  oldTitle?: string,
  oldFolder?: string,
  customVault?: string
): Promise<string | null> => {
  if (!isTauriEnvironment()) return null;
  try {
    return await invoke<string>('save_note_to_disk', {
      customVault: customVault || null,
      title: note.title || 'Untitled',
      content: note.content,
      folder: note.folder || 'Notes',
      oldTitle: oldTitle || null,
      oldFolder: oldFolder || null,
    });
  } catch (err) {
    console.error('Failed to save note to disk:', err);
    return null;
  }
};

export const createFolderOnDisk = async (
  folder: string,
  customVault?: string
): Promise<boolean> => {
  if (!isTauriEnvironment()) return true;
  try {
    return await invoke<boolean>('create_folder_on_disk', {
      customVault: customVault || null,
      folder,
    });
  } catch (err) {
    console.error('Failed to create folder on disk:', err);
    return false;
  }
};

export const trashNoteOnDisk = async (
  note: Note,
  customVault?: string
): Promise<boolean> => {
  if (!isTauriEnvironment()) return true;
  try {
    return await invoke<boolean>('trash_note_on_disk', {
      customVault: customVault || null,
      title: note.title || 'Untitled',
      folder: note.folder || 'Notes',
    });
  } catch (err) {
    console.error('Failed to trash note on disk:', err);
    return false;
  }
};

export const permanentlyDeleteNoteOnDisk = async (
  note: Note,
  isTrash: boolean,
  customVault?: string
): Promise<boolean> => {
  if (!isTauriEnvironment()) return true;
  try {
    return await invoke<boolean>('permanently_delete_note', {
      customVault: customVault || null,
      title: note.title || 'Untitled',
      folder: note.folder || 'Notes',
      isTrash,
    });
  } catch (err) {
    console.error('Failed to delete note on disk:', err);
    return false;
  }
};
