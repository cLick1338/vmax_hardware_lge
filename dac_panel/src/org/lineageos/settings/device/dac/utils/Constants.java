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
    public static final String AVC_VOLUME_KEY = "avc_volume_seekbar";

    public static final String STATUS_CATEGORY_KEY = "dac_status";
    public static final String STATUS_OUTPUT_KEY = "dac_status_output";
    public static final String STATUS_HEADPHONES_KEY = "dac_status_headphones";
    public static final String STATUS_SOURCE_KEY = "dac_status_source";

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
