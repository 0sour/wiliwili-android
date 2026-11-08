package cn.xfangfang.wiliwili.secondscreen;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.xfangfang.wiliwili.BuildConfig;

/**
 * Owns the secondary display (the "cocoon" screen on dual-screen handhelds)
 * and the in-memory model that backs the Presentation.
 *
 * Native code pushes data through {@link #onNativeEvent(int, byte[])}; user
 * input goes back through {@link #sendCommand(int, long, String)}.
 */
public class SecondScreenManager implements DisplayManager.DisplayListener {
    private static final String TAG = "wiliwili-second";

    public static final int MODE_OFF = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_LIVE = 2;
    public static final int MODE_HOME = 3;

    // Keep in sync with the native EventType constants
    private static final int EVENT_MODE = 1;
    private static final int EVENT_VIDEO_INFO = 2;
    private static final int EVENT_COMMENTS = 3;
    private static final int EVENT_PLAYER_STATE = 4;
    private static final int EVENT_LIVE_INFO = 5;
    private static final int EVENT_LIVE_MESSAGES = 6;
    private static final int EVENT_RELATED = 7;
    private static final int EVENT_EPISODES = 8;
    private static final int EVENT_THEME = 9;
    private static final int EVENT_STATUS = 10;
    private static final int EVENT_GAMEPAD = 11;

    // Keep in sync with wiliwili::second_screen::GamepadKey
    public static final int KEY_UP = 1;
    public static final int KEY_DOWN = 2;
    public static final int KEY_LEFT = 3;
    public static final int KEY_RIGHT = 4;
    public static final int KEY_A = 5;
    public static final int KEY_B = 6;
    public static final int KEY_X = 7;
    public static final int KEY_Y = 8;
    public static final int KEY_LB = 9;
    public static final int KEY_RB = 10;
    public static final int KEY_START = 11;
    public static final int KEY_BACK = 12;
    public static final int KEY_LT = 13;
    public static final int KEY_RT = 14;

    public static final int CMD_PLAY_PAUSE = 1;
    public static final int CMD_SEEK = 2;
    public static final int CMD_SEEK_RELATIVE = 3;
    public static final int CMD_SET_SPEED = 4;
    public static final int CMD_SET_QUALITY = 5;
    public static final int CMD_TOGGLE_DANMAKU = 6;
    public static final int CMD_LOAD_MORE_COMMENTS = 7;
    public static final int CMD_EXIT_PLAYER = 8;
    public static final int CMD_OPEN_BV = 9;
    public static final int CMD_PLAY_INDEX = 10;
    public static final int CMD_PREV_EPISODE = 11;
    public static final int CMD_NEXT_EPISODE = 12;
    public static final int CMD_SET_VOLUME = 13;
    public static final int CMD_SET_BRIGHTNESS = 14;
    public static final int CMD_TOGGLE_MIRROR = 15;
    public static final int CMD_SET_ASPECT = 16;
    public static final int CMD_SET_SECOND_SCREEN = 17;
    public static final int CMD_OPEN_SEARCH = 18;
    public static final int CMD_OPEN_SETTINGS = 19;
    public static final int CMD_REFRESH_HOME = 20;
    public static final int CMD_MAIN_TAB = 21;

    private static final int LIVE_MESSAGE_LIMIT = 300;

    public static class VideoHeader {
        public String title = "";
        public String up = "";
        public String bvid = "";
        public String desc = "";
        public String pubdate = "";
        public long view;
        public long danmaku;
        public long like;
        public long coin;
        public long favorite;
        public long share;
        public int[] qualityCodes = new int[0];
        public String[] qualityLabels = new String[0];
        public int qualityIndex;
        public boolean hasPrev;
        public boolean hasNext;
    }

    public static class Emote {
        public String url = "";
        public int size = 1;
    }

    public static class Comment {
        public String name = "";
        public String text = "";
        public String time = "";
        public String avatar = "";
        public final Map<String, Emote> emotes = new HashMap<>();
        public long like;
        public long replies;
        public boolean up;
        public boolean top;
    }

