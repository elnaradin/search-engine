package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import java.util.Optional;



@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    @Query(value = "SELECT SUM(`rank`) / (SELECT SUM(`rank`) " +
            "FROM `indexes` i group by `page_id` order by `rank` DESC limit 1) " +
            "FROM `indexes` i where  page_id = :page", nativeQuery = true)
    Float getRelevance(Page page);


    @Query(value = "SELECT * FROM `indexes` i  where lemma_id = :lemma AND page_id = :page  LIMIT 1", nativeQuery = true)
    Optional<Index> findByLemmaAndPage(Lemma lemma, Page page);

}
