package searchengine.services.search;

import searchengine.dto.statistics.Response;

public interface SearchService {
    String[] errors = {
            "Данного сайта нет в списке",
            "Запрос введен некорректно",
            "Страниц, удовлетворяющих запрос, нет, " +
                    "или слова в запросе встречаются слишком часто",
            "Введен пустой запрос"};

    Response searchAndGetResponse(String query,
                                  String site,
                                  Integer offset,
                                  Integer limit);

}
