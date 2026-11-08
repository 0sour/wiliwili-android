//
// Created by fang on 2023/1/3.
//

#include <utility>
#include <algorithm>
#include <borealis/core/thread.hpp>
#include <borealis/core/touch/tap_gesture.hpp>
#include <borealis/views/applet_frame.hpp>
#include <borealis/views/dialog.hpp>

#include "activity/player_activity.hpp"
#include "fragment/player_collection.hpp"
#include "fragment/player_coin.hpp"
#include "fragment/player_single_comment.hpp"
#include "utils/config_helper.hpp"
#include "utils/dialog_helper.hpp"
#include "utils/number_helper.hpp"
#include "utils/activity_helper.hpp"
#include "presenter/comment_related.hpp"
#include "utils/shortcut_helper.hpp"
#include "view/qr_image.hpp"
#include "view/video_view.hpp"
#include "view/grid_dropdown.hpp"
#include "view/subtitle_core.hpp"
#include "view/mpv_core.hpp"
#include "view/danmaku_core.hpp"
#include "utils/second_screen.hpp"

class DataSourceCommentList : public RecyclingGridDataSource, public CommentAction {
public:
    DataSourceCommentList(bilibili::VideoCommentListResult result, uint64_t aid, int mode, std::function<void(void)> cb)
        : dataList(std::move(result)), aid(aid), commentMode(mode), switchModeCallback(cb) {}
    RecyclingGridItem* cellForRow(RecyclingGrid* recycler, size_t index) override {
        if (index == 0) {
            VideoCommentSort* item = (VideoCommentSort*)recycler->dequeueReusableCell("Sort");
            item->setHeight(30);
            item->setFocusable(false);
            if (commentMode == 3) {
                item->hintLabel->setText("wiliwili/player/comment_sort/top"_i18n);
                item->sortLabel->setText("wiliwili/player/comment_sort/sort_top"_i18n);
            } else {
                item->hintLabel->setText("wiliwili/player/comment_sort/new"_i18n);
                item->sortLabel->setText("wiliwili/player/comment_sort/sort_new"_i18n);
            }
            return item;
        }
        if (index == 1) {
            VideoCommentReply* item = (VideoCommentReply*)recycler->dequeueReusableCell("Reply");
            item->setHeight(40);
            return item;
        }

        //从缓存列表中取出 或者 新生成一个表单项
        VideoComment* item = (VideoComment*)recycler->dequeueReusableCell("Cell");

        item->setData(this->dataList[index - 2]);
        return item;
    }

    size_t getItemCount() override { return dataList.size() + 2; }

    const bilibili::VideoCommentListResult& getData() const { return dataList; }

    void onItemSelected(RecyclingGrid* recycler, size_t index) override {
        if (index == 0) {
            if (switchModeCallback) switchModeCallback();
            return;
        }
        if (index == 1) {
            if (!DialogHelper::checkLogin()) return;
            // 回复评论
            brls::Application::getImeManager()->openForText(
                [this, recycler](const std::string& text) {
                    if (text.empty()) return;
                    this->commentReply(text, std::to_string(aid), 0, 0, 1,
                                       [this, recycler](const bilibili::VideoCommentAddResult& result) {
                                           this->dataList.insert(dataList.begin(), result.reply);
                                           recycler->reloadData();
                                       });
                },
                "wiliwili/player/single_comment/hint"_i18n, "", 500, "", 0);
            return;
        }

        auto* item = dynamic_cast<VideoComment*>(recycler->getGridItemByIndex(index));
        if (!item) return;

        auto* view = new PlayerSingleComment();
        view->setCommentData(dataList[index - 2], item->getY(), 1);
        auto container = new brls::AppletFrame(view);
        container->setHeaderVisibility(brls::Visibility::GONE);
        container->setFooterVisibility(brls::Visibility::GONE);
        container->setInFadeAnimation(true);
        brls::Application::pushActivity(new brls::Activity(container));

        view->likeStateEvent.subscribe([this, item, index](size_t value) {
            auto& itemData  = dataList[index - 2];
            itemData.action = value;
            item->setLiked(value);
        });
        view->likeNumEvent.subscribe([this, item, index](size_t value) {
            auto& itemData = dataList[index - 2];
            itemData.like  = value;
            item->setLikeNum(value);
        });
        view->replyNumEvent.subscribe([this, item, index](size_t value) {
            auto& itemData  = dataList[index - 2];
            itemData.rcount = value;
            item->setReplyNum(value);
        });
        view->deleteEvent.subscribe([this, recycler, index]() {
            dataList.erase(dataList.begin() + index - 2);
            recycler->reloadData();
            // 重新设置一下焦点到 recycler 的默认 cell （顶部）
            brls::Application::giveFocus(recycler);
        });
    }

    void appendData(const bilibili::VideoCommentListResult& data) {
        bool skip = false;
        for (auto& i : data) {
            skip = false;
            for (auto& j : this->dataList) {
                if (j.rpid == i.rpid) {
                    skip = true;
                    break;
                }
            }
            if (!skip) this->dataList.push_back(i);
        }
    }

    void clearData() override { this->dataList.clear(); }

private:
    bilibili::VideoCommentListResult dataList;
    uint64_t aid;
    int commentMode                              = 3;  // 2: 按时间；3: 按热度
    std::function<void(void)> switchModeCallback = nullptr;
};

class QualityCell : public RecyclingGridItem {
public:
    QualityCell() { this->inflateFromXMLRes("xml/views/player_quality_cell.xml"); }

    void setSelected(bool selected) {
        brls::Theme theme = brls::Application::getTheme();

        this->selected = selected;
        this->checkbox->setVisibility(selected ? brls::Visibility::VISIBLE : brls::Visibility::GONE);
        this->title->setTextColor(selected ? theme["brls/list/listItem_value_color"] : theme["brls/text"]);
    }

    bool getSelected() { return this->selected; }

    BRLS_BIND(brls::Label, title, "cell/title");
    BRLS_BIND(brls::Box, loginLabel, "cell/login");
    BRLS_BIND(brls::Box, vipLabel, "cell/vip");
    BRLS_BIND(brls::CheckBox, checkbox, "cell/checkbox");

    static RecyclingGridItem* create() { return new GridRadioCell(); }

private:
    bool selected = false;
};

class QualityDataSource : public DataSourceDropdown {
public:
    QualityDataSource(bilibili::VideoUrlResult result, BaseDropdown* view)
        : DataSourceDropdown(view), data(std::move(result)) {
        login = !ProgramConfig::instance().getCSRF().empty();
    }

