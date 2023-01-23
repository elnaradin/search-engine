package searchengine.services;

import searchengine.dto.statistics.Response;

public interface SearchService {
    String[] ERRORS = {
            "Данного сайта нет в списке",
            "Запрос введен некорректно",
            "Страниц, удовлетворяющих запрос, нет", "Введен пустой запрос"};

    Response getResponse(String query,
                         String site,
                         Integer offset,
                         Integer limit);
}
