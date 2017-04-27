LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

location_infoapi_dir := ../LocationInfo

src_dirs := src \
    $(location_infoapi_dir)/src

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_SRC_FILES += src/com/android/internal/telephony/ITelephony.aidl \
                   src/com/android/incallui/IScreenFlagService.aidl \

LOCAL_PACKAGE_NAME := SosCall

LOCAL_RESOURCE_DIR = \
    $(LOCAL_PATH)/res \

LOCAL_JAVA_LIBRARIES += telephony-common

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-v7-appcompat \
    networkcommon

LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)





