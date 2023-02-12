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
    private final String boldStart = "<b>";
    private final String boldEnd = "</b>";
    private final String boldRegex = "</?b>";
    private final int leftSpace = 20;

    public String createSnippet(String text, List<Lemma> sortedLemmas) {
        String formattedPart = findQueryPartAndFormat(text, sortedLemmas);
        String threeDots = " ...";
        String sentence = formattedPart.replaceAll(boldRegex, "").trim();
        int startIndex = text.indexOf(sentence);
        int endIndex = text.indexOf(sentence) + sentence.length();
        String subString = text.substring(startIndex, endIndex);
        text = text.replace(subString, formattedPart);
        int substringStart = findStartIndexInText(text,
                formattedPart);
        String snippet = text.substring(substringStart,
                text.length() - 1);
        return cutSnippet(snippet).concat(threeDots);
    }

    private String findQueryPartAndFormat(String text,  List<Lemma> sortedLemmas) {
        Map<String, Set<String>> lemmasAndWords =
                lemmaFinder.collectLemmasAndWords(text);
        String[] words = text.split(" ");
        StringBuilder formattedText = new StringBuilder();
        for (String word : words) {
            for (Lemma lemma : sortedLemmas) {
                word = formatWordIfIsInQuery(word, lemma.getLemma(), lemmasAndWords);
            }
            formattedText.append(word).append(" ");
        }
        int start = formattedText.indexOf(boldStart);
        int end = formattedText.lastIndexOf(boldEnd) + boldEnd.length();
        return formattedText.substring(start > 3 ? start - 3 : start,
                formattedText.length() - end > 3 ? end + 3 : end);
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
                word = word.replace(part, boldStart
                        .concat(part).concat(boldEnd));
            }
        }
        return word;
    }

    private int findStartIndexInText(String text, String formattedPart) {
        String[] sentences = formattedPart
                .split("(?<=[.!?·])\\s*(?=(<b>|\"|«|· )?[А-ЯЁ])");
        String maxSentence = "";
        int maxValue = 0;
        for (String sentence : sentences) {
            int boldWordsCount = StringUtils
                    .countOccurrencesOf(sentence, boldStart);
            if (boldWordsCount > maxValue) {
                maxValue = boldWordsCount;
                maxSentence = sentence;
            }
        }
        int startIndex = text.indexOf(maxSentence);
        return text.indexOf(" ", startIndex > leftSpace
                ? startIndex - leftSpace : startIndex);
    }

    private String cutSnippet(String snippet) {
        int count = StringUtils.countOccurrencesOf(snippet, boldStart);
        int snippetLength = leftSpace * 12;
        String unformattedSnippet = snippet.replaceAll(boldRegex, "")
                .substring(0, snippetLength);
        String formattedSnippet = "";
        for (int i = count; i >= 1; i--) {
            int endOfSnippet = snippet.indexOf(" ", snippetLength
                    + i * (boldStart.length() + boldEnd.length()));
            formattedSnippet = snippet.substring(0, endOfSnippet);
            if (formattedSnippet.replaceAll(boldRegex, "")
                    .length() < unformattedSnippet.length()) {
                break;
            }
        }
        return formattedSnippet;
    }
}
