package com.atakmap.android.plugintemplate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.plugintemplate.plugin.R;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

public class PluginTemplateDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = PluginTemplateDropDownReceiver.class
            .getSimpleName();

    public static final String SHOW_PLUGIN =
            "com.atakmap.android.plugintemplate.SHOW_PLUGIN";

    private final View reviewView;
    private final Context pluginContext;
    private final Context atakContext;
    private final LinearLayout galleryList;
    private final TextView emptyState;
    private final TextView sessionPoint;
    private final EditText photoDescription;
    private final Button saveSession;
    private final ArrayList<SessionPhoto> sessionPhotos = new ArrayList<>();
    private GeoPointMetaData sharedPoint;
    private boolean receiverRegistered;

    private final BroadcastReceiver captureResultReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!MultiPixCaptureActivity.ACTION_SESSION_DONE
                            .equals(intent.getAction())) {
                        return;
                    }
                    String records = intent.getStringExtra(
                            MultiPixCaptureActivity.EXTRA_PHOTO_RECORDS);
                    ArrayList<String> paths = intent.getStringArrayListExtra(
                            MultiPixCaptureActivity.EXTRA_PHOTO_PATHS);
                    receiveCaptureSession(records, paths);
                }
            };

    public PluginTemplateDropDownReceiver(final MapView mapView,
            final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.atakContext = mapView.getContext();

        reviewView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);
        galleryList = reviewView.findViewById(R.id.galleryList);
        emptyState = reviewView.findViewById(R.id.emptyState);
        sessionPoint = reviewView.findViewById(R.id.sessionPoint);
        photoDescription = reviewView.findViewById(R.id.photoDescription);
        saveSession = reviewView.findViewById(R.id.saveSession);
        saveSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSession();
            }
        });

        registerCaptureReceiver();
        renderSession();
    }

    public void disposeImpl() {
        if (receiverRegistered) {
            try {
                atakContext.unregisterReceiver(captureResultReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            return;
        }

        if (action.equals(SHOW_PLUGIN)) {
            launchCaptureActivity();
        }
    }

    private void registerCaptureReceiver() {
        if (!receiverRegistered) {
            atakContext.registerReceiver(captureResultReceiver,
                    new IntentFilter(MultiPixCaptureActivity.ACTION_SESSION_DONE));
            receiverRegistered = true;
        }
    }

    private void launchCaptureActivity() {
        Log.d(TAG, "launching MultiPix capture activity");
        Intent intent = new Intent();
        intent.setClassName("com.atakmap.android.plugintemplate.plugin",
                "com.atakmap.android.plugintemplate.MultiPixCaptureActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        atakContext.startActivity(intent);
    }

    private void receiveCaptureSession(String recordsJson, List<String> paths) {
        sessionPhotos.clear();
        photoDescription.setText("");

        List<SessionPhoto> records = readPhotoRecords(recordsJson);
        if (records == null || records.isEmpty()) {
            records = readStoredPhotoRecords();
        }

        if ((records == null || records.isEmpty())
                && (paths == null || paths.isEmpty())) {
            paths = readStoredSessionPaths();
        }

        if (records != null && !records.isEmpty()) {
            for (SessionPhoto photo : records) {
                if (photo.file.exists()) {
                    sessionPhotos.add(photo);
                } else {
                    Log.w(TAG, "Captured photo path was not readable: "
                            + photo.file);
                }
            }
        } else if (paths != null) {
            for (String path : paths) {
                File file = new File(path);
                if (file.exists()) {
                    sessionPhotos.add(new SessionPhoto(file, null));
                } else {
                    Log.w(TAG, "Captured photo path was not readable: " + path);
                }
            }
        }

        sharedPoint = getMapView().getPointWithElevation();
        renderSession();
        showDropDown(reviewView, THREE_EIGHTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, false, this);
    }

    private List<String> readStoredSessionPaths() {
        SharedPreferences preferences = pluginContext.getSharedPreferences(
                MultiPixCaptureActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String stored = preferences.getString(
                MultiPixCaptureActivity.PREF_PHOTO_PATHS, "");
        ArrayList<String> paths = new ArrayList<>();
        if (stored == null || stored.length() == 0) {
            return paths;
        }
        String[] split = stored.split("\n");
        for (String path : split) {
            if (path != null && path.trim().length() > 0) {
                paths.add(path.trim());
            }
        }
        return paths;
    }

    private List<SessionPhoto> readStoredPhotoRecords() {
        SharedPreferences preferences = pluginContext.getSharedPreferences(
                MultiPixCaptureActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return readPhotoRecords(preferences.getString(
                MultiPixCaptureActivity.PREF_PHOTO_RECORDS, ""));
    }

    private List<SessionPhoto> readPhotoRecords(String recordsJson) {
        ArrayList<SessionPhoto> records = new ArrayList<>();
        if (recordsJson == null || recordsJson.trim().length() == 0) {
            return records;
        }

        try {
            JSONArray array = new JSONArray(recordsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }

                String path = object.optString("path", "");
                if (path.trim().length() == 0) {
                    continue;
                }

                Float heading = null;
                if (object.has("heading")) {
                    heading = (float) object.optDouble("heading");
                }
                records.add(new SessionPhoto(new File(path), heading));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse captured photo records", e);
        }
        return records;
    }

    private void renderSession() {
        galleryList.removeAllViews();
        emptyState.setVisibility(sessionPhotos.isEmpty() ? View.VISIBLE
                : View.GONE);
        saveSession.setEnabled(!sessionPhotos.isEmpty());

        GeoPoint point = sharedPoint == null ? null : sharedPoint.get();
        if (point == null) {
            sessionPoint.setText("");
        } else {
            sessionPoint.setText("Shared point: "
                    + format(point.getLatitude()) + ", "
                    + format(point.getLongitude()));
        }

        for (int i = 0; i < sessionPhotos.size(); i++) {
            galleryList.addView(createPhotoRow(sessionPhotos.get(i), i));
        }
    }

    private View createPhotoRow(final SessionPhoto photo, int index) {
        LinearLayout row = new LinearLayout(pluginContext);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 8);

        ImageView thumbnail = new ImageView(pluginContext);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setAdjustViewBounds(true);
        thumbnail.setImageBitmap(decodeThumbnail(photo.file));
        row.addView(thumbnail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 160));

        TextView heading = new TextView(pluginContext);
        heading.setText(formatHeading(photo.headingDegrees));
        heading.setTextColor(0xFFCFD8DC);
        heading.setTextSize(13);
        heading.setPadding(0, 4, 0, 0);
        row.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button delete = new Button(pluginContext);
        delete.setText("Delete");
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sessionPhotos.remove(photo);
                renderSession();
            }
        });
        row.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return row;
    }

    private Bitmap decodeThumbnail(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > 640
                || bounds.outHeight / sampleSize > 640) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void saveSession() {
        String description = photoDescription.getText().toString();
        applyDescriptionToPhotos(description);
        if (sessionPhotos.isEmpty()) {
            Toast.makeText(atakContext, "No photos to save",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        GeoPointMetaData pointMetaData = sharedPoint == null
                ? getMapView().getPointWithElevation() : sharedPoint;
        GeoPoint point = pointMetaData.get();
        Marker marker = createSessionMarker(pointMetaData);

        ArrayList<String> captions = new ArrayList<>();
        ArrayList<String> headings = new ArrayList<>();
        int attached = 0;
        for (SessionPhoto photo : sessionPhotos) {
            try {
                applyExif(photo, point);
                File attachedFile = AttachmentManager.addAttachment(marker,
                        photo.file);
                if (attachedFile != null) {
                    attached++;
                }
                captions.add(photo.caption == null ? "" : photo.caption);
                headings.add(photo.headingDegrees == null ? ""
                        : formatHeadingValue(photo.headingDegrees));
            } catch (Exception e) {
                Log.e(TAG, "Failed to attach " + photo.file, e);
            }
        }

        marker.setMetaStringArrayList("multipix.captions", captions);
        marker.setMetaStringArrayList("multipix.headings", headings);
        marker.setMetaString("photo_description", description);
        marker.setMetaInteger("multipix.photoCount", attached);
        marker.persist(getMapView().getMapEventDispatcher(), null,
                PluginTemplateDropDownReceiver.class);
        marker.refresh(getMapView().getMapEventDispatcher(), null,
                PluginTemplateDropDownReceiver.class);

        Toast.makeText(atakContext, "Saved " + attached
                + " photos to " + marker.getTitle(), Toast.LENGTH_LONG).show();
        closeDropDown();
    }

    private void applyDescriptionToPhotos(String description) {
        for (SessionPhoto photo : sessionPhotos) {
            photo.caption = description == null ? "" : description;
        }
    }

    private Marker createSessionMarker(GeoPointMetaData point) {
        PlacePointTool.MarkerCreator markerCreator =
                new PlacePointTool.MarkerCreator(point);
        String uid = "multipix-" + UUID.randomUUID().toString();
        String callsign = "MultiPix " + (System.currentTimeMillis() / 1000L);
        markerCreator.setUid(uid);
        markerCreator.setCallsign(callsign);
        markerCreator.setType("b-m-p-s-p-loc");
        markerCreator.setArchive(true);
        markerCreator.setNeverPersist(false);
        markerCreator.showCotDetails(false);
        Marker marker = markerCreator.placePoint();
        marker.setTitle(callsign);
        marker.setMetaString("entry", "user");
        marker.setMetaBoolean("archive", true);
        marker.setMetaBoolean("editable", true);
        marker.setMetaBoolean("removable", true);
        marker.setMetaBoolean("movable", true);
        return marker;
    }

    private void applyExif(SessionPhoto photo, GeoPoint point) throws Exception {
        ExifInterface exif = new ExifInterface(photo.file.getAbsolutePath());
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,
                toExifCoordinate(Math.abs(point.getLatitude())));
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,
                point.getLatitude() >= 0 ? "N" : "S");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,
                toExifCoordinate(Math.abs(point.getLongitude())));
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,
                point.getLongitude() >= 0 ? "E" : "W");
        if (photo.headingDegrees != null) {
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION,
                    toExifDirection(photo.headingDegrees));
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M");
        }
        if (photo.caption != null && photo.caption.trim().length() > 0) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION,
                    photo.caption);
        }
        exif.saveAttributes();
    }

    private String toExifCoordinate(double coordinate) {
        int degrees = (int) coordinate;
        double minutesFull = (coordinate - degrees) * 60.0;
        int minutes = (int) minutesFull;
        double seconds = (minutesFull - minutes) * 60.0;
        int secondsScaled = (int) Math.round(seconds * 10000.0);
        return degrees + "/1," + minutes + "/1," + secondsScaled + "/10000";
    }

    private String toExifDirection(float heading) {
        return Math.round(heading * 100.0f) + "/100";
    }

    private String formatHeading(Float heading) {
        if (heading == null) {
            return "Heading: unavailable";
        }
        return "Heading: " + Math.round(heading) + "\u00B0";
    }

    private String formatHeadingValue(float heading) {
        return String.format(Locale.US, "%.2f", heading);
    }

    private String format(double value) {
        return String.format("%.6f", value);
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    private static class SessionPhoto {
        final File file;
        final Float headingDegrees;
        String caption = "";

        SessionPhoto(File file, Float headingDegrees) {
            this.file = file;
            this.headingDegrees = headingDegrees;
        }
    }
}
