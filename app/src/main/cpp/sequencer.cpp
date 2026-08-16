#define MINIAUDIO_IMPLEMENTATION
#include <jni.h>

#include <string>

#include "miniaudio.h"
#include <vector>
#include <any>

#include <chrono>
#include <thread>
static ma_device h;
float myBuffer[200 * 2];
void data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount) {
    float *out = static_cast<float *>(pOutput);

    static int c =0;
    for (ma_uint32 i = 0; i < frameCount * 2; i++) {


            float t= c / 48000.0f;
            out[i] = sinf(2*3.14*440*t)* exp(-4*t); // Silence
            float out1 = sinf(2*3.14*440*c)* exp(-4*440*t)/2;
            float out2 = sinf(3*3.14*440*c)* exp(-4*440*t)/4;
            float out3 = sinf(4*3.14*440*c)* exp(-4*440*t)/8;
            float out4 = sinf(5*3.14*440*c)* exp(-4*440*t)/16;
            out[i] += out1+out2+out3;
        out[i] += out1*out2+out3;
                c++;

        // In playback mode copy data to pOutput. In capture mode read data from pInput. In full-duplex mode, both
        // pOutput and pInput will be valid and you can move data from pInput into pOutput. Never process more than
        // frameCount frames.

    }

}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_myapplication_cpp_sequencer(JNIEnv *env, jobject /* this */,jboolean isPlaying) {

        ma_device_config config = ma_device_config_init(ma_device_type_playback);
        config.playback.format = ma_format_f32;
        config.playback.channels = 2;
        config.sampleRate = 48000;
        config.dataCallback = data_callback;
        static ma_device devices;
        h=devices;

        if (ma_device_init(NULL, &config, &h) != MA_SUCCESS) {
            return -1;  // Failed to initialize the device.
        }

    ma_device_start(&h);




return 0;
}
extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_cpp_play(JNIEnv *env, jobject /* this */) {

    data_callback(nullptr, myBuffer, nullptr, 400);


}