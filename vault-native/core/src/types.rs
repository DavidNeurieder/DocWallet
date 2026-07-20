#[derive(Debug, Clone, uniffi::Record)]
pub struct KeyValue {
    pub key: String,
    pub value: Vec<u8>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct EncryptedData {
    pub iv: Vec<u8>,
    pub ciphertext: Vec<u8>,
}
