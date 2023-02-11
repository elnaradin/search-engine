import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.Application;
import searchengine.services.morphology.LemmaFinderImpl;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = Application.class)
public class LemmaFinderTest {
    @Autowired
    public LemmaFinderImpl lemmaFinder;

    @Test
    @DisplayName("Нахождение лемм и их частотности")
    public void testCollectLemmas(){
        String text = """
                Повторное появление леопарда в Осетии позволяет предположить,
                что леопард постоянно обитает в некоторых районах Северного
                Кавказа.""";
        Map<String, Integer> map1 = Map.of("повторный" ,1,
                "появление" , 1,
                "постоянно" , 1,
                "позволять" , 1,
                "предположить" , 1);
        Map<String, Integer> map2 = Map.of("северный" , 1,
                "район" , 1,
                "кавказ" , 1,
                "осетия"  , 1,
                "леопард" , 2);
        HashMap<String, Integer> expected = new HashMap<>();
        expected.putAll(map1);
        expected.putAll(map2);
        expected.put("некоторый", 1);
        expected.put("обитать", 1);
        Assertions.assertEquals(expected, lemmaFinder.collectLemmas(text));
    }
}
