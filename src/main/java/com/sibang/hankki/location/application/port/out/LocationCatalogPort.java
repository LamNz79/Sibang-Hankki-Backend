package com.sibang.hankki.location.application.port.out;

import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import java.util.List;

public interface LocationCatalogPort {

    List<Province> fetchProvinces();

    List<Ward> fetchWards(int provinceCode);

    class SourceUnavailableException extends RuntimeException {

        public SourceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
