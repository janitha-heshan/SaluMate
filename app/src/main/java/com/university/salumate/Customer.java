package com.university.salumate;

/**
 * Customer — Plain data model representing a customer in the SaluMate system.
 *
 * <p>Used as the list item object by {@link CustomerAdapter} when displaying the
 * customer directory in {@link AllCustomersActivity}. Intentionally lightweight —
 * only the fields needed for list display are stored here. Full customer details
 * (address, GPS coordinates) are fetched on demand via {@link DBHandler}.</p>
 */
public class Customer {

    /** The unique database primary key for this customer (customer_id). */
    public long id;

    /** The customer's full display name. */
    public String name;

    /** The customer's primary contact phone number. */
    public String phone;

    /**
     * Constructs a Customer instance with all required display fields.
     *
     * @param id    The database primary key (customer_id).
     * @param name  Full name of the customer.
     * @param phone Primary contact number.
     */
    public Customer(long id, String name, String phone) {
        this.id    = id;
        this.name  = name;
        this.phone = phone;
    }
}
