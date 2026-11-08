package cn.xfangfang.wiliwili.secondscreen;

import android.app.Presentation;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.xfangfang.wiliwili.R;

/**
 * The window shown on the secondary display. The main display only plays the
 * video; this window carries all controls and the info panels.
 */
public class SecondScreenPresentation extends Presentation {
    private static final String TAG = "wiliwili-second";

    private static final int TAB_COMMENTS = 0;  // chat in live mode
    private static final int TAB_RELATED  = 1;
    private static final int TAB_EPISODES = 2;
    private static final int TAB_INFO     = 3;

    private static final String[] SPEED_LABELS = {"1.0x", "1.25x", "1.5x", "2.0x", "0.75x"};
    private static final long[] SPEED_VALUES = {100, 125, 150, 200, 75};

    private static final String[] ASPECT_VALUES = {"-1", "-2", "-3", "4:3", "16:9"};
    private static final String[] ASPECT_LABELS = {"自动", "拉伸", "裁剪", "4:3", "16:9"};

    private static final String[][] HINTS_XBOX = {
            {"A", "确定"}, {"B", "返回"}, {"X", "弹幕"}, {"Y", "更多"},
            {"LB", "快退"}, {"RB", "快进"}, {"START", "清晰度"}, {"BACK", "信息"}
    };
    private static final String[][] HINTS_PS = {
            {"×", "确定"}, {"○", "返回"}, {"□", "弹幕"}, {"△", "更多"},
            {"L1", "快退"}, {"R1", "快进"}, {"OPT", "清晰度"}, {"SHR", "信息"}
    };
    private static final String[][] HINTS_KEYBOARD = {
            {"Space", "播放"}, {"Esc", "返回"}, {"D", "弹幕"}, {"O", "更多"},
            {"[", "快退"}, {"]", "快进"}, {"F2", "清晰度"}, {"F1", "信息"}
    };

