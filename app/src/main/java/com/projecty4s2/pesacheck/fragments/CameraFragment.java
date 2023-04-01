package com.projecty4s2.pesacheck.fragments;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.projecty4s2.pesacheck.ObjectDetectorHelper;
import com.projecty4s2.pesacheck.R;
import com.projecty4s2.pesacheck.SpeechHelper;
import com.projecty4s2.pesacheck.databinding.FragmentCameraBinding;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CameraFragment extends Fragment implements ObjectDetectorHelper.DetectorListener {

    private FragmentCameraBinding fragmentCameraBinding;

    public static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    private BigDecimal threshold;
    private int numThreads = 2;
    private int maxResults;
    private ObjectDetectorHelper.Device device;
    private ObjectDetectorHelper.Model model;
    private SpeechHelper.SpeechLanguage speechLanguage;
    private SpeechHelper.FeedbackMode feedbackMode;
    private float speechThreshold;


    private Bitmap bitmapBuffer = null;

    private Preview preview = null;

    private MaterialButton btGrantPermissions;
    private ImageAnalysis imageAnalyzer = null;
    private Camera camera = null;
    private ProcessCameraProvider cameraProvider = null;

    private ObjectDetectorHelper objectDetectorHelper;
    private SpeechHelper speechHelper;
    private ExecutorService cameraExecutor;

    private int LENS_FACING;
    private boolean hasFlashLight = false, isTorchOn = false, isAdditive = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        //Getting Default Camera as saved by user
        if (!sharedPreferences.getString("default_camera", "1").equals("1")) {
            fragmentCameraBinding.ChangeCameraButton.setText("USE BACK CAMERA");
            LENS_FACING = CameraSelector.LENS_FACING_FRONT;
        } else {
            fragmentCameraBinding.ChangeCameraButton.setText("USE FRONT CAMERA");
            LENS_FACING = CameraSelector.LENS_FACING_BACK;
        }

        // Getting additive mode
        isAdditive = !sharedPreferences.getString("additive_mode", "1").equals("1");

        btGrantPermissions = fragmentCameraBinding.permFragBtGrantPermission;
        btGrantPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissionLauncher.launch(PERMISSION_CAMERA);
            }
        });

        //Getting
        String defaultDevice = sharedPreferences.getString("inference_device", "1");

        if (defaultDevice.equals("2")) {
            device = ObjectDetectorHelper.Device.GPU;
        } else if (defaultDevice.equals("3")) {
            device = ObjectDetectorHelper.Device.NNAPI;
        } else {
            device = ObjectDetectorHelper.Device.CPU;
        }

        String defaultModel = sharedPreferences.getString("inference_model", "1");

        if (defaultModel.equals("1")) {
            model = ObjectDetectorHelper.Model.EfficientNetDetLite0PesaV3;
        }

        if (sharedPreferences.getString("speech_language", "1").equals("2")) {
            speechLanguage = SpeechHelper.SpeechLanguage.KISWAHILI;
        } else {
            speechLanguage = SpeechHelper.SpeechLanguage.ENGLISH;
        }

        String defaultFeedbackMode = sharedPreferences.getString("feedback_mode", "1");

        if (defaultFeedbackMode.equals("2")) {
            feedbackMode = SpeechHelper.FeedbackMode.VIBRATION;
        } else if (defaultFeedbackMode.equals("3")) {
            feedbackMode = SpeechHelper.FeedbackMode.SPEECH_AND_VIBRATION;
        } else {
            feedbackMode = SpeechHelper.FeedbackMode.SPEECH;
        }

        threshold = BigDecimal.valueOf(Integer.parseInt(sharedPreferences.getString("display_threshold", "1"))).divide(BigDecimal.valueOf(100));

        maxResults = Integer.parseInt(sharedPreferences.getString("max_display_results", "1"));
        ;

        speechThreshold = BigDecimal.valueOf(Integer.parseInt(sharedPreferences.getString("speech_threshold", "1"))).divide(BigDecimal.valueOf(100)).floatValue();
        return fragmentCameraBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().getWindow().setStatusBarColor(Color.parseColor("#3322FF22"));

        objectDetectorHelper = new ObjectDetectorHelper(
                requireContext(), device, model, threshold, numThreads, maxResults, this);

        speechHelper = new SpeechHelper(requireActivity(), speechLanguage, feedbackMode);

        cameraExecutor = Executors.newSingleThreadExecutor();

        fragmentCameraBinding.ChangeCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (LENS_FACING == CameraSelector.LENS_FACING_BACK) {
                    fragmentCameraBinding.ChangeCameraButton.setText(R.string.use_back_camera);
                    LENS_FACING = CameraSelector.LENS_FACING_FRONT;
                } else {
                    fragmentCameraBinding.ChangeCameraButton.setText(R.string.use_front_camera);
                    LENS_FACING = CameraSelector.LENS_FACING_BACK;
                }
                changeCamera();
            }
        });

        fragmentCameraBinding.ToggleTorch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasFlashLight) {
                    if (!isTorchOn) {
                        if (camera != null) {
                            camera.getCameraControl().enableTorch(true);
                            isTorchOn = true;
                            fragmentCameraBinding.ToggleTorch.setText(R.string.turn_off_torch);
                        }
                    } else {
                        if (camera != null) {
                            camera.getCameraControl().enableTorch(false);
                            isTorchOn = false;
                            fragmentCameraBinding.ToggleTorch.setText(R.string.turn_on_torch);
                        }
                    }
                }
            }
        });

        setUpLayout(ContextCompat.checkSelfPermission(requireContext(), PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED);

        // CLear Object Detector
        objectDetectorHelper.close();
        fragmentCameraBinding.overlay.clear();
    }

    private void setUpLayout(Boolean permissionGranted) {
        if (permissionGranted) {
            fragmentCameraBinding.permissionLayout.setVisibility(View.GONE);
            fragmentCameraBinding.cameraLayout.setVisibility(View.VISIBLE);
            fragmentCameraBinding.viewFinder.post(new Runnable() {
                @Override
                public void run() {
                    setUpCamera();
                }
            });
        } else {
            fragmentCameraBinding.permissionLayout.setVisibility(View.VISIBLE);
            fragmentCameraBinding.cameraLayout.setVisibility(View.GONE);
        }
    }

    private void setUpCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }

                bindCameraUseCases();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            throw new IllegalStateException("Camera Initialization Failed");
        }

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(LENS_FACING).build();

        preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.getDisplay().getRotation())
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.getDisplay().getRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (bitmapBuffer == null) {
                    bitmapBuffer = Bitmap.createBitmap(
                            image.getWidth(),
                            image.getHeight(),
                            Bitmap.Config.ARGB_8888
                    );
                }
                detectObjects(image);
                image.close();
            }
        });

        cameraProvider.unbindAll();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            hasFlashLight = camera.getCameraInfo().hasFlashUnit();
            fragmentCameraBinding.ToggleTorch.setEnabled(hasFlashLight);

            preview.setSurfaceProvider(fragmentCameraBinding.viewFinder.getSurfaceProvider());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void detectObjects(ImageProxy image) {
        bitmapBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

        int imageRotation = image.getImageInfo().getRotationDegrees();

        objectDetectorHelper.detect(bitmapBuffer, imageRotation);
    }

    private void changeCamera() {
        cameraProvider.unbindAll();

        fragmentCameraBinding.viewFinder.post(new Runnable() {
            @Override
            public void run() {
                setUpCamera();
            }
        });
    }

    private ObjectDetectorHelper.AdditiveValue getAdditiveSpeechValue(List<Detection> results) {
        List<Detection> results_copy = new ArrayList<>(results);
        Collections.sort(results_copy, new Comparator<Detection>() {
            @Override
            public int compare(Detection lhs, Detection rhs) {
                if (lhs.getCategories().get(0).getScore() != rhs.getCategories().get(0).getScore()) {
                    if (lhs.getCategories().get(0).getScore() > rhs.getCategories().get(0).getScore()) {
                        return 1;
                    } else {
                        return -1;
                    }
                } else {
                    return 0;
                }
            }
        });

        List<String> confident_results = new ArrayList<>();

        if (results_copy.size() != 0) {
            for (Detection result : results) {
                if (result.getCategories().get(0).getScore() > speechThreshold) {
                    confident_results.add(result.getCategories().get(0).getLabel());
                }
            }
        }

        return ObjectDetectorHelper.getAdditiveValue(confident_results);
    }

    private ObjectDetectorHelper.DetectionValue getSpeechValue(List<Detection> results) {
        List<Detection> results_copy = new ArrayList<>(results);
        Collections.sort(results_copy, new Comparator<Detection>() {
            @Override
            public int compare(Detection lhs, Detection rhs) {
                if (lhs.getCategories().get(0).getScore() != rhs.getCategories().get(0).getScore()) {
                    if (lhs.getCategories().get(0).getScore() > rhs.getCategories().get(0).getScore()) {
                        return 1;
                    } else {
                        return -1;
                    }
                } else {
                    return 0;
                }
            }
        });

        if (results_copy.size() != 0) {
            Detection mostConfidentResult = results_copy.get(0);
            if (mostConfidentResult.getCategories().get(0).getScore() >= speechThreshold) {
                return ObjectDetectorHelper.getSpeechValue(mostConfidentResult.getCategories().get(0).getLabel());
            }
        }
        return null;
    }

    private void speech(List<Detection> results) {

        if (isAdditive) {
            ObjectDetectorHelper.AdditiveValue additiveValue = getAdditiveSpeechValue(results);
            speechHelper.speak(additiveValue);
        } else {
            ObjectDetectorHelper.DetectionValue detectionValue = getSpeechValue(results);
            if (detectionValue != null) {
                speechHelper.speak(detectionValue);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        imageAnalyzer.setTargetRotation(fragmentCameraBinding.viewFinder.getDisplay().getRotation());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (speechHelper.isClosed()) {
            speechHelper = new SpeechHelper(requireActivity(), speechLanguage, feedbackMode);
        }
        if (objectDetectorHelper.isClosed()) {
            objectDetectorHelper = new ObjectDetectorHelper(
                    requireContext(), device, model, threshold, numThreads, maxResults, this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        speechHelper.close();
        objectDetectorHelper.close();
    }

    @Override
    public void onError(String error) {
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(requireActivity(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResults(ArrayList<Detection> results, long inferenceTime, int imageHeight, int imageWidth) {
        try {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //fragmentCameraBinding.bottomSheetLayout.bslTvInference.setText(String.format("%d ms", inferenceTime));

                    fragmentCameraBinding.overlay.setResults(
                            results,
                            imageHeight,
                            imageWidth
                    );

                    speech(results);

                    fragmentCameraBinding.overlay.invalidate();
                }
            });
        } catch (IllegalStateException ignored) {
        }
    }

    final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean result) {
                    if (result) {
                        Toast.makeText(requireActivity(), "Permission Granted", Toast.LENGTH_SHORT).show();
                        setUpLayout(true);
                    } else {
                        Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                        setUpLayout(false);
                    }
                }
            });
}
