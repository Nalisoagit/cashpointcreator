package mg.eqima.cashpointcreator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

public class ListCashpointFragment extends Fragment {

    private SwipeRefreshLayout swipe;
    private TextView tvEmpty, tvCount, tvTotal;
    private CashpointAdapter adapter;
    private final List<JSONObject> items = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_list, container, false);
        swipe = v.findViewById(R.id.swipe);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvCount = v.findViewById(R.id.tvCount);
        tvTotal = v.findViewById(R.id.tvTotal);
        RecyclerView rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CashpointAdapter(items);
        rv.setAdapter(adapter);

        swipe.setColorSchemeResources(R.color.primary, R.color.accent);
        swipe.setOnRefreshListener(this::load);
        swipe.post(() -> { swipe.setRefreshing(true); load(); });
        return v;
    }

    private void load() {
        executor.execute(() -> {
            List<JSONObject> result = new ArrayList<>();
            double total = 0;
            String error = null;
            try {
                ApiClient.Response r = ApiClient.get(
                        Environnement.BASE_URL + "/transaction/credit/credit-reste-general");
                if (r.ok()) {
                    JSONArray arr = new JSONArray(r.body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        result.add(o);
                        total += o.optDouble("resteCredit", 0);
                    }
                    Collections.sort(result, (a, b) ->
                            Integer.compare(b.optInt("idCashpoint", 0), a.optInt("idCashpoint", 0)));
                } else {
                    error = "Erreur " + r.code;
                }
            } catch (Exception e) {
                error = "Erreur : " + e.getMessage();
            }
            final String err = error;
            final double sum = total;
            ui.post(() -> {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                items.clear();
                items.addAll(result);
                adapter.notifyDataSetChanged();
                tvCount.setText(String.valueOf(items.size()));
                tvTotal.setText(CashpointAdapter.formatMontant(sum));
                if (err != null) {
                    tvEmpty.setText(err);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else if (items.isEmpty()) {
                    tvEmpty.setText("Aucun cashpoint");
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            });
        });
    }
}