package ai.snips.snipsdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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
import java.util.ArrayList;

import ai.snips.hermes.IntentMessage;
import ai.snips.hermes.SessionEndedMessage;
import ai.snips.hermes.SessionQueuedMessage;
import ai.snips.hermes.SessionStartedMessage;
import ai.snips.platform.SnipsPlatformClient;
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
    private SnipsPlatformClient client;

    private AudioRecord recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ensurePermissions();

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ensurePermissions()) {
                    final Button button = (Button) findViewById(R.id.start);
                    button.setEnabled(false);
                    button.setText(R.string.loading);

                    final View scrollView = findViewById(R.id.scrollView);
                    scrollView.setVisibility(View.GONE);

                    final View loadingPanel = findViewById(R.id.loadingPanel);
                    loadingPanel.setVisibility(View.VISIBLE);

                    startMegazordService();


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
        if (client == null) {
            // a dir where the assistant models was unziped. it should contain the folders asr dialogue hotword and nlu
            File assistantDir = new File(Environment.getExternalStorageDirectory()
                                                    .toString(), "snips_android_assistant");

            client = new SnipsPlatformClient.Builder(assistantDir)
                    .enableDialogue(true) // defaults to true
                    .enableHotword(true) // defaults to true
                    .enableSnipsWatchHtml(true) // defaults to false
                    .enableLogs(true) // defaults to false
                    .withHotwordSensitivity(0.5f) // defaults to 0.5
                    .enableStreaming(true) // defaults to false
                    .build();

            client.setOnPlatformReady(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                            findViewById(R.id.scrollView).setVisibility(View.VISIBLE);

                            final Button button = findViewById(R.id.start);
                            button.setEnabled(true);
                            button.setText(R.string.start_dialog_session);
                            button.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    // programmatically start a dialogue session
                                    client.startSession(null, new ArrayList<String>(), false, null);
                                }
                            });
                        }
                    });
                    return null;
                }
            });

            client.setOnHotwordDetectedListener(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    Log.d(TAG, "an hotword was detected !");
                    // Do your magic here :D
                    return null;
                }
            });

            client.setOnIntentDetectedListener(new Function1<IntentMessage, Unit>() {
                @Override
                public Unit invoke(IntentMessage intentMessage) {
                    Log.d(TAG, "received an intent: " + intentMessage);
                    // Do your magic here :D

                    client.endSession(intentMessage.getSessionId(), null);
                    return null;
                }
            });

            client.setOnListeningStateChangedListener(new Function1<Boolean, Unit>() {
                @Override
                public Unit invoke(Boolean isListening) {
                    Log.d(TAG, "asr listening state: " + isListening);
                    // Do you magic here :D
                    return null;
                }
            });

            client.setOnSessionStartedListener(new Function1<SessionStartedMessage, Unit>() {
                @Override
                public Unit invoke(SessionStartedMessage sessionStartedMessage) {
                    Log.d(TAG, "dialogue session started: " + sessionStartedMessage);
                    return null;
                }
            });

            client.setOnSessionQueuedListener(new Function1<SessionQueuedMessage, Unit>() {
                @Override
                public Unit invoke(SessionQueuedMessage sessionQueuedMessage) {
                    Log.d(TAG, "dialogue session queued: " + sessionQueuedMessage);
                    return null;
                }
            });

            client.setOnSessionEndedListener(new Function1<SessionEndedMessage, Unit>() {
                @Override
                public Unit invoke(SessionEndedMessage sessionEndedMessage) {
                    Log.d(TAG, "dialogue session ended: " + sessionEndedMessage);
                    return null;
                }
            });

            // This api is really for debugging purposes and you should not have features depending on its output
            // If you need us to expose more APIs please do ask !
            client.setOnSnipsWatchListener(new Function1<String, Unit>() {
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

            client.connect(this.getApplicationContext()); // no way to stop it yet, coming soon
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
            if (client != null) {
                client.sendAudioBuffer(buffer);
            }
        }
    }
}
