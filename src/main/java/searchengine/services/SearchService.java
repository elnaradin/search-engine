package searchengine.services;

import searchengine.dto.statistics.Response;

public interface SearchService {
    String[] errors = {
            "Данного сайта нет в списке",
            "Запрос введен некорректно",
            "Страниц, удовлетворяющих запрос, нет, " +
                    "либо слова в запросе встречаются слишком часто",
            "Введен пустой запрос"};

    Response getResponse(String query,
                         String site,
                         Integer offset,
                         Integer limit);
}
