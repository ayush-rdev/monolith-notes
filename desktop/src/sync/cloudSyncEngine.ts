/**
 * Monolith Cloud Sync Engine (Desktop)
 * Direct TypeScript port of Android's MonolithCloudSyncEngine.kt
 */
import type { Note } from '../types';
import { 
  MonolithCloudClient, 
  type MonolithCloudConfig, 
  type CloudSyncItem 
} from './cloudClient';
import { 
  base64ToUint8Array, 
  isEncrypted, 
  decryptBytes, 
  uint8ArrayToString,
  encryptBytes,
  stringToUint8Array,
  uint8ArrayToBase64
} from './vaultCrypto';
import { 
  noteToMarkdown, 
  markdownToNote, 
  noteToFileName 
} from './markdownSyncConverter';

const TOMBSTONES_STORAGE_KEY = 'monolith_cloud_tombstones';

export function getTombstones(): Record<string, number> {
  try {
    const raw = localStorage.getItem(TOMBSTONES_STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export function saveTombstones(tombstones: Record<string, number>): void {
  try {
    localStorage.setItem(TOMBSTONES_STORAGE_KEY, JSON.stringify(tombstones));
  } catch {}
}

export function recordTombstone(fileName: string): void {
  const stones = getTombstones();
  stones[fileName] = Date.now();
  saveTombstones(stones);
}

export function removeTombstones(fileNames: string[]): void {
  const stones = getTombstones();
  for (const name of fileNames) {
    delete stones[name];
  }
  saveTombstones(stones);
}

export interface SyncEngineResult {
  isSuccess: boolean;
  notes: Note[];
  updatedConfig: MonolithCloudConfig;
  uploadedCount: number;
  downloadedCount: number;
  message: string;
}

export async function runCloudSync(
  currentNotes: Note[],
  config: MonolithCloudConfig,
  onProgress: (status: string) => void = () => {}
): Promise<SyncEngineResult> {
  if (!config.token || !config.email) {
    return {
      isSuccess: false,
      notes: currentNotes,
      updatedConfig: config,
      uploadedCount: 0,
      downloadedCount: 0,
      message: 'Not signed in to Monolith Cloud Vault'
    };
  }

  try {
    onProgress('Connecting to Monolith Cloud Vault...');
    let downloaded = 0;
    let uploaded = 0;
    let updatedNotes = [...currentNotes];

    // 1. Pull updates from server
    onProgress('Checking for remote updates...');
    const pullResult = await MonolithCloudClient.pull(
      config.serverUrl,
      config.token,
      config.lastServerSyncTime || 0
    );

    if (!pullResult.isSuccess) {
      return {
        isSuccess: false,
        notes: currentNotes,
        updatedConfig: config,
        uploadedCount: 0,
        downloadedCount: 0,
        message: pullResult.message
      };
    }

    // Process pulled remote items
    for (const remoteItem of pullResult.items) {
      const fileName = remoteItem.fileName;

      // Handle remote deletion
      if (remoteItem.isDeleted) {
        updatedNotes = updatedNotes.filter(n => {
          const fn = n.filePath || noteToFileName(n);
          return fn !== fileName;
        });
        continue;
      }

      // Check if we have this note locally
      const existingIdx = updatedNotes.findIndex(n => {
        const fn = n.filePath || noteToFileName(n);
        return fn === fileName;
      });

      const localModified = existingIdx >= 0 ? updatedNotes[existingIdx].lastModified : 0;

      // Conflict resolution: Remote is newer by at least 2 seconds, or note doesn't exist locally
      if (existingIdx === -1 || (remoteItem.clientModified > (localModified + 2000))) {
        onProgress(`Decrypting: ${fileName}...`);

        let markdownContent = '';
        const rawBytes = base64ToUint8Array(remoteItem.dataBase64);

        if (config.encryptionKey && isEncrypted(rawBytes)) {
          try {
            const decryptedBytes = await decryptBytes(rawBytes, config.encryptionKey);
            markdownContent = uint8ArrayToString(decryptedBytes);
          } catch (err: any) {
            throw new Error(`Decryption failed for "${fileName}": ${err.message || 'Incorrect passphrase'}`);
          }
        } else {
          markdownContent = uint8ArrayToString(rawBytes);
        }

        const parsedNote = markdownToNote(fileName, markdownContent, remoteItem.clientModified);

        if (existingIdx >= 0) {
          // Preserve existing local note id
          updatedNotes[existingIdx] = {
            ...parsedNote,
            id: updatedNotes[existingIdx].id
          };
        } else {
          updatedNotes.unshift(parsedNote);
        }

        downloaded++;
      }
    }

    // 2. Prepare local items for push
    const itemsToPush: CloudSyncItem[] = [];

    // Push notes modified since last sync
    for (const note of updatedNotes) {
      if (note.isTrash) continue;

      const isNewerThanSync = note.lastModified > (config.lastServerSyncTime + 2000) || config.lastServerSyncTime === 0;

      if (isNewerThanSync) {
        const fileName = note.filePath || noteToFileName(note);
        const markdown = noteToMarkdown(note);
        const rawBytes = stringToUint8Array(markdown);

        const encryptedBytes = config.encryptionKey 
          ? await encryptBytes(rawBytes, config.encryptionKey)
          : rawBytes;

        itemsToPush.push({
          fileName,
          dataBase64: uint8ArrayToBase64(encryptedBytes),
          clientModified: note.lastModified,
          isDeleted: false
        });
      }
    }

    // Push local deletions (tombstones)
    const tombstones = getTombstones();
    const tombstoneKeys = Object.keys(tombstones);
    for (const deadFileName of tombstoneKeys) {
      itemsToPush.push({
        fileName: deadFileName,
        dataBase64: '',
        clientModified: tombstones[deadFileName] || Date.now(),
        isDeleted: true
      });
    }

    // 3. Push to server
    if (itemsToPush.length > 0) {
      onProgress(`Encrypting & uploading ${itemsToPush.length} change(s)...`);
      const pushResult = await MonolithCloudClient.push(
        config.serverUrl,
        config.token,
        itemsToPush
      );

      if (!pushResult.isSuccess) {
        return {
          isSuccess: false,
          notes: updatedNotes,
          updatedConfig: config,
          uploadedCount: 0,
          downloadedCount: downloaded,
          message: pushResult.message
        };
      }

      uploaded = pushResult.pushedCount;

      // Purge pushed tombstones
      if (tombstoneKeys.length > 0) {
        removeTombstones(tombstoneKeys);
      }
    }

    const newServerTime = pullResult.serverTime > 0 ? pullResult.serverTime : Date.now();
    const updatedConfig: MonolithCloudConfig = {
      ...config,
      lastServerSyncTime: newServerTime
    };

    const summary = uploaded === 0 && downloaded === 0
      ? 'Cloud vault is up to date'
      : `Cloud vault synced: ${uploaded} uploaded, ${downloaded} downloaded`;

    onProgress(summary);

    return {
      isSuccess: true,
      notes: updatedNotes,
      updatedConfig,
      uploadedCount: uploaded,
      downloadedCount: downloaded,
      message: summary
    };
  } catch (err: any) {
    return {
      isSuccess: false,
      notes: currentNotes,
      updatedConfig: config,
      uploadedCount: 0,
      downloadedCount: 0,
      message: `Cloud sync failed: ${err.message || 'Unknown error'}`
    };
  }
}
