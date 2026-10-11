//Vmax
#define LOG_TAG "HiFiDacAudioPolicyManager"

#include <AudioPolicyConfig.h>
#include <AudioPolicyManager.h>
#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {

namespace {

constexpr char kHiFiDacEnabledProp[] = "persist.vendor.audio.hifi.enabled";
// AudioService sends music in internal units (10 per volume step) so the DAC can step finer
constexpr char kFineMusicVolumeProp[] = "ro.config.media_vol_fine";
constexpr int kMusicUnitsPerStep = 10;

bool isHiFiRate(uint32_t rate) {
    switch (rate) {
        case 44100:
        case 48000:
        case 88200:
        case 96000:
        case 176400:
        case 192000:
        case 352800:  // DSD64 as PCM (Poweramp); the HAL runs the link at 176.4 kHz
        case 384000:
            return true;
        default:
            return false;
    }
}

bool isHiFiFormat(audio_format_t format) {
    switch (format) {
        case AUDIO_FORMAT_PCM_16_BIT:
        case AUDIO_FORMAT_PCM_24_BIT_PACKED:
        case AUDIO_FORMAT_PCM_8_24_BIT:
        case AUDIO_FORMAT_PCM_32_BIT:
        case AUDIO_FORMAT_PCM_FLOAT:
            return true;
        default:
            return false;
    }
}

}  // namespace

/*
 * While the Hi-Fi DAC drives the jack, music gets a direct output, so it reaches the DAC at its
 * own rate and depth and the DAC applies the volume (48 kHz/16-bit included).
 */
class HiFiDacAudioPolicyManager : public AudioPolicyManager {
  public:
    using AudioPolicyManager::AudioPolicyManager;

    status_t getOutputForAttr(const audio_attributes_t* attr, audio_io_handle_t* output,
                              audio_session_t session, audio_stream_type_t* stream,
                              const AttributionSourceState& attributionSource,
                              audio_config_t* config, audio_output_flags_t* flags,
                              DeviceIdVector* selectedDeviceIds, audio_port_handle_t* portId,
                              std::vector<audio_io_handle_t>* secondaryOutputs,
                              output_type_t* outputType, bool* isSpatialized, bool* isBitPerfect,
                              float* volume, bool* muted) override {
        if (wantsHiFiDirect(attr, session, config, *flags, *selectedDeviceIds)) {
            ALOGI("%s: direct output for session %d, %u Hz, format %#x", __func__, session,
                  config->sample_rate, config->format);
            *flags = AUDIO_OUTPUT_FLAG_DIRECT;
        }
        return AudioPolicyManager::getOutputForAttr(
                attr, output, session, stream, attributionSource, config, flags, selectedDeviceIds,
                portId, secondaryOutputs, outputType, isSpatialized, isBitPerfect, volume, muted);
    }

    // Music volume: AudioService's stream index is already in units; everything else (ranges,
    // group index, dB queries) still talks in steps and is scaled here
    void initStreamVolume(audio_stream_type_t stream, int indexMin, int indexMax) override {
        if (isFineMusicGroup(mEngine->getVolumeGroupForStreamType(stream))) {
            indexMin *= kMusicUnitsPerStep;
            indexMax *= kMusicUnitsPerStep;
        }
        AudioPolicyManager::initStreamVolume(stream, indexMin, indexMax);
    }

    status_t setStreamVolumeIndex(audio_stream_type_t stream, int index, bool muted,
                                  audio_devices_t device) override {
        if (!isFineMusicGroup(mEngine->getVolumeGroupForStreamType(stream)))
            return AudioPolicyManager::setStreamVolumeIndex(stream, index, muted, device);
        if (stream != AUDIO_STREAM_MUSIC) index *= kMusicUnitsPerStep;
        const audio_attributes_t attributes = mEngine->getAttributesForStreamType(stream);
        if (attributes == AUDIO_ATTRIBUTES_INITIALIZER) return NO_ERROR;
        // Bypass this class's scaling in setVolumeIndexForAttributes()
        return AudioPolicyManager::setVolumeIndexForAttributes(attributes, index, muted, device);
    }