    RecyclingGridItem* cellForRow(RecyclingGrid* recycler, size_t index) override {
        QualityCell* item = (QualityCell*)recycler->dequeueReusableCell("Cell");

        int quality = data.accept_quality[index];

        item->loginLabel->setVisibility(brls::Visibility::GONE);
        item->vipLabel->setVisibility(brls::Visibility::GONE);

        if (quality > 80) {
            item->vipLabel->setVisibility(brls::Visibility::VISIBLE);
        } else if (quality >= 32) {
            if (!login) item->loginLabel->setVisibility(brls::Visibility::VISIBLE);
        }

        auto r = this->data.accept_description[index];
        item->title->setText(this->data.accept_description[index]);
        item->setSelected(index == dropdown->getSelected());
        return item;
    }

    size_t getItemCount() override { return (std::min)(data.accept_quality.size(), data.accept_description.size()); }

    void clearData() override {}

private:
    bilibili::VideoUrlResult data;
    bool login;
};

/// BasePlayerActivity

void BasePlayerActivity::onContentAvailable() { this->setCommonData(); }

void BasePlayerActivity::onResume() {
    brls::Activity::onResume();
    // 从其他页面返回时重新接管副屏
    this->startSecondScreenSession();
}

void BasePlayerActivity::startSecondScreenSession() {
    if (!wiliwili::second_screen::isEnabled()) {
        wiliwili::second_screen::closeSession(this->secondScreenSession);
        this->secondScreenSession = 0;
        return;
    }

    this->secondScreenSession = wiliwili::second_screen::openSession(wiliwili::second_screen::Mode::VIDEO);
    wiliwili::second_screen::setCommandHandler(
        this->secondScreenSession,
        [this](wiliwili::second_screen::Command cmd, int64_t arg, const std::string& text) {
            this->handleSecondScreenCommand(cmd, arg, text);
        });
    this->pushSecondScreenVideoInfo();
    this->pushSecondScreenPlayerState();
    this->pushSecondScreenCachedComments();
}

