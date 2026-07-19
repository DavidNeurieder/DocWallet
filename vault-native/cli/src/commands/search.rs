use clap::Args;
use std::path::PathBuf;

#[derive(Args)]
pub struct SearchArgs {
    /// Path to the encrypted database
    #[arg(short, long)]
    pub db: PathBuf,
    /// Master key (hex)
    #[arg(short, long)]
    pub key: String,
    /// Search query
    pub query: String,
}

pub fn run(args: SearchArgs) -> anyhow::Result<()> {
    let mk = hex::decode(&args.key)?;
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
            println!("  rowid={} (rank={:.4})", r.rowid, r.rank);
        }
    }
    Ok(())
}
