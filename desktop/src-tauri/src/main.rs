// Prevents additional console window on Windows in release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct NotePayload {
    pub id: String,
    pub title: String,
    pub content: String,
    pub folder: String,
    pub last_modified: u64,
}

#[derive(Debug, Serialize, Deserialize, Default)]
struct VaultConfig {
    vault_path: Option<String>,
}

fn get_config_file_path() -> PathBuf {
    let mut path = dirs::config_dir().unwrap_or_else(|| PathBuf::from("."));
    path.push("monolith-notes");
    let _ = fs::create_dir_all(&path);
    path.push("vault_config.json");
    path
}

fn load_vault_path() -> Option<PathBuf> {
    let config_path = get_config_file_path();
    if let Ok(content) = fs::read_to_string(&config_path) {
        if let Ok(config) = serde_json::from_str::<VaultConfig>(&content) {
            if let Some(vp) = config.vault_path {
                let p = PathBuf::from(&vp);
                if p.exists() {
                    return Some(p);
                }
            }
        }
    }
    None
}

fn save_vault_path(path: &str) -> Result<(), String> {
    let config_path = get_config_file_path();
    let config = VaultConfig {
        vault_path: Some(path.to_string()),
    };
    let json = serde_json::to_string_pretty(&config).map_err(|e| e.to_string())?;
    fs::write(&config_path, json).map_err(|e| e.to_string())?;
    Ok(())
}

fn resolve_vault(custom_vault: Option<String>) -> Result<PathBuf, String> {
    if let Some(cv) = custom_vault {
        if !cv.trim().is_empty() {
            let p = PathBuf::from(cv.trim());
            let _ = fs::create_dir_all(&p);
            return Ok(p);
        }
    }
    if let Some(p) = load_vault_path() {
        return Ok(p);
    }
    Err("No vault location set".to_string())
}

// 1. Return saved vault path or None
#[tauri::command]
fn get_vault_location() -> Result<Option<String>, String> {
    Ok(load_vault_path().map(|p| p.to_string_lossy().to_string()))
}

// 2. Return default fallback path: ~/Documents/MonolithNotes
#[tauri::command]
fn get_default_vault_location() -> Result<String, String> {
    let mut path = dirs::document_dir().unwrap_or_else(|| PathBuf::from("."));
    path.push("MonolithNotes");
    Ok(path.to_string_lossy().to_string())
}

// 3. Set and persist chosen vault path
#[tauri::command]
fn set_vault_location(path: String) -> Result<String, String> {
    let p = PathBuf::from(&path);
    if !p.exists() {
        fs::create_dir_all(&p).map_err(|e| e.to_string())?;
    }
    save_vault_path(&path)?;
    Ok(path)
}

// 4. Open native OS directory picker dialog
#[tauri::command]
fn pick_vault_folder() -> Result<Option<String>, String> {
    let folder = rfd::FileDialog::new()
        .set_title("Choose Monolith Vault Location")
        .pick_folder();

    Ok(folder.map(|p| p.to_string_lossy().to_string()))
}

// 5. Create new vault folder inside parent directory
#[tauri::command]
fn create_vault(parent_dir: String, vault_name: String) -> Result<String, String> {
    let mut path = PathBuf::from(parent_dir.trim());
    let safe_name = vault_name.trim().replace('/', "-").replace('\\', "-");
    let name = if safe_name.is_empty() { "MonolithNotes" } else { &safe_name };
    path.push(name);
    fs::create_dir_all(&path).map_err(|e| e.to_string())?;
    let path_str = path.to_string_lossy().to_string();
    save_vault_path(&path_str)?;
    Ok(path_str)
}

// 6. List all markdown notes recursively from the vault directory
#[tauri::command]
fn list_vault_notes(custom_vault: Option<String>) -> Result<Vec<NotePayload>, String> {
    let vault = match resolve_vault(custom_vault) {
        Ok(v) => v,
        Err(_) => return Ok(Vec::new()),
    };

    if !vault.exists() {
        return Ok(Vec::new());
    }

    let mut notes = Vec::new();

    for entry in walkdir::WalkDir::new(&vault)
        .into_iter()
        .filter_entry(|e| {
            let file_name = e.file_name().to_string_lossy();
            // Skip hidden folders (.git, .obsidian, .trash)
            if e.file_type().is_dir() && file_name.starts_with('.') && file_name != "." {
                return false;
            }
            true
        })
        .flatten()
    {
        let path = entry.path();
        if path.is_file() && path.extension().and_then(|s| s.to_str()) == Some("md") {
            if let Ok(content) = fs::read_to_string(&path) {
                let file_name = path.file_stem().and_then(|s| s.to_str()).unwrap_or("Untitled");
                let modified = entry.metadata().ok()
                    .and_then(|m| m.modified().ok())
                    .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|d| d.as_millis() as u64)
                    .unwrap_or(0);

                let folder = if let Ok(rel) = path.parent().unwrap_or(&vault).strip_prefix(&vault) {
                    let rel_str = rel.to_string_lossy().to_string();
                    if rel_str.is_empty() {
                        "Notes".to_string()
                    } else {
                        rel_str.replace('\\', "/")
                    }
                } else {
                    "Notes".to_string()
                };

                let note_id = if folder == "Notes" {
                    file_name.to_string()
                } else {
                    format!("{}/{}", folder, file_name)
                };

                notes.push(NotePayload {
                    id: note_id,
                    title: file_name.to_string(),
                    content,
                    folder,
                    last_modified: modified,
                });
            }
        }
    }

    Ok(notes)
}

