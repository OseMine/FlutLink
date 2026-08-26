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
    /// The FlutCloud app is installed but too old for guest access.
    FlutCloudAppTooOld,
    Update(String),
    /// The destination already exists on the server and the operation refused
    /// to overwrite it — either an upload without an `overwrite` opt-in or a
    /// rename/move rejected via WebDAV `Overwrite: F` → 412.
    TargetExists(String),
    /// Two sync folders of one account target the same remote folder, which
    /// would overwrite each other's data.
    SyncFolderConflict {
        local_path: String,
        remote_path: String,
    },
}

impl AppError {
    /// Sabre DAV answers impersonation/browse requests for a user that does
    /// not exist on the server with `404` + "Principal with name X not found".
    /// Extract the principal name so the error can be surfaced as a clean,
    /// localized "user not found" message instead of the raw XML body.
    pub fn principal_not_found(&self) -> Option<String> {
        let AppError::Status { status, body } = self else {
            return None;
        };
        if *status != 404 {
            return None;
        }
        let marker = "Principal with name ";
        let start = body.find(marker)? + marker.len();
        let rest = &body[start..];
        let end = rest.find(" not found")?;
        Some(rest[..end].trim_matches('\'').trim_matches('"').to_string())
    }

    pub fn code(&self) -> &'static str {
        match self {
            AppError::NoActiveAccount => "no_active_account",
            AppError::Forbidden => "forbidden",
            AppError::NotFound(_) => "not_found",
            AppError::Http(_) => "http",
            AppError::Status { .. } => {
                if self.principal_not_found().is_some() {
                    "principal_not_found"
                } else {
                    "status"
                }
            }
            AppError::Ocs(_) => "ocs",
            AppError::App(_) => "app",
            AppError::Json(_) => "json",
            AppError::Io(_) => "io",
            AppError::Keyring(_) => "keyring",
            AppError::Parse(_) => "parse",
            AppError::NotFlutCloud(_) => "not_flutcloud",
            AppError::FlutCloudAppMissing => "flutcloud_app_missing",
            AppError::FlutCloudAppTooOld => "flutcloud_app_too_old",
            AppError::Update(_) => "update",
            AppError::TargetExists(_) => "target_exists",
            AppError::SyncFolderConflict { .. } => "sync_folder_conflict",
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
                if let Some(user) = self.principal_not_found() {
                    return Some(user);
                }
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
            AppError::FlutCloudAppTooOld => crate::flutcloud::flutcloud_url().ok(),
            AppError::Update(msg) => Some(msg.clone()),
            AppError::TargetExists(path) => Some(path.clone()),
            AppError::SyncFolderConflict {
                local_path,
                remote_path,
            } => Some(format!("{local_path} ↔ {remote_path}")),
        }
    }

    /// True when the failure is a network-level error (server unreachable),
    /// used to decide whether the caller may fall back to the offline cache.
    pub fn is_network(&self) -> bool {
        matches!(self, AppError::Http(_))
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
                if let Some(user) = self.principal_not_found() {
                    return format!("User '{}' does not exist on the server.", user);
                }
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
            AppError::FlutCloudAppTooOld => {
                let server = crate::flutcloud::flutcloud_url()
                    .unwrap_or_else(|_| "the FlutCloud server".to_string());
                format!(
                    "'{}' is not a FlutCloud server: the FlutCloud Nextcloud app is too old for guest access. Please update the FlutCloud app to the required version.",
                    server
                )
            }
            AppError::Update(msg) => format!("Update error: {}", msg),
            AppError::TargetExists(path) => format!(
                "A file or folder named '{}' already exists on the server. Choose a different name.",
                path.rsplit('/').next().unwrap_or(path)
            ),
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

    #[test]
    fn target_exists_serializes_with_code() {
        let err = AppError::TargetExists("/Documents/report.pdf".into());
        assert_eq!(err.code(), "target_exists");
        assert_eq!(err.detail().as_deref(), Some("/Documents/report.pdf"));
        let json = serde_json::to_string(&err).expect("serializable");
        assert!(json.contains("\"code\":\"target_exists\""));
        assert!(json.contains("\"detail\":\"/Documents/report.pdf\""));
    }

    /// Impersonating a non-existent user surfaces as Sabre's principal-404;
    /// it must map to `principal_not_found` with the username as detail so
    /// the frontend can show a localized message instead of the raw XML.
    #[test]
    fn sabre_principal_404_maps_to_principal_not_found() {
        let body = r#"<?xml version="1.0" encoding="utf-8"?>
<d:error xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
<s:exception>Sabre\DAV\Exception\NotFound</s:exception>
<s:message>Principal with name dsaas not found</s:message>
</d:error>"#;
        let err = AppError::Status {
            status: 404,
            body: body.into(),
        };
        assert_eq!(err.principal_not_found().as_deref(), Some("dsaas"));
        assert_eq!(err.code(), "principal_not_found");
        assert_eq!(err.detail().as_deref(), Some("dsaas"));
        assert!(
            err.message().contains("dsaas"),
            "message names the missing user"
        );
        let json = serde_json::to_string(&err).expect("serializable");
        assert!(json.contains("\"code\":\"principal_not_found\""));
        assert!(json.contains("\"detail\":\"dsaas\""));
    }

    #[test]
    fn other_status_errors_keep_the_status_code_path() {
        let err = AppError::Status {
            status: 404,
            body: "<x>not a principal</x>".into(),
        };
        assert_eq!(err.principal_not_found(), None);
        assert_eq!(err.code(), "status");

        let err = AppError::Status {
            status: 500,
            body: "Principal with name boom not found".into(),
        };
        assert_eq!(err.principal_not_found(), None);
        assert_eq!(err.code(), "status");
    }
}
