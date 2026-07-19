# Unencrypted File Blobs in `files/<id>`

**Status:** Accepted design choice (not a bug)  
**Discovered:** 2026-07-19  
**Component:** `vault-native/core/src/db/storage.rs`, CLI `document add`

## Description

The `storage::import_document` function stores file blobs at `files/<id>` on disk as **plain unencrypted byte-for-byte copies** of the input file. The `documents.encryption_iv` column is populated with a random 12-byte value, but no actual encryption is ever performed — the IV is a dead field retained only for schema compatibility with the Android app.

This means anyone with filesystem access to the vault directory can read all document contents directly, even without the SQLCipher master key.

## Root Cause

The design deliberately mirrors the Android LibreCrate app's actual behavior: the app stores file blobs unencrypted at `files/<id>` and treats `encryption_iv` as a throwaway column. The Rust CLI aimed for vault interop (create a vault that the Android app can open), so it copied the same (non-)pattern.

## Impact

| Severity | Scope |
|----------|-------|
| High     | Any process with filesystem read access to the vault directory can recover all document contents without authentication. The SQLCipher-encrypted DB protects metadata only — the actual payloads are in the clear. |

## Reproduction

```
librecrate vault init -p secret /tmp/vault
echo "sensitive content" > /tmp/doc.txt
librecrate document add -P secret -V /tmp/vault -t "Secret" -f /tmp/doc.txt -d /tmp/vault/databases/librecrate.db
cat /tmp/vault/files/<uuid>   # plaintext visible
```

## Options

### A. Accept as-is (current behavior)
- Rationale: byte-identical vaults with the Android app; the app does the same.
- Cost: files are plaintext on disk.

### B. Encrypt blobs with master key
- Encrypt each blob with AES-256-GCM using the master key before writing to `files/<id>`. Store the real IV (not random garbage) in `encryption_iv`. Decrypt on read.
- Effect: Rust vaults diverge from Android app vaults — the app would not be able to read `files/<id>` written by the CLI and vice versa.
- Requires: changes to `load_file`, `save_file`, `import_document`, `export_document_file` in `storage.rs`; CLI `document get --export` would export decrypted plaintext.

### C. Use per-file key wrapping
- Generate a random file encryption key per blob, wrap it with the master key, store alongside the blob. Similar to how SQLCipher encrypts the DB.
- Even more divergence from Android, more complexity for marginal gain vs. option B.

## Recommendation

**Accept as-is (Option A)** — the vault format is meant to be a bag of files with an encrypted index. Full-disk encryption at the filesystem layer (eCryptfs, LUKS, APFS) is the standard deployment expectation. Adding per-blob encryption would break Android app interop and add complexity without meaningfully improving the security model for this use case.

If per-blob encryption is desired later, it should be a **new vault format version** with a `version: 2` manifest field, not a silent change to the current format.
