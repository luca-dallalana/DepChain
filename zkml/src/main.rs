use std::path::{Path, PathBuf};
use std::process::Command;

fn run_ezkl(args: &[&str], label: &str) {
    let status = Command::new("ezkl")
        .args(args)
        .status()
        .unwrap_or_else(|e| panic!("failed to launch ezkl for step '{}': {}", label, e));

    if !status.success() {
        panic!("ezkl step '{}' exited with status: {}", label, status);
    }
}

fn main() {
    let base: PathBuf = std::env::args()
        .nth(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."));

    let model    = base.join("model.onnx");
    let input    = base.join("input.json");
    let out      = base.join("output");
    let settings = out.join("settings.json");
    let circuit  = out.join("circuit.ezkl");
    let srs      = out.join("kzg.srs");
    let vk       = out.join("vk.key");
    let pk       = out.join("pk.key");
    let witness  = out.join("witness.json");
    let proof    = out.join("proof.json");
    let sol      = out.join("verifier.sol");
    let abi      = out.join("verifier.abi");

    if proof.exists() && sol.exists() {
        println!("outputs already exist, skipping generation");
        return;
    }

    std::fs::create_dir_all(&out).expect("failed to create output dir");

    fn s(p: &Path) -> &str { p.to_str().unwrap() }

    run_ezkl(&[
        "gen-settings",
        "-M", s(&model),
        "-O", s(&settings),
    ], "gen-settings");

    run_ezkl(&[
        "calibrate-settings",
        "-M", s(&model),
        "-D", s(&input),
        "--settings-path", s(&settings),
    ], "calibrate-settings");

    run_ezkl(&[
        "compile-circuit",
        "-M", s(&model),
        "--settings-path", s(&settings),
        "--compiled-circuit", s(&circuit),
    ], "compile-circuit");

    run_ezkl(&[
        "get-srs",
        "--settings-path", s(&settings),
        "--srs-path", s(&srs),
    ], "get-srs");

    run_ezkl(&[
        "setup",
        "-M", s(&circuit),
        "--srs-path", s(&srs),
        "--vk-path", s(&vk),
        "--pk-path", s(&pk),
    ], "setup");

    run_ezkl(&[
        "gen-witness",
        "-M", s(&circuit),
        "-D", s(&input),
        "-O", s(&witness),
    ], "gen-witness");

    run_ezkl(&[
        "prove",
        "-W", s(&witness),
        "-M", s(&circuit),
        "--pk-path", s(&pk),
        "--srs-path", s(&srs),
        "--proof-path", s(&proof),
    ], "prove");

    run_ezkl(&[
        "create-evm-verifier",
        "-S", s(&settings),
        "--srs-path", s(&srs),
        "--vk-path", s(&vk),
        "--sol-code-path", s(&sol),
        "--abi-path", s(&abi),
    ], "create-evm-verifier");

    println!("zkml pipeline complete");
    println!("  verifier: {}", s(&sol));
    println!("  proof:    {}", s(&proof));
}
