// Runs inside the Shizuku user-service process (UID 2000 / shell). Opens the two Joy-Cons'
// /dev/input/eventX nodes directly and EVIOCGRAB's them — this bypasses Android's normal
// focus-gated InputDispatcher entirely, which is the only reason a merge that works while some
// other app (a game) holds focus is possible without root. See the README for exactly which
// SELinux policies this depends on and what happens when they block it.

#include <jni.h>
#include <android/log.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <cerrno>
#include <cstring>
#include <cmath>
#include <string>
#include <sstream>
#include <vector>
#include <map>
#include <mutex>
#include <thread>
#include <atomic>

#define LOG_TAG "joyncon_native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int VENDOR_NINTENDO = 0x057e;
constexpr int PRODUCT_JOYCON_L = 0x2006;
constexpr int PRODUCT_JOYCON_R = 0x2007;
constexpr int ROLE_LEFT = 0;
constexpr int ROLE_RIGHT = 1;

std::string json_escape(const std::string &in) {
    std::string out;
    out.reserve(in.size());
    for (char c : in) {
        if (c == '"' || c == '\\') out += '\\';
        if ((unsigned char) c < 0x20) continue;
        out += c;
    }
    return out;
}

struct AbsInfoCache {
    int minVal = 0;
    int maxVal = 0;
    bool known = false;
};

struct DeviceCtx {
    std::atomic<int> fd{-1};
    std::atomic<bool> grabbed{false};
    std::atomic<bool> running{false};
    std::string name;
    std::string rawCodesJson = "[]";
    std::map<int, AbsInfoCache> absInfo;
    std::thread th;
};

struct StickCal {
    int deadzonePercent = 12;
    bool invertX = false;
    bool invertY = false;
};

struct MapEntry {
    int role;      // 0 = LEFT, 1 = RIGHT
    int rawType;   // 0 = KEY, 1 = ABS
    int rawCode;
    std::string output;
};

enum class OutKind { KEY, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, STICK_LX, STICK_LY, STICK_RX, STICK_RY,
                      TRIGGER_L_AXIS, TRIGGER_R_AXIS };

struct OutputSpec {
    OutKind kind;
    int keyCode = 0;
};

DeviceCtx g_left, g_right;
std::mutex g_state_mutex;

int g_uinput_fd = -1;
std::atomic<bool> g_uinput_active{false};
std::string g_last_error;

std::vector<MapEntry> g_mapping;
StickCal g_leftCal, g_rightCal;
std::mutex g_mapping_mutex;

std::atomic<bool> g_dpad_up{false}, g_dpad_down{false}, g_dpad_left{false}, g_dpad_right{false};

std::atomic<bool> g_supervisor_running{false};
std::thread g_supervisor_thread;

const std::map<std::string, OutputSpec> &output_table() {
    static const std::map<std::string, OutputSpec> table = {
        {"BUTTON_A", {OutKind::KEY, BTN_SOUTH}},
        {"BUTTON_B", {OutKind::KEY, BTN_EAST}},
        {"BUTTON_X", {OutKind::KEY, BTN_NORTH}},
        {"BUTTON_Y", {OutKind::KEY, BTN_WEST}},
        {"BUMPER_L", {OutKind::KEY, BTN_TL}},
        {"BUMPER_R", {OutKind::KEY, BTN_TR}},
        {"TRIGGER_ZL", {OutKind::KEY, BTN_TL2}},
        {"TRIGGER_ZR", {OutKind::KEY, BTN_TR2}},
        {"TRIGGER_ZL_AXIS", {OutKind::TRIGGER_L_AXIS, 0}},
        {"TRIGGER_ZR_AXIS", {OutKind::TRIGGER_R_AXIS, 0}},
        {"BUTTON_MINUS", {OutKind::KEY, BTN_SELECT}},
        {"BUTTON_PLUS", {OutKind::KEY, BTN_START}},
        {"BUTTON_HOME", {OutKind::KEY, BTN_MODE}},
        {"BUTTON_CAPTURE", {OutKind::KEY, BTN_Z}},
        {"STICK_L_CLICK", {OutKind::KEY, BTN_THUMBL}},
        {"STICK_R_CLICK", {OutKind::KEY, BTN_THUMBR}},
        {"RAIL_SL_LEFT", {OutKind::KEY, BTN_TRIGGER_HAPPY1}},
        {"RAIL_SR_LEFT", {OutKind::KEY, BTN_TRIGGER_HAPPY2}},
        {"RAIL_SL_RIGHT", {OutKind::KEY, BTN_TRIGGER_HAPPY3}},
        {"RAIL_SR_RIGHT", {OutKind::KEY, BTN_TRIGGER_HAPPY4}},
        {"DPAD_UP", {OutKind::DPAD_UP, 0}},
        {"DPAD_DOWN", {OutKind::DPAD_DOWN, 0}},
        {"DPAD_LEFT", {OutKind::DPAD_LEFT, 0}},
        {"DPAD_RIGHT", {OutKind::DPAD_RIGHT, 0}},
        {"LEFT_STICK_X", {OutKind::STICK_LX, 0}},
        {"LEFT_STICK_Y", {OutKind::STICK_LY, 0}},
        {"RIGHT_STICK_X", {OutKind::STICK_RX, 0}},
        {"RIGHT_STICK_Y", {OutKind::STICK_RY, 0}},
    };
    return table;
}

