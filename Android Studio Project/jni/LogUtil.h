// LogUtil.h

#ifndef __LOG_UTIL_H__
#define __LOG_UTIL_H__

#include <android/log.h>

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, "SmartCam", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , "SmartCam", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO   , "SmartCam", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN   , "SmartCam", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , "SmartCam", __VA_ARGS__)

#endif//__LOG_UTIL_H__