#define MINIAUDIO_IMPLEMENTATION
#include <jni.h>

#include "miniaudio.h"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>
#include <android/log.h>
#define LOG_TAG "MyNativeTag"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Modal piano: every note is a bank of damped sine resonators
//   y[t] = 2 g cos(w) y[t-1] - g^2 y[t-2]
// one per partial per string, plus a few short hammer-knock / soundboard modes.
// The summed voices go through a soundboard EQ, a small room reverb and a soft limiter.

static const float SR  = 44100.0f;
static const float dt  = 1.0f / SR;
static const float kPi = 3.14159265f;

static const int MAX_OSC    = 160;   // 3 strings x 48 partials + hammer/soundboard modes
static const int MAX_VOICES = 64;
static const int BLOCK      = 256;

struct note {
    int   key = 0;            // 0..87, 0 = A0
    int   midi = 21;
    int   time = 0;
    int   duration = 0;       // samples until the note has rung out on its own
    int   fadeLen = 1;        // fade before the natural end, so it never ends in a click
    int   oscCount = 0;
    float cos[MAX_OSC];       // 2 g cos(w)
    float gg[MAX_OSC];        // g^2
    float middle[MAX_OSC];    // y[t-1]
    float last[MAX_OSC];      // y[t-2]
    float atk = 1.0f;
    float atkStep = 0.0f;
    float relEnv = 1.0f;
    float relStep = 1.0f;     // damper decay per sample, used once released
    bool  hasDamper = true;   // the top ~18 keys of a real piano have no dampers
    bool  released = false;
    float panL = 0.707f;
    float panR = 0.707f;
    bool  dead = false;
};
static std::vector<note> Notes;     // only touched by the audio thread

// play()/release() come from the UI thread; they only queue commands and the
// audio thread picks them up, so Notes is never resized while it is being rendered.
struct Cmd { int key; int level; bool release; };
static std::mutex       cmdLock;
static std::vector<Cmd> cmdQueue;   // UI thread writes
static std::vector<Cmd> cmdLocal;   // audio thread's copy

static uint32_t rngState = 0x9E3779B9u;
static float rnd() {                 // -1..1, audio thread only
    rngState ^= rngState << 13;
    rngState ^= rngState >> 17;
    rngState ^= rngState << 5;
    return (rngState >> 8) * (2.0f / 16777216.0f) - 1.0f;
}

static void addOsc(note& nt, float freq, float decaySec, float amp, float phase) {
    if (nt.oscCount >= MAX_OSC) return;
    float w = 2.0f * kPi * freq * dt;
    float g = expf(-dt / decaySec);
    int   k = nt.oscCount++;
    nt.cos[k]    = 2.0f * g * cosf(w);
    nt.gg[k]     = g * g;
    nt.middle[k] = amp * sinf(phase);
    nt.last[k]   = amp * sinf(phase - w) / g;
}