void BasePlayerActivity::setCommonData() {
    // 视频评论
    recyclingGrid->registerCell("Cell", []() { return VideoComment::create(); });

    recyclingGrid->registerCell("Reply", []() { return VideoCommentReply::create(); });

    recyclingGrid->registerCell("Sort", []() { return VideoCommentSort::create(); });

    recyclingGrid->setDefaultCellFocus(1);

    recyclingGrid->registerAction("wiliwili/home/common/switch"_i18n, brls::ControllerButton::BUTTON_X,
                                  [this](brls::View* view) -> bool {
                                      this->setCommentMode();
                                      return true;
                                  });

    recyclingGrid->registerAction(ShortcutHelper::getRefresh(),
                                  [this](brls::View* view) -> bool {
                                      this->setCommentMode();
                                      return true;
                                  });

    // 切换右侧Tab
    this->registerAction(
        "上一项", brls::ControllerButton::BUTTON_LT,
        [this](brls::View* view) -> bool {
            tabFrame->focus2LastTab();
            return true;
        },
        true);
    this->registerAction(
        "上一项", brls::ControllerButton::BUTTON_LB,
        [this](brls::View* view) -> bool {
            tabFrame->focus2LastTab();
            return true;
        },
        true);

    this->registerAction(
        "下一项", brls::ControllerButton::BUTTON_RT,
        [this](brls::View* view) -> bool {
            tabFrame->focus2NextTab();
            return true;
        },
        true);
    this->registerAction(
        "下一项", brls::ControllerButton::BUTTON_RB,
        [this](brls::View* view) -> bool {
            tabFrame->focus2NextTab();
            return true;
        },
        true);

    this->registerAction(
        ShortcutHelper::getLast(),
        [this](brls::View* view) -> bool {
            tabFrame->focus2LastTab();
            return true;
        });
    this->registerAction(
        ShortcutHelper::getNext(),
        [this](brls::View* view) -> bool {
            tabFrame->focus2NextTab();
            return true;
        });
    video->registerCommonActions(this);

    // 调整清晰度
    this->registerAction("wiliwili/player/quality"_i18n, brls::ControllerButton::BUTTON_START,
                         [this](brls::View* view) -> bool {
                             this->setVideoQuality();
                             return true;
                         });

    this->btnQR->getParent()->addGestureRecognizer(new brls::TapGestureRecognizer(this->btnQR->getParent()));

    this->btnAgree->getParent()->addGestureRecognizer(new brls::TapGestureRecognizer(this->btnAgree->getParent()));

    this->btnCoin->getParent()->addGestureRecognizer(new brls::TapGestureRecognizer(this->btnCoin->getParent()));

    this->btnFavorite->getParent()->addGestureRecognizer(
        new brls::TapGestureRecognizer(this->btnFavorite->getParent()));

    this->videoUserInfo->addGestureRecognizer(new brls::TapGestureRecognizer(this->videoUserInfo));

    this->setRelationButton(false, false, false);

    eventSubscribeID = MPV_E->subscribe([this](MpvEventEnum event) {
        // 上一次报告历史记录的时间点
        static int64_t lastProgress = MPVCore::instance().video_progress;

        // 双屏：播放状态变化时立即同步
        switch (event) {
            case MpvEventEnum::MPV_PAUSE:
            case MpvEventEnum::MPV_RESUME:
            case MpvEventEnum::VIDEO_SPEED_CHANGE:
            case MpvEventEnum::VIDEO_VOLUME_CHANGE:
            case MpvEventEnum::VIDEO_MUTE:
            case MpvEventEnum::VIDEO_UNMUTE:
            case MpvEventEnum::UPDATE_DURATION:
            case MpvEventEnum::LOADING_END:
            case MpvEventEnum::START_FILE:
                this->pushSecondScreenPlayerState();
                break;
            default:
                break;
        }

        switch (event) {
            case MpvEventEnum::UPDATE_PROGRESS: {
                // 双屏：进度最多每秒推送一次
                if (MPVCore::instance().video_progress != this->secondScreenPosition) {
                    this->secondScreenPosition = MPVCore::instance().video_progress;
                    this->pushSecondScreenPlayerState();
                }
                // 每15秒同步一次进度
                if (lastProgress + 15 < MPVCore::instance().video_progress) {
                    lastProgress = MPVCore::instance().video_progress;
                    this->reportCurrentProgress(lastProgress, MPVCore::instance().duration);
                } else if (MPVCore::instance().video_progress < lastProgress) {
                    // 当前播放时间小于上一次上传历史记录的时间点
                    // 发生于向前拖拽进度的时候，此时重置lastProgress的值
                    lastProgress = MPVCore::instance().video_progress;
                }
                // 检查视频链接是否有效
                auto timeNow = std::chrono::system_clock::now();
                if (timeNow > videoDeadline) {
                    // 设置视频加载后跳转的时间
                    setProgress(MPVCore::instance().video_progress);

                    // 暂停播放
                    MPVCore::instance().pause();

                    // 10s 后重新尝试
                    videoDeadline = timeNow + std::chrono::seconds(10);

                    // 有效期已过，重新请求视频链接
                    auto self = dynamic_cast<PlayerSeasonActivity*>(this);
                    if (self) {
                        this->requestSeasonVideoUrl(episodeResult.bvid, episodeResult.cid, false);
                    } else {
                        this->requestVideoUrl(videoDetailResult.bvid, videoDetailPage.cid, false);
                    }

                    //todo: 如果有选择的字幕加载对应的字幕
                }
                break;
            }
            case MpvEventEnum::END_OF_FILE:
                // 尝试自动加载下一分集
                // 如果当前最顶层是Dialog就放弃自动播放，因为有可能是用户点开了收藏或者投币对话框
                {
                    int64_t& progress = MPVCore::instance().video_progress;
                    int64_t& duration = MPVCore::instance().duration;
                    int clipEnd       = videoUrlResult.clipEnd;
                    auto seasonCustom = ProgramConfig::instance().getSeasonCustom(seasonInfo.season_id);
                    if (seasonCustom.custom_clip) {
                        clipEnd = duration - seasonCustom.clip_end;
                    }

                    // 播放到一半没网时也会触发EOF，这里简单判断一下结束播放时的播放条位置是否在片尾或视频结尾附近
                    if ((duration - progress > 5 || progress - duration > 5) && !(clipEnd > 0 && clipEnd - progress < 5)) {
                        brls::Logger::error("EOF: video: {} duration: {} clipEnd: {}", progress, duration, clipEnd);
                        return;
                    }
                    if (PLAYER_STRATEGY == PlayerStrategy::LOOP) {
                        MPVCore::instance().seek(0);
                        MPVCore::instance().resume();
                        return;
                    }
                    auto stack    = brls::Application::getActivitiesStack();
                    Activity* top = stack[stack.size() - 1];
                    if (!dynamic_cast<BasePlayerActivity*>(top) &&
                        !dynamic_cast<VideoView*>(top->getContentView()->getView("video"))) {
                        // 最顶层没有 video 组件，说明用户打开了评论或者其他菜单
                        // 在这种情况下不执行自动播放其他视频显示重播按钮
                        APP_E->fire(VideoView::REPLAY, nullptr);
                    } else if (PLAYER_STRATEGY == PlayerStrategy::NEXT || PLAYER_STRATEGY == PlayerStrategy::RCMD) {
                        this->onIndexChangeToNext();
                    } else {
                        // 对于其他情况，显示重播按钮
                        APP_E->fire(VideoView::REPLAY, nullptr);
                    }
                }
                break;
            case MpvEventEnum::RESTART:
                this->updateVideoLink();
                break;
            default:
                break;
        }
    });

    customEventSubscribeID = APP_E->subscribe([this](const std::string& event, void* data) {
        if (event == VideoView::QUALITY_CHANGE) {
            this->setVideoQuality();
        } else if (event == VideoView::SWITCH_TO_LAST) {
            // 历史播放进度储存在 SubtitleCore 中
            auto videoPage = SubtitleCore::instance().getSubtitleList();
            if (videoPage.last_play_cid == videoDetailPage.cid) {
                // 因为占用了切换弹幕的按键，所以在无效的情况下保持切换弹幕
                this->video->toggleDanmaku();
                return;
            }
            brls::Logger::debug("切换到历史播放进度：{}/{}", videoPage.last_play_cid, videoPage.last_play_time);
            for (auto& p : videoDetailResult.pages) {
                if (p.cid == videoPage.last_play_cid) {
                    this->onIndexChange(p.page - 1);
                    this->setProgress(videoPage.last_play_time / 1000);
                    break;
                }
            }
        } else if (event == "REQUEST_CAST_URL") {
            this->requestCastUrl();
        }
    });

    if (brls::Application::ORIGINAL_WINDOW_HEIGHT < 720) video->hideStatusLabel();

    video->hideOSDLockButton();

    // 双屏：所有播放器子类都会调用 setCommonData，在这里初始化副屏
    // 这些节点可能被自定义主题省略，使用可选查找
    this->videoLeftBox  = dynamic_cast<brls::Box*>(this->getView("video_detail_left_box"));
    this->videoRightBox = dynamic_cast<brls::Box*>(this->getView("video_detail_right_box"));
    this->videoInfoBox  = dynamic_cast<brls::Box*>(this->getView("video_detail_info_box"));
    this->videoCloseButton = this->getView("button/close");

    this->secondScreenSubscribeID = APP_E->subscribe([this](const std::string& event, void*) {
        if (event != wiliwili::second_screen::EVENT_CHANGED) return;
        this->updateSecondScreenLayout();
        this->startSecondScreenSession();
    });

    this->updateSecondScreenLayout();
    this->startSecondScreenSession();
}

void BasePlayerActivity::showCollectionDialog(uint64_t id, int videoType) {
    if (!DialogHelper::checkLogin()) return;
    auto playerCollection = new PlayerCollection(id, videoType);
    auto dialog           = new brls::Dialog(playerCollection);
    dialog->addButton("wiliwili/home/common/save"_i18n, [this, id, videoType, playerCollection]() {
        this->addResource(id, videoType, playerCollection->isFavorite(), playerCollection->getAddCollectionList(),
                          playerCollection->getDeleteCollectionList());
    });
    playerCollection->registerAction(
        "", brls::ControllerButton::BUTTON_START,
        [this, id, videoType, playerCollection, dialog](...) {
            this->addResource(id, videoType, playerCollection->isFavorite(), playerCollection->getAddCollectionList(),
                              playerCollection->getDeleteCollectionList());
            dialog->dismiss();
            return true;
        },
        true);
    dialog->open();
}

void BasePlayerActivity::showCoinDialog(uint64_t aid) {
    if (!DialogHelper::checkLogin()) return;

    if (std::to_string(videoDetailResult.owner.mid) == ProgramConfig::instance().getUserID()) {
        DialogHelper::showDialog("wiliwili/player/coin/own"_i18n);
        return;
    }

    int coins = getCoinTolerate();
    if (coins <= 0) {
        DialogHelper::showDialog("wiliwili/player/coin/run_out"_i18n);
        return;
    }

    auto playerCoin = new PlayerCoin();
    if (coins == 1) playerCoin->hideTwoCoin();
    playerCoin->getSelectEvent()->subscribe(
        [this, playerCoin, aid](int value) { this->addCoin(aid, value, playerCoin->likeAtTheSameTime()); });
    auto dialog = new brls::Dialog(playerCoin);
    dialog->open();
}

