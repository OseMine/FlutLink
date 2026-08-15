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
    /// Two sync folders of one account target the same remote folder, which
    /// would overwrite each other's data.
    SyncFolderConflict {
        local_path: String,
        remote_path: String,
    },
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
            AppError::SyncFolderConflict { .. } => "sync_folder_conflict",
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
            AppError::SyncFolderConflict {
                local_path,
                remote_path,
            } => format!(
                "The local folder '{}' is already connected to '{}'. Remove that sync folder first or choose a different local folder.",
                local_path, remote_path
            ),
        }
    }
}

impl Serialize for AppError {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        let mut state = serializer.serialize_struct("AppError", 2)?;
        state.serialize_field("code", &self.code())?;
        state.serialize_field("message", &self.message())?;
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
