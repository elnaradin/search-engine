
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.Application;
import searchengine.config.Site;
import searchengine.services.IndexationServiceImpl;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest(classes = Application.class)
public class IndexationServiceTest {

    @Autowired
    public IndexationServiceImpl indexationServiceImpl;


    @Test
    @DisplayName("Запуск индексации")
    public void testStartIndexing(){
        indexationServiceImpl.startIndexing();
        Assertions.assertTrue(indexationServiceImpl.isIndexing());
        indexationServiceImpl.stopIndexing();
    }
    @Test
    @DisplayName("Остановка индексации")
    public void testStopIndexing(){
        indexationServiceImpl.stopIndexing();
        indexationServiceImpl.stopIndexing();
        Assertions.assertTrue(indexationServiceImpl.isStopped());
    }
    @Test
    @DisplayName("Индексация отдельной страницы")
    public void testIndexPage(){

        String exampleUrl = "https://example_site/example";
        List<Site> sites = new ArrayList<>();
        Site site = new Site();
        site.setName("Example site");
        site.setUrl("https://example_site");
        sites.add(site);
        indexationServiceImpl.getSitesList().setSites(sites);
        indexationServiceImpl.indexPage(exampleUrl);
        Assertions.assertTrue(indexationServiceImpl.siteIsPresent());
    }
}
