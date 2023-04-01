package com.projecty4s2.pesacheck;

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
import java.util.List;

public class ObjectDetectorHelper {

    public enum Model {
        //EfficientNetDetLite0Pesa,
        //EfficientNetDetLite0PesaV2,
        EfficientNetDetLite0PesaV3
    }

    public enum Device {
        CPU,
        GPU,
        NNAPI
    }

    public static class AdditiveValue {
        int thousands = 0;
        int hundreds = 0;
        int tens = 0;
        int ones = 0;

        public AdditiveValue(int thousands, int hundreds, int tens, int ones) {
            this.thousands = thousands;
            this.hundreds = hundreds;
            this.tens = tens;
            this.ones = ones;
        }
    }

    public enum DetectionValue {
        ONE,
        FIVE,
        TEN,
        TWENTY,
        FIFTY,
        ONE_HUNDRED,
        TWO_HUNDRED,
        FIVE_HUNDRED,
        ONE_THOUSAND,
        OLD_ONE_THOUSAND
    }

    private final Context context;

    public Device currentDevice;
    public Model currentModel;


    public BigDecimal threshold;
    public int numThreads;
    public int maxResults;

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

    private void setUpObjectDetector() {
        ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder =
                ObjectDetector.ObjectDetectorOptions.builder()
                        .setScoreThreshold(threshold.floatValue())
                        .setMaxResults(maxResults);

        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads);

