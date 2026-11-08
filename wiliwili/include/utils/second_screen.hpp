//
// Dual-screen support: native side of the bridge to the Android Presentation.
//
// The main screen keeps using borealis, the second screen is a plain Android
// View hierarchy inside a Presentation. This module pushes display data to it
// over JNI and receives commands back.
//

#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace wiliwili::second_screen {

/// Event fired on APP_E whenever the user switches single/dual screen mode.
inline constexpr const char* EVENT_CHANGED = "SECOND_SCREEN_CHANGED";

enum class Mode : int {
    OFF   = 0,
    VIDEO = 1,
    LIVE  = 2,
    HOME  = 3,
};

/// Commands sent by the second screen.
enum class Command : int {
    PLAY_PAUSE         = 1,
    SEEK               = 2,  // arg: seconds
    SEEK_RELATIVE      = 3,  // arg: +/- seconds
    SET_SPEED          = 4,  // arg: speed * 100
    SET_QUALITY        = 5,  // arg: quality code
    TOGGLE_DANMAKU     = 6,
    LOAD_MORE_COMMENTS = 7,
    EXIT_PLAYER        = 8,
    OPEN_BV            = 9,   // text: bvid, open a new player on the main screen
    PLAY_INDEX         = 10,  // arg: index in the current page/episode list
    PREV_EPISODE       = 11,
    NEXT_EPISODE       = 12,
    SET_VOLUME         = 13,  // arg: 0-100
    SET_BRIGHTNESS     = 14,  // arg: 0-100
    TOGGLE_MIRROR      = 15,
    SET_ASPECT         = 16,  // text: "-1" | "-2" | "-3" | "4:3" | "16:9"
    SET_SECOND_SCREEN  = 17,  // arg: 0 = close the lower screen, 1 = enable it
    OPEN_SEARCH        = 18,  // text: keyword (may be empty), home dashboard
    OPEN_SETTINGS      = 19,  // home dashboard
    REFRESH_HOME       = 20,  // home dashboard
    MAIN_TAB           = 21,  // arg: 0 = home, 1 = dynamic, 2 = mine
};

/// Gamepad buttons forwarded to the lower screen while it owns the input.
/// Keep in sync with SecondScreenManager.KEY_*.
enum class GamepadKey : int {
    UP    = 1,
    DOWN  = 2,
    LEFT  = 3,
    RIGHT = 4,
    A     = 5,
    B     = 6,
    X     = 7,
    Y     = 8,
    LB    = 9,
    RB    = 10,
    START = 11,
    BACK  = 12,
    LT    = 13,
    RT    = 14,
};

struct QualityOption {
    int code = 0;
    std::string label;
};

struct VideoInfo {
    std::string title;
    std::string up;
    std::string bvid;
    std::string desc;
    std::string pubdate;
    int64_t view     = 0;
    int64_t danmaku  = 0;
    int64_t like     = 0;
    int64_t coin     = 0;
    int64_t favorite = 0;
    int64_t share    = 0;

    std::vector<QualityOption> qualities;
    int qualityIndex = 0;
    bool hasPrev     = false;
    bool hasNext     = false;
};

struct RelatedItem {
    std::string title;
    std::string up;
    std::string bvid;
    std::string cover;
    int64_t duration = 0;
    int64_t view     = 0;
};

struct EpisodeItem {
    int index = 0;
    std::string title;
    std::string subtitle;
    int64_t duration = 0;
    bool playable    = true;
};

struct CommentEmote {
    std::string token;  // e.g. "[doge]"
    std::string url;
    int size = 1;
};

struct CommentItem {
    std::string name;
    std::string text;
    std::string time;
    std::string avatar;
    std::vector<CommentEmote> emotes;
    int64_t like    = 0;
    int64_t replies = 0;
    bool up         = false;
    bool top        = false;
};

struct PlayerState {
    bool playing = false;
    bool paused  = false;
    bool danmaku = true;

    int64_t position = 0;  // seconds
    int64_t duration = 0;  // seconds
    double speed     = 1.0;

    int qualityIndex = 0;

    int64_t volume         = 100;
    int64_t brightness     = -1;  // < 0: unsupported on this platform
    bool canSetBrightness  = false;
    bool mirror            = false;
    std::string aspect     = "-1";
};

struct LiveInfo {
    std::string title;
    std::string anchor;
    int64_t online   = 0;
    int64_t watching = 0;
    int64_t roomid   = 0;
};

struct ThemeColors {
    bool dark             = false;
    std::string background;
    std::string surface;
    std::string text;
    std::string textSecondary;
    std::string accent;
    std::string divider;
    std::string name;
};

enum class LiveMessageType : int {
    DANMAKU    = 0,
    SUPER_CHAT = 1,
};

struct LiveMessage {
    LiveMessageType type = LiveMessageType::DANMAKU;
    std::string user;
    std::string text;
    std::string color;  // #RRGGBB, danmaku text color
    std::string bg;     // #RRGGBB, super chat background
    std::string medal;  // fans medal name
    int64_t price = 0;  // super chat price
};

/// True when a Presentation-capable secondary display was reported by Java.
bool isDisplayAvailable();

/// True when the display is available and the user enabled the feature.
bool isEnabled();

/// Called once after the borealis event loop is initialized, so display
/// hot-plug events can safely be dispatched on APP_E.
void setAppRunning();

/// Persist the switch and notify the running player via EVENT_CHANGED.
void setEnabled(bool enabled);

/// Tell the second screen which kind of content is active (or dismiss it).
/// Sessions make sure a player that is being destroyed cannot close the screen
/// of the player that replaced it.
uint64_t openSession(Mode mode);
void closeSession(uint64_t session);
void exit();

/// Data pushes. All are no-ops when the feature is disabled or unavailable.
void pushVideoInfo(const VideoInfo& info);
void pushComments(bool reset, bool end, int next, int total, const std::vector<CommentItem>& items);
void pushRelatedVideos(const std::vector<RelatedItem>& items);
void pushEpisodes(const std::vector<EpisodeItem>& items);
void pushPlayerState(const PlayerState& state);
void pushLiveInfo(const LiveInfo& info);
void pushLiveMessages(const std::vector<LiveMessage>& items);

/// Push the current wiliwili theme so the secondary screen matches the app.
void pushTheme();

/// Show a short-lived message on the secondary screen (command errors, login gates...).
void pushStatus(const std::string& text);

/// Forward a gamepad button to the secondary screen. Only used while the
/// player has handed input ownership to the lower UI.
void pushGamepad(GamepadKey key);

/// Registered by the active player activity, invoked on the app main thread.
void setCommandHandler(uint64_t session, std::function<void(Command, int64_t, const std::string&)> handler);

}  // namespace wiliwili::second_screen
