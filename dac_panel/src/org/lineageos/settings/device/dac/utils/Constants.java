package org.lineageos.settings.device.dac.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Constants {

    public static final String DAC_SWITCH_KEY = "quaddac_switch";
    public static final String SOUND_PRESET_KEY = "sound_preset_dropdown";
    public static final String DIGITAL_FILTER_KEY = "digital_filter_dropdown";
    public static final String BALANCE_KEY = "balance";
    public static final String RESET_COEFFICIENTS_KEY = "reset_coefficients";
    public static final String HIFI_DOP_KEY = "hifi_dop_dropdown";
    public static final String HIFI_MODE_KEY = "hifi_mode_dropdown";
    public static final int HIFI_MODE_AUTO = 3;
    public static final String AVC_VOLUME_KEY = "avc_volume_seekbar";
    public static final String MEDIA_VOLUME_STEPS_KEY = "media_volume_steps";

    // Read by AudioService on every volume change while the Quad DAC is the output
    public static final String MEDIA_VOLUME_STEPS_PROP = "persist.sys.audio.dac_volume_steps";
    // Android's 15, then counts that split both AudioService's 150 units and the 100-point
    // volume curve evenly. More than 50 gives steps under the DAC's 0.5 dB at the top of the
    // headphone curve
    public static final int[] MEDIA_VOLUME_STEPS = { 15, 25, 50 };
    public static final int DEFAULT_MEDIA_VOLUME_STEPS = 50;

    public static final String STATUS_CATEGORY_KEY = "dac_status";
    public static final String STATUS_DETAILS_KEY = "dac_status_details";

    // Driver state lags playback events; re-read once it has settled and once after standby
    public static final long STATUS_SETTLE_DELAY_MS = 500;
    public static final long STATUS_STANDBY_DELAY_MS = 3500;
    public static final long STATUS_POLL_MS = 1000;

    public static final String   CUSTOM_FILTER_SHAPE_KEY = "custom_filter_shape";
    public static final String   CUSTOM_FILTER_SYMMETRY_KEY = "custom_filter_symmetry";
    public static final String[] CUSTOM_FILTER_COEFF_KEYS = {
        "custom_filter_coeff0", "custom_filter_coeff1", "custom_filter_coeff2", "custom_filter_coeff3",
        "custom_filter_coeff4", "custom_filter_coeff5", "custom_filter_coeff6", "custom_filter_coeff7",
        "custom_filter_coeff8", "custom_filter_coeff9", "custom_filter_coeff10", "custom_filter_coeff11",
        "custom_filter_coeff12", "custom_filter_coeff13"
    };
}