        switch (currentDevice) {
            case NNAPI:
                baseOptionsBuilder.useNnapi();
                break;
            case GPU:
                if (new CompatibilityList().isDelegateSupportedOnThisDevice()) {
                    baseOptionsBuilder.useGpu();
                } else {
                    objectDetectorListener.onError("GPU is not supported on this device");
                }
                break;
            default:
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build());

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(context, getModelName(currentModel), optionsBuilder.build());
        } catch (IOException e) {
            objectDetectorListener.onError("Cannot Find Model");
        } catch (IllegalStateException e) {
            objectDetectorListener.onError("Object detector failed to initialize");
            Log.e("bkmbigo.ObjectDetector", "setUpObjectDetector: TFLite failed to load model with error: " + e.getMessage());
        }
    }

    public void detect(Bitmap image, int imageRotation) {
        if (objectDetector == null) {
            setUpObjectDetector();
        }

        long inferenceTime = SystemClock.uptimeMillis();

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new Rot90Op(-imageRotation / 90))
                .build();

        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image));

        if (tensorImage == null || objectDetector == null) {
            return;
        }

        ArrayList<Detection> results = (ArrayList<Detection>) objectDetector.detect(tensorImage);
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        objectDetectorListener.onResults(results, inferenceTime, tensorImage.getHeight(), tensorImage.getWidth());

    }

    public Boolean isClosed() {
        return objectDetector == null;
    }

    public void close() {
        objectDetector = null;
    }

    @SuppressWarnings("SpellCheckingInspection")
    public String getModelName(Model model) throws IOException {
        if (model == Model.EfficientNetDetLite0PesaV3) {
            return "efficientdet_lite0_with_metadata.tflite";
        } else {
            throw new IOException("Model Not Found");
        }
    }

    public static Model getModel(int position) {
        if (position == 0) {
            return Model.EfficientNetDetLite0PesaV3;
        } else {
            return Model.EfficientNetDetLite0PesaV3;
        }
    }

    public static Device getDevice(int position) {
        switch (position) {
            case 1:
                return Device.GPU;
            case 2:
                return Device.NNAPI;
            default:
            case 0:
                return Device.CPU;
        }
    }

    public static AdditiveValue getAdditiveValue(List<String> resultValues) {
        ArrayList<DetectionValue> detectedValues = new ArrayList<>();
        for (String resultValue : resultValues) {
            detectedValues.add(getSpeechValue(resultValue));
        }

        int thousands = 0, hundreds = 0, tens = 0, ones = 0;

        for (DetectionValue detectionValue : detectedValues) {
            if (detectionValue == DetectionValue.ONE) {
                ones++;
            } else if (detectionValue == DetectionValue.FIVE) {
                ones += 5;
            } else if (detectionValue == DetectionValue.TEN) {
                tens++;
            } else if (detectionValue == DetectionValue.FIFTY) {
                tens += 5;
            } else if (detectionValue == DetectionValue.ONE_HUNDRED) {
                hundreds++;
            } else if (detectionValue == DetectionValue.TWO_HUNDRED) {
                hundreds += 2;
            } else if (detectionValue == DetectionValue.FIVE_HUNDRED) {
                hundreds += 5;
            } else if (detectionValue == DetectionValue.ONE_THOUSAND) {
                thousands++;
            }
        }

        return new AdditiveValue(
                thousands,
                hundreds,
                tens,
                ones
        );
    }

    public static DetectionValue getSpeechValue(String resultValue) {
        if (resultValue.equalsIgnoreCase("1")) {
            return DetectionValue.ONE;
        } else if (resultValue.equalsIgnoreCase("5")) {
            return DetectionValue.FIVE;
        } else if (resultValue.equalsIgnoreCase("10")) {
            return DetectionValue.TEN;
        } else if (resultValue.equalsIgnoreCase("20")) {
            return DetectionValue.TWENTY;
        } else if (resultValue.equalsIgnoreCase("50")) {
            return DetectionValue.FIFTY;
        } else if (resultValue.equalsIgnoreCase("100")) {
            return DetectionValue.ONE_HUNDRED;
        } else if (resultValue.equalsIgnoreCase("200")) {
            return DetectionValue.TWO_HUNDRED;
        } else if (resultValue.equalsIgnoreCase("500")) {
            return DetectionValue.FIVE_HUNDRED;
        } else if (resultValue.equalsIgnoreCase("1000")) {
            return DetectionValue.ONE_THOUSAND;
        } else if (resultValue.equalsIgnoreCase("old 1000")) {
            return DetectionValue.OLD_ONE_THOUSAND;
        }
        throw new IllegalStateException("Result Value got not in [1,5,10,20,50,100,200,500,1000]");
    }

    public static String getSpeechValue(DetectionValue resultValue) {
        if (resultValue == DetectionValue.ONE) {
            return "one";
        } else if (resultValue == DetectionValue.FIVE) {
            return "five";
        } else if (resultValue == DetectionValue.TEN) {
            return "ten";
        } else if (resultValue == DetectionValue.TWENTY) {
            return "twenty";
        } else if (resultValue == DetectionValue.FIFTY) {
            return "fifty";
        } else if (resultValue == DetectionValue.ONE_HUNDRED) {
            return "one hundred";
        } else if (resultValue == DetectionValue.TWO_HUNDRED) {
            return "two hundred";
        } else if (resultValue == DetectionValue.FIVE_HUNDRED) {
            return "five hundred";
        } else if (resultValue == DetectionValue.ONE_THOUSAND) {
            return "one thousand";
        } else if (resultValue == DetectionValue.OLD_ONE_THOUSAND) {
            return "old one thousand";
        }
        throw new IllegalStateException("Result Value got not in [1,5,10,20,50,100,200,500,1000]");
    }

    public static String getSpeechValue(AdditiveValue resultValue) {
        String speechValue = "";

        if (resultValue.thousands > 0) {
            speechValue = speechValue + " " + resultValue.thousands + " thousand";
        }

        if (resultValue.hundreds > 0) {
            speechValue = speechValue + " " + resultValue.hundreds + " hundred";
        }

        if (resultValue.tens > 0) {
            speechValue = speechValue + " and";


            if (resultValue.tens == 1) {
                if (resultValue.ones == 0) {
                    speechValue = speechValue + " ten";
                } else if (resultValue.ones == 1) {
                    speechValue = speechValue + " eleven";
                } else if (resultValue.ones == 2) {
                    speechValue = speechValue + " twelve";
                } else if (resultValue.ones == 3) {
                    speechValue = speechValue + " thirteen";
                } else if (resultValue.ones == 4) {
                    speechValue = speechValue + " fourteen";
                } else if (resultValue.ones == 5) {
                    speechValue = speechValue + " fifteen";
                } else if (resultValue.ones == 6) {
                    speechValue = speechValue + " sixteen";
                } else if (resultValue.ones == 7) {
                    speechValue = speechValue + " seventeen";
                } else if (resultValue.ones == 8) {
                    speechValue = speechValue + " eighteen";
                } else if (resultValue.ones == 9) {
                    speechValue = speechValue + " nineteen";
                }
            } else {
                if (resultValue.tens == 2) {
                    speechValue = speechValue + " twenty";
                } else if (resultValue.tens == 3) {
                    speechValue = speechValue + " thirty";
                } else if (resultValue.tens == 4) {
                    speechValue = speechValue + " forty";
                } else if (resultValue.tens == 5) {
                    speechValue = speechValue + " fifty";
                } else if (resultValue.tens == 6) {
                    speechValue = speechValue + " sixty";
                } else if (resultValue.tens == 7) {
                    speechValue = speechValue + " seventy";
                } else if (resultValue.tens == 8) {
                    speechValue = speechValue + " eighty";
                } else if (resultValue.tens == 9) {
                    speechValue = speechValue + " ninety";
                }

                if (resultValue.ones == 1) {
                    speechValue = speechValue + " one";
                } else if (resultValue.ones == 2) {
                    speechValue = speechValue + " two";
                } else if (resultValue.ones == 3) {
                    speechValue = speechValue + " three";
                } else if (resultValue.ones == 4) {
                    speechValue = speechValue + " four";
                } else if (resultValue.ones == 5) {
                    speechValue = speechValue + " five";
                } else if (resultValue.ones == 6) {
                    speechValue = speechValue + " six";
                } else if (resultValue.ones == 7) {
                    speechValue = speechValue + " seven";
                } else if (resultValue.ones == 8) {
                    speechValue = speechValue + " eight";
                } else if (resultValue.ones == 9) {
                    speechValue = speechValue + " nine";
                }

            }
            speechValue = speechValue + " " + resultValue.thousands + " thousand";
        } else {

            if (resultValue.ones > 0) {
                speechValue = speechValue + " and ";

                if (resultValue.ones == 1) {
                    speechValue = speechValue + " one";
                } else if (resultValue.ones == 2) {
                    speechValue = speechValue + " two";
                } else if (resultValue.ones == 3) {
                    speechValue = speechValue + " three";
                } else if (resultValue.ones == 4) {
                    speechValue = speechValue + " four";
                } else if (resultValue.ones == 5) {
                    speechValue = speechValue + " five";
                } else if (resultValue.ones == 6) {
                    speechValue = speechValue + " six";
                } else if (resultValue.ones == 7) {
                    speechValue = speechValue + " seven";
                } else if (resultValue.ones == 8) {
                    speechValue = speechValue + " eight";
                } else if (resultValue.ones == 9) {
                    speechValue = speechValue + " nine";
                }
            }
        }

        return speechValue;
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
