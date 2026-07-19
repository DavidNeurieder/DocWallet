use crate::format::manifest::VaultManifest;

pub const MAGIC: [u8; 16] = *b"LIBCRATE_VAULT\0\0";
pub const MAGIC_LEN: usize = 16;
pub const VERSION_LEN: usize = 4;
pub const MANIFEST_LEN_LEN: usize = 4;

pub struct VaultPackage {
    pub manifest: VaultManifest,
    pub encrypted_blob: Vec<u8>,
}

pub fn read(data: &[u8]) -> Option<VaultPackage> {
    if data.len() < MAGIC_LEN + VERSION_LEN + MANIFEST_LEN_LEN {
        return None;
    }
    if data[..MAGIC_LEN] != MAGIC {
        return None;
    }
    let _version = u32::from_le_bytes(data[MAGIC_LEN..MAGIC_LEN + VERSION_LEN].try_into().ok()?);
    let manifest_len =
        u32::from_le_bytes(data[MAGIC_LEN + VERSION_LEN..MAGIC_LEN + VERSION_LEN + MANIFEST_LEN_LEN]
            .try_into()
            .ok()?) as usize;

    let manifest_start = MAGIC_LEN + VERSION_LEN + MANIFEST_LEN_LEN;
    let manifest_end = manifest_start + manifest_len;
    if data.len() < manifest_end {
        return None;
    }

    let manifest_str = std::str::from_utf8(&data[manifest_start..manifest_end]).ok()?;
    let manifest = VaultManifest::from_json(manifest_str)?;
    let encrypted_blob = data[manifest_end..].to_vec();

    Some(VaultPackage {
        manifest,
        encrypted_blob,
    })
}

pub fn write(manifest: &VaultManifest, encrypted_blob: &[u8]) -> Vec<u8> {
    let manifest_json = manifest.to_json();
    let manifest_bytes = manifest_json.as_bytes();
    let manifest_len = manifest_bytes.len() as u32;

    let mut out = Vec::with_capacity(
        MAGIC_LEN + VERSION_LEN + MANIFEST_LEN_LEN + manifest_bytes.len() + encrypted_blob.len(),
    );
    out.extend_from_slice(&MAGIC);
    out.extend_from_slice(&1u32.to_le_bytes()); // version
    out.extend_from_slice(&manifest_len.to_le_bytes());
    out.extend_from_slice(manifest_bytes);
    out.extend_from_slice(encrypted_blob);
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::format::manifest::VaultManifest;

    #[test]
    fn test_read_write_roundtrip() {
        let manifest = VaultManifest {
            version: 1,
            kdf: "argon2id".into(),
            salt: "dGVzdA==".into(),
            argon2_memory: 19456,
            argon2_iterations: 2,
            argon2_parallelism: 2,
            document_count: 5,
        };
        let blob = b"encrypted-data-here".to_vec();
        let data = write(&manifest, &blob);
        let pkg = read(&data).unwrap();
        assert_eq!(pkg.manifest, manifest);
        assert_eq!(pkg.encrypted_blob, blob);
    }

    #[test]
    fn test_bad_magic_rejected() {
        let data = b"NOT_THE_MAGIC_BYTES_HERE".to_vec();
        assert!(read(&data).is_none());
    }

    #[test]
    fn test_too_short_rejected() {
        let data = b"short".to_vec();
        assert!(read(&data).is_none());
    }
}
