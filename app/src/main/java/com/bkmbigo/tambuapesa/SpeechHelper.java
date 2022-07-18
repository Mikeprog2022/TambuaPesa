package com.bkmbigo.tambuapesa;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SpeechHelper implements TextToSpeech.OnInitListener{

    private final Activity activity;
    private final SpeechLanguage speechLanguage;
    private FeedbackMode feedbackMode;

    private TextToSpeech textToSpeech;

    private MediaPlayer mediaPlayer50, mediaPlayer100, mediaPlayer200, mediaPlayer500, mediaPlayer1000;
    private Vibrator vibrator;
    private boolean isVibrating = false;


    @SuppressWarnings("SpellCheckingInspection")
    public enum SpeechLanguage{
        ENGLISH,
        KISWAHILI
    }

    public enum FeedbackMode{
        SPEECH,
        VIBRATION,
        SPEECH_AND_VIBRATION
    }

    public SpeechHelper(Activity activity, SpeechLanguage speechLanguage, FeedbackMode feedbackMode) {
        this.activity = activity;
        this.feedbackMode = feedbackMode;
        //Volume Controls should affect Media Volume Controls
        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        int currentMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        if(feedbackMode != FeedbackMode.VIBRATION) {
            if (currentMediaVolume == 0) {
                Toast.makeText(activity, "Media Volume is Zero. Using Vibrations", Toast.LENGTH_SHORT).show();
                this.feedbackMode = FeedbackMode.VIBRATION;
            }
        }

        this.speechLanguage = speechLanguage;

        if(this.feedbackMode != FeedbackMode.VIBRATION) {
            if (speechLanguage == SpeechLanguage.ENGLISH) {
                textToSpeech = new TextToSpeech(activity, this);
            } else {
                mediaPlayer1000 = MediaPlayer.create(activity, R.raw.elfu_moja);
                mediaPlayer500 = MediaPlayer.create(activity, R.raw.mia_tano);
                mediaPlayer200 = MediaPlayer.create(activity, R.raw.mia_mbili);
                mediaPlayer100 = MediaPlayer.create(activity, R.raw.mia_moja);
                mediaPlayer50 = MediaPlayer.create(activity, R.raw.hamsini);
            }
        }

        if(this.feedbackMode != FeedbackMode.SPEECH) {
            vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        }

    }

    public void speak(ObjectDetectorHelper.DetectionValue result){

        if(feedbackMode != FeedbackMode.VIBRATION) {
            if (speechLanguage == SpeechLanguage.ENGLISH) {
                if (!textToSpeech.isSpeaking()) {
                    textToSpeech.speak(ObjectDetectorHelper.getDetectionValues(result), TextToSpeech.QUEUE_FLUSH, null, ObjectDetectorHelper.getDetectionValues(result));
                }
            } else {
                if (!isPlaying()) {
                    if (result == ObjectDetectorHelper.DetectionValue.ONE_THOUSAND) {
                        mediaPlayer1000.start();
                    } else if (result == ObjectDetectorHelper.DetectionValue.FIVE_HUNDRED) {
                        mediaPlayer500.start();
                    } else if (result == ObjectDetectorHelper.DetectionValue.TWO_HUNDRED) {
                        mediaPlayer200.start();
                    } else if (result == ObjectDetectorHelper.DetectionValue.ONE_HUNDRED) {
                        mediaPlayer100.start();
                    } else if (result == ObjectDetectorHelper.DetectionValue.FIFTY) {
                        mediaPlayer50.start();
                    }
                }
            }
        }

        if(feedbackMode != FeedbackMode.SPEECH){
            vibrate(result);
        }

    }

    private void vibrate(ObjectDetectorHelper.DetectionValue result){

        int timing = 3000;

        Runnable vibratingRunnable = new Runnable() {
            @Override
            public void run() {
                isVibrating = false;
            }
        };

        if(!isVibrating) {
            isVibrating = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (result == ObjectDetectorHelper.DetectionValue.FIFTY) {
                    long[] pattern = {0, 200, 100, 500};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else if (result == ObjectDetectorHelper.DetectionValue.ONE_HUNDRED) {
                    long[] pattern = {0, 200, 100, 200, 100, 200};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else if (result == ObjectDetectorHelper.DetectionValue.TWO_HUNDRED) {
                    long[] pattern = {0, 200, 100, 200, 100, 500};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else if (result == ObjectDetectorHelper.DetectionValue.FIVE_HUNDRED) {
                    long[] pattern = {0, 200, 100, 200, 100, 1000};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else if (result == ObjectDetectorHelper.DetectionValue.ONE_THOUSAND) {
                    long[] pattern = {0, 200, 100, 200, 100, 200, 100, 500};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            } else {
                if (result == ObjectDetectorHelper.DetectionValue.FIFTY) {
                    vibrator.vibrate(100);
                } else if (result == ObjectDetectorHelper.DetectionValue.ONE_HUNDRED) {
                    vibrator.vibrate(350);
                } else if (result == ObjectDetectorHelper.DetectionValue.TWO_HUNDRED) {
                    vibrator.vibrate(500);
                } else if (result == ObjectDetectorHelper.DetectionValue.FIVE_HUNDRED) {
                    vibrator.vibrate(1000);
                } else if (result == ObjectDetectorHelper.DetectionValue.ONE_THOUSAND) {
                    vibrator.vibrate(200);
                    vibrator.vibrate(1000);
                }
            }

            try {
                new ScheduledThreadPoolExecutor(1).schedule(vibratingRunnable, timing, TimeUnit.MILLISECONDS);
            }catch (RejectedExecutionException ignored){}
        }




    }

    @Override
    public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS){
            int langSuccess = textToSpeech.setLanguage(Locale.ENGLISH);
            if(langSuccess == TextToSpeech.LANG_NOT_SUPPORTED || langSuccess == TextToSpeech.LANG_MISSING_DATA){
                Toast.makeText(activity, "English Not Supported on Device!", Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(activity, "Text To Speech Successfully initiated", Toast.LENGTH_SHORT).show();
            }
        }else{
            Toast.makeText(activity, "Error while initializing Text To Speech", Toast.LENGTH_SHORT).show();
        }
    }



    private boolean isPlaying(){
        return (mediaPlayer50.isPlaying() || mediaPlayer100.isPlaying() ||
                mediaPlayer200.isPlaying() || mediaPlayer500.isPlaying() ||
                mediaPlayer1000.isPlaying());
    }

    public boolean isClosed(){
        if(feedbackMode != FeedbackMode.VIBRATION){
            if(speechLanguage == SpeechLanguage.KISWAHILI) {
                return (mediaPlayer50 == null || mediaPlayer100 == null ||
                        mediaPlayer200 == null || mediaPlayer500 == null ||
                        mediaPlayer1000 == null);
            }
        }
        return true;
    }

    public void close(){
        if(feedbackMode != FeedbackMode.VIBRATION) {
            if (speechLanguage == SpeechLanguage.KISWAHILI) {
                mediaPlayer50.release();
                mediaPlayer100.release();
                mediaPlayer200.release();
                mediaPlayer500.release();
                mediaPlayer1000.release();

                mediaPlayer50 = null;
                mediaPlayer100 = null;
                mediaPlayer200 = null;
                mediaPlayer500 = null;
                mediaPlayer1000 = null;
            }
        }
    }

}