void BasePlayerActivity::updateVideoLink() {
    // 设置视频加载后跳转的时间
    setProgress(MPVCore::instance().video_progress);

    // dash
    if (!this->videoUrlResult.dash.video.empty()) {
        // dash格式的视频无需重复请求视频链接，这里简单的设置清晰度即可
        videoUrlResult.quality = BasePlayerActivity::defaultQuality;
        this->onVideoPlayUrl(videoUrlResult);
        return;
    }

    // flv
    if (dynamic_cast<PlayerSeasonActivity*>(this)) {
        this->requestSeasonVideoUrl(episodeResult.bvid, episodeResult.cid);
    } else {
        this->requestVideoUrl(videoDetailResult.bvid, videoDetailPage.cid);
    }
}

void BasePlayerActivity::setVideoQuality() {
    if (this->videoUrlResult.accept_description.empty()) return;

    auto* dropdown = new BaseDropdown(
        "wiliwili/player/quality"_i18n,
        [this](int selected) {
            int code                           = this->videoUrlResult.accept_quality[selected];
#ifdef __PSV__
            if (code > 64) {
                code = 64;
            }
#endif
            BasePlayerActivity::defaultQuality = code;
            ProgramConfig::instance().setSettingItem(SettingItem::VIDEO_QUALITY, code);

            // 如果未登录选择了大于等于480P清晰度的视频
            if (ProgramConfig::instance().getCSRF().empty() && defaultQuality >= 32) {
                DialogHelper::showDialog("wiliwili/home/common/no_login"_i18n);
                return;
            }

            this->updateVideoLink();
        },
        getQualityIndex());
    auto* recycler = dropdown->getRecyclingList();
    recycler->registerCell("Cell", []() { return new QualityCell(); });
    dropdown->setDataSource(new QualityDataSource(this->videoUrlResult, dropdown));
    dropdown->registerAction(
        "", brls::ControllerButton::BUTTON_START,
        [dropdown](...) {
            dropdown->dismiss();
            return true;
        },
        true);
    dropdown->registerAction(ShortcutHelper::getVideoQuality(), [dropdown](...) {
        dropdown->dismiss();
        return true;
    });

    // 因为触摸的问题 视频组件上开启新的 activity 需要同步执行
    // 不然在某些情况下焦点会错乱
    ASYNC_RETAIN
    brls::sync([ASYNC_TOKEN, dropdown]() {
        ASYNC_RELEASE
        brls::Application::pushActivity(new brls::Activity(dropdown));
    });
}

void BasePlayerActivity::setCommentMode() {
    this->recyclingGrid->estimatedRowHeight = 100;
    this->recyclingGrid->showSkeleton();
    tabFrame->focusTab(0);
    requestVideoComment(std::to_string(this->getAid()), 0, getVideoCommentMode() == 3 ? 2 : 3);
}

