#![windows_subsystem = "windows"]

use std::ffi::OsStr;
use std::os::windows::ffi::OsStrExt;
use std::path::{Path, PathBuf};
use std::ptr;
use std::{env, fs, process};

use jni::objects::{JObjectArray, JString};
use jni::{InitArgsBuilder, JNIVersion, JavaVM};

const MAIN_CLASS: &str = "me/ethanchen/lwjgl3/Lwjgl3Launcher";

#[no_mangle]
#[used]
pub static NvOptimusEnablement: u32 = 0x00000001;

#[no_mangle]
#[used]
pub static AmdPowerXpressRequestHighPerformance: i32 = 1;

fn main() {
    std::panic::set_hook(Box::new(|info| {
        show_error(&info.to_string());
    }));
    if let Err(err) = run() {
        show_error(&err);
        process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let root = env::current_exe()
        .map_err(|e| format!("Could not locate start.exe: {e}"))?
        .parent()
        .ok_or_else(|| "Could not locate the UltraStack folder.".to_string())?
        .to_path_buf();
    env::set_current_dir(&root).map_err(|e| format!("Could not open the UltraStack folder: {e}"))?;

    let jar = find_game_jar(&root)?;
    let jar_name = jar
        .file_name()
        .and_then(OsStr::to_str)
        .ok_or_else(|| "JAR path is not valid UTF-8.".to_string())?;

    let bin_dir = root.join("jdk").join("bin");
    let jvm_dll = bin_dir.join("server").join("jvm.dll");
    if !jvm_dll.is_file() {
        return Err("Could not find jdk\\bin\\server\\jvm.dll. Re-extract the Windows zip.".into());
    }

    prepend_path(&bin_dir);
    set_dll_directory(&bin_dir)?;

    let vm_args = InitArgsBuilder::new()
        .version(JNIVersion::V8)
        .option(format!("-Djava.class.path={jar_name}"))
        .option("--enable-native-access=ALL-UNNAMED")
        .option("-Dfile.encoding=UTF-8")
        .ignore_unrecognized(true)
        .build()
        .map_err(|e| format!("Invalid Java VM arguments: {e}"))?;

    let jvm = JavaVM::with_libjvm(vm_args, || Ok(jvm_dll.clone()))
        .map_err(|e| format!("Could not create the Java VM: {e}"))?;
    let mut env = jvm
        .attach_current_thread()
        .map_err(|e| format!("Could not attach to the Java VM: {e}"))?;

    let extra: Vec<String> = env::args().skip(1).collect();
    let empty = env
        .new_string("")
        .map_err(|e| format!("Could not allocate Java strings: {e}"))?;
    let args: JObjectArray = env
        .new_object_array(extra.len() as i32, "java/lang/String", &empty)
        .map_err(|e| format!("Could not allocate Java arguments: {e}"))?;
    for (i, arg) in extra.iter().enumerate() {
        let value = env
            .new_string(arg)
            .map_err(|e| format!("Could not convert argument to Java: {e}"))?;
        env.set_object_array_element(&args, i as i32, &value)
            .map_err(|e| format!("Could not store Java argument: {e}"))?;
    }

    let result = env.call_static_method(
        MAIN_CLASS,
        "main",
        "([Ljava/lang/String;)V",
        &[(&args).into()],
    );
    if let Err(err) = result {
        if let Some(message) = java_exception_message(&mut env) {
            return Err(message);
        }
        return Err(format!("UltraStack failed to start: {err}"));
    }
    if env.exception_check().unwrap_or(false) {
        if let Some(message) = java_exception_message(&mut env) {
            return Err(message);
        }
        return Err("UltraStack failed to start (Java exception).".into());
    }
    Ok(())
}

fn find_game_jar(root: &Path) -> Result<PathBuf, String> {
    let mut jars = Vec::new();
    let entries = fs::read_dir(root).map_err(|e| format!("Could not read the UltraStack folder: {e}"))?;
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let Some(name) = path.file_name().and_then(OsStr::to_str) else {
            continue;
        };
        if name.starts_with("UltraStack-") && name.ends_with(".jar") {
            jars.push(path);
        }
    }
    match jars.len() {
        0 => Err("No UltraStack-*.jar found. Run update.bat first.".into()),
        1 => Ok(jars.remove(0)),
        _ => Err("Multiple UltraStack-*.jar files found. Leave only one, or run update.bat.".into()),
    }
}

fn prepend_path(bin_dir: &Path) {
    let mut path = bin_dir.display().to_string();
    if let Ok(old) = env::var("PATH") {
        path.push(';');
        path.push_str(&old);
    }
    env::set_var("PATH", path);
}

fn set_dll_directory(bin_dir: &Path) -> Result<(), String> {
    let wide = wide_path(bin_dir);
    let ok = unsafe { SetDllDirectoryW(wide.as_ptr()) };
    if ok == 0 {
        return Err("Could not add jdk\\bin to the DLL search path.".into());
    }
    Ok(())
}

fn wide_path(path: &Path) -> Vec<u16> {
    path.as_os_str().encode_wide().chain(std::iter::once(0)).collect()
}

fn java_exception_message(env: &mut jni::JNIEnv) -> Option<String> {
    let thrown = env.exception_occurred().ok()?;
    let _ = env.exception_clear();
    let message: JString = env
        .call_method(&thrown, "toString", "()Ljava/lang/String;", &[])
        .ok()?
        .l()
        .ok()?
        .into();
    env.get_string(&message).ok().map(|s| s.into())
}

fn show_error(message: &str) {
    let text = wide_str(message);
    let title = wide_str("UltraStack");
    unsafe {
        MessageBoxW(ptr::null_mut(), text.as_ptr(), title.as_ptr(), 0x00000010);
    }
}

fn wide_str(text: &str) -> Vec<u16> {
    OsStr::new(text).encode_wide().chain(std::iter::once(0)).collect()
}

#[link(name = "user32")]
extern "system" {
    fn MessageBoxW(hwnd: *mut core::ffi::c_void, text: *const u16, caption: *const u16, ty: u32) -> i32;
}

#[link(name = "kernel32")]
extern "system" {
    fn SetDllDirectoryW(path: *const u16) -> i32;
}