// 7. Save note as .md directly into vault/folder/title.md
#[tauri::command]
fn save_note_to_disk(
    custom_vault: Option<String>,
    title: String,
    content: String,
    folder: String,
    old_title: Option<String>,
    old_folder: Option<String>,
) -> Result<String, String> {
    let vault = resolve_vault(custom_vault)?;
    let _ = fs::create_dir_all(&vault);

    let mut dest_dir = vault.clone();
    if folder != "Notes" && !folder.trim().is_empty() {
        dest_dir.push(&folder);
        let _ = fs::create_dir_all(&dest_dir);
    }

    let safe_title = title.trim().replace('/', "-").replace('\\', "-");
    let file_name = if safe_title.is_empty() {
        "Untitled.md".to_string()
    } else {
        format!("{}.md", safe_title)
    };

    let target_path = dest_dir.join(&file_name);

    // If note was renamed or moved to another folder, remove old file
    if let (Some(ot), Some(of)) = (old_title, old_folder) {
        if !ot.trim().is_empty() && (ot != title || of != folder) {
            let mut old_dir = vault.clone();
            if of != "Notes" && !of.trim().is_empty() {
                old_dir.push(&of);
            }
            let old_safe = ot.trim().replace('/', "-").replace('\\', "-");
            let old_file = old_dir.join(format!("{}.md", old_safe));
            if old_file.exists() && old_file != target_path {
                let _ = fs::remove_file(old_file);
            }
        }
    }

    fs::write(&target_path, content).map_err(|e| e.to_string())?;
    Ok(target_path.to_string_lossy().to_string())
}

// 8. Create a real directory inside the vault
#[tauri::command]
fn create_folder_on_disk(custom_vault: Option<String>, folder: String) -> Result<bool, String> {
    let vault = resolve_vault(custom_vault)?;
    let mut dir = vault;
    dir.push(&folder);
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(true)
}

// 9. Move note to .trash
#[tauri::command]
fn trash_note_on_disk(custom_vault: Option<String>, title: String, folder: String) -> Result<bool, String> {
    let vault = resolve_vault(custom_vault)?;
    let mut trash_dir = vault.clone();
    trash_dir.push(".trash");
    let _ = fs::create_dir_all(&trash_dir);

    let mut source_path = vault;
    if folder != "Notes" && !folder.is_empty() {
        source_path.push(&folder);
    }
    let safe_title = title.trim().replace('/', "-").replace('\\', "-");
    source_path.push(format!("{}.md", safe_title));

    let mut dest_path = trash_dir;
    dest_path.push(format!("{}.md", safe_title));

    if source_path.exists() {
        fs::rename(source_path, dest_path).map_err(|e| e.to_string())?;
        Ok(true)
    } else {
        Ok(false)
    }
}

// 10. Permanently delete note
#[tauri::command]
fn permanently_delete_note(
    custom_vault: Option<String>,
    title: String,
    folder: String,
    is_trash: bool,
) -> Result<bool, String> {
    let vault = resolve_vault(custom_vault)?;
    let mut path = vault;
    if is_trash {
        path.push(".trash");
    } else if folder != "Notes" && !folder.is_empty() {
        path.push(&folder);
    }
    let safe_title = title.trim().replace('/', "-").replace('\\', "-");
    path.push(format!("{}.md", safe_title));

    if path.exists() {
        fs::remove_file(path).map_err(|e| e.to_string())?;
        Ok(true)
    } else {
        Ok(false)
    }
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            get_vault_location,
            get_default_vault_location,
            set_vault_location,
            pick_vault_folder,
            create_vault,
            list_vault_notes,
            save_note_to_disk,
            create_folder_on_disk,
            trash_note_on_disk,
            permanently_delete_note
        ])
        .run(tauri::generate_context!())
        .expect("error while running Monolith desktop application");
}
