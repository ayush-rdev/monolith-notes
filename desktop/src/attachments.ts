// Simple, persistent IndexedDB attachment storage for Monolith desktop
const DB_NAME = 'monolith_vault_attachments';
const STORE_NAME = 'files';

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE_NAME)) {
        req.result.createObjectStore(STORE_NAME);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

// In-memory cache of object URLs for fast synchronous access
const objectUrlCache = new Map<string, string>();

/**
 * Saves a file or image blob into the attachments database.
 * Returns a clean, human-readable markdown path like: "attachments/img_1729158392_screenshot.png"
 */
export async function saveAttachment(file: Blob, preferredName?: string): Promise<string> {
  const db = await openDB();
  const timestamp = Date.now();
  const safeName = (preferredName || 'image.png')
    .replace(/[^a-zA-Z0-9._-]/g, '_')
    .replace(/^_+/, '');
  const id = `attachments/${timestamp}_${safeName}`;

  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put(file, id);
    tx.oncomplete = () => {
      const url = URL.createObjectURL(file);
      objectUrlCache.set(id, url);
      resolve(id);
    };
    tx.onerror = () => reject(tx.error);
  });
}

/**
 * Retrieves the object URL for an attachment path.
 */
export async function getAttachmentUrl(path: string): Promise<string | null> {
  if (objectUrlCache.has(path)) {
    return objectUrlCache.get(path)!;
  }
  if (path.startsWith('data:') || path.startsWith('http://') || path.startsWith('https://')) {
    return path;
  }
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const req = tx.objectStore(STORE_NAME).get(path);
      req.onsuccess = () => {
        const blob = req.result as Blob | undefined;
        if (blob) {
          const url = URL.createObjectURL(blob);
          objectUrlCache.set(path, url);
          resolve(url);
        } else {
          resolve(null);
        }
      };
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

/**
 * Synchronous cache lookup for instantaneous rendering
 */
export function getCachedAttachmentUrl(path: string): string | null {
  if (path.startsWith('data:') || path.startsWith('http://') || path.startsWith('https://')) {
    return path;
  }
  return objectUrlCache.get(path) || null;
}