static void startNote(int key, int level) {
    // re-striking a key hits the same strings: let the old sound die away quickly
    for (note& o : Notes) {
        if (o.key == key && !o.dead) {
            o.released = true;
            o.relStep  = fminf(o.relStep, expf(-dt / 0.08f));
        }
    }
    // voice limit: fade the oldest voices instead of cutting them
    if (Notes.size() >= (size_t)MAX_VOICES - 8) {
        size_t oldest = 0;
        for (size_t v = 1; v < Notes.size(); v++)
            if (Notes[v].time > Notes[oldest].time) oldest = v;
        Notes[oldest].released = true;
        Notes[oldest].relStep  = fminf(Notes[oldest].relStep, expf(-dt / 0.02f));
    }
    if (Notes.size() >= (size_t)MAX_VOICES) Notes.erase(Notes.begin());

    Notes.emplace_back();
    note& nt = Notes.back();
    nt.key  = key;
    int midi = key + 21;                 // key 0 = A0 (see drawPiano / note - 21 in MainActivity.kt)
    nt.midi = midi;
    float u = (midi - 21) / 87.0f;       // 0 at A0, 1 at C8

    // velocity: the app sends 1 for now ("not supported"), treat that as mezzo-forte
    float v = (level <= 1) ? 0.63f : fminf(level / 127.0f, 1.0f);
    v = fminf(fmaxf(v * (1.0f + 0.03f * rnd()), 0.05f), 1.0f);

    // stable per-key "imperfections" of the instrument
    float h1 = ((midi * 37) % 23) / 23.0f - 0.5f;   // -0.5..0.5
    float h2 = ((midi * 61) % 19) / 19.0f - 0.5f;
    float h3 = ((midi * 29) % 31) / 31.0f - 0.5f;

    // stretch tuning (simplified Railsback curve): octaves are tuned slightly wide
    float d = midi - 69.0f;
    float stretchCents = d > 0.0f ? 0.012f * d * d : -0.008f * d * d;
    float f0 = 440.0f * powf(2.0f, (d + stretchCents / 100.0f) / 12.0f);

    // inharmonicity: low in the tenor, rising toward the treble and in the wound bass strings
    float B = (1.2e-4f * powf(2.0f, (midi - 48) / 9.5f)
             + 4.0e-4f * powf(2.0f, -(midi - 21) / 6.0f)) * (1.0f + 0.25f * h2);

    float tau  = (0.12f + 9.0f * powf(2.0f, -(midi - 21) / 15.0f)) * (1.0f + 0.15f * h1);
    float p    = 0.12f * (1.0f + 0.12f * h3);      // hammer strike position (fraction of string)
    float tilt = 1.15f + 0.25f * h1;
    // harder hits = shorter hammer contact = brighter; treble hammers are harder too
    float fc = (350.0f + 5000.0f * v * v) * powf(2.0f, (midi - 60) / 24.0f) * (1.0f + 0.03f * rnd());

    // strings per note: 1 in the low bass, 2 in the upper bass, 3 from the tenor up.
    // a single string still gets two groups (its two polarisations) for the two-stage decay.
    float slow   = 0.7f + 4.0f * powf((midi - 21) / 66.0f, 2.0f);  // 0.7 in bass, faster up top
    float spread = 0.9f * powf(2.0f, -(midi - 60) / 36.0f) * (1.0f + 0.3f * h2);  // unison detune, cents
    int   groups;
    float det[3], gAmp[3], gRate[3];
    if (midi < 31) {
        groups = 2;
        det[0] = -0.1f * spread; det[1] = 0.1f * spread;
        gAmp[0] = 0.65f; gAmp[1] = 0.35f;
        gRate[0] = 3.0f; gRate[1] = slow;
    } else if (midi < 50) {
        groups = 2;
        det[0] = -0.5f * spread; det[1] = 0.5f * spread;
        gAmp[0] = 0.65f; gAmp[1] = 0.35f;
        gRate[0] = 3.0f; gRate[1] = slow;
    } else {
        groups = 3;
        det[0] = -spread; det[1] = 0.25f * spread; det[2] = 0.85f * spread;
        gAmp[0] = 0.5f; gAmp[1] = 0.3f; gAmp[2] = 0.2f;
        gRate[0] = 3.0f; gRate[1] = slow; gRate[2] = slow * 1.2f;
    }

    int   nMax = (int)(48.0f - 42.0f * u);            // ~48 partials in the bass, ~6 at the top
    float nyq  = 0.45f * SR;
    float sumSq = 0.0f;
    for (int s = 0; s < groups; s++) {
        float fs = f0 * powf(2.0f, det[s] / 1200.0f);
        for (int n = 1; n <= nMax; n++) {
            float fn = n * fs * sqrtf(1.0f + B * n * n);
            if (fn > nyq) break;
            float notch = 0.35f + 0.65f * fabsf(sinf(kPi * n * p));
            float r2    = (fn / fc) * (fn / fc);
            float lp    = 1.0f / ((1.0f + r2) * sqrtf(1.0f + r2));   // ~f^-3 above fc
            float amp   = powf(1.0f / n, tilt) * notch * lp * gAmp[s];
            if (amp < 2e-4f) continue;
            float decay = tau / (gRate[s] * powf((float)n, 0.9f));
            // struck string: partials start (almost) in phase -> sharp hammer attack
            addOsc(nt, fn, decay, amp, 0.3f * rnd());
            sumSq += amp * amp;
        }
    }

    // loudness: velocity curve, bass a little louder like a real instrument
    float target = 0.16f * powf(v, 1.7f) * (1.0f + 0.25f * (1.0f - u));
    float norm   = target / sqrtf(sumSq + 1e-12f);
    for (int k = 0; k < nt.oscCount; k++) {
        nt.middle[k] *= norm;
        nt.last[k]   *= norm;
    }

    // hammer knock: a burst of short, inharmonic modes, stronger on hard hits and in the treble
    float hard = v * v / 0.4f;
    float jit  = 1.0f + 0.06f * ((midi * 37 % 17) / 17.0f - 0.5f);
    float kAmp = target * (0.1f + 0.35f * u) * hard;
    for (int k = 0; k < 12; k++) {
        float hk = ((midi * (7 + k * 13)) % 41) / 41.0f;
        float f  = (600.0f + 5200.0f * hk) * jit;
        if (f > nyq) continue;
        addOsc(nt, f, 0.004f + 0.008f * hk, kAmp * 0.35f / (1.0f + 3.0f * hk), 0.0f);
    }
    // soundboard thump: the low body resonance kicked by the hammer
    addOsc(nt, 95.0f + 30.0f * (h1 + 0.5f), 0.05f, target * 0.12f * (1.0f - 0.5f * u) * hard, 0.0f);

    nt.atk     = 1.0f;
    nt.atkStep = expf(-dt / 0.0007f);

    // ring until the slowest string has dropped ~70 dB (capped)
    float ringSec = 8.06f * tau / gRate[1];
    ringSec       = fminf(fmaxf(ringSec, 0.5f), 20.0f);
    nt.duration   = (int)(ringSec * SR);
    nt.fadeLen    = (int)(fminf(0.3f, ringSec * 0.25f) * SR);

    nt.hasDamper = midi < 90;
    nt.relStep   = expf(-dt / (0.06f + 0.19f * (1.0f - u)));  // bass dampers are slower

    // stereo: bass on the left, treble on the right, as heard from the bench
    float pan = fminf(fmaxf(0.5f + 0.35f * (midi - 64) / 44.0f, 0.0f), 1.0f);
    nt.panL = cosf(pan * 0.5f * kPi);
    nt.panR = sinf(pan * 0.5f * kPi);
}

