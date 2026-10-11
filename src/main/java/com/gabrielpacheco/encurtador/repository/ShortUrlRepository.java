package com.gabrielpacheco.encurtador.repository;

import com.gabrielpacheco.encurtador.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    /** Incremento atômico no banco: evita perder cliques quando há acessos simultâneos. */
    @Modifying
    @Query("update ShortUrl s set s.clickCount = s.clickCount + 1 where s.id = :id")
    void incrementClicks(@Param("id") Long id);
}
