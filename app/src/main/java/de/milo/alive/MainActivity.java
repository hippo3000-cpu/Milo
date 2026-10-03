package de.milo.alive;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Window;

import java.util.Locale;

public class MainActivity extends Activity {
    private TextToSpeech tts;
    private MiloView miloView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.GERMAN);
                tts.setPitch(1.12f);
                tts.setSpeechRate(0.95f);
            }
        });

        miloView = new MiloView(this, this::speak);
        setContentView(miloView);
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "milo");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (miloView != null) {
            miloView.setPaused(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miloView != null) {
            miloView.setPaused(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
