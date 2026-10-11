//Vmax
package org.lineageos.settings.device.dac.utils;

import android.content.Context;

import org.lineageos.settings.device.dac.R;

import java.text.NumberFormat;

import vendor.lge.hardware.audio.dac.control.V2_1.DacState;
import vendor.lge.hardware.audio.dac.control.V2_1.Impedance;
import vendor.lge.hardware.audio.dac.control.V2_1.OutputStatus;

/* Panel and tile text for the driver's output status */
public final class DacStatus {

    // Bounds the MBHC driver uses to classify headphones when the DAC is present
    private static final int HIGH_IMPEDANCE_MIN_OHMS = 47;
    private static final int AUX_MIN_OHMS = 420;
    private static final int UNEVEN_RATIO = 2;

    private DacStatus() {}

    public static String formatRate(Context context, int hz) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);
        return context.getString(R.string.dac_rate_khz, format.format(hz / 1000.0));
    }

    private static boolean isFloating(OutputStatus status) {
        return status.impedanceLeft == Impedance.FLOATING
                || status.impedanceRight == Impedance.FLOATING;
    }

    // Stereo drivers match; a large L/R gap means the plug moved during the measurement
    private static boolean isUneven(OutputStatus status) {
        int low = Math.min(status.impedanceLeft, status.impedanceRight);
        int high = Math.max(status.impedanceLeft, status.impedanceRight);
        return !isFloating(status) && high > 0 && high > UNEVEN_RATIO * low;
    }

    // Hi-Fi mode matching the measured load, or -1 if nothing usable was measured
    public static int suggestedMode(OutputStatus status) {
        int ohms = Math.max(status.impedanceLeft, status.impedanceRight);
        if (isUneven(status))
            return -1;
        if (isFloating(status) || ohms > AUX_MIN_OHMS)
            return 2;
        if (ohms >= HIGH_IMPEDANCE_MIN_OHMS)
            return 1;
        return ohms > 0 ? 0 : -1;
    }

    // IDLE drops to BYPASS a few seconds after the stream stops; keep re-reading until then
    public static boolean isSettling(OutputStatus status) {
        return status != null && status.state == DacState.IDLE;
    }

    public static String output(Context context, OutputStatus status, boolean enabled) {
        if (status == null)
            return context.getString(R.string.dac_status_unavailable);
        switch (status.state) {
            case DacState.HIFI:
                if (status.dop > 0)
                    return context.getString(R.string.dac_output_hifi_dop, status.dop);
                return context.getString(status.osfBypass ? R.string.dac_output_hifi_osf_bypass
                        : R.string.dac_output_hifi,
                        formatRate(context, status.sampleRate), status.bitDepth);
            case DacState.IDLE:
                return context.getString(R.string.dac_output_idle);
            case DacState.BYPASS:
                return context.getString(enabled ? R.string.dac_output_standby
                        : R.string.dac_output_bypass);
            default:
                return context.getString(R.string.dac_output_off);
        }
    }

    private static String impedance(Context context, OutputStatus status, boolean compact) {
        if (isFloating(status))
            return context.getString(R.string.dac_impedance_floating);
        if (status.impedanceLeft == status.impedanceRight)
            return context.getString(R.string.dac_impedance, status.impedanceLeft);
        return context.getString(compact ? R.string.dac_impedance_lr_short
                : R.string.dac_impedance_lr, status.impedanceLeft, status.impedanceRight);
    }

    public static String headphones(Context context, OutputStatus status, int selectedMode) {
        if (status == null)
            return context.getString(R.string.dac_status_unavailable);
        if (isUneven(status))
            return context.getString(R.string.dac_impedance_uneven,
                    impedance(context, status, false));
        int suggested = suggestedMode(status);
        if (suggested < 0)
            return context.getString(R.string.dac_impedance_unknown);
        String text = impedance(context, status, false);
        String mode = context.getResources().getStringArray(R.array.hifi_mode_entry_values)[suggested];
        // A floating reading already names the AUX/Line out mode
        if (isFloating(status) && suggested == (selectedMode == Constants.HIFI_MODE_AUTO
                ? status.hifiMode : selectedMode))
            return text;
        // Auto applies the mode when the DAC next starts, not while it is running
        if (selectedMode == Constants.HIFI_MODE_AUTO)
            return context.getString(status.hifiMode == suggested ? R.string.dac_mode_auto
                    : R.string.dac_mode_auto_pending, text, mode);
        if (suggested == selectedMode)
            return text;
        return context.getString(R.string.dac_mode_suggestion, text, mode);
    }

    // Short form for the QS tile, null when there is nothing useful to add
    public static String tileSubtitle(Context context, OutputStatus status) {
        if (status == null)
            return null;
        String state;
        switch (status.state) {
            case DacState.HIFI:
                state = status.dop > 0 ? context.getString(R.string.dac_tile_dop, status.dop)
                        : context.getString(R.string.dac_tile_format,
                                formatRate(context, status.sampleRate), status.bitDepth);
                break;
            case DacState.IDLE:
            case DacState.BYPASS:
                state = context.getString(R.string.dac_tile_standby);
                break;
            default:
                return null;
        }
        if (suggestedMode(status) < 0)
            return state;
        return context.getString(R.string.dac_tile_subtitle, state,
                impedance(context, status, true));
    }
}
