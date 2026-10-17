// Prevents additional console window on Windows in release
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize)]
pub struct NotePayload {
    pub id: String,
    pub title: String,
    pub content: String,
    pub folder: String,
    pub last_modified: u64,
}

// Get the default Monolith Vault directory: ~/Documents/MonolithNotes
fn get_vault_path() -> PathBuf {
    let mut path = dirs::document_dir().unwrap_or_else(|| PathBuf::from("."));
    path.push("MonolithNotes");
    if !path.exists() {
        let _ = fs::create_dir_all(&path);
    }
    path
}

// 1. Rust Command: List all markdown notes directly from the OS filesystem
#[tauri::command]
fn list_vault_notes() -> Result<Vec<NotePayload>, String> {
    let vault = get_vault_path();
    let mut notes = Vec::new();

    if let Ok(entries) = fs::read_dir(&vault) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_file() && path.extension().and_then(|s| s.to_str()) == Some("md") {
                if let Ok(content) = fs::read_to_string(&path) {
                    let file_name = path.file_stem().and_then(|s| s.to_str()).unwrap_or("Untitled");
                    let modified = entry.metadata().and_then(|m| m.modified()).ok()
                        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                        .map(|d| d.as_millis() as u64)
                        .unwrap_or(0);

                    notes.push(NotePayload {
                        id: file_name.to_string(),
                        title: file_name.to_string(),
                        content,
                        folder: "Notes".to_string(),
                        last_modified: modified,
                    });
                }
            }
        }
    }

    Ok(notes)
}

// 2. Rust Command: Save CommonMark note directly to ~/Documents/MonolithNotes/<title>.md
#[tauri::command]
fn save_note_to_disk(title: String, content: String, folder: String) -> Result<String, String> {
    let mut path = get_vault_path();
    if folder != "Notes" && !folder.is_empty() {
        path.push(&folder);
        let _ = fs::create_dir_all(&path);
    }

    let safe_title = title.trim().replace('/', "-").replace('\\', "-");
    let file_name = if safe_title.is_empty() { "Untitled.md" } else { &format!("{}.md", safe_title) };
    path.push(file_name);

    fs::write(&path, content).map_err(|e| e.to_string())?;
    Ok(path.to_string_lossy().to_string())
}

// 3. Rust Command: Move note to 30-day safety net (.trash folder)
#[tauri::command]
fn trash_note_on_disk(title: String, folder: String) -> Result<bool, String> {
    let vault = get_vault_path();
    let mut trash_dir = vault.clone();
    trash_dir.push(".trash");
    let _ = fs::create_dir_all(&trash_dir);

    let mut source_path = vault;
    if folder != "Notes" && !folder.is_empty() {
        source_path.push(&folder);
    }
    source_path.push(format!("{}.md", title));

    let mut dest_path = trash_dir;
    dest_path.push(format!("{}.md", title));

    if source_path.exists() {
        fs::rename(source_path, dest_path).map_err(|e| e.to_string())?;
        Ok(true)
    } else {
        Ok(false)
    }
}

// 4. Rust Command: Return absolute vault path string
#[tauri::command]
fn get_vault_location() -> String {
    get_vault_path().to_string_lossy().to_string()
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            list_vault_notes,
            save_note_to_disk,
            trash_note_on_disk,
            get_vault_location
        ])
        .run(tauri::generate_context!())
        .expect("error while running Monolith desktop application");
}