static void releaseKey(int key) {
    for (note& nt : Notes)
        if (nt.key == key && nt.hasDamper) nt.released = true;
}

// ---- bus: soundboard EQ, room reverb, limiter ----

struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;
    float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void peak(float f, float q, float dB) {
        float A = powf(10.0f, dB / 40.0f), w = 2.0f * kPi * f / SR;
        float al = sinf(w) / (2.0f * q), c = cosf(w), a0 = 1.0f + al / A;
        b0 = (1.0f + al * A) / a0; b1 = -2.0f * c / a0; b2 = (1.0f - al * A) / a0;
        a1 = -2.0f * c / a0;       a2 = (1.0f - al / A) / a0;
    }
    void highShelf(float f, float dB) {
        float A = powf(10.0f, dB / 40.0f), w = 2.0f * kPi * f / SR;
        float c = cosf(w), al = sinf(w) / 2.0f * sqrtf(2.0f), sA = 2.0f * sqrtf(A) * al;
        float a0 = (A + 1) - (A - 1) * c + sA;
        b0 = A * ((A + 1) + (A - 1) * c + sA) / a0;
        b1 = -2.0f * A * ((A - 1) + (A + 1) * c) / a0;
        b2 = A * ((A + 1) + (A - 1) * c - sA) / a0;
        a1 = 2.0f * ((A - 1) - (A + 1) * c) / a0;
        a2 = ((A + 1) - (A - 1) * c - sA) / a0;
    }
};

