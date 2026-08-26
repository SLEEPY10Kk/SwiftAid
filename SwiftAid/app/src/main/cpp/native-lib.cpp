#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <limits>
#include <mutex>
#include <numeric>
#include <optional>

namespace {

// ─────────────────────────────────────────────────────────────────────────────
//  Logging
// ─────────────────────────────────────────────────────────────────────────────
constexpr const char* kLogTag = "SwiftAidNative";

// ─────────────────────────────────────────────────────────────────────────────
//  Physical / timing constants
// ─────────────────────────────────────────────────────────────────────────────
constexpr float    kEarthGravity            = 9.80665f;
constexpr float    kPi                      = 3.14159265358979323846f;
constexpr float    kMinImpactAngleDeg       = 30.0f;   // Widened for oblique impacts
constexpr float    kMaxImpactAngleDeg       = 150.0f;
constexpr float    kRolloverRad             = kPi;
constexpr int64_t  kOneSecondNs             = 1'000'000'000LL;
constexpr int64_t  kRolloverWindowNs        = 1'500'000'000LL;
constexpr int64_t  kSecondaryWindowNs       = 2'500'000'000LL;  // Extended window
constexpr int64_t  kCooldownNs              = 10'000'000'000LL;
constexpr size_t   kSampleRateHz            = 50;
constexpr size_t   kRingCapacity            = 5 * kSampleRateHz;  // 5 s of history
constexpr float    kGravityLowPassTau       = 0.45f;
constexpr float    kJerkHighPassTau         = 0.02f;  // Jerk (derivative of accel) HPF
constexpr int      kMinSecondaryRules       = 2;

// ─────────────────────────────────────────────────────────────────────────────
//  Utilities
// ─────────────────────────────────────────────────────────────────────────────
struct Vec3 {
    float x = 0.0f, y = 0.0f, z = 0.0f;
    [[nodiscard]] float sqMagnitude() const { return x*x + y*y + z*z; }
    [[nodiscard]] float magnitude()   const { return std::sqrt(sqMagnitude()); }
    [[nodiscard]] Vec3  normalized()  const {
        const float m = magnitude();
        return m > 1e-9f ? Vec3{x/m, y/m, z/m} : Vec3{};
    }
};

Vec3  operator+(const Vec3& a, const Vec3& b) { return {a.x+b.x, a.y+b.y, a.z+b.z}; }
Vec3  operator-(const Vec3& a, const Vec3& b) { return {a.x-b.x, a.y-b.y, a.z-b.z}; }
Vec3  operator*(const Vec3& v, float s)        { return {v.x*s,   v.y*s,   v.z*s  }; }
Vec3  operator/(const Vec3& v, float s)        { return {v.x/s,   v.y/s,   v.z/s  }; }
Vec3& operator+=(Vec3& a, const Vec3& b)       { a.x+=b.x; a.y+=b.y; a.z+=b.z; return a; }

float dot(const Vec3& a, const Vec3& b) { return a.x*b.x + a.y*b.y + a.z*b.z; }

Vec3 vabs(const Vec3& v) { return {std::fabs(v.x), std::fabs(v.y), std::fabs(v.z)}; }
float vmax(const Vec3& v) { return std::max({v.x, v.y, v.z}); }

float toDeg(float rad) { return rad * (180.0f / kPi); }

int64_t nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now().time_since_epoch()).count();
}

// ─────────────────────────────────────────────────────────────────────────────
//  One-pole IIR filters (low-pass / high-pass)
// ─────────────────────────────────────────────────────────────────────────────
struct LowPass1 {
    Vec3  state{};
    bool  initialized = false;

    Vec3 update(const Vec3& x, float tau, float dt) {
        if (!initialized) { state = x; initialized = true; return state; }
        const float alpha = tau / (tau + std::max(dt, 0.001f));
        state = state * alpha + x * (1.0f - alpha);
        return state;
    }
    void reset() { state = {}; initialized = false; }
};

struct HighPass1 {
    Vec3  prev{};
    Vec3  state{};
    bool  initialized = false;

    // y[n] = alpha*(y[n-1] + x[n] - x[n-1])
    Vec3 update(const Vec3& x, float tau, float dt) {
        if (!initialized) { prev = x; initialized = true; return {}; }
        const float alpha = tau / (tau + std::max(dt, 0.001f));
        state = (state + x - prev) * alpha;
        prev = x;
        return state;
    }
    void reset() { prev = {}; state = {}; initialized = false; }
};

