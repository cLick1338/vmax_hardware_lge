//Vmax
package org.lineageos.settings.device.dac.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;

import org.lineageos.settings.device.dac.R;

// SeekBarPreference whose value label reads "-14 dB"; commits on release like the stock one
public class DecibelSeekBarPreference extends SeekBarPreference {

    private TextView label;
    private boolean tracking;

    public DecibelSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        label = (TextView) holder.findViewById(androidx.preference.R.id.seekbar_value);
        SeekBar seekBar = (SeekBar) holder.findViewById(androidx.preference.R.id.seekbar);
        if (label == null || seekBar == null)
            return;
        // Widest label up front so the bar doesn't resize while dragging
        label.setMinWidth(label.getPaddingStart() + label.getPaddingEnd()
                + (int) Math.ceil(label.getPaint().measureText(format(getMin()))));
        // Replaces the stock listener, which rewrites the label as a bare number
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;
                label.setText(format(progress + getMin()));
                if (!tracking)
                    commit(bar);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                tracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                tracking = false;
                commit(bar);
            }
        });
        label.setText(format(getValue()));
    }

    private void commit(SeekBar bar) {
        int value = bar.getProgress() + getMin();
        if (value == getValue())
            return;
        if (callChangeListener(value)) {
            setValue(value);
        } else {
            bar.setProgress(getValue() - getMin());
            label.setText(format(getValue()));
        }
    }

    private String format(int value) {
        return getContext().getString(R.string.decibel_value, value);
    }
}
