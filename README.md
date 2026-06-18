# tauri-plugin-fors-camera

[Tauri v2](https://v2.tauri.app/) plugin for camera capture and gallery pick on Android, with JPEG resize and base64 output.

**Platform:** Android only (API 24+).

## Install

```toml
# src-tauri/Cargo.toml
[dependencies]
tauri-plugin-fors-camera = { git = "https://github.com/giraudsa/tauri-plugin-fors-camera", tag = "v0.1.0" }
```

```rust
// src-tauri/src/lib.rs
tauri::Builder::default()
    .plugin(tauri_plugin_fors_camera::init())
```

## Host app requirements

The host app must declare a `FileProvider` with authority `${applicationId}.fileprovider` and a `cache-path` so the camera intent can share capture files.

## Commands

| Command | Description |
|---------|-------------|
| `take_photo` | Open camera or gallery, resize to target dimensions, return base64 JPEG |

### Arguments

| Field | Default | Description |
|-------|---------|-------------|
| `gallery` | `false` | `true` = pick from gallery, `false` = open camera |
| `quality` | `90` | JPEG compression (0–100) |
| `targetWidth` | `1440` | Max width after resize |
| `targetHeight` | `1440` | Max height after resize |

### JavaScript

```js
import { invoke } from '@tauri-apps/api/core';

const photo = await invoke('plugin:fors-camera|take_photo', {
  gallery: false,
  quality: 90,
  targetWidth: 1440,
  targetHeight: 1440,
});
// photo: { data, name, mimeType }
```

## License

MIT OR Apache-2.0
