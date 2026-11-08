#pragma once

namespace wiliwili {

/**
 * Android diagnostics: mirror all borealis logs into a timestamped file under
 * <config>/logs/, delete files older than 7 days (and cap the directory size).
 * Call after ProgramConfig::init().
 */
void startFileLogger();

/**
 * Android diagnostics: periodic health lines (fps/fd/memory/threads/mpv state)
 * plus a watchdog that logs a thread dump when the main thread stops ticking.
 * Call after brls::Application::init().
 */
void startStallWatchdog();

}  // namespace wiliwili
