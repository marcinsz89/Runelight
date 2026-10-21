package com.moonbench.bifrost.probe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;

/** Android-only dependencies: standalone test-APK launches do not load the target's Kotlin runtime. */
public final class TonePlaybackFixtureActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioTrack track;
    private long startedAt;
    private boolean syncPattern;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        syncPattern = getIntent().getBooleanExtra("sync_pattern", false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(new View(this) {
            private final Paint paint = new Paint();
            @Override protected void onDraw(Canvas canvas) {
                int head = track == null ? 0 : track.getPlaybackHeadPosition();
                float hue = (head % (44100 * 4)) * 360f / (44100 * 4);
                paint.setColor(syncPattern ? Color.HSVToColor(new float[] {hue, 1f, 1f}) : Color.RED);
                canvas.drawRect(0, 0, getWidth() / 2f, getHeight(), paint);
                paint.setColor(syncPattern ? Color.HSVToColor(new float[] {(hue + 180) % 360, 1f, 1f}) : Color.BLUE);
                canvas.drawRect(getWidth() / 2f, 0, getWidth(), getHeight(), paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(18);
                canvas.drawText(syncPattern ? "Sync check: changing picture + 2 beats/sec" : "Separate-UID playback fixture", 10, 24, paint);
                if (syncPattern && head % (44100 / 2) < 44100 / 10)
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getHeight() * .08f, paint);
                if (syncPattern && track != null) postInvalidateOnAnimation();
            }
        });
        try {
            AudioManager manager = getSystemService(AudioManager.class);
            manager.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
            int rate = 44100;
            // Quiet tone, louder tone, then two seconds of silence. Repeats for a bounded time.
            short[] data = new short[rate * 4];
            for (int index = 0; index < data.length; index++) {
                int second = index / rate;
                int amplitude = syncPattern ? (index % (rate / 2) < rate / 10 ? 6600 : 0)
                    : second == 0 ? 2200 : second == 1 ? 6600 : 0;
                data[index] = (short) (amplitude * Math.sin(2 * Math.PI * 440 * index / rate));
            }
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build();
            track = new AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(data.length * 2).build();
            if (track.write(data, 0, data.length) != data.length) throw new IllegalStateException("write");
            track.setVolume(0.35f);
            if (track.setLoopPoints(0, data.length, -1) != AudioTrack.SUCCESS) throw new IllegalStateException("loop");
            track.play();
            startedAt = SystemClock.elapsedRealtime();
            main.post(new Runnable() {
                @Override public void run() {
                    try {
                        writeStatus(new JSONObject().put("uid", Process.myUid()).put("package", getPackageName())
                            .put("capture_policy", manager.getAllowedCapturePolicy())
                            .put("attributes", attributes.toString()).put("state", track.getState())
                            .put("play_state", track.getPlayState()).put("playback_head", track.getPlaybackHeadPosition())
                            .put("session_id", track.getAudioSessionId()).put("started_elapsed_ms", startedAt)
                            .put("sync_pattern", syncPattern)
                            .put("updated_elapsed_ms", SystemClock.elapsedRealtime()));
                    } catch (Exception ignored) { /* Observation must not crash playback. */ }
                    main.postDelayed(this, 500);
                }
            });
            int duration = Math.max(5, Math.min(120, getIntent().getIntExtra("duration_s", 60)));
            main.postDelayed(this::stopTone, duration * 1000L);
        } catch (Exception error) {
            stopTone();
            try { writeStatus(new JSONObject().put("error", error.toString())); } catch (Exception ignored) { }
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getWindow().getInsetsController() != null)
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
    }

    @Override public void onDestroy() { stopTone(); super.onDestroy(); }

    private void writeStatus(JSONObject value) throws Exception {
        try (FileWriter output = new FileWriter(new File(getFilesDir(), "tone-status.json"))) {
            output.write(value.toString());
        }
    }

    private void stopTone() {
        main.removeCallbacksAndMessages(null);
        if (track != null) {
            try { track.stop(); } catch (Exception ignored) { }
            try { track.release(); } catch (Exception ignored) { }
            track = null;
        }
        try { writeStatus(new JSONObject().put("uid", Process.myUid()).put("state", "stopped")
            .put("updated_elapsed_ms", SystemClock.elapsedRealtime())); } catch (Exception ignored) { }
    }
}