bool output_spec_for(const std::string &name, OutputSpec &out) {
    auto &table = output_table();
    auto it = table.find(name);
    if (it == table.end()) return false;
    out = it->second;
    return true;
}

std::string key_name(int code) {
    static const std::map<int, std::string> names = {
        {BTN_SOUTH, "BTN_SOUTH"}, {BTN_EAST, "BTN_EAST"}, {BTN_C, "BTN_C"}, {BTN_NORTH, "BTN_NORTH"},
        {BTN_WEST, "BTN_WEST"}, {BTN_Z, "BTN_Z"}, {BTN_TL, "BTN_TL"}, {BTN_TR, "BTN_TR"},
        {BTN_TL2, "BTN_TL2"}, {BTN_TR2, "BTN_TR2"}, {BTN_SELECT, "BTN_SELECT"}, {BTN_START, "BTN_START"},
        {BTN_MODE, "BTN_MODE"}, {BTN_THUMBL, "BTN_THUMBL"}, {BTN_THUMBR, "BTN_THUMBR"},
        {BTN_DPAD_UP, "BTN_DPAD_UP"}, {BTN_DPAD_DOWN, "BTN_DPAD_DOWN"}, {BTN_DPAD_LEFT, "BTN_DPAD_LEFT"},
        {BTN_DPAD_RIGHT, "BTN_DPAD_RIGHT"}, {BTN_TRIGGER_HAPPY1, "BTN_TRIGGER_HAPPY1"},
        {BTN_TRIGGER_HAPPY2, "BTN_TRIGGER_HAPPY2"}, {BTN_TRIGGER_HAPPY3, "BTN_TRIGGER_HAPPY3"},
        {BTN_TRIGGER_HAPPY4, "BTN_TRIGGER_HAPPY4"},
    };
    auto it = names.find(code);
    if (it != names.end()) return it->second;
    return "KEY_" + std::to_string(code);
}

std::string abs_name(int code) {
    static const std::map<int, std::string> names = {
        {ABS_X, "ABS_X"}, {ABS_Y, "ABS_Y"}, {ABS_Z, "ABS_Z"}, {ABS_RX, "ABS_RX"}, {ABS_RY, "ABS_RY"},
        {ABS_RZ, "ABS_RZ"}, {ABS_HAT0X, "ABS_HAT0X"}, {ABS_HAT0Y, "ABS_HAT0Y"},
    };
    auto it = names.find(code);
    if (it != names.end()) return it->second;
    return "ABS_" + std::to_string(code);
}

std::string extract_str(const std::string &s, const std::string &key) {
    auto pos = s.find("\"" + key + "\":\"");
    if (pos == std::string::npos) return "";
    pos += key.size() + 4;
    auto end = s.find('"', pos);
    if (end == std::string::npos) return "";
    return s.substr(pos, end - pos);
}

