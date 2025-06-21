package com.university.salumate;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

public class CustomerAdapter extends ArrayAdapter<Customer> {

    private Context context;
    private List<Customer> customerList;
    private DBHandler dbHandler;

    public CustomerAdapter(Context context, List<Customer> list, DBHandler dbHandler) {
        super(context, 0, list);
        this.context = context;
        this.customerList = list;
        this.dbHandler = dbHandler;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Customer customer = customerList.get(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.customer_list_item, parent, false);
        }

        TextView name = convertView.findViewById(R.id.textCustomerName);
        TextView phone = convertView.findViewById(R.id.textCustomerPhone);
        Button btnEdit = convertView.findViewById(R.id.btnEdit);
        Button btnDelete = convertView.findViewById(R.id.btnDelete);

        name.setText(customer.name);
        phone.setText(customer.phone);

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(context, UpdateCustomerActivity.class);
            intent.putExtra("customer_id", customer.id);
            context.startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete this customer?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        dbHandler.deleteCustomer(customer.id);
                        customerList.remove(position);
                        notifyDataSetChanged();
                        Toast.makeText(context, "Customer deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        return convertView;
    }
}

