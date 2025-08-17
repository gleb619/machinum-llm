package machinum.repository;

import machinum.entity.LineView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LineRepository extends JpaRepository<LineView, String> {

    Page<LineView> findByBookId(String bookId, Pageable pageable);

    List<LineView> findByChapterId(String chapterId);

    @Query(value = Queries.ENGLISH_IN_TRANSLATED_SQL + " SELECT chapter_id FROM data ORDER BY number",
            countQuery = Queries.ENGLISH_IN_TRANSLATED_SQL + "SELECT count(chapter_id) FROM data",
            nativeQuery = true)
    Page<String> findEnglishInTranslated(@Param("bookId") String bookId, PageRequest request);

    @Query(value = Queries.SUSPICIOUS_IN_ORIGINAL_SQL + " SELECT chapter_id FROM data ORDER BY number",
            countQuery = Queries.SUSPICIOUS_IN_ORIGINAL_SQL + "SELECT count(chapter_id) FROM data",
            nativeQuery = true)
    Page<String> findSuspiciousInOriginal(@Param("bookId") String bookId, PageRequest request);

    @Query(value = Queries.SUSPICIOUS_IN_TRANSLATED_SQL + " SELECT chapter_id FROM data ORDER BY number",
            countQuery = Queries.SUSPICIOUS_IN_TRANSLATED_SQL + "SELECT count(chapter_id) FROM data",
            nativeQuery = true)
    Page<String> findSuspiciousInTranslated(@Param("bookId") String bookId, PageRequest request);

    @Query(value = """
            SELECT l.id FROM lines_info l 
            WHERE l.book_id = :bookId 
            AND (
                CASE WHEN :useRegex = true THEN 
                    (:matchCase = true AND l.original_line ~ :searchPattern)
                    OR (:matchCase = false AND l.original_line ~* :searchPattern)
                ELSE 
                    (:matchWholeWord = true AND 
                        (:matchCase = true AND l.original_line ~ ('\\m' || :text || '\\M'))
                        OR (:matchCase = false AND l.original_line ~* ('\\m' || :text || '\\M'))
                    )
                    OR (:matchWholeWord = false AND 
                        (:matchCase = true AND l.original_line LIKE ('%' || :text || '%'))
                        OR (:matchCase = false AND LOWER(l.original_line) LIKE ('%' || LOWER(:text) || '%'))
                    )
                END
            )
            ORDER BY l.number, l.line_index
            """, nativeQuery = true)
    Page<String> findSimilarOriginalLines(@Param("bookId") String bookId,
                                          @Param("text") String text,
                                          @Param("searchPattern") String searchPattern,
                                          @Param("matchCase") Boolean matchCase,
                                          @Param("matchWholeWord") Boolean matchWholeWord,
                                          @Param("useRegex") Boolean useRegex,
                                          PageRequest request);

    @Query(value = """
            SELECT l.id FROM lines_info l 
            WHERE l.book_id = :bookId 
            AND (
                CASE WHEN :useRegex = true THEN 
                    (:matchCase = true AND l.translated_line ~ :searchPattern)
                    OR (:matchCase = false AND l.translated_line ~* :searchPattern)
                ELSE 
                    (:matchWholeWord = true AND 
                        (:matchCase = true AND l.translated_line ~ ('\\m' || :text || '\\M'))
                        OR (:matchCase = false AND l.translated_line ~* ('\\m' || :text || '\\M'))
                    )
                    OR (:matchWholeWord = false AND 
                        (:matchCase = true AND l.translated_line LIKE ('%' || :text || '%'))
                        OR (:matchCase = false AND LOWER(l.translated_line) LIKE ('%' || LOWER(:text) || '%'))
                    )
                END
            )
            ORDER BY l.number, l.line_index
            """, nativeQuery = true)
    Page<String> findSimilarTranslatedLines(@Param("bookId") String bookId,
                                            @Param("text") String text,
                                            @Param("searchPattern") String searchPattern,
                                            @Param("matchCase") Boolean matchCase,
                                            @Param("matchWholeWord") Boolean matchWholeWord,
                                            @Param("useRegex") Boolean useRegex,
                                            PageRequest request);

    class Queries {

        public static final String ENGLISH_IN_TRANSLATED_SQL = //language=sql
                """
                        WITH data AS (
                            SELECT DISTINCT on (lv0.chapter_id)
                              lv0.chapter_id,
                              lv0.number 
                            FROM lines_info lv0 
                            WHERE book_id = :bookId 
                            AND translated_line ~ '[a-zA-Z]+'
                        )
                        """;

        public static final String SUSPICIOUS_IN_ORIGINAL_SQL = //language=sql
                """
                        WITH data AS (
                         SELECT DISTINCT on (lv0.chapter_id)
                           lv0.chapter_id,
                           lv0.number 
                         FROM lines_info lv0
                         WHERE book_id = :bookId 
                           AND (
                             original_line ILIKE ANY (ARRAY[
                                 '%subscribe%', '%follow %channel%', '%follow %page%', '%follow %account%',
                                 '%read us at%', '%join us at%',
                                 '%discord server%', '%telegram%', '%whatsapp group%',
                                 '%support us on patreon%', '%support us on ko-fi%', '%support us on paypal%',
                                 '%click here%', '%link here%', '%download app%', '%visit site%', '%visit store%',
                                 '%promo code%', '%sponsored%', '%affiliate link%',
                                 '%donate%', '%tip%', '%monetization%',
                                 '%ad%', '%announcement%', '%advertisement%',
                                 '%follow us on facebook%', '%follow us on instagram%', '%follow us on tiktok%', '%follow us on youtube%',
                                 '%exclusive content for subscribers%', '%bonus content for subscribers%',
                                 '%rate this chapter%', '%rate this book%', '%review this chapter%', '%share this chapter%', '%share this book%',
                                 '%turn on notifications%', '%stay tuned%',
                                 '%limited offer%', '%early access%', '%paid chapters%',
                                 '%commercial break%', '%check out%'
                           ])
                           OR original_line ~* '@\\w+|https?://|www\\.|\\.com|\\.org|\\.net'
                           OR original_line ~* '#\\w+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}'
                          )
                        )
                        """;

        public static final String SUSPICIOUS_IN_TRANSLATED_SQL = //language=sql
                """
                        WITH data AS (
                         SELECT DISTINCT on (lv0.chapter_id)
                           lv0.chapter_id,
                           lv0.number 
                         FROM lines_info lv0
                         WHERE book_id = :bookId 
                           AND (
                             translated_line ILIKE ANY (ARRAY[
                                 '%подписывайтесь%', '%подписаться%', '%подпишись%', '%подписка%',
                                 '%читайте нас%', '%присоединяйтесь к нам%', '%найдите нас%',
                                 '%discord сервер%', '%discord-сервер%', '%viber%', '%вайбер%', 
                                 '%whatsapp%', '%телеграм%', '%telegram%', '%vk%', '%вконтакте%', '%одноклассники%',
                                 '%поддержите автора%', '%поддержка автора%', '%поддержать нас%', '%автор просит поддержать%',
                                 '%главы выходят быстрее%', '%юmoney%', '%patreon%', '%donationalerts%',
                                 '%сбербанк%', '%тинькофф%', '%донаты%', '%пожертвования%', '%купите кофе автору%',
                                 '%ссылка для доступа%', '%скачать приложение%', '%перейти по ссылке%', '%переходите по ссылке%',
                                 '%промокод%', '%рекламное предложение%', '%партнерская программа%',
                                 '%рекламная пауза%', '%спонсорский блок%', '%партнерский материал%',
                                 '%извините за рекламу%', '%это не часть сюжета%',
                                 '%эксклюзивный контент%', '%для подписчиков%', '%ранний доступ%', 
                                 '%платные главы%', '%доступ за деньги%', 
                                 '%рейтинг книги%', '%оставьте отзыв%', '%litres%', '%амазон%', '%amazon%',
                                 '%ozon%', '%wildberries%', '%twitch%',
                                 '%включите уведомления%', '%не пропустите обновления%',
                                 '%tiktok%', '%youtube%', '%rutube%', '%zen%', '%яндекс%',
                                 '%отключите блокировщик%', '%реклама помогает%',
                                 '%перевод от команды%', '%переведено для%',
                                 '%акция только для%', '%первых % читателей%',
                                 '%planeta.ru%', '%boomstarter%', '%краудфандинг%',
                                 '%ищите нас в соцсетях%'
                             ])
                             OR translated_line ~* 'vk\\.com|t\\.me|mail\\.ru'
                             OR translated_line ~* '#[а-яА-Яa-zA-Z0-9_]+'
                             OR translated_line ~* '[a-z]+\\.[a-z]{2,3}'
                             OR translated_line ~* '\\+7[0-9]{10}|8[0-9]{10}'
                             OR translated_line ~* 'vpn|torrent|пиратск'
                          )
                        )
                        """;

    }

}
