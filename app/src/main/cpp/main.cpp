#include <jni.h>
#include <thread>
#include <unistd.h>
#include <string>
#include <vector>
#include "log.h"
#include "xdl.h"
#include "dobby.h"

static float g_timeScale = 1.00f;
void*(*il2cpp_resolve_icall)(const char *) = nullptr;

void(*_set_timeScale)(float);
void set_timeScale(float Time) {
    LOGD("Native: Setting timeScale to %.2f (original: %.2f)", g_timeScale, Time);
    _set_timeScale(g_timeScale);
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_testxp_Main_setTimeScale(JNIEnv *env, jclass clazz, jfloat scale) {
    g_timeScale = scale;
    LOGD("Native: TimeScale updated to %.2f", g_timeScale);
}

}

void* find_unity_time_method() {
    if (!il2cpp_resolve_icall) {
        return nullptr;
    }
    
    // 尝试多个可能的函数签名
    std::vector<const char*> method_names = {
        "UnityEngine.Time::set_timeScale(System.Single)",
        "UnityEngine.Time::set_timeScale",
        "Time::set_timeScale",
    };
    
    for (const char* name : method_names) {
        void* method = il2cpp_resolve_icall(name);
        if (method) {
            LOGD("Native: Found Unity method %s at %p", name, method);
            return method;
        }
    }
    
    return nullptr;
}

void hack_thread() {
    sleep(8); // 等待系统稳定
    
    LOGD("Native: Starting universal hack in process %d", getpid());
    
    // 尝试加载libil2cpp.so
    auto il2cpp_handle = xdl_open("libil2cpp.so", XDL_TRY_FORCE_LOAD);
    if (!il2cpp_handle) {
        LOGD("Native: No libil2cpp.so found, this may not be a Unity app");
        return;
    }
    
    LOGD("Native: libil2cpp.so loaded at %p", il2cpp_handle);
    
    // 获取il2cpp_resolve_icall函数
    il2cpp_resolve_icall = (void*(*)(const char*))xdl_sym(il2cpp_handle, "il2cpp_resolve_icall", nullptr);
    if (!il2cpp_resolve_icall) {
        LOGD("Native: Failed to find il2cpp_resolve_icall");
        return;
    }
    
    LOGD("Native: il2cpp_resolve_icall found at %p", il2cpp_resolve_icall);
    
    // 查找并Hook Unity时间方法
    void* time_method = find_unity_time_method();
    if (time_method) {
        if (DobbyHook(time_method, (void*)set_timeScale, (void**)&_set_timeScale) == 0) {
            LOGD("Native: Successfully hooked Unity Time method");
        } else {
            LOGD("Native: Failed to hook Unity Time method");
        }
    } else {
        LOGD("Native: No Unity Time method found, this may not be a Unity app or uses different method names");
    }
}

__attribute__((constructor))
void lib_main() {
    // 直接启动hack线程，不依赖任何条件
    std::thread(hack_thread).detach();
}