use serde::{Deserialize, Serialize};

/// Vault manifest: serialised as JSON and stored in the vault container header.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct VaultManifest {
    pub version: u32,
    pub kdf: String,
    pub salt: String,
    #[serde(rename = "argon2Memory")]
    pub argon2_memory: u32,
    #[serde(rename = "argon2Iterations")]
    pub argon2_iterations: u32,
    #[serde(rename = "argon2Parallelism")]
    pub argon2_parallelism: u32,
    #[serde(rename = "documentCount")]
    pub document_count: u32,
}

impl VaultManifest {
    pub fn to_json(&self) -> String {
        serde_json::to_string(self).expect("JSON serialization")
    }

    pub fn from_json(json: &str) -> Option<Self> {
        serde_json::from_str(json).ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_manifest_roundtrip() {
        let m = VaultManifest {
            version: 1,
            kdf: "argon2id".into(),
            salt: "dGVzdC1zYWx0".into(),
            argon2_memory: 19456,
            argon2_iterations: 2,
            argon2_parallelism: 2,
            document_count: 3,
        };
        let json = m.to_json();
        let parsed = VaultManifest::from_json(&json).unwrap();
        assert_eq!(parsed, m);
    }
}
