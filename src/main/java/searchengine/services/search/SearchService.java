package searchengine.services.search;

import searchengine.dto.statistics.Response;

public interface SearchService {
    String[] errors = {
            "Данного сайта нет в списке.",
            "Запрос введен некорректно.",
            "Страниц, удовлетворяющих запрос, нет.",
            "Слова в запросе встречаются слишком часто." +
                    " Попробуйте ввести более полный запрос."};

    Response searchAndGetResponse(String query, String site,
                                  Integer offset, Integer limit);

}
