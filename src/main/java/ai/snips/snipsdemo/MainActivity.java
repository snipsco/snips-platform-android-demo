package ai.snips.snipsdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;

import java.io.File;

import ai.snips.hermes.IntentMessage;
import ai.snips.hermes.SessionEndedMessage;
import ai.snips.hermes.SessionQueuedMessage;
import ai.snips.hermes.SessionStartedMessage;
import ai.snips.megazord.Megazord;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_ECHO_REQUEST = 0;
    private static final String TAG = "MainActivity";

    private static final int FREQUENCY = 16_000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // Snips platform codename for android port is Megazord
    private static Megazord megazord;

    private AudioRecord recorder;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ensurePermissions();
        handler = new Handler();

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ensurePermissions()) {
                    final Button button = (Button) findViewById(R.id.start);
                    button.setEnabled(false);
                    button.setText(R.string.loading);

                    final View scrollView = findViewById(R.id.scrollView);
                    scrollView.setVisibility(View.GONE);

                    new Thread() {
                        public void run() {
                            startMegazordService();
                        }
                    }.start();

                    // Leave a litle bit of time for the platform to come up. We will provide dedicated methods for
                    // that in the future, for now, just wait 30s, this is VERY conservative if you're on a recent phone
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            button.setEnabled(true);
                            button.setText(R.string.start_dialog_session);
                            button.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    // programmatically start a dialogue session
                                    megazord.startSession("Hello", null, false, null);
                                }
                            });
                            scrollView.setVisibility(View.VISIBLE);
                        }
                    }, 30000);
                }
            }
        });
    }

    private boolean ensurePermissions() {
        int status = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);
        if (status != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, AUDIO_ECHO_REQUEST);
            return false;
        }
        return true;
    }

    private void startMegazordService() {
        if (megazord == null) {
            // a dir where the assistant models was unziped. it should contain the folders asr dialogue hotword and nlu
            File assistantDir = new File(Environment.getExternalStorageDirectory().toString(), "snips_android_assistant");

            megazord = Megazord.builder(assistantDir)
                               .enableDialogue(true) // defaults to true
                               .enableHotword(true) // defaults to true
                               .enableSnipsWatchHtml(true) // defaults to false
                               .enableLogs(true) // defaults to false
                               .withHotwordSensitivity(0.5f) // defaults to 0.5
                               .enableStreaming(true) // defaults to false
                               .build();

            megazord.setOnHotwordDetectedListener(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    Log.d(TAG, "an hotword was detected !");
                    // Do your magic here :D
                    return null;
                }
            });

            megazord.setOnIntentDetectedListener(new Function1<IntentMessage, Unit>() {
                @Override
                public Unit invoke(IntentMessage intentMessage) {
                    Log.d(TAG, "received an intent: " + intentMessage);
                    // Do your magic here :D
                    return null;
                }
            });

            megazord.setOnListeningStateChangedListener(new Function1<Boolean, Unit>() {
                @Override
                public Unit invoke(Boolean isListening) {
                    Log.d(TAG, "asr listening state: " + isListening);
                    // Do you magic here :D
                    return null;
                }
            });

            megazord.setOnSessionStartedListener(new Function1<SessionStartedMessage, Unit>() {
                @Override
                public Unit invoke(SessionStartedMessage sessionStartedMessage) {
                    Log.d(TAG, "dialogue session started: " + sessionStartedMessage);
                    return null;
                }
            });

            megazord.setOnSessionQueuedListener(new Function1<SessionQueuedMessage, Unit>() {
                @Override
                public Unit invoke(SessionQueuedMessage sessionQueuedMessage) {
                    Log.d(TAG, "dialogue session queued: " + sessionQueuedMessage);
                    return null;
                }
            });

            megazord.setOnSessionEndedListener(new Function1<SessionEndedMessage, Unit>() {
                @Override
                public Unit invoke(SessionEndedMessage sessionEndedMessage) {
                    Log.d(TAG, "dialogue session ended: " + sessionEndedMessage);
                    return null;
                }
            });

            // This api is really for debugging purposes and you should not have features depending on its output
            // If you need us to expose more APIs please do ask !
            megazord.setOnSnipsWatchListener(new Function1<String, Unit>() {
                public Unit invoke(final String s) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // We enabled html logs in the builder, hence the fromHtml. If you only log to the console,
                            // or don't want colors to be displayed, do not enable the option
                            ((EditText) findViewById(R.id.text)).append(Html.fromHtml(s + "<br />"));
                            findViewById(R.id.scrollView).post(new Runnable() {
                                @Override
                                public void run() {
                                    ((ScrollView) findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
                                }
                            });
                        }
                    });
                    return null;
                }
            });

            // We enabled steaming in the builder, so we need to provide the platform an audio stream. If you don't want
            // to manage the audio stream do no enable the option, and the snips platform will grab the mic by itself
            new Thread() {
                public void run() {
                    runStreaming();
                }
            }.start();

            megazord.start(); // no way to stop it yet, coming soon
        }
    }

    private void runStreaming() {
        final int minBufferSizeInBytes = AudioRecord.getMinBufferSize(FREQUENCY, CHANNEL, ENCODING);
        Log.d(TAG, "minBufferSizeInBytes: " + minBufferSizeInBytes);

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, FREQUENCY, CHANNEL, ENCODING, minBufferSizeInBytes);
        recorder.startRecording();

        // In a non demo app, you want to have a way to stop this :)
        while (true) {
            short[] buffer = new short[minBufferSizeInBytes / 2];
            recorder.read(buffer, 0, buffer.length);
            if (megazord != null) {
                megazord.sendAudioBuffer(buffer);
            }
        }
    }
}
