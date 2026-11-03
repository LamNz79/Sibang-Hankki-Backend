package com.sibang.hankki.home.application.port.out;

import com.sibang.hankki.home.application.model.HomeContent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HomeContentPort {

    Optional<HomeContent.Hero> findHero();

    List<HomeContent.City> findCities();

    HomeContent.Hero saveHero(HomeContent.Hero hero, Instant now);

    HomeContent.City saveCity(HomeContent.City city, Instant now);
}
