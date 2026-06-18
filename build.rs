const COMMANDS: &[&str] = &["take_photo"];

fn main() {
    tauri_plugin::Builder::new(COMMANDS).android_path("android").build();
}