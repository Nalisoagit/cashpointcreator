package mg.eqima.cashpointcreator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

public class CashpointAdapter extends RecyclerView.Adapter<CashpointAdapter.VH> {

    private final List<JSONObject> data;

    public CashpointAdapter(List<JSONObject> data) { this.data = data; }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvInitial, tvNom, tvNumero, tvEmail, tvLieu, tvCarte;
        VH(View v) {
            super(v);
            tvInitial = v.findViewById(R.id.tvInitial);
            tvNom = v.findViewById(R.id.tvNom);
            tvNumero = v.findViewById(R.id.tvNumero);
            tvEmail = v.findViewById(R.id.tvEmail);
            tvLieu = v.findViewById(R.id.tvLieu);
            tvCarte = v.findViewById(R.id.tvCarte);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cashpoint, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        JSONObject o = data.get(position);
        String nom = o.optString("nom", "");
        h.tvInitial.setText(nom.isEmpty() ? "?" : nom.substring(0, 1).toUpperCase());
        h.tvNom.setText(nom);
        h.tvNumero.setText(o.optString("numero", ""));
        h.tvEmail.setText(o.optString("email", ""));
        String lieu = o.optString("lieu", "");
        String repere = o.optString("repere", "");
        if ("null".equals(repere)) repere = "";
        h.tvLieu.setText(repere.isEmpty() ? lieu : lieu + " · " + repere);
        h.tvCarte.setText("Carte : " + o.optString("idCarte", ""));
    }

    @Override
    public int getItemCount() { return data.size(); }
}