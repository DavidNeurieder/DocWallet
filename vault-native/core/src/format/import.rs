use base64::Engine;
use crate::crypto::aes_gcm;
use crate::crypto::argon2::{self, Argon2Params};
use crate::error::{Error, Result};
use crate::format::package;
use std::io::Read;
use zip::ZipArchive;

pub struct ImportedContents {
    pub keys: Vec<(String, Vec<u8>)>,
    pub db_file: Option<Vec<u8>>,
    pub files: Vec<(String, Vec<u8>)>,
}

pub fn import(vault_data: &[u8], vault_password: &str, _kdf_params: &Argon2Params) -> Result<ImportedContents> {
    let pkg = package::read(vault_data).ok_or(Error::Format("invalid vault format".into()))?;

    let salt_bytes = base64::engine::general_purpose::STANDARD
        .decode(&pkg.manifest.salt)
    .map_err(|_| Error::InvalidData("invalid base64 salt in manifest".into()))?;

    let container_key = argon2::derive_key(
        vault_password,
        &salt_bytes,
        &Argon2Params::new(
            pkg.manifest.argon2_memory,
            pkg.manifest.argon2_iterations,
            pkg.manifest.argon2_parallelism,
            32,
        ),
    )
    .ok_or(Error::AuthenticationFailed)?;

    // Split IV + ciphertext
    if pkg.encrypted_blob.len() < aes_gcm::IV_LENGTH {
        return Err(Error::InvalidData("encrypted blob too short".into()));
    }
    let iv = &pkg.encrypted_blob[..aes_gcm::IV_LENGTH];
    let ciphertext = &pkg.encrypted_blob[aes_gcm::IV_LENGTH..];
    let plain_zip = aes_gcm::decrypt_bytes(ciphertext, &container_key, iv)
        .ok_or(Error::AuthenticationFailed)?;

    extract_zip(&plain_zip)
}

fn extract_zip(data: &[u8]) -> Result<ImportedContents> {
    let cursor = std::io::Cursor::new(data);
    let mut archive =
        ZipArchive::new(cursor).map_err(|e| Error::Compression(e.to_string()))?;

    let mut keys = Vec::new();
    let mut db_file = None;
    let mut files = Vec::new();

    for i in 0..archive.len() {
        let mut entry = archive
            .by_index(i)
            .map_err(|e| Error::Compression(e.to_string()))?;
        let name = entry.name().to_string();
        let mut content = Vec::new();
        entry
            .read_to_end(&mut content)
            .map_err(|e| Error::Compression(e.to_string()))?;

        if let Some(stripped) = name.strip_prefix("keys/") {
            keys.push((stripped.to_string(), content));
        } else if name == "db/librecrate.db" {
            db_file = Some(content);
        } else if let Some(stripped) = name.strip_prefix("files/") {
            files.push((stripped.to_string(), content));
        }
    }

    Ok(ImportedContents { keys, db_file, files })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::argon2::Argon2Params;
    use crate::format::export;

    #[test]
    fn test_import_rejects_wrong_password() {
        let password = "correct-pw";
        let kdf = Argon2Params::default();
        let exported = export::export(
            &[],
            None,
            password,
            &[],
            &kdf,
        )
        .unwrap();

        let result = import(&exported.data, "wrong-pw", &kdf);
        assert!(result.is_err());
    }

    #[test]
    fn test_import_empty_vault() {
        let password = "empty-test";
        let kdf = Argon2Params::default();
        let exported = export::export(&[], None, password, &[], &kdf).unwrap();
        let contents = import(&exported.data, password, &kdf).unwrap();
        assert!(contents.keys.is_empty());
        assert!(contents.db_file.is_none());
        assert!(contents.files.is_empty());
    }
}
