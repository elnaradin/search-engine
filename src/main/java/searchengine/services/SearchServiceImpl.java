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
    private Float maxRelevanceValue;
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
        List<Site> sites = getSites(site);
        List<Data> dataList = new ArrayList<>();
        Set<String> lemmas = lemmaFinder.getLemmaSet(query
                .replaceAll("[Ёё]", "е"));
        List<Lemma> sortedLemmas = getSortedLemmas(lemmas);
        Set<Page> pages = getPages(sortedLemmas, sites, offset, limit);
        try {
            if (pages != null) {
                for (Page page : pages) {
                    String content = page.getContent();
                    dataList.add(getData(page, content, sortedLemmas));
                }
            }
            dataList.sort(Collections.reverseOrder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return handleErrors(site, lemmas, sortedLemmas, pages, dataList, query);
    }

    private Response handleErrors(String site, Set<String> lemmas, List<Lemma> sortedLemmas,
                                  Set<Page> pages, List<Data> dataList, String query) {
        Response response = new Response();
        if (site != null && !siteIsPresent(site)) {
            response.setError(errors[0]);
            return response;
        }
        if (CollectionUtils.isEmpty(lemmas)) {
            response.setError(errors[1]);
            return response;
        }
        if (CollectionUtils.isEmpty(sortedLemmas)) {
            response.setError(errors[2]);
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
                         List<Lemma> sortedLemmas) throws IOException {
        Data data = new Data();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        data.setTitle(getTitle(content));
        data.setRelevance(getRelevance(page));
        String text = Jsoup.clean(content, Safelist.none())
                .replaceAll("&nbsp;", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("https?://[\\w\\W]\\S+", "")
                .replaceAll("\\s+", " ");

        data.setSnippet(getSnippet(getBoldPhrase(text, sortedLemmas), text));
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
        if (maxRelevanceValue == null) {
            maxRelevanceValue = indexRepo.getMaxValue();
        }
        return indexRepo
                .getRelevance(page, maxRelevanceValue);
    }

    private Set<Page> getPages(List<Lemma> sortedLemmas, List<Site> sites, int offset, int limit) {
        if(CollectionUtils.isEmpty(sortedLemmas)){
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
        List<Lemma> lemmasToRemove = new Vector<>();
        for (Lemma lemma : sortedLemmas) {
            if (lemma.getFrequency() > 2000) {
                lemmasToRemove.add(lemma);
            }
        }
        sortedLemmas.removeAll(lemmasToRemove);
        return sortedLemmas;
    }

    private String getBoldPhrase(String text, List<Lemma> sortedLemmas) {
        Map<String, Integer> snippets = new LinkedHashMap<>();

        Map<String, Set<String>> lemmasAndWords =
                lemmaFinder.getTextInLemmas(text);
        String[] sentences = text.split("\\.");
        for (String sentence : sentences) {

            String formattedSentence = getFormattedSentence(sentence, sortedLemmas, lemmasAndWords);
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


    private String getFormattedSentence(String sentence, List<Lemma> sortedLemmas,
                                        Map<String, Set<String>> lemmasAndWords) {
        String[] words = sentence.split("\\s");
        StringBuilder formattedSentence = new StringBuilder();
        for (String word : words) {

            for (Lemma lemma : sortedLemmas) {
                if (lemmasAndWords == null) {
                    continue;
                }
                word = getWordInBold(word, lemma.getLemma(), lemmasAndWords);
            }
            formattedSentence.append(word).append(" ");
        }
        return formattedSentence.toString();
    }

    private String getWordInBold(String word, String lemma,
                                 Map<String, Set<String>> lemmasAndWords) {
        String[] formattedWord = word
                .replaceAll("([^А-Яа-яЁё\\-])", " ")
                .trim()
                .split("[- ]");
        for (String part : formattedWord) {
            if ((lemmasAndWords.get(lemma).stream()
                    .anyMatch(part::equalsIgnoreCase))) {
                word = word.replace(part,
                        boldStart.concat(part).concat(boldEnd));
            }
        }
        return word;
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
        int substringStart = text.indexOf(" ",
                startIndex > distance[0]
                        ? startIndex - distance[0] : startIndex);
        if (substringStart > text.indexOf(boldStart)) {
            substringStart = 0;
        }
        int substringEnd = text.length() - endIndex - 1 > distance[1]
                ? endIndex + distance[1] : text.length();
        String snippet = text.substring(substringStart,
                substringEnd).concat("...");
        if (snippet.length() > 260) {
            snippet = snippet.substring(0, 260)
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