use clap::Args;
use std::path::PathBuf;

#[derive(Args)]
pub struct DocumentArgs {
    #[command(subcommand)]
    pub action: DocumentAction,
}

#[derive(clap::Subcommand)]
pub enum DocumentAction {
    /// List all documents in a vault
    List {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: String,
    },
    /// Add a document to a vault
    Add {
        /// Path to the encrypted database
        #[arg(short, long)]
        db: PathBuf,
        /// Master key (hex)
        #[arg(short, long)]
        key: String,
        /// Document title
        #[arg(short, long)]
        title: String,
        /// File to import
        #[arg(short, long)]
        file: PathBuf,
    },
}

pub fn run(args: DocumentArgs) -> anyhow::Result<()> {
    match args.action {
        DocumentAction::List { db, key } => {
            let mk = hex::decode(&key)?;
            let conn =
                vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            let docs = vault_native::db::queries::list_documents(&conn)?;
            println!("Documents ({}):", docs.len());
            for doc in &docs {
                let fav = if doc.is_favorite { " *" } else { "" };
                println!(
                    "  {}: {} ({}){}",
                    doc.id, doc.title, doc.mime_type, fav
                );
            }
            Ok(())
        }
        DocumentAction::Add {
            db,
            key,
            title,
            file,
        } => {
            let mk = hex::decode(&key)?;
            let conn =
                vault_native::db::schema::open_encrypted(db.to_str().unwrap(), &mk)?;
            let id = uuid::Uuid::new_v4().to_string();
            let file_bytes = std::fs::read(&file)?;
            let file_name = file
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .to_string();
            let mime = mime_guess::from_path(&file_name)
                .first_or_octet_stream()
                .to_string();
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as i64;

            vault_native::db::queries::add_document(
                &conn,
                &vault_native::db::queries::DocumentRow {
                    id,
                    title,
                    file_name: file_name.clone(),
                    mime_type: mime,
                    file_path: file_name,
                    file_size: file_bytes.len() as i64,
                    page_count: 0,
                    author: String::new(),
                    description: String::new(),
                    thumbnail_path: None,
                    imported_at: now,
                    last_opened_at: now,
                    modified_at: now,
                    is_favorite: false,
                    is_conflict: false,
                    conflict_with: None,
                    collection_id: None,
                    encryption_iv: None,
                    current_page: 0,
                },
            )?;
            println!("Document added");
            Ok(())
        }
    }
}
