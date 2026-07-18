# LibreCrate CLI Usage

## Setup

Build the fat jar (one-time, auto-triggered by the wrapper):

    ./gradlew :vault-cli:shadowJar

Output: `vault-cli/build/libs/librecrate.jar`

### Run via the wrapper

    vault-cli/bin/librecrate vault --help

### Install on PATH (optional)

    ln -s "$PWD/vault-cli/bin/librecrate" ~/.local/bin/librecrate

Now `librecrate` works from anywhere.

---

## Command reference

    librecrate vault       Backup/restore/merge .vault files
    librecrate crypto      Cryptographic operations
    librecrate document    Document management
    librecrate search      Full-text search (FTS5)
    librecrate bench       Performance benchmarks

### `vault` subcommands

| Command | Description |
|---------|-------------|
| `inspect -i FILE` | Show vault manifest (version, KDF params, doc count) |
| `export -p PASS -d DIR -o FILE [--db DB]` | Export directory + optional SQLite DB to a .vault file |
| `extract -p PASS -i FILE -d DIR` | Extract a .vault file back to a directory (raw) |
| `import -p PASS -i FILE -d DIR [--use-shared]` | Import a .vault; `--use-shared` enables full restore (password check, DB merge, file decryption) |
| `merge -p PASS -i FILE -d DB` | Merge a .vault's DB contents into an existing SQLite DB |
| `roundtrip -p PASS -d DIR` | Export then re-extract and verify data matches |

### `crypto` subcommands

    kdf         Derive key from password (Argon2id)
    encrypt     AES-256-GCM encrypt
    decrypt     AES-256-GCM decrypt
    keywrap     AES-KW (RFC 3394) key wrap
    keyunwrap   AES-KW key unwrap

### `document` subcommands

    add     Import a document into a vault DB
    list    List documents
    get     Show details or export file
    delete  Delete a document

### `search` subcommands

    init    Create FTS5 search index
    add     Add document to index
    query   Search documents

### `bench` subcommands

    kdf       Benchmark Argon2id key derivation
    encrypt   Benchmark AES-256-GCM encryption

---

## Common workflows

### Inspect a backup

    librecrate vault inspect -i backup.vault

### Export a directory (plain files) to a vault

    librecrate vault export -p mypassword -d ./mydir -o backup.vault --db ./mydir/librecrate.db

### Restore an Android-app vault onto the CLI

    librecrate vault import -p mypassword -i backup.vault -d ~/restored --use-shared

Creates `~/restored/{databases,encryption,files}`. The `--use-shared` flag drives the same `BackupRestoreService` the app uses (password verification, key unwrap, Branch A/B/C merge).

### Merge a vault into an existing database

    librecrate vault merge -p mypassword -i backup.vault -d ~/existing/librecrate.db

### Verify export roundtrip

    librecrate vault roundtrip -p test -d ./source-dir

Reports PASS if every file exported and re-extracted matches the original.

---

## Requirements

- Java 17+ runtime (JRE)
- The fat jar bundles all dependencies (SQLCipher via willena/sqlite-jdbc, BouncyCastle, kotlinx-serialization)
