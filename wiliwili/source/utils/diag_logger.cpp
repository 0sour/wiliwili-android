#include "utils/diag_logger.hpp"

#ifdef __ANDROID__

#include <dirent.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <string>
#include <thread>
#include <vector>

#include <borealis/core/application.hpp>
#include <borealis/core/logger.hpp>
#include <borealis/core/task.hpp>

#include "utils/config_helper.hpp"
#include "view/mpv_core.hpp"

namespace wiliwili {
namespace {

constexpr int LOG_RETENTION_DAYS  = 7;
constexpr long long LOG_MAX_BYTES = 200LL * 1024 * 1024;  // 200 MiB safety cap
constexpr int64_t STALL_THRESHOLD_MS = 8000;
constexpr int64_t STALL_RELOG_MS     = 10000;

std::atomic<int64_t> g_lastHeartbeatMs{0};
std::atomic<bool> g_stalled{false};
int64_t g_startMs = 0;

int64_t steadyMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

void countFds(size_t& total, size_t& sync) {
    total = 0;
    sync  = 0;
    DIR* dir = opendir("/proc/self/fd");
    if (!dir) return;
    int dirFd = dirfd(dir);
    char path[64];
    char target[256];
    while (struct dirent* entry = readdir(dir)) {
        int fd = atoi(entry->d_name);
        if (fd <= 2 || fd == dirFd) continue;
        total++;
        snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
        ssize_t len = readlink(path, target, sizeof(target) - 1);
        if (len > 0) {
            target[len] = '\0';
            if (strstr(target, "sync_file")) sync++;
        }
    }
    closedir(dir);
}

size_t countThreads() {
    size_t count = 0;
    DIR* dir     = opendir("/proc/self/task");
    if (!dir) return 0;
    while (struct dirent* entry = readdir(dir)) {
        if (entry->d_name[0] == '.') continue;
        count++;
    }
    closedir(dir);
    return count;
}

long rssKb() {
    FILE* fp = fopen("/proc/self/status", "r");
    if (!fp) return 0;
    char line[256];
    long rss = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "VmRSS:", 6) == 0) {
            sscanf(line + 6, "%ld", &rss);
            break;
        }
    }
    fclose(fp);
    return rss;
}

const char* mpvState() {
    auto& mpv = MPVCore::instance();
    if (mpv.isStopped()) return "stopped";
    if (mpv.isPaused()) return "paused";
    if (mpv.isPlaying()) return "playing";
    return "unknown";
}

