use iced::{Element, Subscription, Task, Theme};
use std::path::PathBuf;
use std::sync::Arc;

use crate::config::Config;
use crate::keychain::SecureStore;
use crate::opener::{DocumentOpener, SystemOpener};
use crate::screens::{self, Navigation};

pub enum Screen {
    FirstRun(screens::first_run::State),
    Unlock(screens::unlock::State),
    Library(screens::library::State),
    Settings(screens::settings::State),
    Export(screens::export::State),
    Collections(screens::collections::State),
}

#[derive(Debug)]
pub enum Message {
    FirstRun(screens::first_run::Message),
    Unlock(screens::unlock::Message),
    Library(screens::library::Message),
    Settings(screens::settings::Message),
    Export(screens::export::Message),
    Collections(screens::collections::Message),
    Navigate(Navigation),
    FileDropped(PathBuf),
}

pub struct App {
    pub screen: Screen,
    pub _keychain: SecureStore,
    pub config: Config,
    pub _opener: SystemOpener,
}

pub fn boot() -> (App, Task<Message>) {
    let config = Config::load();
    let keychain = SecureStore::new("com.librecrate.desktop");

    let vault_exists = config
        .vault_dir
        .as_ref()
        .map(|d| d.join("encryption").join("master_key").exists())
        .unwrap_or(false);

    if vault_exists {
        let unlock = screens::unlock::State::new();
        (
            App {
                screen: Screen::Unlock(unlock),
                _keychain: keychain,
                config,
                _opener: SystemOpener,
            },
            Task::none(),
        )
    } else {
        let first_run = screens::first_run::State::new();
        (
            App {
                screen: Screen::FirstRun(first_run),
                _keychain: keychain,
                config,
                _opener: SystemOpener,
            },
            Task::none(),
        )
    }
}

pub fn update(app: &mut App, message: Message) -> Task<Message> {
    match message {
        Message::FirstRun(msg) => {
            if let Screen::FirstRun(ref mut state) = app.screen {
                let task = state.update(msg);
                return task;
            }
            Task::none()
        }
        Message::Unlock(msg) => {
            if let Screen::Unlock(ref mut state) = app.screen {
                let task = state.update(msg);
                return task;
            }
            Task::none()
        }
        Message::Library(msg) => {
            if let Screen::Library(ref mut state) = app.screen {
                let task = state.update(msg);
                return task;
            }
            Task::none()
        }
        Message::Settings(msg) => {
            if let Screen::Settings(ref mut state) = app.screen {
                let task = state.update(msg);
                return task;
            }
            Task::none()
        }
        Message::Export(msg) => {
            if let Screen::Export(ref mut state) = app.screen {
                let task = state.update(msg);
                return task;
            }
            Task::none()
        }
        Message::Collections(msg) => {
            if let Screen::Collections(ref mut state) = app.screen {
                let task = state.update(msg);
                return task;
            }
            Task::none()
        }
        Message::Navigate(nav) => handle_navigation(app, nav),
        Message::FileDropped(path) => {
            if let Screen::Library(ref mut state) = app.screen {
                let vault = state.vault.clone();
                return Task::perform(
                    async move {
                        tokio::task::spawn_blocking(move || -> Result<usize, String> {
                            if path.is_dir() {
                                let mut count = 0;
                                for entry in std::fs::read_dir(&path).map_err(|e| e.to_string())? {
                                    let entry = entry.map_err(|e| e.to_string())?;
                                    let child = entry.path();
                                    if child.is_file() {
                                        vault.import_file(&child).map_err(|e| e.to_string())?;
                                        count += 1;
                                    }
                                }
                                Ok(count)
                            } else {
                                vault.import_file(&path).map(|_| 1).map_err(|e| e.to_string())
                            }
                        })
                        .await
                        .map_err(|e| e.to_string())?
                    },
                    |result| Message::Library(screens::library::Message::DropResult(result)),
                );
            }
            Task::none()
        }
    }
}

fn handle_navigation(app: &mut App, nav: Navigation) -> Task<Message> {
    match nav {
        Navigation::FirstRun => {
            let state = screens::first_run::State::new();
            app.screen = Screen::FirstRun(state);
            Task::none()
        }
        Navigation::Library(vault) => {
            let (state, task) = screens::library::State::new(Arc::clone(&vault));
            app.screen = Screen::Library(state);
            task
        }
        Navigation::Settings(vault) => {
            let state = screens::settings::State::new(vault);
            app.screen = Screen::Settings(state);
            Task::none()
        }
        Navigation::Export(vault) => {
            let state = screens::export::State::new(vault);
            app.screen = Screen::Export(state);
            Task::none()
        }
        Navigation::Collections(vault) => {
            let state = screens::collections::State::new(vault);
            app.screen = Screen::Collections(state);
            Task::none()
        }
        Navigation::OpenDocument(doc) => {
            let base_dir = app
                .config
                .vault_dir
                .clone()
                .unwrap_or_else(Config::vault_data_dir);
            if let Err(e) = app._opener.open(&doc, &base_dir) {
                tracing::error!("Failed to open document: {e}");
            }
            Task::none()
        }
    }
}

pub fn view(app: &App) -> Element<'_, Message> {
    match &app.screen {
        Screen::FirstRun(state) => state.view().map(Message::FirstRun),
        Screen::Unlock(state) => state.view().map(Message::Unlock),
        Screen::Library(state) => state.view().map(Message::Library),
        Screen::Settings(state) => state.view().map(Message::Settings),
        Screen::Export(state) => state.view().map(Message::Export),
        Screen::Collections(state) => state.view().map(Message::Collections),
    }
}

pub fn subscription(_state: &App) -> Subscription<Message> {
    iced::event::listen_with(drop_event_handler)
}

fn drop_event_handler(
    event: iced::Event,
    _status: iced::event::Status,
    _window: iced::window::Id,
) -> Option<Message> {
    match event {
        iced::Event::Window(iced::window::Event::FileDropped(path)) => {
            Some(Message::FileDropped(path))
        }
        _ => None,
    }
}

pub fn theme(_app: &App) -> Theme {
    Theme::Dark
}
