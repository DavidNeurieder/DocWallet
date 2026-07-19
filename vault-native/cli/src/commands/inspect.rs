use clap::Args;
use std::path::PathBuf;

#[derive(Args)]
pub struct InspectArgs {
    /// Path to the vault file
    pub vault_path: PathBuf,
}

pub fn run(args: InspectArgs) -> anyhow::Result<()> {
    let data = std::fs::read(&args.vault_path)?;
    let pkg = vault_native::format::package::read(&data)
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
