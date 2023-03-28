package com.projecty4s2.pesacheck;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;

import com.google.android.material.navigation.NavigationBarView;
import com.projecty4s2.pesacheck.databinding.ActivityMainBinding;

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

        try{
            Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_settingsFragment_to_cameraFragment);
            activityMainBinding.bottomNavigation.setSelectedItemId(R.id.main_page);
        }catch (Exception ignored){}

        activityMainBinding.bottomNavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(item.getItemId() == R.id.main_page) {
                    if (activityMainBinding.bottomNavigation.getSelectedItemId() == R.id.settings_page) {
                        Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_settingsFragment_to_cameraFragment);
                        return true;
                    }else{
                        Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_settingsFragment_to_permissionsFragment);
                    }
                }else if(item.getItemId() == R.id.settings_page){
                    if (hasPermission()){
                        Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_cameraFragment_to_settingsFragment);
                    }else{
                        Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_permissionsFragment_to_settingsFragment);
                    }
                    return true;
                }
                return false;
            }
        });
        activityMainBinding.bottomNavigation.setOnItemReselectedListener(new NavigationBarView.OnItemReselectedListener() {
            @Override
            public void onNavigationItemReselected(@NonNull MenuItem item) {}
        });

        Objects.requireNonNull(getSupportActionBar()).hide();
    }


    @Override
    protected void onResume() {
        activityMainBinding.bottomNavigation.setSelectedItemId(R.id.main_page);
        super.onResume();
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
