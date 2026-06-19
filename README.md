# tauri-plugin-fors-camera

[Tauri v2](https://v2.tauri.app/) plugin for camera capture and gallery pick on Android, with JPEG resize, EXIF orientation fix, and base64 output.

**Platform:** Android only (API 24+).

Uses Tauri `startActivityForResult` + `@ActivityCallback` (not `registerForActivityResult` in the plugin constructor, which breaks when the plugin loads after the activity has started).

## Install

```toml
# src-tauri/Cargo.toml
[dependencies]
tauri-plugin-fors-camera = { git = "https://github.com/giraudsa/tauri-plugin-fors-camera", branch = "main" }
```

```rust
// src-tauri/src/lib.rs
tauri::Builder::default()
    .plugin(tauri_plugin_fors_camera::init())
```

## Host app requirements

1. **`FileProvider`** with authority `${applicationId}.fileprovider` and a `cache-path` in `res/xml/file_paths.xml` (camera writes capture files to cache).
2. **`CAMERA` permission** in `AndroidManifest.xml`. Runtime permission is requested automatically by `take_photo` when opening the camera (not required for gallery pick).

Example `file_paths.xml`:

```xml
<paths xmlns:android="http://schemas.android.com/apk/res/android">
  <cache-path name="fors_camera_cache" path="." />
</paths>
```

## Commands

| Command | Description |
|---------|-------------|
| `take_photo` | Open camera or gallery, resize to target dimensions, return base64 JPEG |
| `check_permissions` | Optional — check `camera` permission state |
| `request_permissions` | Optional — request `camera` permission (alias `camera`) |

For most apps, call only `take_photo` — it requests the camera permission when needed.

### Arguments (`take_photo`)

| Field | Default | Description |
|-------|---------|-------------|
| `gallery` | `false` | `true` = pick from gallery, `false` = open camera |
| `quality` | `90` | JPEG compression (0–100) |
| `targetWidth` | `1440` | Max width after resize |
| `targetHeight` | `1440` | Max height after resize |

### JavaScript

On Tauri v2 mobile, command arguments are passed in an `args` wrapper:

```js
import { invoke } from '@tauri-apps/api/core';

// Camera
const photo = await invoke('plugin:fors-camera|take_photo', {
  args: {
    gallery: false,
    quality: 90,
    targetWidth: 1440,
    targetHeight: 1440,
  },
});
// photo: { data, name, mimeType }  — data is base64 JPEG (no data: prefix)

// Gallery
const fromGallery = await invoke('plugin:fors-camera|take_photo', {
  args: { gallery: true },
});
```

Do not rely on Cordova globals (`Camera`, `navigator.camera`) in application code — pass `gallery: true/false` directly.

## License

MIT OR Apache-2.0