    status_t getStreamVolumeIndex(audio_stream_type_t stream, int* index,
                                  audio_devices_t device) override {
        const status_t status = AudioPolicyManager::getStreamVolumeIndex(stream, index, device);
        if (status == NO_ERROR &&
            isFineMusicGroup(mEngine->getVolumeGroupForStreamType(stream)))
            *index = toSteps(*index);
        return status;
    }

    status_t setVolumeIndexForAttributes(const audio_attributes_t& attr, int index, bool muted,
                                         audio_devices_t device) override {
        if (isFineMusicGroup(mEngine->getVolumeGroupForAttributes(attr)))
            index *= kMusicUnitsPerStep;
        return AudioPolicyManager::setVolumeIndexForAttributes(attr, index, muted, device);
    }

    status_t getVolumeIndexForAttributes(const audio_attributes_t& attr, int& index,
                                         audio_devices_t device) override {
        const status_t status = AudioPolicyManager::getVolumeIndexForAttributes(attr, index, device);
        if (status == NO_ERROR && isFineMusicGroup(mEngine->getVolumeGroupForAttributes(attr)))
            index = toSteps(index);
        return status;
    }

    status_t getMinVolumeIndexForAttributes(const audio_attributes_t& attr, int& index) override {
        const status_t status = AudioPolicyManager::getMinVolumeIndexForAttributes(attr, index);
        if (status == NO_ERROR && isFineMusicGroup(mEngine->getVolumeGroupForAttributes(attr)))
            index = toSteps(index);
        return status;
    }

    status_t getMaxVolumeIndexForAttributes(const audio_attributes_t& attr, int& index) override {
        const status_t status = AudioPolicyManager::getMaxVolumeIndexForAttributes(attr, index);
        if (status == NO_ERROR && isFineMusicGroup(mEngine->getVolumeGroupForAttributes(attr)))
            index = toSteps(index);
        return status;
    }

    float getStreamVolumeDB(audio_stream_type_t stream, int index,
                            audio_devices_t device) override {
        if (isFineMusicGroup(mEngine->getVolumeGroupForStreamType(stream)))
            index *= kMusicUnitsPerStep;
        return AudioPolicyManager::getStreamVolumeDB(stream, index, device);
    }

    // A configured music range is in steps; AudioService reads it back as the stream maximum.
    // Legacy configs leave it unset (-1) for AudioService to initialize
    void scaleMusicVolumeRange() {
        if (!mFineMusicVolume) return;
        IVolumeCurves& curves = getVolumeCurves(AUDIO_STREAM_MUSIC);
        if (curves.getVolumeIndexMin() < 0 || curves.getVolumeIndexMax() < 0) return;
        curves.initVolume(curves.getVolumeIndexMin() * kMusicUnitsPerStep,
                          curves.getVolumeIndexMax() * kMusicUnitsPerStep);
    }

  private:
    const bool mFineMusicVolume = property_get_bool(kFineMusicVolumeProp, false);

    bool isFineMusicGroup(volume_group_t group) const {
        return mFineMusicVolume && group != VOLUME_GROUP_NONE &&
               group == mEngine->getVolumeGroupForStreamType(AUDIO_STREAM_MUSIC, false);
    }

    // Negative values mean "unset" and pass through
    static int toSteps(int units) {
        return units < 0 ? units : (units + kMusicUnitsPerStep / 2) / kMusicUnitsPerStep;
    }