// ─────────────────────────────────────────────────────────────────────────────
//  Ring-buffer helper
// ─────────────────────────────────────────────────────────────────────────────
template<typename T, size_t N>
struct RingBuffer {
    std::array<T, N> buf{};
    size_t           writeIdx  = 0;
    size_t           count     = 0;

    void push(const T& v) {
        buf[writeIdx] = v;
        writeIdx = (writeIdx + 1) % N;
        if (count < N) ++count;
    }

    // Iterate from oldest → newest
    template<typename Fn>
    void forEach(Fn fn) const {
        const size_t start = (writeIdx + N - count) % N;
        for (size_t i = 0; i < count; ++i) fn(buf[(start + i) % N]);
    }

    void reset() { buf = {}; writeIdx = 0; count = 0; }
    [[nodiscard]] bool empty() const { return count == 0; }
};

// ─────────────────────────────────────────────────────────────────────────────
//  Data types
// ─────────────────────────────────────────────────────────────────────────────
struct SensorSample {
    int64_t timestampNs      = 0;
    Vec3    accelG{};           // Total accel in g
    Vec3    gyroRadPerSec{};
    Vec3    gravityG{};         // Estimated gravity component
    Vec3    linearG{};          // accelG − gravity (linear acceleration)
    Vec3    jerk{};             // High-pass of linearG (proxy for jerk)
    float   linearMagG        = 0.0f;
    float   jerkMag           = 0.0f;
    float   gyroPeakRadPerSec = 0.0f;  // max-axis magnitude
    float   audioDb           = 0.0f;
};

struct ThresholdConfig {
    int   version            = 1;
    float impactThresholdG   = 10.0f;
    float audioCrashDb       = 100.0f;
    float stillnessStdDevG   = 0.5f;
    float jerkThreshold      = 30.0f;  // g/s – additional pre-screening
    float gyroPeakRadPerSec  = 2.0f;   // secondary: rotation at impact
};

// ─────────────────────────────────────────────────────────────────────────────
//  Welford online mean/variance for Vec3
// ─────────────────────────────────────────────────────────────────────────────
struct WelfordVec3 {
    int  n  = 0;
    Vec3 mean{};
    Vec3 M2{};

    void update(const Vec3& x) {
        ++n;
        const Vec3 delta      = x - mean;
        mean                 += delta / static_cast<float>(n);
        const Vec3 delta2     = x - mean;
        M2.x += delta.x * delta2.x;
        M2.y += delta.y * delta2.y;
        M2.z += delta.z * delta2.z;
    }

    [[nodiscard]] float totalStdDev() const {
        if (n < 2) return std::numeric_limits<float>::infinity();
        const float inv = 1.0f / static_cast<float>(n - 1);
        return std::sqrt((M2.x + M2.y + M2.z) * inv);
    }
};

// ─────────────────────────────────────────────────────────────────────────────
//  Exponential moving average for audio dB (fast attack, slow decay)
// ─────────────────────────────────────────────────────────────────────────────
struct AudioEnvelope {
    float value          = 0.0f;
    float attackAlpha    = 0.8f;   // fast attack
    float releaseAlpha   = 0.05f;  // slow release

    float update(float sample) {
        const float alpha = (sample > value) ? attackAlpha : releaseAlpha;
        value = value * (1.0f - alpha) + sample * alpha;
        return value;
    }
    void reset() { value = 0.0f; }
};

// ─────────────────────────────────────────────────────────────────────────────
//  Crash confidence score (0–1)
// ─────────────────────────────────────────────────────────────────────────────
struct CrashConfidence {
    float impactScore    = 0.0f;
    float acousticScore  = 0.0f;
    float rolloverScore  = 0.0f;
    float stillnessScore = 0.0f;
    float jerkScore      = 0.0f;
    float gyroScore      = 0.0f;

    // Weighted geometric mean; returns [0, 1]
    [[nodiscard]] float total() const {
        // Primary gate: impact must be meaningful
        if (impactScore < 0.1f) return 0.0f;
        const float secondary =
            0.30f * acousticScore  +
            0.25f * rolloverScore  +
            0.20f * stillnessScore +
            0.15f * jerkScore      +
            0.10f * gyroScore;
        return impactScore * secondary;
    }

    [[nodiscard]] int discreteRuleCount() const {
        int c = 0;
        if (acousticScore  > 0.5f) ++c;
        if (rolloverScore  > 0.5f) ++c;
        if (stillnessScore > 0.5f) ++c;
        return c;
    }
};

