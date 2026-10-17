import React, { useState } from 'react';
import { 
  Cloud, 
  Lock, 
  Mail, 
  Shield, 
  X, 
  Eye, 
  EyeOff, 
  RefreshCw, 
  LogOut, 
  ChevronDown, 
  ChevronUp,
  Server,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import type { MonolithCloudConfig } from '../types';
import { MonolithCloudClient, DEFAULT_SERVER_URL } from '../sync/cloudClient';

interface MonolithCloudModalProps {
  isOpen: boolean;
  config: MonolithCloudConfig;
  isSyncing: boolean;
  lastSyncMessage?: string;
  onClose: () => void;
  onSaveConfig: (newConfig: MonolithCloudConfig) => void;
  onTriggerSync: () => Promise<void>;
}

export const MonolithCloudModal: React.FC<MonolithCloudModalProps> = ({
  isOpen,
  config,
  isSyncing,
  lastSyncMessage,
  onClose,
  onSaveConfig,
  onTriggerSync
}) => {
  const isLoggedIn = Boolean(config.token && config.email);

  const [isRegisterMode, setIsRegisterMode] = useState(!isLoggedIn);
  const [email, setEmail] = useState(config.email || '');
  const [password, setPassword] = useState(config.encryptionKey || '');
  const [serverUrl, setServerUrl] = useState(config.serverUrl || DEFAULT_SERVER_URL);
  const [showPassword, setShowPassword] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ isSuccess: boolean; text: string } | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !email.includes('@')) {
      setStatusMessage({ isSuccess: false, text: 'Please enter a valid email address.' });
      return;
    }
    if (password.length < 6) {
      setStatusMessage({ isSuccess: false, text: 'Password must be at least 6 characters.' });
      return;
    }

    setIsLoading(true);
    setStatusMessage(null);

    const clientAction = isRegisterMode 
      ? MonolithCloudClient.register(serverUrl, email, password)
      : MonolithCloudClient.login(serverUrl, email, password);

    const result = await clientAction;
    setIsLoading(false);

    if (result.isSuccess && result.token) {
      setStatusMessage({ isSuccess: true, text: result.message });
      const newConfig: MonolithCloudConfig = {
        serverUrl: serverUrl.trim() || DEFAULT_SERVER_URL,
        email: result.email || email.trim().toLowerCase(),
        token: result.token,
        encryptionKey: password,
        autoSyncOnLaunch: true,
        lastServerSyncTime: config.lastServerSyncTime || 0
      };
      onSaveConfig(newConfig);
      // Automatically trigger initial sync after connecting
      setTimeout(() => {
        onTriggerSync();
      }, 300);
    } else {
      setStatusMessage({ isSuccess: false, text: result.message });
    }
  };

  const handleLogout = () => {
    onSaveConfig({
      ...config,
      token: '',
      email: '',
      encryptionKey: '',
      lastServerSyncTime: 0
    });
    setStatusMessage(null);
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      backgroundColor: 'rgba(0,0,0,0.7)',
      backdropFilter: 'blur(12px)',
      zIndex: 1100,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px'
    }}>
      <div style={{
        width: '460px',
        maxWidth: '100%',
        backgroundColor: '#121215',
        borderRadius: '20px',
        border: '0.5px solid rgba(255,255,255,0.1)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        boxShadow: '0 30px 80px rgba(0,0,0,0.95)'
      }}>
        {/* Top Handle */}
        <div style={{
          width: '100%',
          display: 'flex',
          justifyContent: 'center',
          paddingTop: '10px'
        }}>
          <div style={{
            width: '36px',
            height: '4px',
            borderRadius: '2px',
            backgroundColor: 'rgba(255,255,255,0.2)'
          }} />
        </div>

        {/* Header */}
        <div style={{
          padding: '16px 22px 14px 22px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '0.5px solid rgba(255,255,255,0.06)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              backgroundColor: 'rgba(229, 169, 60, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--accent)'
            }}>
              <Cloud size={18} />
            </div>
            <div>
              <div style={{
                fontFamily: 'var(--font-display)',
                fontSize: '17px',
                fontWeight: 700,
                color: '#FFFFFF'
              }}>
                Monolith Cloud Vault
              </div>
              <div style={{
                fontFamily: 'var(--font-text)',
                fontSize: '12px',
                color: 'rgba(255,255,255,0.5)'
              }}>
                Zero-knowledge end-to-end encrypted sync
              </div>
            </div>
          </div>

          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'rgba(255,255,255,0.5)',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Body Content */}
        <div style={{ padding: '20px 22px' }}>
          {isLoggedIn ? (
            /* Logged In View */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {/* Account Status Card */}
              <div style={{
                backgroundColor: 'rgba(255,255,255,0.04)',
                border: '0.5px solid rgba(255,255,255,0.08)',
                borderRadius: '14px',
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: '10px'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <div style={{
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    backgroundColor: '#10B981',
                    boxShadow: '0 0 10px #10B981'
                  }} />
                  <span style={{
                    fontFamily: 'var(--font-text)',
                    fontSize: '13px',
                    fontWeight: 600,
                    color: '#10B981',
                    letterSpacing: '0.2px'
                  }}>
                    Connected & Encrypted
                  </span>
                </div>

                <div style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '14px',
                  fontWeight: 600,
                  color: '#FFFFFF'
                }}>
                  {config.email}
                </div>

                <div style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '11px',
                  color: 'rgba(255,255,255,0.5)',
                  wordBreak: 'break-all'
                }}>
                  Server: {config.serverUrl}
                </div>

                {config.lastServerSyncTime > 0 && (
                  <div style={{
                    fontFamily: 'var(--font-text)',
                    fontSize: '11px',
                    color: 'rgba(255,255,255,0.4)'
                  }}>
                    Last Synced: {new Date(config.lastServerSyncTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                  </div>
                )}
              </div>

              {lastSyncMessage && (
                <div style={{
                  fontSize: '12px',
                  fontFamily: 'var(--font-text)',
                  color: 'rgba(255,255,255,0.7)',
                  textAlign: 'center'
                }}>
                  {lastSyncMessage}
                </div>
              )}

              {/* Sync Now Button */}
              <button
                onClick={() => onTriggerSync()}
                disabled={isSyncing}
                style={{
                  height: '46px',
                  borderRadius: '12px',
                  backgroundColor: 'var(--accent)',
                  color: '#000000',
                  border: 'none',
                  fontFamily: 'var(--font-text)',
                  fontSize: '14px',
                  fontWeight: 600,
                  cursor: isSyncing ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  opacity: isSyncing ? 0.7 : 1,
                  transition: 'opacity 0.2s'
                }}
              >
                <RefreshCw size={16} className={isSyncing ? 'animate-spin' : ''} style={{
                  animation: isSyncing ? 'spin 1s linear infinite' : 'none'
                }} />
                <span>{isSyncing ? 'Syncing...' : 'Sync Vault Now'}</span>
              </button>

              {/* Disconnect Button */}
              <button
                onClick={handleLogout}
                style={{
                  height: '42px',
                  borderRadius: '12px',
                  backgroundColor: 'rgba(239, 68, 68, 0.1)',
                  color: '#EF4444',
                  border: '0.5px solid rgba(239, 68, 68, 0.2)',
                  fontFamily: 'var(--font-text)',
                  fontSize: '13px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '6px'
                }}
              >
                <LogOut size={15} />
                <span>Disconnect & Log Out</span>
              </button>
            </div>
          ) : (
            /* Auth Form (Tabs: Create Free Vault | Sign In) */
            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Tab Selector */}
              <div style={{
                display: 'flex',
                backgroundColor: 'rgba(255,255,255,0.05)',
                borderRadius: '10px',
                padding: '3px'
              }}>
                <button
                  type="button"
                  onClick={() => { setIsRegisterMode(true); setStatusMessage(null); }}
                  style={{
                    flex: 1,
                    padding: '8px 0',
                    border: 'none',
                    borderRadius: '8px',
                    fontFamily: 'var(--font-text)',
                    fontSize: '13px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    backgroundColor: isRegisterMode ? 'var(--accent)' : 'transparent',
                    color: isRegisterMode ? '#000000' : 'rgba(255,255,255,0.6)',
                    transition: 'all 0.15s ease'
                  }}
                >
                  Create Free Vault
                </button>
                <button
                  type="button"
                  onClick={() => { setIsRegisterMode(false); setStatusMessage(null); }}
                  style={{
                    flex: 1,
                    padding: '8px 0',
                    border: 'none',
                    borderRadius: '8px',
                    fontFamily: 'var(--font-text)',
                    fontSize: '13px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    backgroundColor: !isRegisterMode ? 'var(--accent)' : 'transparent',
                    color: !isRegisterMode ? '#000000' : 'rgba(255,255,255,0.6)',
                    transition: 'all 0.15s ease'
                  }}
                >
                  Sign In
                </button>
              </div>

              {/* Email Input */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '12px',
                  fontWeight: 500,
                  color: 'rgba(255,255,255,0.7)'
                }}>
                  Email Address
                </label>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  border: '0.5px solid rgba(255,255,255,0.1)',
                  borderRadius: '10px',
                  padding: '0 12px'
                }}>
                  <Mail size={16} color="var(--accent)" style={{ marginRight: '8px' }} />
                  <input
                    type="email"
                    value={email}
                    onChange={e => { setEmail(e.target.value); setStatusMessage(null); }}
                    placeholder="you@example.com"
                    required
                    style={{
                      flex: 1,
                      height: '42px',
                      background: 'transparent',
                      border: 'none',
                      outline: 'none',
                      color: '#FFFFFF',
                      fontFamily: 'var(--font-text)',
                      fontSize: '14px'
                    }}
                  />
                </div>
              </div>

              {/* Password Input */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <label style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '12px',
                  fontWeight: 500,
                  color: 'rgba(255,255,255,0.7)'
                }}>
                  Vault Passphrase / Password
                </label>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  border: '0.5px solid rgba(255,255,255,0.1)',
                  borderRadius: '10px',
                  padding: '0 12px'
                }}>
                  <Lock size={16} color="var(--accent)" style={{ marginRight: '8px' }} />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={e => { setPassword(e.target.value); setStatusMessage(null); }}
                    placeholder={isRegisterMode ? 'Create password (min 6 chars)' : 'Your vault password'}
                    required
                    style={{
                      flex: 1,
                      height: '42px',
                      background: 'transparent',
                      border: 'none',
                      outline: 'none',
                      color: '#FFFFFF',
                      fontFamily: 'var(--font-text)',
                      fontSize: '14px'
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'rgba(255,255,255,0.5)',
                      cursor: 'pointer',
                      padding: '4px'
                    }}
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {/* E2EE Callout Banner */}
              <div style={{
                backgroundColor: 'rgba(30, 58, 95, 0.25)',
                border: '0.5px solid rgba(78, 168, 222, 0.3)',
                borderRadius: '10px',
                padding: '10px 12px',
                display: 'flex',
                alignItems: 'flex-start',
                gap: '8px'
              }}>
                <Shield size={16} color="#4EA8DE" style={{ flexShrink: 0, marginTop: '2px' }} />
                <span style={{
                  fontFamily: 'var(--font-text)',
                  fontSize: '11.5px',
                  lineHeight: '15px',
                  color: '#A8DADC'
                }}>
                  End-to-End Encrypted (AES-256-GCM). Your password encrypts notes locally before leaving your device. Plaintext never touches the server.
                </span>
              </div>

              {/* Advanced Server URL Toggle */}
              <div>
                <button
                  type="button"
                  onClick={() => setShowAdvanced(!showAdvanced)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    width: '100%',
                    background: 'transparent',
                    border: 'none',
                    color: 'rgba(255,255,255,0.5)',
                    fontFamily: 'var(--font-text)',
                    fontSize: '12px',
                    fontWeight: 500,
                    cursor: 'pointer',
                    padding: '4px 0'
                  }}
                >
                  <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Server size={13} />
                    Advanced Server URL
                  </span>
                  {showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                </button>

                {showAdvanced && (
                  <div style={{ marginTop: '8px' }}>
                    <input
                      type="text"
                      value={serverUrl}
                      onChange={e => setServerUrl(e.target.value)}
                      placeholder="http://localhost:8080"
                      style={{
                        width: '100%',
                        height: '38px',
                        backgroundColor: 'rgba(255,255,255,0.06)',
                        border: '0.5px solid rgba(255,255,255,0.1)',
                        borderRadius: '8px',
                        color: '#FFFFFF',
                        padding: '0 10px',
                        fontFamily: 'var(--font-mono, monospace)',
                        fontSize: '12px',
                        outline: 'none',
                        boxSizing: 'border-box'
                      }}
                    />
                    <div style={{
                      fontSize: '10.5px',
                      color: 'rgba(255,255,255,0.4)',
                      marginTop: '4px'
                    }}>
                      Default: http://localhost:8080 (Matches server/docker-compose.yml)
                    </div>
                  </div>
                )}
              </div>

              {/* Status Message */}
              {statusMessage && (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '12.5px',
                  fontFamily: 'var(--font-text)',
                  color: statusMessage.isSuccess ? '#10B981' : '#EF4444'
                }}>
                  {statusMessage.isSuccess ? <CheckCircle2 size={15} /> : <AlertCircle size={15} />}
                  <span>{statusMessage.text}</span>
                </div>
              )}

              {/* Submit Button */}
              <button
                type="submit"
                disabled={isLoading}
                style={{
                  height: '46px',
                  borderRadius: '12px',
                  backgroundColor: 'var(--accent)',
                  color: '#000000',
                  border: 'none',
                  fontFamily: 'var(--font-text)',
                  fontSize: '14px',
                  fontWeight: 700,
                  cursor: isLoading ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  marginTop: '4px',
                  opacity: isLoading ? 0.7 : 1,
                  boxShadow: '0 4px 14px rgba(0,0,0,0.4)'
                }}
              >
                {isLoading && (
                  <RefreshCw size={16} style={{
                    animation: 'spin 1s linear infinite'
                  }} />
                )}
                <span>
                  {isLoading 
                    ? (isRegisterMode ? 'Creating Vault...' : 'Signing In...')
                    : (isRegisterMode ? 'Create Cloud Vault' : 'Sign In to Vault')}
                </span>
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
