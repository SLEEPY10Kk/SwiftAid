#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <limits>
#include <mutex>

namespace {

constexpr const char* kLogTag = "SwiftAidNative";
constexpr float kEarthGravity = 9.80665f;
constexpr float kMinImpactAngleDegrees = 45.0f;
constexpr float kMaxImpactAngleDegrees = 135.0f;
constexpr float kPi = 3.14159265358979323846f;
constexpr float kRolloverRadians = kPi;
constexpr int64_t kOneSecondNs = 1'000'000'000LL;
constexpr int64_t kRolloverWindowNs = 1'500'000'000LL;
constexpr int64_t kSecondaryWindowNs = 2'000'000'000LL;
constexpr int64_t kCooldownNs = 10'000'000'000LL;
constexpr size_t kSampleRateHz = 50;
constexpr size_t kRingCapacity = 3 * kSampleRateHz;
constexpr float kGravityLowPassTauSeconds = 0.45f;

struct Vec3 {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
};

Vec3 operator+(const Vec3& lhs, const Vec3& rhs) {
    return {lhs.x + rhs.x, lhs.y + rhs.y, lhs.z + rhs.z};
}

Vec3 operator-(const Vec3& lhs, const Vec3& rhs) {
    return {lhs.x - rhs.x, lhs.y - rhs.y, lhs.z - rhs.z};
}

Vec3 operator*(const Vec3& value, float scalar) {
    return {value.x * scalar, value.y * scalar, value.z * scalar};
}

Vec3 operator/(const Vec3& value, float scalar) {
    return {value.x / scalar, value.y / scalar, value.z / scalar};
}

float dot(const Vec3& lhs, const Vec3& rhs) {
    return lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z;
}

float magnitude(const Vec3& value) {
    return std::sqrt(dot(value, value));
}

float radiansToDegrees(float radians) {
    return radians * 180.0f / kPi;
}

int64_t nowNs() {
    const auto now = std::chrono::steady_clock::now().time_since_epoch();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(now).count();
}

struct SensorSample {
    int64_t timestampNs = 0;
    Vec3 accelG{};
    Vec3 gyroRadPerSec{};
    Vec3 gravityG{};
    Vec3 impactG{};
    float impactMagnitudeG = 0.0f;
    float audioDb = 0.0f;
};

struct ThresholdConfig {
    int version = 1;
    float impactThresholdG = 10.0f;
    float audioCrashDb = 100.0f;
    float stillnessStdDevG = 0.5f;
};

class CrashEngine {
public:
    bool feed(const Vec3& accelMetersPerSecSquared,
              const Vec3& gyroRadPerSec,
              float audioDb) {
        std::lock_guard<std::mutex> lock(mutex_);

        const int64_t timestampNs = nowNs();
        const float deltaSeconds = deltaSecondsSinceLastSample(timestampNs);
        const Vec3 accelG = accelMetersPerSecSquared / kEarthGravity;
        updateGravity(accelG, deltaSeconds);

        SensorSample sample{};
        sample.timestampNs = timestampNs;
        sample.accelG = accelG;
        sample.gyroRadPerSec = gyroRadPerSec;
        sample.gravityG = gravityG_;
        sample.impactG = accelG - gravityG_;
        sample.impactMagnitudeG = magnitude(sample.impactG);
        sample.audioDb = audioDb;
        push(sample);

        if (state_ == State::Monitoring &&
            timestampNs >= cooldownUntilNs_ &&
            isPrimaryImpact(sample)) {
            beginCandidate(sample);
        }

        if (state_ == State::Candidate) {
            evaluateCandidate(sample, deltaSeconds);
            if (secondaryRuleCount() >= 2) {
                state_ = State::Cooldown;
                cooldownUntilNs_ = timestampNs + kCooldownNs;
                __android_log_print(ANDROID_LOG_WARN, kLogTag,
                                    "Crash confirmed: acoustic=%d rollover=%d stillness=%d",
                                    acousticConfirmed_, rolloverConfirmed_, stillnessConfirmed_);
                return true;
            }

            if (timestampNs - primaryTimestampNs_ > kSecondaryWindowNs) {
                resetCandidate();
            }
        } else if (state_ == State::Cooldown && timestampNs >= cooldownUntilNs_) {
            resetCandidate();
        }

        return false;
    }

    void reset() {
        std::lock_guard<std::mutex> lock(mutex_);
        state_ = State::Monitoring;
        primaryTimestampNs_ = 0;
        cooldownUntilNs_ = 0;
        acousticConfirmed_ = false;
        rolloverConfirmed_ = false;
        stillnessConfirmed_ = false;
        integratedRotationRad_ = {};
    }

