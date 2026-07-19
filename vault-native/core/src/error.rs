use thiserror::Error;

#[derive(Error, Debug)]
pub enum Error {
    #[error("Crypto error: {0}")]
    Crypto(String),

    #[error("Format error: {0}")]
    Format(String),

    #[error("KDF error: {0}")]
    Kdf(String),

    #[error("Database error: {0}")]
    Database(#[from] rusqlite::Error),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Invalid data: {0}")]
    InvalidData(String),

    #[error("Wrong password or corrupt backup")]
    AuthenticationFailed,

    #[error("Missing key file: {0}")]
    MissingKey(String),

    #[error("Compression error: {0}")]
    Compression(String),
}

pub type Result<T> = std::result::Result<T, Error>;
