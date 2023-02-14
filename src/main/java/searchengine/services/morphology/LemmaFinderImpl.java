package searchengine.services.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class LemmaFinderImpl implements LemmaFinder {
    private final LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
    private final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public LemmaFinderImpl() throws IOException {
    }

    @Override
    public Map<String, Integer> collectLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();
        for (String word : words) {
            if (isWrongWord(word)) {
                continue;
            }
            List<String> normalForms = luceneMorphology
                    .getNormalForms(word);
            if(normalForms.isEmpty()){
                continue;
            }
            String normalWord = normalForms.get(0);
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    @Override
    public Set<String> getLemmaSet(String text) {
        String[] words = arrayContainsRussianWords(text);
        Set<String> lemmas = new HashSet<>();
        for (String word : words) {
            if (isWrongWord(word)) {
                continue;
            }
            List<String> normalForms = luceneMorphology
                    .getNormalForms(word);
            if(normalForms.isEmpty()){
                continue;
            }
            String normalWord = normalForms.get(0);
            lemmas.add(normalWord);
        }
        return lemmas;
    }

    @Override
    public Map<String, Set<String>> collectLemmasAndWords(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Set<String>> lemmas = new HashMap<>();
        for (String word : words) {
            if (isWrongWord(word)) {
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if(normalForms.isEmpty()){
                continue;
            }
            String normalWord = normalForms.get(0);
            if (lemmas.containsKey(normalWord)) {
                Set<String> wordsSet = new HashSet<>(lemmas.get(normalWord));
                wordsSet.add(word);
                lemmas.put(normalWord, wordsSet);
            } else {
                lemmas.put(normalWord, new HashSet<>() {{
                    add(word);
                }});
            }
        }
        return lemmas;
    }


    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream()
                .anyMatch(this::hasParticleProperty);
    }

    private boolean isWrongWord(String word) {
        if (word.isBlank()) {
            return true;
        }
        List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
        return anyWordBaseBelongToParticle(wordBaseForms);
    }

    private boolean hasParticleProperty(String wordBase) {
        String properties = wordBase.toUpperCase().substring(wordBase
                .indexOf("|"));
        for (String property : particlesNames) {
            if (properties.contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("ё", "е")
                .replaceAll("Ё", "Е")
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }
}

