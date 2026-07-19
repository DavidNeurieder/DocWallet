use std::path::Path;
use vault_native::crypto::argon2::Argon2Params;
use vault_native::kdf;

/// Resolve the master key from either a hex key string or a vault directory + password.
/// When `key_hex` is `Some`, it's decoded directly.
/// When `password` is `Some`, the vault dir's `encryption/` folder is read and the key derived.
pub fn resolve_master_key(
    vault_dir: &Path,
    key_hex: Option<&str>,
    password: Option<&str>,
) -> anyhow::Result<Vec<u8>> {
    match (key_hex, password) {
        (Some(kh), _) => Ok(hex::decode(kh)?),
        (_, Some(pw)) => {
            let salt = std::fs::read(vault_dir.join("encryption").join("salt"))?;
            let wrapped = std::fs::read(vault_dir.join("encryption").join("wrapped_master_key"))?;
            let params = Argon2Params::default();
            let mk = kdf::derive_backup_master_key(&wrapped, pw, &salt, &params)?;
            Ok(mk)
        }
        (None, None) => anyhow::bail!("Either --key or --password must be provided"),
    }
}