long extract_int(const std::string &s, const std::string &key) {
    auto pos = s.find("\"" + key + "\":");
    if (pos == std::string::npos) return 0;
    pos += key.size() + 3;
    size_t end = pos;
    while (end < s.size() && (isdigit((unsigned char) s[end]) || s[end] == '-')) end++;
    if (end == pos) return 0;
    return strtol(s.substr(pos, end - pos).c_str(), nullptr, 10);
}

bool extract_bool(const std::string &s, const std::string &key) {
    auto pos = s.find("\"" + key + "\":");
    if (pos == std::string::npos) return false;
    pos += key.size() + 3;
    return s.compare(pos, 4, "true") == 0;
}

void parse_mapping(const std::string &json) {
    std::vector<MapEntry> newMapping;
    auto entriesPos = json.find("\"entries\":[");
    if (entriesPos != std::string::npos) {
        size_t i = entriesPos + strlen("\"entries\":[");
        int depth = 0;
        size_t objStart = std::string::npos;
        while (i < json.size()) {
            char c = json[i];
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != std::string::npos) {
                    std::string obj = json.substr(objStart, i - objStart + 1);
                    MapEntry e{};
                    e.role = (extract_str(obj, "role") == "RIGHT") ? ROLE_RIGHT : ROLE_LEFT;
                    e.rawType = (extract_str(obj, "rawType") == "ABS") ? 1 : 0;
                    e.rawCode = (int) extract_int(obj, "rawCode");
                    e.output = extract_str(obj, "output");
                    newMapping.push_back(e);
                    objStart = std::string::npos;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
            i++;
        }
    }

    auto parse_cal = [&](const std::string &key) {
        StickCal cal;
        auto pos = json.find("\"" + key + "\":{");
        if (pos != std::string::npos) {
            auto end = json.find('}', pos);
            if (end != std::string::npos) {
                std::string chunk = json.substr(pos, end - pos + 1);
                cal.deadzonePercent = (int) extract_int(chunk, "deadzone");
                cal.invertX = extract_bool(chunk, "invertX");
                cal.invertY = extract_bool(chunk, "invertY");
            }
        }
        return cal;
    };
    StickCal lc = parse_cal("leftCal");
    StickCal rc = parse_cal("rightCal");

    std::lock_guard<std::mutex> lock(g_mapping_mutex);
    g_mapping = std::move(newMapping);
    g_leftCal = lc;
    g_rightCal = rc;
}

std::string enumerate_codes_json(int fd) {
    std::ostringstream out;
    out << "[";
    bool first = true;
    unsigned char keybits[96] = {0};
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keybits)), keybits) >= 0) {
        for (int code = 0; code < 768; code++) {
            if ((keybits[code / 8] >> (code % 8)) & 1) {
                if (!first) out << ",";
                first = false;
                out << "{\"type\":\"KEY\",\"code\":" << code << ",\"name\":\"" << json_escape(key_name(code)) << "\"}";
            }
        }
    }
    unsigned char absbits[8] = {0};
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absbits)), absbits) >= 0) {
        for (int code = 0; code < 64; code++) {
            if ((absbits[code / 8] >> (code % 8)) & 1) {
                if (!first) out << ",";
                first = false;
                out << "{\"type\":\"ABS\",\"code\":" << code << ",\"name\":\"" << json_escape(abs_name(code)) << "\"}";
            }
        }
    }
    out << "]";
    return out.str();
}