struct Comb {      // Freeverb lowpass-feedback comb
    float buf[1600] = {};
    int   len = 1, idx = 0;
    float store = 0;
    float process(float in, float fb, float damp) {
        float out = buf[idx];
        store = out * (1.0f - damp) + store * damp;
        buf[idx] = in + store * fb;
        if (++idx >= len) idx = 0;
        return out;
    }
};

struct Allpass {
    float buf[600] = {};
    int   len = 1, idx = 0;
    float process(float in) {
        float b = buf[idx];
        buf[idx] = in + b * 0.5f;
        if (++idx >= len) idx = 0;
        return b - in;
    }
};

static const int   EQ_BANDS = 4;
static Biquad      eqL[EQ_BANDS], eqR[EQ_BANDS];
static Comb        combL[4], combR[4];
static Allpass     apL[2], apR[2];
static const float REV_IN   = 0.03f;
static const float REV_FB   = 0.82f;
static const float REV_DAMP = 0.35f;
static const float REV_WET  = 0.12f;
static const float MASTER   = 1.0f;

static float busL[BLOCK], busR[BLOCK];

static void initBus() {
    for (int ch = 0; ch < 2; ch++) {
        Biquad* eq = ch ? eqR : eqL;
        eq[0].peak(110.0f, 1.0f, 3.0f);     // soundboard body
        eq[1].peak(450.0f, 1.2f, 2.0f);
        eq[2].peak(2500.0f, 1.0f, -2.0f);
        eq[3].highShelf(7000.0f, -4.0f);
    }
    const int combLen[4] = { 1116, 1277, 1422, 1557 };
    const int apLen[2]   = { 556, 341 };
    for (int k = 0; k < 4; k++) { combL[k].len = combLen[k]; combR[k].len = combLen[k] + 23; }
    for (int k = 0; k < 2; k++) { apL[k].len = apLen[k];     apR[k].len = apLen[k] + 23; }
}

static struct Init {
    Init() {
        Notes.reserve(MAX_VOICES + 1);
        cmdQueue.reserve(256);
        cmdLocal.reserve(256);
        initBus();
    }
} initOnce;

static float softClip(float x) {     // linear up to 0.7, then smoothly saturates at 1.0
    float a = fabsf(x);
    if (a <= 0.7f) return x;
    float y = 0.7f + 0.3f * tanhf((a - 0.7f) / 0.3f);
    return x < 0.0f ? -y : y;
}

