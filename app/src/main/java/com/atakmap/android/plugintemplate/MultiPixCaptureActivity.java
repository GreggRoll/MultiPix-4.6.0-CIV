package com.atakmap.android.plugintemplate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.plugintemplate.plugin.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MultiPixCaptureActivity extends Activity
        implements SurfaceHolder.Callback {

    public static final String ACTION_SESSION_DONE =
            "com.atakmap.android.plugintemplate.MULTIPIX_SESSION_DONE";
    public static final String EXTRA_PHOTO_PATHS = "photo_paths";
    public static final String PREFS_NAME = "multipix_capture";
    public static final String PREF_PHOTO_PATHS = "photo_paths";

    private static final int REQUEST_CAMERA_PERMISSION = 82;
    private static final String PATH_SEPARATOR = "\n";

    private final ArrayList<String> photoPaths = new ArrayList<>();

    private SurfaceHolder holder;
    private Camera camera;
    private int cameraId = -1;
    private Button captureButton;
    private Button doneButton;
    private TextView countView;
    private boolean surfaceReady;
    private boolean capturing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        clearStoredSession();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);

        SurfaceView preview = new SurfaceView(this);
        holder = preview.getHolder();
        holder.addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(18, 12, 18, 12);
        controls.setBackgroundColor(0xB0000000);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button cancelButton = new Button(this);
        cancelButton.setText(R.string.multipix_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        controls.addView(cancelButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        captureButton = new Button(this);
        captureButton.setText(R.string.multipix_capture);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturePhoto();
            }
        });
        controls.addView(captureButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        doneButton = new Button(this);
        doneButton.setText(R.string.multipix_done);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (capturing) {
                    Toast.makeText(MultiPixCaptureActivity.this,
                            "Finishing capture", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendSession();
                finish();
            }
        });
        controls.addView(doneButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        countView = new TextView(this);
        countView.setTextColor(0xFFFFFFFF);
        countView.setGravity(Gravity.CENTER);
        countView.setTextSize(16);
        controls.addView(countView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        updateCount();

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        controlParams.gravity = Gravity.BOTTOM;
        root.addView(controls, controlParams);

        setContentView(root);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        surfaceReady = true;
        openCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format,
            int width, int height) {
        if (surfaceReady && camera != null) {
            restartPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        surfaceReady = false;
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceReady) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera and storage permissions are required",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void openCamera() {
        if (camera != null || !surfaceReady) {
            return;
        }
        try {
            cameraId = findBackCameraId();
            camera = Camera.open(cameraId);
            camera.setPreviewDisplay(holder);
            Camera.Parameters parameters = camera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null
                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            applyLandscapeOrientation(parameters);
            camera.setParameters(parameters);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open camera",
                    Toast.LENGTH_SHORT).show();
            releaseCamera();
        }
    }

    private int findBackCameraId() {
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return 0;
    }

    private void applyLandscapeOrientation(Camera.Parameters parameters) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int degrees = getDisplayRotationDegrees();
        int rotation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation + degrees) % 360;
            rotation = (360 - rotation) % 360;
        } else {
            rotation = (info.orientation - degrees + 360) % 360;
        }

        camera.setDisplayOrientation(rotation);
        parameters.setRotation(rotation);
    }

    private int getDisplayRotationDegrees() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

    private void restartPreview() {
        try {
            camera.stopPreview();
        } catch (Exception ignored) {
        }
        try {
            camera.setPreviewDisplay(holder);
            Camera.Parameters parameters = camera.getParameters();
            applyLandscapeOrientation(parameters);
            camera.setParameters(parameters);
            camera.startPreview();
        } catch (Exception ignored) {
        }
    }

    private void capturePhoto() {
        if (camera == null || capturing) {
            return;
        }

        capturing = true;
        captureButton.setEnabled(false);
        doneButton.setEnabled(false);
        try {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    savePhoto(data);
                    capturing = false;
                    captureButton.setEnabled(true);
                    doneButton.setEnabled(true);
                    updateCount();
                    restartPreview();
                }
            });
        } catch (Exception e) {
            capturing = false;
            captureButton.setEnabled(true);
            doneButton.setEnabled(true);
            Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show();
            restartPreview();
        }
    }

    private void savePhoto(byte[] data) {
        if (data == null || data.length == 0) {
            Toast.makeText(this, "Camera returned no image",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FileOutputStream outputStream = null;
        try {
            File dir = new File(Environment.getExternalStorageDirectory(),
                    "atak/tools/multipix/pending");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Could not create image folder");
            }

            File image = new File(dir, "multipix_"
                    + System.currentTimeMillis() + "_"
                    + (photoPaths.size() + 1) + ".jpg");
            outputStream = new FileOutputStream(image);
            outputStream.write(data);
            photoPaths.add(image.getAbsolutePath());
            storeSession();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save photo",
                    Toast.LENGTH_SHORT).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void updateCount() {
        if (countView != null) {
            countView.setText(photoPaths.size() + " photos");
        }
    }

    private void sendSession() {
        storeSession();
        Intent intent = new Intent(ACTION_SESSION_DONE);
        intent.putStringArrayListExtra(EXTRA_PHOTO_PATHS, photoPaths);
        sendBroadcast(intent);
    }

    private void storeSession() {
        StringBuilder builder = new StringBuilder();
        for (String path : photoPaths) {
            if (builder.length() > 0) {
                builder.append(PATH_SEPARATOR);
            }
            builder.append(path);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_PHOTO_PATHS, builder.toString())
                .apply();
    }

    private void clearStoredSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove(PREF_PHOTO_PATHS)
                .apply();
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
            } catch (Exception ignored) {
            }
            camera.release();
            camera = null;
        }
    }
}
