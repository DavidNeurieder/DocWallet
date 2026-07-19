use clap::Args;
use std::path::PathBuf;
use vault_native::crypto::argon2::Argon2Params;

#[derive(Args)]
pub struct VaultArgs {
    #[command(subcommand)]
    pub action: VaultAction,
}

#[derive(clap::Subcommand)]
pub enum VaultAction {
    /// Inspect a vault file and print its manifest
    Inspect {
        /// Path to the vault file
        vault_path: PathBuf,
    },
    /// Create a new vault file from source directory (expects encryption/, databases/, files/ subdirs)
    Create {
        /// Output vault path
        #[arg(short, long)]
        output: PathBuf,
        /// Password for encryption
        #[arg(short, long)]
        password: String,
        /// Source directory containing encryption/, databases/, files/
        source: PathBuf,
    },
    /// Export a vault from explicit paths
    Export {
        /// Output vault path
        #[arg(short, long)]
        output: PathBuf,
        /// Password
        #[arg(short, long)]
        password: String,
        /// Encryption key directory
        #[arg(short, long)]
        enc_dir: PathBuf,
        /// Database directory
        #[arg(short, long)]
        db_dir: PathBuf,
        /// Files directory
        #[arg(short, long)]
        files_dir: PathBuf,
    },
    /// Import a vault file to a directory
    Import {
        /// Vault file to import
        vault_path: PathBuf,
        /// Password
        #[arg(short, long)]
        password: String,
        /// Output directory for extracted contents
        output: PathBuf,
    },
}

fn read_dir_files(dir: &PathBuf) -> anyhow::Result<Vec<(String, Vec<u8>)>> {
    let mut entries = Vec::new();
    if dir.exists() {
        for entry in std::fs::read_dir(dir)? {
            let entry = entry?;
            let path = entry.path();
            if path.is_file() {
                let name = path.file_name().unwrap().to_string_lossy().to_string();
                let data = std::fs::read(&path)?;
                entries.push((name, data));
            }
        }
    }
    Ok(entries)
}

pub fn run(args: VaultArgs) -> anyhow::Result<()> {
    match args.action {
        VaultAction::Inspect { vault_path } => {
            let data = std::fs::read(&vault_path)?;
            let pkg =
                vault_native::format::package::read(&data)
                    .ok_or_else(|| anyhow::anyhow!("Invalid vault file"))?;
            let m = &pkg.manifest;
            println!("LibreCrate Vault Manifest");
            println!("  Version:       {}", m.version);
            println!("  KDF:           {}", m.kdf);
            println!("  Salt (b64):    {}", m.salt);
            println!("  Argon2 memory: {}", m.argon2_memory);
            println!("  Argon2 iters:  {}", m.argon2_iterations);
            println!("  Argon2 parall: {}", m.argon2_parallelism);
            println!("  Documents:     {}", m.document_count);
            println!("  Blob size:     {} bytes", pkg.encrypted_blob.len());
            Ok(())
        }
        VaultAction::Create {
            output,
            password,
            source,
        } => {
            let enc_dir = source.join("encryption");
            let db_dir = source.join("databases");
            let files_dir = source.join("files");
            do_export(&password, &enc_dir, &db_dir, &files_dir, &output)
        }
        VaultAction::Export {
            output,
            password,
            enc_dir,
            db_dir,
            files_dir,
        } => do_export(&password, &enc_dir, &db_dir, &files_dir, &output),
        VaultAction::Import {
            vault_path,
            password,
            output,
        } => {
            let data = std::fs::read(&vault_path)?;
            let kdf_params = Argon2Params::default();
            let contents =
                vault_native::format::import::import(&data, &password, &kdf_params)?;

            std::fs::create_dir_all(output.join("encryption"))?;
            std::fs::create_dir_all(output.join("databases"))?;
            std::fs::create_dir_all(output.join("files"))?;

            for (name, key_data) in &contents.keys {
                std::fs::write(output.join("encryption").join(name), key_data)?;
            }
            if let Some(db) = &contents.db_file {
                std::fs::write(output.join("databases").join("librecrate.db"), db)?;
            }
            for (name, file_data) in &contents.files {
                let path = output.join("files").join(name);
                if let Some(parent) = path.parent() {
                    std::fs::create_dir_all(parent)?;
                }
                std::fs::write(path, file_data)?;
            }
            println!("Vault imported to {}", output.display());
            Ok(())
        }
    }
}

fn do_export(
    password: &str,
    enc_dir: &PathBuf,
    db_dir: &PathBuf,
    files_dir: &PathBuf,
    output: &PathBuf,
) -> anyhow::Result<()> {
    let keys = read_dir_files(enc_dir)?;
    let db_file = {
        let db_path = db_dir.join("librecrate.db");
        if db_path.exists() {
            Some(std::fs::read(&db_path)?)
        } else {
            None
        }
    };
    let files = read_dir_files(files_dir)?;
    let kdf_params = Argon2Params::default();
    let exported = vault_native::format::export::export(
        &files,
        db_file.as_deref(),
        password,
        &keys,
        &kdf_params,
    )?;
    std::fs::write(output, &exported.data)?;
    println!("Vault written to {}", output.display());
    Ok(())
}
