package com.sibang.hankki.location.application.port.out;

import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Province;
import com.sibang.hankki.location.application.port.in.LocationCatalogUseCase.Ward;
import java.util.List;

public interface LocationCatalogPort {

    List<Province> fetchProvinces();

    List<Ward> fetchWards(int provinceCode);

    List<Province> snapshotProvinces();

    List<Ward> snapshotWards(int provinceCode);

    class SourceUnavailableException extends RuntimeException {

        private final String dataType;
        private final String endpoint;
        private final int attempt;

        public SourceUnavailableException(String message, Throwable cause) {
            this(message, cause, "unknown", "unknown", 1);
        }

        public SourceUnavailableException(
                String message, Throwable cause, String dataType, String endpoint, int attempt) {
            super(message, cause);
            this.dataType = dataType;
            this.endpoint = endpoint;
            this.attempt = attempt;
        }

        public String dataType() {
            return dataType;
        }

        public String endpoint() {
            return endpoint;
        }

        public int attempt() {
            return attempt;
        }
    }
}
