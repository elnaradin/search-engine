package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query(value = "SELECT Distinct * FROM lemmas WHERE `lemma` = :lemma  LIMIT 1", nativeQuery = true)
    Optional<Lemma> findByLemma(String lemma);

    @Query(value = "SELECT * FROM lemmas " +
            "WHERE `lemma` IN :lemmaSet AND frequency < 3000 ORDER BY `frequency` ASC", nativeQuery = true)
    List<Lemma> findByLemmas(Set<String> lemmaSet);

    @Query(value = "SELECT COUNT(*) count  FROM lemmas  l " +
            "JOIN lemmas_sites ls ON l.id = ls.lemma_id " +
            "WHERE ls.`site_id` = :site", nativeQuery = true)
    Integer countLemmas(Site site);

}
