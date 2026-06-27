package com.example.TravelApp.controller;

import com.example.TravelApp.model.Booking;
import com.example.TravelApp.model.Hotel;
import com.example.TravelApp.model.TourPackage;
import com.example.TravelApp.model.User;
import com.example.TravelApp.service.BookingService;
import com.example.TravelApp.service.EmailService;
import com.example.TravelApp.service.HotelService;
import com.example.TravelApp.service.TourPackageService;
import com.example.TravelApp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
public class BookingController {

    private final BookingService bookingService;
    private final TourPackageService tourPackageService;
    private final UserService userService;
    private final EmailService emailService;
    private final HotelService hotelService;

    public BookingController(BookingService bookingService, TourPackageService tourPackageService, UserService userService, EmailService emailService, HotelService hotelService) {
        this.bookingService = bookingService;
        this.tourPackageService = tourPackageService;
        this.userService = userService;
        this.emailService = emailService;
        this.hotelService = hotelService;
    }

    // 🎯 URL එකේ ඇල ඉරි ප්‍රශ්න ආවොත් බේරෙන්න Mappings දෙකක්ම දමා ඇත
    @GetMapping({"/book/{packageId}", "/book/{packageId}/"})
    public String bookPackage(@PathVariable Long packageId, Model model) {
        TourPackage tourPackage = tourPackageService.findById(packageId).orElse(null);
        if (tourPackage == null) {
            return "redirect:/packages";
        }
        model.addAttribute("package", tourPackage);
        model.addAttribute("booking", new Booking());
        return "book-package";
    }

    @PostMapping("/book")
    public String placeBooking(@ModelAttribute Booking booking,
                               @RequestParam Long packageId,
                               @RequestParam String userEmail,
                               @RequestParam(required = false) Long hotelId,
                               @RequestParam(required = false, defaultValue = "1") Integer hotelNights, // HTML එකෙන් එන දවස් ගණන
                               @RequestParam(required = false) String foodType,       // 🎯 HTML එකෙන් එන කෑම වර්ගය
                               @RequestParam(required = false) String transportMode, // 🎯 HTML එකෙන් එන වාහන වර්ගය
                               Model model) {
        Optional<TourPackage> optionalPackage = tourPackageService.findById(packageId);
        Optional<User> optionalUser = userService.findByEmail(userEmail);
        if (optionalPackage.isEmpty()) {
            return "redirect:/packages";
        }
        TourPackage tourPackage = optionalPackage.get();

        if (optionalUser.isEmpty()) {
            model.addAttribute("error", "User not found. Please register or use a valid email.");
            model.addAttribute("package", tourPackage);
            model.addAttribute("booking", booking == null ? new Booking() : booking);
            return "book-package";
        }
        User user = optionalUser.get();
        booking.setTourPackage(tourPackage);
        booking.setUser(user);

        // 🏨 💰 හෝටලය අනුව අමතර මිල ගැනීම
        double basePrice = tourPackage.getPrice();
        double extraPricePerNight = 0.0;

        if (hotelId != null) {
            Optional<Hotel> optionalHotel = hotelService.findById(hotelId);
            if (optionalHotel.isPresent()) {
                Hotel hotel = optionalHotel.get();
                extraPricePerNight = hotel.getExtraPrice();
            }
        }

        // 🍔 🎯 කෑම වර්ගය අනුව මිල තීරණය කිරීම (HTML එකේ මිල ගණන් වලට සර්වසම වේ)
        double foodPrice = 0.0;
        if (foodType != null && hotelId != null) { // හෝටලයක් තෝරාගෙන තිබේ නම් පමණක්
            switch (foodType) {
                case "local" -> foodPrice = 500.00;
                case "western" -> foodPrice = 1500.00;
                case "asian" -> foodPrice = 1000.00;
                case "indian" -> foodPrice = 800.00;
            }
        }

        // 🚗 🎯 වාහන වර්ගය අනුව මිල තීරණය කිරීම (HTML එකේ මිල ගණන් වලට සර්වසම වේ)
        double transPrice = 0.0;
        if (transportMode != null && hotelId != null) { // හෝටලයක් තෝරාගෙන තිබේ නම් පමණක්
            switch (transportMode) {
                case "bike" -> transPrice = 1000.00;
                case "threewheel" -> transPrice = 1500.00;
                case "car" -> transPrice = 3500.00;
            }
        }

        // දවස් ගණන බිංදුවක් හෝ හිස් නම් Default 1ක් ලෙස ගනී
        int days = (hotelNights != null && hotelNights > 0) ? hotelNights : 1;

        // 🧮 🎯 අවසාන සුපිරි සූත්‍රය: (මූලික මිල * සෙනඟ) + [(හෝටල් මිල + කෑම මිල + වාහන මිල) * සෙනඟ * දවස් ගණන]
        double totalExtraCostPerPersonPerDay = extraPricePerNight + foodPrice + transPrice;
        double totalPrice = (basePrice * booking.getTravelers()) + (totalExtraCostPerPersonPerDay * booking.getTravelers() * days);

        booking.setTotalPrice(totalPrice);

        // 💾 ඩේටාබේස් එකට දවස් ගණන සේව් කිරීම (Booking.java එකේ field එක තිබේ නම් පමණක් මෙය uncomment කරන්න)
        // booking.setHotelNights(days);

        // 💾 Booking එක Database එකට සේව් කිරීම
        bookingService.save(booking);

        // 📧 බුකින් එක සේව් වුණු ගමන් යූසර්ට HTML Email එකක් යැවීම
        try {
            emailService.sendBookingSuccessEmail(user.getEmail(), user.getName(), tourPackage.getName(), totalPrice);
        } catch (Exception e) {
            System.out.println("❌ Email Sending Failed: " + e.getMessage());
        }

        model.addAttribute("booking", booking);
        return "booking-confirmation";
    }

    @GetMapping("/bookings")
    public String bookingHistory(@RequestParam String userEmail, Model model) {
        Optional<User> user = userService.findByEmail(userEmail);
        if (user.isEmpty()) {
            model.addAttribute("bookings", List.of());
            model.addAttribute("userEmail", userEmail);
            return "booking-history";
        }
        List<Booking> bookings = bookingService.findByUser(user.get());
        model.addAttribute("bookings", bookings);
        model.addAttribute("userEmail", userEmail);
        return "booking-history";
    }
}