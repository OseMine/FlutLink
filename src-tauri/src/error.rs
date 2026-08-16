use serde::ser::{SerializeStruct, Serializer};
use serde::Serialize;

#[derive(Debug)]
pub enum AppError {
    NoActiveAccount,
    Forbidden,
    NotFound(String),
    Http(reqwest::Error),
    Status {
        status: u16,
        body: String,
    },
    Ocs(String),
    App(String),
    Json(serde_json::Error),
    Io(std::io::Error),
    Keyring(String),
    Parse(String),
    NotFlutCloud(String),
    FlutCloudAppMissing,
    Update(String),
    /// Two sync folders of one account target the same remote folder, which
    /// would overwrite each other's data.
    SyncFolderConflict {
        local_path: String,
        remote_path: String,
    },
    /// Rename/move target already exists and the operation refused to
    /// overwrite it (WebDAV `Overwrite: F` → 412).
    TargetExists(String),
}

impl AppError {
    pub fn code(&self) -> &'static str {
        match self {
            AppError::NoActiveAccount => "no_active_account",
            AppError::Forbidden => "forbidden",
            AppError::NotFound(_) => "not_found",
            AppError::Http(_) => "http",
            AppError::Status { .. } => "status",
            AppError::Ocs(_) => "ocs",
            AppError::App(_) => "app",
            AppError::Json(_) => "json",
            AppError::Io(_) => "io",
            AppError::Keyring(_) => "keyring",
            AppError::Parse(_) => "parse",
            AppError::NotFlutCloud(_) => "not_flutcloud",
            AppError::FlutCloudAppMissing => "flutcloud_app_missing",
            AppError::Update(_) => "update",
            AppError::SyncFolderConflict { .. } => "sync_folder_conflict",
            AppError::TargetExists(_) => "target_exists",
        }
    }

    /// Raw technical detail for the frontend i18n mapping. The English
    /// `message()` stays available for logs/CLI output; the frontend maps
    /// `code` + `detail` to a localized string instead of displaying the raw
    /// English backend text.
    pub fn detail(&self) -> Option<String> {
        match self {
            AppError::NoActiveAccount | AppError::Forbidden => None,
            AppError::NotFound(name) => Some(name.clone()),
            AppError::Http(e) => Some(e.to_string()),
            AppError::Status { status, body } => {
                if body.is_empty() {
                    Some(status.to_string())
                } else {
                    Some(format!("{status}: {body}"))
                }
            }
            AppError::Ocs(msg) => Some(msg.clone()),
            AppError::App(msg) => Some(msg.clone()),
            AppError::Json(e) => Some(e.to_string()),
            AppError::Io(e) => Some(e.to_string()),
            AppError::Keyring(e) => Some(e.clone()),
            AppError::Parse(e) => Some(e.clone()),
            AppError::NotFlutCloud(url) => Some(url.clone()),
            AppError::FlutCloudAppMissing => crate::flutcloud::flutcloud_url().ok(),
            AppError::Update(msg) => Some(msg.clone()),
            AppError::SyncFolderConflict {
                local_path,
                remote_path,
            } => Some(format!("{local_path} ↔ {remote_path}")),
            AppError::TargetExists(path) => Some(path.clone()),
        }
    }

    pub fn message(&self) -> String {
        match self {
            AppError::NoActiveAccount => {
                "No active account. Please add an account first.".to_string()
            }
            AppError::Forbidden => "This operation requires an admin account.".to_string(),
            AppError::NotFound(name) => format!("Account '{}' not found.", name),
            AppError::Http(e) => format!("Network error: {}", e),
            AppError::Status { status, body } => {
                if body.is_empty() {
                    format!("Server returned HTTP {}", status)
                } else {
                    format!("Server returned HTTP {}: {}", status, body)
                }
            }
            AppError::Ocs(msg) => format!("Nextcloud API error: {}", msg),
            AppError::App(msg) => format!("Application error: {}", msg),
            AppError::Json(e) => format!("Invalid JSON response: {}", e),
            AppError::Io(e) => format!("I/O error: {}", e),
            AppError::Keyring(e) => format!("Credential store error: {}", e),
            AppError::Parse(e) => format!("Parse error: {}", e),
            AppError::NotFlutCloud(url) => {
                let server = crate::flutcloud::flutcloud_url()
                    .unwrap_or_else(|_| "the FlutCloud server".to_string());
                format!(
                    "FlutLink is a dedicated client for the FlutCloud server ({}). It does not connect to '{}'.",
                    server, url
                )
            }
            AppError::FlutCloudAppMissing => {
                let server = crate::flutcloud::flutcloud_url()
                    .unwrap_or_else(|_| "the FlutCloud server".to_string());
                format!(
                    "'{}' is not a FlutCloud server: the FlutCloud Nextcloud app is not installed or disabled. Install it from the 'flutcloud-app' folder of the FlutLink repository.",
                    server
                )
            }
            AppError::Update(msg) => format!("Update error: {}", msg),
            AppError::SyncFolderConflict {
                local_path,
                remote_path,
            } => format!(
                "The local folder '{}' is already connected to '{}'. Remove that sync folder first or choose a different local folder.",
                local_path, remote_path
            ),
            AppError::TargetExists(path) => format!(
                "A file or folder named '{}' already exists. Choose a different name.",
                path.rsplit('/').next().unwrap_or(path)
            ),
        }
    }
}

impl Serialize for AppError {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        let mut state = serializer.serialize_struct("AppError", 3)?;
        state.serialize_field("code", &self.code())?;
        state.serialize_field("message", &self.message())?;
        state.serialize_field("detail", &self.detail())?;
        state.end()
    }
}

impl From<reqwest::Error> for AppError {
    fn from(e: reqwest::Error) -> Self {
        AppError::Http(e)
    }
}

impl From<serde_json::Error> for AppError {
    fn from(e: serde_json::Error) -> Self {
        AppError::Json(e)
    }
}

impl From<std::io::Error> for AppError {
    fn from(e: std::io::Error) -> Self {
        AppError::Io(e)
    }
}

pub type AppResult<T> = Result<T, AppError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn target_exists_serializes_with_code_and_name_detail() {
        let err = AppError::TargetExists("/Documents/neu.txt".into());
        assert_eq!(err.code(), "target_exists");
        assert_eq!(err.detail().as_deref(), Some("/Documents/neu.txt"));
        assert!(
            err.message().contains("neu.txt"),
            "message names the conflicting target"
        );
        assert!(
            err.message().to_lowercase().contains("already exists"),
            "message states the conflict"
        );
    }
}