    void updateThresholds(int version,
                          float impactThresholdG,
                          float audioCrashDb,
                          float stillnessStdDevG) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!std::isfinite(impactThresholdG) ||
            !std::isfinite(audioCrashDb) ||
            !std::isfinite(stillnessStdDevG)) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag, "Ignoring invalid threshold config");
            return;
        }

        thresholds_.version = std::max(version, 1);
        thresholds_.impactThresholdG = std::clamp(impactThresholdG, 2.0f, 50.0f);
        thresholds_.audioCrashDb = std::clamp(audioCrashDb, 50.0f, 160.0f);
        thresholds_.stillnessStdDevG = std::clamp(stillnessStdDevG, 0.05f, 5.0f);
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "Applied threshold config v%d: impact=%.2fG audio=%.1fdB stillness=%.2fG",
                            thresholds_.version,
                            thresholds_.impactThresholdG,
                            thresholds_.audioCrashDb,
                            thresholds_.stillnessStdDevG);
    }

private:
    enum class State {
        Monitoring,
        Candidate,
        Cooldown,
    };

    float deltaSecondsSinceLastSample(int64_t timestampNs) {
        if (lastTimestampNs_ == 0) {
            lastTimestampNs_ = timestampNs;
            return 1.0f / static_cast<float>(kSampleRateHz);
        }

        const int64_t deltaNs = timestampNs - lastTimestampNs_;
        lastTimestampNs_ = timestampNs;
        const float deltaSeconds = static_cast<float>(deltaNs) / 1'000'000'000.0f;
        return std::clamp(deltaSeconds, 0.001f, 0.1f);
    }

    void updateGravity(const Vec3& accelG, float deltaSeconds) {
        if (!gravityInitialized_) {
            gravityG_ = accelG;
            gravityInitialized_ = true;
            return;
        }

        const float alpha = kGravityLowPassTauSeconds /
                            (kGravityLowPassTauSeconds + std::max(deltaSeconds, 0.001f));
        gravityG_ = gravityG_ * alpha + accelG * (1.0f - alpha);
    }

    void push(const SensorSample& sample) {
        ring_[writeIndex_] = sample;
        writeIndex_ = (writeIndex_ + 1) % kRingCapacity;
        sampleCount_ = std::min(sampleCount_ + 1, kRingCapacity);
    }

    bool isPrimaryImpact(const SensorSample& sample) const {
        if (sample.impactMagnitudeG <= thresholds_.impactThresholdG) {
            return false;
        }

        const float gravityMagnitude = magnitude(sample.gravityG);
        if (gravityMagnitude < 0.25f || sample.impactMagnitudeG < 0.001f) {
            return false;
        }

        const float cosine = std::clamp(
                dot(sample.gravityG, sample.impactG) /
                (gravityMagnitude * sample.impactMagnitudeG),
                -1.0f,
                1.0f);
        const float angleDegrees = radiansToDegrees(std::acos(cosine));
        const bool perpendicularToGravity =
                angleDegrees >= kMinImpactAngleDegrees &&
                angleDegrees <= kMaxImpactAngleDegrees;

        if (perpendicularToGravity) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag,
                                "Primary impact: %.2fG at %.1f degrees, audio %.1fdB",
                                sample.impactMagnitudeG, angleDegrees, sample.audioDb);
        }

        return perpendicularToGravity;
    }

    void beginCandidate(const SensorSample& sample) {
        state_ = State::Candidate;
        primaryTimestampNs_ = sample.timestampNs;
        acousticConfirmed_ = sample.audioDb > thresholds_.audioCrashDb;
        rolloverConfirmed_ = false;
        stillnessConfirmed_ = false;
        integratedRotationRad_ = {};
    }

    void evaluateCandidate(const SensorSample& sample, float deltaSeconds) {
        const int64_t elapsedSincePrimaryNs = sample.timestampNs - primaryTimestampNs_;
        if (elapsedSincePrimaryNs > 0 && elapsedSincePrimaryNs <= kRolloverWindowNs) {
            integratedRotationRad_.x += std::fabs(sample.gyroRadPerSec.x) * deltaSeconds;
            integratedRotationRad_.y += std::fabs(sample.gyroRadPerSec.y) * deltaSeconds;
            integratedRotationRad_.z += std::fabs(sample.gyroRadPerSec.z) * deltaSeconds;

            rolloverConfirmed_ =
                    integratedRotationRad_.x > kRolloverRadians ||
                    integratedRotationRad_.y > kRolloverRadians ||
                    integratedRotationRad_.z > kRolloverRadians;
        }

        if (!stillnessConfirmed_ && elapsedSincePrimaryNs >= kOneSecondNs) {
            stillnessConfirmed_ =
                    accelerometerStdDevSincePrimary(sample.timestampNs) < thresholds_.stillnessStdDevG;
        }
    }

    int secondaryRuleCount() const {
        int count = 0;
        if (acousticConfirmed_) ++count;
        if (rolloverConfirmed_) ++count;
        if (stillnessConfirmed_) ++count;
        return count;
    }

    float accelerometerStdDevSincePrimary(int64_t nowTimestampNs) const {
        const int64_t startNs = std::max(primaryTimestampNs_, nowTimestampNs - kOneSecondNs);
        int count = 0;
        Vec3 mean{};
        Vec3 m2{};

        for (size_t i = 0; i < sampleCount_; ++i) {
            const SensorSample& sample = ring_[i];
            if (sample.timestampNs < startNs || sample.timestampNs > nowTimestampNs) {
                continue;
            }

            ++count;
            const Vec3 delta = sample.accelG - mean;
            mean = mean + delta / static_cast<float>(count);
            const Vec3 deltaAfterMean = sample.accelG - mean;
            m2.x += delta.x * deltaAfterMean.x;
            m2.y += delta.y * deltaAfterMean.y;
            m2.z += delta.z * deltaAfterMean.z;
        }

        if (count < static_cast<int>(kSampleRateHz / 2)) {
            return std::numeric_limits<float>::infinity();
        }

        const float invCount = 1.0f / static_cast<float>(count - 1);
        return std::sqrt((m2.x + m2.y + m2.z) * invCount);
    }

    void resetCandidate() {
        state_ = State::Monitoring;
        primaryTimestampNs_ = 0;
        acousticConfirmed_ = false;
        rolloverConfirmed_ = false;
        stillnessConfirmed_ = false;
        integratedRotationRad_ = {};
    }

    mutable std::mutex mutex_;
    std::array<SensorSample, kRingCapacity> ring_{};
    size_t writeIndex_ = 0;
    size_t sampleCount_ = 0;
    bool gravityInitialized_ = false;
    Vec3 gravityG_{};
    int64_t lastTimestampNs_ = 0;
    ThresholdConfig thresholds_{};

    State state_ = State::Monitoring;
    int64_t primaryTimestampNs_ = 0;
    int64_t cooldownUntilNs_ = 0;
    bool acousticConfirmed_ = false;
    bool rolloverConfirmed_ = false;
    bool stillnessConfirmed_ = false;
    Vec3 integratedRotationRad_{};
};

