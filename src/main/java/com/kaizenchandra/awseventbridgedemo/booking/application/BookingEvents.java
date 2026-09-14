package com.kaizenchandra.awseventbridgedemo.booking.application;

import com.kaizenchandra.awseventbridgedemo.booking.domain.Booking;

public interface BookingEvents {
    void append(Booking booking);
}
