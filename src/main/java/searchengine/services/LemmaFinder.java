package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class LemmaFinder {
    private final LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
    private  final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "МС"};

    public LemmaFinder() throws IOException {
    }

    public Map<String, Integer> collectLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();
        for (String word : words) {
            if (!isProperWord(word)){
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
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


    public Set<String> getLemmaSet(String text) {
        String[] words = arrayContainsRussianWords(text);
        Set<String> lemmas = new HashSet<>();
        for (String word : words) {
            if (!isProperWord(word)){
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String normalWord = normalForms.get(0);
            lemmas.add(normalWord);
        }
        return lemmas;
    }

    public Map<String, Set<String>> getTextInLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Set<String>> lemmas = new HashMap<>();
        for (String word : words) {
            if (!isProperWord(word)){
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String normalWord = normalForms.get(0);
            if (lemmas.containsKey(normalWord)) {
                Set<String> wordsSet = new HashSet<>(lemmas.get(normalWord));
                 wordsSet.add(word);
                lemmas.put(normalWord, wordsSet);
            } else {
                lemmas.put(normalWord, new HashSet<>(){{add(word);}});
            }
        }
        return lemmas;
    }


    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }
    private boolean isProperWord(String word){
        if (word.isBlank()) {
            return false;
        }
        List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
        return !anyWordBaseBelongToParticle(wordBaseForms);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {

        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-яё\\s])", " ")
                .trim()
                .split("\\s+");
    }
}