static void renderBlock(float* out, int frames) {
    memset(busL, 0, frames * sizeof(float));
    memset(busR, 0, frames * sizeof(float));

    for (note& nt : Notes) {
        if (nt.dead) continue;
        for (int i = 0; i < frames; i++) {
            if (nt.time >= nt.duration) { nt.dead = true; break; }
            float s = 0.0f;
            for (int k = 0; k < nt.oscCount; k++) {
                float x = nt.cos[k] * nt.middle[k] - nt.gg[k] * nt.last[k];
                nt.last[k]   = nt.middle[k];
                nt.middle[k] = x;
                s += x;
            }
            nt.atk *= nt.atkStep;
            float env = 1.0f - nt.atk;
            if (nt.released) { nt.relEnv *= nt.relStep; env *= nt.relEnv; }
            int left = nt.duration - nt.time;
            if (left < nt.fadeLen) env *= (float)left / nt.fadeLen;
            busL[i] += s * env * nt.panL;
            busR[i] += s * env * nt.panR;
            nt.time++;
        }
        if (nt.released && nt.relEnv < 1e-4f) nt.dead = true;

        // drop partials that have decayed below ~-110 dB (saves CPU, avoids denormals)
        for (int k = 0; k < nt.oscCount; ) {
            if (fabsf(nt.middle[k]) < 1e-8f && fabsf(nt.last[k]) < 1e-8f) {
                int e = --nt.oscCount;
                nt.cos[k] = nt.cos[e]; nt.gg[k] = nt.gg[e];
                nt.middle[k] = nt.middle[e]; nt.last[k] = nt.last[e];
            } else {
                k++;
            }
        }
    }

    size_t w = 0;
    for (size_t r = 0; r < Notes.size(); r++)
        if (!Notes[r].dead) { if (w != r) Notes[w] = Notes[r]; w++; }
    Notes.resize(w);

    for (int i = 0; i < frames; i++) {
        float l = busL[i], r = busR[i];
        for (int b = 0; b < EQ_BANDS; b++) { l = eqL[b].process(l); r = eqR[b].process(r); }

        float in = (l + r) * REV_IN + 1e-18f;      // tiny offset keeps the tails out of denormals
        float rl = 0.0f, rr = 0.0f;
        for (int k = 0; k < 4; k++) {
            rl += combL[k].process(in, REV_FB, REV_DAMP);
            rr += combR[k].process(in, REV_FB, REV_DAMP);
        }
        for (int k = 0; k < 2; k++) { rl = apL[k].process(rl); rr = apR[k].process(rr); }

        out[2 * i]     = softClip(MASTER * (l + REV_WET * rl));
        out[2 * i + 1] = softClip(MASTER * (r + REV_WET * rr));
    }
}

void data_callback(ma_device*, void* pOutput, const void*, ma_uint32 frameCount) {
    float* out = static_cast<float*>(pOutput);

    if (cmdLock.try_lock()) {            // never block the audio thread
        cmdLocal.swap(cmdQueue);
        cmdLock.unlock();
    }
    for (const Cmd& c : cmdLocal) {
        if (c.release) releaseKey(c.key);
        else           startNote(c.key, c.level);
    }
    cmdLocal.clear();

    for (ma_uint32 done = 0; done < frameCount; ) {
        int n = (int)(frameCount - done);
        if (n > BLOCK) n = BLOCK;
        renderBlock(out + 2 * done, n);
        done += n;
    }
}

static ma_device device;
static bool      deviceStarted = false;

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_myapplication_cpp_sequencer(JNIEnv *env, jobject /* this */,jboolean isPlaying) {
    if (deviceStarted) return 0;

    ma_device_config config = ma_device_config_init(ma_device_type_playback);
    config.playback.format   = ma_format_f32;
    config.playback.channels = 2;
    config.sampleRate        = 44100;
    config.dataCallback      = data_callback;
    config.periodSizeInFrames = 1024;

    if (ma_device_init(NULL, &config, &device) != MA_SUCCESS) {
        return -1;  // Failed to initialize the device.
    }
    ma_device_start(&device);
    deviceStarted = true;
    return 0;
}

// freq is the piano key index 0..87 (0 = A0), level the velocity 0..127 (<= 1 means default)
extern "C" JNIEXPORT jint JNICALL
Java_com_example_myapplication_cpp_play(JNIEnv *env, jobject /* this */,jint freq, jint level) {
    if (freq < 0 || freq > 87) return 0;
    std::lock_guard<std::mutex> lock(cmdLock);
    cmdQueue.push_back({ (int)freq, (int)level, false });
    return 0;
}

// key released: the damper comes down on the strings
extern "C" JNIEXPORT jint JNICALL
Java_com_example_myapplication_cpp_release(JNIEnv *env, jobject /* this */,jint key) {
    if (key < 0 || key > 87) return 0;
    std::lock_guard<std::mutex> lock(cmdLock);
    cmdQueue.push_back({ (int)key, 0, true });
    return 0;
}
