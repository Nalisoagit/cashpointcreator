package mg.eqima.cashpointcreator;

import android.app.AlertDialog;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    // Émulateur : 10.0.2.2 ; appareil réel : IP du serveur
    //private static final String BASE_URL = "http://10.10.11.56:8081/cashpoint/create";
    private static final String BASE_URL = "https://preprod.api.cashless.eqima.org/cashpoint/create";


    private EditText etNom, etEmail, etNumero, etPassword, etRepere, etLieu;
    private TextView tvCoord;
    private Double latitude = null, longitude = null;

    private NfcAdapter nfcAdapter;
    private AlertDialog scanDialog;
    private volatile boolean waitingTag = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> mapLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    latitude = result.getData().getDoubleExtra("lat", 0);
                    longitude = result.getData().getDoubleExtra("lng", 0);
                    tvCoord.setText("Coordonnées : " + latitude + " , " + longitude);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etNom = findViewById(R.id.etNom);
        etEmail = findViewById(R.id.etEmail);
        etNumero = findViewById(R.id.etNumero);
        etPassword = findViewById(R.id.etPassword);
        etRepere = findViewById(R.id.etRepere);
        etLieu = findViewById(R.id.etLieu);
        tvCoord = findViewById(R.id.tvCoord);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        ((Button) findViewById(R.id.btnMap)).setOnClickListener(v ->
                mapLauncher.launch(new Intent(this, MapPickerActivity.class)));
        ((Button) findViewById(R.id.btnOk)).setOnClickListener(v -> onOkClicked());
    }

    private void onOkClicked() {
        String nom = etNom.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String numero = etNumero.getText().toString().trim();
        String password = etPassword.getText().toString();
        String lieu = etLieu.getText().toString().trim();

        if (nom.isEmpty() || email.isEmpty() || numero.isEmpty() || password.isEmpty() || lieu.isEmpty()) {
            toast("Veuillez remplir tous les champs");
            return;
        }
        if (!numero.matches("\\d{1,10}")) {
            toast("Le numéro ne doit pas dépasser 10 chiffres");
            return;
        }
        if (latitude == null || longitude == null) {
            toast("Choisissez le lieu sur la carte");
            return;
        }
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            toast("NFC indisponible ou désactivé");
            return;
        }

        waitingTag = true;
        scanDialog = new AlertDialog.Builder(this)
                .setTitle("Carte NFC")
                .setMessage("Approchez la carte NFC du téléphone...")
                .setCancelable(false)
                .setNegativeButton("Annuler", (d, w) -> stopScan())
                .show();

        nfcAdapter.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V,
                null);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if (!waitingTag) return;
        waitingTag = false;

        final String numero = etNumero.getText().toString().trim();
        final String idCarte = bytesToHex(tag.getId());

        try {
            writeLogin(tag, numero);
        } catch (Exception e) {
            ui.post(() -> {
                stopScan();
                toast("Écriture NFC échouée : " + e.getMessage());
            });
            return;
        }

        ui.post(() -> {
            stopScan();
            callApi(idCarte);
        });
    }

    private void writeLogin(Tag tag, String numero) throws Exception {
        String payload = "{\"login\":\"" + numero + "\"}";
        NdefRecord record = NdefRecord.createTextRecord("en", payload);
        NdefMessage message = new NdefMessage(new NdefRecord[]{record});

        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            ndef.connect();
            try {
                if (!ndef.isWritable()) throw new Exception("Carte en lecture seule");
                if (ndef.getMaxSize() < message.toByteArray().length) throw new Exception("Carte trop petite");
                ndef.writeNdefMessage(message);
            } finally {
                ndef.close();
            }
        } else {
            NdefFormatable formatable = NdefFormatable.get(tag);
            if (formatable == null) throw new Exception("Carte non compatible NDEF");
            formatable.connect();
            try {
                formatable.format(message);
            } finally {
                formatable.close();
            }
        }
    }

    private void callApi(String idCarte) {
        final String nom = etNom.getText().toString().trim();
        final String email = etEmail.getText().toString().trim();
        final String numero = etNumero.getText().toString().trim();
        final String password = etPassword.getText().toString();
        final String repere = etRepere.getText().toString().trim();
        final String lieu = etLieu.getText().toString().trim();

        executor.execute(() -> {
            String result;
            try {
                JSONObject json = new JSONObject();
                json.put("signature", "EQ-");
                json.put("idCarte", idCarte);
                json.put("idDistributeur", "1");
                json.put("nom", nom);
                json.put("email", email);
                json.put("numero", numero);
                json.put("password", sha256(password));
                json.put("validation", true);
                json.put("latitude", latitude);
                json.put("longitude", longitude);
                json.put("lieu", lieu);
                json.put("repere", repere);

                String url = BASE_URL + "?cashpoint=" +
                        URLEncoder.encode(json.toString(), "UTF-8");

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                result = code < 400 ? "OK : " + sb : "Erreur " + code + " : " + sb;
            } catch (Exception e) {
                result = "Erreur : " + e.getMessage();
            }
            final String msg = result;
            ui.post(() -> new AlertDialog.Builder(this)
                    .setTitle("Résultat")
                    .setMessage(msg)
                    .setPositiveButton("Fermer", null)
                    .show());
        });
    }

    private void stopScan() {
        waitingTag = false;
        if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
        if (scanDialog != null && scanDialog.isShowing()) scanDialog.dismiss();
    }

    private static String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(s.getBytes(StandardCharsets.UTF_8))).toLowerCase();
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }
}