// ─────────────────────────────────────────────────────────────────────────────
//  CrashEngine
// ─────────────────────────────────────────────────────────────────────────────
class CrashEngine {
public:
    // Returns true exactly once per confirmed crash event.
    bool feed(const Vec3& accelMpss,
              const Vec3& gyroRadPerSec,
              float       audioDb) {
        std::lock_guard<std::mutex> lock(mutex_);

        const int64_t tsNs      = nowNs();
        const float   dt        = deltaSeconds(tsNs);
        const Vec3    accelG    = accelMpss / kEarthGravity;

        // ── Gravity estimation (low-pass) ────────────────────────────────────
        const Vec3 gravity  = gravityLp_.update(accelG, kGravityLowPassTau, dt);
        const Vec3 linearG  = accelG - gravity;
        const Vec3 jerk     = jerkHp_.update(linearG, kJerkHighPassTau, dt);

        // ── Audio envelope ───────────────────────────────────────────────────
        const float audioEnv = audioEnv_.update(audioDb);

        // ── Build sample ─────────────────────────────────────────────────────
        SensorSample s{};
        s.timestampNs      = tsNs;
        s.accelG           = accelG;
        s.gyroRadPerSec    = gyroRadPerSec;
        s.gravityG         = gravity;
        s.linearG          = linearG;
        s.jerk             = jerk;
        s.linearMagG       = linearG.magnitude();
        s.jerkMag          = jerk.magnitude();
        s.gyroPeakRadPerSec= vabs(gyroRadPerSec).magnitude();  // L2 across axes
        s.audioDb          = audioEnv;
        ring_.push(s);

        // ── State machine ────────────────────────────────────────────────────
        if (state_ == State::Monitoring &&
            tsNs >= cooldownUntilNs_   &&
            isPrimaryImpact(s)) {
            beginCandidate(s);
        }

        bool confirmed = false;
        if (state_ == State::Candidate) {
            evaluateCandidate(s, dt);
            const CrashConfidence& conf = confidence_;

            if (conf.discreteRuleCount() >= kMinSecondaryRules) {
                triggerCrash(tsNs, conf);
                confirmed = true;
            } else if (tsNs - primaryTimestampNs_ > kSecondaryWindowNs) {
                __android_log_print(ANDROID_LOG_DEBUG, kLogTag,
                    "Candidate expired (score=%.3f)", conf.total());
                resetCandidate();
            }
        } else if (state_ == State::Cooldown && tsNs >= cooldownUntilNs_) {
            resetCandidate();
        }

        return confirmed;
    }

    void reset() {
        std::lock_guard<std::mutex> lock(mutex_);
        hardReset();
    }

    void updateThresholds(int   version,
                          float impactThresholdG,
                          float audioCrashDb,
                          float stillnessStdDevG) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!std::isfinite(impactThresholdG) ||
            !std::isfinite(audioCrashDb)     ||
            !std::isfinite(stillnessStdDevG)) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag,
                                "Ignoring invalid threshold config");
            return;
        }
        thresholds_.version          = std::max(version, 1);
        thresholds_.impactThresholdG = std::clamp(impactThresholdG,  2.0f,  50.0f);
        thresholds_.audioCrashDb     = std::clamp(audioCrashDb,      50.0f, 160.0f);
        thresholds_.stillnessStdDevG = std::clamp(stillnessStdDevG,  0.05f, 5.0f);
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
            "Thresholds v%d — impact=%.2fg audio=%.1fdB stillness=%.2fg",
            thresholds_.version,
            thresholds_.impactThresholdG,
            thresholds_.audioCrashDb,
            thresholds_.stillnessStdDevG);
    }

