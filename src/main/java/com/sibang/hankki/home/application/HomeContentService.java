package com.sibang.hankki.home.application;

import com.sibang.hankki.home.application.exception.InvalidHomeContentException;
import com.sibang.hankki.home.application.model.HomeContent;
import com.sibang.hankki.home.application.model.PublicHomeContent;
import com.sibang.hankki.home.application.port.in.HomeContentUseCase;
import com.sibang.hankki.home.application.port.out.HomeContentPort;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeContentService implements HomeContentUseCase {

    private static final String DEFAULT_LOCALE = "en";
    private static final String CITY_SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private final HomeContentPort port;
    private final Clock clock;

    public HomeContentService(HomeContentPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicHomeContent getPublic(String locale) {
        String requestedLocale = supportedLocale(locale);
        PublicHomeContent.HeroBanner hero = port.findHero()
                .filter(HomeContent.Hero::enabled)
                .map(value -> publicHero(value, requestedLocale))
                .orElse(null);
        List<PublicHomeContent.CityImage> cities = port.findCities().stream()
                .filter(HomeContent.City::enabled)
                .sorted(Comparator.comparingInt(HomeContent.City::sortOrder)
                        .thenComparing(HomeContent.City::citySlug))
                .map(value -> new PublicHomeContent.CityImage(
                        value.provinceCode(), value.citySlug(), value.imageUrl(), value.imageAlt(), value.sortOrder()))
                .toList();
        return new PublicHomeContent(hero, cities);
    }

    @Override
    @Transactional(readOnly = true)
    public HomeContent getAdmin() {
        return new HomeContent(port.findHero().orElse(null), port.findCities());
    }

    @Override
    @Transactional
    public HomeContent.Hero updateHero(HomeContent.HeroUpdate update) {
        if (update == null) {
            throw invalid("Request body is required");
        }
        HomeContent.Hero hero = new HomeContent.Hero(
                optional(update.titleVi(), 120),
                optional(update.titleEn(), 120),
                optional(update.titleKo(), 120),
                optional(update.descriptionVi(), 500),
                optional(update.descriptionEn(), 500),
                optional(update.descriptionKo(), 500),
                imageUrl(update.imageUrl()),
                optional(update.imageAltVi(), 300),
                optional(update.imageAltEn(), 300),
                optional(update.imageAltKo(), 300),
                targetUrl(update.targetUrl()),
                required(update.enabled(), "enabled"));
        return port.saveHero(hero, Instant.now(clock));
    }

    @Override
    @Transactional
    public HomeContent.City upsertCity(int provinceCode, HomeContent.CityUpdate update) {
        if (provinceCode <= 0) {
            throw invalid("provinceCode must be positive");
        }
        if (update == null) {
            throw invalid("Request body is required");
        }
        String citySlug = required(update.citySlug(), "citySlug", 160);
        if (!citySlug.matches(CITY_SLUG_PATTERN)) {
            throw invalid("citySlug must contain lowercase letters, digits and hyphens only");
        }
        if (update.sortOrder() == null || update.sortOrder() < 0) {
            throw invalid("sortOrder must be zero or greater");
        }
        HomeContent.City city = new HomeContent.City(
                provinceCode,
                citySlug,
                imageUrl(update.imageUrl()),
                optional(update.imageAlt(), 300),
                update.sortOrder(),
                required(update.enabled(), "enabled"));
        return port.saveCity(city, Instant.now(clock));
    }

    private PublicHomeContent.HeroBanner publicHero(HomeContent.Hero hero, String locale) {
        return new PublicHomeContent.HeroBanner(
                localized(locale, hero.titleVi(), hero.titleEn(), hero.titleKo()),
                localized(locale, hero.descriptionVi(), hero.descriptionEn(), hero.descriptionKo()),
                hero.imageUrl(),
                localized(locale, hero.imageAltVi(), hero.imageAltEn(), hero.imageAltKo()),
                hero.targetUrl());
    }

    private String localized(String locale, String vi, String en, String ko) {
        String requested = switch (locale) {
            case "vi" -> vi;
            case "ko" -> ko;
            default -> en;
        };
        if (hasText(requested)) {
            return requested;
        }
        if (hasText(en)) {
            return en;
        }
        return Stream.of(vi, en, ko).filter(this::hasText).findFirst().orElse(null);
    }

    private String supportedLocale(String locale) {
        if (locale == null) {
            return DEFAULT_LOCALE;
        }
        String normalized = locale.strip().toLowerCase(Locale.ROOT);
        return List.of("vi", "en", "ko").contains(normalized) ? normalized : DEFAULT_LOCALE;
    }

    private String imageUrl(String value) {
        String normalized = required(value, "imageUrl", 2000);
        if (normalized.contains("\\") || normalized.startsWith("//")) {
            throw invalid("imageUrl is invalid");
        }
        URI uri = parseUri(normalized, "imageUrl");
        if (normalized.startsWith("/images/")) {
            if (uri.isAbsolute() || uri.getRawAuthority() != null || unsafePath(uri.getRawPath())) {
                throw invalid("imageUrl is invalid");
            }
            return normalized;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw invalid("imageUrl is invalid");
        }
        return normalized;
    }

    private String targetUrl(String value) {
        String normalized = required(value, "targetUrl", 2000);
        if (!normalized.startsWith("/") || normalized.startsWith("//") || normalized.contains("\\")) {
            throw invalid("targetUrl must be an internal root-relative path");
        }
        URI uri = parseUri(normalized, "targetUrl");
        if (uri.isAbsolute() || uri.getRawAuthority() != null || unsafePath(uri.getRawPath())) {
            throw invalid("targetUrl must be an internal root-relative path");
        }
        return normalized;
    }

    private boolean unsafePath(String path) {
        if (path == null) {
            return true;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.contains("//") || normalized.contains("/../") || normalized.endsWith("/..")
                || normalized.contains("%2e") || normalized.contains("%5c");
    }

    private URI parseUri(String value, String field) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " is invalid");
        }
    }

    private String required(String value, String field, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw invalid(field + " is required");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw invalid("Value is too long");
        }
        return normalized;
    }

    private boolean required(Boolean value, String field) {
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private InvalidHomeContentException invalid(String message) {
        return new InvalidHomeContentException(message);
    }
}
