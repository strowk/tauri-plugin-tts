const COMMANDS: &[&str] = &[
    "speak",
    "stop",
    "get_voices",
    "is_speaking",
    "pause_speaking",
    "resume_speaking",
    "preview_voice",
];

fn main() {
    tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .ios_path("ios")
        .build();
}
