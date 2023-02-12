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
import searchengine.services.morphology.LemmaFinderImpl;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private Float maxRelevanceValue;
    private final String boldStart = "<b>";
    private final String boldEnd = "</b>";
    private final String boldRegex = "</?b>";
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final SitesList sitesList;
    private final LemmaFinderImpl lemmaFinder;

    @Override
    public Response searchAndGetResponse(String query, String site,
                                         Integer offset, Integer limit) {
        Set<String> lemmas = lemmaFinder.getLemmaSet(query
                .replaceAll("[Ёё]", "е"));
        List<Lemma> sortedLemmas = findLemmasInDBAndSort(lemmas);
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
        return createOkResponse(limit, offset, sortedLemmas, site);
    }

    private boolean siteIsPresent(String site) {
        return sitesList.getSites().stream()
                .anyMatch(s -> (s.getUrl().endsWith("/") ?
                        s.getUrl().substring(0, s.getUrl().length() - 1)
                        : s.getUrl()).equals(site));
    }

    private Response createOkResponse(int limit, int offset,
                                      List<Lemma> sortedLemmas, String site) {
        List<Data> dataList = createDataList(sortedLemmas, site);
        Response response = new Response();
        response.setCount(dataList.size());
        response.setResult(true);
        int endIndex = offset + limit;
        response.setData(dataList.subList(offset,
                Math.min(endIndex, dataList.size())));
        return response;
    }

    private List<Data> createDataList(List<Lemma> sortedLemmas, String site) {
        List<Site> sites = getSites(site);
        Set<Page> pages = findPages(sortedLemmas, sites);
        List<Data> dataList = new ArrayList<>();
        for (Page page : pages) {
            String content = page.getContent();
            Data data = collectData(page, content, sortedLemmas);
            dataList.add(data);
        }
        dataList.sort(Collections.reverseOrder());
        return dataList;
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


    private Data collectData(Page page, String content,
                             List<Lemma> sortedLemmas) {
        Data data = new Data();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        data.setTitle(findTitle(content));
        data.setRelevance(getRelevance(page));
        String text = Jsoup.clean(content, Safelist.relaxed())
                .replaceAll("&nbsp;", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("https?://[\\w\\W]\\S+", "")
                .replaceAll("\\s*\\n+\\s*", " · ");
        data.setSnippet(createSnippet(text, sortedLemmas));
        return data;
    }

    private String findTitle(String content) {
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

    private Set<Page> findPages(List<Lemma> sortedLemmas, List<Site> sites) {
        Set<Page> pages = pageRepo.findPagesByLemmasAndSites(sortedLemmas, sites);
        for (int i = 0; i < sortedLemmas.size() - 1; i++) {
            Set<Page> foundPages = pageRepo
                    .findPagesByOneLemmaAndSitesAndPages(sortedLemmas.get(i),
                            sites, pages);
            pages.clear();
            pages.addAll(foundPages);
        }
        return pageRepo.findPagesByOneLemmaAndSitesAndPages(sortedLemmas
                .get(sortedLemmas.size() > 0 ? sortedLemmas
                        .size() - 1 : 0), sites, pages);
    }

    private List<Lemma> findLemmasInDBAndSort(Set<String> lemmas) {
        List<Lemma> sortedLemmas = lemmaRepo.findByLemmas(lemmas);
        if (sortedLemmas.size() < lemmas.size()) {
            return null;
        }
        List<Lemma> lemmasToRemove = new Vector<>();
        for (Lemma lemma : sortedLemmas) {
            if (lemma.getFrequency() > 250) {
                lemmasToRemove.add(lemma);
            }
        }
        sortedLemmas.removeAll(lemmasToRemove);
        return sortedLemmas;
    }

    private String createSnippet(String text, List<Lemma> sortedLemmas) {
        String formattedPart = findQueryPartAndFormat(text, sortedLemmas);
        int leftSpace = 20;
        int snippetLength = leftSpace * 12;
        String threeDots = " ...";
        String sentence = formattedPart
                .replaceAll(boldRegex, "").trim();
        int startIndex = text.indexOf(sentence);
        int endIndex = text.indexOf(sentence) + sentence.length();
        String subString = text.substring(startIndex, endIndex);
        text = text.replace(subString, formattedPart);
        int substringStart = findStartIndexInText(text,
                formattedPart, leftSpace);
        String snippet = text.substring(substringStart,
                text.length() - 1);
        return cutSnippet(snippet, snippetLength).concat(threeDots);
    }

    private String findQueryPartAndFormat(String text, List<Lemma> sortedLemmas) {
        Map<String, Set<String>> lemmasAndWords =
                lemmaFinder.collectLemmasAndWords(text);
        String formattedText = formatText(text,
                sortedLemmas, lemmasAndWords);
        int start = formattedText.indexOf(boldStart);
        int end = formattedText.lastIndexOf(boldEnd) + boldEnd.length();
        return formattedText.substring(start > 3 ? start - 3 : start,
                formattedText.length() - end > 3 ? end + 3 : end);
    }


    private String formatText(String text, List<Lemma> sortedLemmas,
                              Map<String, Set<String>> lemmasAndWords) {
        String[] words = text.split(" ");
        StringBuilder formattedPart = new StringBuilder();
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

    private String cutSnippet(String snippet, int snippetLength) {

        int count = StringUtils.countOccurrencesOf(snippet, boldStart);
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


    private int findStartIndexInText(String text, String formattedPart, int leftSpace) {
        String maxSentence = findSentenceWithMaxTags(formattedPart);
        int startIndex = text.indexOf(maxSentence);
        if (startIndex < leftSpace) {
            leftSpace = 0;
        }
        return text.indexOf(" ",
                startIndex > leftSpace ? startIndex
                        - leftSpace : startIndex);
    }

    private String findSentenceWithMaxTags(String formattedPart) {
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