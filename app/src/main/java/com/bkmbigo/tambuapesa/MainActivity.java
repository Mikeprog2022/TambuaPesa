package com.bkmbigo.tambuapesa;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NavigationRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;

import com.bkmbigo.tambuapesa.databinding.ActivityMainBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding activityMainBinding;
    
    public static final int PERMISSION_REQUEST = 1;
    public static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());

        Objects.requireNonNull(getSupportActionBar()).hide();
    }





    @Override
    public void onBackPressed() {
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q){
            finishAfterTransition();
        }else{
            super.onBackPressed();
        }
    }
    
    public boolean hasPermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        }else{
            return true;
        }
    }
    
    private void requestPermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(shouldShowRequestPermissionRationale(PERMISSION_CAMERA)){
                Toast.makeText(this, "Requesting Camera Permission", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(this, "Cannot Ask For Permission", Toast.LENGTH_SHORT).show();
                return;
            }
            
            requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if(requestCode == PERMISSION_REQUEST){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Navigation.findNavController(this, R.id.main_container).navigate(R.id.action_cameraFragment_to_permissionsFragment);
            }else{
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