void BasePlayerActivity::onVideoPlayUrl(const bilibili::VideoUrlResult& result) {
    brls::Logger::debug("onVideoPlayUrl quality: {}", result.quality);

    if (result.accept_quality.empty() || result.accept_description.empty()) {
        // 通常是返回了其他报错信息, 比如验证码
        brls::Logger::error("onVideoPlayUrl: no video url available");
        auto dialog = new brls::Dialog("Error: No video url available");
        dialog->setCancelable(false);
        dialog->addButton("hints/ok"_i18n, []() {
            brls::sync([]() { brls::Application::popActivity(); });
        });
        dialog->open();
        return;
    }

    // 有效期 110 分钟
    videoDeadline = std::chrono::system_clock::now() + std::chrono::seconds(6600);

    // 获取预设的跳转位置
    int start    = this->getProgress();
    int end      = -1;
    int clipOpen = result.clipOpen, clipEnd = result.clipEnd;
    int time_sec = result.timelength / 1000;
    std::string customAspect;

    // 加载覆盖设置
    if (seasonInfo.season_id > 0) {
        auto seasonSetting = ProgramConfig::instance().getSeasonCustom(seasonInfo.season_id);
        customAspect       = seasonSetting.player_aspect;

        if (seasonSetting.custom_clip) {
            // 设置了自定义的片头片尾
            // 判断是否是正片
            for (auto& e : seasonInfo.episodes) {
                if (episodeResult.id == e.id) {
                    // 如果当前播放的是正片，则使用自定义的片头片尾
                    clipOpen = seasonSetting.clip_start;
                    if (clipOpen > time_sec - 5) clipOpen = 0;
                    clipEnd = time_sec - seasonSetting.clip_end;
                    if (seasonSetting.clip_end <= 0 || clipEnd < clipOpen) clipEnd = 0;
                    break;
                }
            }
        }
    }
    if (customAspect.empty()) {
        customAspect = ProgramConfig::instance().getSettingItem(SettingItem::PLAYER_ASPECT, std::string{"-1"});
    }
    MPVCore::instance().setAspect(customAspect);

    // 当要跳转的进度距离尾部只有 5s，就重新播放
    if (start > 0 && abs(time_sec - start) <= 5) start = 0;

    // 跳过片头片尾
    if (PLAYER_SKIP_OPENING_CREDITS) {
        start = std::max(start, clipOpen);
        if (clipEnd > 0) start = std::min(start, clipEnd);
        end = clipEnd;
        brls::Logger::debug("片头片尾: {}/{}", clipOpen, clipEnd);
    }

    // 针对用户上传的视频，尝试加载上一次播放的进度
    if (videoDetailPage.cid && start < 0) {
        auto data = SubtitleCore::instance().getSubtitleList();
        if (data.last_play_cid == videoDetailPage.cid && data.last_play_time > 0) {
            APP_E->fire(VideoView::LAST_TIME, (void*)&(data.last_play_time));
        }
    } else {
        // 设置为 POSITION_DISCARD 后，不会加载网络历史记录，而是直接使用 setProgress 指定的位置
        // 番剧视频或指定了进度的用户视频
        int64_t position = 0;
        APP_E->fire(VideoView::LAST_TIME, (void*)&(position));
        if (start > 0) {
            std::string hint = fmt::format("已为您定位至: {}", wiliwili::sec2Time(start));
            APP_E->fire(VideoView::HINT, (void*)hint.c_str());
        }
    }

    if (!result.dash.video.empty()) {
        // dash
        brls::Logger::debug("Video type: dash");

        // 找到当前可用的清晰度
        for (const auto& i : result.dash.video) {
            int desiredQuality = result.quality;
            // 若设置了过高的清晰度, 自动切换到合适的清晰度, 默认为 128 (即无限制)
            if (i.height > i.width) {
                desiredQuality = std::min(desiredQuality, portraitQualityMax);
            } else {
                desiredQuality = std::min(desiredQuality, landscapeQualityMax);
            }

            if (desiredQuality >= i.id) {
                videoUrlResult.quality = i.id;
                break;
            }
        }

        // 找到当前清晰度下可用的视频
        std::vector<bilibili::DashMedia> codecs;
        for (const auto& i : result.dash.video) {
            if (i.id == videoUrlResult.quality) {
                codecs.emplace_back(i);
            }
        }

        // 匹配当前设定的视频编码
        bilibili::DashMedia v = codecs[0];  // 默认是 AVC/H.264
        for (const auto& i : codecs) {
            if (BILI::VIDEO_CODEC == i.codecid) {
                v = i;
                break;
            }
        }

        // 将主音频和备份音频链接合并，当作不同的音轨传给播放器，可以实现在播放失败时自动切换
        std::vector<std::string> audios;
        if (!result.dash.audio.empty()) {
            // 选择音轨，支持杜比/无损优先和多级回退
            auto pickDolby = [&]() -> std::optional<bilibili::DashMedia> {
                if (result.dash.dolby_audio.empty()) return std::nullopt;
                bilibili::DashMedia best = result.dash.dolby_audio[0];
                for (auto& dm : result.dash.dolby_audio) if (dm.bandwidth > best.bandwidth) best = dm;
                return best;
            };
            auto pickFlac = [&]() -> std::optional<bilibili::DashMedia> {
                if (!result.dash.has_flac) return std::nullopt;
                return result.dash.flac_audio;
            };
            auto pickStandard = [&](int qid) -> std::optional<bilibili::DashMedia> {
                for (auto& i : result.dash.audio) if (i.id == qid) return i;
                return std::nullopt;
            };
            auto pickFirstAvailableStandard = [&]() -> std::optional<bilibili::DashMedia> {
                int candidates[] = {30280, 30232, 30216};
                for (int q : candidates) {
                    auto m = pickStandard(q);
                    if (m) return m;
                }
                // fallback to first item if none matched
                if (!result.dash.audio.empty()) return result.dash.audio[0];
                return std::nullopt;
            };

            bilibili::DashMedia a = result.dash.audio[0];
            bool selected = false;
            if (BILI::AUDIO_QUALITY == 30250) {
                // Dolby → Lossless → High → Medium → Low
                if (auto m = pickDolby()) { a = *m; selected = true; brls::Logger::debug("Picked Dolby audio (type {}), bw {}", result.dash.dolby_type, a.bandwidth);} else
                if (auto m = pickFlac()) { a = *m; selected = true; brls::Logger::debug("Picked FLAC audio, bw {}", a.bandwidth);} else
                if (auto m = pickFirstAvailableStandard()) { a = *m; selected = true; }
            } else if (BILI::AUDIO_QUALITY == 30251) {
                // Lossless → Dolby → High → Medium → Low
                if (auto m = pickFlac()) { a = *m; selected = true; brls::Logger::debug("Picked FLAC audio, bw {}", a.bandwidth);} else
                if (auto m = pickDolby()) { a = *m; selected = true; brls::Logger::debug("Picked Dolby audio (type {}), bw {}", result.dash.dolby_type, a.bandwidth);} else
                if (auto m = pickFirstAvailableStandard()) { a = *m; selected = true; }
            } else {
                // Try user-selected standard, then fallback High→Medium→Low
                if (auto m = pickStandard(BILI::AUDIO_QUALITY)) { a = *m; selected = true; }
                if (!selected) {
                    if (auto m = pickFirstAvailableStandard()) { a = *m; selected = true; }
                }
            }
            // 生成音频列表
            audios.emplace_back(a.base_url);
            audios.insert(audios.end(), a.backup_url.begin(), a.backup_url.end());
            brls::Logger::debug("Dash quality: {}; video: {}; audio: {}", videoUrlResult.quality, v.codecid, a.id);
        }

        // 给播放器设置链接
        this->video->setUrl(v.base_url, start, end, audios);

        // 设置备份视频链接
        for (const auto& backup_url : v.backup_url) {
            this->video->setBackupUrl(backup_url, start, end, audios);
        }
    } else {
        // flv
        brls::Logger::debug("Video type: flv");
        if (result.durl.empty()) {
            brls::Logger::error("No media");
        } else if (result.durl.size() == 1) {
            this->video->setUrl(result.durl[0].url, start, end);
        } else {
            std::vector<EDLUrl> urls;
            urls.reserve(result.durl.size());
            for (auto& i : result.durl) {
                urls.emplace_back(i.url, i.length / 1000.0f);
            }
            this->video->setUrl(urls, start, end);
        }
    }

    // 设置mpv事件
    // 1.更新清晰度
    std::string quality = videoUrlResult.accept_description[getQualityIndex()];
    APP_E->fire(VideoView::SET_QUALITY, (void*)quality.c_str());
    // 2.绘制进度条标记点（例如：片头片尾）
    if (clipOpen > 0) {
        float data = clipOpen * 1.0f / time_sec;
        APP_E->fire(VideoView::CLIP_INFO, (void*)&data);
    }
    if (clipEnd > 0) {
        float data2 = clipEnd * 1.0f / time_sec;
        APP_E->fire(VideoView::CLIP_INFO, (void*)&data2);
    }
    // 3. 设置视频时长
    APP_E->fire(VideoView::REAL_DURATION, (void*)&time_sec);

    brls::Logger::debug("BasePlayerActivity::onVideoPlayUrl done");

    // 双屏：清晰度列表与播放状态已就绪
    this->pushSecondScreenVideoInfo();
    this->pushSecondScreenPlayerState();

    // 根据配置决定是否自动全屏
    if (ProgramConfig::instance().getBoolOption(SettingItem::PLAYER_AUTO_FULLSCREEN) &&
        !this->video->isFullscreen()) {
        this->video->setFullScreen(true);
    }
}

