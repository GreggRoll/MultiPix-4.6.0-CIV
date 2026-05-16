package com.atakmap.android.multipix;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
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

import com.atakmap.android.multipix.plugin.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class MultiPixCaptureActivity extends Activity
        implements SurfaceHolder.Callback, SensorEventListener {

    public static final String ACTION_SESSION_DONE =
            "com.atakmap.android.multipix.MULTIPIX_SESSION_DONE";
    public static final String EXTRA_PHOTO_PATHS = "photo_paths";
    public static final String EXTRA_PHOTO_RECORDS = "photo_records";
    public static final String EXTRA_RESULT_PACKAGE = "result_package";
    public static final String PREFS_NAME = "multipix_capture";
    public static final String PREF_PHOTO_PATHS = "photo_paths";
    public static final String PREF_PHOTO_RECORDS = "photo_records";

    private static final int REQUEST_CAMERA_PERMISSION = 82;
    private static final String PATH_SEPARATOR = "\n";

    private final ArrayList<PhotoRecord> photoRecords = new ArrayList<>();

    private SurfaceHolder holder;
    private Camera camera;
    private int cameraId = -1;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor accelerometerSensor;
    private Sensor magneticFieldSensor;
    private Button captureButton;
    private Button doneButton;
    private TextView countView;
    private boolean surfaceReady;
    private boolean capturing;
    private Float latestHeadingDegrees;
    private Float headingAtCapturePress;
    private float[] latestAccelerometer;
    private float[] latestMagneticField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(
                    Sensor.TYPE_ROTATION_VECTOR);
            accelerometerSensor = sensorManager.getDefaultSensor(
                    Sensor.TYPE_ACCELEROMETER);
            magneticFieldSensor = sensorManager.getDefaultSensor(
                    Sensor.TYPE_MAGNETIC_FIELD);
        }
        buildUi();
        clearStoredSession();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.CAMERA },
                    REQUEST_CAMERA_PERMISSION);
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
        captureButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    headingAtCapturePress = latestHeadingDegrees;
                }
                return false;
            }
        });
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
        registerHeadingSensors();
        if (surfaceReady) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        unregisterHeadingSensors();
        releaseCamera();
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix,
                    event.values);
            updateHeading(rotationMatrix);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            latestAccelerometer = event.values.clone();
            updateHeadingFromFallbackSensors();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            latestMagneticField = event.values.clone();
            updateHeadingFromFallbackSensors();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required",
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

        final Float heading = headingAtCapturePress == null
                ? latestHeadingDegrees : headingAtCapturePress;
        headingAtCapturePress = null;
        capturing = true;
        captureButton.setEnabled(false);
        doneButton.setEnabled(false);
        try {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    savePhoto(data, heading);
                    capturing = false;
                    captureButton.setEnabled(true);
                    doneButton.setEnabled(true);
                    updateCount();
                    restartPreview();
                }
            });
        } catch (Exception e) {
            headingAtCapturePress = null;
            capturing = false;
            captureButton.setEnabled(true);
            doneButton.setEnabled(true);
            Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show();
            restartPreview();
        }
    }

    private void savePhoto(byte[] data, Float heading) {
        if (data == null || data.length == 0) {
            Toast.makeText(this, "Camera returned no image",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PhotoRecord mediaPhoto = savePhotoToMediaStore(data, heading);
            photoRecords.add(mediaPhoto);
            storeSession();
            return;
        } catch (Exception ignored) {
        }

        FileOutputStream outputStream = null;
        try {
            File dir = getSharedPendingDirectory();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Could not create image folder");
            }

            File image = new File(dir, "multipix_"
                    + System.currentTimeMillis() + "_"
                    + (photoRecords.size() + 1) + ".jpg");
            outputStream = new FileOutputStream(image);
            outputStream.write(data);
            photoRecords.add(new PhotoRecord(image.getAbsolutePath(), null,
                    heading));
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

    private PhotoRecord savePhotoToMediaStore(byte[] data, Float heading)
            throws Exception {
        String fileName = "multipix_" + System.currentTimeMillis() + "_"
                + (photoRecords.size() + 1) + ".jpg";

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "MultiPix");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Could not create media folder");
            }
            File image = new File(dir, fileName);
            FileOutputStream outputStream = new FileOutputStream(image);
            try {
                outputStream.write(data);
            } finally {
                outputStream.close();
            }
            return new PhotoRecord(image.getAbsolutePath(), null, heading);
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/MultiPix");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Could not create media entry");
        }

        boolean saved = false;
        try {
            OutputStream outputStream = getContentResolver()
                    .openOutputStream(uri);
            if (outputStream == null) {
                throw new IllegalStateException("Could not open media entry");
            }
            try {
                outputStream.write(data);
            } finally {
                outputStream.close();
            }

            ContentValues completeValues = new ContentValues();
            completeValues.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, completeValues, null, null);
            saved = true;
            return new PhotoRecord(null, uri.toString(), heading);
        } finally {
            if (!saved) {
                getContentResolver().delete(uri, null, null);
            }
        }
    }

    private File getSharedPendingDirectory() {
        File appDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (appDir != null) {
            File pendingDir = new File(appDir, "pending");
            if (pendingDir.exists() || pendingDir.mkdirs()) {
                return pendingDir;
            }
        }

        File sharedDir = new File(Environment.getExternalStorageDirectory(),
                "atak/tools/multipix/pending");
        try {
            File parent = sharedDir.getParentFile();
            if ((sharedDir.exists() || parent == null || parent.exists()
                    || parent.mkdirs()) && (sharedDir.exists()
                    || sharedDir.mkdirs()) && sharedDir.canWrite()) {
                return sharedDir;
            }
        } catch (Exception ignored) {
        }

        return sharedDir;
    }

    private void updateCount() {
        if (countView != null) {
            countView.setText(photoRecords.size() + " photos");
        }
    }

    private void sendSession() {
        storeSession();
        Intent intent = new Intent(ACTION_SESSION_DONE);
        intent.putStringArrayListExtra(EXTRA_PHOTO_PATHS, getPhotoPaths());
        intent.putExtra(EXTRA_PHOTO_RECORDS, serializePhotoRecords());
        ClipData clipData = getPhotoUriClipData();
        if (clipData != null) {
            intent.setClipData(clipData);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        String resultPackage = getIntent().getStringExtra(EXTRA_RESULT_PACKAGE);
        if (resultPackage != null && resultPackage.trim().length() > 0) {
            intent.setPackage(resultPackage);
        }
        sendBroadcast(intent);
    }

    private void storeSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_PHOTO_RECORDS, serializePhotoRecords())
                .putString(PREF_PHOTO_PATHS, serializePhotoPaths())
                .commit();
    }

    private String serializePhotoPaths() {
        StringBuilder builder = new StringBuilder();
        for (PhotoRecord photoRecord : photoRecords) {
            if (builder.length() > 0) {
                builder.append(PATH_SEPARATOR);
            }
            builder.append(photoRecord.path);
        }
        return builder.toString();
    }

    private String serializePhotoRecords() {
        JSONArray array = new JSONArray();
        for (PhotoRecord photoRecord : photoRecords) {
            JSONObject object = new JSONObject();
            try {
                if (photoRecord.path != null) {
                    object.put("path", photoRecord.path);
                }
                if (photoRecord.uri != null) {
                    object.put("uri", photoRecord.uri);
                }
                if (photoRecord.headingDegrees != null) {
                    object.put("heading", photoRecord.headingDegrees);
                }
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        return array.toString();
    }

    private ArrayList<String> getPhotoPaths() {
        ArrayList<String> paths = new ArrayList<>();
        for (PhotoRecord photoRecord : photoRecords) {
            if (photoRecord.path != null) {
                paths.add(photoRecord.path);
            }
        }
        return paths;
    }

    private ClipData getPhotoUriClipData() {
        ClipData clipData = null;
        for (PhotoRecord photoRecord : photoRecords) {
            if (photoRecord.uri == null) {
                continue;
            }

            Uri uri = Uri.parse(photoRecord.uri);
            if (clipData == null) {
                clipData = ClipData.newUri(getContentResolver(),
                        "MultiPix photo", uri);
            } else {
                clipData.addItem(new ClipData.Item(uri));
            }
        }
        return clipData;
    }

    private void clearStoredSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove(PREF_PHOTO_RECORDS)
                .remove(PREF_PHOTO_PATHS)
                .apply();
    }

    private void registerHeadingSensors() {
        if (sensorManager == null) {
            return;
        }
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor,
                        SensorManager.SENSOR_DELAY_UI);
            }
            if (magneticFieldSensor != null) {
                sensorManager.registerListener(this, magneticFieldSensor,
                        SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void unregisterHeadingSensors() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void updateHeadingFromFallbackSensors() {
        if (latestAccelerometer == null || latestMagneticField == null) {
            return;
        }

        float[] rotationMatrix = new float[9];
        float[] inclinationMatrix = new float[9];
        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix,
                latestAccelerometer, latestMagneticField)) {
            updateHeading(rotationMatrix);
        }
    }

    private void updateHeading(float[] rotationMatrix) {
        Float cameraHeading = getBackCameraHeading(rotationMatrix);
        if (cameraHeading != null) {
            latestHeadingDegrees = cameraHeading;
        }
    }

    private Float getBackCameraHeading(float[] rotationMatrix) {
        float east = -rotationMatrix[2];
        float north = -rotationMatrix[5];
        float horizontalMagnitude = (float) Math.sqrt(east * east
                + north * north);
        if (horizontalMagnitude < 0.1f) {
            return null;
        }

        return normalizeHeadingDegrees(
                (float) Math.toDegrees(Math.atan2(east, north)));
    }

    private float normalizeHeadingDegrees(float degrees) {
        float normalized = degrees % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        return normalized;
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

    private static class PhotoRecord {
        final String path;
        final String uri;
        final Float headingDegrees;

        PhotoRecord(String path, String uri, Float headingDegrees) {
            this.path = path;
            this.uri = uri;
            this.headingDegrees = headingDegrees;
        }
    }
}
