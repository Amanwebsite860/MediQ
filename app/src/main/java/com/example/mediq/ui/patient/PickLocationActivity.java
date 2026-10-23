package com.example.mediq.ui.patient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mediq.R;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Locale;

/**
 * "Drag the map under the pin" location picker for hospital owners.
 * Input extras: lat, lon (optional). Result extras: lat, lon.
 */
public class PickLocationActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";

    private MapView map;
    private TextView tvCoords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_location);

        map = findViewById(R.id.pickMap);
        tvCoords = findViewById(R.id.tvPickCoords);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        Intent in = getIntent();
        if (in.hasExtra(EXTRA_LAT) && in.hasExtra(EXTRA_LON)) {
            map.getController().setCenter(new GeoPoint(in.getDoubleExtra(EXTRA_LAT, 25.6093), in.getDoubleExtra(EXTRA_LON, 85.1376)));
        } else {
            map.getController().setCenter(new GeoPoint(25.6093, 85.1376));
            centerOnMe();
        }

        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent e) { showCoords(); return false; }
            @Override public boolean onZoom(ZoomEvent e) { showCoords(); return false; }
        });
        showCoords();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnConfirmLocation).setOnClickListener(v -> {
            IGeoPoint c = map.getMapCenter();
            Intent out = new Intent();
            out.putExtra(EXTRA_LAT, c.getLatitude());
            out.putExtra(EXTRA_LON, c.getLongitude());
            setResult(RESULT_OK, out);
            finish();
        });
    }

    private void showCoords() {
        IGeoPoint c = map.getMapCenter();
        tvCoords.setText(String.format(Locale.US, "%.5f, %.5f", c.getLatitude(), c.getLongitude()));
    }

    private void centerOnMe() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!granted) return;
        try {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(l -> {
                if (l != null && !isFinishing()) map.getController().setCenter(new GeoPoint(l.getLatitude(), l.getLongitude()));
            });
        } catch (SecurityException ignored) { }
    }

    @Override protected void onResume() { super.onResume(); map.onResume(); }
    @Override protected void onPause() { super.onPause(); map.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); map.onDetach(); }
}
