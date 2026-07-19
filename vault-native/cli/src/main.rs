use clap::{Parser, Subcommand};

mod commands;

#[derive(Parser)]
#[command(name = "librecrate", about = "LibreCrate CLI")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Vault file operations
    Vault(commands::vault::VaultArgs),
    /// Cryptographic operations
    Crypto(commands::crypto::CryptoArgs),
    /// Document management operations
    Document(commands::document::DocumentArgs),
    /// Full-text search operations
    Search(commands::search::SearchArgs),
    /// Performance benchmarks
    Bench(commands::bench::BenchArgs),
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    match cli.command {
        Commands::Vault(args) => commands::vault::run(args),
        Commands::Crypto(args) => commands::crypto::run(args),
        Commands::Document(args) => commands::document::run(args),
        Commands::Search(args) => commands::search::run(args),
        Commands::Bench(args) => commands::bench::run(args),
    }
}
