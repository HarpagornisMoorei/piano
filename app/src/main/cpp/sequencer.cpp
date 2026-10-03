 #define MINIAUDIO_IMPLEMENTATION
#include <jni.h>

#include <string>

#include "miniaudio.h"
#include <vector>
#include <any>

#include <chrono>
#include <thread>
#include <cmath>
#include <math.h>
#include <vector>
#include <android/log.h>
#define LOG_TAG "MyNativeTag"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ma_device h;
float myBuffer[200 * 2];
static ma_uint32 i;
static int c =0;
 const float dt = 1.0f / 44100.0f;
 struct note {
     float freq;
     int   time = 0;
     int   duration = 44100;
     int   midi = 0;
     float gg[2][32]     = {};   // 32, not 20 — knock writes up to index 28
     float middle[2][32] = {};
     float last[2][32]   = {};
     float cos[2][32]    = {};
     float tau = 0.0f;
     float atk = 1.0f;
     float atkStep = 1.0f;
     int   oscCount = 29;        // highest written index (28) + 1
     bool  dead = false;
 };
std::vector<note> Notes;
int l;
int n;
float harmonies=0.0f;

#include <cstring>  // for memset

 void data_callback(ma_device*, void* pOutput, const void*, ma_uint32 frameCount) {
     float* out = static_cast<float*>(pOutput);
     memset(out, 0, frameCount * sizeof(float));

     for (size_t v = 0; v < Notes.size(); v++) {
         note& nt = Notes[v];          // <- reference, use nt everywhere below

         int end = (int)frameCount;
         if (nt.duration - nt.time < end) end = nt.duration - nt.time;

         for (int i = 0; i < end; i++) {
             float s = 0.0f;
             for (int ch = 0; ch < 2; ch++) {
                 for (int n = 1; n < nt.oscCount; n++) {
                     float x = nt.cos[ch][n] * nt.middle[ch][n]
                               - nt.last[ch][n] * nt.gg[ch][n];
                     nt.last[ch][n]   = nt.middle[ch][n];
                     if (x > -1e-12f && x < 1e-12f) x = 0.0f;   // denormal kill
                     nt.middle[ch][n] = x;
                     s += x;
                 }
             }

             nt.atk *= nt.atkStep;
             out[i] += 0.22f * (1.0f - nt.atk) * s;
             nt.time++;
         }
         nt.dead = (nt.time >= nt.duration);
     }

     // one compaction pass per block
     size_t w = 0;
     for (size_t r = 0; r < Notes.size(); r++)
         if (!Notes[r].dead) Notes[w++] = Notes[r];
     Notes.resize(w);
 }
void resstart(){

    c=0;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_myapplication_cpp_sequencer(JNIEnv *env, jobject /* this */,jboolean isPlaying) {

    ma_device_config config = ma_device_config_init(ma_device_type_playback);
    config.playback.format = ma_format_f32;
    config.playback.channels = 1;
    config.sampleRate = 44100;
    config.dataCallback = data_callback;
    config.periodSizeInFrames = 1024;
    static ma_device devices;
    h=devices;

    if (ma_device_init(NULL, &config, &h) != MA_SUCCESS) {
        return -1;  // Failed to initialize the device.
    }

    ma_device_start(&h);




    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_myapplication_cpp_play(JNIEnv *env, jobject /* this */,jint freq, jint level) {
    note myNote;
    int midi = freq + 20;
    float Freq = powf(powf(2.0f,1.0f/12.0f),freq-49.0f)*440.0f;

    Notes.push_back({Freq,0,44100, freq});
    float h1 = ((midi * 37) % 23) / 23.0f - 0.5f;   // -0.5..0.5, stable per note
    float h2 = ((midi * 61) % 19) / 19.0f - 0.5f;
    float h3 = ((midi * 29) % 31) / 31.0f - 0.5f;
    float b   = 0.00008f * powf(2.0f, (midi - 60) / 11.0f) * (1.0f + 0.25f * h2);
    float p   = 0.12f * (1.0f + 0.12f * h3);
    Notes.back().tau = (0.12f + 9.0f * powf(2.0f, -(midi - 21) / 15.0f)) * (1.0f + 0.15f * h1);
    float dt_cents = 0.0015f * powf(2.0f, -(midi - 60) / 24.0f);

    float detune[2] = { -dt_cents, +dt_cents };   // ±0.6 cents

    for (int s = 0; s < 2; s++) {
        int nMax = 16 - (int)(11.0f * (midi - 21) / 66.0f);   // 16 in bass, ~5 up top
        for (int n = 1; n <= nMax; n++) {



            float slow = 0.7f + 4.0f * powf((midi - 21) / 66.0f, 2.0f);   // 0.7 in bass, ~2.3 up top
            float rate[2] = { 3.0f, slow };
            float g = expf(-rate[s] * powf((float)n, 0.9f) * dt / Notes.back().tau);
            float w = 2.0f*M_PI * Freq * (1.0f + detune[s]) * (n + b*n*n*n) * dt;

            float width = 0.15f * (1.0f + 0.35f * h2);
            float notch = 0.35f + 0.65f * fabsf(sinf(M_PI * n * p));
            float lp    = 1.0f / (1.0f + powf(n * width, 2.0f));
            float sAmp[2] = { 0.65f, 0.35f };
            float amp = powf(1.0f/n, 1.15f + 0.25f*h1) * notch * lp * sAmp[s];

            Notes.back().cos[s][n] = 2.0f * g * cosf(w);;
            float ph = 2.0f * M_PI * (((midi * 53 + n * 17) % 97) / 97.0f);
            Notes.back().middle[s][n] = amp * sinf(ph) ;
            Notes.back().last[s][n]   = amp * sinf(ph - w) / g;
            Notes.back().gg[s][n] = g * g;


        }
        if (s == 0) {                                  // knock on one string only
            float jitter = 1.0f + 0.06f * ((midi * 37 % 17) / 17.0f - 0.5f);
            float kf[3] = { 1300.0f * jitter, 2450.0f * jitter, 3900.0f * jitter };
            float kdecay  = 0.011f;                    // ~11 ms, very fast
            float kAmp    = 0.05f + 0.22f * (midi - 21) / 66.0f;   // louder in treble
            Notes.back().atk = 1.0f;
            Notes.back().atkStep = expf(-dt / 0.005f);
            for (int k = 0; k < 12; k++) {
                int   idx = 17 + k;
                float hk  = ((midi * (7 + k*13)) % 41) / 41.0f;
                float f   = 600.0f + 5200.0f * hk;
                float w   = 2.0f * M_PI * f * dt;
                float g   = expf(-dt / (0.004f + 0.008f * hk));
                float a   = kAmp * (0.35f / (1.0f + 3.0f * hk));

                Notes.back().cos[0][idx]    = 2.0f * g * cosf(w);
                Notes.back().gg[0][idx]     = g * g;
                Notes.back().last[0][idx]   = -a * sinf(w) / g;
                Notes.back().middle[0][idx] = 0.0f;
            }
        }

    }
    resstart();
    return 0;


}