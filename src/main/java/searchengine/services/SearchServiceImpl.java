package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
    private final String[] errors = {
            "Указанная страница не найдена",
            "Запрос содержит несуществующие слова",
            "Ошибка при поиске"};
    private final String boldStart = "<b>";
    private final String boldEnd = "</b>";
    private final SiteRepository siteRepo;
    private final PageRepository pageRepo;
    private final LemmaRepository lemmaRepo;
    private final IndexRepository indexRepo;
    private final SitesList sitesList;

    @Override
    public ResponseEntity<Response> getResponse(String query, String site, Integer offset, Integer limit) {
        try {
            limit = 20;
            List<Site> sites = getSites(site);
            List<Data> dataList = new ArrayList<>();
            Set<String> lemmas = LemmaFinder.getInstance().getLemmaSet(query);
            Set<Page> pages = getPages(lemmas, sites, offset, limit);
            if (pages != null) {
                for (Page page : pages) {
                    String content = page.getContent();
                    dataList.add(getData(page, content, lemmas));
                }
            }
            dataList.sort(Collections.reverseOrder());
            return handleErrors(site, lemmas, pages, dataList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<Response> handleErrors(String site, Set<String> lemmas,
                                  Set<Page> pages, List<Data> dataList) {
        Response response = new Response();
        if (site != null && sitesList.getSites().stream()
                .noneMatch(s -> s.getUrl().equals(site))) {
            response.setError(errors[0]);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        if (lemmas.isEmpty()) {
            response.setError(errors[1]);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        if (pages == null) {
            response.setError(errors[2]);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(getResponse(dataList), HttpStatus.OK);

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
        String titleStart = "<title>";
        String titleEnd = "</title>";
        Data data = new Data();
        data.setSite(page.getSite().getUrl());
        data.setSiteName(page.getSite().getName());
        data.setUri(page.getPath());
        if (content.contains(titleStart)) {
            int start = content.indexOf(titleStart) + titleStart.length();
            int end = content.indexOf(titleEnd);
            data.setTitle(page.getContent().substring(start, end));
        }
        float relevance = indexRepo.getRelevance(page);
        data.setRelevance(relevance);
        String text = Jsoup.clean(content, Safelist.none());
        data.setSnippet(getSnippet(getBoldPhrase(text, lemmas), text));
        return data;
    }

    private Set<Page> getPages(Set<String> lemmas, List<Site> sites, int offset, int limit) {
        List<Lemma> sortedLemmas = lemmaRepo.findByLemmas(lemmas);
        if (sortedLemmas.isEmpty()) {
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

    private String getBoldPhrase(String text, Set<String> lemmas) throws IOException {
        Map<String, Integer> snippets = new LinkedHashMap<>();

        Map<String, Set<String>> lemmasAndWords =
                LemmaFinder.getInstance().getTextInLemmas(text);
        String[] sentences = text.split("\\.");
        for (String sentence : sentences) {
            String[] words = sentence.split("[\\s,:;!?()\"<>*/]+");
            String formattedSentence = getFormattedSentence(words, lemmas, lemmasAndWords);
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
        Optional<Map.Entry<String, Integer>> optBoldPhrase = snippets.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        if(optBoldPhrase.isPresent()) {
            String boldPhrase = optBoldPhrase.get().getKey();
            int start = boldPhrase.indexOf(boldStart);
            int end = boldPhrase.lastIndexOf(boldEnd) + boldEnd.length();
            boldPhrase = boldPhrase.substring(start, end);
            return boldPhrase;
        }
        return "";
    }



    private String getFormattedSentence(String[] words, Set<String> lemmas,
                                        Map<String, Set<String>> lemmasAndWords) {
        String formattedSentence;
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            word = word.trim();
            for (String lemma : lemmas) {
                if (lemmasAndWords.get(lemma).contains(word.toLowerCase())) {
                    word = boldStart.concat(word).concat(boldEnd);
                }
            }
            builder.append(word).append(" ");
        }
        formattedSentence = builder.toString();
        return formattedSentence;
    }

    private String getSnippet(String formattedSentence, String text) {
        String sentence = formattedSentence
                .replaceAll("</?b>", "").trim();
        int startIndex = text.indexOf(sentence);
        int endIndex = text.indexOf(sentence) + sentence.length();
        if (startIndex < 0) {
            return formattedSentence.length() > 250
                    ? formattedSentence
                    .substring(0, 250) : formattedSentence;
        }
        String subString = text.substring(startIndex, endIndex);
        text = text.replace(subString, formattedSentence);
        return cutText(startIndex, endIndex, text);
    }


    private String cutText(int startIndex, int endIndex, String text) {
        int[] spaces = getSpaces(text, startIndex, endIndex);
        int substringStart = text.indexOf(" ",
                startIndex > spaces[0]
                        ? startIndex - spaces[0] : startIndex) + 1;
        int substringEnd = text.indexOf(" ",
                text.length() - endIndex > spaces[1]
                        ? endIndex + spaces[1] : endIndex);
        String snippet = text.substring(substringStart,
                substringEnd).concat("...");
        if (snippet.length() > 290) {
            snippet = snippet.substring(0, 290)
                    .concat("...");
        }
        return snippet;
    }

    private int[] getSpaces(String text, int startIndex, int endIndex) {
        int leftSpace = 50;
        int rightSpace = 190;
        if (startIndex < leftSpace) {
            leftSpace = Math.abs(leftSpace - startIndex);
        }
        if (text.length() - endIndex < rightSpace) {
            rightSpace = Math.abs(text.length() - 1 - endIndex);
        }
        return new int[]{leftSpace, rightSpace};
    }
}