# DownTune

An Android YouTube Music client with a **plugin system**. DownTune is an unofficial fork of
[ArchiveTune](https://github.com/rukamori/ArchiveTune) (itself built on Metrolist / InnerTune).
It is not affiliated with, or endorsed by, the ArchiveTune maintainers, Google or YouTube.

## What's different from ArchiveTune

- **Plugins** (Settings → Integration → Plugins): small add-ons that react to playback events and can
  control the player. Off by default, isolated from each other, and auto-disabled if they keep crashing.
  Built-in plugins: *Skip short tracks* and *Now playing webhook*. See [docs/PLUGINS.md](docs/PLUGINS.md).
- **Download button in the full player** for the current song (tap to download, tap again to cancel/remove). Playlists, albums and artists already have download buttons in their headers.
- Renamed to DownTune, with its own application ID (`app.downtune`) so it installs next to other builds.
- The in-app updater is disabled (it pointed at upstream's releases). Point it at your own releases before enabling it.

The plugin lifecycle (enable / disable / settings-changed, per-plugin config) is modelled on
[Pear Desktop](https://github.com/pear-devs/pear-desktop) (MIT).

## Building

See [CONTRIBUTING.md](CONTRIBUTING.md) (unchanged from upstream) for the toolchain. Git submodules
(`core`, `lyrics`, `IconPack`, `morideobfuscator`) are required: `git submodule update --init --recursive`.

## License and attribution

GPL-3.0, same as upstream — see [LICENSE](LICENSE). Original copyright notices in source files are kept;
files added by this fork say so in their headers. The **ArchiveTune name, logo and icon are not covered by the
GPL**: replace the launcher icon and artwork with your own before distributing DownTune.
