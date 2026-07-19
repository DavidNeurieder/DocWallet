use clap::Args;
use std::path::PathBuf;

#[derive(Args)]
pub struct SearchArgs {
    /// Path to the encrypted database
    #[arg(short, long)]
    pub db: PathBuf,
    /// Master key (hex)
    #[arg(short, long)]
    pub key: Option<String>,
    /// Password to derive master key from vault dir
    #[arg(short = 'P', long)]
    pub password: Option<String>,
    /// Search query
    pub query: String,
}

pub fn run(args: SearchArgs) -> anyhow::Result<()> {
    let mk = crate::commands::password::resolve_master_key(
        &args.db.parent().and_then(|p| p.parent()).unwrap_or(&args.db),
        args.key.as_deref(),
        args.password.as_deref(),
    )?;
    let conn = vault_native::db::schema::open_encrypted(
        args.db.to_str().unwrap(),
        &mk,
    )?;
    let results = vault_native::db::fts::search(&conn, &args.query)?;
    if results.is_empty() {
        println!("No results found");
    } else {
        println!("Results ({}):", results.len());
        for r in &results {
            println!("  {}: {} (rank={:.4})", r.id, r.title, r.rank);
        }
    }
    Ok(())
}
