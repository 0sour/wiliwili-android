//
// Dual-screen support: native side of the bridge to the Android Presentation.
//

#include "utils/second_screen.hpp"

#include <atomic>
#include <cstdio>
#include <utility>

#include <borealis/core/application.hpp>
#include <borealis/core/logger.hpp>
#include <borealis/core/theme.hpp>
#include <borealis/core/thread.hpp>
#include <nlohmann/json.hpp>

#include "utils/config_helper.hpp"
#include "utils/event_helper.hpp"

#if defined(__ANDROID__)
#include <jni.h>
#endif

namespace wiliwili::second_screen {

namespace {

// Keep in sync with SecondScreenManager.EventType
constexpr int EVENT_MODE           = 1;
constexpr int EVENT_VIDEO_INFO     = 2;
constexpr int EVENT_COMMENTS       = 3;
constexpr int EVENT_PLAYER_STATE   = 4;
constexpr int EVENT_LIVE_INFO      = 5;
constexpr int EVENT_LIVE_MESSAGES  = 6;
constexpr int EVENT_RELATED        = 7;
constexpr int EVENT_EPISODES       = 8;
constexpr int EVENT_THEME          = 9;
constexpr int EVENT_STATUS         = 10;
constexpr int EVENT_GAMEPAD        = 11;

std::atomic<bool> gDisplayAvailable{false};
std::atomic<bool> gAppRunning{false};
std::function<void(Command, int64_t, const std::string&)> gCommandHandler;
uint64_t gSessionCounter = 0;
uint64_t gSession        = 0;

#if defined(__ANDROID__)

JavaVM* gVM              = nullptr;
jclass gBridgeClass      = nullptr;
jmethodID gOnNativeEvent = nullptr;

JNIEnv* getEnv() {
    if (!gVM) return nullptr;
    JNIEnv* env = nullptr;
    jint result = gVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        if (gVM->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    } else if (result != JNI_OK) {
        return nullptr;
    }
    return env;
}

void emit(int type, const nlohmann::json& payload) {
    if (!gBridgeClass || !gOnNativeEvent) return;
    JNIEnv* env = getEnv();
    if (!env) return;

    std::string data = payload.dump();
    jbyteArray array = env->NewByteArray(static_cast<jsize>(data.size()));
    if (!array) return;
    env->SetByteArrayRegion(array, 0, static_cast<jsize>(data.size()),
                            reinterpret_cast<const jbyte*>(data.data()));
    env->CallStaticVoidMethod(gBridgeClass, gOnNativeEvent, type, array);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(array);
}

void emitMode(Mode mode) { emit(EVENT_MODE, {{"mode", static_cast<int>(mode)}}); }

#else

void emit(int, const nlohmann::json&) {}
void emitMode(Mode) {}

#endif

}  // namespace

bool isDisplayAvailable() { return gDisplayAvailable.load(std::memory_order_relaxed); }

void setAppRunning() { gAppRunning.store(true, std::memory_order_relaxed); }

bool isEnabled() {
#if defined(__ANDROID__)
    if (!gDisplayAvailable.load(std::memory_order_relaxed)) return false;
    return ProgramConfig::instance().getBoolOption(SettingItem::PLAYER_SECOND_SCREEN);
#else
    return false;
#endif
}

void setEnabled(bool enabled) {
    ProgramConfig::instance().setSettingItem(SettingItem::PLAYER_SECOND_SCREEN, enabled);
    brls::Logger::info("[second-screen] enabled: {}", enabled);
    if (!enabled) exit();
    APP_E->fire(EVENT_CHANGED, nullptr);
}

uint64_t openSession(Mode mode) {
    if (!isEnabled()) {
        exit();
        return 0;
    }
    gSession = ++gSessionCounter;
    emitMode(mode);
    pushTheme();
    brls::Logger::debug("[second-screen] session {} opened (mode {})", gSession, static_cast<int>(mode));
    return gSession;
}

void closeSession(uint64_t session) {
    if (session == 0 || session != gSession) return;
    brls::Logger::debug("[second-screen] session {} closed", session);
    gSession = 0;
    gCommandHandler = nullptr;
    emitMode(Mode::OFF);
}

void exit() {
    gSession        = 0;
    gCommandHandler = nullptr;
    emitMode(Mode::OFF);
}

void pushVideoInfo(const VideoInfo& info) {
    if (!isEnabled()) return;

    nlohmann::json qualities = nlohmann::json::array();
    for (const auto& item : info.qualities) {
        qualities.push_back({{"code", item.code}, {"label", item.label}});
    }
    emit(EVENT_VIDEO_INFO,
         {{"title", info.title},
          {"up", info.up},
          {"bvid", info.bvid},
          {"desc", info.desc},
          {"pubdate", info.pubdate},
          {"view", info.view},
          {"danmaku", info.danmaku},
          {"like", info.like},
          {"coin", info.coin},
          {"favorite", info.favorite},
          {"share", info.share},
          {"qualityIndex", info.qualityIndex},
          {"hasPrev", info.hasPrev},
          {"hasNext", info.hasNext},
          {"qualities", std::move(qualities)}});
}

void pushRelatedVideos(const std::vector<RelatedItem>& items) {
    if (!isEnabled()) return;

    nlohmann::json list = nlohmann::json::array();
    for (const auto& item : items) {
        list.push_back({{"title", item.title},
                        {"up", item.up},
                        {"bvid", item.bvid},
                        {"cover", item.cover},
                        {"duration", item.duration},
                        {"view", item.view}});
    }
    emit(EVENT_RELATED, {{"items", std::move(list)}});
}

void pushEpisodes(const std::vector<EpisodeItem>& items) {
    if (!isEnabled()) return;

    nlohmann::json list = nlohmann::json::array();
    for (const auto& item : items) {
        list.push_back({{"index", item.index},
                        {"title", item.title},
                        {"subtitle", item.subtitle},
                        {"duration", item.duration},
                        {"playable", item.playable}});
    }
    emit(EVENT_EPISODES, {{"items", std::move(list)}});
}

void pushComments(bool reset, bool end, int next, int total, const std::vector<CommentItem>& items) {
    if (!isEnabled()) return;

    nlohmann::json list = nlohmann::json::array();
    for (const auto& item : items) {
        nlohmann::json emotes = nlohmann::json::array();
        for (const auto& emote : item.emotes) {
            emotes.push_back({{"token", emote.token}, {"url", emote.url}, {"size", emote.size}});
        }
        list.push_back({{"name", item.name},
                        {"text", item.text},
                        {"time", item.time},
                        {"avatar", item.avatar},
                        {"emotes", std::move(emotes)},
                        {"like", item.like},
                        {"replies", item.replies},
                        {"up", item.up},
                        {"top", item.top}});
    }
    emit(EVENT_COMMENTS,
         {{"reset", reset}, {"end", end}, {"next", next}, {"total", total}, {"items", std::move(list)}});
}

void pushPlayerState(const PlayerState& state) {
    if (!isEnabled()) return;

    emit(EVENT_PLAYER_STATE,
         {{"playing", state.playing},
          {"paused", state.paused},
          {"danmaku", state.danmaku},
          {"pos", state.position},
          {"dur", state.duration},
          {"speed", state.speed},
          {"qualityIndex", state.qualityIndex},
          {"volume", state.volume},
          {"brightness", state.brightness},
          {"canBrightness", state.canSetBrightness},
          {"mirror", state.mirror},
          {"aspect", state.aspect}});
}

void pushLiveInfo(const LiveInfo& info) {
    if (!isEnabled()) return;

    emit(EVENT_LIVE_INFO,
         {{"title", info.title},
          {"anchor", info.anchor},
          {"online", info.online},
          {"watching", info.watching},
          {"roomid", info.roomid}});
}

void pushLiveMessages(const std::vector<LiveMessage>& items) {
    if (!isEnabled() || items.empty()) return;

    nlohmann::json list = nlohmann::json::array();
    for (const auto& item : items) {
        list.push_back({{"type", static_cast<int>(item.type)},
                        {"user", item.user},
                        {"text", item.text},
                        {"color", item.color},
                        {"bg", item.bg},
                        {"medal", item.medal},
                        {"price", item.price}});
    }
    emit(EVENT_LIVE_MESSAGES, {{"items", std::move(list)}});
}

void pushTheme() {
    if (!isEnabled()) return;

    auto theme = brls::Application::getTheme();
    auto toHex  = [](NVGcolor color) {
        char buffer[8];
        snprintf(buffer, sizeof(buffer), "#%02X%02X%02X", static_cast<int>(color.r * 255),
                 static_cast<int>(color.g * 255), static_cast<int>(color.b * 255));
        return std::string(buffer);
    };

    emit(EVENT_THEME,
         {{"dark", brls::Application::getThemeVariant() == brls::ThemeVariant::DARK},
          {"background", toHex(theme.getColor("brls/background"))},
          {"surface", toHex(theme.getColor("color/grey_1"))},
          {"text", toHex(theme.getColor("brls/text"))},
          {"textSecondary", toHex(theme.getColor("font/grey"))},
          {"accent", toHex(theme.getColor("color/bilibili"))},
          {"divider", toHex(theme.getColor("color/line"))},
          {"name", toHex(theme.getColor("color/link"))},
          {"keymap", ProgramConfig::instance().getSettingItem(SettingItem::KEYMAP, std::string{"xbox"})},
          {"swap", brls::Application::isSwapInputKeys()}});
}

void pushStatus(const std::string& text) {
    if (!isEnabled() || text.empty()) return;
    emit(EVENT_STATUS, {{"text", text}});
}

void pushGamepad(GamepadKey key) {
    if (!isEnabled()) return;
    emit(EVENT_GAMEPAD, {{"key", static_cast<int>(key)}});
}

void setCommandHandler(uint64_t session, std::function<void(Command, int64_t, const std::string&)> handler) {
    if (session == 0 || session != gSession) return;
    gCommandHandler = std::move(handler);
}

#if defined(__ANDROID__)

namespace {

void JNICALL nativeCommand(JNIEnv* env, jclass, jint cmd, jlong arg, jstring text) {
    std::string payload;
    if (text) {
        const char* chars = env->GetStringUTFChars(text, nullptr);
        if (chars) {
            payload = chars;
            env->ReleaseStringUTFChars(text, chars);
        }
    }

    Command command = static_cast<Command>(cmd);
    brls::sync([command, arg, payload]() {
        if (gCommandHandler) gCommandHandler(command, arg, payload);
    });
}

void JNICALL nativeSetDisplayAvailable(JNIEnv*, jclass, jboolean available) {
    bool was = gDisplayAvailable.exchange(available, std::memory_order_relaxed);
    if (was == available) return;

    brls::Logger::info("[second-screen] display available: {}", static_cast<bool>(available));
    // 应用事件循环就绪后再派发，避免启动阶段跨线程创建 EventHelper
    if (gAppRunning.load(std::memory_order_relaxed)) {
        APP_E->fire(EVENT_CHANGED, nullptr);
    }
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    gVM = vm;

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        brls::Logger::error("[second-screen] JNI_OnLoad: no env");
        return JNI_VERSION_1_6;
    }

