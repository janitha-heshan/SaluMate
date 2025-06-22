package com.university.salumate;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class DressAdapter extends BaseAdapter {

    Context context;
    ArrayList<HashMap<String, String>> dresses;
    LayoutInflater inflater;

    public DressAdapter(Context context, ArrayList<HashMap<String, String>> dresses) {
        this.context = context;
        this.dresses = dresses;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return dresses.size();
    }

    @Override
    public Object getItem(int i) {
        return dresses.get(i);
    }

    @Override
    public long getItemId(int i) {
        return Long.parseLong(dresses.get(i).get("dress_template_id"));
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = inflater.inflate(R.layout.customer_list_item, null);

        TextView name = view.findViewById(R.id.textCustomerName);
        TextView updated = view.findViewById(R.id.textCustomerPhone);
        Button edit = view.findViewById(R.id.btnEdit);
        Button delete = view.findViewById(R.id.btnDelete);

        HashMap<String, String> item = dresses.get(i);
        name.setText(item.get("dress_name"));
        updated.setText("Updated: " + item.get("updated_at"));

        edit.setOnClickListener(v -> {
            Intent intent = new Intent(context, UpdateDressActivity.class);
            intent.putExtra("dress_template_id", Long.parseLong(item.get("dress_template_id")));
            context.startActivity(intent);
        });

        delete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Dress")
                    .setMessage("Are you sure you want to delete this dress?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        DBHandler db = new DBHandler(context);
                        if (db.deleteDress(Long.parseLong(item.get("dress_template_id")))) {
                            dresses.remove(i);
                            notifyDataSetChanged();
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        view.setOnClickListener(v -> {
            Intent intent = new Intent(context, UpdateDressActivity.class);
            intent.putExtra("dress_template_id", Long.parseLong(item.get("dress_template_id")));
            context.startActivity(intent);
        });

        return view;
    }
}
