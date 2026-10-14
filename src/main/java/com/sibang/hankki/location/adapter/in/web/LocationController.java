package com.sibang.hankki.location.adapter.in.web;

import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationCatalogUseCase locationCatalog;

    public LocationController(LocationCatalogUseCase locationCatalog) {
        this.locationCatalog = locationCatalog;
    }

    @GetMapping("/provinces")
    public List<Province> findProvinces() {
        return locationCatalog.findProvinces();
    }

    @GetMapping("/provinces/{provinceCode}/wards")
    public List<Ward> findWards(@PathVariable int provinceCode) {
        return locationCatalog.findWards(provinceCode);
    }
}
