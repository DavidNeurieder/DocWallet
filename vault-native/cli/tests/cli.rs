use std::path::Path;
use std::process::Command;

fn librecrate_bin() -> std::path::PathBuf {
    Path::new(env!("CARGO_BIN_EXE_librecrate")).to_path_buf()
}

/// Create a temp dir with sample files for testing.
fn create_sample_dir(_name: &str, files: &[(&str, &str)]) -> tempfile::TempDir {
    let dir = tempfile::TempDir::new().unwrap();
    for (rel, content) in files {
        let path = dir.path().join(rel);
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).unwrap();
        }
        std::fs::write(&path, content).unwrap();
    }
    dir
}

#[test]
fn test_create_and_inspect() {
    let src = create_sample_dir(
        "src",
        &[("doc1.txt", "hello world"), ("doc2.txt", "second file")],
    );
    let vault_path = src.path().join("out.vault");

    let output = Command::new(librecrate_bin())
        .args([
            "create",
            src.path().to_str().unwrap(),
            "-p",
            "test-pw",
            "-o",
            vault_path.to_str().unwrap(),
        ])
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "create failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    assert!(vault_path.exists());

    // Inspect the vault
    let inh = Command::new(librecrate_bin())
        .args(["inspect", vault_path.to_str().unwrap()])
        .output()
        .unwrap();
    assert!(inh.status.success());
    let stdout = String::from_utf8_lossy(&inh.stdout);
    assert!(stdout.contains("Documents:     2"));
}

#[test]
fn test_create_export_roundtrip() {
    let content_a = "content of A";
    let content_b = "content of B";
    let src = create_sample_dir(
        "src",
        &[("a.txt", content_a), ("sub/b.txt", content_b)],
    );
    let vault_path = src.path().join("out.vault");
    let export_dir = src.path().join("exported");

    // Create
    let create_out = Command::new(librecrate_bin())
        .args([
            "create",
            src.path().to_str().unwrap(),
            "-p",
            "pw",
            "-o",
            vault_path.to_str().unwrap(),
        ])
        .output()
        .unwrap();
    assert!(
        create_out.status.success(),
        "create: {}",
        String::from_utf8_lossy(&create_out.stderr)
    );

    // Export
    let export_out = Command::new(librecrate_bin())
        .args([
            "export",
            vault_path.to_str().unwrap(),
            "-p",
            "pw",
            "-o",
            export_dir.to_str().unwrap(),
        ])
        .output()
        .unwrap();
    assert!(
        export_out.status.success(),
        "export: {}",
        String::from_utf8_lossy(&export_out.stderr)
    );

    // Verify structure
    assert!(export_dir.join("encryption").join("salt").exists());
    assert!(export_dir.join("encryption").join("wrapped_master_key").exists());
    assert!(export_dir.join("databases").join("librecrate.db").exists());

    // Count files in files/
    let files: Vec<_> = std::fs::read_dir(export_dir.join("files"))
        .unwrap()
        .filter_map(|e| e.ok())
        .collect();
    assert_eq!(files.len(), 2, "expected 2 file blobs");
}

#[test]
fn test_merge_two_vaults() {
    let src_a = create_sample_dir("a", &[("a.txt", "from A")]);
    let src_b = create_sample_dir("b", &[("b.txt", "from B")]);
    let vault_a = src_a.path().join("a.vault");
    let vault_b = src_b.path().join("b.vault");
    let merged = src_a.path().join("merged.vault");
    let export_dir = src_a.path().join("merged-export");

    // Create two vaults
    for (src, out) in [(&src_a, &vault_a), (&src_b, &vault_b)] {
        let out = Command::new(librecrate_bin())
            .args([
                "create",
                src.path().to_str().unwrap(),
                "-p",
                "pw",
                "-o",
                out.to_str().unwrap(),
            ])
            .output()
            .unwrap();
        assert!(out.status.success());
    }

    // Merge
    let merge_out = Command::new(librecrate_bin())
        .args([
            "merge",
            vault_a.to_str().unwrap(),
            vault_b.to_str().unwrap(),
            "-p",
            "pw",
            "-o",
            merged.to_str().unwrap(),
        ])
        .output()
        .unwrap();
    assert!(
        merge_out.status.success(),
        "merge: {}",
        String::from_utf8_lossy(&merge_out.stderr)
    );
    let stdout = String::from_utf8_lossy(&merge_out.stdout);
    assert!(stdout.contains("docs added: 1"));

    // Export merged and verify 2 files
    let export_out = Command::new(librecrate_bin())
        .args([
            "export",
            merged.to_str().unwrap(),
            "-p",
            "pw",
            "-o",
            export_dir.to_str().unwrap(),
        ])
        .output()
        .unwrap();
    assert!(export_out.status.success());

    let files: Vec<_> = std::fs::read_dir(export_dir.join("files"))
        .unwrap()
        .filter_map(|e| e.ok())
        .collect();
    assert_eq!(files.len(), 2, "merged vault should have 2 documents");
}

#[test]
fn test_export_wrong_password() {
    let src = create_sample_dir("src", &[("doc.txt", "content")]);
    let vault_path = src.path().join("out.vault");
    let export_dir = src.path().join("extracted");

    // Create
    Command::new(librecrate_bin())
        .args([
            "create",
            src.path().to_str().unwrap(),
            "-p",
            "correct",
            "-o",
            vault_path.to_str().unwrap(),
        ])
        .output()
        .unwrap();

    // Export with wrong password should fail
    let out = Command::new(librecrate_bin())
        .args([
            "export",
            vault_path.to_str().unwrap(),
            "-p",
            "wrong",
            "-o",
            export_dir.to_str().unwrap(),
        ])
        .output()
        .unwrap();
    assert!(!out.status.success());
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(stderr.contains("Wrong password") || stderr.contains("AuthenticationFailed"));
}
