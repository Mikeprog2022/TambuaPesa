package com.bkmbigo.tambuapesa;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;

public class ObjectDetectorHelper {

    public enum Model{
        MobileNetV1,
        EfficientNetDetLite0,
        EfficientNetDetLite1,
        EfficientNetDetLite2,
        EfficientNetDetLite4,
        EfficientNetDetLite0Pesa
    }

    public enum Device{
        CPU,
        GPU,
        NNAPI
    }

    private final Context context;

    public Device currentDevice;
    public Model currentModel;


    public BigDecimal threshold;
    public int numThreads = 2;
    public int maxResults = 5;

    private final DetectorListener objectDetectorListener;

    private ObjectDetector objectDetector;

    public ObjectDetectorHelper(Context context, Device currentDevice, Model currentModel, BigDecimal threshold, int numThreads, int maxResults, DetectorListener objectDetectorListener) {
        this.context = context;
        this.currentDevice = currentDevice;
        this.currentModel = currentModel;
        this.threshold = threshold;
        this.numThreads = numThreads;
        this.maxResults = maxResults;
        this.objectDetectorListener = objectDetectorListener;
    }

    private void setUpObjectDetector(){
        ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder =
                ObjectDetector.ObjectDetectorOptions.builder()
                        .setScoreThreshold(threshold.floatValue())
                        .setMaxResults(maxResults);

        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads);

        switch (currentDevice){
            case NNAPI:
                baseOptionsBuilder.useNnapi();
                break;
            case GPU:
                if(new CompatibilityList().isDelegateSupportedOnThisDevice()){
                    baseOptionsBuilder.useGpu();
                }else{
                    objectDetectorListener.onError("GPU is not supported on this device");
                }
                break;
            default:
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build());

        try{
            objectDetector = ObjectDetector.createFromFileAndOptions(context, getModelName(currentModel), optionsBuilder.build());
        } catch (IOException e) {
            objectDetectorListener.onError("Cannot Find Model");
        } catch (IllegalStateException e){
            objectDetectorListener.onError("Object detector failed to initialize");
            Log.e("bkmbigo.ObjectDetector", "setUpObjectDetector: TFLite failed to load model with error: " + e.getMessage());
        }
    }

    public void detect(Bitmap image, int imageRotation){
        if(objectDetector == null){
            setUpObjectDetector();
        }

        long inferenceTime  = SystemClock.uptimeMillis();

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new Rot90Op(-imageRotation / 90))
                .build();

        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image));

        if(tensorImage == null || objectDetector == null){
            return;
        }

        ArrayList<Detection> results = (ArrayList<Detection>) objectDetector.detect(tensorImage);
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        objectDetectorListener.onResults(results, inferenceTime, tensorImage.getHeight(), tensorImage.getWidth());

    }

    public void close(){
        objectDetector = null;
    }

    public String getModelName(Model model) throws IOException {
        switch(model){
            case MobileNetV1:
                return "mobilenetv1.tflite";
            case EfficientNetDetLite0:
                return "efficientdet-lite0.tflite";
            case EfficientNetDetLite1:
                return "efficientdet-lite1.tflite";
            case EfficientNetDetLite2:
                return "efficientdet-lite2.tflite";
            case EfficientNetDetLite4:
                return "efficientdet-lite4.tflite";
            case EfficientNetDetLite0Pesa:
                return "efficientdet_lite0_Pesa.tflite";
            default:
                throw new IOException("Model Not Found");
        }
    }

    public static Model getModel(int position){
        switch (position){
            case 1:
                return Model.EfficientNetDetLite0;
            case 2:
                return Model.EfficientNetDetLite1;
            case 3:
                return Model.EfficientNetDetLite2;
            case 4:
                return Model.EfficientNetDetLite4;
            case 5:
                return Model.EfficientNetDetLite0Pesa;
            case 0:
            default:
                return Model.MobileNetV1;
        }

    }

    public static Device getDevice(int position){
        switch (position){
            case 1:
                return Device.GPU;
            case 2:
                return Device.NNAPI;
            default:
            case 0:
                return Device.CPU;
        }
    }


    public interface DetectorListener {
        void onError(String error);
        void onResults(
                ArrayList<Detection> results,
                long inferenceTime,
                int imageHeight,
                int imageWidth
        );
    }
}
