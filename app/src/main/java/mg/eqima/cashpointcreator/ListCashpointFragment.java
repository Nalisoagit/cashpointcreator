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

public class ListCashpointFragment extends Fragment {

    private SwipeRefreshLayout swipe;
    private TextView tvEmpty;
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
        RecyclerView rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CashpointAdapter(items);
        rv.setAdapter(adapter);

        swipe.setOnRefreshListener(this::load);
        swipe.post(() -> { swipe.setRefreshing(true); load(); });
        return v;
    }

    private void load() {
        executor.execute(() -> {
            List<JSONObject> result = new ArrayList<>();
            String error = null;
            try {
                ApiClient.Response r = ApiClient.get(ApiClient.BASE_URL + "/cashpoint/findAll");
                if (r.ok()) {
                    JSONArray arr = new JSONArray(r.body);
                    for (int i = 0; i < arr.length(); i++) result.add(arr.getJSONObject(i));
                } else {
                    error = "Erreur " + r.code;
                }
            } catch (Exception e) {
                error = "Erreur : " + e.getMessage();
            }
            final String err = error;
            ui.post(() -> {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                items.clear();
                items.addAll(result);
                adapter.notifyDataSetChanged();
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