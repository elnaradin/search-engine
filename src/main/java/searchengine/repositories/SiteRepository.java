package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

import java.util.Optional;


@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Query(value = "SELECT * FROM `sites` WHERE `url` = :url LIMIT 1", nativeQuery = true)
    Optional<Site> findByUrl(String url);

    @Query(value = "Select * from sites where `status` = :status Limit 1 ", nativeQuery = true)
    Optional<Site> findByStatus(String status);

}