    public static class PlayerState {
        public boolean playing;
        public boolean paused;
        public boolean danmaku = true;
        public long position;
        public long duration;
        public double speed = 1.0;
        public int qualityIndex;
        public long volume = 100;
        public long brightness = -1;
        public boolean canSetBrightness;
        public boolean mirror;
        public String aspect = "-1";
    }

    public static class LiveHeader {
        public String title = "";
        public String anchor = "";
        public long online;
        public long watching;
        public long roomid;
    }

    public static class LiveMsg {
        public int type;
        public String user = "";
        public String text = "";
        public String color = "";
        public String bg = "";
        public String medal = "";
        public long price;
    }

    public static class RelatedItem {
        public String title = "";
        public String up = "";
        public String bvid = "";
        public String cover = "";
        public long duration;
        public long view;
    }

    public static class EpisodeItem {
        public int index;
        public String title = "";
        public String subtitle = "";
        public long duration;
        public boolean playable = true;
    }

    public static class Theme {
        public boolean dark;
        public String background = "";
        public String surface = "";
        public String text = "";
        public String textSecondary = "";
        public String accent = "";
        public String divider = "";
        public String name = "";
        public String keymap = "xbox";
        public boolean swapABXY;
    }

    private static SecondScreenManager instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Comment> comments = new ArrayList<>();
    private final List<LiveMsg> liveMessages = new ArrayList<>();
    private final List<RelatedItem> related = new ArrayList<>();
    private final List<EpisodeItem> episodes = new ArrayList<>();

    private Activity activity;
    private DisplayManager displayManager;
    private SecondScreenPresentation presentation;
    private boolean resumed;
    private boolean lastAvailable;

    private int mode = MODE_OFF;
    private VideoHeader videoHeader = new VideoHeader();
    private PlayerState playerState = new PlayerState();
    private LiveHeader liveHeader = new LiveHeader();
    private boolean commentsEnd;
    private int commentsNext;
    private int commentsTotal;

    public static synchronized SecondScreenManager get() {
        if (instance == null) {
            instance = new SecondScreenManager();
        }
        return instance;
    }

    public List<Comment> comments() {
        return comments;
    }

    public List<LiveMsg> liveMessages() {
        return liveMessages;
    }

    public List<RelatedItem> related() {
        return related;
    }

    public List<EpisodeItem> episodes() {
        return episodes;
    }

    // ---------------------------------------------------------------- lifecycle

    public static void attach(Activity activity) {
        if (!BuildConfig.DUAL_SCREEN) return;
        get().doAttach(activity);
    }

    public static void onResume() {
        if (!BuildConfig.DUAL_SCREEN) return;
        get().doResume();
    }

    public static void onPause() {
        if (!BuildConfig.DUAL_SCREEN) return;
        get().doPause();
    }

    public static void detach() {
        if (!BuildConfig.DUAL_SCREEN) return;
        get().doDetach();
    }