void BasePlayerActivity::onCommentInfo(const bilibili::VideoCommentResultWrapper& result) {
    // 双屏：把评论同步到副屏（置顶评论只在第一页携带）
    if (wiliwili::second_screen::isEnabled()) {
        bool reset = result.requestIndex == 0;
        std::vector<wiliwili::second_screen::CommentItem> items;
        auto append = [&items](const bilibili::VideoCommentResult& comment) {
            wiliwili::second_screen::CommentItem item;
            item.name = comment.member.uname;
            item.text = comment.content.message;
            item.time = comment.reply_control.time_desc;
            if (item.time.empty()) item.time = wiliwili::sec2FullDate(comment.ctime);
            item.avatar  = comment.member.avatar;
            for (const auto& [token, emote] : comment.content.emote) {
                item.emotes.push_back({token, emote.url, emote.size});
            }
            item.like    = comment.like;
            item.replies = comment.rcount;
            item.up      = comment.member.is_uploader;
            item.top     = comment.top;
            items.push_back(std::move(item));
        };
        if (reset) {
            for (const auto& comment : result.top_replies) append(comment);
        }
        for (const auto& comment : result.replies) append(comment);
        wiliwili::second_screen::pushComments(reset, result.cursor.is_end, result.cursor.next,
                                              result.cursor.all_count, items);
    }

    auto* datasource = dynamic_cast<DataSourceCommentList*>(recyclingGrid->getDataSource());
    if (!datasource && result.requestIndex == 0) {
        // 第一页评论
        //整合置顶评论
        std::vector<bilibili::VideoCommentResult> comments(result.top_replies);
        comments.insert(comments.end(), result.replies.begin(), result.replies.end());
        // 为了加载骨架屏美观，设置为了100，在加载评论时手动修改回来
        // 这里限制的是评论的最大高度，实际评论高度还受评论组件的最大行数限制
        this->recyclingGrid->estimatedRowHeight = 600;
        this->recyclingGrid->setDataSource(new DataSourceCommentList(
            comments, this->getAid(), this->getVideoCommentMode(), [this]() { this->setCommentMode(); }));
        this->recyclingGrid->selectRowAt(comments.empty() ? 1 : 2, false);
        // 设置评论数量提示
        auto item = this->tabFrame->getTab("wiliwili/player/comment"_i18n);
        if (item) item->setSubtitle(wiliwili::num2w(result.cursor.all_count));
    } else if (datasource) {
        // 第N页评论
        if (!result.replies.empty()) {
            datasource->appendData(result.replies);
            recyclingGrid->notifyDataChanged();
        }
    } else {
        brls::Logger::error("onCommentInfo ds: {} index: {} end: {}", (bool)datasource, result.requestIndex,
                            result.cursor.is_end);
    }
}

void BasePlayerActivity::onRequestCommentError(const std::string& error) {
    brls::sync([this, error]() {
        this->recyclingGrid->setError(error);
        wiliwili::second_screen::pushStatus(error.empty() ? "评论加载失败" : error);
    });
}

void BasePlayerActivity::updateSecondScreenLayout() {
    if (!this->videoLeftBox || !this->videoRightBox || !this->video) return;

    bool enabled = wiliwili::second_screen::isEnabled();
    float width = brls::Application::getStyle().getMetric("wiliwili/player/width");

    if (enabled) {
        // 上屏只保留视频：隐藏右侧面板、下方信息区、底部按键提示和关闭按钮
        this->videoRightBox->setVisibility(brls::Visibility::GONE);
        if (this->videoInfoBox) this->videoInfoBox->setVisibility(brls::Visibility::GONE);
        if (this->videoCloseButton) this->videoCloseButton->setVisibility(brls::Visibility::GONE);
        this->appletFrame->setFooterVisibility(brls::Visibility::GONE);
        this->videoLeftBox->setWidth(brls::View::AUTO);
        this->videoLeftBox->setGrow(1);
        this->video->setWidthPercentage(100);

        // 焦点若停在将被隐藏的区域，转交给视频
        bool focusHidden = false;
        for (brls::View* view = brls::Application::getCurrentFocus(); view; view = view->getParent()) {
            if (view == this->videoRightBox || view == this->videoInfoBox) {
                focusHidden = true;
                break;
            }
        }
        if (focusHidden) brls::Application::giveFocus(this->video);
        // 双屏模式下上屏没有可用控件，焦点直接交给视频，按键交给下屏
        brls::Application::giveFocus(this->video);
    } else {
        this->videoRightBox->setVisibility(brls::Visibility::VISIBLE);
        if (this->videoInfoBox) this->videoInfoBox->setVisibility(brls::Visibility::VISIBLE);
        if (this->videoCloseButton) this->videoCloseButton->setVisibility(brls::Visibility::VISIBLE);
        this->appletFrame->setFooterVisibility(brls::Visibility::VISIBLE);
        this->videoLeftBox->setWidth(width);
        this->videoLeftBox->setGrow(0);
        // setWidthPercentage 会同时设置 minWidth 百分比，恢复时需要清掉
        this->video->setMinWidth(brls::View::AUTO);
        this->video->setWidth(width);
    }
    brls::Logger::debug("[second-screen] layout: enabled={}", enabled);
}

void BasePlayerActivity::pushSecondScreenVideoInfo() {
    if (!wiliwili::second_screen::isEnabled()) return;

    wiliwili::second_screen::VideoInfo info;
    if (dynamic_cast<PlayerSeasonActivity*>(this) != nullptr) {
        info.title = this->seasonInfo.season_title;
        if (!this->episodeResult.title.empty()) {
            if (!info.title.empty()) info.title += " - ";
            info.title += this->episodeResult.title;
        }
        info.up      = this->seasonInfo.up_info.uname;
        info.bvid    = this->episodeResult.bvid;
        info.desc    = this->seasonInfo.season_desc;
        info.pubdate = this->seasonInfo.publish.pub_time_show;
        info.view    = this->seasonInfo.stat.views;
        info.danmaku = this->seasonInfo.stat.danmakus;
        info.like    = this->seasonInfo.stat.likes;
        info.coin    = this->seasonInfo.stat.coins;
        info.favorite = this->seasonInfo.stat.favorite;
    } else {
        info.title   = this->videoDetailResult.title;
        info.up      = this->videoDetailResult.owner.name;
        info.bvid    = this->videoDetailResult.bvid;
        info.desc    = this->videoDetailResult.desc;
        info.pubdate = wiliwili::sec2FullDate(this->videoDetailResult.pubdate);
        info.view    = this->videoDetailResult.stat.view;
        info.danmaku = this->videoDetailResult.stat.danmaku;
        info.like    = this->videoDetailResult.stat.like;
        info.coin    = this->videoDetailResult.stat.coin;
        info.favorite = this->videoDetailResult.stat.favorite;
        info.share   = this->videoDetailResult.stat.share;
    }

    const auto& codes  = this->videoUrlResult.accept_quality;
    const auto& labels = this->videoUrlResult.accept_description;
    for (size_t i = 0; i < codes.size() && i < labels.size(); i++) {
        info.qualities.push_back({codes[i], labels[i]});
    }
    info.qualityIndex = this->getQualityIndex();

    // 上一集/下一集是否可用
    if (dynamic_cast<PlayerSeasonActivity*>(this) != nullptr) {
        int current = static_cast<int>(this->episodeResult.index);
        int total   = static_cast<int>(this->episodeList.size());
        for (int i = current - 1; i >= 0; --i) {
            if (this->episodeList[i].id != 0) {
                info.hasPrev = true;
                break;
            }
        }
        for (int i = current + 1; i < total; ++i) {
            if (this->episodeList[i].id != 0) {
                info.hasNext = true;
                break;
            }
        }
    } else if (this->videoDetailResult.pages.size() > 1) {
        info.hasPrev = this->videoDetailPage.page > 1;
        info.hasNext = this->videoDetailPage.page < this->videoDetailResult.pages.size();
    }

    // 会话恢复时播放器数据可能尚未回填，空标题不要覆盖下屏已有的标题
    if (info.title.empty()) return;

    wiliwili::second_screen::pushVideoInfo(info);

    // 副屏的推荐/分集列表也在这里一起刷新（从其他页面返回时同样适用）
    if (dynamic_cast<PlayerSeasonActivity*>(this) != nullptr) {
        this->pushSecondScreenEpisodeList();
    } else {
        this->pushSecondScreenRelatedList();
        this->pushSecondScreenPageList(this->videoDetailResult.pages);
    }
}

