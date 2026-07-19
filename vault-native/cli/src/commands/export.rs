use clap::Args;
use std::path::PathBuf;
use vault_native::crypto::argon2::Argon2Params;

#[derive(Args)]
pub struct ExportArgs {
    /// Vault file to export
    pub vault_path: PathBuf,
    /// Password
    #[arg(short, long)]
    pub password: String,
    /// Output directory for extracted plaintext files
    #[arg(short, long)]
    pub output: PathBuf,
}

pub fn run(args: ExportArgs) -> anyhow::Result<()> {
    let data = std::fs::read(&args.vault_path)?;
    let kdf = Argon2Params::default();
    let contents =
        vault_native::format::import::import(&data, &args.password, &kdf)?;

    crate::commands::util::write_contents(&args.output, &contents)?;
    println!("Vault exported to {}", args.output.display());
    Ok(())
}
