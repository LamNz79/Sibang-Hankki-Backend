package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.PageData;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.RestaurantStatus;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserRole;
import com.sibang.hankki.admin.application.port.out.SystemAdminQueryPort.UserStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAdminReadServiceTest {

    @Mock
    private SystemAdminQueryPort queryPort;

    private SystemAdminReadService service;

    @BeforeEach
    void setUp() {
        service = new SystemAdminReadService(queryPort);
    }

    @Test
    void normalizesAndMapsUserFilters() {
        given(queryPort.findUsers(1, 50, "Minh", UserRole.CUSTOMER, UserStatus.ACTIVE))
                .willReturn(new PageData<>(List.of(), 0, 0));

        var page = service.findUsers(1, 50, "  Minh  ", "CUSTOMER", "ACTIVE");

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(50);
        verify(queryPort).findUsers(1, 50, "Minh", UserRole.CUSTOMER, UserStatus.ACTIVE);
    }

    @Test
    void normalizesAndMapsRestaurantFilters() {
        given(queryPort.findRestaurants(0, 20, "Royal", RestaurantStatus.SUSPENDED, "ho-chi-minh", 79))
                .willReturn(new PageData<>(List.of(), 0, 0));

        service.findRestaurants(0, 20, " Royal ", "SUSPENDED", " ho-chi-minh ", 79);

        verify(queryPort).findRestaurants(0, 20, "Royal", RestaurantStatus.SUSPENDED, "ho-chi-minh", 79);
    }

    @Test
    void rejectsInvalidPageSizeRoleAndStatus() {
        assertThatThrownBy(() -> service.findUsers(-1, 20, null, null, null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findUsers(0, 25, null, null, null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findUsers(0, 20, null, "SUPERADMIN", null))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.findRestaurants(0, 20, null, "DELETED", null))
                .isInstanceOf(InvalidAdminQueryException.class);
    }
}
