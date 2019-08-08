LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
#LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21

LOCAL_SRC_FILES := $(call all-java-files-under,src)

LOCAL_PROGUARD_ENABLED := full
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_PACKAGE_NAME := ScreenRecorder
LOCAL_CERTIFICATE := platform

LOCAL_USE_AAPT2 := true


LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    android-support-v17-leanback


include $(BUILD_PACKAGE)
#include $(BUILD_MULTI_PREBUILT)
# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
