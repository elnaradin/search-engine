package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import searchengine.model.Lemma;
import searchengine.services.morphology.LemmaFinder;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SnippetCreator {
    private final LemmaFinder lemmaFinder;
    private final String START_TAG = "<b>";
    private final String END_TAG = "</b>";
    private final int SNIPPET_LENGTH = 240;

    public String createSnippet(String text, List<Lemma> sortedLemmas) {
        String formattedText = formatText(text, sortedLemmas);
        String threeDots = " ...";
        int substringStart = findStartIndexInText(formattedText);
        String partWithTags = formattedText.substring(substringStart,
                formattedText.length() - 1);
        return cutSnippet(partWithTags).concat(threeDots);
    }

    private String formatText(String text, List<Lemma> sortedLemmas) {
        Map<String, Set<String>> lemmasAndForms =
                lemmaFinder.collectLemmasAndWords(text);
        String[] words = text.split(" ");
        StringBuilder formattedText = new StringBuilder();
        for (String word : words) {
            for (Lemma lemma : sortedLemmas) {
                word = formatWordIfIsInQuery(word, lemma.getLemma(), lemmasAndForms);
            }
            formattedText.append(word).append(" ");
        }
        return formattedText.toString();
    }


    private String formatWordIfIsInQuery(String word, String lemma,
                                         Map<String, Set<String>> lemmasAndWords) {
        String[] formattedWord = word
                .replaceAll("([^А-Яа-яЁё\\-])", " ")
                .trim().split("[- ]");
        for (String part : formattedWord) {
            if ((lemmasAndWords.get(lemma).stream().anyMatch(part
                    .replaceAll("Ё", "Е")
                    .replaceAll("ё", "е")
                    ::equalsIgnoreCase))) {
                word = word.replace(part, START_TAG
                        .concat(part).concat(END_TAG));
            }
        }
        return word;
    }

    private int findStartIndexInText(String formattedText) {
        String[] sentences = formattedText.substring(formattedText
                        .indexOf(START_TAG), formattedText.lastIndexOf(END_TAG))
                .split("(?<=[.!?·])\\s*(?=(<b>|\"|«|· )?[А-ЯЁ])");
        String maxSentence = "";
        int maxValue = 0;
        for (String sentence : sentences) {
            int boldWordsCount = StringUtils
                    .countOccurrencesOf(sentence, START_TAG);
            if (boldWordsCount > maxValue) {
                maxValue = boldWordsCount;
                maxSentence = sentence;
            }
        }
        int startIndex = formattedText.indexOf(maxSentence);
        int leftSpace = SNIPPET_LENGTH / 12;
        return formattedText.indexOf(" ", startIndex > leftSpace
                ? startIndex - leftSpace : startIndex);
    }

    private String cutSnippet(String partWithTags) {
        int tagsCount = StringUtils.countOccurrencesOf(partWithTags, START_TAG);
        int tagsLength = START_TAG.length() + END_TAG.length();
        String tagsRegex = "</?b>";
        String unformattedSnippet = partWithTags.replaceAll(tagsRegex, "")
                .substring(0, SNIPPET_LENGTH);
        String formattedSnippet = "";
        for (int i = tagsCount; i >= 1; i--) {
            int endOfSnippet = partWithTags.indexOf(" ",
                    SNIPPET_LENGTH + i * tagsLength);
            formattedSnippet = partWithTags.substring(0, endOfSnippet);
            if (formattedSnippet.replaceAll(tagsRegex, "")
                    .length() < unformattedSnippet.length()) {
                break;
            }
        }
        return formattedSnippet;
    }
}
