package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;
import java.util.Set;


@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    @Query(value = "SELECT Distinct`path` from `pages`" +
            " where `path` = :path " +
            "AND `site_id` = :site  LIMIT 1", nativeQuery = true)
    Optional<String> findByPathAndSite(String path, Site site);

    @Query(value = "SELECT * from `pages`" +
            " where `path` = :path" + " " +
            "AND `site_id` = :site LIMIT 1", nativeQuery = true)
    Optional<Page> findPage(String path, Site site);

    @Query(value = "SELECT COUNT(*) count from `pages`" +
            " where `site_id` = :site", nativeQuery = true)
    Integer countPages(Site site);

    @Query(value = "SELECT * FROM pages p " +
            "join indexes i on i.page_id = p. id " +
            "join lemmas l on i.lemma_id = l.id " +
            "where l.id in :lemmas " +
            "AND p.site_id IN :sites", nativeQuery = true)
    Set<Page> getALLPages(List<Lemma> lemmas, List<Site> sites);


    @Query(value = "SELECT * FROM pages p " +
            "join indexes i on i.page_id = p. id " +
            "join lemmas l on i.lemma_id = l.id " +
            "where l.id = :lemma AND p.site_id IN :sites " +
            "AND p.id in :pages", nativeQuery = true)
    Set<Page> getPages(Lemma lemma, List<Site> sites, Set<Page> pages);

    @Query(value = "SELECT * FROM pages p " +
            "join indexes i on i.page_id = p. id " +
            "join lemmas l on i.lemma_id = l.id " +
            "where l.id = :lemma AND p.site_id IN :sites " +
            "AND p.id in :pages " +
            "LIMIT :limit OFFSET :offset", nativeQuery = true)
    Set<Page> getPagesWithLimit(Lemma lemma, List<Site> sites,
                                Set<Page> pages, int limit, int offset);

}
