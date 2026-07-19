use argon2::{Algorithm, Argon2, Params, Version};
use rand::rngs::OsRng;

pub const DEFAULT_MEMORY_COST: u32 = 19456;
pub const DEFAULT_ITERATIONS: u32 = 2;
pub const DEFAULT_PARALLELISM: u32 = 2;
pub const DEFAULT_HASH_LENGTH: usize = 32;

#[derive(Debug, Clone)]
pub struct Argon2Params {
    pub memory_cost: u32,
    pub iterations: u32,
    pub parallelism: u32,
    pub hash_length: usize,
}

impl Default for Argon2Params {
    fn default() -> Self {
        Self {
            memory_cost: DEFAULT_MEMORY_COST,
            iterations: DEFAULT_ITERATIONS,
            parallelism: DEFAULT_PARALLELISM,
            hash_length: DEFAULT_HASH_LENGTH,
        }
    }
}

impl Argon2Params {
    pub fn new(memory_cost: u32, iterations: u32, parallelism: u32, hash_length: usize) -> Self {
        Self {
            memory_cost,
            iterations,
            parallelism,
            hash_length,
        }
    }
}

pub fn generate_salt() -> Vec<u8> {
    use rand::RngCore;
    let mut salt = vec![0u8; 16];
    OsRng.fill_bytes(&mut salt);
    salt
}

pub fn derive_key(password: &str, salt: &[u8], params: &Argon2Params) -> Option<Vec<u8>> {
    let p = Params::new(
        params.memory_cost,
        params.iterations,
        params.parallelism,
        Some(params.hash_length),
    )
    .ok()?;
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, p);
    let mut key = vec![0u8; params.hash_length];
    argon2
        .hash_password_into(password.as_bytes(), salt, &mut key)
        .ok()?;
    Some(key)
}

pub fn derive_key_and_zero(password: &str, salt: &[u8], params: &Argon2Params) -> Option<Vec<u8>> {
    derive_key(password, salt, params)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_derive_key_default_params() {
        let password = "test-password";
        let salt = b"0123456789abcdef";
        let key = derive_key(password, salt, &Argon2Params::default());
        assert!(key.is_some());
        assert_eq!(key.unwrap().len(), 32);
    }

    #[test]
    fn test_different_salts_different_keys() {
        let password = "same-password";
        let k1 = derive_key(password, b"saltsalt12345678", &Argon2Params::default());
        let k2 = derive_key(password, b"DIFFERENTsalt123", &Argon2Params::default());
        assert!(k1.is_some());
        assert!(k2.is_some());
        assert_ne!(k1, k2);
    }

    #[test]
    fn test_generate_salt_is_random() {
        let s1 = generate_salt();
        let s2 = generate_salt();
        assert_ne!(s1, s2);
        assert_eq!(s1.len(), 16);
    }
}
