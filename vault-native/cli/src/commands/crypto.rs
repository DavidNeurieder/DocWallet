use clap::Args;

#[derive(Args)]
pub struct CryptoArgs {
    #[command(subcommand)]
    pub action: CryptoAction,
}

#[derive(clap::Subcommand)]
pub enum CryptoAction {
    /// Generate a random master key
    GenerateMasterKey {
        /// Output file (hex-encoded)
        #[arg(short, long)]
        output: Option<String>,
    },
    /// Derive key using Argon2id
    DeriveKey {
        /// Password
        #[arg(short, long)]
        password: String,
        /// Salt (hex)
        #[arg(short, long)]
        salt: String,
        /// Output file (hex-encoded)
        #[arg(short, long)]
        output: Option<String>,
    },
}

pub fn run(args: CryptoArgs) -> anyhow::Result<()> {
    match args.action {
        CryptoAction::GenerateMasterKey { output } => {
            let key = vault_native::crypto::aes_kw::generate_master_key();
            let hex = hex::encode(&key);
            match output {
                Some(path) => std::fs::write(&path, &hex)?,
                None => println!("{}", hex),
            }
            Ok(())
        }
        CryptoAction::DeriveKey {
            password,
            salt,
            output,
        } => {
            let salt_bytes = hex::decode(&salt)?;
            let key = vault_native::crypto::argon2::derive_key(
                &password,
                &salt_bytes,
                &vault_native::crypto::argon2::Argon2Params::default(),
            )
            .ok_or_else(|| anyhow::anyhow!("key derivation failed"))?;
            let hex = hex::encode(&key);
            match output {
                Some(path) => std::fs::write(&path, &hex)?,
                None => println!("{}", hex),
            }
            Ok(())
        }
    }
}