bool hasSuffix(const std::string& value, const std::string& suffix) {
    return value.size() >= suffix.size() &&
           value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

struct LogFileInfo {
    std::string path;
    time_t mtime;
    long long size;
};

void cleanOldLogs(const std::string& dir) {
    DIR* dp = opendir(dir.c_str());
    if (!dp) return;

    const time_t now     = time(nullptr);
    const time_t maxAge  = (time_t)LOG_RETENTION_DAYS * 24 * 3600;
    std::vector<LogFileInfo> files;
    long long totalSize = 0;

    while (struct dirent* entry = readdir(dp)) {
        std::string name = entry->d_name;
        if (name.compare(0, 9, "wiliwili_") != 0 || !hasSuffix(name, ".log")) continue;
        std::string path = dir + "/" + name;
        struct stat st;
        if (stat(path.c_str(), &st) != 0) continue;
        if (now - st.st_mtime > maxAge) {
            remove(path.c_str());
            continue;
        }
        files.push_back({path, st.st_mtime, (long long)st.st_size});
        totalSize += (long long)st.st_size;
    }
    closedir(dp);

    if (totalSize > LOG_MAX_BYTES) {
        std::sort(files.begin(), files.end(),
                  [](const LogFileInfo& a, const LogFileInfo& b) { return a.mtime < b.mtime; });
        for (const auto& file : files) {
            if (totalSize <= LOG_MAX_BYTES) break;
            if (remove(file.path.c_str()) == 0) totalSize -= file.size;
        }
    }
}

std::string makeLogPath(const std::string& dir) {
    char stamp[32];
    time_t now = time(nullptr);
    struct tm tmNow;
    localtime_r(&now, &tmNow);
    strftime(stamp, sizeof(stamp), "%Y%m%d_%H%M%S", &tmNow);
    return dir + "/wiliwili_" + stamp + ".log";
}

void dumpThreads() {
    DIR* dir = opendir("/proc/self/task");
    if (!dir) return;
    int logged = 0;
    while (struct dirent* entry = readdir(dir)) {
        if (entry->d_name[0] == '.') continue;
        if (logged++ >= 128) break;
        std::string base = std::string("/proc/self/task/") + entry->d_name;

        char comm[64] = "";
        FILE* fp = fopen((base + "/comm").c_str(), "r");
        if (fp) {
            if (fgets(comm, sizeof(comm), fp)) {
                size_t len = strlen(comm);
                if (len && comm[len - 1] == '\n') comm[len - 1] = '\0';
            }
            fclose(fp);
        }

        char state  = '?';
        char wchan[64] = "";
        fp = fopen((base + "/stat").c_str(), "r");
        if (fp) {
            char buf[512] = "";
            if (fgets(buf, sizeof(buf), fp)) {
                char* close = strrchr(buf, ')');
                if (close && close[1] == ' ') state = close[2];
            }
            fclose(fp);
        }
        fp = fopen((base + "/wchan").c_str(), "r");
        if (fp) {
            if (!fgets(wchan, sizeof(wchan), fp)) wchan[0] = '\0';
            fclose(fp);
        }
        brls::Logger::warning("[diag]   tid={} comm={} state={} wchan={}", entry->d_name, comm, state, wchan);
    }
    closedir(dir);
}

void logHealth(const char* tag) {
    size_t fd = 0, sync = 0;
    countFds(fd, sync);
    brls::Logger::info("[diag] {} up={}s fps={} fd={} sync={} rss={}MB thr={} mpv={}", tag,
                       (steadyMs() - g_startMs) / 1000, brls::Application::getFPS(), fd, sync,
                       rssKb() / 1024, countThreads(), mpvState());
}

class DiagTickTask : public brls::RepeatingTask {
  public:
    DiagTickTask() : brls::RepeatingTask(1000) {}
    void run() override {
        g_lastHeartbeatMs.store(steadyMs(), std::memory_order_release);
        if (++ticks_ % 10 == 0) logHealth("health");
    }

  private:
    int ticks_ = 0;
};

void watchdogLoop() {
    int64_t lastStallLog = 0;
    while (true) {
        std::this_thread::sleep_for(std::chrono::seconds(5));

        int64_t now    = steadyMs();
        int64_t last   = g_lastHeartbeatMs.load(std::memory_order_acquire);
        int64_t silent = now - last;

        if (silent > STALL_THRESHOLD_MS) {
            if (!g_stalled.exchange(true) || now - lastStallLog > STALL_RELOG_MS) {
                lastStallLog = now;
                brls::Logger::error("[diag] UI STALL: no main-loop tick for {} ms", silent);
                logHealth("stall");
                dumpThreads();
            }
        } else if (g_stalled.exchange(false)) {
            brls::Logger::warning("[diag] UI recovered after {} ms stall", silent);
        }
    }
}

}  // namespace

void startFileLogger() {
    std::string dir = ProgramConfig::instance().getConfigDir() + "/logs";
    if (mkdir(dir.c_str(), 0755) != 0 && errno != EEXIST) {
        brls::Logger::error("[diag] cannot create log dir {}", dir);
        return;
    }
    cleanOldLogs(dir);

    std::string path = makeLogPath(dir);
    FILE* fp = fopen(path.c_str(), "w");
    if (!fp) {
        brls::Logger::error("[diag] cannot open log file {}", path);
        return;
    }

    brls::Logger::setThreadSafeLogging(true);
    brls::Logger::setLogOutput(fp);

    g_startMs = steadyMs();
    g_lastHeartbeatMs.store(g_startMs, std::memory_order_release);

    struct rlimit limit{};
    getrlimit(RLIMIT_NOFILE, &limit);
    brls::Logger::info("[diag] logging to {}", path);
    brls::Logger::info("[diag] retention={} days, max={} MB, fd limit={}", LOG_RETENTION_DAYS,
                       LOG_MAX_BYTES / 1024 / 1024, (long long)limit.rlim_cur);
}

void startStallWatchdog() {
    static bool started = false;
    if (started) return;
    started = true;

    (new DiagTickTask())->start();
    std::thread(watchdogLoop).detach();
    brls::Logger::info("[diag] health line every 10s, stall watchdog enabled");
}

}  // namespace wiliwili

#else

namespace wiliwili {
void startFileLogger() {}
void startStallWatchdog() {}
}  // namespace wiliwili

#endif