private:
    // ── State machine ─────────────────────────────────────────────────────────
    enum class State { Monitoring, Candidate, Cooldown };

    // ── Timing ───────────────────────────────────────────────────────────────
    float deltaSeconds(int64_t tsNs) {
        if (lastTimestampNs_ == 0) {
            lastTimestampNs_ = tsNs;
            return 1.0f / static_cast<float>(kSampleRateHz);
        }
        const float dt = static_cast<float>(tsNs - lastTimestampNs_) * 1e-9f;
        lastTimestampNs_ = tsNs;
        return std::clamp(dt, 0.001f, 0.1f);
    }

    // ── Primary-impact gate ───────────────────────────────────────────────────
    bool isPrimaryImpact(const SensorSample& s) const {
        if (s.linearMagG <= thresholds_.impactThresholdG) return false;

        const float gMag = s.gravityG.magnitude();
        if (gMag < 0.25f) return false;

        // Angle between gravity vector and linear-acceleration vector
        const float cosine = std::clamp(
            dot(s.gravityG, s.linearG) / (gMag * std::max(s.linearMagG, 1e-6f)),
            -1.0f, 1.0f);
        const float angleDeg = toDeg(std::acos(cosine));
        if (angleDeg < kMinImpactAngleDeg || angleDeg > kMaxImpactAngleDeg) return false;

        // Optional jerk pre-screening – very gentle threshold
        if (s.jerkMag < 3.0f) return false;

        __android_log_print(ANDROID_LOG_INFO, kLogTag,
            "Primary impact: linear=%.2fg angle=%.1f° jerk=%.1f audio=%.1fdB",
            s.linearMagG, angleDeg, s.jerkMag, s.audioDb);
        return true;
    }

    // ── Begin candidate ───────────────────────────────────────────────────────
    void beginCandidate(const SensorSample& s) {
        state_                = State::Candidate;
        primaryTimestampNs_   = s.timestampNs;
        integratedRotation_   = {};
        stillnessAccumulator_ = {};

        // Immediate acoustic check on buffered envelope
        const float audioRatio =
            (thresholds_.audioCrashDb > 0.0f)
            ? (s.audioDb / thresholds_.audioCrashDb)
            : 0.0f;
        confidence_.acousticScore = sigmoidScore(audioRatio, 1.0f, 4.0f);

        // Immediate jerk score
        confidence_.jerkScore = sigmoidScore(
            s.jerkMag / std::max(thresholds_.jerkThreshold, 1.0f), 1.0f, 4.0f);

        // Impact score
        confidence_.impactScore = sigmoidScore(
            s.linearMagG / std::max(thresholds_.impactThresholdG, 1.0f), 1.0f, 6.0f);

        // Gyro at impact
        confidence_.gyroScore = sigmoidScore(
            s.gyroPeakRadPerSec / std::max(thresholds_.gyroPeakRadPerSec, 0.1f), 1.0f, 3.0f);

        confidence_.rolloverScore  = 0.0f;
        confidence_.stillnessScore = 0.0f;
    }

    // ── Evaluate candidate on every subsequent sample ─────────────────────────
    void evaluateCandidate(const SensorSample& s, float dt) {
        const int64_t elapsed = s.timestampNs - primaryTimestampNs_;

        // Rollover: integrate absolute rotation in the first 1.5 s
        if (elapsed > 0 && elapsed <= kRolloverWindowNs) {
            integratedRotation_ += vabs(s.gyroRadPerSec) * dt;
            const float maxRot = vmax(integratedRotation_);
            confidence_.rolloverScore = sigmoidScore(maxRot / kRolloverRad, 1.0f, 6.0f);
        }

        // Continuous audio tracking (take the max)
        if (s.audioDb > 0.0f) {
            const float newAcoustic = sigmoidScore(
                s.audioDb / std::max(thresholds_.audioCrashDb, 1.0f), 1.0f, 4.0f);
            if (newAcoustic > confidence_.acousticScore)
                confidence_.acousticScore = newAcoustic;
        }

        // Stillness: accumulate samples from 1 s post-impact onwards
        if (elapsed >= kOneSecondNs) {
            stillnessAccumulator_.update(s.accelG);
            if (stillnessAccumulator_.n >= static_cast<int>(kSampleRateHz / 2)) {
                const float stdDev = stillnessAccumulator_.totalStdDev();
                confidence_.stillnessScore = sigmoidScore(
                    thresholds_.stillnessStdDevG / std::max(stdDev, 1e-6f), 1.0f, 5.0f);
            }
        }
    }

    // ── Trigger confirmed crash ───────────────────────────────────────────────
    void triggerCrash(int64_t tsNs, const CrashConfidence& c) {
        state_           = State::Cooldown;
        cooldownUntilNs_ = tsNs + kCooldownNs;
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
            "CRASH CONFIRMED — score=%.3f "
            "impact=%.2f acoustic=%.2f rollover=%.2f stillness=%.2f jerk=%.2f gyro=%.2f",
            c.total(),
            c.impactScore, c.acousticScore, c.rolloverScore,
            c.stillnessScore, c.jerkScore, c.gyroScore);
    }

    // ── Reset candidate state ─────────────────────────────────────────────────
    void resetCandidate() {
        state_              = State::Monitoring;
        primaryTimestampNs_ = 0;
        integratedRotation_ = {};
        stillnessAccumulator_ = {};
        confidence_         = {};
    }

    // ── Full hard reset ───────────────────────────────────────────────────────
    void hardReset() {
        resetCandidate();
        cooldownUntilNs_ = 0;
        lastTimestampNs_ = 0;
        ring_.reset();
        gravityLp_.reset();
        jerkHp_.reset();
        audioEnv_.reset();
    }

    // ── Sigmoid score helper: returns ~0 below threshold, ~1 above ───────────
    // x=ratio (measured/threshold), centre=1.0, steepness k
    static float sigmoidScore(float x, float centre, float k) {
        return 1.0f / (1.0f + std::exp(-k * (x - centre)));
    }

    // ── Members ───────────────────────────────────────────────────────────────
    mutable std::mutex mutex_;

    // Sensor processing
    LowPass1    gravityLp_;
    HighPass1   jerkHp_;
    AudioEnvelope audioEnv_;
    int64_t     lastTimestampNs_ = 0;

    // Ring buffer
    RingBuffer<SensorSample, kRingCapacity> ring_;

    // Configuration
    ThresholdConfig thresholds_{};

    // State machine
    State   state_              = State::Monitoring;
    int64_t primaryTimestampNs_ = 0;
    int64_t cooldownUntilNs_    = 0;

    // Per-candidate accumulators
    Vec3           integratedRotation_{};
    WelfordVec3    stillnessAccumulator_{};
    CrashConfidence confidence_{};
};

