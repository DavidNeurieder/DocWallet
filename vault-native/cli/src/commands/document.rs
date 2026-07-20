use clap::Args;
use std::path::PathBuf;
use vault_native::db::storage;

#[derive(Args)]
pub struct DocumentArgs {
    #[command(subcommand)]
    pub action: DocumentAction,
}

#[derive(clap::Subcommand)]
pub enum DocumentAction {
    /// List all documents
    List {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: Option<String>,
        /// Password to derive master key from vault dir
        #[arg(short = 'P', long)]
        password: Option<String>,
    },
    /// Get a document by ID
    Get {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: Option<String>,
        /// Password to derive master key from vault dir
        #[arg(short = 'P', long)]
        password: Option<String>,
        /// Document ID
        id: String,
        /// Export file blob to this path
        #[arg(short, long)]
        export: Option<PathBuf>,
    },
    /// Add a document (stores blob in files/)
    Add {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: Option<String>,
        /// Password to derive master key from vault dir
        #[arg(short = 'P', long)]
        password: Option<String>,
        /// Title
        #[arg(short, long)]
        title: String,
        /// File to import
        #[arg(short, long)]
        file: PathBuf,
        /// Vault root (for files/ storage)
        #[arg(short = 'V', long)]
        vault: PathBuf,
    },
    /// Update a document's title and/or favorite status
    Update {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: Option<String>,
        /// Password to derive master key from vault dir
        #[arg(short = 'P', long)]
        password: Option<String>,
        /// Document ID
        id: String,
        /// New title
        #[arg(short, long)]
        title: Option<String>,
        /// Set favorite
        #[arg(short, long)]
        favorite: Option<bool>,
    },
    /// Delete a document by ID (removes blob + DB + FTS entry)
    Delete {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: Option<String>,
        /// Password to derive master key from vault dir
        #[arg(short = 'P', long)]
        password: Option<String>,
        /// Document ID
        id: String,
        /// Vault root (for files/ removal)
        #[arg(short = 'V', long)]
        vault: PathBuf,
    },
}

fn resolve_mk(db: &PathBuf, key: &Option<String>, password: &Option<String>) -> anyhow::Result<Vec<u8>> {
    crate::commands::password::resolve_master_key(
        &db.parent().and_then(|p| p.parent()).unwrap_or(db),
        key.as_deref(),
        password.as_deref(),
    )
}

pub fn run(args: DocumentArgs) -> anyhow::Result<()> {
    match args.action {
        DocumentAction::List { db, key, password } => {
            let mk = resolve_mk(&db, &key, &password)?;
            let conn = vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            let docs = vault_native::db::queries::list_documents(&conn)?;
            println!("Documents ({}):", docs.len());
            for doc in &docs {
                let fav = if doc.is_favorite { " *" } else { "" };
                println!("  {}: {} ({}){}", doc.id, doc.title, doc.mime_type, fav);
            }
            Ok(())
        }
        DocumentAction::Get {
            db,
            key,
            password,
            id,
            export,
        } => {
            let mk = resolve_mk(&db, &key, &password)?;
            let conn = vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            match vault_native::db::queries::get_document(&conn, &id)? {
                Some(doc) => {
                    if let Some(export_path) = export {
                        let vault_root = db.parent().and_then(|p| p.parent()).unwrap_or(std::path::Path::new("."));
                        let data = storage::export_document_file(&conn, &vault_root, &doc.id, Some(&mk))
                            .ok_or_else(|| anyhow::anyhow!("File blob not found for document '{}'", doc.id))?;
                        std::fs::write(&export_path, data)?;
                        println!("Blob exported to {}", export_path.display());
                    } else {
                        println!("ID:          {}", doc.id);
                        println!("Title:       {}", doc.title);
                        println!("File name:   {}", doc.file_name);
                        println!("MIME type:   {}", doc.mime_type);
                        println!("File path:   {}", doc.file_path);
                        println!("File size:   {}", doc.file_size);
                        println!("Page count:  {}", doc.page_count);
                        println!("Author:      {}", doc.author);
                        println!("Favorite:    {}", doc.is_favorite);
                        println!("Conflict:    {}", doc.is_conflict);
                        println!("Modified at: {}", doc.modified_at);
                    }
                    Ok(())
                }
                None => {
                    eprintln!("Document '{}' not found", id);
                    std::process::exit(1);
                }
            }
        }
        DocumentAction::Add {
            db,
            key,
            password,
            title,
            file,
            vault,
        } => {
            let mk = resolve_mk(&db, &key, &password)?;
            let conn = vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            let file_bytes = std::fs::read(&file)?;
            let file_name = file.file_name().unwrap_or_default().to_string_lossy().to_string();
            let mime = mime_guess::from_path(&file_name)
                .first_or_octet_stream()
                .to_string();
            let id = uuid::Uuid::new_v4().to_string();
            let text_content = String::from_utf8_lossy(&file_bytes);

            storage::import_document(
                &conn, &vault, &id, &title,
                &file_bytes, &mime, "", "",
                Some(&text_content),
                Some(&mk),
            )?;
            println!("Document added with id: {}", id);
            Ok(())
        }
        DocumentAction::Update {
            db,
            key,
            password,
            id,
            title,
            favorite,
        } => {
            let mk = resolve_mk(&db, &key, &password)?;
            let conn = vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            let existing = vault_native::db::queries::get_document(&conn, &id)?
                .ok_or_else(|| anyhow::anyhow!("Document '{}' not found", id))?;
            let new_title = title.unwrap_or(existing.title);
            let new_fav = favorite.unwrap_or(existing.is_favorite);
            vault_native::db::queries::update_document(&conn, &id, &new_title, new_fav)?;
            println!("Document updated");
            Ok(())
        }
        DocumentAction::Delete {
            db,
            key,
            password,
            id,
            vault,
        } => {
            let mk = resolve_mk(&db, &key, &password)?;
            let conn = vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            if storage::delete_document_full(&conn, &vault, &id)? {
                println!("Document deleted");
            } else {
                eprintln!("Document '{}' not found", id);
                std::process::exit(1);
            }
            Ok(())
        }
    }
}
