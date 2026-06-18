use serde::{Deserialize, Serialize};
use tauri::{
    plugin::{PluginHandle, TauriPlugin},
    Manager, Runtime,
};

mod error;

pub use error::{Error, Result};

#[cfg(target_os = "android")]
const PLUGIN_IDENTIFIER: &str = "app.fors.camera";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TakePhotoArgs {
    #[serde(default)]
    pub gallery: bool,
    #[serde(default = "default_quality")]
    pub quality: u8,
    #[serde(default = "default_size")]
    pub target_width: u32,
    #[serde(default = "default_size")]
    pub target_height: u32,
}

fn default_quality() -> u8 {
    90
}

fn default_size() -> u32 {
    1440
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PhotoResult {
    pub data: String,
    pub name: String,
    pub mime_type: String,
}

pub struct ForsCamera<R: Runtime> {
    handle: PluginHandle<R>,
}

impl<R: Runtime> ForsCamera<R> {
    pub async fn take_photo(&self, args: TakePhotoArgs) -> Result<PhotoResult> {
        self.handle
            .run_mobile_plugin("takePhoto", args)
            .map_err(Into::into)
    }
}

pub trait ForsCameraExt<R: Runtime> {
    fn fors_camera(&self) -> &ForsCamera<R>;
}

impl<R: Runtime, T: Manager<R>> ForsCameraExt<R> for T {
    fn fors_camera(&self) -> &ForsCamera<R> {
        self.state::<ForsCamera<R>>().inner()
    }
}

#[tauri::command]
async fn take_photo<R: Runtime>(app: tauri::AppHandle<R>, args: TakePhotoArgs) -> Result<PhotoResult> {
    app.fors_camera().take_photo(args).await
}

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::new("fors-camera")
        .invoke_handler(tauri::generate_handler![take_photo])
        .setup(|app, api| {
            #[cfg(target_os = "android")]
            {
                let handle = api.register_android_plugin(PLUGIN_IDENTIFIER, "ForsCameraPlugin")?;
                app.manage(ForsCamera { handle });
            }
            Ok(())
        })
        .build()
}