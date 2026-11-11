package mg.eqima.cashpointcreator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CashpointAdapter extends RecyclerView.Adapter<CashpointAdapter.VH> {

    private final List<JSONObject> data;

    public CashpointAdapter(List<JSONObject> data) { this.data = data; }

    public static String formatMontant(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvInitial, tvNom, tvInfo, tvReste, tvBadge;
        VH(View v) {
            super(v);
            tvInitial = v.findViewById(R.id.tvInitial);
            tvNom = v.findViewById(R.id.tvNom);
            tvInfo = v.findViewById(R.id.tvInfo);
            tvReste = v.findViewById(R.id.tvReste);
            tvBadge = v.findViewById(R.id.tvBadge);
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
        String nom = o.optString("nomCashpoint", "").trim();
        String affiche = nom.isEmpty() ? "(sans nom)" : nom;

        h.tvInitial.setText(nom.isEmpty() ? "?" : nom.substring(0, 1).toUpperCase());
        h.tvNom.setText(affiche);
        h.tvInfo.setText("ID " + o.optString("idCashpoint", "") + " · " + o.optString("daty", ""));
        h.tvReste.setText(formatMontant(o.optDouble("resteCredit", 0)));

        boolean actif = o.optInt("validation", 0) == 1;
        h.tvBadge.setText(actif ? "Actif" : "Inactif");
        h.tvBadge.setBackgroundResource(actif ? R.drawable.bg_badge_active : R.drawable.bg_badge_inactive);
        h.tvBadge.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                actif ? R.color.success : R.color.danger));
    }

    @Override
    public int getItemCount() { return data.size(); }
}