CrashEngine gEngine;
JavaVM* gJavaVm = nullptr;
jobject gCallback = nullptr;
jmethodID gOnCrashConfirmed = nullptr;
std::mutex gCallbackMutex;

void invokeCrashCallback() {
    std::lock_guard<std::mutex> lock(gCallbackMutex);
    if (gJavaVm == nullptr || gCallback == nullptr || gOnCrashConfirmed == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Crash callback is not initialized");
        return;
    }

    JNIEnv* env = nullptr;
    bool detach = false;
    jint getEnvResult = gJavaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (gJavaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to attach callback thread");
            return;
        }
        detach = true;
    } else if (getEnvResult != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to get JNI environment");
        return;
    }

    env->CallVoidMethod(gCallback, gOnCrashConfirmed);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (detach) {
        gJavaVm->DetachCurrentThread();
    }
}

Vec3 readVec3(JNIEnv* env, jfloatArray values, const char* name) {
    Vec3 result{};
    if (values == nullptr || env->GetArrayLength(values) < 3) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s array must contain at least 3 floats", name);
        return result;
    }

    float buffer[3] = {};
    env->GetFloatArrayRegion(values, 0, 3, buffer);
    result.x = buffer[0];
    result.y = buffer[1];
    result.z = buffer[2];
    return result;
}

} // namespace

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    gJavaVm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_nativeInit(
        JNIEnv* env,
        jobject,
        jobject callback) {
    std::lock_guard<std::mutex> lock(gCallbackMutex);
    if (gCallback != nullptr) {
        env->DeleteGlobalRef(gCallback);
        gCallback = nullptr;
    }

    gCallback = env->NewGlobalRef(callback);
    jclass callbackClass = env->GetObjectClass(callback);
    gOnCrashConfirmed = env->GetMethodID(callbackClass, "onCrashConfirmed", "()V");
    env->DeleteLocalRef(callbackClass);
    gEngine.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_feedSensorData(
        JNIEnv* env,
        jobject,
        jfloatArray accel,
        jfloatArray gyro,
        jfloat audioDb) {
    const Vec3 accelMetersPerSecSquared = readVec3(env, accel, "accel");
    const Vec3 gyroRadPerSec = readVec3(env, gyro, "gyro");
    const bool confirmed = gEngine.feed(accelMetersPerSecSquared, gyroRadPerSec, audioDb);
    if (confirmed) {
        invokeCrashCallback();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_resetEngine(
        JNIEnv*,
        jobject) {
    gEngine.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_swiftaid_NativeCrashBridge_updateThresholds(
        JNIEnv*,
        jobject,
        jint configVersion,
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
Java_com_example_swiftaid_NativeCrashBridge_nativeShutdown(
        JNIEnv* env,
        jobject) {
    std::lock_guard<std::mutex> lock(gCallbackMutex);
    if (gCallback != nullptr) {
        env->DeleteGlobalRef(gCallback);
        gCallback = nullptr;
    }
    gOnCrashConfirmed = nullptr;
}
