const COMMANDS: &[&str] = &[
    "take_photo",
    "check_permissions",
    "request_permissions",
];

fn main() {
    tauri_plugin::Builder::new(COMMANDS).android_path("android").build();
}