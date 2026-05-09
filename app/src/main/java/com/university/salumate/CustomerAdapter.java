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

/**
 * CustomerAdapter — List adapter for displaying {@link Customer} objects in a ListView.
 *
 * <p>Inflates each row using the {@code customer_list_item} layout, which shows
 * a customer name, phone number, and two action buttons (Edit / Delete).</p>
 *
 * <ul>
 *   <li><b>Edit</b>: Navigates to {@link UpdateCustomerActivity} passing the customer's ID.</li>
 *   <li><b>Delete</b>: Shows a confirmation dialog; on confirm, removes the record from
 *       the database and notifies the adapter to refresh the list immediately.</li>
 * </ul>
 */
public class CustomerAdapter extends ArrayAdapter<Customer> {

    /** The application context used for inflating views and starting activities. */
    private final Context context;

    /** The live in-memory list of customers driving the adapter. */
    private final List<Customer> customerList;

    /** Database access helper for performing the delete operation. */
    private final DBHandler dbHandler;

    /**
     * Creates a new CustomerAdapter.
     *
     * @param context      Application or Activity context.
     * @param list         The list of {@link Customer} objects to display.
     * @param dbHandler    The database handler for delete operations.
     */
    public CustomerAdapter(Context context, List<Customer> list, DBHandler dbHandler) {
        super(context, 0, list);
        this.context      = context;
        this.customerList = list;
        this.dbHandler    = dbHandler;
    }

    /**
     * Inflates or recycles a customer row view and binds the customer data and
     * action button listeners for the given position.
     *
     * <p>Uses the View-recycling pattern ({@code convertView} reuse) to avoid
     * unnecessary inflation on every scroll event.</p>
     *
     * @param position    Position of the item in the data set.
     * @param convertView A previously recycled view to reuse, or null if none is available.
     * @param parent      The parent ViewGroup the view will be attached to.
     * @return The populated row view for this position.
     */
    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Customer customer = customerList.get(position);

        // Inflate the row layout if we don't have a recycled view to reuse
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.customer_list_item, parent, false);
        }

        // Bind customer data to the row's TextViews
        TextView name  = convertView.findViewById(R.id.textCustomerName);
        TextView phone = convertView.findViewById(R.id.textCustomerPhone);
        Button btnEdit   = convertView.findViewById(R.id.btnEdit);
        Button btnDelete = convertView.findViewById(R.id.btnDelete);

        name.setText(customer.name);
        phone.setText(customer.phone);

        // Edit button: open the update form for this customer
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(context, UpdateCustomerActivity.class);
            intent.putExtra("customer_id", customer.id);
            context.startActivity(intent);
        });

        // Delete button: confirm before permanently removing from the database
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Customer")
                    .setMessage("Are you sure you want to permanently remove \"" + customer.name + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        dbHandler.deleteCustomer(customer.id);
                        customerList.remove(position);
                        notifyDataSetChanged(); // Refresh the list immediately
                        Toast.makeText(context, "Customer removed.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        return convertView;
    }
}
