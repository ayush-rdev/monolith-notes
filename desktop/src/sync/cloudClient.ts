/**
 * Monolith Cloud Vault API Client
 * Connects to the standalone Node.js / Docker sync server.
 */

export interface CloudAuthResult {
  isSuccess: boolean;
  token?: string;
  email?: string;
  message: string;
}

export interface CloudSyncItem {
  fileName: string;
  dataBase64: string;
  clientModified: number;
  serverModified?: number;
  isDeleted?: boolean;
}

export interface CloudPullResult {
  isSuccess: boolean;
  items: CloudSyncItem[];
  serverTime: number;
  message: string;
}

export interface CloudPushResult {
  isSuccess: boolean;
  pushedCount: number;
  serverTime: number;
  message: string;
}

export interface MonolithCloudConfig {
  serverUrl: string;
  email: string;
  token: string;
  encryptionKey: string;
  autoSyncOnLaunch: boolean;
  lastServerSyncTime: number;
}

export const DEFAULT_SERVER_URL = 'http://localhost:8080';

export function sanitizeServerUrl(rawUrl: string): string {
  let url = (rawUrl || '').trim();
  if (!url) return DEFAULT_SERVER_URL;
  if (!url.startsWith('http://') && !url.startsWith('https://')) {
    url = `https://${url}`;
  }
  return url.replace(/\/+$/, '');
}

export const MonolithCloudClient = {
  async register(serverUrl: string, email: string, password: string): Promise<CloudAuthResult> {
    try {
      const endpoint = `${sanitizeServerUrl(serverUrl)}/api/v1/auth/register`;
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          password
        })
      });

      const json = await response.json().catch(() => ({}));

      if (response.ok && json.success) {
        return {
          isSuccess: true,
          token: json.token,
          email: json.email || email.trim().toLowerCase(),
          message: 'Account created successfully'
        };
      } else {
        return {
          isSuccess: false,
          message: json.message || `Registration failed (HTTP ${response.status})`
        };
      }
    } catch (err: any) {
      return {
        isSuccess: false,
        message: `Connection error: ${err.message || 'Unable to reach sync server'}`
      };
    }
  },

  async login(serverUrl: string, email: string, password: string): Promise<CloudAuthResult> {
    try {
      const endpoint = `${sanitizeServerUrl(serverUrl)}/api/v1/auth/login`;
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
        body: JSON.stringify({
          email: email.trim().toLowerCase(),
          password
        })
      });

      const json = await response.json().catch(() => ({}));

      if (response.ok && json.success) {
        return {
          isSuccess: true,
          token: json.token,
          email: json.email || email.trim().toLowerCase(),
          message: 'Login successful'
        };
      } else {
        return {
          isSuccess: false,
          message: json.message || `Login failed (HTTP ${response.status})`
        };
      }
    } catch (err: any) {
      return {
        isSuccess: false,
        message: `Connection error: ${err.message || 'Unable to reach sync server'}`
      };
    }
  },

  async push(serverUrl: string, token: string, items: CloudSyncItem[]): Promise<CloudPushResult> {
    if (items.length === 0) {
      return {
        isSuccess: true,
        pushedCount: 0,
        serverTime: Date.now(),
        message: 'No items to push'
      };
    }

    try {
      const endpoint = `${sanitizeServerUrl(serverUrl)}/api/v1/sync/push`;
      const payloadItems = items.map(item => ({
        file_name: item.fileName,
        data_base64: item.dataBase64,
        client_modified: item.clientModified,
        is_deleted: Boolean(item.isDeleted)
      }));

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ items: payloadItems })
      });

      const json = await response.json().catch(() => ({}));

      if (response.ok && json.success) {
        return {
          isSuccess: true,
          pushedCount: json.pushed_count ?? items.length,
          serverTime: json.server_time || Date.now(),
          message: `Pushed ${json.pushed_count ?? items.length} items`
        };
      } else {
        return {
          isSuccess: false,
          pushedCount: 0,
          serverTime: 0,
          message: json.message || `Push failed (HTTP ${response.status})`
        };
      }
    } catch (err: any) {
      return {
        isSuccess: false,
        pushedCount: 0,
        serverTime: 0,
        message: `Push error: ${err.message || 'Network error'}`
      };
    }
  },

  async pull(serverUrl: string, token: string, since: number = 0): Promise<CloudPullResult> {
    try {
      const endpoint = `${sanitizeServerUrl(serverUrl)}/api/v1/sync/pull?since=${since}`;
      const response = await fetch(endpoint, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      const json = await response.json().catch(() => ({}));

      if (response.ok && json.success) {
        const rawItems = Array.isArray(json.items) ? json.items : [];
        const items: CloudSyncItem[] = rawItems.map((obj: any) => ({
          fileName: obj.file_name,
          dataBase64: obj.data_base64,
          clientModified: Number(obj.client_modified) || 0,
          serverModified: Number(obj.server_modified) || 0,
          isDeleted: Boolean(obj.is_deleted)
        }));

        return {
          isSuccess: true,
          items,
          serverTime: json.server_time || Date.now(),
          message: `Pulled ${items.length} items`
        };
      } else {
        return {
          isSuccess: false,
          items: [],
          serverTime: 0,
          message: json.message || `Pull failed (HTTP ${response.status})`
        };
      }
    } catch (err: any) {
      return {
        isSuccess: false,
        items: [],
        serverTime: 0,
        message: `Pull error: ${err.message || 'Network error'}`
      };
    }
  }
};
