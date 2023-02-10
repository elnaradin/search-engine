package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import searchengine.model.Page;


@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    @Query(value = "SELECT MAX(sum) FROM (SELECT SUM(`rank`) sum " +
            "FROM `indexes` i GROUP BY `page_id`) AS `value`", nativeQuery = true)
    Float getMaxValue();

    @Query(value = "SELECT SUM(`rank`) / :maxValue " +
            "FROM `indexes` i WHERE  page_id = :page", nativeQuery = true)
    Float getRelevance(Page page, float maxValue);
}
