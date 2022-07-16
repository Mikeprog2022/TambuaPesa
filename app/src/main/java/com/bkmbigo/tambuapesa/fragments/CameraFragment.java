package com.bkmbigo.tambuapesa.fragments;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

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
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.bkmbigo.tambuapesa.ObjectDetectorHelper;
import com.bkmbigo.tambuapesa.R;
import com.bkmbigo.tambuapesa.databinding.FragmentCameraBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CameraFragment extends Fragment implements ObjectDetectorHelper.DetectorListener{

    private FragmentCameraBinding fragmentCameraBinding;

    private BigDecimal threshold;
    private int numThreads = 2;
    private int maxResults = 5;
    private ObjectDetectorHelper.Device device;
    private ObjectDetectorHelper.Model model = ObjectDetectorHelper.Model.MobileNetV1;

    private Bitmap bitmapBuffer = null;

    private Preview preview = null;
    private ImageAnalysis imageAnalyzer = null;
    private Camera camera = null;
    private ProcessCameraProvider cameraProvider = null;

    private ObjectDetectorHelper objectDetectorHelper;
    private ExecutorService cameraExecutor;

    private int LENS_FACING;
    private boolean hasFlashLight = false, isTorchOn = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container,false);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        if(!sharedPreferences.getString("default_camera", "1").equals("1")) {
            fragmentCameraBinding.ChangeCameraButton.setText("USE BACK CAMERA");
            LENS_FACING = CameraSelector.LENS_FACING_FRONT;
        }else{
            fragmentCameraBinding.ChangeCameraButton.setText("USE FRONT CAMERA");
            LENS_FACING = CameraSelector.LENS_FACING_BACK;
        }

        String defaultDevice = sharedPreferences.getString("inference_device", "1");

        if(defaultDevice.equals("2")) {
            device = ObjectDetectorHelper.Device.GPU;
        }else if(defaultDevice.equals("3")) {
            device = ObjectDetectorHelper.Device.NNAPI;
        } else{
            device = ObjectDetectorHelper.Device.CPU;
        }

        threshold = BigDecimal.valueOf(Integer.parseInt(sharedPreferences.getString("display_threshold", "1"))).divide(BigDecimal.valueOf(100));

        return fragmentCameraBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().getWindow().setStatusBarColor(Color.parseColor("#3322FF22"));

        objectDetectorHelper = new ObjectDetectorHelper(
                requireContext(), device,model, threshold, numThreads, maxResults,this);

        cameraExecutor = Executors.newSingleThreadExecutor();

        fragmentCameraBinding.viewFinder.post(new Runnable() {
            @Override
            public void run() {
                setUpCamera();
            }
        });

        fragmentCameraBinding.ChangeCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(LENS_FACING == CameraSelector.LENS_FACING_BACK){
                    fragmentCameraBinding.ChangeCameraButton.setText("USE BACK CAMERA");
                    LENS_FACING = CameraSelector.LENS_FACING_FRONT;
                }else{
                    fragmentCameraBinding.ChangeCameraButton.setText("USE FRONT CAMERA");
                    LENS_FACING = CameraSelector.LENS_FACING_BACK;
                }
                changeCamera();
            }
        });

        fragmentCameraBinding.ToggleTorch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hasFlashLight){
                    if(!isTorchOn){
                        if(camera != null){
                            camera.getCameraControl().enableTorch(true);
                            isTorchOn = true;
                            fragmentCameraBinding.ToggleTorch.setText(R.string.turn_off_torch);
                        }
                    }else{
                        if(camera != null){
                            camera.getCameraControl().enableTorch(false);
                            isTorchOn = false;
                            fragmentCameraBinding.ToggleTorch.setText(R.string.turn_on_torch);
                        }
                    }
                }
            }
        });

        BottomSheetBehavior<NestedScrollView> sheetBehavior = BottomSheetBehavior.from(fragmentCameraBinding.bottomSheetLayout.bottomSheetLayout);
        fragmentCameraBinding.bottomSheetLayout.bslCvGesture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(sheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED){
                    sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }else if(sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED){
                    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        });

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState){
                    case BottomSheetBehavior.STATE_HIDDEN:
                    case BottomSheetBehavior.STATE_HALF_EXPANDED:
                    case BottomSheetBehavior.STATE_DRAGGING:
                        break;

                    case BottomSheetBehavior.STATE_EXPANDED:
                        fragmentCameraBinding.bottomSheetLayout.bslIvArrow.setImageResource(R.drawable.ic_baseline_keyboard_arrow_down_24);
                        break;

                    case BottomSheetBehavior.STATE_COLLAPSED:
                    case BottomSheetBehavior.STATE_SETTLING:
                        fragmentCameraBinding.bottomSheetLayout.bslIvArrow.setImageResource(R.drawable.ic_baseline_keyboard_arrow_up_24);
                        break;
                }
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });

        initBottomSheetControls();
    }

    private void initBottomSheetControls(){
        //Threshold Reduce
        fragmentCameraBinding.bottomSheetLayout.bslIbtThresholdReduce.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(objectDetectorHelper.threshold.floatValue() > 0.1){
                    objectDetectorHelper.threshold = objectDetectorHelper.threshold.subtract(BigDecimal.ONE.divide(BigDecimal.TEN));
                    updateControlsInterface();
                }
            }
        });

        //Threshold Add
        fragmentCameraBinding.bottomSheetLayout.bslIbtThresholdAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(objectDetectorHelper.threshold.floatValue() <= 0.8){
                    objectDetectorHelper.threshold = objectDetectorHelper.threshold.add(BigDecimal.ONE.divide(BigDecimal.TEN));
                    updateControlsInterface();
                }
            }
        });

        //Max Result Reduce
        fragmentCameraBinding.bottomSheetLayout.bslIbtMaxResultsReduce.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(objectDetectorHelper.maxResults > 1){
                    objectDetectorHelper.maxResults--;
                    updateControlsInterface();
                }
            }
        });

        //Max Result Add
        fragmentCameraBinding.bottomSheetLayout.bslIbtMaxResultsAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(objectDetectorHelper.maxResults < 10){
                    objectDetectorHelper.maxResults++;
                    updateControlsInterface();
                }
            }
        });

        //Threads Reduce
        fragmentCameraBinding.bottomSheetLayout.bslIbtThreadsReduce.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(objectDetectorHelper.numThreads > 1){
                    objectDetectorHelper.numThreads--;
                    updateControlsInterface();
                }
            }
        });

        //Threads Add
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        fragmentCameraBinding.bottomSheetLayout.bslIbtThreadsAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(objectDetectorHelper.numThreads < availableProcessors){
                    objectDetectorHelper.numThreads++;
                    updateControlsInterface();
                }
            }
        });

        ArrayList<String> models = new ArrayList<>();
        models.add(ObjectDetectorHelper.Model.MobileNetV1.toString());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite0.name());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite1.name());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite2.name());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite4.name());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite0Pesa.name());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite0PesaV2.name());
        models.add(ObjectDetectorHelper.Model.EfficientNetDetLite0PesaV3.name());

        ArrayList<String> devices = new ArrayList<>();
        devices.add(ObjectDetectorHelper.Device.CPU.name());
        devices.add(ObjectDetectorHelper.Device.GPU.name());
        devices.add(ObjectDetectorHelper.Device.NNAPI.name());


        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(requireContext(), R.layout.list_item, models);
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(requireContext(), R.layout.list_item, devices);

        fragmentCameraBinding.bottomSheetLayout.bslActvModel.setAdapter(modelAdapter);
        fragmentCameraBinding.bottomSheetLayout.bslActvDevice.setAdapter(deviceAdapter);

        fragmentCameraBinding.bottomSheetLayout.bslActvModel.setText(models.get(0), false);
        fragmentCameraBinding.bottomSheetLayout.bslActvDevice.setText(devices.get(0), false);

        fragmentCameraBinding.bottomSheetLayout.bslActvModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                objectDetectorHelper.currentModel = ObjectDetectorHelper.getModel(position);
                updateControlsInterface();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        fragmentCameraBinding.bottomSheetLayout.bslActvModel.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                objectDetectorHelper.currentModel = ObjectDetectorHelper.getModel(position);
                updateControlsInterface();
            }
        });

        fragmentCameraBinding.bottomSheetLayout.bslActvDevice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                objectDetectorHelper.currentDevice = ObjectDetectorHelper.getDevice(position);
                updateControlsInterface();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        fragmentCameraBinding.bottomSheetLayout.bslActvDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                objectDetectorHelper.currentDevice = ObjectDetectorHelper.getDevice(position);
                updateControlsInterface();
            }
        });

        updateControlsInterface();

    }

    private void updateControlsInterface(){
        fragmentCameraBinding.bottomSheetLayout.bslTvMaxResults.setText(String.valueOf(objectDetectorHelper.maxResults));
        fragmentCameraBinding.bottomSheetLayout.bslTvThreads.setText(String.valueOf(objectDetectorHelper.numThreads));
        fragmentCameraBinding.bottomSheetLayout.bslTvThreshold.setText(String.format(objectDetectorHelper.threshold.toString(), Locale.getDefault()));

        // CLear Object Detector
        objectDetectorHelper.close();
        fragmentCameraBinding.overlay.clear();
    }

    private void setUpCamera(){
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

    private void bindCameraUseCases(){
        if(cameraProvider == null){
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
                if(bitmapBuffer == null){
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

        try{
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            hasFlashLight = camera.getCameraInfo().hasFlashUnit();
            fragmentCameraBinding.ToggleTorch.setEnabled(hasFlashLight);

            preview.setSurfaceProvider(fragmentCameraBinding.viewFinder.getSurfaceProvider());

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void detectObjects(ImageProxy image){
        bitmapBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

        int imageRotation = image.getImageInfo().getRotationDegrees();

        objectDetectorHelper.detect(bitmapBuffer, imageRotation);
    }

    private void changeCamera(){
        cameraProvider.unbindAll();

        fragmentCameraBinding.viewFinder.post(new Runnable() {
            @Override
            public void run() {
                setUpCamera();
            }
        });
    }

    private void speakResults(List<Detection> results){
        TextToSpeech tts;
        Collections.sort(results, new Comparator<Detection>() {
            @Override
            public int compare(Detection lhs, Detection rhs) {
                if(lhs.getCategories().get(0).getScore() != rhs.getCategories().get(0).getScore()) {
                    if (lhs.getCategories().get(0).getScore() > rhs.getCategories().get(0).getScore()) {
                        return 1;
                    } else {
                        return -1;
                    }
                }else{
                    return 0;
                }
            }
        });

        String value = results.get(0).getCategories().get(0).getLabel();

         tts = new TextToSpeech(requireContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS){
                    //ToDo: tts.speak(value, TextToSpeech.QUEUE_ADD, null);
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        imageAnalyzer.setTargetRotation(fragmentCameraBinding.viewFinder.getDisplay().getRotation());
    }

    @Override
    public void onResume() {
        super.onResume();
        if(ContextCompat.checkSelfPermission(requireContext(), PermissionsFragment.PERMISSION_CAMERA) != PackageManager.PERMISSION_GRANTED){
            Navigation.findNavController(fragmentCameraBinding.getRoot()).navigate(R.id.action_cameraFragment_to_permissionsFragment);
        }
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
        try{
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fragmentCameraBinding.bottomSheetLayout.bslTvInference.setText(String.format("%d ms", inferenceTime));

                    fragmentCameraBinding.overlay.setResults(
                            results,
                            imageHeight,
                            imageWidth
                    );

                    fragmentCameraBinding.overlay.invalidate();
                }
            });
        }catch (IllegalStateException ignored){}
    }
}
