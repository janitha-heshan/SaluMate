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

public class OrderAdapter extends BaseAdapter {

    private Context context;
    private ArrayList<HashMap<String, String>> orders;
    private LayoutInflater inflater;
    private DBHandler db;

    public OrderAdapter(Context context, ArrayList<HashMap<String, String>> orders, DBHandler db) {
        this.context = context;
        this.orders = orders;
        this.inflater = LayoutInflater.from(context);
        this.db = db;
    }

    @Override
    public int getCount() {
        return orders.size();
    }

    @Override
    public Object getItem(int i) {
        return orders.get(i);
    }

    @Override
    public long getItemId(int i) {
        return Long.parseLong(orders.get(i).get("order_id"));
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if(view == null) {
            view = inflater.inflate(R.layout.customer_list_item, null);
        }

        TextView title = view.findViewById(R.id.textCustomerName);
        TextView subtitle = view.findViewById(R.id.textCustomerPhone);
        Button edit = view.findViewById(R.id.btnEdit);
        Button delete = view.findViewById(R.id.btnDelete);

        HashMap<String, String> item = orders.get(i);
        long orderId = Long.parseLong(item.get("order_id"));

        title.setText("Order #" + item.get("order_id") + " - " + item.get("status"));
        
        String dueDateStr = item.get("due_date");
        if (dueDateStr != null && !dueDateStr.isEmpty()) {
            subtitle.setText("Created: " + item.get("created_at") + "  |  Due: " + dueDateStr);
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                java.util.Date dueDate = sdf.parse(dueDateStr);
                long diff = dueDate.getTime() - new java.util.Date().getTime();
                long days = java.util.concurrent.TimeUnit.DAYS.convert(diff, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (days <= 3 && !"Completed".equalsIgnoreCase(item.get("status"))) {
                    view.setBackgroundColor(android.graphics.Color.parseColor("#FFEAEA")); // Light red/pink
                } else if ("Completed".equalsIgnoreCase(item.get("status"))) {
                    view.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")); // Light green
                } else {
                    view.setBackgroundColor(android.graphics.Color.WHITE);
                }
            } catch (Exception e) {
                view.setBackgroundColor(android.graphics.Color.WHITE);
            }
        } else {
            subtitle.setText("Created: " + item.get("created_at") + "  |  Cost: LKR " + item.get("total"));
            if ("Completed".equalsIgnoreCase(item.get("status"))) {
                view.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")); 
            } else {
                view.setBackgroundColor(android.graphics.Color.WHITE);
            }
        }

        // Repurpose "Edit" button to "View"
        edit.setText("VIEW");
        edit.setOnClickListener(v -> {
            Intent intent = new Intent(context, OrderDetailsActivity.class);
            intent.putExtra("order_id", orderId);
            context.startActivity(intent);
        });

        delete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Order")
                    .setMessage("Are you sure you want to delete this order?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        db.getWritableDatabase().execSQL("DELETE FROM Orders WHERE order_id = " + orderId);
                        orders.remove(i);
                        notifyDataSetChanged();
                        Toast.makeText(context, "Order Deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        view.setOnClickListener(v -> {
            Intent intent = new Intent(context, OrderDetailsActivity.class);
            intent.putExtra("order_id", orderId);
            context.startActivity(intent);
        });

        return view;
    }
}
