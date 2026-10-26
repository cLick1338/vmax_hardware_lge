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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuadDACTileService extends TileService {

    private final static String TAG = "QuadDACTileService";

    private HeadsetPluggedTileReceiver headsetPluggedTileReceiver = new HeadsetPluggedTileReceiver();

    private QuadDAC DacControlInterface = new QuadDAC();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateSubtitleRunnable = this::updateSubtitle;
    private final Runnable pollSubtitleRunnable = this::pollSubtitle;
    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();
    private boolean listening;
    private boolean readInFlight;
    private int pollGeneration;
    private String lastSubtitle;
    private boolean subtitlePublished;

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
        if (!isPlugged())
            return;
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
        listening = true;
        refreshTile();
        schedulePoll();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        listening = false;
        stopPoll();
        lastSubtitle = null;
        subtitlePublished = false;
        unregisterReceiver(headsetPluggedTileReceiver);
        getSystemService(AudioManager.class).unregisterAudioPlaybackCallback(playbackCallback);
        handler.removeCallbacks(updateSubtitleRunnable);
    }

    @Override
    public void onDestroy() {
        listening = false;
        stopPoll();
        handler.removeCallbacks(updateSubtitleRunnable);
        statusExecutor.shutdownNow();
        super.onDestroy();
    }

    private void stopPoll() {
        handler.removeCallbacks(pollSubtitleRunnable);
        pollGeneration++;
    }

    private boolean shouldPoll() {
        if (!listening || !isPlugged())
            return false;
        try {
            return DacControlInterface.isEnabled();
        } catch(Exception e) {
            return false;
        }
    }

    private void schedulePoll() {
        handler.removeCallbacks(pollSubtitleRunnable);
        if (shouldPoll())
            handler.postDelayed(pollSubtitleRunnable, 1000);
        else
            stopPoll();
    }

    private void pollSubtitle() {
        if (!shouldPoll()) {
            stopPoll();
            return;
        }
        if (readInFlight) {
            schedulePoll();
            return;
        }
        final int generation = pollGeneration;
        readInFlight = true;
        statusExecutor.execute(() -> {
            String subtitle = getSubtitle();
            handler.post(() -> {
                readInFlight = false;
                if (generation != pollGeneration)
                    return;
                if (!shouldPoll()) {
                    stopPoll();
                    return;
                }
                Tile tile = getQsTile();
                if (tile != null && tile.getState() == Tile.STATE_ACTIVE)
                    publishSubtitle(tile, subtitle);
                schedulePoll();
            });
        });
    }

    private void publishSubtitle(Tile tile, String subtitle) {
        if (subtitlePublished && Objects.equals(lastSubtitle, subtitle))
            return;
        tile.setSubtitle(subtitle);
        tile.updateTile();
        lastSubtitle = subtitle;
        subtitlePublished = true;
    }

    private boolean isPlugged() {
        return getSystemService(AudioManager.class).isWiredHeadsetOn();
    }

    // No sticky plug broadcast after boot when unplugged, so set the state from the current plug state
    private void refreshTile() {
        if (getQsTile() == null)
            return;
        if (!DacControlInterface.dac_service_available) {
            setTileUnavailable();
        } else if (!isPlugged()) {
            setTileUnplugged();
        } else {
            try {
                if (DacControlInterface.isEnabled())
                    setTileActive();
                else
                    setTileInactive();
            } catch(Exception e) {}
        }
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
        publishSubtitle(quaddactile, getSubtitle());
    }

    private void setTileActive()
    {
        Tile quaddactile = getQsTile();
        quaddactile.setState(Tile.STATE_ACTIVE);
        quaddactile.setLabel(getResources().getString(R.string.quad_dac_tile));
        lastSubtitle = getSubtitle();
        subtitlePublished = true;
        quaddactile.setSubtitle(lastSubtitle);
	    quaddactile.updateTile();
        schedulePoll();
    }

    private void setTileInactive()
    {
        stopPoll();
        Tile quaddactile = getQsTile();
        quaddactile.setState(Tile.STATE_INACTIVE);
        quaddactile.setLabel(getResources().getString(R.string.quad_dac_tile));
        lastSubtitle = getResources().getString(R.string.dac_tile_off);
        subtitlePublished = true;
        quaddactile.setSubtitle(lastSubtitle);
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
        stopPoll();
        Tile quaddactile = getQsTile();
        quaddactile.setState(Tile.STATE_UNAVAILABLE);
        quaddactile.setLabel(getResources().getString(R.string.quad_dac_tile));
        lastSubtitle = subtitle;
        subtitlePublished = true;
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
