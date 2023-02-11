package searchengine.services.morphology;

import java.util.Map;
import java.util.Set;

public interface LemmaFinder {
    Map<String, Integer> collectLemmas(String text);
    Set<String> getLemmaSet(String text);
    Map<String, Set<String>> collectLemmasAndWords(String text);
}
