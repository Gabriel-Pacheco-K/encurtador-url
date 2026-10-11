package com.gabrielpacheco.encurtador.repository;

import com.gabrielpacheco.encurtador.domain.Click;
import com.gabrielpacheco.encurtador.dto.CountByKey;
import com.gabrielpacheco.encurtador.dto.DailyClicks;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ClickRepository extends JpaRepository<Click, Long> {

    @Query("""
            select new com.gabrielpacheco.encurtador.dto.DailyClicks(c.clickedOn, count(c))
            from Click c
            where c.shortUrlId = :id and c.clickedOn >= :since
            group by c.clickedOn
            """)
    List<DailyClicks> countByDay(@Param("id") Long shortUrlId, @Param("since") LocalDate since);

    @Query("""
            select new com.gabrielpacheco.encurtador.dto.CountByKey(coalesce(c.referrer, 'Direto'), count(c))
            from Click c where c.shortUrlId = :id
            group by coalesce(c.referrer, 'Direto') order by count(c) desc
            """)
    List<CountByKey> topReferrers(@Param("id") Long shortUrlId, Pageable limit);

    @Query("""
            select new com.gabrielpacheco.encurtador.dto.CountByKey(c.browser, count(c))
            from Click c where c.shortUrlId = :id
            group by c.browser order by count(c) desc
            """)
    List<CountByKey> countByBrowser(@Param("id") Long shortUrlId);

    @Query("""
            select new com.gabrielpacheco.encurtador.dto.CountByKey(c.device, count(c))
            from Click c where c.shortUrlId = :id
            group by c.device order by count(c) desc
            """)
    List<CountByKey> countByDevice(@Param("id") Long shortUrlId);

    @Modifying
    @Query("delete from Click c where c.shortUrlId = :id")
    void deleteByShortUrlId(@Param("id") Long shortUrlId);
}
