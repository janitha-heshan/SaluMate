package com.university.salumate;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ReportsActivity extends AppCompatActivity {

    private DBHandler dbHandler;
    private TextView txtTotalRevenue, txtMonthlySales, txtTotalCustomers, txtTotalOrders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        dbHandler = new DBHandler(this);

        txtTotalRevenue = findViewById(R.id.txtReportTotalRevenue);
        txtMonthlySales = findViewById(R.id.txtReportMonthlySales);
        txtTotalCustomers = findViewById(R.id.txtReportTotalCustomers);
        txtTotalOrders = findViewById(R.id.txtReportTotalOrders);

        Button btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportPDF.setOnClickListener(v -> generateAndExportPDF());

        loadMetrics();
    }

    private void loadMetrics() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();

        // Total Revenue
        Cursor c1 = db.rawQuery("SELECT SUM(total_price) FROM Orders", null);
        if (c1 != null && c1.moveToFirst()) {
            double total = c1.getDouble(0);
            txtTotalRevenue.setText(String.format("LKR %.2f", total));
            c1.close();
        }

        // Monthly Sales
        Cursor c2 = db.rawQuery("SELECT SUM(total_price) FROM Orders WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now')", null);
        if (c2 != null && c2.moveToFirst()) {
            double monthly = c2.getDouble(0);
            txtMonthlySales.setText(String.format("LKR %.2f", monthly));
            c2.close();
        }

        // Total Customers
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM Customers", null);
        if (c3 != null && c3.moveToFirst()) {
            int customers = c3.getInt(0);
            txtTotalCustomers.setText(String.valueOf(customers));
            c3.close();
        }

        // Total Orders
        Cursor c4 = db.rawQuery("SELECT COUNT(*) FROM Orders", null);
        if (c4 != null && c4.moveToFirst()) {
            int orders = c4.getInt(0);
            txtTotalOrders.setText(String.valueOf(orders));
            c4.close();
        }
    }

    private void generateAndExportPDF() {
        try {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create(); // Standard A4 specs
            android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);
            
            android.graphics.Canvas canvas = page.getCanvas();
            android.graphics.Paint paint = new android.graphics.Paint();
            
            paint.setTextSize(28);
            paint.setColor(android.graphics.Color.rgb(0, 51, 153)); // Primary dark
            paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            canvas.drawText("SaluMate Shop Performance Report", 50, 70, paint);
            
            paint.setTextSize(16);
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
            
            String timeStamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
            canvas.drawText("Generated On: " + timeStamp, 50, 110, paint);
            
            paint.setTextSize(18);
            canvas.drawText("Financials", 50, 160, paint);
            paint.setTextSize(14);
            canvas.drawText("Total Overall Revenue: " + txtTotalRevenue.getText().toString(), 70, 190, paint);
            canvas.drawText("Current Monthly Sales: " + txtMonthlySales.getText().toString(), 70, 220, paint);
            
            paint.setTextSize(18);
            canvas.drawText("Customer & Engagement Metrics", 50, 270, paint);
            paint.setTextSize(14);
            canvas.drawText("Overall Registered Customers: " + txtTotalCustomers.getText().toString(), 70, 300, paint);
            canvas.drawText("Total Active Lifetime Orders: " + txtTotalOrders.getText().toString(), 70, 330, paint);
            
            paint.setColor(android.graphics.Color.GRAY);
            paint.setTextSize(10);
            canvas.drawText("Automated Report by SaluMate Point of Sale Application.", 50, 800, paint);
            
            document.finishPage(page);

            String fileTimeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
            String fileName = fileTimeStamp + "_report.pdf";
            java.io.OutputStream fos = null;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    fos = getContentResolver().openOutputStream(uri);
                }
            } else {
                java.io.File file = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName);
                fos = new java.io.FileOutputStream(file);
            }

            if (fos != null) {
                document.writeTo(fos);
                fos.close();
                Toast.makeText(this, "Exported: " + fileName + " into Downloads Folder!", Toast.LENGTH_LONG).show();
            }
            document.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to Generate PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
