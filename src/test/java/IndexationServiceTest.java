
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.Application;
import searchengine.dto.statistics.Response;
import searchengine.services.indexation.IndexationService;



@SpringBootTest(classes = Application.class)
public class IndexationServiceTest {

    @Autowired
    public IndexationService indexationService;

    @Test
    @DisplayName("Индексация существующей страницы")
    public void testIndexExistingPage() {
        String exampleUrl = "https://dombulgakova.ru";
        Response actual = indexationService
                    .indexPageAndGetIndexPageResponse(exampleUrl);
        Response expected = new Response();
        expected.setResult(true);
        Assertions.assertEquals(expected, actual);
    }
    @Test
    @DisplayName("Индексация несуществующей страницы")
    public void testIndexNonExistingPage() {
        String exampleUrl = "https://www.playback.ru/89797";
        Response actual = indexationService
                    .indexPageAndGetIndexPageResponse(exampleUrl);

        Response expected = new Response();
        expected.setResult(false);
        expected.setError(indexationService.errors[2]);
        Assertions.assertEquals(expected, actual);
    }

}
