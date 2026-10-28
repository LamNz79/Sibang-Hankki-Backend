package com.sibang.hankki.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sibang.hankki.admin.application.exception.AdminResourceNotFoundException;
import com.sibang.hankki.admin.application.exception.AdminRestaurantConflictException;
import com.sibang.hankki.admin.application.exception.InvalidAdminQueryException;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.CreateRestaurantCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.UpdateRestaurantCommand;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileData;
import com.sibang.hankki.admin.application.port.out.SystemAdminRestaurantPort.RestaurantProfileUpdate;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAdminRestaurantServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");

    @Mock
    private SystemAdminRestaurantPort port;

    @Mock
    private LocationCatalogUseCase locationCatalog;

    private SystemAdminRestaurantService service;

    @BeforeEach
    void setUp() {
        service = new SystemAdminRestaurantService(port, locationCatalog, Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(locationCatalog.findProvince(79))
                .thenReturn(Optional.of(new Province(79, "ho_chi_minh", "Thành phố Hồ Chí Minh", "thành phố")));
        org.mockito.Mockito.lenient().when(locationCatalog.findWard(79, 26734))
                .thenReturn(Optional.of(new Ward(26734, "phuong_ben_nghe", "Phường Bến Nghé", "phường", 79)));
    }

    @Test
    void createsTrimmedDraftWithApplicationUuidAndDefaultTimezone() {
        given(port.create(any())).willAnswer(invocation -> {
            RestaurantProfileData value = invocation.getArgument(0);
            return new RestaurantProfileData(
                    value.id(), value.slug(), value.name(), value.description(), value.cuisineType(),
                    value.cuisineLabel(), value.citySlug(), value.area(), value.district(), value.provinceCode(),
                    value.provinceName(), value.wardCode(), value.wardName(), value.address(),
                    value.phone(), value.email(), value.timezone(), value.priceRange(), value.approvalStatus(),
                    NOW, NOW);
        });

        var result = service.create(new CreateRestaurantCommand(
                " new-restaurant ", " Restaurant ", " ", null, null, null, null, null, null, null, 79, 26734));

        assertThat(result.id()).isNotNull();
        assertThat(result.slug()).isEqualTo("new-restaurant");
        assertThat(result.name()).isEqualTo("Restaurant");
        assertThat(result.description()).isNull();
        assertThat(result.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(result.citySlug()).isEqualTo("ho-chi-minh");
        assertThat(result.district()).isEqualTo("Phường Bến Nghé");
        assertThat(result.provinceName()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(result.approvalStatus()).isEqualTo("DRAFT");
    }

    @Test
    void rejectsInvalidInputAndDuplicateSlug() {
        assertThatThrownBy(() -> service.create(command("Not-Kebab")))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.create(new CreateRestaurantCommand(
                "valid-slug", " ", null, null, null, null, null, null, null, null, 79, 26734)))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.create(new CreateRestaurantCommand(
                "valid-slug", "Name", null, null, null, null, null, null, null, null, null, 26734)))
                .isInstanceOf(InvalidAdminQueryException.class);
        assertThatThrownBy(() -> service.create(new CreateRestaurantCommand(
                "valid-slug", "Name", null, null, null, null, null, null, "Not/A_Timezone", null, 79, 26734)))
                .isInstanceOf(InvalidAdminQueryException.class);

        given(port.existsBySlug("existing-slug")).willReturn(true);
        assertThatThrownBy(() -> service.create(command("existing-slug")))
                .isInstanceOf(AdminRestaurantConflictException.class);
        verify(port, never()).create(any());
    }

    @Test
    void rejectsUnknownProvinceAndWardOutsideSelectedProvince() {
        given(locationCatalog.findProvince(80)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new CreateRestaurantCommand(
                "valid-slug", "Name", null, null, null, null, null, null, null, null, 80, 27694)))
                .isInstanceOf(InvalidAdminQueryException.class);

        given(locationCatalog.findWard(79, 27694)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new CreateRestaurantCommand(
                "another-slug", "Name", null, null, null, null, null, null, null, null, 79, 27694)))
                .isInstanceOf(InvalidAdminQueryException.class);

        verify(port, never()).create(any());
    }

    @Test
    void updatesEditableFieldsWithoutChangingSlugOrApprovalStatus() {
        RestaurantProfileData current = restaurant("DRAFT");
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(current));
        given(port.update(any(), any())).willAnswer(invocation -> {
            RestaurantProfileUpdate update = invocation.getArgument(1);
            return new RestaurantProfileData(
                    RESTAURANT_ID, current.slug(), update.name(), update.description(), update.cuisineType(),
                    update.cuisineLabel(), update.citySlug(), update.area(), update.district(), update.provinceCode(),
                    update.provinceName(), update.wardCode(), update.wardName(), update.address(),
                    update.phone(), update.email(), update.timezone(), update.priceRange(), current.approvalStatus(),
                    current.createdAt(), NOW);
        });

        var result = service.update(RESTAURANT_ID, updateCommand(" Updated "));

        assertThat(result.name()).isEqualTo("Updated");
        assertThat(result.slug()).isEqualTo("restaurant-slug");
        assertThat(result.approvalStatus()).isEqualTo("DRAFT");
        assertThat(result.area()).isEqualTo("Legacy area");
        verify(port).findByIdForUpdate(RESTAURANT_ID);
    }

    @Test
    void allowsTimezoneChangeBeforeBookingSetupStarts() {
        RestaurantProfileData current = restaurant("DRAFT");
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(current));
        given(port.update(any(), any())).willAnswer(invocation -> {
            RestaurantProfileUpdate update = invocation.getArgument(1);
            return new RestaurantProfileData(
                    RESTAURANT_ID, current.slug(), update.name(), update.description(), update.cuisineType(),
                    update.cuisineLabel(), update.citySlug(), update.area(), update.district(), update.provinceCode(),
                    update.provinceName(), update.wardCode(), update.wardName(), update.address(),
                    update.phone(), update.email(), update.timezone(), update.priceRange(), current.approvalStatus(),
                    current.createdAt(), NOW);
        });

        var result = service.update(RESTAURANT_ID, updateCommand("Restaurant", "Asia/Seoul"));

        assertThat(result.timezone()).isEqualTo("Asia/Seoul");
        verify(port).hasBookingSlots(RESTAURANT_ID);
        verify(port).hasReservations(RESTAURANT_ID);
    }

    @Test
    void rejectsTimezoneChangeAfterBookingSlotsExist() {
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(restaurant("DRAFT")));
        given(port.hasBookingSlots(RESTAURANT_ID)).willReturn(true);

        assertThatThrownBy(() -> service.update(
                RESTAURANT_ID, updateCommand("Restaurant", "Asia/Seoul")))
                .isInstanceOf(AdminRestaurantConflictException.class)
                .hasMessageContaining("timezone cannot be changed");

        verify(port, never()).update(any(), any());
    }

    @Test
    void rejectsTimezoneChangeAfterReservationsExist() {
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(restaurant("DRAFT")));
        given(port.hasBookingSlots(RESTAURANT_ID)).willReturn(false);
        given(port.hasReservations(RESTAURANT_ID)).willReturn(true);

        assertThatThrownBy(() -> service.update(
                RESTAURANT_ID, updateCommand("Restaurant", "Asia/Seoul")))
                .isInstanceOf(AdminRestaurantConflictException.class)
                .hasMessageContaining("timezone cannot be changed");

        verify(port, never()).update(any(), any());
    }

    @Test
    void findAndUpdateHideMissingOrArchivedRestaurants() {
        given(port.findById(RESTAURANT_ID)).willReturn(Optional.empty());
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(RESTAURANT_ID))
                .isInstanceOf(AdminResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(RESTAURANT_ID, updateCommand("Name")))
                .isInstanceOf(AdminResourceNotFoundException.class);
    }

    @Test
    void archivesOnlyUnreferencedDraftRestaurant() {
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(restaurant("DRAFT")));

        service.archive(RESTAURANT_ID);

        ArgumentCaptor<Instant> deletedAt = ArgumentCaptor.forClass(Instant.class);
        verify(port).archive(org.mockito.ArgumentMatchers.eq(RESTAURANT_ID), deletedAt.capture());
        assertThat(deletedAt.getValue()).isEqualTo(NOW);
    }

    @Test
    void rejectsArchiveForWrongStateUsersOrReservations() {
        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(restaurant("ACTIVE")));
        assertThatThrownBy(() -> service.archive(RESTAURANT_ID))
                .isInstanceOf(AdminRestaurantConflictException.class);

        given(port.findByIdForUpdate(RESTAURANT_ID)).willReturn(Optional.of(restaurant("DRAFT")));
        given(port.hasNonDeletedUsers(RESTAURANT_ID)).willReturn(true);
        assertThatThrownBy(() -> service.archive(RESTAURANT_ID))
                .isInstanceOf(AdminRestaurantConflictException.class);

        given(port.hasNonDeletedUsers(RESTAURANT_ID)).willReturn(false);
        given(port.hasReservations(RESTAURANT_ID)).willReturn(true);
        assertThatThrownBy(() -> service.archive(RESTAURANT_ID))
                .isInstanceOf(AdminRestaurantConflictException.class);
        verify(port, never()).archive(any(), any());
    }

    private CreateRestaurantCommand command(String slug) {
        return new CreateRestaurantCommand(
                slug, "Restaurant", null, null, null, null, null, null, "Asia/Ho_Chi_Minh", null, 79, 26734);
    }

    private UpdateRestaurantCommand updateCommand(String name) {
        return updateCommand(name, "Asia/Ho_Chi_Minh");
    }

    private UpdateRestaurantCommand updateCommand(String name, String timezone) {
        return new UpdateRestaurantCommand(
                name, null, null, null, null, null, null, timezone, null, 79, 26734);
    }

    private RestaurantProfileData restaurant(String status) {
        return new RestaurantProfileData(
                RESTAURANT_ID, "restaurant-slug", "Restaurant", null, null, null, "ho-chi-minh", "Legacy area", null,
                null, null, null, "Asia/Ho_Chi_Minh", null, status, NOW.minusSeconds(60), NOW);
    }
}
