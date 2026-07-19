use clap::Args;
use std::path::PathBuf;

#[derive(Args)]
pub struct VaultArgs {
    #[command(subcommand)]
    pub action: VaultAction,
}

#[derive(clap::Subcommand)]
pub enum VaultAction {
    /// Bootstrap a new vault directory structure
    Init {
        /// Directory to create the vault in
        dir: PathBuf,
        /// Password for key derivation
        #[arg(short, long)]
        password: String,
    },
}

pub fn run(args: VaultArgs) -> anyhow::Result<()> {
    match args.action {
        VaultAction::Init { dir, password } => {
            let mk = vault_native::format::export::create_vault_layout(&dir, &password)?;
            println!("Vault initialized at {}", dir.display());
            println!("Master key (hex): {}", hex::encode(&mk));
            println!("Save this key for direct DB access (without --password)");
            Ok(())
        }
    }
}
