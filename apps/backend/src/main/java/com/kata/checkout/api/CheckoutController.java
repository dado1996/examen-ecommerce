package com.kata.checkout.api;

import com.kata.checkout.api.dto.CheckoutBreakdownResponse;
import com.kata.checkout.api.dto.CheckoutRequest;
import com.kata.checkout.api.dto.CheckoutResponse;
import com.kata.checkout.application.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/checkouts} y {@code /checkouts/preview} (HU2, HU3, HU4). */
@RestController
@RequestMapping("/api/v1/checkouts")
public class CheckoutController {

    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/preview")
    public CheckoutBreakdownResponse preview(@Valid @RequestBody CheckoutRequest request) {
        return CheckoutBreakdownResponse.from(checkout.preview(request.toCommand()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse create(@Valid @RequestBody CheckoutRequest request) {
        return CheckoutResponse.from(checkout.checkout(request.toCommand()));
    }
}
