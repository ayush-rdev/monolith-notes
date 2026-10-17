/**
 * Monolith Cloud Vault — Client-Side Zero-Knowledge Cryptography (AES-256-GCM + PBKDF2)
 *
 * Wire format matches Android's VaultCrypto.kt 1:1:
 * [MAGIC_HEADER (9B "MLTH_E2EE")][SALT (16B)][IV (12B)][CIPHERTEXT + 16B GCM TAG]
 * Uses Web Cryptography API (window.crypto.subtle)
 */

const MAGIC_HEADER_STRING = 'MLTH_E2EE';
const MAGIC_HEADER = new TextEncoder().encode(MAGIC_HEADER_STRING); // 9 bytes: [77, 76, 84, 72, 95, 69, 50, 69, 69]
const SALT_LEN = 16;
const IV_LEN = 12;
const GCM_TAG_LEN_BYTES = 16;
const PBKDF2_ITERATIONS = 100_000;
const KEY_LEN_BITS = 256;

export function isEncrypted(bytes: Uint8Array): boolean {
  if (bytes.length < MAGIC_HEADER.length + SALT_LEN + IV_LEN + GCM_TAG_LEN_BYTES) {
    return false;
  }
  for (let i = 0; i < MAGIC_HEADER.length; i++) {
    if (bytes[i] !== MAGIC_HEADER[i]) {
      return false;
    }
  }
  return true;
}

export async function deriveKey(passphrase: string, salt: Uint8Array, usages: KeyUsage[]): Promise<CryptoKey> {
  const encoder = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    encoder.encode(passphrase),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  return await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt as BufferSource,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256'
    },
    keyMaterial,
    { name: 'AES-GCM', length: KEY_LEN_BITS },
    false,
    usages
  );
}

/**
 * Encrypts plaintext bytes using AES-256-GCM with a passphrase-derived key.
 * Result format: [MAGIC_HEADER (9B)][SALT (16B)][IV (12B)][CIPHERTEXT + 16B GCM TAG]
 */
export async function encryptBytes(plaintext: Uint8Array, passphrase: string): Promise<Uint8Array> {
  if (!passphrase || passphrase.trim().length === 0) {
    throw new Error('Passphrase cannot be empty');
  }

  const salt = new Uint8Array(SALT_LEN);
  crypto.getRandomValues(salt);

  const iv = new Uint8Array(IV_LEN);
  crypto.getRandomValues(iv);

  const key = await deriveKey(passphrase, salt, ['encrypt']);

  // Web Crypto encrypt with AES-GCM appends the 16-byte authentication tag to the ciphertext buffer
  const encryptedBuf = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: iv as BufferSource,
      additionalData: MAGIC_HEADER as BufferSource,
      tagLength: 128
    },
    key,
    plaintext as BufferSource
  );

  const ciphertextAndTag = new Uint8Array(encryptedBuf);
  const totalLength = MAGIC_HEADER.length + SALT_LEN + IV_LEN + ciphertextAndTag.length;
  const result = new Uint8Array(totalLength);

  let offset = 0;
  result.set(MAGIC_HEADER, offset);
  offset += MAGIC_HEADER.length;

  result.set(salt, offset);
  offset += SALT_LEN;

  result.set(iv, offset);
  offset += IV_LEN;

  result.set(ciphertextAndTag, offset);

  return result;
}

/**
 * Decrypts AES-256-GCM ciphertext bytes using the passphrase.
 * Throws an error if corrupted, tampered with, or invalid passphrase.
 */
export async function decryptBytes(encryptedBytes: Uint8Array, passphrase: string): Promise<Uint8Array> {
  if (!passphrase || passphrase.trim().length === 0) {
    throw new Error('Passphrase cannot be empty');
  }
  if (!isEncrypted(encryptedBytes)) {
    throw new Error('Data does not contain a valid Monolith E2EE header');
  }

  let offset = MAGIC_HEADER.length;

  const salt = encryptedBytes.slice(offset, offset + SALT_LEN);
  offset += SALT_LEN;

  const iv = encryptedBytes.slice(offset, offset + IV_LEN);
  offset += IV_LEN;

  const ciphertextAndTag = encryptedBytes.slice(offset);

  const key = await deriveKey(passphrase, salt, ['decrypt']);

  try {
    const decryptedBuf = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: iv as BufferSource,
        additionalData: MAGIC_HEADER as BufferSource,
        tagLength: 128
      },
      key,
      ciphertextAndTag as BufferSource
    );

    return new Uint8Array(decryptedBuf);
  } catch (err) {
    throw new Error('Decryption failed: Invalid passphrase or corrupted data');
  }
}

// Base64 helpers
export function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

export function base64ToUint8Array(base64: string): Uint8Array {
  const binaryString = atob(base64);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
}

export function stringToUint8Array(str: string): Uint8Array {
  return new TextEncoder().encode(str);
}

export function uint8ArrayToString(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}

export async function encryptStringToBase64(plaintext: string, passphrase: string): Promise<string> {
  const rawBytes = stringToUint8Array(plaintext);
  const encrypted = await encryptBytes(rawBytes, passphrase);
  return uint8ArrayToBase64(encrypted);
}

export async function decryptBase64ToString(base64: string, passphrase: string): Promise<string> {
  const encryptedBytes = base64ToUint8Array(base64);
  const decrypted = await decryptBytes(encryptedBytes, passphrase);
  return uint8ArrayToString(decrypted);
}