void BasePlayerActivity::pushSecondScreenRelatedList() {
    if (!wiliwili::second_screen::isEnabled() || this->videDetailRelated.empty()) return;

    std::vector<wiliwili::second_screen::RelatedItem> items;
    items.reserve(this->videDetailRelated.size());
    for (const auto& video : this->videDetailRelated) {
        wiliwili::second_screen::RelatedItem item;
        item.title    = video.title;
        item.up       = video.owner.name;
        item.bvid     = video.bvid;
        item.cover    = video.pic;
        item.duration = video.duration;
        item.view     = video.stat.view;
        items.push_back(std::move(item));
    }
    wiliwili::second_screen::pushRelatedVideos(items);
}

void BasePlayerActivity::pushSecondScreenPageList(const bilibili::VideoDetailPageListResult& pages) {
    if (!wiliwili::second_screen::isEnabled() || pages.size() <= 1) return;

    std::vector<wiliwili::second_screen::EpisodeItem> items;
    items.reserve(pages.size());
    for (size_t i = 0; i < pages.size(); i++) {
        wiliwili::second_screen::EpisodeItem item;
        item.index    = static_cast<int>(i);
        item.title    = fmt::format("P{} {}", pages[i].page, pages[i].part);
        item.duration = pages[i].duration;
        item.playable = true;
        items.push_back(std::move(item));
    }
    wiliwili::second_screen::pushEpisodes(items);
}

void BasePlayerActivity::pushSecondScreenEpisodeList() {
    if (!wiliwili::second_screen::isEnabled() || this->episodeList.size() <= 1) return;

    std::vector<wiliwili::second_screen::EpisodeItem> items;
    items.reserve(this->episodeList.size());
    for (size_t i = 0; i < this->episodeList.size(); i++) {
        const auto& episode = this->episodeList[i];
        wiliwili::second_screen::EpisodeItem item;
        item.index    = static_cast<int>(i);
        item.title    = episode.title;
        item.subtitle = episode.long_title;
        // 番剧接口的 duration 单位是毫秒
        item.duration = episode.duration / 1000;
        item.playable = episode.id != 0;
        items.push_back(std::move(item));
    }
    wiliwili::second_screen::pushEpisodes(items);
}

void BasePlayerActivity::pushSecondScreenCachedComments() {
    if (!wiliwili::second_screen::isEnabled()) return;

    auto* datasource = dynamic_cast<DataSourceCommentList*>(this->recyclingGrid->getDataSource());
    if (!datasource || datasource->getData().empty()) return;

    std::vector<wiliwili::second_screen::CommentItem> items;
    items.reserve(datasource->getData().size());
    for (const auto& comment : datasource->getData()) {
        wiliwili::second_screen::CommentItem item;
        item.name = comment.member.uname;
        item.text = comment.content.message;
        item.time = comment.reply_control.time_desc;
        if (item.time.empty()) item.time = wiliwili::sec2FullDate(comment.ctime);
        item.avatar  = comment.member.avatar;
        for (const auto& [token, emote] : comment.content.emote) {
            item.emotes.push_back({token, emote.url, emote.size});
        }
        item.like    = comment.like;
        item.replies = comment.rcount;
        item.up      = comment.member.is_uploader;
        item.top     = comment.top;
        items.push_back(std::move(item));
    }
    wiliwili::second_screen::pushComments(true, this->end, this->commentRequestIndex, 0, items);
}

void BasePlayerActivity::pushSecondScreenPlayerState() {
    if (!wiliwili::second_screen::isEnabled()) return;

    auto& mpv = MPVCore::instance();
    wiliwili::second_screen::PlayerState state;
    state.playing      = mpv.video_playing;
    state.paused       = mpv.video_paused;
    state.danmaku      = DanmakuCore::DANMAKU_ON;
    state.position     = mpv.video_progress;
    state.duration     = mpv.duration;
    state.speed        = mpv.video_speed;
    state.qualityIndex = this->getQualityIndex();
    state.volume       = mpv.volume;
    state.mirror       = MPVCore::VIDEO_MIRROR;
    state.aspect       = MPVCore::VIDEO_ASPECT;

    auto* platform = brls::Application::getPlatform();
    if (platform && platform->canSetBacklightBrightness()) {
        state.canSetBrightness = true;
        state.brightness =
            static_cast<int64_t>(platform->getBacklightBrightness() * 100.0f + 0.5f);
    }

    wiliwili::second_screen::pushPlayerState(state);
}

