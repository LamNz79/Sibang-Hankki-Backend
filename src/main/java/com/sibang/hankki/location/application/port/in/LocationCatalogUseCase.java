package com.sibang.hankki.location.application.port.in;

import java.util.List;
import java.util.Optional;

public interface LocationCatalogUseCase {

    List<Province> findProvinces();

    List<Ward> findWards(int provinceCode);

    default Optional<Province> findProvince(int provinceCode) {
        return findProvinces().stream().filter(province -> province.code() == provinceCode).findFirst();
    }

    default Optional<Ward> findWard(int provinceCode, int wardCode) {
        return findWards(provinceCode).stream().filter(ward -> ward.code() == wardCode).findFirst();
    }

    record Province(int code, String codename, String name, String divisionType) {
    }

    record Ward(int code, String codename, String name, String divisionType, int provinceCode) {
    }
}
