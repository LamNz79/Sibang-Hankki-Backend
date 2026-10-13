package com.sibang.hankki.admin.application.port.in;

import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurant;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUser;
import java.util.UUID;

public interface SystemAdminStatusUseCase {

    AdminUser updateUserStatus(UUID userId, String status);

    AdminRestaurant updateRestaurantStatus(UUID restaurantId, String status);
}
