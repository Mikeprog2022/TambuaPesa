package com.bkmbigo.tambuapesa.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bkmbigo.tambuapesa.R;
import com.bkmbigo.tambuapesa.databinding.FragmentPermissionsBinding;
import com.google.android.material.button.MaterialButton;


public class PermissionsFragment extends Fragment {

    public static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    private FragmentPermissionsBinding permissionsBinding;
    private MaterialButton btGrantPermissions;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        permissionsBinding = FragmentPermissionsBinding.inflate(inflater, container, false);
        btGrantPermissions = permissionsBinding.permFragBtGrantPermission;
        btGrantPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissionLauncher.launch(PERMISSION_CAMERA);
            }
        });
        return permissionsBinding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (ContextCompat.checkSelfPermission(requireContext(), PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED) {
            navigateToCamera();
        }
    }

    private void navigateToCamera(){
        Navigation.findNavController(requireActivity(), R.id.main_container).navigate(R.id.action_permissionsFragment_to_cameraFragment);
    }

    final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean result) {
                    if (result) {
                        Toast.makeText(requireActivity(), "Permission Granted", Toast.LENGTH_SHORT).show();
                        navigateToCamera();
                    } else {
                        Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                }
            });

}
