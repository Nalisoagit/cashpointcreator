package mg.eqima.cashpointcreator;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateCashpointFragment extends Fragment {

    private TextInputEditText etNom, etEmail, etNumero, etPassword, etRepere, etLieu;
    private TextView tvCoord;
    private Double latitude = null, longitude = null;

    private volatile boolean waiting = false;
    private JSONObject pendingJson;
    private MainActivity host;

    private AlertDialog scanDialog, progressDialog;
    private TextView tvProgress;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> mapLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    latitude = result.getData().getDoubleExtra("lat", 0);
                    longitude = result.getData().getDoubleExtra("lng", 0);
                    refreshCoord();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_create, container, false);
        etNom = v.findViewById(R.id.etNom);
        etEmail = v.findViewById(R.id.etEmail);
        etNumero = v.findViewById(R.id.etNumero);
        etPassword = v.findViewById(R.id.etPassword);
        etRepere = v.findViewById(R.id.etRepere);
        etLieu = v.findViewById(R.id.etLieu);
        tvCoord = v.findViewById(R.id.tvCoord);

        v.findViewById(R.id.btnMap).setOnClickListener(x -> {
            Intent i = new Intent(requireContext(), MapPickerActivity.class);
            if (latitude != null && longitude != null) {
                i.putExtra("lat", latitude.doubleValue());
                i.putExtra("lng", longitude.doubleValue());
            }
            mapLauncher.launch(i);
        });
        v.findViewById(R.id.btnOk).setOnClickListener(x -> onOkClicked());
        refreshCoord();
        return v;
    }

    private void refreshCoord() {
        if (tvCoord == null) return;
        tvCoord.setText(latitude == null
                ? "Coordonnées : non définies"
                : "Coordonnées : " + latitude + " , " + longitude);
    }

    private String txt(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void onOkClicked() {
        String nom = txt(etNom), email = txt(etEmail), numero = txt(etNumero);
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
        String lieu = txt(etLieu), repere = txt(etRepere);

        if (nom.isEmpty() || email.isEmpty() || numero.isEmpty() || password.isEmpty()
                || lieu.isEmpty() || repere.isEmpty()) {
            toast("Tous les champs sont obligatoires");
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
        host = (MainActivity) requireActivity();
        if (!host.isNfcReady()) {
            toast("NFC indisponible ou désactivé");
            return;
        }

        try {
            pendingJson = new JSONObject();
            pendingJson.put("signature", "EQ-");
            pendingJson.put("idDistributeur", Environnement.ID_DISTRIBUTEUR);
            pendingJson.put("nom", nom);
            pendingJson.put("email", email);
            pendingJson.put("numero", numero);
            pendingJson.put("password", sha256(password));
            pendingJson.put("validation", true);
            pendingJson.put("latitude", latitude);
            pendingJson.put("longitude", longitude);
            pendingJson.put("lieu", lieu);
            pendingJson.put("repere", repere);
        } catch (Exception e) {
            toast("Erreur : " + e.getMessage());
            return;
        }

        waiting = true;
        host.setTagListener(this::handleTag);
        scanDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Carte NFC")
                .setMessage("Approchez la carte NFC du téléphone...")
                .setCancelable(false)
                .setNegativeButton("Annuler", (d, w) -> {
                    waiting = false;
                    host.setTagListener(null);
                })
                .show();
    }

    // Appelé depuis le thread NFC
    private void handleTag(Tag tag) {
        if (!waiting) return;
        waiting = false;
        host.setTagListener(null);

        final String idCarte = bytesToHex(tag.getId())
                .replaceAll("(.{2})(?!$)", "$1:");
        ui.post(() -> {
            if (!isAdded()) return;
            dismiss(scanDialog);
            showProgress("Écriture sur la carte...");
        });

        executor.execute(() -> {
            try {
                writeLogin(tag, pendingJson.getString("numero"));
            } catch (Exception e) {
                finish(false, "Écriture NFC échouée : " + e.getMessage());
                return;
            }
            ui.post(() -> setProgress("Enregistrement du cashpoint..."));
            try {
                pendingJson.put("idCarte", idCarte);
                String url = ApiClient.BASE_URL + "/cashpoint/create?cashpoint="
                        + URLEncoder.encode(pendingJson.toString(), "UTF-8");
                ApiClient.Response r = ApiClient.get(url);
                if (r.ok()) {
                    finish(true, "Cashpoint créé avec succès.");
                } else {
                    finish(false, "Erreur " + r.code + " : " + r.body);
                }
            } catch (Exception e) {
                finish(false, "Erreur : " + e.getMessage());
            }
        });
    }

    private void finish(boolean success, String message) {
        ui.post(() -> {
            if (!isAdded()) return;
            dismiss(progressDialog);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(success ? "Succès" : "Échec")
                    .setMessage(message)
                    .setPositiveButton("Fermer", null)
                    .show();
            resetForm();
        });
    }

    private void resetForm() {
        etNom.setText("");
        etEmail.setText("");
        etNumero.setText("");
        etPassword.setText("");
        etRepere.setText("");
        etLieu.setText("");
        latitude = null;
        longitude = null;
        pendingJson = null;
        refreshCoord();
        etNom.requestFocus();
    }

    private void writeLogin(Tag tag, String numero) throws Exception {
        String payload = "{\"login\":\"" + numero + "\"}";
        NdefMessage message = new NdefMessage(new NdefRecord[]{
                NdefRecord.createTextRecord("en", payload)});

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
            NdefFormatable f = NdefFormatable.get(tag);
            if (f == null) throw new Exception("Carte non compatible NDEF");
            f.connect();
            try {
                f.format(message);
            } finally {
                f.close();
            }
        }
    }

    private void showProgress(String text) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null);
        tvProgress = v.findViewById(R.id.tvProgress);
        tvProgress.setText(text);
        progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(v)
                .setCancelable(false)
                .show();
    }

    private void setProgress(String text) {
        if (tvProgress != null) tvProgress.setText(text);
    }

    private void dismiss(AlertDialog d) {
        if (d != null && d.isShowing()) d.dismiss();
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        waiting = false;
        if (host != null) host.setTagListener(null);
        dismiss(scanDialog);
        dismiss(progressDialog);
    }
}