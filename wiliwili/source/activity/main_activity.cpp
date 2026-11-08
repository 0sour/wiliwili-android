/*
    Copyright 2020-2021 natinusala

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

#include <borealis/core/touch/tap_gesture.hpp>

#include "activity/main_activity.hpp"
#include "utils/activity_helper.hpp"
#include "utils/dialog_helper.hpp"
#include "utils/second_screen.hpp"
#include "view/custom_button.hpp"
#include "view/auto_tab_frame.hpp"
#include "view/svg_image.hpp"

MainActivity::~MainActivity() {
    wiliwili::second_screen::closeSession(this->homeSession);
    brls::Logger::debug("del MainActivity");
}

void MainActivity::onResume() {
    brls::Activity::onResume();
    this->startHomeSession();
}

void MainActivity::startHomeSession() {
    if (!wiliwili::second_screen::isEnabled()) {
        wiliwili::second_screen::closeSession(this->homeSession);
        this->homeSession = 0;
        return;
    }

    // The lower screen keeps a companion dashboard while the main page is active.
    this->homeSession = wiliwili::second_screen::openSession(wiliwili::second_screen::Mode::HOME);
    wiliwili::second_screen::setCommandHandler(
        this->homeSession,
        [this](wiliwili::second_screen::Command cmd, int64_t arg, const std::string& text) {
            using Command = wiliwili::second_screen::Command;
            switch (cmd) {
                case Command::OPEN_SEARCH:
                    if (text.empty()) {
                        Intent::openTVSearch();
                    } else {
                        Intent::openSearch(text);
                    }
                    break;
                case Command::OPEN_SETTINGS:
                    Intent::openSetting();
                    break;
                case Command::REFRESH_HOME:
                    if (this->tabFrame) this->tabFrame->refresh();
                    wiliwili::second_screen::pushTheme();
                    break;
                case Command::MAIN_TAB:
                    if (this->tabFrame) this->tabFrame->focusTab(static_cast<int>(arg));
                    break;
                case Command::SET_SECOND_SCREEN:
                    wiliwili::second_screen::setEnabled(arg != 0);
                    break;
                default:
                    break;
            }
        });
}

void MainActivity::onPause() {
    wiliwili::second_screen::closeSession(this->homeSession);
    this->homeSession = 0;
    brls::Activity::onPause();
}

void MainActivity::onContentAvailable() {
    this->registerAction(
        "Settings", brls::ControllerButton::BUTTON_BACK,
        [](brls::View* view) -> bool {
            Intent::openSetting();
            return true;
        },
        true);

    this->registerAction(
        "Settings", brls::ControllerButton::BUTTON_START,
        [](brls::View* view) -> bool {
            Intent::openSetting();
            return true;
        },
        true);

    this->settingBtn->registerClickAction([](brls::View* view) -> bool {
        Intent::openSetting();
        return true;
    });

    this->settingBtn->getFocusEvent()->subscribe([this](bool value) {
        SVGImage* image = dynamic_cast<SVGImage*>(this->settingBtn->getChildren()[0]);
        if (!image) return;
        if (value) {
            image->setImageFromSVGRes("svg/ico-setting-activate.svg");
        } else {
            image->setImageFromSVGRes("svg/ico-setting.svg");
        }
    });

    this->inboxBtn->setCustomNavigation([this](brls::FocusDirection direction) {
        if (tabFrame->getSideBarPosition() == AutoTabBarPosition::LEFT) {
            if (direction == brls::FocusDirection::RIGHT) {
                return (brls::View*)this->tabFrame->getActiveTab();
            } else if (direction == brls::FocusDirection::UP) {
                return (brls::View*)this->tabFrame->getSidebar();
            }
        } else if (tabFrame->getSideBarPosition() == AutoTabBarPosition::TOP) {
            if (direction == brls::FocusDirection::DOWN) {
                return (brls::View*)this->tabFrame->getActiveTab();
            } else if (direction == brls::FocusDirection::LEFT) {
                return (brls::View*)this->tabFrame->getSidebar();
            }
        }
        return (brls::View*)nullptr;
    });
    this->settingBtn->setCustomNavigation([this](brls::FocusDirection direction) {
        if (tabFrame->getSideBarPosition() == AutoTabBarPosition::LEFT) {
            if (direction == brls::FocusDirection::RIGHT) {
                return (brls::View*)this->tabFrame->getActiveTab();
            } else if (direction == brls::FocusDirection::UP) {
                return (brls::View*)this->inboxBtn;
            }
        } else if (tabFrame->getSideBarPosition() == AutoTabBarPosition::TOP) {
            if (direction == brls::FocusDirection::DOWN) {
                return (brls::View*)this->tabFrame->getActiveTab();
            } else if (direction == brls::FocusDirection::LEFT) {
                return (brls::View*)this->inboxBtn;
            }
        }
        return (brls::View*)nullptr;
    });
    this->settingBtn->addGestureRecognizer(new brls::TapGestureRecognizer(this->settingBtn));

    this->inboxBtn->registerClickAction([](brls::View* view) -> bool {
        if (DialogHelper::checkLogin()) Intent::openInbox();
        return true;
    });

    this->inboxBtn->getFocusEvent()->subscribe([this](bool value) {
        SVGImage* image = dynamic_cast<SVGImage*>(this->inboxBtn->getChildren()[0]);
        if (!image) return;
        if (value) {
            image->setImageFromSVGRes("svg/ico-inbox-activate.svg");
        } else {
            image->setImageFromSVGRes("svg/ico-inbox.svg");
        }
    });
    this->inboxBtn->addGestureRecognizer(new brls::TapGestureRecognizer(this->inboxBtn));

    // pushActivity() does not call onResume(), so open the HOME session here too.
    this->startHomeSession();
}