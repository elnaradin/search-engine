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

    Optional<Page> findFirstByPathAndSite(String path, Site site);

    boolean existsByPathAndSite(String path, Site site);

    Integer countPageBySite(Site site);

    @Query(value = "SELECT * FROM pages p " +
            "join indexes i on i.page_id = p. id " +
            "join lemmas l on i.lemma_id = l.id " +
            "where l.id in :lemmas " +
            "AND p.site_id IN :sites", nativeQuery = true)
    Set<Page> findByLemmasAndSites(List<Lemma> lemmas, List<Site> sites);


    @Query(value = "SELECT * FROM pages p " +
            "join indexes i on i.page_id = p. id " +
            "join lemmas l on i.lemma_id = l.id " +
            "where l.id = :lemma AND p.site_id IN :sites " +
            "AND p.id in :pages", nativeQuery = true)
    Set<Page> findByOneLemmaAndSitesAndPages(Lemma lemma, List<Site> sites,
                                                  Set<Page> pages);

}
