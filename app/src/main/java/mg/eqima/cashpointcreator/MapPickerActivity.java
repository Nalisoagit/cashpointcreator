package mg.eqima.cashpointcreator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private TextInputEditText etLat, etLng;
    private GeoPoint selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("osmdroid", MODE_PRIVATE);
        Configuration.getInstance().load(ctx, prefs);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        File base = new File(getCacheDir(), "osmdroid");
        File tiles = new File(base, "tiles");
        tiles.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(tiles);

        setContentView(R.layout.activity_map);

        etLat = findViewById(R.id.etLat);
        etLng = findViewById(R.id.etLng);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(6.0);
        map.getController().setCenter(new GeoPoint(-18.8792, 47.5079)); // Antananarivo

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Clic sur la carte : place le repère et remplit les champs
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                setPoint(p, false);
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        }));

        // Coordonnées déjà choisies : affichées dès l'ouverture
        Intent in = getIntent();
        if (in.hasExtra("lat") && in.hasExtra("lng")) {
            setPoint(new GeoPoint(in.getDoubleExtra("lat", 0), in.getDoubleExtra("lng", 0)), true);
            map.getController().setZoom(15.0);
        }

        findViewById(R.id.btnShow).setOnClickListener(v -> showFromFields());
        etLng.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                showFromFields();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            if (!showFromFields()) return;
            Intent data = new Intent();
            data.putExtra("lat", selected.getLatitude());
            data.putExtra("lng", selected.getLongitude());
            setResult(RESULT_OK, data);
            finish();
        });
    }

    // Lit les champs, valide, puis affiche le lieu sur la carte
    private boolean showFromFields() {
        String sLat = etLat.getText() == null ? "" : etLat.getText().toString().trim().replace(',', '.');
        String sLng = etLng.getText() == null ? "" : etLng.getText().toString().trim().replace(',', '.');
        if (sLat.isEmpty() || sLng.isEmpty()) {
            toast("Touchez la carte ou saisissez latitude et longitude");
            return false;
        }
        try {
            double lat = Double.parseDouble(sLat);
            double lng = Double.parseDouble(sLng);
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                toast("Coordonnées invalides");
                return false;
            }
            setPoint(new GeoPoint(lat, lng), true);
            return true;
        } catch (NumberFormatException e) {
            toast("Coordonnées invalides");
            return false;
        }
    }

    private void setPoint(GeoPoint p, boolean recenter) {
        selected = p;
        etLat.setText(String.format(Locale.US, "%.6f", p.getLatitude()));
        etLng.setText(String.format(Locale.US, "%.6f", p.getLongitude()));
        marker.setPosition(p);
        if (!map.getOverlays().contains(marker)) map.getOverlays().add(marker);
        if (recenter) map.getController().animateTo(p);
        map.invalidate();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override protected void onResume() { super.onResume(); map.onResume(); }
    @Override protected void onPause() { super.onPause(); map.onPause(); }
}