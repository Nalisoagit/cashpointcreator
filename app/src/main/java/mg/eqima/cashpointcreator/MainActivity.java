package mg.eqima.cashpointcreator;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    public interface TagListener { void onTag(Tag tag); }

    private DrawerLayout drawer;
    private NavigationView navView;
    private NfcAdapter nfcAdapter;
    private volatile TagListener tagListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.app_name, R.string.app_name);
        toggle.getDrawerArrowDrawable().setColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.primary));
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        navView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_create) {
                show(new CreateCashpointFragment(), "Création cashpoint");
            } else if (item.getItemId() == R.id.nav_list) {
                show(new ListCashpointFragment(), "Liste cashpoint");
            }
            drawer.closeDrawers();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(navView)) {
                    drawer.closeDrawers();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState == null) {
            navView.setCheckedItem(R.id.nav_create);
            show(new CreateCashpointFragment(), "Création cashpoint");
        }
    }

    private void show(Fragment f, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .commit();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
    }

    public boolean isNfcReady() {
        return nfcAdapter != null && nfcAdapter.isEnabled();
    }

    public void setTagListener(TagListener l) { this.tagListener = l; }

    // Le mode lecteur reste actif tant que l'appli est au premier plan :
    // le système n'ouvre donc plus de "nouveau tag" par-dessus l'appli.
    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        TagListener l = tagListener;
        if (l != null) l.onTag(tag);
    }
}