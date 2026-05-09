package com.university.salumate;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * ReportsActivity — Business intelligence dashboard for the SaluMate shop.
 *
 * <p>Displays four key performance metrics aggregated from the SQLite database:</p>
 * <ul>
 *   <li><b>Total Revenue</b>    – Lifetime sum of all order prices (LKR)</li>
 *   <li><b>Monthly Sales</b>    – Sum of orders created in the current calendar month (LKR)</li>
 *   <li><b>Total Customers</b>  – Count of registered customers in the directory</li>
 *   <li><b>Total Orders</b>     – Count of all orders ever placed</li>
 * </ul>
 *
 * <p>Also provides a one-tap <b>Export as PDF</b> button that generates a formatted
 * A4 PDF report and saves it to the device's Downloads folder with a timestamp filename
 * (e.g. {@code 20240509_153042_report.pdf}).</p>
 */
public class ReportsActivity extends AppCompatActivity {

    /** Central database access helper. */
    private DBHandler dbHandler;

    /** TextViews bound to the four metric card values in the layout. */
    private TextView txtTotalRevenue, txtMonthlySales, txtTotalCustomers, txtTotalOrders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        dbHandler = new DBHandler(this);

        // Bind all four metric TextViews from the layout
        txtTotalRevenue   = findViewById(R.id.txtReportTotalRevenue);
        txtMonthlySales   = findViewById(R.id.txtReportMonthlySales);
        txtTotalCustomers = findViewById(R.id.txtReportTotalCustomers);
        txtTotalOrders    = findViewById(R.id.txtReportTotalOrders);

        // Trigger PDF generation and export when the button is pressed
        Button btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportPDF.setOnClickListener(v -> generateAndExportPDF());

        // Populate metrics on first load
        loadMetrics();
    }

    /**
     * Queries the database for all four business metrics and populates the
     * corresponding TextViews in the reports layout.
     * <p>Each query is executed independently against a read-only database handle.</p>
     */
    private void loadMetrics() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();

        // ── Total Revenue (all time) ──────────────────────────────────────────
        Cursor c1 = db.rawQuery("SELECT SUM(total_price) FROM Orders", null);
        if (c1 != null && c1.moveToFirst()) {
            txtTotalRevenue.setText(String.format("LKR %.2f", c1.getDouble(0)));
            c1.close();
        }

        // ── Monthly Sales (current calendar month only) ───────────────────────
        Cursor c2 = db.rawQuery(
                "SELECT SUM(total_price) FROM Orders " +
                "WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now')", null);
        if (c2 != null && c2.moveToFirst()) {
            txtMonthlySales.setText(String.format("LKR %.2f", c2.getDouble(0)));
            c2.close();
        }

        // ── Customer Count ────────────────────────────────────────────────────
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM Customers", null);
        if (c3 != null && c3.moveToFirst()) {
            txtTotalCustomers.setText(String.valueOf(c3.getInt(0)));
            c3.close();
        }

        // ── Order Count ───────────────────────────────────────────────────────
        Cursor c4 = db.rawQuery("SELECT COUNT(*) FROM Orders", null);
        if (c4 != null && c4.moveToFirst()) {
            txtTotalOrders.setText(String.valueOf(c4.getInt(0)));
            c4.close();
        }
    }

    /**
     * Generates a formatted A4 PDF report using the Android {@link android.graphics.pdf.PdfDocument}
     * API and saves it to the device's public Downloads folder.
     *
     * <h3>File Naming</h3>
     * <p>Files are named using a {@code yyyyMMdd_HHmmss_report.pdf} pattern so each
     * export produces a unique, time-stamped file without overwriting previous exports.</p>
     *
     * <h3>Storage Strategy</h3>
     * <ul>
     *   <li><b>Android Q (API 29)+</b>: Uses the {@link android.provider.MediaStore} scoped
     *       storage API to insert the file into the MediaStore Downloads collection.
     *       This does not require the {@code WRITE_EXTERNAL_STORAGE} permission.</li>
     *   <li><b>Android P (API 28) and below</b>: Falls back to writing directly to the
     *       public Downloads directory via {@link java.io.FileOutputStream}.</li>
     * </ul>
     */
    private void generateAndExportPDF() {
        try {
            // ── Create a new single-page A4 PDF document ──────────────────────
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();

            // A4 dimensions in points: 595 × 842 pt (standard portrait)
            android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create();
            android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);

            android.graphics.Canvas canvas = page.getCanvas();
            android.graphics.Paint  paint  = new android.graphics.Paint();

            // ── Report Title ──────────────────────────────────────────────────
            paint.setTextSize(28);
            paint.setColor(android.graphics.Color.rgb(145, 32, 61)); // Primary Burgundy
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            canvas.drawText("SaluMate — Shop Performance Report", 50, 70, paint);

            // ── Generation Timestamp ──────────────────────────────────────────
            paint.setTextSize(12);
            paint.setColor(android.graphics.Color.DKGRAY);
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
            String readableTimestamp = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            canvas.drawText("Generated: " + readableTimestamp, 50, 100, paint);

            // ── Horizontal divider line ───────────────────────────────────────
            paint.setColor(android.graphics.Color.LTGRAY);
            canvas.drawLine(50, 115, 545, 115, paint);

            // ── Financials Section ────────────────────────────────────────────
            paint.setTextSize(16);
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            canvas.drawText("Financials", 50, 145, paint);

            paint.setTextSize(13);
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
            canvas.drawText("Total Revenue (All Time):   " + txtTotalRevenue.getText(), 70, 170, paint);
            canvas.drawText("Monthly Sales (This Month): " + txtMonthlySales.getText(), 70, 195, paint);

            // ── Engagement Metrics Section ────────────────────────────────────
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            canvas.drawText("Customer & Order Metrics", 50, 240, paint);

            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL));
            canvas.drawText("Registered Customers: " + txtTotalCustomers.getText(), 70, 265, paint);
            canvas.drawText("Total Orders Placed:  " + txtTotalOrders.getText(), 70, 290, paint);

            // ── Footer ────────────────────────────────────────────────────────
            paint.setColor(android.graphics.Color.GRAY);
            paint.setTextSize(9);
            canvas.drawText(
                "This report was automatically generated by the SaluMate Shop Management System.",
                50, 820, paint);

            document.finishPage(page);

            // ── Determine output filename using a unique timestamp ─────────────
            String fileTimestamp = new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String fileName = fileTimestamp + "_report.pdf";

            // ── Save to Downloads folder ──────────────────────────────────────
            java.io.OutputStream outputStream = null;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+: use MediaStore (no WRITE_EXTERNAL_STORAGE permission needed)
                android.content.ContentValues contentValues = new android.content.ContentValues();
                contentValues.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (uri != null) {
                    outputStream = getContentResolver().openOutputStream(uri);
                }
            } else {
                // Android 9 and below: write directly to the public Downloads directory
                java.io.File file = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), fileName);
                outputStream = new java.io.FileOutputStream(file);
            }

            if (outputStream != null) {
                document.writeTo(outputStream);
                outputStream.close();
                Toast.makeText(this,
                        "✅ Exported: " + fileName + " → Downloads",
                        Toast.LENGTH_LONG).show();
            }

            document.close();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "PDF export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
