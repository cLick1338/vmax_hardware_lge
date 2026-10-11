//Vmax
package org.lineageos.settings.device.dac.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.settings.device.dac.R;

// Read-only label/value lines for the output status, not a clickable row
public class StatusPreference extends Preference {

    private CharSequence output, impedance;

    public StatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.status_preference);
        setSelectable(false);
    }

    public void setStatus(CharSequence output, CharSequence impedance) {
        if (TextUtils.equals(output, this.output) && TextUtils.equals(impedance, this.impedance))
            return;
        this.output = output;
        this.impedance = impedance;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setFocusable(false);
        ((TextView) holder.findViewById(R.id.status_output)).setText(output);
        ((TextView) holder.findViewById(R.id.status_impedance)).setText(impedance);
    }
}
