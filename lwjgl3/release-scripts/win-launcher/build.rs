use std::env;
use std::path::PathBuf;

fn main() {
    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let def = manifest_dir.join("gpu_exports.def");
    println!("cargo:rerun-if-changed={}", def.display());
    println!("cargo:rustc-link-arg={}", def.display());

}
