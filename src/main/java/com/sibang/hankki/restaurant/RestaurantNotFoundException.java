package com.sibang.hankki.restaurant;

class RestaurantNotFoundException extends RuntimeException {

    RestaurantNotFoundException() {
        super("Restaurant not found");
    }
}
