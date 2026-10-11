package org.lineageos.settings.device.dac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.lineageos.settings.device.dac.utils.Constants;
import org.lineageos.settings.device.dac.utils.DacStatus;
import org.lineageos.settings.device.dac.utils.QuadDAC;

import java.util.List;

public class QuadDACTileService extends TileService {

    private final static String TAG = "QuadDACTileService";

    private HeadsetPluggedTileReceiver headsetPluggedTileReceiver = new HeadsetPluggedTileReceiver();

    private QuadDAC DacControlInterface = new QuadDAC();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateSubtitleRunnable = this::updateSubtitle;

    private final AudioManager.AudioPlaybackCallback playbackCallback =
            new AudioManager.AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            scheduleSubtitleUpdate();
        }
    };

    @Override
    public void onClick() {
        super.onClick();
        try {
            if (DacControlInterface.isEnabled()) {
                DacControlInterface.disable();
                setTileInactive();
            } else {
                DacControlInterface.enable();
                setTileActive();
                scheduleSubtitleUpdate();
            }
        } catch(Exception e) {}
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        try {
            DacControlInterface.initialize();
        } catch(Exception e) {}
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetPluggedTileReceiver, filter);
        getSystemService(AudioManager.class).registerAudioPlaybackCallback(playbackCallback, handler);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        unregisterReceiver(headsetPluggedTileReceiver);
        getSystemService(AudioManager.class).unregisterAudioPlaybackCallback(playbackCallback);
        handler.removeCallbacks(updateSubtitleRunnable);
    }

    private void scheduleSubtitleUpdate() {
        handler.removeCallbacks(updateSubtitleRunnable);
        handler.postDelayed(updateSubtitleRunnable, Constants.STATUS_SETTLE_DELAY_MS);
        handler.postDelayed(updateSubtitleRunnable, Constants.STATUS_STANDBY_DELAY_MS);
    }

    private String getSubtitle() {
        try {
            return DacStatus.tileSubtitle(this, DacControlInterface.getOutputStatus());
        } catch(Exception e) {
            return null;
        }
    }

    private void updateSubtitle()
    {
        Tile quaddactile = getQsTile();
        if (quaddactile == null || quaddactile.getState() != Tile.STATE_ACTIVE)
            return;
        quaddactile.setSubtitle(getSubtitle());
        quaddactile.updateTile();
    }

    private void setTileActive()
    {
        Tile quaddactile = getQsTile();
        quaddactile.setState(Tile.STATE_ACTIVE);
        quaddactile.setLabel(getResources().getString(R.string.quad_dac_tile));
        quaddactile.setSubtitle(getSubtitle());
	    quaddactile.updateTile();
    }

    private void setTileInactive()
    {
        Tile quaddactile = getQsTile();
        quaddactile.setState(Tile.STATE_INACTIVE);
        quaddactile.setLabel(getResources().getString(R.string.quad_dac_tile));
        quaddactile.setSubtitle(getResources().getString(R.string.dac_tile_off));
	    quaddactile.updateTile();
    }

    private void setTileUnavailable()
    {
        setTileUnavailable(null);
    }

    // Nothing plugged in: say whether the DAC will power up on the next plug
    private void setTileUnplugged()
    {
        boolean enabled = false;
        try {
            enabled = DacControlInterface.isEnabled();
        } catch(Exception e) {}
        setTileUnavailable(getResources().getString(
                enabled ? R.string.dac_tile_powered_off : R.string.dac_tile_off));
    }

    private void setTileUnavailable(String subtitle)
    {
        Tile quaddactile = getQsTile();
        quaddactile.setState(Tile.STATE_UNAVAILABLE);
        quaddactile.setLabel(getResources().getString(R.string.quad_dac_tile));
        quaddactile.setSubtitle(subtitle);
	    quaddactile.updateTile();
    }

    private class HeadsetPluggedTileReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(!DacControlInterface.dac_service_available) {
                setTileUnavailable();
                return;
            }
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch(state)
                {
                    case 1: // Headset plugged in
                        try {
                            if (DacControlInterface.isEnabled()) {
                                setTileActive();
                            } else {
                                setTileInactive();
                            }
                        } catch(Exception e) {}
                        break;
                    case 0: // Headset unplugged
                        setTileUnplugged();
                        break;
                    default: break;
                }
            }
        }
    }
}
