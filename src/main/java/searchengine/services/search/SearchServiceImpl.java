package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
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
import searchengine.services.indexation.LemmaFinder;

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
    public Response getResponse(String query, String site, Integer offset,
                                Integer limit) {
        List<Site> sites = getSites(site);
        List<Data> dataList = new ArrayList<>();
        Set<String> lemmas = lemmaFinder.getLemmaSet(query
                .replaceAll("[Ёё]", "е"));
        List<Lemma> sortedLemmas = getSortedLemmas(lemmas);
        Set<Page> pages = getPages(sortedLemmas, sites, offset, limit);
        if (pages != null) {
            for (Page page : pages) {
                String content = page.getContent();
                dataList.add(getData(page, content, sortedLemmas));
            }
        }
        dataList.sort(Collections.reverseOrder());
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
        Optional<Site> optSite = siteRepo.findFirstByUrl(site);
        if (site != null && optSite.isPresent()) {
            sites.add(optSite.get());
        } else {
            sites = siteRepo.findAll();
        }
        return sites;
    }


    private Data getData(Page page, String content,
                         List<Lemma> sortedLemmas) {
        Data data = new Data();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        data.setTitle(getTitle(content));
        data.setRelevance(getRelevance(page));
        String text = Jsoup.clean(content, Safelist.relaxed())
                .replaceAll("&nbsp;", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("https?://[\\w\\W]\\S+", "")
                .replaceAll("\\s*\\n+\\s*", " · ");
        //               .replaceAll("\\s+", " ");
        String boldPhrase = getBoldPhrase(text, sortedLemmas);
        if (boldPhrase == null) {
            return null;
        }
        data.setSnippet(getSnippet(boldPhrase, text));
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

    private Set<Page> getPages(List<Lemma> sortedLemmas, List<Site> sites,
                               int offset, int limit) {
        if (CollectionUtils.isEmpty(sortedLemmas)) {
            return null;
        }
        Set<Page> pages = pageRepo.findPagesByLemmasAndSites(sortedLemmas, sites);
        for (int i = 0; i < sortedLemmas.size() - 1; i++) {
            Set<Page> foundPages = pageRepo
                    .findPagesByLemmaAndSites(sortedLemmas.get(i), sites, pages);
            pages.clear();
            pages.addAll(foundPages);
        }
        return pageRepo.findPagesBySitesAndLemmaWithLimitAndOffset(sortedLemmas
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
        if (CollectionUtils.isEmpty(lemmasAndWords)) {
            return null;
        }
        String formattedPart = getFormattedPart(text,
                sortedLemmas, lemmasAndWords);
        if (formattedPart != null && formattedPart.contains(boldStart)) {
            snippets.putAll(collectPhrasesAndLengths(formattedPart));
        }
        return getLongestBoldPart(snippets);
    }

    private Map<String, Integer> collectPhrasesAndLengths(String formattedPart) {
        Map<String, Integer> snippets = new LinkedHashMap<>();
        String[] snippetWords = formattedPart.split(" ");
        for (String w : snippetWords) {
            if (w.contains(boldStart)) {
                int length = formattedPart.substring(formattedPart
                        .indexOf(boldStart) + boldStart.length(), formattedPart
                        .indexOf(boldEnd)).length();
                if (snippets.containsKey(formattedPart)) {
                    snippets.put(formattedPart,
                            snippets.get(formattedPart) + length);
                } else {
                    snippets.put(formattedPart, length);
                }
            }
        }
        return snippets;
    }


    private String getLongestBoldPart(Map<String, Integer> snippets) {
        String boldPart = "";
        Optional<Map.Entry<String, Integer>> optBoldPart
                = snippets.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        if (optBoldPart.isPresent()) {
            boldPart = optBoldPart.get().getKey();
        }
        int start = boldPart.indexOf(boldStart);
        int end = boldPart.lastIndexOf(boldEnd) + boldEnd.length();
        boldPart = boldPart.substring(start > 3 ? start - 3 : start,
                boldPart.length() - end > 3 ? end + 3 : end);
        return boldPart;
    }


    private String getFormattedPart(String part, List<Lemma> sortedLemmas,
                                    Map<String, Set<String>> lemmasAndWords) {
        String[] words = part.split("\\s");
        StringBuilder formattedPart = new StringBuilder();
        if (lemmasAndWords == null) {
            return null;
        }
        for (String word : words) {
            for (Lemma lemma : sortedLemmas) {
                word = getWordInBold(word, lemma.getLemma(), lemmasAndWords);
            }
            formattedPart.append(word).append(" ");
        }
        return formattedPart.toString();
    }

    private String getWordInBold(String word, String lemma,
                                 Map<String, Set<String>> lemmasAndWords) {
        String[] formattedWord = word
                .replaceAll("([^А-Яа-яЁё\\-])", " ")
                .trim()
                .split("[- ]");
        for (String part : formattedWord) {
            if ((lemmasAndWords.get(lemma).stream()
                    .anyMatch(part.replaceAll("Ё", "Е")
                            .replaceAll("ё", "е")
                            ::equalsIgnoreCase))) {
                word = word.replace(part,
                        boldStart.concat(part).concat(boldEnd));
            }
        }
        return word;
    }

    private String getSnippet(String formattedPart, String text) {
        int leftSpace = 20;
        int snippetLength = leftSpace * 12;
        String threeDots = " ...";
        String sentence = formattedPart
                .replaceAll("</?b>", "").trim();
        int startIndex = text.indexOf(sentence);
        int endIndex = text.indexOf(sentence) + sentence.length();
        String subString = text.substring(startIndex, endIndex);
        text = text.replace(subString, formattedPart);
        int substringStart = getSubstringStart(text,
                formattedPart, leftSpace);
        String snippet = text.substring(substringStart,
                text.length() - 1);
        return getCutSnippet(snippet, snippetLength).concat(threeDots);
    }

    private String getCutSnippet(String snippet, int snippetLength) {
        String tags = "</?b>";
        int count = StringUtils.countOccurrencesOf(snippet, boldStart);
        String unformattedSnippet = snippet.replaceAll(tags, "")
                .substring(0, snippetLength);
        String formattedSnippet = "";
        for (int i = count; i >= 1; i--) {
            int endOfSnippet = snippet.indexOf(" ",
                    snippetLength + i
                            * (boldStart.length() + boldEnd.length()));
            formattedSnippet = snippet.substring(0, endOfSnippet);
            if (formattedSnippet.replaceAll(tags, "")
                    .length() < unformattedSnippet.length()) {
                break;
            }
        }
        return formattedSnippet;
    }


    private int getSubstringStart(String text, String formattedPart, int leftSpace) {
        String maxFormattedSentence = getMaxSentenceWithTags(formattedPart);
        int startIndex = text.indexOf(maxFormattedSentence);
        if (startIndex < leftSpace) {
            leftSpace = 0;
        }
        return text.indexOf(" ",
                startIndex > leftSpace ? startIndex
                        - leftSpace : startIndex);
    }

    private String getMaxSentenceWithTags(String formattedPart) {
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
        int index = formattedPart.indexOf(maxSentence);
        return formattedPart.substring(index,
                index + maxSentence.length());
    }
}