void BasePlayerActivity::handleSecondScreenCommand(wiliwili::second_screen::Command cmd, int64_t arg,
                                                   const std::string& text) {
    using Command = wiliwili::second_screen::Command;
    switch (cmd) {
        case Command::PLAY_PAUSE:
            if (MPVCore::instance().video_paused) {
                MPVCore::instance().resume();
            } else {
                MPVCore::instance().pause();
            }
            break;
        case Command::SEEK:
            MPVCore::instance().seek(arg);
            break;
        case Command::SEEK_RELATIVE:
            MPVCore::instance().seekRelative(arg);
            break;
        case Command::SET_SPEED:
            this->video->setSpeed(static_cast<float>(arg) / 100.0f);
            break;
        case Command::SET_QUALITY: {
            int code = static_cast<int>(arg);
            const auto& codes = this->videoUrlResult.accept_quality;
            if (code <= 0 || std::find(codes.begin(), codes.end(), code) == codes.end()) break;
            if (ProgramConfig::instance().getCSRF().empty() && code >= 32) {
                std::string message = "wiliwili/home/common/no_login"_i18n;
                brls::Application::notify(message);
                wiliwili::second_screen::pushStatus(message);
                this->pushSecondScreenPlayerState();
                break;
            }
            BasePlayerActivity::defaultQuality = code;
            ProgramConfig::instance().setSettingItem(SettingItem::VIDEO_QUALITY, code);
            this->updateVideoLink();
            break;
        }
        case Command::TOGGLE_DANMAKU:
            this->video->toggleDanmaku();
            this->pushSecondScreenPlayerState();
            break;
        case Command::LOAD_MORE_COMMENTS:
            this->requestVideoComment(std::to_string(this->getAid()));
            break;
        case Command::EXIT_PLAYER:
            brls::Application::popActivity();
            break;
        case Command::OPEN_BV:
            if (!text.empty()) Intent::openBV(text);
            break;
        case Command::PLAY_INDEX:
            this->onIndexChange(static_cast<size_t>(arg));
            break;
        case Command::PREV_EPISODE:
        case Command::NEXT_EPISODE: {
            int delta = cmd == Command::NEXT_EPISODE ? 1 : -1;
            if (dynamic_cast<PlayerSeasonActivity*>(this) != nullptr) {
                int index = static_cast<int>(this->episodeResult.index) + delta;
                int total = static_cast<int>(this->episodeList.size());
                // 跳过“正片/花絮”等不可播放的分组标题
                while (index >= 0 && index < total && this->episodeList[index].id == 0) {
                    index += delta;
                }
                if (index >= 0 && index < total) {
                    this->onIndexChange(static_cast<size_t>(index));
                }
            } else {
                int index = static_cast<int>(this->videoDetailPage.page) - 1 + delta;
                if (index >= 0 && index < static_cast<int>(this->videoDetailResult.pages.size())) {
                    this->onIndexChange(static_cast<size_t>(index));
                }
            }
            break;
        }
        case Command::SET_VOLUME: {
            int64_t volume = std::max<int64_t>(0, std::min<int64_t>(100, arg));
            MPVCore::instance().setVolume(volume);
            this->pushSecondScreenPlayerState();
            break;
        }
        case Command::SET_BRIGHTNESS: {
            auto* platform = brls::Application::getPlatform();
            if (!platform || !platform->canSetBacklightBrightness()) break;
            float value = static_cast<float>(std::max<int64_t>(0, std::min<int64_t>(100, arg))) / 100.0f;
            platform->setBacklightBrightness(value);
            this->pushSecondScreenPlayerState();
            break;
        }
        case Command::TOGGLE_MIRROR:
            MPVCore::instance().setMirror(!MPVCore::VIDEO_MIRROR);
            this->pushSecondScreenPlayerState();
            break;
        case Command::SET_ASPECT: {
            static const std::vector<std::string> options = {"-1", "-2", "-3", "4:3", "16:9"};
            if (std::find(options.begin(), options.end(), text) == options.end()) break;
            MPVCore::instance().setAspect(text);
            ProgramConfig::instance().setSettingItem(SettingItem::PLAYER_ASPECT, text);
            this->pushSecondScreenPlayerState();
            break;
        }
        case Command::SET_SECOND_SCREEN:
            wiliwili::second_screen::setEnabled(arg != 0);
            if (arg == 0 && this->video) this->video->showOSD(true);
            break;
        default:
            break;
    }
}

void BasePlayerActivity::onVideoOnlineCount(const bilibili::VideoOnlineTotal& result) {
    std::string count = result.total + "wiliwili/player/current"_i18n;
    this->videoPeopleLabel->setText(count);
    APP_E->fire(VideoView::SET_ONLINE_NUM, (void*)count.c_str());
}

void BasePlayerActivity::onVideoRelationInfo(const bilibili::VideoRelation& result) {
    brls::Logger::debug("onVideoRelationInfo: {} {} {}", result.like, result.coin, result.favorite);
    this->setRelationButton(result.like, result.coin, result.favorite);
}

void BasePlayerActivity::onHighlightProgress(const bilibili::VideoHighlightProgress& result) {
    brls::Logger::debug("highlight: {}/{}", result.step_sec, result.data.size());
    VideoHighlightData data{result.step_sec, result.data};
    APP_E->fire(VideoView::HIGHLIGHT_INFO, (void*)&data);
}

void BasePlayerActivity::setRelationButton(bool liked, bool coin, bool favorite) {
    if (liked) {
        btnAgree->setImageFromSVGRes("svg/bpx-svg-sprite-liked-active.svg");
    } else {
        btnAgree->setImageFromSVGRes("svg/bpx-svg-sprite-liked.svg");
    }
    if (coin) {
        btnCoin->setImageFromSVGRes("svg/bpx-svg-sprite-coin-active.svg");
    } else {
        btnCoin->setImageFromSVGRes("svg/bpx-svg-sprite-coin.svg");
    }
    if (favorite) {
        btnFavorite->setImageFromSVGRes("svg/bpx-svg-sprite-collection-active.svg");
    } else {
        btnFavorite->setImageFromSVGRes("svg/bpx-svg-sprite-collection.svg");
    }
}

void BasePlayerActivity::onError(const std::string& error) {
    if (!activityShown) return;
    MPVCore::instance().stop();
    MPVCore::instance().reset();
    bool forceClose = true;
    std::string msg = error;
    if (pystring::count(error, "87007") > 0 || pystring::count(error, "87008") > 0) {
        forceClose = false;
        msg        = "该视频为「充电」专属视频";
    } else if (pystring::count(error, "10403") > 0) {
        forceClose = false;
        msg        = "大会员专享限制";
    } else if (pystring::count(error, "404") > 0) {
        msg = "啥都木有";
    } else if (pystring::count(error, "62002") > 0) {
        msg = "稿件不可见";
    }
    auto dialog = new brls::Dialog(msg);
    dialog->setCancelable(false);
    dialog->addButton("hints/ok"_i18n, [forceClose]() {
        if (forceClose) brls::sync([]() { brls::Application::popActivity(); });
    });
    dialog->open();
}

void BasePlayerActivity::willDisappear(bool resetState) {
    activityShown = false;
    brls::Activity::willDisappear(resetState);
}

void BasePlayerActivity::willAppear(bool resetState) {
    activityShown = true;
    brls::Activity::willAppear(resetState);
}

BasePlayerActivity::~BasePlayerActivity() {
    brls::Logger::debug("del BasePlayerActivity");
    // 取消监控mpv
    MPV_E->unsubscribe(eventSubscribeID);
    APP_E->unsubscribe(customEventSubscribeID);
    APP_E->unsubscribe(secondScreenSubscribeID);
    // 双屏：只关闭自己的会话，避免误关栈中其他播放器的副屏
    wiliwili::second_screen::closeSession(this->secondScreenSession);
    // 停止视频播放
    this->video->stop();
}