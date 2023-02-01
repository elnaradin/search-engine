package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import searchengine.config.SitesList;
import searchengine.dto.statistics.Data;
import searchengine.dto.statistics.Response;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private Float maxValue;
    private final String boldStart = "<b>";
    private final String boldEnd = "</b>";
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final SitesList sitesList;
    private final LemmaFinder lemmaFinder;

    @Override
    public Response getResponse(String query, String site, Integer offset, Integer limit) {
        limit = 20;
        List<Site> sites = getSites(site);
        List<Data> dataList = new ArrayList<>();
        Set<String> lemmas = lemmaFinder.getLemmaSet(query
                .replaceAll("[Ёё]", "е"));
        Set<Page> pages = getPages(lemmas, sites, offset, limit);
        try {
            if (pages != null) {
                for (Page page : pages) {
                    String content = page.getContent();
                    dataList.add(getData(page, content, lemmas));
                }
            }
            dataList.sort(Collections.reverseOrder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return handleErrors(site, lemmas, pages, dataList, query);
    }

    private Response handleErrors(String site, Set<String> lemmas,
                                  Set<Page> pages, List<Data> dataList, String query) {
        Response response = new Response();
        if (site != null && !siteIsPresent(site)) {
            response.setError(errors[0]);
            return response;
        }
        if (lemmas.isEmpty()) {
            response.setError(errors[1]);
            return response;
        }
        if (pages == null) {
            response.setError(errors[2]);
            return response;
        }
        if (query == null) {
            response.setError(errors[3]);
        }
        return getResponse(dataList);
    }

    private boolean siteIsPresent(String site) {
        return sitesList.getSites().stream()
                .anyMatch(s -> (s.getUrl().endsWith("/") ?
                        s.getUrl().substring(0, s.getUrl().length() - 1)
                        : s.getUrl()).equals(site));
    }

    private Response getResponse(List<Data> dataList) {
        Response response = new Response();
        response.setCount(dataList.size());
        response.setResult(true);
        response.setData(dataList);
        return response;
    }

    private List<Site> getSites(String site) {
        List<Site> sites = new ArrayList<>();
        Optional<Site> optSite = siteRepo.findByUrl(site);
        if (site != null && optSite.isPresent()) {
            sites.add(optSite.get());
        } else {
            sites = siteRepo.findAll();
        }
        return sites;
    }


    private Data getData(Page page, String content,
                         Set<String> lemmas) throws IOException {
        Data data = new Data();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        data.setTitle(getTitle(content));
        data.setRelevance(getRelevance(page));
        String text = Jsoup.clean(content, Safelist.none())
                .replaceAll("&nbsp;", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ");

        data.setSnippet(getSnippet(getBoldPhrase(text, lemmas), text));
        return data;
    }

    private String getTitle(String content) {
        String titleStart = "<title>";
        String titleEnd = "</title>";
        if (content.contains(titleStart)) {
            int start = content.indexOf(titleStart) + titleStart.length();
            int end = content.indexOf(titleEnd);
            return content.substring(start, end);
        }
        return null;
    }

    private float getRelevance(Page page) {
        if (maxValue == null) {
            maxValue = indexRepo.getMaxValue();
        }
        return indexRepo
                .getRelevance(page, maxValue);
    }

    private Set<Page> getPages(Set<String> lemmas, List<Site> sites, int offset, int limit) {
        List<Lemma> sortedLemmas = getSortedLemmas(lemmas);
        if (sortedLemmas == null) {
            return null;
        }
        Set<Page> pages = pageRepo.getALLPages(sortedLemmas, sites);
        for (int i = 0; i < sortedLemmas.size() - 1; i++) {
            Set<Page> foundPages = pageRepo
                    .getPages(sortedLemmas.get(i), sites, pages);
            pages.clear();
            pages.addAll(foundPages);
        }
        return pageRepo.getPagesWithLimit(sortedLemmas
                .get(sortedLemmas.size() > 0 ? sortedLemmas
                        .size() - 1 : 0), sites, pages, limit, offset);
    }

    private List<Lemma> getSortedLemmas(Set<String> lemmas) {
        List<Lemma> sortedLemmas = lemmaRepo.findByLemmas(lemmas);
        if (sortedLemmas.size() < lemmas.size()) {
            return null;
        }
        for (int i = 0; i < sortedLemmas.size(); i++) {
            if (sortedLemmas.get(i).getFrequency() > 2000) {
                sortedLemmas.remove(sortedLemmas.get(i));
            }
        }
        if (CollectionUtils.isEmpty(sortedLemmas)) {
            return null;
        }
        return sortedLemmas;
    }

    private String getBoldPhrase(String text, Set<String> lemmas) {
        Map<String, Integer> snippets = new LinkedHashMap<>();

        Map<String, Set<String>> lemmasAndWords =
                lemmaFinder.getTextInLemmas(text);
        String[] sentences = text.split("\\.");
        for (String sentence : sentences) {

            String formattedSentence = getFormattedSentence(sentence, lemmas, lemmasAndWords);
            if (!formattedSentence.contains(boldStart)) {
                continue;
            }
            snippets.putAll(collectPhrasesAndLength(formattedSentence));
        }
        return getLongestBoldPhrase(snippets);
    }

    private Map<String, Integer> collectPhrasesAndLength(String formattedSentence) {
        Map<String, Integer> snippets = new LinkedHashMap<>();
        String[] snippetWords = formattedSentence.split(" ");
        for (String w : snippetWords) {
            if (w.contains(boldStart)) {
                int length = formattedSentence
                        .substring(formattedSentence.indexOf(boldStart)
                                + boldStart.length(), formattedSentence
                                .indexOf(boldEnd)).length();
                if (snippets.containsKey(formattedSentence)) {
                    snippets.put(formattedSentence,
                            snippets.get(formattedSentence) + length);
                } else {
                    snippets.put(formattedSentence, length);
                }
            }
        }
        return snippets;
    }


    private String getLongestBoldPhrase(Map<String, Integer> snippets) {
        Optional<Map.Entry<String, Integer>> optBoldPhrase
                = snippets.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        if (optBoldPhrase.isEmpty()) {
            return "";
        }
        String boldPhrase = optBoldPhrase.get().getKey();
        int start = boldPhrase.indexOf(boldStart);
        int end = boldPhrase.lastIndexOf(boldEnd) + boldEnd.length();
        boldPhrase = boldPhrase.substring(start > 3 ? start - 3 : start,
                boldPhrase.length() - end > 3 ? end + 3 : end);
        return boldPhrase;
    }


    private String getFormattedSentence(String sentence, Set<String> lemmas,
                                        Map<String, Set<String>> lemmasAndWords) {
        String[] words = sentence
//                .replaceAll("&nbsp;", " ")
                .split("\\s");
        StringBuilder formattedSentence = new StringBuilder();
        for (String word : words) {
            String formattedWord = word.toLowerCase(Locale.ROOT)
                    .replaceAll("([^а-яё\\-\\s])", " ").strip();
            for (String lemma : lemmas) {
                if (lemmasAndWords == null) {
                    continue;
                }

                if (lemmasAndWords.get(lemma).stream().anyMatch(formattedWord::equals)) {
                    String cleanedWord = word.replaceAll("[^\\-а-яA-ЯЁё]", "");
                    word = word.replace(cleanedWord, boldStart.concat(cleanedWord).concat(boldEnd));
                }
                if (formattedWord.contains("-")){
                    if((lemmasAndWords.get(lemma).stream().map(String::strip).anyMatch(formattedWord::startsWith))){
                        String cleanedWord = word.substring(0, word.indexOf("-")).replaceAll("[^\\-а-яA-ЯЁё]", "");
                        word = word.replace(cleanedWord, boldStart.concat(cleanedWord).concat(boldEnd));
                    }
                    if((lemmasAndWords.get(lemma).stream().anyMatch(formattedWord::endsWith))){
                        String cleanedWord = word.substring(word.lastIndexOf("-") + 1).replaceAll("[^\\-а-яA-ЯЁё]", "");
                        word = word.replace(cleanedWord, boldStart.concat(cleanedWord).concat(boldEnd));
                    }
                }
            }
            formattedSentence.append(word).append(" ");
        }
        return formattedSentence.toString();
    }

    private String getSnippet(String formattedSentence, String text) {
        String sentence = formattedSentence
                .replaceAll("</?b>", "").trim();
        int startIndex = text.indexOf(sentence);
        int endIndex = text.indexOf(sentence) + sentence.length();
        if (startIndex < 0) {
            return formattedSentence.length() > 240
                    ? formattedSentence
                    .substring(0, 240) : formattedSentence;
        }
        String subString = text.substring(startIndex, endIndex);
        text = text.replace(subString, formattedSentence);
        return cutText(startIndex, endIndex, text);
    }


    private String cutText(int startIndex, int endIndex, String text) {
        int[] distance = getDistance(text, startIndex, endIndex);
        int substringStart = text.indexOf("\s",
                startIndex > distance[0]
                        ? startIndex - distance[0] : startIndex);
        if (substringStart > text.indexOf(boldStart)) {
            substringStart = 0;
        }
        int substringEnd = text.length() - endIndex - 1 > distance[1]
                ? endIndex + distance[1] : text.length();
        String snippet = text.substring(substringStart,
                substringEnd).concat("...");
        if (snippet.length() > 240) {
            snippet = snippet.substring(0, 240)
                    .concat("...");
        }
        return snippet;
    }

    private int[] getDistance(String text, int startIndex, int endIndex) {
        int leftSpace = 30;
        int rightSpace = 250;
        if (startIndex < leftSpace) {
            leftSpace = Math.abs(leftSpace - startIndex);
        }
        if (text.length() - endIndex < rightSpace) {
            rightSpace = Math.abs(text.length() - endIndex - 1);
        }
        return new int[]{leftSpace, rightSpace};
    }
}