/** Opens (without grabbing) whichever /dev/input node matches the requested Joy-Con role. */
bool try_open_and_identify(int role, int &outFd, std::string &outName, std::map<int, AbsInfoCache> &outAbs,
                            int *outDeniedCount = nullptr, int *outIoctlDeniedCount = nullptr) {
    DIR *dir = opendir("/dev/input");
    if (!dir) {
        if (outDeniedCount && (errno == EACCES || errno == EPERM)) *outDeniedCount += 1;
        return false;
    }
    int wantProduct = (role == ROLE_LEFT) ? PRODUCT_JOYCON_L : PRODUCT_JOYCON_R;
    bool found = false;
    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string fname = entry->d_name;
        if (fname.rfind("event", 0) != 0) continue;
        std::string path = "/dev/input/" + fname;
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd < 0) {
            if (outDeniedCount && (errno == EACCES || errno == EPERM)) *outDeniedCount += 1;
            continue;
        }

        struct input_id id{};
        if (ioctl(fd, EVIOCGID, &id) < 0) {
            if (outIoctlDeniedCount && (errno == EACCES || errno == EPERM)) *outIoctlDeniedCount += 1;
            close(fd);
            continue;
        }
        if (id.vendor != VENDOR_NINTENDO || id.product != wantProduct) {
            close(fd);
            continue;
        }

        // Each Joy-Con exposes two evdev nodes with the SAME vendor/product: the actual
        // button/stick device, and a separate motion-sensor (IMU) device with no EV_KEY bits at
        // all. Vendor/product alone can't tell them apart, and readdir() order isn't guaranteed,
        // so without this check the grab could silently land on the IMU node — EVIOCGRAB still
        // succeeds (it's a real device), but no button ever fires and the "stick" is just
        // accelerometer noise.
        unsigned char keybits[96] = {0};
        bool hasAnyKey = false;
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keybits)), keybits) >= 0) {
            for (unsigned char b : keybits) {
                if (b != 0) { hasAnyKey = true; break; }
            }
        }
        if (!hasAnyKey) {
            close(fd);
            continue;
        }

        char nameBuf[256] = {0};
        if (ioctl(fd, EVIOCGNAME(sizeof(nameBuf) - 1), nameBuf) < 0) {
            strncpy(nameBuf, "Joy-Con", sizeof(nameBuf) - 1);
        }

        std::map<int, AbsInfoCache> absInfo;
        for (int code : {ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_HAT0X, ABS_HAT0Y}) {
            struct input_absinfo info{};
            if (ioctl(fd, EVIOCGABS(code), &info) >= 0) {
                absInfo[code] = {info.minimum, info.maximum, true};
            }
        }

        outFd = fd;
        outName = nameBuf;
        outAbs = absInfo;
        found = true;
        break;
    }
    closedir(dir);
    return found;
}

void emit(int type, int code, int value) {
    if (g_uinput_fd < 0) return;
    struct input_event ev{};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    ssize_t written = write(g_uinput_fd, &ev, sizeof(ev));
    (void) written;
}

void update_hat_x() {
    int v = g_dpad_right.load() ? 1 : (g_dpad_left.load() ? -1 : 0);
    emit(EV_ABS, ABS_HAT0X, v);
    emit(EV_SYN, SYN_REPORT, 0);
}

void update_hat_y() {
    int v = g_dpad_down.load() ? 1 : (g_dpad_up.load() ? -1 : 0);
    emit(EV_ABS, ABS_HAT0Y, v);
    emit(EV_SYN, SYN_REPORT, 0);
}

std::string find_output_for(int role, int rawType, int code) {
    std::lock_guard<std::mutex> lock(g_mapping_mutex);
    for (auto &e : g_mapping) {
        if (e.role == role && e.rawType == rawType && e.rawCode == code) return e.output;
    }
    return "";
}

void handle_key(int role, int code, int value) {
    if (value == 2) return; // ignore autorepeat
    std::string outName = find_output_for(role, 0, code);
    if (outName.empty() || outName == "UNMAPPED") return;
    OutputSpec spec;
    if (!output_spec_for(outName, spec)) return;
    bool pressed = value != 0;
    switch (spec.kind) {
        case OutKind::KEY:
            emit(EV_KEY, spec.keyCode, pressed ? 1 : 0);
            emit(EV_SYN, SYN_REPORT, 0);
            break;
        case OutKind::DPAD_UP: g_dpad_up = pressed; update_hat_y(); break;
        case OutKind::DPAD_DOWN: g_dpad_down = pressed; update_hat_y(); break;
        case OutKind::DPAD_LEFT: g_dpad_left = pressed; update_hat_x(); break;
        case OutKind::DPAD_RIGHT: g_dpad_right = pressed; update_hat_x(); break;
        case OutKind::TRIGGER_L_AXIS:
            emit(EV_ABS, ABS_Z, pressed ? 255 : 0);
            emit(EV_SYN, SYN_REPORT, 0);
            break;
        case OutKind::TRIGGER_R_AXIS:
            emit(EV_ABS, ABS_RZ, pressed ? 255 : 0);
            emit(EV_SYN, SYN_REPORT, 0);
            break;
        default: break;
    }
}

