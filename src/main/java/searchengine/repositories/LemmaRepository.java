package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.Optional;
import java.util.Set;
import java.util.Vector;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    Optional<Lemma> findFirstByLemma(String lemma);

    @Query(value = "SELECT * FROM lemmas " +
            "WHERE `lemma` IN :lemmaSet " +
            "ORDER BY `frequency` ASC ", nativeQuery = true)
    Vector<Lemma> findByLemmas(Set<String> lemmaSet);

    Integer countLemmaBySite(Site site);

}