    private TextView titleView;
    private TextView subtitleView;
    private TextView liveBadge;
    private TextView onlineView;
    private TextView tabComments;
    private TextView tabRelated;
    private TextView tabEpisodes;
    private TextView tabInfo;
    private View tabCommentsBox;
    private View tabRelatedBox;
    private View tabEpisodesBox;
    private View tabInfoBox;
    private View tabCommentsIndicator;
    private View tabRelatedIndicator;
    private View tabEpisodesIndicator;
    private View tabInfoIndicator;
    private View rootView;
    private View secondMainView;
    private View homeView;
    private View homeDivider;
    private TextView homeTitle;
    private TextView homeSubtitle;
    private TextView homeMessage;
    private TextView homeSection;
    private TextView homeHint;
    private TextView homeTabHome;
    private TextView homeTabDynamic;
    private TextView homeTabMine;
    private TextView homeSearch;
    private TextView homeSettings;
    private TextView homeRefresh;
    private TextView homeClose;
    private View headerDivider;
    private View tabsDivider;
    private View controlsDivider;
    private TextView positionView;
    private TextView durationView;
    private ImageView playView;
    private TextView qualityView;
    private ImageView danmakuView;
    private TextView exitView;
    private ImageView prevView;
    private ImageView rewindView;
    private ImageView forwardView;
    private ImageView nextView;
    private ImageView moreView;
    private SeekBar seekBar;
    private View seekRow;
    private ListView listView;
    private ScrollView infoScroll;
    private TextView infoText;
    private TextView emptyView;
    private TextView statusView;
    private LinearLayout hintsBox;
    private android.widget.FrameLayout overlayView;
    private LinearLayout overlayPanel;

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideStatus = () -> {
        if (statusView == null) {
            return;
        }
        statusView.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> statusView.setVisibility(View.GONE));
    };

    private int commentsScroll;
    private int relatedScroll;
    private int episodesScroll;
    private int commentsScrollTop;
    private int relatedScrollTop;
    private int episodesScrollTop;
    private int infoScrollY;

    private interface PickerListener {
        void onPick(int index);
    }

    private interface SliderListener {
        void onValue(int value);
    }

    private CommentAdapter commentAdapter;
    private LiveMessageAdapter liveAdapter;
    private RelatedAdapter relatedAdapter;
    private EpisodeAdapter episodeAdapter;

    private SecondScreenManager.VideoHeader videoHeader;
    private SecondScreenManager.PlayerState playerState = new SecondScreenManager.PlayerState();
    private SecondScreenManager.LiveHeader liveHeader;
    private int mode = SecondScreenManager.MODE_OFF;
    private int currentTab = TAB_COMMENTS;
    private String lastBvid = "";
    private boolean commentsEnd;
    private boolean dragging;
    private long durationSeconds;
    private long lastLoadMoreTime;

    /** The view that owns the gamepad focus ring, and the overlay's own focus list. */
    private View mainFocus;
    private View focusedView;
    private final List<View> overlayFocusTargets = new ArrayList<>();
    private int overlayFocusIndex;

    public SecondScreenPresentation(Context context, Display display) {
        super(context, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.presentation_second_screen);

        titleView = findViewById(R.id.second_title);
        subtitleView = findViewById(R.id.second_subtitle);
        liveBadge = findViewById(R.id.second_live_badge);
        onlineView = findViewById(R.id.second_online);
        tabComments = findViewById(R.id.second_tab_comments);
        tabRelated = findViewById(R.id.second_tab_related);
        tabEpisodes = findViewById(R.id.second_tab_episodes);
        tabInfo = findViewById(R.id.second_tab_info);
        tabCommentsBox = findViewById(R.id.second_tab_comments_box);
        tabRelatedBox = findViewById(R.id.second_tab_related_box);
        tabEpisodesBox = findViewById(R.id.second_tab_episodes_box);
        tabInfoBox = findViewById(R.id.second_tab_info_box);
        tabCommentsIndicator = findViewById(R.id.second_tab_comments_indicator);
        tabRelatedIndicator = findViewById(R.id.second_tab_related_indicator);
        tabEpisodesIndicator = findViewById(R.id.second_tab_episodes_indicator);
        tabInfoIndicator = findViewById(R.id.second_tab_info_indicator);
        rootView = findViewById(R.id.second_root);
        secondMainView = findViewById(R.id.second_main);
        homeView = findViewById(R.id.second_home);
        homeDivider = findViewById(R.id.second_home_divider);
        homeTitle = findViewById(R.id.second_home_title);
        homeSubtitle = findViewById(R.id.second_home_subtitle);
        homeMessage = findViewById(R.id.second_home_message);
        homeSection = findViewById(R.id.second_home_section);
        homeHint = findViewById(R.id.second_home_hint);
        homeTabHome = findViewById(R.id.second_home_tab_home);
        homeTabDynamic = findViewById(R.id.second_home_tab_dynamic);
        homeTabMine = findViewById(R.id.second_home_tab_mine);
        homeSearch = findViewById(R.id.second_home_search);
        homeSettings = findViewById(R.id.second_home_settings);
        homeRefresh = findViewById(R.id.second_home_refresh);
        homeClose = findViewById(R.id.second_home_close);
        // Home is touch-only; Android's default focus highlight would light up the first button.
        TextView[] homeButtons = {homeTabHome, homeTabDynamic, homeTabMine, homeSearch,
                homeSettings, homeRefresh, homeClose};
        for (TextView button : homeButtons) {
            button.setFocusable(false);
            button.setDefaultFocusHighlightEnabled(false);
        }
        headerDivider = findViewById(R.id.second_header_divider);
        tabsDivider = findViewById(R.id.second_tabs_divider);
        controlsDivider = findViewById(R.id.second_controls_divider);
        positionView = findViewById(R.id.second_position);
        durationView = findViewById(R.id.second_duration);
        playView = findViewById(R.id.second_play);
        qualityView = findViewById(R.id.second_quality);
        danmakuView = findViewById(R.id.second_danmaku);
        exitView = findViewById(R.id.second_exit);
        prevView = findViewById(R.id.second_prev);
        rewindView = findViewById(R.id.second_rewind);
        forwardView = findViewById(R.id.second_forward);
        nextView = findViewById(R.id.second_next);
        moreView = findViewById(R.id.second_more);
        hintsBox = findViewById(R.id.second_hints);
        overlayView = findViewById(R.id.second_overlay);
        overlayPanel = findViewById(R.id.second_overlay_panel);
        overlayView.setOnClickListener(v -> hideOverlay());
        seekBar = findViewById(R.id.second_seek);
        seekRow = findViewById(R.id.second_seek_row);
        listView = findViewById(R.id.second_list);
        infoScroll = findViewById(R.id.second_info_scroll);
        infoText = findViewById(R.id.second_info_text);
        emptyView = findViewById(R.id.second_empty);
        statusView = findViewById(R.id.second_status);

        SecondScreenManager manager = SecondScreenManager.get();
        commentAdapter = new CommentAdapter(getContext(), manager.comments());
        liveAdapter = new LiveMessageAdapter(getContext(), manager.liveMessages());
        relatedAdapter = new RelatedAdapter(getContext(), manager.related());
        episodeAdapter = new EpisodeAdapter(getContext(), manager.episodes());
        listView.setAdapter(commentAdapter);

        tabCommentsBox.setOnClickListener(v -> onTabClicked(TAB_COMMENTS));
        tabRelatedBox.setOnClickListener(v -> onTabClicked(TAB_RELATED));
        tabEpisodesBox.setOnClickListener(v -> onTabClicked(TAB_EPISODES));
        tabInfoBox.setOnClickListener(v -> onTabClicked(TAB_INFO));

        playView.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_PLAY_PAUSE, 0, null));
        qualityView.setOnClickListener(v -> showQualityPicker());
        danmakuView.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_TOGGLE_DANMAKU, 0, null));
        moreView.setOnClickListener(v -> showMoreOverlay());
        exitView.setOnClickListener(v -> showExitConfirm());
        prevView.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_PREV_EPISODE, 0, null));
        nextView.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_NEXT_EPISODE, 0, null));
        rewindView.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_SEEK_RELATIVE, -10, null));
        forwardView.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_SEEK_RELATIVE, 10, null));

        homeTabHome.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_MAIN_TAB, 0, null));
        homeTabDynamic.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_MAIN_TAB, 1, null));
        homeTabMine.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_MAIN_TAB, 2, null));
        homeSearch.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_OPEN_SEARCH, 0, ""));
        homeSettings.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_OPEN_SETTINGS, 0, null));
        homeRefresh.setOnClickListener(v ->
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_REFRESH_HOME, 0, null));
        homeClose.setOnClickListener(v ->
                showConfirmOverlay("关闭下屏？可在设置中重新开启", "取消", "关闭",
                        () -> SecondScreenManager.sendCommand(
                                SecondScreenManager.CMD_SET_SECOND_SCREEN, 0, null)));

        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (currentTab == TAB_RELATED) {
                SecondScreenManager.RelatedItem item = relatedAdapter.getItem(position);
                if (item != null && !item.bvid.isEmpty()) {
                    SecondScreenManager.sendCommand(SecondScreenManager.CMD_OPEN_BV, 0, item.bvid);
                }
            } else if (currentTab == TAB_EPISODES) {
                SecondScreenManager.EpisodeItem item = episodeAdapter.getItem(position);
                if (item != null && item.playable) {
                    SecondScreenManager.sendCommand(SecondScreenManager.CMD_PLAY_INDEX, item.index, null);
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (dragging && durationSeconds > 0) {
                    positionView.setText(formatTime((long) (progress / 1000.0 * durationSeconds)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                long target = (long) (bar.getProgress() / 1000.0 * durationSeconds);
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_SEEK, target, null);
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                if (mode != SecondScreenManager.MODE_VIDEO || currentTab != TAB_COMMENTS
                        || commentsEnd || totalCount == 0) {
                    return;
                }
                if (firstVisible + visibleCount < totalCount - 2) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastLoadMoreTime < 1000) {
                    return;
                }
                lastLoadMoreTime = now;
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_LOAD_MORE_COMMENTS, 0, null);
            }
        });

        selectTab(TAB_COMMENTS);
        // Focus ring slightly scales the buttons; do not clip it inside the rows.
        if (playView.getParent() instanceof ViewGroup) {
            ((ViewGroup) playView.getParent()).setClipChildren(false);
            if (playView.getParent().getParent() instanceof ViewGroup) {
                ((ViewGroup) playView.getParent().getParent()).setClipChildren(false);
            }
        }
        if (tabCommentsBox.getParent() instanceof ViewGroup) {
            ((ViewGroup) tabCommentsBox.getParent()).setClipChildren(false);
        }
        overlayPanel.setClipChildren(false);
        applyTheme();
    }

    // ----------------------------------------------------------------- mode

    void setMode(int value) {
        boolean modeChanged = mode != value;
        mode = value;

        if (value == SecondScreenManager.MODE_HOME) {
            if (secondMainView != null) secondMainView.setVisibility(View.GONE);
            if (homeView != null) homeView.setVisibility(View.VISIBLE);
            if (overlayView != null) overlayView.setVisibility(View.GONE);
            overlayFocusTargets.clear();
            clearFocusRing(focusedView);
            focusedView = null;
            mainFocus = null;
            return;
        }

        if (secondMainView != null) secondMainView.setVisibility(View.VISIBLE);
        if (homeView != null) homeView.setVisibility(View.GONE);

        boolean live = value == SecondScreenManager.MODE_LIVE;

        tabComments.setText(live ? "弹幕" : "评论");
        tabRelatedBox.setVisibility(live ? View.GONE : View.VISIBLE);
        tabEpisodesBox.setVisibility(live ? View.GONE : View.VISIBLE);
        if (live) {
            selectTab(TAB_COMMENTS);
        } else {
            updateTabVisibility();
        }

        seekRow.setVisibility(live ? View.GONE : View.VISIBLE);
        liveBadge.setVisibility(live ? View.VISIBLE : View.GONE);
        onlineView.setVisibility(live ? View.VISIBLE : View.GONE);
        if (live) {
            commentsScroll = 0;
            commentsScrollTop = 0;
        }
        updateTransportVisibility();
        updateEmptyState();
        buildHints();
        if (modeChanged || mainFocus == null) {
            // Entering the player: the lower screen takes over immediately.
            mainFocus = playView;
        }
        ensureMainFocus();
        refreshFocusVisual();
    }

    /** Previous/next only exist for seasons and multi-P videos; live has no transport. */
    private void updateTransportVisibility() {
        boolean live = mode == SecondScreenManager.MODE_LIVE;
        boolean hasPrev = !live && videoHeader != null && videoHeader.hasPrev;
        boolean hasNext = !live && videoHeader != null && videoHeader.hasNext;
        prevView.setVisibility(hasPrev ? View.VISIBLE : View.GONE);
        nextView.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        rewindView.setVisibility(live ? View.GONE : View.VISIBLE);
        forwardView.setVisibility(live ? View.GONE : View.VISIBLE);
        ensureMainFocus();
        refreshFocusVisual();
    }

    private void updateTabVisibility() {
        boolean live = mode == SecondScreenManager.MODE_LIVE;
        if (!live) {
            tabRelatedBox.setVisibility(relatedAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
            tabEpisodesBox.setVisibility(episodeAdapter.getCount() > 0 ? View.VISIBLE : View.GONE);
        }
        if (currentTab == TAB_RELATED && tabRelatedBox.getVisibility() != View.VISIBLE) {
            selectTab(TAB_COMMENTS);
        } else if (currentTab == TAB_EPISODES && tabEpisodesBox.getVisibility() != View.VISIBLE) {
            selectTab(TAB_COMMENTS);
        }
        ensureMainFocus();
        refreshFocusVisual();
    }

    private void selectTab(int tab) {
        if (currentTab != tab) {
            rememberScroll();
        }
        currentTab = tab;
        boolean live = mode == SecondScreenManager.MODE_LIVE;

        if (tab == TAB_INFO) {
            listView.setVisibility(View.GONE);
            infoScroll.setVisibility(View.VISIBLE);
            infoText.setText(buildInfoText());
            final int saved = infoScrollY;
            infoScroll.post(() -> infoScroll.scrollTo(0, saved));
        } else {
            infoScroll.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            if (tab == TAB_COMMENTS) {
                listView.setAdapter(live ? liveAdapter : commentAdapter);
                listView.setStackFromBottom(live);
                listView.setTranscriptMode(live
                        ? AbsListView.TRANSCRIPT_MODE_NORMAL
                        : AbsListView.TRANSCRIPT_MODE_DISABLED);
            } else if (tab == TAB_RELATED) {
                listView.setAdapter(relatedAdapter);
                listView.setStackFromBottom(false);
                listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
            } else {
                listView.setAdapter(episodeAdapter);
                listView.setStackFromBottom(false);
                listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
            }
            if (!live) {
                int saved = savedScroll(tab);
                int savedTop = savedScrollTop(tab);
                if (saved > 0 || savedTop != 0) {
                    listView.setSelectionFromTop(saved, savedTop);
                }
            }
        }
        updateTabColors();
        updateEmptyState();
    }

    /** Clicking the active tab scrolls its content back to the top. */
    private void onTabClicked(int tab) {
        if (tab != currentTab) {
            selectTab(tab);
            return;
        }
        if (tab == TAB_INFO) {
            infoScrollY = 0;
            infoScroll.smoothScrollTo(0, 0);
        } else if (mode != SecondScreenManager.MODE_LIVE) {
            setSavedScroll(tab, 0);
            setSavedScrollTop(tab, 0);
            listView.smoothScrollToPosition(0);
        }
    }

    private void rememberScroll() {
        if (currentTab == TAB_INFO) {
            infoScrollY = infoScroll.getScrollY();
            return;
        }
        if (listView.getVisibility() != View.VISIBLE) {
            return;
        }
        int position = listView.getFirstVisiblePosition();
        View first = listView.getChildAt(0);
        int top = first != null ? first.getTop() : 0;
        if (currentTab == TAB_COMMENTS) {
            commentsScroll = position;
            commentsScrollTop = top;
        } else if (currentTab == TAB_RELATED) {
            relatedScroll = position;
            relatedScrollTop = top;
        } else if (currentTab == TAB_EPISODES) {
            episodesScroll = position;
            episodesScrollTop = top;
        }
    }

    private int savedScroll(int tab) {
        if (tab == TAB_COMMENTS) {
            return commentsScroll;
        }
        if (tab == TAB_RELATED) {
            return relatedScroll;
        }
        if (tab == TAB_EPISODES) {
            return episodesScroll;
        }
        return 0;
    }

    private int savedScrollTop(int tab) {
        if (tab == TAB_COMMENTS) {
            return commentsScrollTop;
        }
        if (tab == TAB_RELATED) {
            return relatedScrollTop;
        }
        if (tab == TAB_EPISODES) {
            return episodesScrollTop;
        }
        return 0;
    }

    private void setSavedScroll(int tab, int value) {
        if (tab == TAB_COMMENTS) {
            commentsScroll = value;
        } else if (tab == TAB_RELATED) {
            relatedScroll = value;
        } else if (tab == TAB_EPISODES) {
            episodesScroll = value;
        }
    }

    private void setSavedScrollTop(int tab, int value) {
        if (tab == TAB_COMMENTS) {
            commentsScrollTop = value;
        } else if (tab == TAB_RELATED) {
            relatedScrollTop = value;
        } else if (tab == TAB_EPISODES) {
            episodesScrollTop = value;
        }
    }

    /** Show a placeholder when the active list has nothing to display yet. */
    private void updateEmptyState() {
        if (currentTab == TAB_INFO) {
            emptyView.setVisibility(View.GONE);
            return;
        }
        String text = null;
        if (currentTab == TAB_COMMENTS) {
            if (mode == SecondScreenManager.MODE_LIVE) {
                if (liveAdapter.getCount() == 0) {
                    text = "等待弹幕…";
                }
            } else if (commentAdapter.getCount() == 0) {
                text = commentsEnd ? "暂无评论" : "正在加载评论…";
            }
        } else if (currentTab == TAB_RELATED) {
            if (relatedAdapter.getCount() == 0) {
                text = "暂无推荐";
            }
        } else if (currentTab == TAB_EPISODES) {
            if (episodeAdapter.getCount() == 0) {
                text = "暂无分集";
            }
        }
        if (text == null) {
            emptyView.setVisibility(View.GONE);
        } else {
            emptyView.setText(text);
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    private void updateTabColors() {
        int active = SecondScreenTheme.accent;
        int inactive = SecondScreenTheme.dark ? 0xFFCCCCCC : 0xFF61666D;
        tabComments.setTextColor(currentTab == TAB_COMMENTS ? active : inactive);
        tabRelated.setTextColor(currentTab == TAB_RELATED ? active : inactive);
        tabEpisodes.setTextColor(currentTab == TAB_EPISODES ? active : inactive);
        tabInfo.setTextColor(currentTab == TAB_INFO ? active : inactive);
        tabCommentsIndicator.setBackgroundColor(active);
        tabRelatedIndicator.setBackgroundColor(active);
        tabEpisodesIndicator.setBackgroundColor(active);
        tabInfoIndicator.setBackgroundColor(active);
        tabCommentsIndicator.setVisibility(currentTab == TAB_COMMENTS ? View.VISIBLE : View.GONE);
        tabRelatedIndicator.setVisibility(currentTab == TAB_RELATED ? View.VISIBLE : View.GONE);
        tabEpisodesIndicator.setVisibility(currentTab == TAB_EPISODES ? View.VISIBLE : View.GONE);
        tabInfoIndicator.setVisibility(currentTab == TAB_INFO ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ gamepad focus

    /**
     * Native forwards gamepad buttons here while the player owns the lower
     * screen. D-pad moves the focus ring, A activates, B goes back.
     */
    void dispatchGamepadKey(int key) {
        if (overlayView != null && overlayView.getVisibility() == View.VISIBLE) {
            handleOverlayKey(key);
        } else if (mode != SecondScreenManager.MODE_HOME) {
            handleMainKey(key);
        }
    }

    private void handleMainKey(int key) {
        switch (key) {
            case SecondScreenManager.KEY_UP:
                moveMainFocusRow(0);
                break;
            case SecondScreenManager.KEY_DOWN:
                moveMainFocusRow(1);
                break;
            case SecondScreenManager.KEY_LEFT:
                moveMainColumn(-1);
                break;
            case SecondScreenManager.KEY_RIGHT:
                moveMainColumn(1);
                break;
            case SecondScreenManager.KEY_A:
                if (isVisible(mainFocus)) {
                    mainFocus.performClick();
                }
                break;
            case SecondScreenManager.KEY_B:
                showExitConfirm();
                break;
            case SecondScreenManager.KEY_X:
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_TOGGLE_DANMAKU, 0, null);
                break;
            case SecondScreenManager.KEY_Y:
                showMoreOverlay();
                break;
            case SecondScreenManager.KEY_LB:
                if (mode != SecondScreenManager.MODE_LIVE) {
                    SecondScreenManager.sendCommand(SecondScreenManager.CMD_SEEK_RELATIVE, -10, null);
                }
                break;
            case SecondScreenManager.KEY_RB:
                if (mode != SecondScreenManager.MODE_LIVE) {
                    SecondScreenManager.sendCommand(SecondScreenManager.CMD_SEEK_RELATIVE, 10, null);
                }
                break;
            case SecondScreenManager.KEY_LT:
                switchTabRelative(-1);
                break;
            case SecondScreenManager.KEY_RT:
                switchTabRelative(1);
                break;
            case SecondScreenManager.KEY_START:
                showQualityPicker();
                break;
            case SecondScreenManager.KEY_BACK:
                selectTab(TAB_INFO);
                mainFocus = tabBox(TAB_INFO);
                refreshFocusVisual();
                break;
            default:
                break;
        }
    }

    private void handleOverlayKey(int key) {
        switch (key) {
            case SecondScreenManager.KEY_UP:
                moveOverlayFocus(-1);
                break;
            case SecondScreenManager.KEY_DOWN:
                moveOverlayFocus(1);
                break;
            case SecondScreenManager.KEY_LEFT:
                adjustOverlaySlider(-1);
                break;
            case SecondScreenManager.KEY_RIGHT:
                adjustOverlaySlider(1);
                break;
            case SecondScreenManager.KEY_A:
                activateOverlayFocus();
                break;
            case SecondScreenManager.KEY_B:
                hideOverlay();
                break;
            default:
                break;
        }
    }

    private List<View> tabTargets() {
        List<View> targets = new ArrayList<>();
        addVisible(targets, tabCommentsBox);
        addVisible(targets, tabRelatedBox);
        addVisible(targets, tabEpisodesBox);
        addVisible(targets, tabInfoBox);
        return targets;
    }

    private List<View> controlTargets() {
        List<View> targets = new ArrayList<>();
        addVisible(targets, prevView);
        addVisible(targets, rewindView);
        addVisible(targets, playView);
        addVisible(targets, forwardView);
        addVisible(targets, nextView);
        addVisible(targets, danmakuView);
        addVisible(targets, qualityView);
        addVisible(targets, moreView);
        return targets;
    }

    private void addVisible(List<View> targets, View view) {
        if (isVisible(view)) {
            targets.add(view);
        }
    }

    private View tabBox(int tab) {
        switch (tab) {
            case TAB_COMMENTS:
                return tabCommentsBox;
            case TAB_RELATED:
                return tabRelatedBox;
            case TAB_EPISODES:
                return tabEpisodesBox;
            case TAB_INFO:
                return tabInfoBox;
            default:
                return null;
        }
    }

    private static boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /** Keep the focus on a visible control; fall back to play after mode changes. */
    private void ensureMainFocus() {
        List<View> tabs = tabTargets();
        List<View> controls = controlTargets();
        if (mainFocus != null && (tabs.contains(mainFocus) || controls.contains(mainFocus))) {
            return;
        }
        if (isVisible(playView)) {
            mainFocus = playView;
        } else if (!controls.isEmpty()) {
            mainFocus = controls.get(0);
        } else if (!tabs.isEmpty()) {
            mainFocus = tabs.get(0);
        } else {
            mainFocus = null;
        }
    }

    private void moveMainFocusRow(int row) {
        if (row == 0) {
            View target = tabBox(currentTab);
            if (!isVisible(target) || !tabTargets().contains(target)) {
                List<View> tabs = tabTargets();
                target = tabs.isEmpty() ? null : tabs.get(0);
            }
            mainFocus = target;
        } else {
            List<View> controls = controlTargets();
            mainFocus = isVisible(playView) ? playView : (controls.isEmpty() ? mainFocus : controls.get(0));
        }
        refreshFocusVisual();
    }

    private void moveMainColumn(int delta) {
        List<View> targets = tabTargets().contains(mainFocus) ? tabTargets() : controlTargets();
        if (targets.isEmpty()) {
            return;
        }
        int index = targets.indexOf(mainFocus);
        if (index < 0) {
            index = 0;
        }
        index = Math.max(0, Math.min(targets.size() - 1, index + delta));
        mainFocus = targets.get(index);
        refreshFocusVisual();
    }

    private void switchTabRelative(int delta) {
        List<Integer> tabs = new ArrayList<>();
        if (isVisible(tabCommentsBox)) tabs.add(TAB_COMMENTS);
        if (isVisible(tabRelatedBox)) tabs.add(TAB_RELATED);
        if (isVisible(tabEpisodesBox)) tabs.add(TAB_EPISODES);
        if (isVisible(tabInfoBox)) tabs.add(TAB_INFO);
        if (tabs.isEmpty()) {
            return;
        }
        int index = tabs.indexOf(currentTab);
        if (index < 0) {
            index = 0;
        }
        index = (index + delta + tabs.size()) % tabs.size();
        int tab = tabs.get(index);
        selectTab(tab);
        mainFocus = tabBox(tab);
        refreshFocusVisual();
    }

    private void refreshFocusVisual() {
        View target;
        if (overlayView != null && overlayView.getVisibility() == View.VISIBLE) {
            if (overlayFocusTargets.isEmpty()) {
                target = null;
            } else {
                overlayFocusIndex = Math.max(0, Math.min(overlayFocusTargets.size() - 1, overlayFocusIndex));
                target = overlayFocusTargets.get(overlayFocusIndex);
            }
        } else if (mode == SecondScreenManager.MODE_HOME) {
            target = null;
        } else {
            ensureMainFocus();
            target = mainFocus;
        }
        if (target == focusedView) {
            return;
        }
        clearFocusRing(focusedView);
        focusedView = target;
        applyFocusRing(focusedView);
    }

    private void applyFocusRing(View view) {
        if (view == null) {
            return;
        }
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.RECTANGLE);
        ring.setCornerRadius(dp(8));
        // A translucent accent fill makes rows read as focused without hiding the text.
        ring.setColor((SecondScreenTheme.accent & 0x00FFFFFF) | 0x22000000);
        ring.setStroke(dp(2), view == playView ? SecondScreenTheme.onAccent : SecondScreenTheme.accent);
        view.setForeground(ring);
        view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(90).start();
    }

    private void clearFocusRing(View view) {
        if (view == null) {
            return;
        }
        view.setForeground(null);
        view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
    }

    private void setOverlayTargets(List<View> targets, int defaultIndex) {
        overlayFocusTargets.clear();
        overlayFocusTargets.addAll(targets);
        overlayFocusIndex = targets.isEmpty()
                ? 0 : Math.max(0, Math.min(targets.size() - 1, defaultIndex));
        refreshFocusVisual();
    }

    private void moveOverlayFocus(int delta) {
        if (overlayFocusTargets.isEmpty()) {
            return;
        }
        overlayFocusIndex = (overlayFocusIndex + delta + overlayFocusTargets.size())
                % overlayFocusTargets.size();
        refreshFocusVisual();
    }

    private void activateOverlayFocus() {
        if (overlayFocusTargets.isEmpty()) {
            return;
        }
        View target = overlayFocusTargets.get(overlayFocusIndex);
        if (!(target instanceof SeekBar)) {
            target.performClick();
        }
    }

    private void adjustOverlaySlider(int delta) {
        if (overlayFocusTargets.isEmpty()) {
            return;
        }
        View target = overlayFocusTargets.get(overlayFocusIndex);
        if (target instanceof SeekBar) {
            SeekBar bar = (SeekBar) target;
            int step = Math.max(1, bar.getMax() / 50);
            bar.setProgress(Math.max(0, Math.min(bar.getMax(), bar.getProgress() + delta * step)));
        }
    }

    /** Re-apply the wiliwili theme (colors + spacing) to every view. */
    void applyTheme() {
        int background = SecondScreenTheme.background;
        int surface = SecondScreenTheme.surface;
        int text = SecondScreenTheme.text;
        int secondary = SecondScreenTheme.textSecondary;
        int accent = SecondScreenTheme.accent;
        int divider = SecondScreenTheme.divider;

        rootView.setBackgroundColor(background);
        findViewById(R.id.second_header).setBackgroundColor(surface);
        findViewById(R.id.second_tabs).setBackgroundColor(surface);
        findViewById(R.id.second_controls).setBackgroundColor(surface);
        listView.setBackgroundColor(surface);
        infoScroll.setBackgroundColor(surface);
        headerDivider.setBackgroundColor(divider);
        tabsDivider.setBackgroundColor(divider);
        controlsDivider.setBackgroundColor(divider);
        findViewById(R.id.second_hints_divider).setBackgroundColor(divider);
        findViewById(R.id.second_hints_scroll).setBackgroundColor(surface);
        findViewById(R.id.second_group_divider).setBackgroundColor(divider);

        titleView.setTextColor(text);
        subtitleView.setTextColor(secondary);
        onlineView.setTextColor(secondary);
        liveBadge.setBackgroundColor(accent);
        positionView.setTextColor(secondary);
        durationView.setTextColor(secondary);
        infoText.setTextColor(text);
        emptyView.setTextColor(secondary);

        if (homeView != null) {
            homeView.setBackgroundColor(background);
            homeDivider.setBackgroundColor(divider);
            homeTitle.setTextColor(text);
            homeSubtitle.setTextColor(secondary);
            homeMessage.setTextColor(text);
            homeSection.setTextColor(text);
            homeHint.setTextColor(secondary);
            styleButton(homeSearch, accent, SecondScreenTheme.onAccent);
            styleButton(homeTabHome, SecondScreenTheme.buttonBackground, text);
            styleButton(homeTabDynamic, SecondScreenTheme.buttonBackground, text);
            styleButton(homeTabMine, SecondScreenTheme.buttonBackground, text);
            styleButton(homeSettings, SecondScreenTheme.buttonBackground, text);
            styleButton(homeRefresh, SecondScreenTheme.buttonBackground, text);
            styleButton(homeClose, SecondScreenTheme.buttonBackground, text);
        }

        styleButton(qualityView, SecondScreenTheme.buttonBackground, text);
        styleButton(exitView, SecondScreenTheme.buttonBackground, text);
        styleIconButton(playView, accent, SecondScreenTheme.onAccent);
        styleIconButton(prevView, SecondScreenTheme.buttonBackground, text);
        styleIconButton(rewindView, SecondScreenTheme.buttonBackground, text);
        styleIconButton(forwardView, SecondScreenTheme.buttonBackground, text);
        styleIconButton(nextView, SecondScreenTheme.buttonBackground, text);
        styleIconButton(moreView, SecondScreenTheme.buttonBackground, text);
        styleIconButton(danmakuView, SecondScreenTheme.buttonBackground,
                playerState.danmaku ? accent : secondary);

        seekBar.setProgressTintList(ColorStateList.valueOf(accent));
        seekBar.setThumbTintList(ColorStateList.valueOf(accent));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(divider));

        buildHints();
        updateTabColors();
        commentAdapter.notifyDataSetChanged();
        liveAdapter.notifyDataSetChanged();
        relatedAdapter.notifyDataSetChanged();
        episodeAdapter.notifyDataSetChanged();
        if (currentTab == TAB_INFO) {
            infoText.setText(buildInfoText());
        }
        // Re-create the ring with the new accent color.
        clearFocusRing(focusedView);
        focusedView = null;
        if (mode == SecondScreenManager.MODE_HOME) {
            mainFocus = null;
        } else {
            refreshFocusVisual();
        }
    }

    private void styleButton(TextView button, int backgroundColor, int textColor) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                pill(blend(backgroundColor, 0xFF000000, 0.12f)));
        states.addState(new int[]{}, pill(backgroundColor));
        button.setBackground(states);
        button.setTextColor(textColor);
    }

    private GradientDrawable pill(int color) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(getContext().getResources().getDisplayMetrics().density * 6);
        pill.setColor(color);
        return pill;
    }

    private void styleIconButton(ImageView button, int backgroundColor, int iconColor) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                pill(blend(backgroundColor, 0xFF000000, 0.12f)));
        states.addState(new int[]{}, pill(backgroundColor));
        button.setBackground(states);
        button.setImageTintList(ColorStateList.valueOf(iconColor));
    }

    /** Reserved bottom strip: current gamepad mappings for the player. */
    private void buildHints() {
        if (hintsBox == null) {
            return;
        }
        hintsBox.removeAllViews();

        String keymap = SecondScreenTheme.keymap;
        String[][] hints;
        String prefix;
        if ("ps".equals(keymap)) {
            hints = HINTS_PS;
            prefix = "手柄";
        } else if ("keyboard".equals(keymap)) {
            hints = HINTS_KEYBOARD;
            prefix = "键盘";
        } else {
            hints = HINTS_XBOX;
            prefix = "手柄";
        }

        TextView prefixView = new TextView(getContext());
        prefixView.setText(prefix);
        prefixView.setTextSize(9);
        prefixView.setTextColor(SecondScreenTheme.textSecondary);
        LinearLayout.LayoutParams prefixParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prefixParams.rightMargin = dp(6);
        hintsBox.addView(prefixView, prefixParams);

        for (String[] hint : hints) {
            if (mode == SecondScreenManager.MODE_LIVE
                    && ("LB".equals(hint[0]) || "RB".equals(hint[0])
                    || "L1".equals(hint[0]) || "R1".equals(hint[0])
                    || "[".equals(hint[0]) || "]".equals(hint[0]))) {
                continue;
            }
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemParams.rightMargin = dp(8);
            hintsBox.addView(item, itemParams);

            TextView badge = new TextView(getContext());
            badge.setText(SecondScreenTheme.swapABXY && !"keyboard".equals(keymap)
                    ? swapKeyLabel(hint[0]) : hint[0]);
            badge.setTextSize(8);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(SecondScreenTheme.text);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(3));
            background.setColor(SecondScreenTheme.buttonBackground);
            badge.setBackground(background);
            badge.setPadding(dp(4), dp(1), dp(4), dp(1));
            item.addView(badge);

            TextView label = new TextView(getContext());
            label.setText(hint[1]);
            label.setTextSize(9);
            label.setTextColor(SecondScreenTheme.textSecondary);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.leftMargin = dp(3);
            item.addView(label, labelParams);
        }
        if (tabTargets().size() > 1) {
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            hintsBox.addView(item);
            TextView badge = new TextView(getContext());
            badge.setText("LT/RT");
            badge.setTextSize(8);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(SecondScreenTheme.text);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(3));
            background.setColor(SecondScreenTheme.buttonBackground);
            badge.setBackground(background);
            badge.setPadding(dp(4), dp(1), dp(4), dp(1));
            item.addView(badge);
            TextView label = new TextView(getContext());
            label.setText("切页");
            label.setTextSize(9);
            label.setTextColor(SecondScreenTheme.textSecondary);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.leftMargin = dp(3);
            item.addView(label, labelParams);
        }
    }

    /** Display the physical button label when the user enabled A/B and X/Y swap. */
    private static String swapKeyLabel(String label) {
        switch (label) {
            case "A":
                return "B";
            case "B":
                return "A";
            case "X":
                return "Y";
            case "Y":
                return "X";
            case "×":
                return "○";
            case "○":
                return "×";
            case "□":
                return "△";
            case "△":
                return "□";
            default:
                return label;
        }
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private static int blend(int base, int overlay, float ratio) {
        int r = (int) (android.graphics.Color.red(base) * (1 - ratio)
                + android.graphics.Color.red(overlay) * ratio);
        int g = (int) (android.graphics.Color.green(base) * (1 - ratio)
                + android.graphics.Color.green(overlay) * ratio);
        int b = (int) (android.graphics.Color.blue(base) * (1 - ratio)
                + android.graphics.Color.blue(overlay) * ratio);
        return android.graphics.Color.rgb(r, g, b);
    }

    // --------------------------------------------------------------- binding

    void bindVideo(SecondScreenManager.VideoHeader header) {
        videoHeader = header;
        titleView.setText(header.title);
        titleView.requestLayout();
        titleView.invalidate();
        StringBuilder subtitle = new StringBuilder();
        if (!header.up.isEmpty()) {
            subtitle.append(header.up);
        }
        if (!header.bvid.isEmpty()) {
            if (subtitle.length() > 0) {
                subtitle.append(" · ");
            }
            subtitle.append(header.bvid);
        }
        subtitleView.setText(subtitle.toString());
        updateQualityLabel(header.qualityIndex);
        updateTransportVisibility();

        if (!header.bvid.isEmpty() && !header.bvid.equals(lastBvid)) {
            lastBvid = header.bvid;
            infoScrollY = 0;
            if (currentTab == TAB_INFO) {
                infoScroll.post(() -> infoScroll.scrollTo(0, 0));
            }
        }
        if (currentTab == TAB_INFO) {
            infoText.setText(buildInfoText());
        }
    }

    void bindPlayerState(SecondScreenManager.PlayerState state) {
        playerState = state;
        playView.setImageResource(state.paused ? R.drawable.ic_play : R.drawable.ic_pause);
        playView.setContentDescription(state.paused ? "播放" : "暂停");
        danmakuView.setImageResource(state.danmaku
                ? R.drawable.ic_danmaku_on : R.drawable.ic_danmaku_off);
        danmakuView.setImageTintList(ColorStateList.valueOf(state.danmaku
                ? SecondScreenTheme.accent : SecondScreenTheme.textSecondary));
        updateQualityLabel(state.qualityIndex);

        durationSeconds = state.duration;
        durationView.setText(formatTime(state.duration));
        positionView.setText(formatTime(state.position));
        if (!dragging && state.duration > 0) {
            seekBar.setProgress((int) Math.max(0, Math.min(1000,
                    state.position * 1000.0 / state.duration)));
        }
        if (currentTab == TAB_INFO) {
            infoText.setText(buildInfoText());
        }
    }

    void setComments(List<SecondScreenManager.Comment> comments, boolean end, boolean reset) {
        commentsEnd = end;
        commentAdapter.notifyDataSetChanged();
        if (reset) {
            commentsScroll = 0;
            commentsScrollTop = 0;
            listView.setSelection(0);
        }
        updateEmptyState();
    }

    void refreshComments() {
        commentAdapter.notifyDataSetChanged();
    }

    void setRelated(List<SecondScreenManager.RelatedItem> items) {
        relatedAdapter.notifyDataSetChanged();
        updateTabVisibility();
        updateEmptyState();
    }

    void setEpisodes(List<SecondScreenManager.EpisodeItem> items) {
        episodeAdapter.notifyDataSetChanged();
        updateTabVisibility();
        updateEmptyState();
    }

    void setLiveHeader(SecondScreenManager.LiveHeader header) {
        liveHeader = header;
        titleView.setText(header.title);
        subtitleView.setText(header.anchor);
        if (header.online > 0) {
            onlineView.setText(formatCount(header.online) + "人在线");
        } else if (header.watching > 0) {
            onlineView.setText(formatCount(header.watching) + "人看过");
        } else {
            onlineView.setText("");
        }
        if (currentTab == TAB_INFO) {
            infoText.setText(buildInfoText());
        }
    }

    void setLiveMessages(List<SecondScreenManager.LiveMsg> messages) {
        liveAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    void refreshLiveMessages() {
        liveAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // ---------------------------------------------------------------- controls

    private void showQualityPicker() {
        if (videoHeader == null || videoHeader.qualityLabels.length == 0) {
            return;
        }
        int checked = Math.max(0, Math.min(videoHeader.qualityLabels.length - 1, videoHeader.qualityIndex));
        showPickerOverlay("清晰度", videoHeader.qualityLabels, checked, index -> {
            if (index < 0 || index >= videoHeader.qualityCodes.length) {
                return;
            }
            videoHeader.qualityIndex = index;
            updateQualityLabel(index);
            SecondScreenManager.sendCommand(SecondScreenManager.CMD_SET_QUALITY,
                    videoHeader.qualityCodes[index], null);
        });
    }

    private void updateQualityLabel(int index) {
        if (videoHeader == null || videoHeader.qualityLabels.length == 0) {
            qualityView.setText("清晰度");
            return;
        }
        int safe = Math.max(0, Math.min(videoHeader.qualityLabels.length - 1, index));
        qualityView.setText(videoHeader.qualityLabels[safe]);
    }

    private void showSpeedPicker() {
        int checked = 0;
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (Math.abs(playerState.speed * 100 - SPEED_VALUES[i]) < 1) {
                checked = i;
                break;
            }
        }
        showPickerOverlay("倍速", SPEED_LABELS, checked, index -> {
            playerState.speed = SPEED_VALUES[index] / 100.0;
            SecondScreenManager.sendCommand(SecondScreenManager.CMD_SET_SPEED,
                    SPEED_VALUES[index], null);
            showStatus("倍速 " + SPEED_LABELS[index]);
        });
    }

    private void showAspectPicker() {
        int checked = 0;
        for (int i = 0; i < ASPECT_VALUES.length; i++) {
            if (ASPECT_VALUES[i].equals(playerState.aspect)) {
                checked = i;
                break;
            }
        }
        showPickerOverlay("画面比例", ASPECT_LABELS, checked, index -> {
            playerState.aspect = ASPECT_VALUES[index];
            SecondScreenManager.sendCommand(SecondScreenManager.CMD_SET_ASPECT,
                    0, ASPECT_VALUES[index]);
            showStatus("画面 " + ASPECT_LABELS[index]);
        });
    }

    private static String aspectLabel(String value) {
        for (int i = 0; i < ASPECT_VALUES.length; i++) {
            if (ASPECT_VALUES[i].equals(value)) {
                return ASPECT_LABELS[i];
            }
        }
        return ASPECT_LABELS[0];
    }

    // ------------------------------------------------------------- secondary menu

    private interface MenuAction {
        void run(TextView valueView);
    }

    /** "更多功能" secondary menu: speed / volume / brightness / aspect / mirror. */
    private void showMoreOverlay() {
        prepareOverlay("更多功能");

        if (mode != SecondScreenManager.MODE_LIVE) {
            addMenuRow("倍速", speedLabel(playerState.speed), value -> {
                hideOverlay();
                showSpeedPicker();
            });
        }
        addMenuRow("音量", playerState.volume + "%", value -> {
            hideOverlay();
            showSliderOverlay("音量", (int) playerState.volume, 100, volume -> {
                SecondScreenManager.sendCommand(SecondScreenManager.CMD_SET_VOLUME, volume, null);
                showStatus("音量 " + volume + "%");
            });
        });
        if (playerState.canSetBrightness) {
            addMenuRow("亮度", Math.max(0, playerState.brightness) + "%", value -> {
                hideOverlay();
                showSliderOverlay("亮度", (int) Math.max(0, playerState.brightness), 100, brightness -> {
                    SecondScreenManager.sendCommand(SecondScreenManager.CMD_SET_BRIGHTNESS, brightness, null);
                    showStatus("亮度 " + brightness + "%");
                });
            });
        }
        addMenuRow("画面比例", aspectLabel(playerState.aspect), value -> {
            hideOverlay();
            showAspectPicker();
        });
        addMenuRow("镜像", playerState.mirror ? "开" : "关", value -> {
            boolean next = !playerState.mirror;
            playerState.mirror = next;
            value.setText(next ? "开" : "关");
            SecondScreenManager.sendCommand(SecondScreenManager.CMD_TOGGLE_MIRROR, 0, null);
        });
        addMenuRow("关闭下屏", "上屏 OSD 控制", value -> {
            hideOverlay();
            showConfirmOverlay("关闭下屏？", "取消", "关闭",
                    () -> SecondScreenManager.sendCommand(
                            SecondScreenManager.CMD_SET_SECOND_SCREEN, 0, null));
        });

        overlayView.setVisibility(View.VISIBLE);
        List<View> targets = new ArrayList<>(overlayFocusTargets);
        setOverlayTargets(targets, 0);
    }

    private void addMenuRow(String label, String value, MenuAction action) {
        if (overlayPanel.getChildCount() > 1) {
            View divider = new View(getContext());
            divider.setBackgroundColor(SecondScreenTheme.divider);
            overlayPanel.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(12));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, pill(0x14000000));
        background.addState(new int[]{}, pill(0x00000000));
        row.setBackground(background);

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(SecondScreenTheme.text);
        row.addView(labelView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(SecondScreenTheme.textSecondary);
        row.addView(valueView);

        TextView arrow = new TextView(getContext());
        arrow.setText("›");
        arrow.setTextSize(16);
        arrow.setTextColor(SecondScreenTheme.divider);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        arrowParams.leftMargin = dp(8);
        row.addView(arrow, arrowParams);

        row.setOnClickListener(v -> action.run(valueView));
        overlayPanel.addView(row);
        overlayFocusTargets.add(row);
    }

    // ------------------------------------------------------------- overlay dialogs

    private void showPickerOverlay(String title, String[] items, int checked, PickerListener listener) {
        prepareOverlay(title);

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView row = new TextView(getContext());
            row.setText((i == checked ? "✓  " : "    ") + items[i]);
            row.setTextSize(14);
            row.setTextColor(i == checked ? SecondScreenTheme.accent : SecondScreenTheme.text);
            row.setPadding(dp(4), dp(11), dp(4), dp(11));
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v -> {
                hideOverlay();
                listener.onPick(index);
            });
            list.addView(row);
            rows.add(row);
        }

        ScrollView scroll = new ScrollView(getContext());
        scroll.addView(list);
        int height = Math.min(dp(340), dp(44) * Math.max(1, items.length) + dp(8));
        overlayPanel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        overlayView.setVisibility(View.VISIBLE);
        int safe = rows.isEmpty() ? 0 : Math.max(0, Math.min(rows.size() - 1, checked));
        setOverlayTargets(rows, safe);
        if (!rows.isEmpty()) {
            View row = rows.get(safe);
            scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, row.getTop() - dp(4))));
        }
    }

    private void showSliderOverlay(String title, int value, int max, SliderListener listener) {
        prepareOverlay(title);

        TextView valueView = new TextView(getContext());
        valueView.setText(value + "%");
        valueView.setTextSize(16);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(SecondScreenTheme.accent);
        valueView.setPadding(0, dp(6), 0, dp(8));
        overlayPanel.addView(valueView);

        SeekBar bar = new SeekBar(getContext());
        bar.setMax(max);
        bar.setProgress(Math.max(0, Math.min(max, value)));
        bar.setProgressTintList(ColorStateList.valueOf(SecondScreenTheme.accent));
        bar.setThumbTintList(ColorStateList.valueOf(SecondScreenTheme.accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(SecondScreenTheme.divider));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueView.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        overlayPanel.addView(bar);
        TextView adjustHint = new TextView(getContext());
        adjustHint.setText("左右方向键调节");
        adjustHint.setTextSize(11);
        adjustHint.setTextColor(SecondScreenTheme.textSecondary);
        adjustHint.setGravity(Gravity.RIGHT);
        overlayPanel.addView(adjustHint);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT);
        actions.setPadding(0, dp(6), 0, 0);

        TextView cancel = overlayAction("取消", false);
        cancel.setOnClickListener(v -> hideOverlay());
        TextView confirm = overlayAction("完成", true);
        confirm.setOnClickListener(v -> {
            hideOverlay();
            listener.onValue(bar.getProgress());
        });
        actions.addView(cancel);
        actions.addView(confirm);
        overlayPanel.addView(actions);
        overlayView.setVisibility(View.VISIBLE);
        List<View> targets = new ArrayList<>();
        targets.add(bar);
        targets.add(cancel);
        targets.add(confirm);
        setOverlayTargets(targets, 0);
    }

    private void prepareOverlay(String title) {
        overlayPanel.removeAllViews();
        overlayFocusTargets.clear();
        overlayFocusIndex = 0;
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(14));
        background.setColor(SecondScreenTheme.surface);
        overlayPanel.setBackground(background);

        TextView overlayTitle = new TextView(getContext());
        overlayTitle.setText(title);
        overlayTitle.setTextSize(15);
        overlayTitle.setTypeface(Typeface.DEFAULT_BOLD);
        overlayTitle.setTextColor(SecondScreenTheme.text);
        overlayTitle.setPadding(0, 0, 0, dp(4));
        overlayPanel.addView(overlayTitle);
    }

    private TextView overlayAction(String text, boolean primary) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(6));
        background.setColor(primary ? SecondScreenTheme.accent : SecondScreenTheme.buttonBackground);
        view.setBackground(background);
        view.setTextColor(primary ? SecondScreenTheme.onAccent : SecondScreenTheme.text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private void hideOverlay() {
        if (overlayView != null) {
            overlayView.setVisibility(View.GONE);
        }
        if (overlayPanel != null) {
            overlayPanel.removeAllViews();
        }
        overlayFocusTargets.clear();
        overlayFocusIndex = 0;
        refreshFocusVisual();
    }

    private void showExitConfirm() {
        showConfirmOverlay("退出播放？", "取消", "退出",
                () -> SecondScreenManager.sendCommand(SecondScreenManager.CMD_EXIT_PLAYER, 0, null));
    }

    private void showConfirmOverlay(String title, String cancelText, String confirmText,
                                    Runnable onConfirm) {
        prepareOverlay(title);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT);
        actions.setPadding(0, dp(10), 0, 0);

        TextView cancel = overlayAction(cancelText, false);
        cancel.setOnClickListener(v -> hideOverlay());
        TextView confirm = overlayAction(confirmText, true);
        confirm.setOnClickListener(v -> {
            hideOverlay();
            onConfirm.run();
        });
        actions.addView(cancel);
        actions.addView(confirm);
        overlayPanel.addView(actions);
        overlayView.setVisibility(View.VISIBLE);
        List<View> targets = new ArrayList<>();
        targets.add(cancel);
        targets.add(confirm);
        setOverlayTargets(targets, 0);
    }

    /** Short-lived message bubble above the controls (errors, login gates...). */
    void showStatus(String text) {
        if (statusView == null || text == null || text.isEmpty()) {
            return;
        }
        statusHandler.removeCallbacks(hideStatus);
        statusView.animate().cancel();
        statusView.setText(text);
        statusView.setAlpha(1f);
        statusView.setVisibility(View.VISIBLE);
        statusHandler.postDelayed(hideStatus, 2500);
    }

    // ---------------------------------------------------------------- info text

    private CharSequence buildInfoText() {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (mode == SecondScreenManager.MODE_LIVE) {
            if (liveHeader == null) {
                return builder;
            }
            appendTitle(builder, liveHeader.title);
            appendMeta(builder, liveHeader.anchor);
            appendStatLine(builder, "在线", formatCount(liveHeader.online),
                    "看过", formatCount(liveHeader.watching));
            appendStatLine(builder, "房间号", String.valueOf(liveHeader.roomid), null, null);
            return builder;
        }
        if (videoHeader == null) {
            return builder;
        }
        appendTitle(builder, videoHeader.title);
        appendMeta(builder, videoHeader.up);
        appendStatLine(builder, "播放", formatCount(videoHeader.view),
                "弹幕", formatCount(videoHeader.danmaku));
        appendStatLine(builder, "点赞", formatCount(videoHeader.like),
                "投币", formatCount(videoHeader.coin));
        appendStatLine(builder, "收藏", formatCount(videoHeader.favorite),
                "分享", formatCount(videoHeader.share));
        long duration = durationSeconds > 0 ? durationSeconds : playerState.duration;
        if (duration > 0) {
            appendStatLine(builder, "时长", formatTime(duration), null, null);
        }
        if (!videoHeader.bvid.isEmpty()) {
            appendStatLine(builder, "BV 号", videoHeader.bvid, null, null);
        }
        if (!videoHeader.pubdate.isEmpty()) {
            appendStatLine(builder, "发布", videoHeader.pubdate, null, null);
        }
        if (!videoHeader.desc.isEmpty()) {
            builder.append('\n');
            appendStatLine(builder, "简介", videoHeader.desc, null, null);
        }
        return builder;
    }

    private void appendTitle(SpannableStringBuilder builder, String title) {
        int start = builder.length();
        builder.append(title).append('\n');
        int end = start + title.length();
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new RelativeSizeSpan(1.35f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(SecondScreenTheme.text), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendMeta(SpannableStringBuilder builder, String name) {
        int start = builder.length();
        builder.append(name).append("\n\n");
        builder.setSpan(new ForegroundColorSpan(SecondScreenTheme.name), start,
                start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendStatLine(SpannableStringBuilder builder, String label1, String value1,
                                String label2, String value2) {
        appendLabel(builder, label1);
        appendValue(builder, value1);
        if (label2 != null) {
            builder.append("    ");
            appendLabel(builder, label2);
            appendValue(builder, value2);
        }
        builder.append('\n');
    }

    private void appendLabel(SpannableStringBuilder builder, String label) {
        int start = builder.length();
        builder.append(label).append(' ');
        int end = builder.length();
        builder.setSpan(new ForegroundColorSpan(SecondScreenTheme.textSecondary), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new RelativeSizeSpan(0.82f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendValue(SpannableStringBuilder builder, String value) {
        int start = builder.length();
        builder.append(value);
        builder.setSpan(new ForegroundColorSpan(SecondScreenTheme.text), start,
                builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ---------------------------------------------------------------- helpers

    private static String speedLabel(double speed) {
        if (Math.abs(speed - Math.rint(speed)) < 0.001) {
            return String.format(Locale.US, "%.1fx", speed);
        }
        return String.format(Locale.US, "%.2fx", speed);
    }

    private static String formatTime(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }

    private static String formatCount(long value) {
        if (value >= 100000000L) {
            return String.format(Locale.US, "%.1f亿", value / 100000000.0);
        }
        if (value >= 10000L) {
            return String.format(Locale.US, "%.1f万", value / 10000.0);
        }
        return String.valueOf(value);
    }
}