// ─────────────────────────────────────────────────────────────────────────────
//  Globals & JNI plumbing  (unchanged API surface)
// ─────────────────────────────────────────────────────────────────────────────
CrashEngine gEngine;
JavaVM*      gJavaVm          = nullptr;
jobject      gCallback        = nullptr;
jmethodID    gOnCrashConfirmed = nullptr;
std::mutex   gCallbackMutex;

void invokeCrashCallback() {
    std::lock_guard<std::mutex> lock(gCallbackMutex);
    if (!gJavaVm || !gCallback || !gOnCrashConfirmed) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Callback not initialised");
        return;
    }

    JNIEnv* env = nullptr;
    bool detach = false;
    const jint rc = gJavaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if (gJavaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "AttachCurrentThread failed");
            return;
        }
        detach = true;
    } else if (rc != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "GetEnv failed");
        return;
    }

    env->CallVoidMethod(gCallback, gOnCrashConfirmed);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (detach) gJavaVm->DetachCurrentThread();
}

Vec3 readVec3(JNIEnv* env, jfloatArray arr, const char* name) {
    if (!arr || env->GetArrayLength(arr) < 3) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                            "%s must have ≥3 floats", name);
        return {};
    }
    float buf[3]{};
    env->GetFloatArrayRegion(arr, 0, 3, buf);
    return {buf[0], buf[1], buf[2]};
}

} // namespace

// ─────────────────────────────────────────────────────────────────────────────
//  JNI entry-points  (all original names preserved)
// ─────────────────────────────────────────────────────────────────────────────
extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    gJavaVm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_nativeInit(
        JNIEnv* env, jobject, jobject callback) {
    std::lock_guard<std::mutex> lock(gCallbackMutex);
    if (gCallback) { env->DeleteGlobalRef(gCallback); gCallback = nullptr; }
    gCallback         = env->NewGlobalRef(callback);
    jclass cls        = env->GetObjectClass(callback);
    gOnCrashConfirmed = env->GetMethodID(cls, "onCrashConfirmed", "()V");
    env->DeleteLocalRef(cls);
    gEngine.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_feedSensorData(
        JNIEnv* env, jobject,
        jfloatArray accel,
        jfloatArray gyro,
        jfloat      audioDb) {
    const Vec3 a = readVec3(env, accel, "accel");
    const Vec3 g = readVec3(env, gyro,  "gyro");
    if (gEngine.feed(a, g, static_cast<float>(audioDb)))
        invokeCrashCallback();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_resetEngine(JNIEnv*, jobject) {
    gEngine.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_updateThresholds(
        JNIEnv*, jobject,
        jint   configVersion,
        jfloat impactThresholdG,
        jfloat audioCrashDb,
        jfloat stillnessStdDevG) {
    gEngine.updateThresholds(
        static_cast<int>(configVersion),
        impactThresholdG,
        audioCrashDb,
        stillnessStdDevG);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_nativeShutdown(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gCallbackMutex);
    if (gCallback) { env->DeleteGlobalRef(gCallback); gCallback = nullptr; }
    gOnCrashConfirmed = nullptr;
}