    private void doAttach(Activity value) {
        activity = value;
        displayManager = (DisplayManager) value.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(this, handler);
            for (Display display : displayManager.getDisplays()) {
                Log.i(TAG, "display " + display.getDisplayId() + " flags=" + display.getFlags()
                        + " state=" + display.getState()
                        + " " + display.getMode().getPhysicalWidth() + "x"
                        + display.getMode().getPhysicalHeight());
            }
        }
        updateAvailability();
        Log.i(TAG, "attached");
    }

    private void doResume() {
        resumed = true;
        updateAvailability();
        ensurePresentation();
    }

    private void doPause() {
        resumed = false;
        dismiss();
    }

    private void doDetach() {
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(this);
            displayManager = null;
        }
        dismiss();
        activity = null;
        resumed = false;
        setDisplayAvailable(false);
        Log.i(TAG, "detached");
    }

    // ------------------------------------------------------------ native bridge

    /** Called from native code on the SDL thread. */
    public static void onNativeEvent(int type, byte[] payload) {
        if (!BuildConfig.DUAL_SCREEN) return;
        get().handleNativeEvent(type, payload);
    }

    public static void sendCommand(int cmd, long arg, String text) {
        if (!BuildConfig.DUAL_SCREEN) return;
        try {
            nativeCommand(cmd, arg, text);
        } catch (Throwable t) {
            Log.e(TAG, "nativeCommand failed", t);
        }
    }

    private static native void nativeCommand(int cmd, long arg, String text);

    private static native void nativeSetDisplayAvailable(boolean available);

    private static void setDisplayAvailable(boolean available) {
        try {
            nativeSetDisplayAvailable(available);
        } catch (Throwable t) {
            Log.e(TAG, "nativeSetDisplayAvailable failed", t);
        }
    }

    private void handleNativeEvent(int type, byte[] payload) {
        try {
            String json = new String(payload == null ? new byte[0] : payload, StandardCharsets.UTF_8);
            JSONObject object = json.isEmpty() ? new JSONObject() : new JSONObject(json);
            switch (type) {
                case EVENT_MODE: {
                    int value = object.optInt("mode", MODE_OFF);
                    handler.post(() -> applyMode(value));
                    break;
                }
                case EVENT_VIDEO_INFO: {
                    VideoHeader header = parseVideoHeader(object);
                    handler.post(() -> applyVideoHeader(header));
                    break;
                }
                case EVENT_COMMENTS: {
                    boolean reset = object.optBoolean("reset", false);
                    boolean end = object.optBoolean("end", false);
                    int next = object.optInt("next", 0);
                    int total = object.optInt("total", 0);
                    List<Comment> items = parseComments(object.optJSONArray("items"));
                    handler.post(() -> applyComments(reset, end, next, total, items));
                    break;
                }
                case EVENT_PLAYER_STATE: {
                    PlayerState state = parsePlayerState(object);
                    handler.post(() -> applyPlayerState(state));
                    break;
                }
                case EVENT_LIVE_INFO: {
                    LiveHeader header = parseLiveHeader(object);
                    handler.post(() -> applyLiveHeader(header));
                    break;
                }
                case EVENT_LIVE_MESSAGES: {
                    List<LiveMsg> items = parseLiveMessages(object.optJSONArray("items"));
                    handler.post(() -> applyLiveMessages(items));
                    break;
                }
                case EVENT_RELATED: {
                    List<RelatedItem> items = parseRelated(object.optJSONArray("items"));
                    handler.post(() -> applyRelated(items));
                    break;
                }
                case EVENT_EPISODES: {
                    List<EpisodeItem> items = parseEpisodes(object.optJSONArray("items"));
                    handler.post(() -> applyEpisodes(items));
                    break;
                }
                case EVENT_THEME: {
                    Theme theme = parseTheme(object);
                    handler.post(() -> applyTheme(theme));
                    break;
                }
                case EVENT_STATUS: {
                    String text = object.optString("text", "");
                    handler.post(() -> {
                        if (presentation != null && !text.isEmpty()) {
                            presentation.showStatus(text);
                        }
                    });
                    break;
                }
                case EVENT_GAMEPAD: {
                    int key = object.optInt("key", 0);
                    handler.post(() -> {
                        if (presentation != null) {
                            presentation.dispatchGamepadKey(key);
                        }
                    });
                    break;
                }
                default:
                    Log.w(TAG, "unknown native event " + type);
                    break;
            }
        } catch (Throwable t) {
            Log.e(TAG, "onNativeEvent failed", t);
        }
    }

    // ------------------------------------------------------------- model updates

    private void applyMode(int value) {
        mode = value;
        Log.i(TAG, "mode -> " + value);
        if (value == MODE_OFF) {
            dismiss();
            return;
        }
        ensurePresentation();
        if (presentation != null) {
            presentation.setMode(value);
        }
    }

    private void applyVideoHeader(VideoHeader header) {
        if (header.title.isEmpty() && videoHeader != null && !videoHeader.title.isEmpty()
                && (header.bvid.isEmpty() || header.bvid.equals(videoHeader.bvid))) {
            // The player pushes a partial header (same bvid, title not backfilled yet) on resume.
            Log.i(TAG, "ignore partial video header, keep cached title");
            return;
        }
        if (!header.bvid.isEmpty() && !header.bvid.equals(videoHeader.bvid)) {
            comments.clear();
        }
        videoHeader = header;
        if (presentation != null) {
            presentation.bindVideo(videoHeader);
            presentation.refreshComments();
        }
    }

    private void applyComments(boolean reset, boolean end, int next, int total, List<Comment> items) {
        if (reset) {
            comments.clear();
        }
        comments.addAll(items);
        commentsEnd = end;
        commentsNext = next;
        commentsTotal = total;
        if (presentation != null) {
            presentation.setComments(comments, commentsEnd, reset);
        }
    }

    private void applyPlayerState(PlayerState state) {
        playerState = state;
        if (presentation != null) {
            presentation.bindPlayerState(playerState);
        }
    }

    private void applyLiveHeader(LiveHeader header) {
        if (header.roomid != liveHeader.roomid) {
            liveMessages.clear();
        }
        liveHeader = header;
        if (presentation != null) {
            presentation.setLiveHeader(liveHeader);
            presentation.refreshLiveMessages();
        }
    }

    private void applyLiveMessages(List<LiveMsg> items) {
        if (items.isEmpty()) {
            return;
        }
        liveMessages.addAll(items);
        while (liveMessages.size() > LIVE_MESSAGE_LIMIT) {
            liveMessages.remove(0);
        }
        Log.i(TAG, "live messages +" + items.size() + " total=" + liveMessages.size());
        if (presentation != null) {
            presentation.refreshLiveMessages();
        }
    }

    private void applyRelated(List<RelatedItem> items) {
        related.clear();
        related.addAll(items);
        if (presentation != null) {
            presentation.setRelated(related);
        }
    }

    private void applyEpisodes(List<EpisodeItem> items) {
        episodes.clear();
        episodes.addAll(items);
        if (presentation != null) {
            presentation.setEpisodes(episodes);
        }
    }

    private void applyTheme(Theme theme) {
        SecondScreenTheme.apply(theme.dark,
                color(theme.background, SecondScreenTheme.background),
                color(theme.surface, SecondScreenTheme.surface),
                color(theme.text, SecondScreenTheme.text),
                color(theme.textSecondary, SecondScreenTheme.textSecondary),
                color(theme.accent, SecondScreenTheme.accent),
                color(theme.divider, SecondScreenTheme.divider),
                color(theme.name, SecondScreenTheme.name));
        if (theme.keymap != null && !theme.keymap.isEmpty()) {
            SecondScreenTheme.keymap = theme.keymap;
        }
        SecondScreenTheme.swapABXY = theme.swapABXY;
        if (presentation != null) {
            presentation.applyTheme();
        }
    }

    private static int color(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return android.graphics.Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // -------------------------------------------------------------- presentation

    private void ensurePresentation() {
        if (!resumed || mode == MODE_OFF || activity == null || activity.isFinishing()) {
            return;
        }
        Display display = findDisplay();
        if (display == null) {
            dismiss();
            return;
        }
        if (presentation != null) {
            Display current = presentation.getDisplay();
            if (current != null && current.getDisplayId() == display.getDisplayId()) {
                return;
            }
            dismiss();
        }
        try {
            SecondScreenPresentation value = new SecondScreenPresentation(activity, display);
            value.show();
            presentation = value;
            value.setMode(mode);
            value.bindVideo(videoHeader);
            value.bindPlayerState(playerState);
            value.setComments(comments, commentsEnd, false);
            value.setLiveHeader(liveHeader);
            value.setLiveMessages(liveMessages);
            value.applyTheme();
            // A freshly shown Presentation can draw its first frame before the
            // header texts are measured; rebind after layout and once more as a
            // safety net for slow window creation.
            final SecondScreenPresentation shown = value;
            Runnable rebind = () -> {
                if (presentation != shown) {
                    return;
                }
                shown.bindVideo(videoHeader);
                shown.bindPlayerState(playerState);
                shown.setComments(comments, commentsEnd, false);
                shown.setLiveHeader(liveHeader);
                shown.setLiveMessages(liveMessages);
                shown.applyTheme();
            };
            value.getWindow().getDecorView().post(rebind);
            value.getWindow().getDecorView().postDelayed(rebind, 500);
            Log.i(TAG, "presentation shown on display " + display.getDisplayId()
                    + " " + display.getMode().getPhysicalWidth() + "x" + display.getMode().getPhysicalHeight()
                    + " titleLen=" + (videoHeader == null ? 0 : videoHeader.title.length()));
        } catch (Throwable t) {
            Log.e(TAG, "failed to show presentation", t);
            presentation = null;
        }
    }

    private void dismiss() {
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (Throwable t) {
                Log.e(TAG, "dismiss failed", t);
            }
            presentation = null;
        }
    }

    private Display findDisplay() {
        if (displayManager == null) {
            return null;
        }
        Display best = pickDisplay(displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION));
        if (best == null) {
            best = pickDisplay(displayManager.getDisplays());
        }
        return best;
    }

    private static Display pickDisplay(Display[] displays) {
        if (displays == null) {
            return null;
        }
        for (Display display : displays) {
            if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                continue;
            }
            if ((display.getFlags() & Display.FLAG_PRESENTATION) == 0) {
                continue;
            }
            if (display.getState() == Display.STATE_OFF) {
                continue;
            }
            return display;
        }
        return null;
    }

    private void updateAvailability() {
        boolean available = findDisplay() != null;
        if (available == lastAvailable) {
            return;
        }
        lastAvailable = available;
        Log.i(TAG, "display available: " + available);
        setDisplayAvailable(available);
    }

    // --------------------------------------------------------- display listener

    @Override
    public void onDisplayAdded(int displayId) {
        updateAvailability();
        ensurePresentation();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        updateAvailability();
        if (findDisplay() == null) {
            dismiss();
        } else {
            ensurePresentation();
        }
    }

    @Override
    public void onDisplayChanged(int displayId) {
        updateAvailability();
        if (findDisplay() == null) {
            dismiss();
        }
    }

    // ----------------------------------------------------------------- parsing

    private static VideoHeader parseVideoHeader(JSONObject object) {
        VideoHeader header = new VideoHeader();
        header.title = object.optString("title", "");
        header.up = object.optString("up", "");
        header.bvid = object.optString("bvid", "");
        header.desc = object.optString("desc", "");
        header.pubdate = object.optString("pubdate", "");
        header.view = object.optLong("view", 0);
        header.danmaku = object.optLong("danmaku", 0);
        header.like = object.optLong("like", 0);
        header.coin = object.optLong("coin", 0);
        header.favorite = object.optLong("favorite", 0);
        header.share = object.optLong("share", 0);
        header.qualityIndex = object.optInt("qualityIndex", 0);
        header.hasPrev = object.optBoolean("hasPrev", false);
        header.hasNext = object.optBoolean("hasNext", false);

        JSONArray qualities = object.optJSONArray("qualities");
        if (qualities != null) {
            int count = qualities.length();
            header.qualityCodes = new int[count];
            header.qualityLabels = new String[count];
            for (int i = 0; i < count; i++) {
                JSONObject item = qualities.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                header.qualityCodes[i] = item.optInt("code", 0);
                header.qualityLabels[i] = item.optString("label", "");
            }
        }
        return header;
    }

    private static List<Comment> parseComments(JSONArray array) {        List<Comment> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Comment comment = new Comment();
            comment.name = item.optString("name", "");
            comment.text = item.optString("text", "");
            comment.time = item.optString("time", "");
            comment.avatar = item.optString("avatar", "");
            parseEmotes(item.optJSONArray("emotes"), comment.emotes);
            comment.like = item.optLong("like", 0);
            comment.replies = item.optLong("replies", 0);
            comment.up = item.optBoolean("up", false);
            comment.top = item.optBoolean("top", false);
            result.add(comment);
        }
        return result;
    }

    private static void parseEmotes(JSONArray array, Map<String, Emote> target) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String token = item.optString("token", "");
            String url = item.optString("url", "");
            if (token.isEmpty() || url.isEmpty()) {
                continue;
            }
            Emote emote = new Emote();
            emote.url = url.startsWith("//") ? "https:" + url : url;
            emote.size = Math.max(1, item.optInt("size", 1));
            target.put(token, emote);
        }
    }

    private static PlayerState parsePlayerState(JSONObject object) {
        PlayerState state = new PlayerState();
        state.playing = object.optBoolean("playing", false);
        state.paused = object.optBoolean("paused", false);
        state.danmaku = object.optBoolean("danmaku", true);
        state.position = object.optLong("pos", 0);
        state.duration = object.optLong("dur", 0);
        state.speed = object.optDouble("speed", 1.0);
        state.qualityIndex = object.optInt("qualityIndex", 0);
        state.volume = object.optLong("volume", 100);
        state.brightness = object.optLong("brightness", -1);
        state.canSetBrightness = object.optBoolean("canBrightness", false);
        state.mirror = object.optBoolean("mirror", false);
        state.aspect = object.optString("aspect", "-1");
        return state;
    }

    private static LiveHeader parseLiveHeader(JSONObject object) {
        LiveHeader header = new LiveHeader();
        header.title = object.optString("title", "");
        header.anchor = object.optString("anchor", "");
        header.online = object.optLong("online", 0);
        header.watching = object.optLong("watching", 0);
        header.roomid = object.optLong("roomid", 0);
        return header;
    }

    private static List<LiveMsg> parseLiveMessages(JSONArray array) {
        List<LiveMsg> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            LiveMsg message = new LiveMsg();
            message.type = item.optInt("type", 0);
            message.user = item.optString("user", "");
            message.text = item.optString("text", "");
            message.color = item.optString("color", "");
            message.bg = item.optString("bg", "");
            message.medal = item.optString("medal", "");
            message.price = item.optLong("price", 0);
            result.add(message);
        }
        return result;
    }

    private static List<RelatedItem> parseRelated(JSONArray array) {
        List<RelatedItem> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            RelatedItem related = new RelatedItem();
            related.title = item.optString("title", "");
            related.up = item.optString("up", "");
            related.bvid = item.optString("bvid", "");
            related.cover = item.optString("cover", "");
            related.duration = item.optLong("duration", 0);
            related.view = item.optLong("view", 0);
            result.add(related);
        }
        return result;
    }

    private static List<EpisodeItem> parseEpisodes(JSONArray array) {
        List<EpisodeItem> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            EpisodeItem episode = new EpisodeItem();
            episode.index = item.optInt("index", 0);
            episode.title = item.optString("title", "");
            episode.subtitle = item.optString("subtitle", "");
            episode.duration = item.optLong("duration", 0);
            episode.playable = item.optBoolean("playable", true);
            result.add(episode);
        }
        return result;
    }

    private static Theme parseTheme(JSONObject object) {
        Theme theme = new Theme();
        theme.dark = object.optBoolean("dark", false);
        theme.background = object.optString("background", "");
        theme.surface = object.optString("surface", "");
        theme.text = object.optString("text", "");
        theme.textSecondary = object.optString("textSecondary", "");
        theme.accent = object.optString("accent", "");
        theme.divider = object.optString("divider", "");
        theme.name = object.optString("name", "");
        theme.keymap = object.optString("keymap", "xbox");
        theme.swapABXY = object.optBoolean("swap", false);
        return theme;
    }
}