    bool wantsHiFiDirect(const audio_attributes_t* attr, audio_session_t session,
                         const audio_config_t* config, audio_output_flags_t flags,
                         const DeviceIdVector& selectedDeviceIds) {
        // Plain music only: FAST, offload, HW A/V sync etc. keep their own paths. Some players
        // (Poweramp) ask for DIRECT|DEEP_BUFFER, which no direct profile matches as is
        if (attr == nullptr ||
            (flags & ~(AUDIO_OUTPUT_FLAG_DIRECT | AUDIO_OUTPUT_FLAG_DEEP_BUFFER)) != 0 ||
            (attr->usage != AUDIO_USAGE_MEDIA && attr->usage != AUDIO_USAGE_UNKNOWN) ||
            // UNKNOWN is SoundPool's default (game and app sound effects)
            attr->content_type != AUDIO_CONTENT_TYPE_MUSIC ||
            (attr->flags & (AUDIO_FLAG_HW_AV_SYNC | AUDIO_FLAG_LOW_LATENCY)) != 0)
            return false;
        if (!isHiFiFormat(config->format) || !isHiFiRate(config->sample_rate) ||
            config->channel_mask != AUDIO_CHANNEL_OUT_STEREO)
            return false;
        if (!property_get_bool(kHiFiDacEnabledProp, false) ||
            mEngine->getPhoneState() != AUDIO_MODE_NORMAL)
            return false;
        // Playback capture and other dynamic policies need a mixed output
        if (!mPolicyMixes.isEmpty() || hasEnabledEffects(session)) return false;

        const sp<DeviceDescriptor> requested =
                mAvailableOutputDevices.getDeviceFromId(getFirstDeviceId(selectedDeviceIds));
        const DeviceVector devices = mEngine->getOutputDevicesForAttributes(*attr, requested);
        if (devices.size() != 1 ||
            !(devices.onlyContainsDevicesWithType(AUDIO_DEVICE_OUT_WIRED_HEADSET) ||
              devices.onlyContainsDevicesWithType(AUDIO_DEVICE_OUT_WIRED_HEADPHONE)))
            return false;

        const sp<IOProfile> profile = getProfileForOutput(
                devices, config->sample_rate, config->format, config->channel_mask,
                AUDIO_OUTPUT_FLAG_DIRECT, true /*directOnly*/);
        if (profile == nullptr) return false;
        // The direct output can't be shared: leave it to a player that is already using it
        for (size_t i = 0; i < mOutputs.size(); i++) {
            const sp<SwAudioOutputDescriptor> desc = mOutputs.valueAt(i);
            if (desc->mProfile == profile && desc->mDirectClientSession != session &&
                desc->isActive()) {
                ALOGI("%s: direct output busy with session %d", __func__,
                      desc->mDirectClientSession);
                return false;
            }
        }
        return true;
    }

    // Effects don't run on direct outputs; keep sessions that use them on the mixer
    bool hasEnabledEffects(audio_session_t session) const {
        for (size_t i = 0; i < mEffects.size(); i++) {
            const sp<EffectDescriptor> effect = mEffects.valueAt(i);
            // Listeners such as QTI's music_helper (post-processing on every music session)
            if ((effect->mDesc.flags & EFFECT_FLAG_NO_PROCESS_MASK) == EFFECT_FLAG_NO_PROCESS)
                continue;
            if (effect->mEnabled && effect->mSession == session) {
                ALOGD("%s: session %d keeps the mixer for effect \"%s\"", __func__, session,
                      effect->mDesc.name);
                return true;
            }
        }
        return false;
    }
};

// Same as AudioPolicyService's built-in factory, with the subclass
extern "C" AudioPolicyInterface* createAudioPolicyManager(
        AudioPolicyClientInterface* clientInterface) {
    HiFiDacAudioPolicyManager* apm;
    media::AudioPolicyConfig apmConfig;
    if (clientInterface->getAudioPolicyConfig(&apmConfig) == OK) {
        auto config = AudioPolicyConfig::loadFromApmAidlConfigWithFallback(apmConfig);
        apm = new HiFiDacAudioPolicyManager(
                config,
                loadApmEngineLibraryAndCreateEngine(config->getEngineLibraryNameSuffix(),
                                                    apmConfig.engineConfig),
                clientInterface);
    } else {
        auto config = AudioPolicyConfig::loadFromApmXmlConfigWithFallback();
        apm = new HiFiDacAudioPolicyManager(
                config, loadApmEngineLibraryAndCreateEngine(config->getEngineLibraryNameSuffix()),
                clientInterface);
    }
    if (apm->initialize() != NO_ERROR) {
        delete apm;
        return nullptr;
    }
    apm->scaleMusicVolumeRange();
    return apm;
}

extern "C" void destroyAudioPolicyManager(AudioPolicyInterface* interface) {
    delete interface;
}

}  // namespace android