void handle_abs(int role, int code, int value, DeviceCtx &ctx) {
    std::string outName = find_output_for(role, 1, code);
    if (outName.empty() || outName == "UNMAPPED") return;
    OutputSpec spec;
    if (!output_spec_for(outName, spec)) return;

    if (spec.kind == OutKind::DPAD_UP) { g_dpad_up = value < 0; update_hat_y(); return; }
    if (spec.kind == OutKind::DPAD_DOWN) { g_dpad_down = value > 0; update_hat_y(); return; }
    if (spec.kind == OutKind::DPAD_LEFT) { g_dpad_left = value < 0; update_hat_x(); return; }
    if (spec.kind == OutKind::DPAD_RIGHT) { g_dpad_right = value > 0; update_hat_x(); return; }

    int minIn = -32767, maxIn = 32767;
    auto it = ctx.absInfo.find(code);
    if (it != ctx.absInfo.end() && it->second.known) {
        minIn = it->second.minVal;
        maxIn = it->second.maxVal;
    }
    if (maxIn == minIn) return;

    double norm = (double) (value - minIn) / (double) (maxIn - minIn);
    double centered = norm * 2.0 - 1.0;

    StickCal cal;
    bool isX = (spec.kind == OutKind::STICK_LX || spec.kind == OutKind::STICK_RX);
    {
        std::lock_guard<std::mutex> lock(g_mapping_mutex);
        cal = (spec.kind == OutKind::STICK_LX || spec.kind == OutKind::STICK_LY) ? g_leftCal : g_rightCal;
    }
    if (isX && cal.invertX) centered = -centered;
    if (!isX && cal.invertY) centered = -centered;

    double dz = cal.deadzonePercent / 100.0;
    if (std::fabs(centered) < dz) {
        centered = 0.0;
    } else {
        double sign = centered < 0 ? -1.0 : 1.0;
        centered = sign * ((std::fabs(centered) - dz) / (1.0 - dz));
    }

    int outVal = (int) std::lround(centered * 32767.0);
    int outCode = (spec.kind == OutKind::STICK_LX) ? ABS_X
                : (spec.kind == OutKind::STICK_LY) ? ABS_Y
                : (spec.kind == OutKind::STICK_RX) ? ABS_RX
                : ABS_RY;
    emit(EV_ABS, outCode, outVal);
    emit(EV_SYN, SYN_REPORT, 0);
}

void reader_loop(DeviceCtx *ctx, int role) {
    while (ctx->running.load()) {
        int fd = ctx->fd.load();
        if (fd < 0) break;
        struct pollfd pfd{};
        pfd.fd = fd;
        pfd.events = POLLIN;
        int rc = poll(&pfd, 1, 200);
        if (rc < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (rc == 0) continue;
        if (pfd.revents & (POLLHUP | POLLERR | POLLNVAL)) break;
        if (pfd.revents & POLLIN) {
            struct input_event ev{};
            while (true) {
                ssize_t n = read(fd, &ev, sizeof(ev));
                if (n != (ssize_t) sizeof(ev)) break;
                if (ev.type == EV_KEY) handle_key(role, ev.code, ev.value);
                else if (ev.type == EV_ABS) handle_abs(role, ev.code, ev.value, *ctx);
            }
        }
    }
    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        int fd = ctx->fd.load();
        if (fd >= 0) {
            if (ctx->grabbed.load()) ioctl(fd, EVIOCGRAB, 0);
            close(fd);
        }
        ctx->fd = -1;
        ctx->grabbed = false;
    }
    ctx->running = false;
}

void release_device(DeviceCtx &ctx) {
    ctx.running = false;
    if (ctx.th.joinable()) ctx.th.join();
    std::lock_guard<std::mutex> lock(g_state_mutex);
    int fd = ctx.fd.load();
    if (fd >= 0) {
        if (ctx.grabbed.load()) ioctl(fd, EVIOCGRAB, 0);
        close(fd);
    }
    ctx.fd = -1;
    ctx.grabbed = false;
}

