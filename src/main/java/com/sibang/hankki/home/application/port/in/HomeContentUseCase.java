package com.sibang.hankki.home.application.port.in;

import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.model.PublicHomeContent;

public interface HomeContentUseCase {

    PublicHomeContent getPublic(String locale);

    HomeContent getAdmin();

    HomeContent.Hero updateHero(HomeContent.HeroUpdate update);

    HomeContent.City upsertCity(int provinceCode, HomeContent.CityUpdate update);
}
