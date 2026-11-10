package mg.eqima.cashpointcreator;



import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.File;

public class MapPickerActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
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

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(6.0);
        map.getController().setCenter(new GeoPoint(-18.8792, 47.5079)); // Antananarivo

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                selected = p;
                marker.setPosition(p);
                if (!map.getOverlays().contains(marker)) map.getOverlays().add(marker);
                map.invalidate();
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        }));

        ((Button) findViewById(R.id.btnConfirm)).setOnClickListener(v -> {
            if (selected == null) {
                Toast.makeText(this, "Touchez la carte pour choisir un lieu", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putExtra("lat", selected.getLatitude());
            data.putExtra("lng", selected.getLongitude());
            setResult(RESULT_OK, data);
            finish();
        });
    }

    @Override protected void onResume() { super.onResume(); map.onResume(); }
    @Override protected void onPause() { super.onPause(); map.onPause(); }
}