bool create_uinput_device() {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_last_error = std::string("open /dev/uinput failed: ") + strerror(errno);
        LOGE("%s", g_last_error.c_str());
        return false;
    }

    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    for (auto &kv : output_table()) {
        if (kv.second.kind == OutKind::KEY) ioctl(fd, UI_SET_KEYBIT, kv.second.keyCode);
    }

    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    for (int code : {ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_HAT0X, ABS_HAT0Y, ABS_Z, ABS_RZ}) {
        ioctl(fd, UI_SET_ABSBIT, code);
    }

    struct uinput_setup usetup{};
    usetup.id.bustype = BUS_VIRTUAL;
    usetup.id.vendor = 0x0ee2;
    usetup.id.product = 0x0001;
    usetup.id.version = 1;
    strncpy(usetup.name, "JoynCon Virtual Gamepad", sizeof(usetup.name) - 1);
    if (ioctl(fd, UI_DEV_SETUP, &usetup) < 0) {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_last_error = std::string("UI_DEV_SETUP failed: ") + strerror(errno);
        LOGE("%s", g_last_error.c_str());
        close(fd);
        return false;
    }

    auto setup_abs = [&](int code, int minV, int maxV, int flat) {
        struct uinput_abs_setup asetup{};
        asetup.code = code;
        asetup.absinfo.minimum = minV;
        asetup.absinfo.maximum = maxV;
        asetup.absinfo.flat = flat;
        asetup.absinfo.fuzz = 0;
        ioctl(fd, UI_ABS_SETUP, &asetup);
    };
    setup_abs(ABS_X, -32767, 32767, 1000);
    setup_abs(ABS_Y, -32767, 32767, 1000);
    setup_abs(ABS_RX, -32767, 32767, 1000);
    setup_abs(ABS_RY, -32767, 32767, 1000);
    setup_abs(ABS_HAT0X, -1, 1, 0);
    setup_abs(ABS_HAT0Y, -1, 1, 0);
    // min == 0 is what makes Android/Unity read these as unidirectional trigger axes (0.0-1.0)
    // rather than a symmetric joystick axis (-1.0-1.0).
    setup_abs(ABS_Z, 0, 255, 0);
    setup_abs(ABS_RZ, 0, 255, 0);

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_last_error = std::string("UI_DEV_CREATE failed: ") + strerror(errno);
        LOGE("%s", g_last_error.c_str());
        close(fd);
        return false;
    }

    g_uinput_fd = fd;
    g_uinput_active = true;
    return true;
}

void destroy_uinput_device() {
    if (g_uinput_fd >= 0) {
        ioctl(g_uinput_fd, UI_DEV_DESTROY);
        close(g_uinput_fd);
    }
    g_uinput_fd = -1;
    g_uinput_active = false;
}