    jclass clazz = env->FindClass("cn/xfangfang/wiliwili/secondscreen/SecondScreenManager");
    if (!clazz) {
        brls::Logger::error("[second-screen] JNI_OnLoad: SecondScreenManager not found");
        env->ExceptionClear();
        return JNI_VERSION_1_6;
    }

    JNINativeMethod methods[] = {
        {"nativeCommand", "(IJLjava/lang/String;)V", reinterpret_cast<void*>(&nativeCommand)},
        {"nativeSetDisplayAvailable", "(Z)V", reinterpret_cast<void*>(&nativeSetDisplayAvailable)},
    };
    if (env->RegisterNatives(clazz, methods, 2) != JNI_OK) {
        brls::Logger::error("[second-screen] JNI_OnLoad: RegisterNatives failed");
        env->ExceptionClear();
        env->DeleteLocalRef(clazz);
        return JNI_VERSION_1_6;
    }

    gBridgeClass      = static_cast<jclass>(env->NewGlobalRef(clazz));
    gOnNativeEvent    = env->GetStaticMethodID(clazz, "onNativeEvent", "(I[B)V");
    if (!gOnNativeEvent) {
        brls::Logger::error("[second-screen] JNI_OnLoad: onNativeEvent not found");
        env->ExceptionClear();
    }
    env->DeleteLocalRef(clazz);

    brls::Logger::info("[second-screen] JNI_OnLoad done");
    return JNI_VERSION_1_6;
}

#endif

}  // namespace wiliwili::second_screen
