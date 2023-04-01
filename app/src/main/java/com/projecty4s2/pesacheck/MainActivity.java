package com.projecty4s2.pesacheck;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;

import com.google.android.material.navigation.NavigationBarView;
import com.projecty4s2.pesacheck.databinding.ActivityMainBinding;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding activityMainBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());

        activityMainBinding.bottomNavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.camera_fragment) {
                    Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_settingsFragment_to_cameraFragment);
                    return true;
                } else if (item.getItemId() == R.id.settings_fragment) {
                    Navigation.findNavController(MainActivity.this, R.id.main_container).navigate(R.id.action_cameraFragment_to_settingsFragment);

                    return true;
                }
                return false;
            }
        });

        Objects.requireNonNull(getSupportActionBar()).hide();
    }


    @Override
    protected void onResume() {
        //activityMainBinding.bottomNavigation.setSelectedItemId(R.id.main_page);
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }
}