void supervisor_loop() {
    while (g_supervisor_running.load()) {
        for (int role = 0; role < 2; role++) {
            DeviceCtx &ctx = (role == ROLE_LEFT) ? g_left : g_right;
            bool needScan;
            {
                std::lock_guard<std::mutex> lock(g_state_mutex);
                needScan = ctx.fd.load() < 0;
            }
            if (!needScan) continue;

            int fd = -1;
            std::string name;
            std::map<int, AbsInfoCache> absInfo;
            int deniedCount = 0;
            int ioctlDeniedCount = 0;
            if (!try_open_and_identify(role, fd, name, absInfo, &deniedCount, &ioctlDeniedCount)) {
                if (deniedCount > 0) {
                    std::lock_guard<std::mutex> lock(g_state_mutex);
                    g_last_error = "Permission denied opening /dev/input/eventX as shell — "
                                   "SELinux policy likely blocks this device; see README";
                } else if (ioctlDeniedCount > 0) {
                    std::lock_guard<std::mutex> lock(g_state_mutex);
                    g_last_error = "shell can open /dev/input nodes but EVIOCGID is denied by SELinux's "
                                   "ioctl allowlist for this device type — see README";
                }
                continue;
            }

            if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                std::string err = "EVIOCGRAB failed for " + name + ": " + strerror(errno);
                LOGE("%s", err.c_str());
                {
                    std::lock_guard<std::mutex> lock(g_state_mutex);
                    g_last_error = err;
                }
                close(fd);
                continue;
            }
            std::string codesJson = enumerate_codes_json(fd);
            {
                std::lock_guard<std::mutex> lock(g_state_mutex);
                ctx.fd = fd;
                ctx.grabbed = true;
                ctx.name = name;
                ctx.absInfo = absInfo;
                ctx.rawCodesJson = codesJson;
            }
            if (ctx.th.joinable()) ctx.th.join();
            ctx.running = true;
            ctx.th = std::thread(reader_loop, &ctx, role);
            LOGI("Grabbed role %d (%s)", role, name.c_str());
        }
        for (int i = 0; i < 10 && g_supervisor_running.load(); i++) usleep(100 * 1000);
    }
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeScanRawDevices(JNIEnv *env, jobject) {
    std::ostringstream out;
    out << "{";
    bool first = true;
    for (int role = 0; role < 2; role++) {
        int fd = -1;
        std::string name;
        std::map<int, AbsInfoCache> absInfo;
        if (!try_open_and_identify(role, fd, name, absInfo)) continue;
        std::string codes = enumerate_codes_json(fd);
        close(fd);
        if (!first) out << ",";
        first = false;
        out << "\"" << (role == ROLE_LEFT ? "left" : "right") << "\":{";
        out << "\"name\":\"" << json_escape(name) << "\",";
        out << "\"codes\":" << codes;
        out << "}";
    }
    out << "}";
    return env->NewStringUTF(out.str().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeStartMerge(JNIEnv *env, jobject, jstring mappingJson) {
    const char *chars = env->GetStringUTFChars(mappingJson, nullptr);
    parse_mapping(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(mappingJson, chars);

    if (g_uinput_fd < 0) {
        if (!create_uinput_device()) return JNI_FALSE;
    }
    if (!g_supervisor_running.exchange(true)) {
        g_supervisor_thread = std::thread(supervisor_loop);
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeUpdateMapping(JNIEnv *env, jobject, jstring mappingJson) {
    const char *chars = env->GetStringUTFChars(mappingJson, nullptr);
    parse_mapping(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(mappingJson, chars);
}

JNIEXPORT void JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeStopMerge(JNIEnv *, jobject) {
    g_supervisor_running = false;
    if (g_supervisor_thread.joinable()) g_supervisor_thread.join();
    release_device(g_left);
    release_device(g_right);
    destroy_uinput_device();
    g_dpad_up = g_dpad_down = g_dpad_left = g_dpad_right = false;
}

JNIEXPORT jboolean JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeIsUinputWritable(JNIEnv *, jobject) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) return JNI_FALSE;
    close(fd);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeIsEvdevReadable(JNIEnv *, jobject) {
    DIR *dir = opendir("/dev/input");
    if (!dir) return JNI_FALSE;
    bool any = false;
    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string fname = entry->d_name;
        if (fname.rfind("event", 0) != 0) continue;
        int fd = open(("/dev/input/" + fname).c_str(), O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            any = true;
            close(fd);
            break;
        }
    }
    closedir(dir);
    return any ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_eastonseidel_joyncon_shizuku_UInputBridgeService_nativeGetStatusJson(JNIEnv *env, jobject) {
    std::ostringstream out;
    out << "{";
    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        out << "\"left\":{\"grabbed\":" << (g_left.grabbed.load() ? "true" : "false")
            << ",\"name\":\"" << json_escape(g_left.name) << "\""
            << ",\"rawCodes\":" << (g_left.rawCodesJson.empty() ? "[]" : g_left.rawCodesJson) << "},";
        out << "\"right\":{\"grabbed\":" << (g_right.grabbed.load() ? "true" : "false")
            << ",\"name\":\"" << json_escape(g_right.name) << "\""
            << ",\"rawCodes\":" << (g_right.rawCodesJson.empty() ? "[]" : g_right.rawCodesJson) << "},";
        out << "\"uinputActive\":" << (g_uinput_active.load() ? "true" : "false") << ",";
        out << "\"detail\":\"" << json_escape(g_last_error) << "\"";
    }
    out << "}";
    return env->NewStringUTF(out.str().c_str());
}

} // extern "C"
